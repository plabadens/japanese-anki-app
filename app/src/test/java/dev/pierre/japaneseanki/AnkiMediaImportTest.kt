package dev.pierre.japaneseanki

import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnkiMediaImportTest {
    @Test
    fun sendImportsAudioBeforeImageAndPersistsEachReceiptBeforeProceeding() {
        val sentence = MediaImportRequest(MediaIdentity("/draft/sentence.wav", 3L, 10L))
        val word = MediaImportRequest(MediaIdentity("/draft/word.ogg", 4L, 11L))
        val image = MediaImportRequest(MediaIdentity("/draft/image.webp", 5L, 12L))
        val events = mutableListOf<String>()
        val receipts = mutableListOf<ImportedMediaReceipt>()

        val result = importSendMedia(
            sentenceAudio = sentence,
            wordAudio = word,
            image = image,
            markImporting = { operation, _ -> events += "begin:$operation"; true },
            import = { operation, _ ->
                events += "import:$operation"
                when (operation) {
                    MediaImportOperation.SENTENCE_AUDIO -> "sentence.wav"
                    MediaImportOperation.WORD_AUDIO -> "word.ogg"
                    MediaImportOperation.IMAGE -> "image.webp"
                    else -> error("Unexpected operation: $operation")
                }
            },
            persistReceipt = { receipt ->
                events += "receipt:${receipt.operation}"
                receipts += receipt
                true
            },
        )

        assertEquals(
            listOf(
                "begin:SENTENCE_AUDIO", "import:SENTENCE_AUDIO", "receipt:SENTENCE_AUDIO",
                "begin:WORD_AUDIO", "import:WORD_AUDIO", "receipt:WORD_AUDIO",
                "begin:IMAGE", "import:IMAGE", "receipt:IMAGE",
            ),
            events,
        )
        assertEquals(receipts, result)
        assertEquals(listOf("sentence.wav", "word.ogg", "image.webp"), result.map { it.filename })
    }

    @Test
    fun providerFailureKeepsPriorReceiptAndStopsBeforeLaterMedia() {
        val saved = mutableListOf<ImportedMediaReceipt>()
        val exception = assertThrows(MediaImportException::class.java) {
            importSendMedia(
                sentenceAudio = MediaImportRequest(MediaIdentity("/draft/sentence.wav", 3L, 10L)),
                wordAudio = MediaImportRequest(MediaIdentity("/draft/word.ogg", 4L, 11L)),
                image = MediaImportRequest(MediaIdentity("/draft/image.webp", 5L, 12L)),
                markImporting = { _, _ -> true },
                import = { operation, _ ->
                    if (operation == MediaImportOperation.WORD_AUDIO) error("provider details stay in cause")
                    "sentence.wav"
                },
                persistReceipt = { saved += it; true },
            )
        }

        assertEquals(MediaImportOperation.WORD_AUDIO, exception.operation)
        assertEquals(MediaImportFailureStage.PROVIDER_IMPORT, exception.stage)
        assertEquals(listOf("sentence.wav"), saved.map { it.filename })
        assertEquals("Could not complete word audio import (provider import).", exception.message)
    }

    @Test
    fun receiptFailureStopsLaterImportsWithoutDiscardingEarlierReceipt() {
        val saved = mutableListOf<ImportedMediaReceipt>()
        val imported = mutableListOf<MediaImportOperation>()
        val exception = assertThrows(MediaImportException::class.java) {
            importSendMedia(
                sentenceAudio = MediaImportRequest(MediaIdentity("/draft/sentence.wav", 3L, 10L)),
                wordAudio = MediaImportRequest(MediaIdentity("/draft/word.ogg", 4L, 11L)),
                image = MediaImportRequest(MediaIdentity("/draft/image.webp", 5L, 12L)),
                markImporting = { _, _ -> true },
                import = { operation, _ ->
                    imported += operation
                    when (operation) {
                        MediaImportOperation.SENTENCE_AUDIO -> "sentence.wav"
                        MediaImportOperation.WORD_AUDIO -> "word.ogg"
                        else -> "image.webp"
                    }
                },
                persistReceipt = { receipt ->
                    if (receipt.operation == MediaImportOperation.WORD_AUDIO) false
                    else { saved += receipt; true }
                },
            )
        }

        assertEquals(MediaImportOperation.WORD_AUDIO, exception.operation)
        assertEquals(MediaImportFailureStage.RECEIPT_PERSISTENCE, exception.stage)
        assertEquals(listOf(MediaImportOperation.SENTENCE_AUDIO, MediaImportOperation.WORD_AUDIO), imported)
        assertEquals(listOf(MediaImportOperation.SENTENCE_AUDIO), saved.map { it.operation })
    }

    @Test
    fun beginMarkerFailureDoesNotCallProvider() {
        var providerCalled = false
        val exception = assertThrows(MediaImportException::class.java) {
            importSendMedia(
                sentenceAudio = MediaImportRequest(MediaIdentity("/draft/sentence.wav", 3L, 10L)),
                wordAudio = null,
                image = null,
                markImporting = { _, _ -> false },
                import = { _, _ -> providerCalled = true; "sentence.wav" },
                persistReceipt = { true },
            )
        }
        assertEquals(MediaImportFailureStage.START_MARKER, exception.stage)
        assertEquals(MediaImportOperation.SENTENCE_AUDIO, exception.operation)
        assertEquals(false, providerCalled)
    }

    @Test
    fun imageReturnCannotBeSatisfiedByAnAudioFilename() {
        val exception = assertThrows(MediaImportException::class.java) {
            importSendMedia(
                sentenceAudio = null,
                wordAudio = null,
                image = MediaImportRequest(MediaIdentity("/draft/image.webp", 5L, 12L)),
                markImporting = { _, _ -> true },
                import = { _, _ -> "voice.mp3" },
                persistReceipt = { true },
            )
        }
        assertEquals(MediaImportOperation.IMAGE, exception.operation)
        assertEquals(MediaImportFailureStage.RETURNED_MEDIA_VALIDATION, exception.stage)
    }

    @Test
    fun invalidExistingReceiptIsNotSilentlyReimported() {
        val request = MediaImportRequest(
            source = MediaIdentity("/draft/sentence.wav", 3L, 10L),
            existingReceipt = ImportedMediaReceipt(
                operation = MediaImportOperation.SENTENCE_AUDIO,
                source = MediaIdentity("/draft/other.wav", 3L, 10L),
                filename = "other.wav",
            ),
        )
        var providerCalled = false
        val exception = assertThrows(MediaImportException::class.java) {
            importSendMedia(
                sentenceAudio = request,
                wordAudio = null,
                image = null,
                markImporting = { _, _ -> true },
                import = { _, _ -> providerCalled = true; "sentence.wav" },
                persistReceipt = { true },
            )
        }
        assertEquals(MediaImportFailureStage.INVALID_SAVED_RECEIPT, exception.stage)
        assertEquals(false, providerCalled)
    }
}
