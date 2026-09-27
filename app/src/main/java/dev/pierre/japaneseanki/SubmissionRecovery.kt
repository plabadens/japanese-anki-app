package dev.pierre.japaneseanki

import android.content.SharedPreferences
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
}

internal enum class NoteInsertedRecoveryDecision { MOVE, COMPLETE, RESOLVE, FAIL }

internal fun noteInsertedRecoveryDecision(
    noteId: Long?,
    cards: List<AnkiCardLocation>?,
    deckId: Long,
): NoteInsertedRecoveryDecision = when {
    noteId == null || cards == null || cards.isEmpty() -> NoteInsertedRecoveryDecision.RESOLVE
    deckId <= 0L -> NoteInsertedRecoveryDecision.FAIL
    cards.all { it.deckId == deckId } -> NoteInsertedRecoveryDecision.COMPLETE
    else -> NoteInsertedRecoveryDecision.MOVE
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

internal fun readFrozenSubmission(preferences: SharedPreferences): FrozenSubmission? {
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
    )
}

internal fun SharedPreferences.Editor.putFrozenSubmission(
    snapshot: FrozenSubmission,
    phase: SubmissionPhase,
): SharedPreferences.Editor {
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
    return this
}

internal fun clearFrozenSubmission(preferences: SharedPreferences) {
    preferences.edit()
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
        .commit()
}
