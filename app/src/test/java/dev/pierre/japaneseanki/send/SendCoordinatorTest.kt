package dev.pierre.japaneseanki.send

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pierre.japaneseanki.AnkiCardLocation
import dev.pierre.japaneseanki.AnkiDeck
import dev.pierre.japaneseanki.AnkiFieldContract
import dev.pierre.japaneseanki.AnkiNoteType
import dev.pierre.japaneseanki.AudioCacheKey
import dev.pierre.japaneseanki.AudioFileOperations
import dev.pierre.japaneseanki.AudioFormat
import dev.pierre.japaneseanki.AudioFixtures
import dev.pierre.japaneseanki.AudioItem
import dev.pierre.japaneseanki.DraftAudioController
import dev.pierre.japaneseanki.DraftAudioFileStore
import dev.pierre.japaneseanki.DraftAudioInput
import dev.pierre.japaneseanki.DraftAudioPersistence
import dev.pierre.japaneseanki.ManualNoteDraft
import dev.pierre.japaneseanki.ImportedMediaReceipt
import dev.pierre.japaneseanki.MediaIdentity
import dev.pierre.japaneseanki.PreferenceChanges
import dev.pierre.japaneseanki.PreferenceFile
import dev.pierre.japaneseanki.putMediaReceipt
import dev.pierre.japaneseanki.MemoryAppPreferenceStorage
import dev.pierre.japaneseanki.MediaImportException
import dev.pierre.japaneseanki.MediaImportFailureStage
import dev.pierre.japaneseanki.MediaImportOperation
import dev.pierre.japaneseanki.PitchAccent
import dev.pierre.japaneseanki.SubmissionPhase
import dev.pierre.japaneseanki.SubmissionRecoveryKeys
import dev.pierre.japaneseanki.readFrozenSubmission
import dev.pierre.japaneseanki.TargetSpan
import dev.pierre.japaneseanki.liveMediaIdentity
import dev.pierre.japaneseanki.runAttemptForTest
import dev.pierre.japaneseanki.runSuspendTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [37])
class SendCoordinatorTest {
    private class MapPersistence : DraftAudioPersistence {
        private val values = mutableMapOf<String, String>()
        override fun read(key: String): String? = values[key]
        override fun write(changes: Map<String, String?>): Boolean {
            changes.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
            return true
        }
    }

    private class FakeSink(private val events: MutableList<String>) : AnkiNoteSink {
        val mediaOperations = mutableListOf<MediaImportOperation>()
        var duplicateId: Long? = null
        var failureOperation: MediaImportOperation? = null
        var failureStage = MediaImportFailureStage.PROVIDER_IMPORT
        var insertFailure: Exception? = null
        var insertCalls = 0
        var verifyCalls = 0
        var verifiedDeckId: Long? = null
        var verificationResult = true
        var insertedNoteId = 501L

        override fun hasDuplicate(model: AnkiNoteType, deck: AnkiDeck, fieldsByName: Map<String, String>): Long? = duplicateId
        override fun hasMatchingNoteInAnyDeck(model: AnkiNoteType, fieldsByName: Map<String, String>): Long? = duplicateId

        override fun insertNote(model: AnkiNoteType, orderedFields: List<String>, tags: Set<String>): Long {
            events += "insert"
            insertCalls++
            insertFailure?.let { throw it }
            return insertedNoteId
        }

        override fun ensureNoteInDeck(noteId: Long, deckId: Long): Boolean {
            verifyCalls++
            verifiedDeckId = deckId
            return verificationResult
        }

        override fun noteCards(noteId: Long): List<AnkiCardLocation> = listOf(AnkiCardLocation(0, 200L))

        override fun importAudio(file: File, operation: MediaImportOperation): String {
            mediaOperations += operation
            failIfRequested(operation)
            return when (operation) {
                MediaImportOperation.SENTENCE_AUDIO -> "sentence.mp3"
                MediaImportOperation.WORD_AUDIO -> "word.ogg"
                else -> error("Unexpected audio operation: $operation")
            }
        }

        override fun importProcessedImage(file: File): String {
            mediaOperations += MediaImportOperation.IMAGE
            failIfRequested(MediaImportOperation.IMAGE)
            return "capture.webp"
        }

        private fun failIfRequested(operation: MediaImportOperation) {
            if (failureOperation == operation) throw MediaImportException(operation, failureStage)
        }
    }

    private class Fixture {
        private val events = mutableListOf<String>()
        val root = kotlin.io.path.createTempDirectory("send-coordinator").toFile()
        val draftDirectory = File(root, "draft").apply { mkdirs() }
        private val stagingDirectory = File(root, "audio-staging").apply { mkdirs() }
        val storage = MemoryAppPreferenceStorage(events)
        private val persistence = MapPersistence()
        val audio = DraftAudioController(
            store = DraftAudioFileStore(
                draftDirectory,
                stagingDirectory,
                AudioFileOperations.system(),
            ),
            persistence = persistence,
            synthesize = { input, output ->
                when (input.expectedFormat) {
                    AudioFormat.WAV -> AudioFixtures.wav(output)
                    AudioFormat.OGG -> AudioFixtures.ogg(output)
                    AudioFormat.MP3 -> AudioFixtures.mp3(output)
                }
            },
        )
        val sink = FakeSink(events)
        val coordinator = SendCoordinator(
            gateway = sink,
            storage = storage,
            audio = audio,
        )

        init {
            ready(AudioItem.SENTENCE, AudioFormat.MP3, "sentence", "猫が走る")
            ready(AudioItem.VOCABULARY, AudioFormat.OGG, "word", "猫")
        }

        private fun ready(item: AudioItem, format: AudioFormat, identity: String, text: String) {
            audio.setInput(
                DraftAudioInput(
                    item = item,
                    identity = identity,
                    text = text,
                    revision = 0,
                    cacheKey = AudioCacheKey("send-test", text, "test-voice", 1f),
                    expectedFormat = format,
                ),
            )
            val attempt = requireNotNull(audio.confirm(item))
            audio.runAttemptForTest(attempt)
        }

        fun request(withImage: Boolean = false): SendRequest {
            val draft = ManualNoteDraft(
                sentence = "猫が走る",
                target = TargetSpan(0, 1),
                sentenceReading = "ねこ",
                vocabulary = "猫",
                dictionaryEntryResolved = true,
                vocabularyReading = "ねこ",
                definition = "cat",
                translation = "The cat runs.",
                pitch = PitchAccent.create("ねこ", 0),
            )
            val imageFile = if (withImage) File(draftDirectory, "capture.webp").apply {
                writeBytes(byteArrayOf(0x52, 0x49, 0x46, 0x46))
            } else null
            return SendRequest(
                draft = draft,
                fieldsByName = requireNotNull(draft.fieldsByName()),
                model = AnkiNoteType(
                    id = 100L,
                    name = "Basic",
                    fields = AnkiFieldContract.requiredFields + AnkiFieldContract.optionalFields,
                ),
                deck = AnkiDeck(200L, "Default"),
                tags = setOf("send-test"),
                pitchAccentText = "0",
                imageAttached = withImage,
                imageProcessing = false,
                processedImagePath = imageFile?.path,
                processedImageIdentity = liveMediaIdentity(imageFile),
                imageReceiptFilename = null,
            )
        }

        fun close() {
            audio.dispose()
            root.deleteRecursively()
        }

        fun eventIndex(event: String): Int = events.indexOf(event)
    }

    @Test
    fun duplicateReturnsDuplicateWithoutInserting() = withFixture { fixture ->
        fixture.sink.duplicateId = 812L

        val outcome = runSuspendTest { fixture.coordinator.submit(fixture.request()) }

        assertEquals(SendOutcome.Duplicate(812L), outcome)
        assertEquals(0, fixture.sink.insertCalls)
        assertEquals(0, fixture.sink.verifyCalls)
    }

    @Test
    fun frozenSendPhaseIsDurablyRecordedBeforeInsertSinkCall() = withFixture { fixture ->
        val outcome = runSuspendTest { fixture.coordinator.submit(fixture.request()) }
        val durableMarker = fixture.eventIndex("persist:started")
        val insert = fixture.eventIndex("insert")

        assertTrue(outcome is SendOutcome.Created)
        assertTrue(durableMarker >= 0)
        assertTrue(insert >= 0)
        assertTrue(durableMarker < insert)
    }

    @Test
    fun mediaFailureCarriesOperationAndStageAndStopsBeforeInsert() = withFixture { fixture ->
        fixture.sink.failureOperation = MediaImportOperation.WORD_AUDIO
        fixture.sink.failureStage = MediaImportFailureStage.PROVIDER_IMPORT

        val outcome = runSuspendTest { fixture.coordinator.submit(fixture.request(withImage = true)) }

        assertEquals(
            SendOutcome.MediaFailed(
                MediaImportOperation.WORD_AUDIO,
                MediaImportFailureStage.PROVIDER_IMPORT,
                "Could not complete word audio import (provider import).",
            ),
            outcome,
        )
        assertEquals(listOf(MediaImportOperation.SENTENCE_AUDIO, MediaImportOperation.WORD_AUDIO), fixture.sink.mediaOperations)
        assertEquals(0, fixture.sink.insertCalls)
        assertEquals(
            SubmissionPhase.MEDIA_IMPORTING,
            SubmissionPhase.fromPersisted(fixture.storage.draft.value.getString(SubmissionRecoveryKeys.STATUS, null)),
        )
        assertEquals(
            MediaImportOperation.WORD_AUDIO.name,
            fixture.storage.draft.value.getString(SubmissionRecoveryKeys.MEDIA_OPERATION, null),
        )
        assertEquals("sentence.mp3", readFrozenSubmission(fixture.storage.draft.value)?.sentenceAudio?.importedFilename)
    }

    @Test
    fun insertExceptionIsIndeterminateAndNeverCreated() = withFixture { fixture ->
        fixture.sink.insertFailure = IllegalStateException("provider outcome uncertain")

        val outcome = runSuspendTest { fixture.coordinator.submit(fixture.request()) }

        assertTrue(outcome is SendOutcome.Indeterminate)
        assertEquals(0, fixture.sink.verifyCalls)
        assertEquals(1, fixture.sink.insertCalls)
        assertFalse(outcome is SendOutcome.Created)
    }

    @Test
    fun destinationVerificationFailureIsNotCreated() = withFixture { fixture ->
        fixture.sink.verificationResult = false

        val outcome = runSuspendTest { fixture.coordinator.submit(fixture.request()) }

        assertEquals(SendOutcome.DestinationUnverified(501L), outcome)
        assertEquals(1, fixture.sink.insertCalls)
        assertEquals(1, fixture.sink.verifyCalls)
        assertEquals(200L, fixture.sink.verifiedDeckId)
    }

    @Test
    fun staleReceiptDoesNotBlockFreshSendOrExplicitFrozenRetry() {
        for (retryFrozen in listOf(false, true)) withFixture { fixture ->
            val request = fixture.request()
            val frozen = if (retryFrozen) {
                fixture.sink.failureOperation = MediaImportOperation.SENTENCE_AUDIO
                runSuspendTest { fixture.coordinator.submit(request) }
                fixture.sink.failureOperation = null
                requireNotNull(readFrozenSubmission(fixture.storage.draft.value))
            } else null
            fixture.storage.update(PreferenceFile.DRAFT, PreferenceChanges().putMediaReceipt(
                ImportedMediaReceipt(
                    MediaImportOperation.SENTENCE_AUDIO,
                    MediaIdentity("/draft/older-sentence.mp3", 14016L, 20L),
                    "older-sentence.mp3",
                ),
            ))
            fixture.sink.mediaOperations.clear()

            val outcome = runSuspendTest {
                fixture.coordinator.submit(request.copy(restoredFrozen = frozen))
            }

            assertEquals(SendOutcome.Created(501L), outcome)
            assertEquals(listOf(MediaImportOperation.SENTENCE_AUDIO, MediaImportOperation.WORD_AUDIO), fixture.sink.mediaOperations)
            assertEquals(1, fixture.sink.insertCalls)
            assertEquals(1, fixture.sink.verifyCalls)
        }
    }

    @Test
    fun happyPathInsertsOnceAndVerifiesDestination() = withFixture { fixture ->
        val outcome = runSuspendTest { fixture.coordinator.submit(fixture.request()) }

        assertEquals(SendOutcome.Created(501L), outcome)
        assertEquals(1, fixture.sink.insertCalls)
        assertEquals(1, fixture.sink.verifyCalls)
        assertEquals(200L, fixture.sink.verifiedDeckId)
    }

    @Test
    fun mediaOrderIsSentenceWordImageAndFailureStopsTheSequence() = withFixture { fixture ->
        val outcome = runSuspendTest { fixture.coordinator.submit(fixture.request(withImage = true)) }

        assertEquals(SendOutcome.Created(501L), outcome)
        assertEquals(
            listOf(MediaImportOperation.SENTENCE_AUDIO, MediaImportOperation.WORD_AUDIO, MediaImportOperation.IMAGE),
            fixture.sink.mediaOperations,
        )
        assertEquals(1, fixture.sink.insertCalls)
    }

    private inline fun withFixture(block: (Fixture) -> Unit) {
        val fixture = Fixture()
        try {
            block(fixture)
        } finally {
            fixture.close()
        }
    }
}
