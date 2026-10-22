package dev.pierre.japaneseanki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

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
