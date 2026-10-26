package dev.pierre.japaneseanki


import java.io.File
import java.util.Base64

internal enum class SubmissionPhase(val persisted: String) {
    MEDIA_IMPORTING("media_importing"),
    MEDIA_IMPORTED("media_imported"),
    NOTE_INSERTING("started"),
    NOTE_INSERTED("note_inserted"),
    DESTINATION_VERIFIED("destination_verified");

    companion object {
        fun fromPersisted(value: String?): SubmissionPhase? = entries.firstOrNull { it.persisted == value }
    }
}

internal data class MediaIdentity(
    val path: String,
    val size: Long,
    val modified: Long,
) {
    companion object {
        fun fromFile(file: File): MediaIdentity = MediaIdentity(
            path = file.canonicalPath,
            size = file.length(),
            modified = file.lastModified(),
        )
    }
}

internal data class FrozenAudioItem(
    val identity: MediaIdentity?,
    val importedFilename: String? = null,
    val omitted: Boolean = false,
)

internal data class FrozenSubmission(
    val revision: Long,
    val modelId: Long,
    val deckId: Long,
    val modelFields: List<String>,
    val orderedFields: List<String>,
    val sentence: String,
    val lemma: String,
    val vocabularyReading: String,
    val sentenceReading: String,
    val definition: String,
    val translation: String,
    val notes: String,
    val tags: Set<String>,
    val pitchAccentText: String,
    val targetStart: Int,
    val targetEnd: Int,
    val media: MediaIdentity?,
    val importedFilename: String?,
    val noteId: Long?,
    val sentenceAudio: FrozenAudioItem = FrozenAudioItem(null),
    val wordAudio: FrozenAudioItem = FrozenAudioItem(null),
)

internal object SubmissionRecoveryKeys {
    const val STATUS = "submission_status"
    const val REVISION = "pending_revision"
    const val REVISION_SEQUENCE = "submission_revision_sequence"
    const val MODEL_ID = "pending_model_id"
    const val DECK_ID = "pending_deck_id"
    const val MODEL_FIELDS = "pending_model_fields"
    const val ORDERED_FIELDS = "pending_ordered_fields"
    const val SENTENCE = "pending_sentence"
    const val LEMMA = "pending_lemma"
    const val VOCABULARY_READING = "pending_vocabulary_reading"
    const val SENTENCE_READING = "pending_sentence_reading"
    const val DEFINITION = "pending_definition"
    const val TRANSLATION = "pending_translation"
    const val NOTES = "pending_notes"
    const val TAGS = "pending_tags"
    const val PITCH = "pending_pitch"
    const val TARGET_START = "pending_target_start"
    const val TARGET_END = "pending_target_end"
    const val MEDIA_PATH = "pending_media_path"
    const val MEDIA_SIZE = "pending_media_size"
    const val MEDIA_MODIFIED = "pending_media_modified"
    const val IMPORTED_FILENAME = "pending_imported_filename"
    const val NOTE_ID = "pending_note_id"
    const val SENTENCE_AUDIO_PATH = "pending_sentence_audio_path"
    const val SENTENCE_AUDIO_SIZE = "pending_sentence_audio_size"
    const val SENTENCE_AUDIO_MODIFIED = "pending_sentence_audio_modified"
    const val SENTENCE_AUDIO_IMPORTED_FILENAME = "pending_sentence_audio_imported_filename"
    const val SENTENCE_AUDIO_OMITTED = "pending_sentence_audio_omitted"
    const val WORD_AUDIO_PATH = "pending_word_audio_path"
    const val WORD_AUDIO_SIZE = "pending_word_audio_size"
    const val WORD_AUDIO_MODIFIED = "pending_word_audio_modified"
    const val WORD_AUDIO_IMPORTED_FILENAME = "pending_word_audio_imported_filename"
    const val WORD_AUDIO_OMITTED = "pending_word_audio_omitted"
    const val MEDIA_OPERATION = "pending_media_operation"
    const val MEDIA_SOURCE_PATH = "pending_media_source_path"
    const val MEDIA_SOURCE_SIZE = "pending_media_source_size"
    const val MEDIA_SOURCE_MODIFIED = "pending_media_source_modified"
}

private data class FrozenAudioKeys(
    val path: String,
    val size: String,
    val modified: String,
    val importedFilename: String,
    val omitted: String,
)

private val sentenceAudioKeys = FrozenAudioKeys(
    SubmissionRecoveryKeys.SENTENCE_AUDIO_PATH,
    SubmissionRecoveryKeys.SENTENCE_AUDIO_SIZE,
    SubmissionRecoveryKeys.SENTENCE_AUDIO_MODIFIED,
    SubmissionRecoveryKeys.SENTENCE_AUDIO_IMPORTED_FILENAME,
    SubmissionRecoveryKeys.SENTENCE_AUDIO_OMITTED,
)
private val wordAudioKeys = FrozenAudioKeys(
    SubmissionRecoveryKeys.WORD_AUDIO_PATH,
    SubmissionRecoveryKeys.WORD_AUDIO_SIZE,
    SubmissionRecoveryKeys.WORD_AUDIO_MODIFIED,
    SubmissionRecoveryKeys.WORD_AUDIO_IMPORTED_FILENAME,
    SubmissionRecoveryKeys.WORD_AUDIO_OMITTED,
)

private fun readFrozenAudioItem(preferences: PreferenceValues, keys: FrozenAudioKeys): FrozenAudioItem {
    val path = preferences.getString(keys.path, null)
    val identity = path?.let {
        MediaIdentity(it, preferences.getLong(keys.size, -1L), preferences.getLong(keys.modified, -1L))
    }
    val filename = preferences.getString(keys.importedFilename, null)
    return FrozenAudioItem(identity, filename, preferences.getBoolean(keys.omitted, false))
}

private fun PreferenceChanges.putFrozenAudioItem(item: FrozenAudioItem, keys: FrozenAudioKeys) {
    if (item.importedFilename != null) {
        require(validMediaIdentity(item.identity)) { "Imported audio requires a complete source identity" }
        require(AnkiMediaImporter.sanitizeAudioFilename(item.importedFilename) == item.importedFilename) {
            "Unsafe imported audio filename"
        }
    }
    if (item.identity == null) remove(keys.path).remove(keys.size).remove(keys.modified)
    else putString(keys.path, item.identity.path).putLong(keys.size, item.identity.size)
        .putLong(keys.modified, item.identity.modified)
    if (item.importedFilename == null) remove(keys.importedFilename)
    else putString(keys.importedFilename, item.importedFilename)
    putBoolean(keys.omitted, item.omitted)
}

private fun validMediaIdentity(identity: MediaIdentity?): Boolean = identity != null &&
    identity.path.isNotBlank() && identity.size >= 0L && identity.modified >= 0L

/**
 * The four persisted keys of one imported-media receipt. The image slot and each audio slot own
 * a key set; the matcher never assumes which slot it is looking at.
 */
internal data class ImportedReceiptKeys(
    val path: String,
    val filename: String,
    val size: String,
    val modified: String,
)

/**
 * LIVE imported-receipt match: the imported filename to reuse, or null when the media must be
 * imported again.
 *
 * Matches only when the saved receipt and the identity computed right now are the same media —
 * same canonical path, same size, same mtime — and the saved filename survives [sanitize].
 * Pure by contract: it touches no file, so a caller that already holds the identity pays for
 * exactly one stat, and a caller with no identity matches nothing instead of silently re-stating.
 */
internal fun matchImportedReceipt(
    savedIdentity: MediaIdentity?,
    savedFilename: String?,
    currentMediaIdentity: MediaIdentity?,
    sanitize: (String) -> String?,
): String? {
    if (!validMediaIdentity(savedIdentity) || savedIdentity != currentMediaIdentity) return null
    val filename = savedFilename?.let(sanitize) ?: return null
    return filename.takeIf { it.isNotBlank() }
}

/**
 * Preferences adapter over [matchImportedReceipt], for callers that read a receipt rather than
 * hold one. It reads only the three identity values and the filename under [keys]; the caller
 * supplies [currentMediaIdentity] so this never stats. Legacy or half-written receipts resolve
 * to the -1 defaults and cannot match a real identity.
 *
 * Integrator wiring: image and audio slots pass their own [ImportedReceiptKeys] and their own
 * sanitizer ([AnkiMediaImporter.sanitizeFilename] vs. [AnkiMediaImporter.sanitizeAudioFilename]).
 */
internal fun importedReceiptFromPreferences(
    preferences: PreferenceValues,
    keys: ImportedReceiptKeys,
    currentMediaIdentity: MediaIdentity?,
    sanitize: (String) -> String?,
): String? = matchImportedReceipt(
    savedIdentity = preferences.getString(keys.path, null)?.let { path ->
        MediaIdentity(path, preferences.getLong(keys.size, -1L), preferences.getLong(keys.modified, -1L))
    },
    savedFilename = preferences.getString(keys.filename, null),
    currentMediaIdentity = currentMediaIdentity,
    sanitize = sanitize,
)

internal fun receiptIdentity(preferences: PreferenceValues, keys: ImportedReceiptKeys): MediaIdentity? =
    preferences.getString(keys.path, null)?.let { path ->
        MediaIdentity(path, preferences.getLong(keys.size, -1L), preferences.getLong(keys.modified, -1L))
    }

internal fun mediaImportRequest(
    preferences: PreferenceValues,
    keys: ImportedReceiptKeys,
    operation: MediaImportOperation,
    source: MediaIdentity,
    frozenFilename: String?,
): MediaImportRequest {
    val hasSavedReceipt = listOf(keys.path, keys.filename, keys.size, keys.modified).any(preferences::contains)
    val savedReceipt = when {
        hasSavedReceipt -> ImportedMediaReceipt(
            operation = operation,
            source = receiptIdentity(preferences, keys) ?: MediaIdentity("", -1L, -1L),
            filename = preferences.getString(keys.filename, null).orEmpty(),
        )
        frozenFilename != null -> ImportedMediaReceipt(operation, source, frozenFilename)
        else -> null
    }
    // A live receipt can outlast regenerated media or a discarded Send. Without a frozen
    // filename, a complete receipt for another source is stale, not a corrupt current receipt.
    if (frozenFilename == null && savedReceipt != null && savedReceipt.source != source) {
        val sanitize = when (operation.kind) {
            AnkiMediaKind.IMAGE -> AnkiMediaImporter::sanitizeFilename
            AnkiMediaKind.AUDIO -> AnkiMediaImporter::sanitizeAudioFilename
        }
        if (matchImportedReceipt(savedReceipt.source, savedReceipt.filename, savedReceipt.source, sanitize) != null) {
            return MediaImportRequest(source)
        }
    }
    val receipt = if (frozenFilename != null && savedReceipt != null && savedReceipt.filename != frozenFilename) {
        savedReceipt.copy(source = MediaIdentity("", -1L, -1L))
    } else savedReceipt
    return MediaImportRequest(source, receipt)
}

internal fun PreferenceChanges.putMediaReceipt(receipt: ImportedMediaReceipt): PreferenceChanges {
    val keys = when (receipt.operation) {
        MediaImportOperation.IMAGE -> IMAGE_RECEIPT_KEYS
        MediaImportOperation.SENTENCE_AUDIO -> SENTENCE_AUDIO_RECEIPT_KEYS
        MediaImportOperation.WORD_AUDIO -> WORD_AUDIO_RECEIPT_KEYS
        MediaImportOperation.AUDIO -> throw IllegalArgumentException("Generic audio is not a Send operation.")
    }
    return putString(keys.path, receipt.source.path)
        .putLong(keys.size, receipt.source.size)
        .putLong(keys.modified, receipt.source.modified)
        .putString(keys.filename, receipt.filename)
}

internal fun applyImportedReceipt(snapshot: FrozenSubmission, receipt: ImportedMediaReceipt): FrozenSubmission {
    val (fieldName, serialized, updatedSnapshot) = when (receipt.operation) {
        MediaImportOperation.IMAGE -> Triple(
            "Image", AnkiFieldSerializer.imageReference(receipt.filename),
            snapshot.copy(importedFilename = receipt.filename),
        )
        MediaImportOperation.SENTENCE_AUDIO -> Triple(
            "SentAudio", AnkiFieldSerializer.audioReference(receipt.filename),
            snapshot.copy(sentenceAudio = snapshot.sentenceAudio.copy(importedFilename = receipt.filename)),
        )
        MediaImportOperation.WORD_AUDIO -> Triple(
            "VocabAudio", AnkiFieldSerializer.audioReference(receipt.filename),
            snapshot.copy(wordAudio = snapshot.wordAudio.copy(importedFilename = receipt.filename)),
        )
        MediaImportOperation.AUDIO -> throw IllegalArgumentException("Generic audio is not a Send operation.")
    }
    val valuesByName = snapshot.modelFields.zip(snapshot.orderedFields).toMap().toMutableMap()
    require(fieldName in valuesByName && serialized != null) { "The imported media reference cannot be serialized." }
    valuesByName[fieldName] = serialized
    val mapping = AnkiFieldContract.mapToModelOrder(snapshot.modelFields, valuesByName)
    require(mapping.isValid) { mapping.errors.joinToString() }
    return updatedSnapshot.copy(orderedFields = mapping.values)
}

/**
 * Identity of a live media file for receipt matching: null when it is missing, not a regular
 * file, or cannot be stat'ed. Call once per validation phase and reuse the value.
 */
internal fun liveMediaIdentity(file: File?): MediaIdentity? {
    if (file == null || !file.isFile) return null
    return runCatching { MediaIdentity.fromFile(file) }.getOrNull()
}

/**
 * FROZEN image receipt policy, deliberately separate from the LIVE matcher above. A frozen
 * snapshot's image was already imported into AnkiDroid, so a local export that has since been
 * deleted may still be submitted; a local file that still exists must still be exactly the
 * frozen media. The receipt must name the frozen identity and the frozen filename verbatim.
 *
 * Pure: the caller reports whether the local file exists and, when it does, its identity.
 */
internal fun frozenImagePermitsImport(
    importedFilename: String?,
    frozenIdentity: MediaIdentity?,
    receiptIdentity: MediaIdentity?,
    receiptFilename: String?,
    localFileExists: Boolean,
    localIdentity: MediaIdentity?,
): Boolean = when {
    importedFilename == null -> true
    !validMediaIdentity(frozenIdentity) -> false
    receiptIdentity != frozenIdentity || receiptFilename != importedFilename -> false
    !localFileExists -> true
    else -> localIdentity == frozenIdentity
}

internal enum class AudioReceiptValidation {
    NO_RECEIPT,
    VALID,
    INVALID_RECEIPT,
    SOURCE_MISSING,
    SOURCE_CHANGED,
}

/** Validate before reusing an imported filename; every non-VALID receipt requires explicit recovery. */
internal fun validateAudioReceipt(item: FrozenAudioItem, source: File): AudioReceiptValidation {
    val filename = item.importedFilename ?: return AudioReceiptValidation.NO_RECEIPT
    if (AnkiMediaImporter.sanitizeAudioFilename(filename) != filename || !validMediaIdentity(item.identity)) {
        return AudioReceiptValidation.INVALID_RECEIPT
    }
    if (!source.exists()) return AudioReceiptValidation.SOURCE_MISSING
    val current = runCatching { MediaIdentity.fromFile(source) }.getOrNull()
        ?: return AudioReceiptValidation.SOURCE_CHANGED
    return if (current == item.identity) AudioReceiptValidation.VALID else AudioReceiptValidation.SOURCE_CHANGED
}

internal enum class NoteInsertedRecoveryDecision { COMPLETE, NEEDS_REVIEW, INDETERMINATE, FAIL }

internal fun noteInsertedRecoveryDecision(
    noteId: Long?,
    cards: List<AnkiCardLocation>?,
    deckId: Long,
): NoteInsertedRecoveryDecision = when {
    noteId == null || cards == null || cards.isEmpty() -> NoteInsertedRecoveryDecision.INDETERMINATE
    noteId <= 0L || deckId <= 0L -> NoteInsertedRecoveryDecision.FAIL
    cards.any { it.ord < 0 || it.deckId <= 0L } -> NoteInsertedRecoveryDecision.FAIL
    cards.all { it.deckId == deckId } -> NoteInsertedRecoveryDecision.COMPLETE
    else -> NoteInsertedRecoveryDecision.NEEDS_REVIEW
}

internal fun nextSubmissionRevision(currentSequence: Long, currentPendingRevision: Long): Long =
    maxOf(currentSequence, currentPendingRevision, 0L).let { current ->
        check(current < Long.MAX_VALUE) { "Submission revision limit reached" }
        current + 1L
    }

internal fun encodeSubmissionValues(values: List<String>): String = values.joinToString(".") {
    Base64.getUrlEncoder().withoutPadding().encodeToString(it.toByteArray(Charsets.UTF_8))
}

internal fun decodeSubmissionValues(encoded: String?): List<String>? {
    if (encoded == null) return null
    return runCatching {
        if (encoded.isEmpty()) emptyList() else encoded.split('.').map {
            String(Base64.getUrlDecoder().decode(it), Charsets.UTF_8)
        }
    }.getOrNull()
}

internal fun readFrozenSubmission(preferences: PreferenceValues): FrozenSubmission? {
    val revision = preferences.getLong(SubmissionRecoveryKeys.REVISION, -1L).takeIf { it >= 0 } ?: return null
    val modelId = preferences.getLong(SubmissionRecoveryKeys.MODEL_ID, -1L).takeIf { it > 0 } ?: return null
    val deckId = preferences.getLong(SubmissionRecoveryKeys.DECK_ID, -1L).takeIf { it > 0 } ?: return null
    val modelFields = decodeSubmissionValues(preferences.getString(SubmissionRecoveryKeys.MODEL_FIELDS, null))
        ?.takeIf { it.isNotEmpty() } ?: return null
    val orderedFields = decodeSubmissionValues(preferences.getString(SubmissionRecoveryKeys.ORDERED_FIELDS, null))
        ?.takeIf { it.size == modelFields.size } ?: return null
    val sentence = preferences.getString(SubmissionRecoveryKeys.SENTENCE, null) ?: return null
    val lemma = preferences.getString(SubmissionRecoveryKeys.LEMMA, null) ?: return null
    val mediaPath = preferences.getString(SubmissionRecoveryKeys.MEDIA_PATH, null)
    val media = if (mediaPath == null) {
        null
    } else {
        MediaIdentity(
            path = mediaPath,
            size = preferences.getLong(SubmissionRecoveryKeys.MEDIA_SIZE, -1L),
            modified = preferences.getLong(SubmissionRecoveryKeys.MEDIA_MODIFIED, -1L),
        )
    }
    return FrozenSubmission(
        revision = revision,
        modelId = modelId,
        deckId = deckId,
        modelFields = modelFields,
        orderedFields = orderedFields,
        sentence = sentence,
        lemma = lemma,
        vocabularyReading = preferences.getString(SubmissionRecoveryKeys.VOCABULARY_READING, "").orEmpty(),
        sentenceReading = preferences.getString(SubmissionRecoveryKeys.SENTENCE_READING, "").orEmpty(),
        definition = preferences.getString(SubmissionRecoveryKeys.DEFINITION, "").orEmpty(),
        translation = preferences.getString(SubmissionRecoveryKeys.TRANSLATION, "").orEmpty(),
        notes = preferences.getString(SubmissionRecoveryKeys.NOTES, "").orEmpty(),
        tags = decodeSubmissionValues(preferences.getString(SubmissionRecoveryKeys.TAGS, null)).orEmpty().toSet(),
        pitchAccentText = preferences.getString(SubmissionRecoveryKeys.PITCH, "").orEmpty(),
        targetStart = preferences.getInt(SubmissionRecoveryKeys.TARGET_START, -1),
        targetEnd = preferences.getInt(SubmissionRecoveryKeys.TARGET_END, -1),
        media = media,
        importedFilename = preferences.getString(SubmissionRecoveryKeys.IMPORTED_FILENAME, null),
        noteId = preferences.getLong(SubmissionRecoveryKeys.NOTE_ID, -1L).takeIf { it > 0 },
        sentenceAudio = readFrozenAudioItem(preferences, sentenceAudioKeys),
        wordAudio = readFrozenAudioItem(preferences, wordAudioKeys),
    )
}

internal fun PreferenceChanges.putFrozenSubmission(
    snapshot: FrozenSubmission,
    phase: SubmissionPhase,
): PreferenceChanges {
    putString(SubmissionRecoveryKeys.STATUS, phase.persisted)
        .putLong(SubmissionRecoveryKeys.REVISION, snapshot.revision)
        .putLong(SubmissionRecoveryKeys.MODEL_ID, snapshot.modelId)
        .putLong(SubmissionRecoveryKeys.DECK_ID, snapshot.deckId)
        .putString(SubmissionRecoveryKeys.MODEL_FIELDS, encodeSubmissionValues(snapshot.modelFields))
        .putString(SubmissionRecoveryKeys.ORDERED_FIELDS, encodeSubmissionValues(snapshot.orderedFields))
        .putString(SubmissionRecoveryKeys.SENTENCE, snapshot.sentence)
        .putString(SubmissionRecoveryKeys.LEMMA, snapshot.lemma)
        .putString(SubmissionRecoveryKeys.VOCABULARY_READING, snapshot.vocabularyReading)
        .putString(SubmissionRecoveryKeys.SENTENCE_READING, snapshot.sentenceReading)
        .putString(SubmissionRecoveryKeys.DEFINITION, snapshot.definition)
        .putString(SubmissionRecoveryKeys.TRANSLATION, snapshot.translation)
        .putString(SubmissionRecoveryKeys.NOTES, snapshot.notes)
        .putString(SubmissionRecoveryKeys.TAGS, encodeSubmissionValues(snapshot.tags.toList().sorted()))
        .putString(SubmissionRecoveryKeys.PITCH, snapshot.pitchAccentText)
        .putInt(SubmissionRecoveryKeys.TARGET_START, snapshot.targetStart)
        .putInt(SubmissionRecoveryKeys.TARGET_END, snapshot.targetEnd)
    if (snapshot.media == null) {
        remove(SubmissionRecoveryKeys.MEDIA_PATH)
            .remove(SubmissionRecoveryKeys.MEDIA_SIZE)
            .remove(SubmissionRecoveryKeys.MEDIA_MODIFIED)
    } else {
        putString(SubmissionRecoveryKeys.MEDIA_PATH, snapshot.media.path)
            .putLong(SubmissionRecoveryKeys.MEDIA_SIZE, snapshot.media.size)
            .putLong(SubmissionRecoveryKeys.MEDIA_MODIFIED, snapshot.media.modified)
    }
    if (snapshot.importedFilename == null) remove(SubmissionRecoveryKeys.IMPORTED_FILENAME)
    else putString(SubmissionRecoveryKeys.IMPORTED_FILENAME, snapshot.importedFilename)
    if (snapshot.noteId == null) remove(SubmissionRecoveryKeys.NOTE_ID)
    else putLong(SubmissionRecoveryKeys.NOTE_ID, snapshot.noteId)
    putFrozenAudioItem(snapshot.sentenceAudio, sentenceAudioKeys)
    putFrozenAudioItem(snapshot.wordAudio, wordAudioKeys)
    return this
}

internal fun clearFrozenSubmission(): PreferenceChanges = PreferenceChanges()
        .remove(SubmissionRecoveryKeys.STATUS)
        .remove(SubmissionRecoveryKeys.REVISION)
        .remove(SubmissionRecoveryKeys.MODEL_ID)
        .remove(SubmissionRecoveryKeys.DECK_ID)
        .remove(SubmissionRecoveryKeys.MODEL_FIELDS)
        .remove(SubmissionRecoveryKeys.ORDERED_FIELDS)
        .remove(SubmissionRecoveryKeys.SENTENCE)
        .remove(SubmissionRecoveryKeys.LEMMA)
        .remove(SubmissionRecoveryKeys.VOCABULARY_READING)
        .remove(SubmissionRecoveryKeys.SENTENCE_READING)
        .remove(SubmissionRecoveryKeys.DEFINITION)
        .remove(SubmissionRecoveryKeys.TRANSLATION)
        .remove(SubmissionRecoveryKeys.NOTES)
        .remove(SubmissionRecoveryKeys.TAGS)
        .remove(SubmissionRecoveryKeys.PITCH)
        .remove(SubmissionRecoveryKeys.TARGET_START)
        .remove(SubmissionRecoveryKeys.TARGET_END)
        .remove(SubmissionRecoveryKeys.MEDIA_PATH)
        .remove(SubmissionRecoveryKeys.MEDIA_SIZE)
        .remove(SubmissionRecoveryKeys.MEDIA_MODIFIED)
        .remove(SubmissionRecoveryKeys.IMPORTED_FILENAME)
        .remove(SubmissionRecoveryKeys.NOTE_ID)
        .remove(SubmissionRecoveryKeys.SENTENCE_AUDIO_PATH)
        .remove(SubmissionRecoveryKeys.SENTENCE_AUDIO_SIZE)
        .remove(SubmissionRecoveryKeys.SENTENCE_AUDIO_MODIFIED)
        .remove(SubmissionRecoveryKeys.SENTENCE_AUDIO_IMPORTED_FILENAME)
        .remove(SubmissionRecoveryKeys.SENTENCE_AUDIO_OMITTED)
        .remove(SubmissionRecoveryKeys.WORD_AUDIO_PATH)
        .remove(SubmissionRecoveryKeys.WORD_AUDIO_SIZE)
        .remove(SubmissionRecoveryKeys.WORD_AUDIO_MODIFIED)
        .remove(SubmissionRecoveryKeys.WORD_AUDIO_IMPORTED_FILENAME)
        .remove(SubmissionRecoveryKeys.WORD_AUDIO_OMITTED)
        .remove(SubmissionRecoveryKeys.MEDIA_OPERATION)
        .remove(SubmissionRecoveryKeys.MEDIA_SOURCE_PATH)
        .remove(SubmissionRecoveryKeys.MEDIA_SOURCE_SIZE)
        .remove(SubmissionRecoveryKeys.MEDIA_SOURCE_MODIFIED)
