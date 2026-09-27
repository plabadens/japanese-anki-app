package dev.pierre.japaneseanki

import android.content.SharedPreferences
import java.io.File
import java.util.Base64

internal enum class SubmissionPhase(val persisted: String) {
    MEDIA_IMPORTING("media_importing"),
    MEDIA_IMPORTED("media_imported"),
    NOTE_INSERTING("started");

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
    val media: MediaIdentity?,
    val importedFilename: String?,
)

internal object SubmissionRecoveryKeys {
    const val STATUS = "submission_status"
    const val REVISION = "pending_revision"
    const val MODEL_ID = "pending_model_id"
    const val DECK_ID = "pending_deck_id"
    const val MODEL_FIELDS = "pending_model_fields"
    const val ORDERED_FIELDS = "pending_ordered_fields"
    const val SENTENCE = "pending_sentence"
    const val LEMMA = "pending_lemma"
    const val MEDIA_PATH = "pending_media_path"
    const val MEDIA_SIZE = "pending_media_size"
    const val MEDIA_MODIFIED = "pending_media_modified"
    const val IMPORTED_FILENAME = "pending_imported_filename"
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
        media = media,
        importedFilename = preferences.getString(SubmissionRecoveryKeys.IMPORTED_FILENAME, null),
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
        .remove(SubmissionRecoveryKeys.MEDIA_PATH)
        .remove(SubmissionRecoveryKeys.MEDIA_SIZE)
        .remove(SubmissionRecoveryKeys.MEDIA_MODIFIED)
        .remove(SubmissionRecoveryKeys.IMPORTED_FILENAME)
        .commit()
}
