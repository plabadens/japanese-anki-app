package dev.pierre.japaneseanki

import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

/** Item 5: the production controller's per-item jobs, tokens, retry, persistence and freeze. */
@RunWith(AndroidJUnit4::class)
class DraftAudioControllerTest {
    private class Fixture {
        val root: File = kotlin.io.path.createTempDirectory("audio-controller").toFile()
        val finals: File = File(root, "draft").apply { mkdirs() }
        val staging: File = File(root, "audio-staging").apply { mkdirs() }
        val cacheDir: File = File(root, "cache")
        val ownership = AudioOwnershipRegistry()
        val store = DraftAudioFileStore(finals, staging, AudioFileOperations.system(), ownership)
        var providerCalls = 0
        var providerFailure: Exception? = null
        var duringSynthesis: (() -> Unit)? = null
        val provider: (DraftAudioInput, File) -> Unit = { input, file ->
            providerCalls++
            duringSynthesis?.invoke()
            providerFailure?.let { throw it }
            when (input.expectedFormat) {
                AudioFormat.WAV -> AudioFixtures.wav(file)
                AudioFormat.OGG -> AudioFixtures.ogg(file)
                AudioFormat.MP3 -> AudioFixtures.mp3(file)
            }
        }

        fun input(
            item: AudioItem,
            identity: String,
            text: String = "悪逆だ",
            format: AudioFormat = AudioFormat.MP3,
            revision: Long = 0,
        ) = DraftAudioInput(
            item = item,
            identity = identity,
            text = text,
            revision = revision,
            cacheKey = AudioCacheKey("test-source", text, "voice", 1f),
            expectedFormat = format,
        )

        fun close() = root.deleteRecursively()
    }

    private class MapPersistence : DraftAudioPersistence {
        val values = HashMap<String, String>()
        var failWrites = false
        var writes = 0

        override fun read(key: String): String? = values[key]

        override fun write(changes: Map<String, String?>): Boolean {
            writes++
            if (failWrites) return false
            for ((key, value) in changes) {
                if (value == null) values.remove(key) else values[key] = value
            }
            return true
        }
    }

    private fun controller(
        fixture: Fixture,
        persistence: MapPersistence,
        cache: AudioCacheSource = AudioCacheSource.disabled,
        legacyPaths: Map<AudioItem, String?> = emptyMap(),
    ) = DraftAudioController(
        store = fixture.store,
        persistence = persistence,
        synthesize = fixture.provider,
        cache = cache,
        legacyPaths = legacyPaths,
    )

    private fun readyBoth(fixture: Fixture, persistence: MapPersistence): DraftAudioController {
        val controller = controller(fixture, persistence)
        controller.setInput(fixture.input(AudioItem.SENTENCE, "sentence-a"))
        controller.runAttemptForTest(controller.confirm(AudioItem.SENTENCE)!!)
        controller.setInput(fixture.input(AudioItem.VOCABULARY, "word-a", format = AudioFormat.OGG))
        controller.runAttemptForTest(controller.confirm(AudioItem.VOCABULARY)!!)
        return controller
    }

    @Test
    fun newInputRequiresExplicitContinueAndDoesNotGenerate() {
        val fixture = Fixture()
        try {
            val persistence = MapPersistence()
            val controller = controller(fixture, persistence)
            assertTrue(controller.setInput(fixture.input(AudioItem.SENTENCE, "sentence-a")))
            assertTrue(controller.state(AudioItem.SENTENCE) is DraftAudioState.NeedsContinue)
            assertEquals("no automatic provider call", 0, fixture.providerCalls)
            assertEquals(DraftAudioState.Idle, controller.state(AudioItem.VOCABULARY))

            val attempt = controller.confirm(AudioItem.SENTENCE)
            assertNotNull(attempt)
            val result = controller.runAttemptForTest(attempt!!)
            assertTrue(result is DraftAudioAttemptResult.Accepted)
            assertTrue(controller.state(AudioItem.SENTENCE) is DraftAudioState.Ready)
            assertEquals(1, fixture.providerCalls)
            assertEquals(
                DraftAudioPreferences.STATUS_READY,
                persistence.values[DraftAudioPreferences.status(AudioItem.SENTENCE)],
            )
        } finally {
            fixture.close()
        }
    }

    @Test
    fun permanentSpeechFailureIsNotRetriedForUnchangedInput() {
        val fixture = Fixture()
        try {
            val persistence = MapPersistence()
            var calls = 0
            val controller = DraftAudioController(
                store = fixture.store,
                persistence = persistence,
                synthesize = { _, _ ->
                    calls++
                    throw dev.pierre.japaneseanki.speech.SpeechException(
                        dev.pierre.japaneseanki.speech.SpeechFailure.INVALID_REQUEST,
                        "The request was rejected.",
                        diagnostic = dev.pierre.japaneseanki.speech.SpeechDiagnostic(
                            category = dev.pierre.japaneseanki.speech.SpeechFailure.INVALID_REQUEST,
                            httpStatus = 400,
                            googleStatus = "INVALID_ARGUMENT",
                            googleReason = "INVALID_ARGUMENT",
                            exceptionClass = null,
                            durationMillis = 12,
                            request = dev.pierre.japaneseanki.speech.SpeechRequestMetadata(
                                operation = dev.pierre.japaneseanki.speech.SpeechOperation.SYNTHESIS,
                                inputBytes = 9,
                                requestBytes = 128,
                                voiceName = "ja-JP-Chirp3-HD-Achernar",
                                hintCount = 1,
                            ),
                        ),
                    )
                },
            )
            val input = fixture.input(AudioItem.SENTENCE, "permanent-rejection", revision = 3)
            controller.setInput(input)
            val failed = controller.runAttemptForTest(controller.confirm(AudioItem.SENTENCE)!!)
            assertTrue(failed is DraftAudioAttemptResult.Failed)
            assertFalse((failed as DraftAudioAttemptResult.Failed).retryable)
            assertNull(controller.retry(AudioItem.SENTENCE))

            controller.setInput(input)
            assertNull("an unchanged permanent failure is not eligible for Generate", controller.confirm(AudioItem.SENTENCE))
            assertEquals(1, calls)
            val state = controller.state(AudioItem.SENTENCE) as DraftAudioState.Failed
            assertFalse(state.retryable)
            assertEquals("INVALID_REQUEST", state.diagnostic?.category)
            assertNull(state.diagnostic?.exceptionClass)
            assertEquals(3L, state.diagnostic?.revision)
            assertEquals(AudioItem.SENTENCE, state.diagnostic?.item)
            assertEquals(400, state.diagnostic?.httpStatus)
            assertEquals("INVALID_ARGUMENT", state.diagnostic?.googleReason)
            assertEquals(12L, state.diagnostic?.durationMillis)
            assertEquals(128, state.diagnostic?.requestBytes)
            assertEquals("ja-JP-Chirp3-HD-Achernar", state.diagnostic?.voiceName)
            assertEquals(1, state.diagnostic?.hintCount)

            controller.dispose()
            val restored = controller(fixture, persistence)
            restored.setInput(input)
            val restoredFailure = restored.state(AudioItem.SENTENCE) as DraftAudioState.Failed
            assertFalse("restoring a permanent rejection does not make it retryable", restoredFailure.retryable)
            assertEquals("INVALID_REQUEST", restoredFailure.diagnostic?.category)
            assertEquals(400, restoredFailure.diagnostic?.httpStatus)
            assertEquals("INVALID_ARGUMENT", restoredFailure.diagnostic?.googleReason)
            assertNull(restored.retry(AudioItem.SENTENCE))
            assertEquals("restoration must not call the provider", 1, calls)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun confirmIsSingleFlightPerItem() {
        val fixture = Fixture()
        try {
            val controller = controller(fixture, MapPersistence())
            controller.setInput(fixture.input(AudioItem.SENTENCE, "sentence-a"))
            assertNotNull(controller.confirm(AudioItem.SENTENCE))
            assertNull("a second Continue must not start another attempt", controller.confirm(AudioItem.SENTENCE))
            assertTrue(controller.state(AudioItem.SENTENCE) is DraftAudioState.Generating)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun cacheHitSkipsTheProviderEntirely() {
        val fixture = Fixture()
        try {
            val cache = ReusableAudioCache(fixture.cacheDir)
            val input = fixture.input(AudioItem.SENTENCE, "sentence-a")
            val seed = AudioFixtures.mp3(File(fixture.root, "seed.mp3"))
            assertTrue(cache.store(input.cacheKey, seed) is AudioCacheStoreResult.Stored)

            val controller = controller(fixture, MapPersistence(), cache)
            controller.setInput(input)
            val result = controller.runAttemptForTest(controller.confirm(AudioItem.SENTENCE)!!)
            assertTrue(result is DraftAudioAttemptResult.Accepted)
            assertTrue((result as DraftAudioAttemptResult.Accepted).fromCache)
            assertEquals("cache hit must not call the provider", 0, fixture.providerCalls)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun cacheMissCallsProviderOnceAndCachesBestEffort() {
        val fixture = Fixture()
        try {
            val cache = ReusableAudioCache(fixture.cacheDir)
            val controller = controller(fixture, MapPersistence(), cache)
            val input = fixture.input(AudioItem.SENTENCE, "sentence-a")
            controller.setInput(input)
            val result = controller.runAttemptForTest(controller.confirm(AudioItem.SENTENCE)!!)
            assertTrue(result is DraftAudioAttemptResult.Accepted)
            assertFalse((result as DraftAudioAttemptResult.Accepted).fromCache)
            assertEquals(1, fixture.providerCalls)
            assertNotNull("provider output is cached for reuse", cache.get(input.cacheKey))
        } finally {
            fixture.close()
        }
    }

    @Test
    fun brokenCacheDoesNotLoseValidDraftAudio() {
        val fixture = Fixture()
        try {
            val broken = object : AudioCacheSource {
                override fun open(key: AudioCacheKey): AudioCacheLease? = throw IOException("unwritable cache")
                override fun store(key: AudioCacheKey, source: File): AudioCacheStoreResult =
                    AudioCacheStoreResult.Failed("Reusable audio cache is unavailable (IOException)")
            }
            val controller = controller(fixture, MapPersistence(), broken)
            controller.setInput(fixture.input(AudioItem.SENTENCE, "sentence-a"))
            val result = controller.runAttemptForTest(controller.confirm(AudioItem.SENTENCE)!!)
            assertTrue(result is DraftAudioAttemptResult.Accepted)
            assertEquals(1, fixture.providerCalls)
            assertTrue((controller.state(AudioItem.SENTENCE) as DraftAudioState.Ready).file.isIntact())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun uncopyableCacheEntryFailsInsteadOfPayingForSynthesis() {
        val fixture = Fixture()
        try {
            val brokenCopy = object : AudioCacheSource {
                override fun open(key: AudioCacheKey): AudioCacheLease =
                    AudioCacheLease(File(fixture.root, "gone.mp3"), AudioFormat.MP3) {}
                override fun store(key: AudioCacheKey, source: File): AudioCacheStoreResult =
                    AudioCacheStoreResult.Skipped("not used")
            }
            val controller = controller(fixture, MapPersistence(), brokenCopy)
            controller.setInput(fixture.input(AudioItem.SENTENCE, "sentence-a"))
            val result = controller.runAttemptForTest(controller.confirm(AudioItem.SENTENCE)!!)
            assertTrue(result is DraftAudioAttemptResult.Failed)
            assertEquals("a cache read failure must not trigger a billable call", 0, fixture.providerCalls)
            assertTrue(controller.state(AudioItem.SENTENCE) is DraftAudioState.Failed)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun providerFailureRequiresExplicitPerItemRetry() {
        val fixture = Fixture()
        try {
            val persistence = MapPersistence()
            val controller = controller(fixture, persistence)
            controller.setInput(fixture.input(AudioItem.SENTENCE, "sentence-a"))
            fixture.providerFailure = IOException("Speech synthesis failed.")
            val failed = controller.runAttemptForTest(controller.confirm(AudioItem.SENTENCE)!!)
            assertTrue(failed is DraftAudioAttemptResult.Failed)
            val state = controller.state(AudioItem.SENTENCE)
            assertTrue(state is DraftAudioState.Failed)
            assertTrue((state as DraftAudioState.Failed).retryable)

            // Recomposition with the same input must not retry by itself.
            controller.setInput(fixture.input(AudioItem.SENTENCE, "sentence-a"))
            assertEquals(1, fixture.providerCalls)
            assertTrue(controller.confirm(AudioItem.SENTENCE) == null)

            fixture.providerFailure = null
            val retried = controller.retry(AudioItem.SENTENCE)
            assertNotNull("Retry must be explicit and per item", retried)
            assertTrue(controller.runAttemptForTest(retried!!) is DraftAudioAttemptResult.Accepted)
            assertEquals(2, fixture.providerCalls)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun lateSuccessAfterInputChangeIsRejected() {
        val fixture = Fixture()
        try {
            val controller = controller(fixture, MapPersistence())
            controller.setInput(fixture.input(AudioItem.SENTENCE, "sentence-a"))
            val attempt = controller.confirm(AudioItem.SENTENCE)!!
            // The user corrects the sentence while the provider is still working.
            fixture.duringSynthesis = {
                controller.setInput(fixture.input(AudioItem.SENTENCE, "sentence-b", text = "別の文"))
            }
            val result = controller.runAttemptForTest(attempt)
            assertEquals(DraftAudioAttemptResult.Stale, result)
            assertTrue(controller.state(AudioItem.SENTENCE) is DraftAudioState.NeedsContinue)
            assertEquals("no file may be published for a stale attempt", 0, fixture.finals.listFiles().orEmpty().size)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun cancellationRevokesAcceptanceButKeepsTheAlreadyStartedCallHonest() {
        val fixture = Fixture()
        try {
            val controller = controller(fixture, MapPersistence())
            controller.setInput(fixture.input(AudioItem.SENTENCE, "sentence-a"))
            val attempt = controller.confirm(AudioItem.SENTENCE)!!
            assertTrue(controller.cancel(AudioItem.SENTENCE))
            assertTrue(controller.state(AudioItem.SENTENCE) is DraftAudioState.NeedsContinue)
            assertEquals("cancelled attempt must not call the provider", DraftAudioAttemptResult.Stale, controller.runAttemptForTest(attempt))
            assertEquals(0, fixture.providerCalls)
            assertEquals(0, fixture.finals.listFiles().orEmpty().size)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun workerCancellationRevokesTheAttemptAndIsNotSwallowed() {
        val fixture = Fixture()
        try {
            val controller = controller(fixture, MapPersistence())
            controller.setInput(fixture.input(AudioItem.SENTENCE, "sentence-a"))
            val attempt = controller.confirm(AudioItem.SENTENCE)!!
            fixture.providerFailure = java.util.concurrent.CancellationException("job cancelled")
            var rethrown = false
            try {
                controller.runAttemptForTest(attempt)
            } catch (_: java.util.concurrent.CancellationException) {
                rethrown = true
            }
            assertTrue("cancellation must propagate to the caller", rethrown)
            assertTrue(controller.state(AudioItem.SENTENCE) is DraftAudioState.NeedsContinue)
            assertEquals(0, fixture.finals.listFiles().orEmpty().size)
            assertTrue("staging is cleaned up", fixture.staging.listFiles().orEmpty().isEmpty())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun aToBToARequiresContinueAndRetiresTheInvalidatedFile() {
        val fixture = Fixture()
        try {
            val controller = controller(fixture, MapPersistence())
            controller.setInput(fixture.input(AudioItem.SENTENCE, "sentence-a"))
            controller.runAttemptForTest(controller.confirm(AudioItem.SENTENCE)!!)
            val firstFile = (controller.state(AudioItem.SENTENCE) as DraftAudioState.Ready).file
            assertTrue(firstFile.file().isFile)

            controller.setInput(fixture.input(AudioItem.SENTENCE, "sentence-b", text = "別の文"))
            assertFalse("invalidated file is retired after the invalidation is persisted", firstFile.file().isFile)

            controller.setInput(fixture.input(AudioItem.SENTENCE, "sentence-a"))
            assertTrue(controller.state(AudioItem.SENTENCE) is DraftAudioState.NeedsContinue)
            assertEquals("returning to an old identity must not re-run automatically", 1, fixture.providerCalls)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun discardClearsStatePersistsAndLeavesNoLiveReference() {
        val fixture = Fixture()
        try {
            val persistence = MapPersistence()
            val controller = readyBoth(fixture, persistence)
            val sentenceFile = (controller.state(AudioItem.SENTENCE) as DraftAudioState.Ready).file
            val retirements = controller.discard()
            assertTrue(retirements.all { it == DraftAudioRetirement.RETIRED || it == DraftAudioRetirement.ALREADY_GONE })
            assertFalse(sentenceFile.file().isFile)
            assertEquals(DraftAudioState.Idle, controller.state(AudioItem.SENTENCE))
            assertNull(persistence.values[DraftAudioPreferences.status(AudioItem.SENTENCE)])

            val restarted = controller(fixture, persistence)
            restarted.setInput(fixture.input(AudioItem.SENTENCE, "sentence-a"))
            assertTrue(restarted.state(AudioItem.SENTENCE) is DraftAudioState.NeedsContinue)
            assertEquals("a recreated identical draft must not auto-generate", 2, fixture.providerCalls)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun restoreValidatedReadyMakesZeroProviderCalls() {
        val fixture = Fixture()
        try {
            val persistence = MapPersistence()
            val controller = readyBoth(fixture, persistence)
            val readyFile = (controller.state(AudioItem.SENTENCE) as DraftAudioState.Ready).file
            controller.dispose()

            val restored = controller(fixture, persistence)
            val state = restored.state(AudioItem.SENTENCE)
            assertTrue(state is DraftAudioState.Ready)
            assertEquals(readyFile, (state as DraftAudioState.Ready).file)
            assertTrue(restored.state(AudioItem.VOCABULARY) is DraftAudioState.Ready)
            assertEquals("restore must never call the provider", 2, fixture.providerCalls)
            assertTrue(restored.protectedPaths().contains(readyFile.path))
            assertNull("validated Ready audio needs no Continue", restored.confirm(AudioItem.SENTENCE))
        } finally {
            fixture.close()
        }
    }

    @Test
    fun restoreInterruptedOrLegacyStateRequiresRetry() {
        val fixture = Fixture()
        try {
            val pending = MapPersistence()
            pending.values[DraftAudioPreferences.VERSION] = DraftAudioPreferences.VERSION_VALUE
            pending.values[DraftAudioPreferences.EPOCH] = "7"
            pending.values[DraftAudioPreferences.identity(AudioItem.SENTENCE)] = "sentence-a"
            pending.values[DraftAudioPreferences.status(AudioItem.SENTENCE)] = DraftAudioPreferences.STATUS_PENDING
            val controller = controller(fixture, pending)
            val state = controller.state(AudioItem.SENTENCE)
            assertTrue(state is DraftAudioState.Failed)
            assertTrue((state as DraftAudioState.Failed).retryable)
            assertEquals(0, fixture.providerCalls)

            controller.setInput(fixture.input(AudioItem.SENTENCE, "sentence-a"))
            assertEquals("interrupted work stays failed until Retry", DraftAudioState.Failed::class.java, controller.state(AudioItem.SENTENCE)::class.java)
            assertNotNull(controller.retry(AudioItem.SENTENCE))

            val legacyFile = AudioFixtures.mp3(File(fixture.finals, "sentence-1700000000000.mp3"))
            val legacy = MapPersistence()
            val legacyController = controller(fixture, legacy, legacyPaths = mapOf(AudioItem.SENTENCE to legacyFile.absolutePath))
            val legacyState = legacyController.state(AudioItem.SENTENCE)
            assertTrue(legacyState is DraftAudioState.Failed)
            assertTrue((legacyState as DraftAudioState.Failed).message.contains("older version"))
            assertEquals(0, fixture.providerCalls)
            legacyController.setInput(fixture.input(AudioItem.SENTENCE, "sentence-a"))
            assertTrue(legacyController.state(AudioItem.SENTENCE) is DraftAudioState.Failed)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun restoreMissingReadyFileIsRetryableAndProtectedNothing() {
        val fixture = Fixture()
        try {
            val persistence = MapPersistence()
            val controller = controller(fixture, persistence)
            controller.setInput(fixture.input(AudioItem.SENTENCE, "sentence-a"))
            controller.runAttemptForTest(controller.confirm(AudioItem.SENTENCE)!!)
            val file = (controller.state(AudioItem.SENTENCE) as DraftAudioState.Ready).file
            assertTrue(file.file().delete())

            val restored = controller(fixture, persistence)
            val state = restored.state(AudioItem.SENTENCE)
            assertTrue(state is DraftAudioState.Failed)
            assertTrue((state as DraftAudioState.Failed).retryable)
            assertEquals(1, fixture.providerCalls)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun retryOfOneItemLeavesTheReadySiblingUntouched() {
        val fixture = Fixture()
        try {
            val controller = readyBoth(fixture, MapPersistence())
            val wordFile = (controller.state(AudioItem.VOCABULARY) as DraftAudioState.Ready).file
            controller.setInput(fixture.input(AudioItem.SENTENCE, "sentence-b", text = "別の文"))
            fixture.providerFailure = IOException("Speech synthesis failed.")
            controller.runAttemptForTest(controller.confirm(AudioItem.SENTENCE)!!)
            assertTrue(controller.state(AudioItem.SENTENCE) is DraftAudioState.Failed)
            assertEquals("Ready sibling must stay Ready", wordFile, (controller.state(AudioItem.VOCABULARY) as DraftAudioState.Ready).file)
            assertTrue(wordFile.file().isFile)

            fixture.providerFailure = null
            controller.runAttemptForTest(controller.retry(AudioItem.SENTENCE)!!)
            assertEquals(wordFile, (controller.state(AudioItem.VOCABULARY) as DraftAudioState.Ready).file)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun sendFreezeRequiresBothReadyThenPinsFilesAndBlocksChanges() {
        val fixture = Fixture()
        try {
            val controller = controller(fixture, MapPersistence())
            controller.setInput(fixture.input(AudioItem.SENTENCE, "sentence-a"))
            val attempt = controller.confirm(AudioItem.SENTENCE)!!
            val incomplete = controller.freezeForSend()
            assertTrue(incomplete is DraftAudioFreeze.Incomplete)
            assertTrue((incomplete as DraftAudioFreeze.Incomplete).missing.contains(AudioItem.SENTENCE))
            controller.runAttemptForTest(attempt)
            controller.setInput(fixture.input(AudioItem.VOCABULARY, "word-a", format = AudioFormat.OGG))
            controller.runAttemptForTest(controller.confirm(AudioItem.VOCABULARY)!!)

            val freeze = controller.freezeForSend()
            assertTrue(freeze is DraftAudioFreeze.Frozen)
            val frozen = (freeze as DraftAudioFreeze.Frozen).snapshot
            assertEquals(2, frozen.files.size)
            assertTrue(controller.protectedPaths().containsAll(frozen.files.values.map { it.path }))
            assertTrue(fixture.store.isOwned(frozen.files.getValue(AudioItem.SENTENCE).file()))
            assertFalse("frozen audio cannot be rescheduled", controller.setInput(fixture.input(AudioItem.SENTENCE, "sentence-b")))
            assertNull(controller.confirm(AudioItem.SENTENCE))
            assertTrue(controller.discard().isEmpty())
            assertTrue("frozen files remain on disk", frozen.files.getValue(AudioItem.SENTENCE).file().isFile)

            controller.releaseFreeze(retainFiles = false)
            assertTrue(controller.setInput(fixture.input(AudioItem.SENTENCE, "sentence-b", text = "別の文")))
        } finally {
            fixture.close()
        }
    }

    @Test
    fun persistenceFailureDoesNotClaimReadyAudio() {
        val fixture = Fixture()
        try {
            val persistence = MapPersistence()
            val controller = controller(fixture, persistence)
            controller.setInput(fixture.input(AudioItem.SENTENCE, "sentence-a"))
            val attempt = controller.confirm(AudioItem.SENTENCE)!!
            persistence.failWrites = true
            val result = controller.runAttemptForTest(attempt)
            assertTrue(result is DraftAudioAttemptResult.Failed)
            assertTrue(controller.state(AudioItem.SENTENCE) is DraftAudioState.Failed)
            assertEquals("no durable reference is claimed", 0, fixture.finals.listFiles().orEmpty().size)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun disposeRejectsLaterAcceptance() {
        val fixture = Fixture()
        try {
            val controller = controller(fixture, MapPersistence())
            controller.setInput(fixture.input(AudioItem.SENTENCE, "sentence-a"))
            val attempt = controller.confirm(AudioItem.SENTENCE)!!
            controller.dispose()
            assertEquals(DraftAudioAttemptResult.Stale, controller.runAttemptForTest(attempt))
            assertEquals(0, fixture.providerCalls)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun vocabularyProviderWritesOggIntoAnOggNamedStagingFile() {
        val fixture = Fixture()
        try {
            var stagingName: String? = null
            val controller = DraftAudioController(
                store = fixture.store,
                persistence = MapPersistence(),
                synthesize = { input, file ->
                    stagingName = file.name
                    AudioFixtures.ogg(file)
                },
            )
            controller.setInput(fixture.input(AudioItem.VOCABULARY, "word-a", format = AudioFormat.OGG))
            val result = controller.runAttemptForTest(controller.confirm(AudioItem.VOCABULARY)!!)
            assertTrue(result is DraftAudioAttemptResult.Accepted)
            assertTrue("NHK provider requires an .ogg destination", stagingName!!.endsWith(".ogg"))
            val ready = controller.state(AudioItem.VOCABULARY) as DraftAudioState.Ready
            assertEquals(AudioFormat.OGG.extension, ready.file.format)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun acceptedReplacementCommitsBeforeRetiringTheOldFile() {
        val fixture = Fixture()
        try {
            val controller = controller(fixture, MapPersistence())
            controller.setInput(fixture.input(AudioItem.SENTENCE, "sentence-a"))
            controller.runAttemptForTest(controller.confirm(AudioItem.SENTENCE)!!)
            val old = (controller.state(AudioItem.SENTENCE) as DraftAudioState.Ready).file
            val playback = fixture.store.lease(old, AudioFileOwner.PLAYBACK)

            controller.setInput(fixture.input(AudioItem.SENTENCE, "sentence-b", text = "別の文"))
            assertTrue("old file is still being played", old.file().isFile)
            controller.runAttemptForTest(controller.confirm(AudioItem.SENTENCE)!!)
            val new = (controller.state(AudioItem.SENTENCE) as DraftAudioState.Ready).file
            assertTrue(new.file().isFile)
            assertTrue("leased previous file is not deleted under the player", old.file().isFile)
            playback.close()

            val sweep = fixture.store.sweep(controller.protectedPaths())
            assertTrue("released previous file is retired by ownership-aware sweep", sweep.deleted.contains(old.file().name))
            assertTrue(new.file().isFile)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun invalidationIsNotRetiredWhenTheCommitFails() {
        val fixture = Fixture()
        try {
            val persistence = MapPersistence()
            val controller = readyBoth(fixture, persistence)
            val old = (controller.state(AudioItem.SENTENCE) as DraftAudioState.Ready).file

            persistence.failWrites = true
            controller.setInput(fixture.input(AudioItem.SENTENCE, "sentence-b", text = "別の文"))

            assertTrue(controller.state(AudioItem.SENTENCE) is DraftAudioState.NeedsContinue)
            assertTrue("a failed commit leaves an orphan, never a dangling reference", old.file().isFile)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun discardKeepsAcceptedFilesWhenTheStateCommitFails() {
        val fixture = Fixture()
        try {
            val persistence = MapPersistence()
            val controller = readyBoth(fixture, persistence)
            val sentence = (controller.state(AudioItem.SENTENCE) as DraftAudioState.Ready).file
            val word = (controller.state(AudioItem.VOCABULARY) as DraftAudioState.Ready).file

            persistence.failWrites = true
            assertEquals(emptyList<DraftAudioRetirement>(), controller.discard())

            assertTrue("nothing is deleted before the empty state is durable", sentence.file().isFile)
            assertTrue(word.file().isFile)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun clearDropsTheInputAndRetiresTheAcceptedFile() {
        val fixture = Fixture()
        try {
            val controller = readyBoth(fixture, MapPersistence())
            val word = (controller.state(AudioItem.VOCABULARY) as DraftAudioState.Ready).file

            assertTrue(controller.clear(AudioItem.VOCABULARY))

            assertEquals(DraftAudioState.Idle, controller.state(AudioItem.VOCABULARY))
            assertNull("a cleared item cannot be continued", controller.confirm(AudioItem.VOCABULARY))
            assertFalse("the retired recording is gone", word.file().exists())
            assertTrue(controller.state(AudioItem.SENTENCE) is DraftAudioState.Ready)
        } finally {
            fixture.close()
        }
    }
}
