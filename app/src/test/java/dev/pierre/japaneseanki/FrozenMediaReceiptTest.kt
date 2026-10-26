package dev.pierre.japaneseanki

import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FrozenMediaReceiptTest {
    private val modelFields = AnkiFieldContract.requiredFields + AnkiFieldContract.optionalFields

    private fun snapshot(): FrozenSubmission {
        val sentence = MediaIdentity("/draft/sentence.mp3", 10L, 20L)
        val word = MediaIdentity("/draft/word.ogg", 11L, 21L)
        val image = MediaIdentity("/draft/image.webp", 12L, 22L)
        val values = modelFields.associateWith { "original:$it" }
        val ordered = AnkiFieldContract.mapToModelOrder(modelFields, values)
        return FrozenSubmission(
            revision = 1L,
            modelId = 2L,
            deckId = 3L,
            modelFields = modelFields,
            orderedFields = ordered.values,
            sentence = "今日",
            lemma = "今日",
            vocabularyReading = "きょう",
            sentenceReading = "きょう",
            definition = "today",
            translation = "today",
            notes = "note",
            tags = setOf("tag"),
            pitchAccentText = "0",
            targetStart = 0,
            targetEnd = 2,
            media = image,
            importedFilename = null,
            noteId = null,
            sentenceAudio = FrozenAudioItem(sentence),
            wordAudio = FrozenAudioItem(word),
        )
    }

    @Test
    fun receiptForOlderMediaDoesNotBlockAnUnimportedSnapshot() {
        for ((operation, keys, filename) in listOf(
            Triple(MediaImportOperation.SENTENCE_AUDIO, SENTENCE_AUDIO_RECEIPT_KEYS, "old.mp3"),
            Triple(MediaImportOperation.WORD_AUDIO, WORD_AUDIO_RECEIPT_KEYS, "old.ogg"),
            Triple(MediaImportOperation.IMAGE, IMAGE_RECEIPT_KEYS, "old.webp"),
        )) {
            val oldSource = MediaIdentity("/draft/$filename", 10L, 20L)
            val preferences = PreferenceValues().changed(PreferenceChanges().putMediaReceipt(
                ImportedMediaReceipt(operation, oldSource, filename),
            ))
            for (source in listOf(oldSource.copy(path = "/draft/new-$filename"), oldSource.copy(modified = 21L))) {
                val request = mediaImportRequest(preferences, keys, operation, source, frozenFilename = null)
                assertEquals(MediaImportRequest(source), request)
            }
        }
    }

    @Test
    fun matchingReceiptIsReusedEvenBeforeItsFilenameIsFrozen() {
        val receipt = ImportedMediaReceipt(
            MediaImportOperation.SENTENCE_AUDIO, MediaIdentity("/draft/sentence.mp3", 10L, 20L), "sentence.mp3",
        )
        val preferences = PreferenceValues().changed(PreferenceChanges().putMediaReceipt(receipt))
        val request = mediaImportRequest(
            preferences, SENTENCE_AUDIO_RECEIPT_KEYS, receipt.operation, receipt.source, frozenFilename = null,
        )
        assertEquals(receipt, request.existingReceipt)
    }

    @Test
    fun conflictingFrozenReceiptAndIncompleteSavedReceiptStillRequireRecovery() {
        val source = MediaIdentity("/draft/sentence.mp3", 10L, 20L)
        val receipt = ImportedMediaReceipt(MediaImportOperation.SENTENCE_AUDIO, source, "sentence.mp3")
        val complete = PreferenceValues().changed(PreferenceChanges().putMediaReceipt(receipt))
        val incomplete = PreferenceValues().changed(PreferenceChanges().putString(SENTENCE_AUDIO_RECEIPT_FILENAME, "sentence.mp3"))
        val requests = listOf(
            mediaImportRequest(complete, SENTENCE_AUDIO_RECEIPT_KEYS, receipt.operation, source.copy(modified = 21L), "sentence.mp3"),
            mediaImportRequest(complete, SENTENCE_AUDIO_RECEIPT_KEYS, receipt.operation, source, "different.mp3"),
            mediaImportRequest(incomplete, SENTENCE_AUDIO_RECEIPT_KEYS, receipt.operation, source, null),
        )
        for (request in requests) {
            val error = org.junit.Assert.assertThrows(MediaImportException::class.java) {
                importSendMedia(request, null, null, { _, _ -> error("Must not mark import") },
                    { _, _ -> error("Must not import") }, { error("Must not save receipt") })
            }
            assertEquals(MediaImportFailureStage.INVALID_SAVED_RECEIPT, error.stage)
        }
    }

    @Test
    fun sentenceAudioReceiptUpdatesOnlyItsFrozenFieldAndIdentity() {
        val original = snapshot()
        val source = requireNotNull(original.sentenceAudio.identity)
        val updated = applyImportedReceipt(
            original,
            ImportedMediaReceipt(MediaImportOperation.SENTENCE_AUDIO, source, "sentence_clip.mp3"),
        )

        assertEquals("[sound:sentence_clip.mp3]", updated.modelFields.zip(updated.orderedFields).toMap()["SentAudio"])
        assertEquals("sentence_clip.mp3", updated.sentenceAudio.importedFilename)
        assertEquals(original.orderedFields[original.modelFields.indexOf("VocabAudio")], updated.orderedFields[updated.modelFields.indexOf("VocabAudio")])
        assertNotSame(original, updated)
    }

    @Test
    fun imageReceiptIsSerializedIntoFrozenFieldsWithoutChangingSourceIdentity() {
        val original = snapshot()
        val updated = applyImportedReceipt(
            original,
            ImportedMediaReceipt(MediaImportOperation.IMAGE, requireNotNull(original.media), "capture.webp"),
        )

        assertEquals("<img src=\"capture.webp\">", updated.modelFields.zip(updated.orderedFields).toMap()["Image"])
        assertEquals("capture.webp", updated.importedFilename)
        assertEquals(original.media, updated.media)
    }
}
