package dev.pierre.japaneseanki

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioGenerationTest {
    // Device gate: cancellation and serialization require an instrumented test with a real TTS engine.
    @Test fun cacheKeyIncludesProviderTextVoiceAndRateWithoutExposingTextInFilename() {
        val base = AudioCacheKey("azure", "猫", "ja-JP-NanamiNeural", 1f)
        assertNotEquals(base.filename(), base.copy(source = "android:engine").filename())
        assertNotEquals(base.filename(), base.copy(text = "犬").filename())
        assertNotEquals(base.filename(), base.copy(voice = "other").filename())
        assertNotEquals(base.filename(), base.copy(rate = 1.1f).filename())
        assertFalse(base.filename().contains("猫"))
        assertEquals(base.filename(), base.copy().filename())
        assertNotEquals(base.filename(), base.copy(entryIdentity = "entry-a").filename())
        assertNotEquals(base.copy(entryIdentity = "entry-a").filename(), base.copy(entryIdentity = "entry-b").filename())
    }

    @Test fun cacheEvictsLeastRecentlyUsedReusableFilesAndKeepsDraftCopyAfterClear() {
        val root = Files.createTempDirectory(java.nio.file.Path.of("build"), "audio-cache-test").toFile()
        try {
            val cacheDir = File(root, "cache")
            val draftDir = File(root, "draft")
            val cache = ReusableAudioCache(cacheDir, maxBytes = 92)
            val first = AudioCacheKey("android:e", "一", "voice", 1f)
            val second = AudioCacheKey("android:e", "二", "voice", 1f)
            val third = AudioCacheKey("android:e", "三", "voice", 1f)
            assertTrue(cache.put(first, writeValidWav(File(root, "first"))) != null)
            Thread.sleep(5)
            assertTrue(cache.put(second, writeValidWav(File(root, "second"))) != null)
            Thread.sleep(5)
            assertTrue(cache.get(first) != null) // first is now more recent
            val activeDraft = cache.copyToActiveDraft(first, draftDir, "sentence.wav")
            assertTrue(cache.put(third, writeValidWav(File(root, "third"))) != null)
            assertNull(cache.get(second))
            assertTrue(cache.sizeBytes() <= 92)
            cache.clear()
            assertEquals(0L, cache.sizeBytes())
            assertTrue(activeDraft.isFile)
            assertTrue(AudioFileValidator.requireWav(activeDraft).isFile)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun oversizedFileIsNotCached() {
        val root = Files.createTempDirectory(java.nio.file.Path.of("build"), "audio-cache-limit").toFile()
        try {
            val cache = ReusableAudioCache(File(root, "cache"), maxBytes = 2)
            assertNull(cache.put(AudioCacheKey("source", "text", "voice", 1f), writeValidWav(File(root, "large"))))
            assertEquals(0L, cache.sizeBytes())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun obsoleteAudioCompletionCannotReplaceNewRevisionAndFailureCanBeRetriedExplicitly() {
        val root = Files.createTempDirectory(java.nio.file.Path.of("build"), "audio-revision-state").toFile()
        try {
            val result = RevisionedAudioResult()
            val oldRequest = AudioRequestIdentity(AudioItem.SENTENCE, 0, "猫です")
            val currentRequest = AudioRequestIdentity(AudioItem.SENTENCE, 1, "犬です")
            result.begin(oldRequest)
            result.begin(currentRequest)
            assertFalse(result.beginIfNeeded(oldRequest)) // a delayed automatic request cannot regress the revision
            assertFalse(result.complete(oldRequest, writeValidWav(File(root, "old.wav"))))
            assertTrue(result.fail(currentRequest, retryable = true))
            assertTrue(result.state is AudioGenerationState.Failed)
            assertTrue(result.retry(currentRequest))
            val ready = writeValidWav(File(root, "new.wav"))
            assertTrue(result.complete(currentRequest, ready))
            assertEquals(ready.name, (result.state as AudioGenerationState.Ready).file.name)
        } finally { root.deleteRecursively() }
    }

    @Test fun incompleteAudioCannotEnterReadyState() {
        val result = RevisionedAudioResult()
        val request = AudioRequestIdentity(AudioItem.SENTENCE, 0, "猫です")
        result.begin(request)
        assertFalse(result.complete(request, File("missing.wav")))
        assertTrue(result.state is AudioGenerationState.Failed)
    }

    @Test fun vocabularyUsesRecordingThenAzureThenOfflineAndSentenceSkipsRecording() {
        val root = Files.createTempDirectory(java.nio.file.Path.of("build"), "audio-chain-order").toFile()
        try {
            val calls = mutableListOf<String>()
            fun binding(source: AudioSource, fails: Boolean): AudioProviderBinding = AudioProviderBinding(
                AudioSourceIdentity(source, source.name, engine = "engine", voice = "voice", rate = 1f),
                AudioProvider { _, output ->
                    calls += source.name
                    if (fails) throw IllegalStateException("unavailable")
                    writeValidWav(output)
                },
            )
            val chain = AudioSourceChain(
                AudioSourceConfiguration(
                    recordedPronunciationEnabled = true,
                    recordedSourceIdentityVerified = true,
                    recordedRightsVerified = true,
                    azureConfigured = true,
                ),
                binding(AudioSource.RECORDED_PRONUNCIATION, true),
                binding(AudioSource.AZURE, false),
                binding(AudioSource.ANDROID_OFFLINE, false),
            )
            val vocabulary = chain.generate(AudioSynthesisRequest(AudioItem.VOCABULARY, "猫", 0), File(root, "word"))
            assertEquals(listOf("RECORDED_PRONUNCIATION", "AZURE"), calls)
            assertTrue(vocabulary is AudioChainResult.Ready && vocabulary.source == AudioSource.AZURE)
            calls.clear()
            val sentence = chain.generate(AudioSynthesisRequest(AudioItem.SENTENCE, "猫です", 0), File(root, "sentence"))
            assertEquals(listOf("AZURE"), calls)
            assertTrue(sentence is AudioChainResult.Ready)
        } finally { root.deleteRecursively() }
    }

    @Test fun azureFailureFallsBackToOfflineProvider() {
        val root = Files.createTempDirectory(java.nio.file.Path.of("build"), "audio-offline-fallback").toFile()
        try {
            val calls = mutableListOf<AudioSource>()
            fun binding(source: AudioSource, fails: Boolean) = AudioProviderBinding(
                AudioSourceIdentity(source, source.name, voice = "voice", rate = 1f),
                AudioProvider { _, output ->
                    calls += source
                    if (fails) throw IllegalStateException()
                    writeValidWav(output)
                },
            )
            val chain = AudioSourceChain(
                AudioSourceConfiguration(azureConfigured = true), null,
                binding(AudioSource.AZURE, true), binding(AudioSource.ANDROID_OFFLINE, false),
            )
            val result = chain.generate(AudioSynthesisRequest(AudioItem.SENTENCE, "text", 2), root)
            assertEquals(listOf(AudioSource.AZURE, AudioSource.ANDROID_OFFLINE), calls)
            assertTrue(result is AudioChainResult.Ready && result.source == AudioSource.ANDROID_OFFLINE)
        } finally { root.deleteRecursively() }
    }

    @Test fun recordingsAreDisabledByDefaultAndCacheSeparatesEngine() {
        assertFalse(AudioSourceConfiguration().recordedPronunciationEnabled)
        val base = AudioCacheKey("provider", "text", "voice", 1f, engine = "engine-one")
        assertNotEquals(base, base.copy(engine = "engine-two"))
    }

    @Test fun recordingIsIneligibleWithoutVerifiedSourceRightsAndResolvedEntry() {
        val root = Files.createTempDirectory(java.nio.file.Path.of("build"), "audio-recording-gate").toFile()
        try {
            var calls = 0
            val recording = AudioProviderBinding(
                AudioSourceIdentity(AudioSource.RECORDED_PRONUNCIATION, "NHK-2016", voice = "recording", rate = 1f),
                AudioProvider { _, output -> calls++; writeValidWav(output) },
            )
            val unverified = AudioSourceChain(
                AudioSourceConfiguration(recordedPronunciationEnabled = true, azureConfigured = false),
                recording, null, null,
            )
            assertTrue(unverified.generate(AudioSynthesisRequest(AudioItem.VOCABULARY, "猫", 1, "entry"), root).let { it is AudioChainResult.Failed })
            val verified = AudioSourceChain(
                AudioSourceConfiguration(
                    recordedPronunciationEnabled = true,
                    recordedSourceIdentityVerified = true,
                    recordedRightsVerified = true,
                ),
                recording, null, null,
            )
            assertTrue(verified.generate(AudioSynthesisRequest(AudioItem.VOCABULARY, "猫", 2), root).let { it is AudioChainResult.Failed })
            assertEquals(0, calls)
            assertTrue(verified.generate(AudioSynthesisRequest(AudioItem.VOCABULARY, "猫", 3, "entry"), root) is AudioChainResult.Ready)
            assertEquals(1, calls)
        } finally { root.deleteRecursively() }
    }

    @Test fun allUnavailableReturnsFailureAndUnchangedReadyRequestDoesNotRestart() {
        val root = Files.createTempDirectory(java.nio.file.Path.of("build"), "audio-chain-failure").toFile()
        try {
            val chain = AudioSourceChain(AudioSourceConfiguration(), null, null, null)
            assertTrue(chain.generate(AudioSynthesisRequest(AudioItem.SENTENCE, "text", 1), root) is AudioChainResult.Failed)
            val result = RevisionedAudioResult()
            val request = AudioRequestIdentity(AudioItem.SENTENCE, 1, "text")
            assertTrue(result.beginIfNeeded(request))
            assertTrue(result.complete(request, source(root, "ready.wav", "wav")))
            assertFalse(result.beginIfNeeded(request))
            assertFalse(result.retry(request))
            val failedRequest = AudioRequestIdentity(AudioItem.SENTENCE, 2, "changed")
            assertTrue(result.beginIfNeeded(failedRequest))
            assertTrue(result.fail(failedRequest, true))
            assertTrue(result.retry(failedRequest))
            val interrupted = AudioRequestIdentity(AudioItem.SENTENCE, 3, "billable")
            assertFalse(result.beginIfNeeded(interrupted, afterProcessRestart = true))
            assertTrue(result.state is AudioGenerationState.Failed)
            assertFalse(result.beginIfNeeded(interrupted))
            assertTrue(result.retry(interrupted))
            val notRetryable = AudioRequestIdentity(AudioItem.SENTENCE, 4, "terminal")
            assertTrue(result.beginIfNeeded(notRetryable))
            assertTrue(result.fail(notRetryable, retryable = false))
            assertFalse(result.retry(notRetryable))
        } finally { root.deleteRecursively() }
    }


    @Test fun cachedRecordingIsUsedBeforeAvailabilityAndEntryIdentitySeparatesRecordings() {
        val root = Files.createTempDirectory(java.nio.file.Path.of("build"), "audio-cache-hit").toFile()
        try {
            val cache = ReusableAudioCache(File(root, "cache"))
            val identity = AudioSourceIdentity(AudioSource.RECORDED_PRONUNCIATION, "nhk", voice = "recorded", rate = 1f)
            val key = AudioCacheKey("RECORDED_PRONUNCIATION:nhk", "猫", "recorded", 1f, entryIdentity = "entry-1")
            cache.put(key, writeValidWav(File(root, "seed.wav")))
            var called = false
            val chain = AudioSourceChain(
                AudioSourceConfiguration(
                    recordedPronunciationEnabled = true,
                    recordedSourceIdentityVerified = true,
                    recordedRightsVerified = true,
                ),
                AudioProviderBinding(identity, AudioProvider { _, _ -> called = true; error("must use cache") }, available = { false }), null, null, cache,
            )
            val result = chain.generate(AudioSynthesisRequest(AudioItem.VOCABULARY, "猫", 0, "entry-1"), File(root, "draft"))
            assertTrue(result is AudioChainResult.Ready)
            assertFalse(called)
            assertTrue(chain.generate(AudioSynthesisRequest(AudioItem.VOCABULARY, "猫", 1, "entry-2"), File(root, "draft2")) is AudioChainResult.Failed)
        } finally { root.deleteRecursively() }
    }

    @Test fun invalidWavFormatIsRejectedAndDraftCopyCannotTargetCacheRoot() {
        val root = Files.createTempDirectory(java.nio.file.Path.of("build"), "audio-wav-validation").toFile()
        try {
            val invalid = writeValidWav(File(root, "invalid.wav"))
            val bytes = invalid.readBytes()
            // Corrupt the block alignment field in the fmt descriptor.
            bytes[32] = 4
            bytes[33] = 0
            invalid.writeBytes(bytes)
            assertTrue(runCatching { AudioFileValidator.requireWav(invalid) }.isFailure)
            val cache = ReusableAudioCache(File(root, "cache"))
            assertTrue(runCatching { cache.put(AudioCacheKey("s", "t", "v", 1f), invalid) }.isFailure)
            assertTrue(runCatching { cache.copyToActiveDraft(AudioCacheKey("s", "t", "v", 1f), File(root, "cache"), "draft.wav") }.isFailure)
        } finally { root.deleteRecursively() }
    }

    private fun writeValidWav(file: File): File {
        val dataSize = 2
        val bytes = java.nio.ByteBuffer.allocate(46).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()).putInt(38).put("WAVE".toByteArray())
        bytes.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(8000)
        bytes.putInt(16000).putShort(2).putShort(16)
        bytes.put("data".toByteArray()).putInt(dataSize).putShort(0)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes.array())
        return file
    }

    private fun source(root: File, name: String, content: String): File = File(root, name).apply { writeText(content) }
}
