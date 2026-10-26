package dev.pierre.japaneseanki

import android.content.Intent
import android.net.Uri

internal const val ANKIDROID_READ_WRITE_PERMISSION = "com.ichi2.anki.permission.READ_WRITE_DATABASE"
internal const val IMAGE_RECEIPT_PATH = "image_import_receipt_path"
internal const val IMAGE_RECEIPT_FILENAME = "image_import_receipt_filename"
internal const val IMAGE_RECEIPT_SIZE = "image_import_receipt_size"
internal const val IMAGE_RECEIPT_MODIFIED = "image_import_receipt_modified"
internal const val SENTENCE_AUDIO_RECEIPT_PATH = "sentence_audio_receipt_path"
internal const val SENTENCE_AUDIO_RECEIPT_FILENAME = "sentence_audio_receipt_filename"
internal const val SENTENCE_AUDIO_RECEIPT_SIZE = "sentence_audio_receipt_size"
internal const val SENTENCE_AUDIO_RECEIPT_MODIFIED = "sentence_audio_receipt_modified"
internal const val WORD_AUDIO_RECEIPT_PATH = "word_audio_receipt_path"
internal const val WORD_AUDIO_RECEIPT_FILENAME = "word_audio_receipt_filename"
internal const val WORD_AUDIO_RECEIPT_SIZE = "word_audio_receipt_size"
internal const val WORD_AUDIO_RECEIPT_MODIFIED = "word_audio_receipt_modified"
internal val IMAGE_RECEIPT_KEYS = ImportedReceiptKeys(IMAGE_RECEIPT_PATH, IMAGE_RECEIPT_FILENAME, IMAGE_RECEIPT_SIZE, IMAGE_RECEIPT_MODIFIED)
internal val SENTENCE_AUDIO_RECEIPT_KEYS = ImportedReceiptKeys(SENTENCE_AUDIO_RECEIPT_PATH, SENTENCE_AUDIO_RECEIPT_FILENAME, SENTENCE_AUDIO_RECEIPT_SIZE, SENTENCE_AUDIO_RECEIPT_MODIFIED)
internal val WORD_AUDIO_RECEIPT_KEYS = ImportedReceiptKeys(WORD_AUDIO_RECEIPT_PATH, WORD_AUDIO_RECEIPT_FILENAME, WORD_AUDIO_RECEIPT_SIZE, WORD_AUDIO_RECEIPT_MODIFIED)

internal fun ankiBrowserUriText(noteId: Long): String {
    require(noteId > 0)
    return "anki://x-callback-url/browser?search=nid%3A$noteId"
}
internal fun ankiBrowserUri(noteId: Long): Uri = Uri.parse(ankiBrowserUriText(noteId))
internal fun ankiBrowserIntent(noteId: Long): Intent = Intent(Intent.ACTION_VIEW, ankiBrowserUri(noteId)).setPackage("com.ichi2.anki")

internal fun draftCallbackIsCurrent(callbackGeneration: Long, currentGeneration: Long, locked: Boolean): Boolean =
    !locked && callbackGeneration == currentGeneration
internal fun ocrResultIsCurrent(callbackGeneration: Long, currentGeneration: Long, callbackEditorRevision: Long, currentEditorRevision: Long, locked: Boolean): Boolean =
    draftCallbackIsCurrent(callbackGeneration, currentGeneration, locked) && callbackEditorRevision == currentEditorRevision
internal fun intentPayloadMatches(currentText: String?, currentImage: String?, expectedText: String?, expectedImage: String?): Boolean =
    currentText == expectedText && currentImage == expectedImage
internal fun initialDraftTags(draftTags: String?, lastNoteTags: String?): String = draftTags ?: lastNoteTags ?: ""
