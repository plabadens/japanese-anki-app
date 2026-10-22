package dev.pierre.japaneseanki

import java.util.Locale

/** Identifies the individual media operation, not merely its file extension. */
internal enum class MediaImportOperation(
    val kind: AnkiMediaKind,
    val label: String,
) {
    IMAGE(AnkiMediaKind.IMAGE, "image"),
    AUDIO(AnkiMediaKind.AUDIO, "audio"),
    SENTENCE_AUDIO(AnkiMediaKind.AUDIO, "sentence audio"),
    WORD_AUDIO(AnkiMediaKind.AUDIO, "word audio"),
}

internal enum class MediaImportFailureStage {
    SOURCE_VALIDATION,
    START_MARKER,
    PROVIDER_IMPORT,
    RETURNED_MEDIA_VALIDATION,
    RECEIPT_PERSISTENCE,
    INVALID_SAVED_RECEIPT,
}

/** Safe, machine-readable import failure. The cause is retained but not copied into the message. */
internal class MediaImportException(
    val operation: MediaImportOperation,
    val stage: MediaImportFailureStage,
    cause: Throwable? = null,
) : IllegalStateException(
    "Could not complete ${operation.label} import (${stage.name.lowercase(Locale.ROOT).replace('_', ' ')}).",
    cause,
)

internal data class ImportedMediaReceipt(
    val operation: MediaImportOperation,
    val source: MediaIdentity,
    val filename: String,
)

/** A verified prior receipt is optional; an invalid supplied receipt is never silently reimported. */
internal data class MediaImportRequest(
    val source: MediaIdentity,
    val existingReceipt: ImportedMediaReceipt? = null,
)

/**
 * Imports a frozen Send's media in the only supported order: sentence audio, word audio, image.
 * Each new import is marked in-flight before the provider call and receipted before the next call.
 * A failed stage stops the sequence; already persisted receipts are left intact for recovery.
 */
internal fun importSendMedia(
    sentenceAudio: MediaImportRequest?,
    wordAudio: MediaImportRequest?,
    image: MediaImportRequest?,
    markImporting: (MediaImportOperation, MediaIdentity) -> Boolean,
    import: (MediaImportOperation, MediaIdentity) -> String,
    persistReceipt: (ImportedMediaReceipt) -> Boolean,
): List<ImportedMediaReceipt> = buildList {
    fun process(operation: MediaImportOperation, request: MediaImportRequest?) {
        if (request == null) return
        if (!validMediaIdentity(request.source)) {
            throw MediaImportException(operation, MediaImportFailureStage.SOURCE_VALIDATION)
        }

        request.existingReceipt?.let { receipt ->
            if (receipt.operation != operation || receipt.source != request.source ||
                sanitizeMediaFilename(operation.kind, receipt.filename) != receipt.filename
            ) {
                throw MediaImportException(operation, MediaImportFailureStage.INVALID_SAVED_RECEIPT)
            }
            add(receipt)
            return
        }

        val marked = try {
            markImporting(operation, request.source)
        } catch (cancelled: java.util.concurrent.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            throw MediaImportException(operation, MediaImportFailureStage.START_MARKER, error)
        }
        if (!marked) throw MediaImportException(operation, MediaImportFailureStage.START_MARKER)

        val returnedFilename = try {
            import(operation, request.source)
        } catch (cancelled: java.util.concurrent.CancellationException) {
            throw cancelled
        } catch (error: MediaImportException) {
            if (error.operation == operation) throw error
            throw MediaImportException(operation, error.stage, error)
        } catch (error: Exception) {
            throw MediaImportException(operation, MediaImportFailureStage.PROVIDER_IMPORT, error)
        }
        val filename = sanitizeMediaFilename(operation.kind, returnedFilename)
        if (filename == null || filename != returnedFilename) {
            throw MediaImportException(operation, MediaImportFailureStage.RETURNED_MEDIA_VALIDATION)
        }
        val receipt = ImportedMediaReceipt(operation, request.source, filename)
        val saved = try {
            persistReceipt(receipt)
        } catch (cancelled: java.util.concurrent.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            throw MediaImportException(operation, MediaImportFailureStage.RECEIPT_PERSISTENCE, error)
        }
        if (!saved) throw MediaImportException(operation, MediaImportFailureStage.RECEIPT_PERSISTENCE)
        add(receipt)
    }

    process(MediaImportOperation.SENTENCE_AUDIO, sentenceAudio)
    process(MediaImportOperation.WORD_AUDIO, wordAudio)
    process(MediaImportOperation.IMAGE, image)
}

private fun sanitizeMediaFilename(kind: AnkiMediaKind, filename: String): String? = when (kind) {
    AnkiMediaKind.IMAGE -> AnkiMediaImporter.sanitizeFilename(filename)
    AnkiMediaKind.AUDIO -> AnkiMediaImporter.sanitizeAudioFilename(filename)
}

private fun validMediaIdentity(identity: MediaIdentity): Boolean =
    identity.path.isNotBlank() && identity.size >= 0L && identity.modified >= 0L
