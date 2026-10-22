package dev.pierre.japaneseanki

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** Provider constants reference AnkiDroid v2.24.0, commit ebcf8e0e34921628b9b8a496c66ffd4adbb3705f. */
class AnkiDroidGateway(
    private val resolver: ContentResolver,
    private val context: Context? = null,
) {
    /** Imports a completed durable WebP through AnkiDroid's v2.24.0 media provider. */
    fun importProcessedImage(file: File): String = importMedia(file, MediaImportOperation.IMAGE)

    /** Imports a completed draft MP3/Ogg recording through the same media provider. */
    fun importAudio(file: File): String = importMedia(file, MediaImportOperation.AUDIO)

    /** Operation-aware overload lets Send errors identify sentence vs. vocabulary audio. */
    internal fun importAudio(file: File, operation: MediaImportOperation): String {
        if (operation.kind != AnkiMediaKind.AUDIO) {
            throw MediaImportException(operation, MediaImportFailureStage.SOURCE_VALIDATION)
        }
        return importMedia(file, operation)
    }

    private fun importMedia(file: File, operation: MediaImportOperation): String {
        val kind = operation.kind
        val (appContext, canonicalFile, preferredName) = try {
            val appContext = requireNotNull(context) {
                "A Context is required to grant AnkiDroid temporary access to media."
            }.applicationContext
            require(file.isFile) { "Media file does not exist." }
            val approvedDirectory = File(appContext.filesDir, MEDIA_DIRECTORY).canonicalFile
            val canonicalFile = file.canonicalFile
            require(canonicalFile.parentFile == approvedDirectory) {
                "Media must be stored in the durable draft media directory."
            }
            val preferredName = when (kind) {
                AnkiMediaKind.IMAGE -> AnkiMediaImporter.sanitizeFilename(canonicalFile.name)
                AnkiMediaKind.AUDIO -> AnkiMediaImporter.sanitizeAudioFilename(canonicalFile.name)
            }
            require(canonicalFile.name == preferredName) { "Media filename is unsafe." }
            Triple(appContext, canonicalFile, preferredName)
        } catch (cancelled: java.util.concurrent.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            throw MediaImportException(operation, MediaImportFailureStage.SOURCE_VALIDATION, error)
        }

        val fileUri = try {
            FileProvider.getUriForFile(appContext, MEDIA_FILE_PROVIDER_AUTHORITY, canonicalFile)
        } catch (cancelled: java.util.concurrent.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            throw MediaImportException(operation, MediaImportFailureStage.SOURCE_VALIDATION, error)
        }
        var granted = false
        var importedUri: Uri? = null
        var providerFailure: Exception? = null
        try {
            appContext.grantUriPermission(ANKI_PACKAGE, fileUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            granted = true
            val values = ContentValues().apply {
                put(MEDIA_FILE_URI, fileUri.toString())
                put(MEDIA_PREFERRED_NAME, preferredName)
            }
            importedUri = resolver.insert(MEDIA_URI, values)
        } catch (cancelled: java.util.concurrent.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            providerFailure = error
        } finally {
            if (granted) {
                try {
                    appContext.revokeUriPermission(fileUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (cancelled: java.util.concurrent.CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    if (providerFailure == null) providerFailure = error
                    else providerFailure?.addSuppressed(error)
                }
            }
        }
        providerFailure?.let { throw MediaImportException(operation, MediaImportFailureStage.PROVIDER_IMPORT, it) }
        val returnedUri = importedUri?.toString()
            ?: throw MediaImportException(operation, MediaImportFailureStage.PROVIDER_IMPORT)
        return AnkiMediaImporter.importedFilename(returnedUri, kind)
            ?: throw MediaImportException(operation, MediaImportFailureStage.RETURNED_MEDIA_VALIDATION)
    }
    fun noteTypes(): List<AnkiNoteType> = try {
        resolver.query(
            MODELS_URI,
            arrayOf(ID, NAME, FIELD_NAMES),
            null,
            null,
            null,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(ID)
            val nameColumn = cursor.getColumnIndexOrThrow(NAME)
            val fieldsColumn = cursor.getColumnIndexOrThrow(FIELD_NAMES)
            buildList {
                while (cursor.moveToNext()) {
                    val rawFields = cursor.getString(fieldsColumn).orEmpty()
                    add(
                        AnkiNoteType(
                            id = cursor.getLong(idColumn),
                            name = cursor.getString(nameColumn).orEmpty(),
                            fields = AnkiFieldContract.parseModelFieldNames(rawFields),
                        ),
                    )
                }
            }
        } ?: error("AnkiDroid did not return note types. Check AnkiDroid API access and retry.")
    } catch (error: SecurityException) {
        throw ankiPermissionError("read available note types", error)
    }

    fun hasDuplicate(
        model: AnkiNoteType,
        deck: AnkiDeck,
        fieldsByName: Map<String, String>,
    ): Long? = matchingNoteId(model, deck.id, fieldsByName)

    /** Used only to reconcile an interrupted insert whose destination is not yet known. */
    fun hasMatchingNoteInAnyDeck(
        model: AnkiNoteType,
        fieldsByName: Map<String, String>,
    ): Long? = matchingNoteId(model, null, fieldsByName)

    fun noteCards(noteId: Long): List<AnkiCardLocation> {
        require(noteId > 0) { "Note ID must be positive." }
        val cardsUri = Uri.withAppendedPath(Uri.withAppendedPath(NOTES_URI, noteId.toString()), "cards")
        val cursor = try {
            resolver.query(cardsUri, arrayOf(CARD_ORD, CARD_DECK_ID), null, null, null)
        } catch (error: RuntimeException) {
            throw AnkiProviderUncertainException("AnkiDroid card lookup outcome is uncertain.", error)
        } ?: error("AnkiDroid could not query the note's cards.")
        return cursor.use {
            val ordColumn = it.getColumnIndexOrThrow(CARD_ORD)
            val deckColumn = it.getColumnIndexOrThrow(CARD_DECK_ID)
            requireCardProjection(ordColumn, deckColumn)
            buildList {
                while (it.moveToNext()) {
                    val ord = it.getInt(ordColumn)
                    val deckId = it.getLong(deckColumn)
                    add(validateCardLocation(ord, deckId))
                }
            }
        }
    }

    /** Moves only cards belonging to noteId, then verifies every card's destination. */
    fun ensureNoteInDeck(noteId: Long, deckId: Long): Boolean {
        require(noteId > 0) { "Note ID must be positive." }
        require(deckId > 0) { "Deck ID must be positive." }
        val cards = noteCards(noteId)
        if (cards.isEmpty()) return false
        cards.filter { it.deckId != deckId }.forEach { card ->
            val cardUri = Uri.withAppendedPath(
                Uri.withAppendedPath(Uri.withAppendedPath(NOTES_URI, noteId.toString()), "cards"),
                card.ord.toString(),
            )
            val updated = try {
                resolver.update(cardUri, ContentValues().apply { put(CARD_DECK_ID, deckId) }, null, null)
            } catch (error: RuntimeException) {
                throw AnkiProviderUncertainException("AnkiDroid card move outcome is uncertain.", error)
            }
            requireCardMoveCount(updated)
        }
        val verified = noteCards(noteId)
        check(verified.isNotEmpty() && verified.all { it.deckId == deckId }) {
            "The inserted note's cards were not verified in the destination deck."
        }
        return true
    }

    private fun matchingNoteId(
        model: AnkiNoteType,
        destinationDeckId: Long?,
        fieldsByName: Map<String, String>,
    ): Long? {
        require("SentKanji" in model.fields && "VocabKanji" in model.fields) {
            "Selected note type is missing SentKanji or VocabKanji; choose a note type with the required fields."
        }
        val sentence = fieldsByName["SentKanji"] ?: error("Missing SentKanji value.")
        val lemma = fieldsByName["VocabKanji"] ?: error("Missing VocabKanji value.")
        val duplicateKey = DuplicateKey.fromFields(sentence, lemma)
        val sentenceIndex = model.fields.indexOf("SentKanji")
        require(sentenceIndex >= 0) { "Selected note type is missing required duplicate fields." }

        val projection = arrayOf(NOTE_ID, NOTE_FIELDS)
        // Scan this model's notes rather than relying on Anki's first-field checksum. The
        // checksum cannot represent the app's trimmed, markup-decoded two-field equality.
        val candidates = try {
            resolver.query(NOTES_V2_URI, projection, "$NOTE_MODEL_ID=?", arrayOf(model.id.toString()), null)
        } catch (error: SecurityException) {
            throw ankiPermissionError("check for duplicate notes", error)
        } ?: error("AnkiDroid could not complete the duplicate query. Check AnkiDroid API access and retry.")
        candidates.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(NOTE_ID)
            val fieldsColumn = cursor.getColumnIndexOrThrow(NOTE_FIELDS)
            while (cursor.moveToNext()) {
                val candidateKey = DuplicateKey.fromModelFields(
                    model.fields,
                    cursor.getString(fieldsColumn).orEmpty(),
                )
                if (!duplicateKey.matches(candidateKey)) continue
                val noteId = cursor.getLong(idColumn)
                val cardsUri = Uri.withAppendedPath(
                    Uri.withAppendedPath(NOTES_URI, noteId.toString()),
                    "cards",
                )
                val cards = try {
                    resolver.query(cardsUri, arrayOf(CARD_DECK_ID), null, null, null)
                } catch (error: SecurityException) {
                    throw ankiPermissionError("check the destination deck for duplicate notes", error)
                } ?: error("AnkiDroid could not check a matching note's deck. Check API access and retry.")
                cards.use { cardCursor ->
                    val deckColumn = cardCursor.getColumnIndexOrThrow(CARD_DECK_ID)
                    while (cardCursor.moveToNext()) {
                        if (destinationDeckId == null || cardCursor.getLong(deckColumn) == destinationDeckId) return noteId
                    }
                }
            }
        }
        return null
    }

    /** Inserts one note; AnkiDroid creates its cards in the model's default deck. */
    fun insertNote(
        model: AnkiNoteType,
        orderedFields: List<String>,
        tags: Set<String>,
    ): Long {
        require(orderedFields.size == model.fields.size) { "Field count does not match selected note type." }
        val values = ContentValues().apply {
            put(NOTE_MODEL_ID, model.id)
            put(NOTE_FIELDS, orderedFields.joinToString(FIELD_SEPARATOR.toString()))
            put(NOTE_TAGS, tags.joinToString(" "))
        }
        val returnedUri = try {
            resolver.insert(NOTES_URI, values)
        } catch (error: SecurityException) {
            throw ankiPermissionError("insert the new note", error)
        } catch (error: RuntimeException) {
            throw AnkiProviderUncertainException("AnkiDroid note insertion outcome is uncertain.", error)
        }
        val returnedUriString = requireReturnedNoteUri(returnedUri?.toString())
        return try {
            parseInsertedNoteId(returnedUriString)
        } catch (error: IllegalArgumentException) {
            throw AnkiProviderDefiniteInsertException(
                "AnkiDroid returned no valid inserted note ID.",
                error,
            )
        }
    }

    fun decks(): List<AnkiDeck> = try {
        resolver.query(
            DECKS_URI,
            arrayOf(DECK_ID, DECK_NAME, DECK_DYNAMIC),
            null,
            null,
            null,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(DECK_ID)
            val nameColumn = cursor.getColumnIndexOrThrow(DECK_NAME)
            val dynamicColumn = cursor.getColumnIndexOrThrow(DECK_DYNAMIC)
            buildList {
                while (cursor.moveToNext()) {
                    if (cursor.getInt(dynamicColumn) == 0) {
                        add(AnkiDeck(cursor.getLong(idColumn), cursor.getString(nameColumn).orEmpty()))
                    }
                }
            }
        } ?: error("AnkiDroid did not return decks. Check AnkiDroid API access and retry.")
    } catch (error: SecurityException) {
        throw ankiPermissionError("read available decks", error)
    }

    companion object {
        private const val AUTHORITY = "com.ichi2.anki.flashcards"
        private const val ANKI_PACKAGE = "com.ichi2.anki"
        private const val MEDIA_DIRECTORY = "draft"
        private const val MEDIA_FILE_PROVIDER_AUTHORITY = "dev.pierre.japaneseanki.media"
        private const val MEDIA_FILE_URI = "file_uri"
        private const val MEDIA_PREFERRED_NAME = "preferred_name"
        private const val FIELD_SEPARATOR = '\u001f'
        private const val ID = "_id"
        private const val NAME = "name"
        private const val FIELD_NAMES = "field_names"
        private const val DECK_ID = "deck_id"
        private const val DECK_NAME = "deck_name"
        private const val DECK_DYNAMIC = "deck_dyn"
        private const val NOTE_MODEL_ID = "mid"
        private const val NOTE_FIELDS = "flds"
        private const val NOTE_TAGS = "tags"
        private const val NOTE_ID = "_id"
        private const val CARD_ORD = "ord"
        private const val CARD_DECK_ID = "deck_id"
        private val BASE_URI = Uri.parse("content://$AUTHORITY")
        private val MODELS_URI = Uri.withAppendedPath(BASE_URI, "models")
        private val DECKS_URI = Uri.withAppendedPath(BASE_URI, "decks")
        private val NOTES_URI = Uri.withAppendedPath(BASE_URI, "notes")
        private val NOTES_V2_URI = Uri.withAppendedPath(BASE_URI, "notes_v2")
        private val MEDIA_URI = Uri.withAppendedPath(BASE_URI, "media")
    }
}

data class AnkiNoteType(val id: Long, val name: String, val fields: List<String>)
data class AnkiDeck(val id: Long, val name: String)
data class AnkiCardLocation(val ord: Int, val deckId: Long)

private fun ankiPermissionError(operation: String, cause: SecurityException): IllegalStateException =
    IllegalStateException(
        "AnkiDroid denied permission to $operation. Grant this app AnkiDroid API access in AnkiDroid settings, then retry.",
        cause,
    )

internal class AnkiProviderUncertainException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
internal class AnkiProviderDefiniteInsertException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

internal fun requireReturnedNoteUri(uri: String?): String = uri ?: throw AnkiProviderDefiniteInsertException(
    "AnkiDroid returned no note URI; no inserted note ID was produced."
)

internal fun parseInsertedNoteId(uri: String): Long {
    val match = Regex("^content://com\\.ichi2\\.anki\\.flashcards/notes/([1-9][0-9]*)(?:\\?[^#]*)?$")
        .matchEntire(uri)
    val noteId = match?.groupValues?.getOrNull(1)?.toLongOrNull()
    require(noteId != null && noteId > 0) { "AnkiDroid returned an invalid inserted note URI." }
    return noteId
}

internal fun requireCardProjection(ordColumn: Int, deckColumn: Int) {
    require(ordColumn >= 0 && deckColumn >= 0) { "AnkiDroid returned a malformed card projection." }
}

internal fun validateCardLocation(ord: Int, deckId: Long): AnkiCardLocation {
    require(ord >= 0) { "AnkiDroid returned a malformed card ordinal." }
    require(deckId > 0) { "AnkiDroid returned a malformed card deck ID." }
    return AnkiCardLocation(ord, deckId)
}

internal fun requireCardMoveCount(updated: Int) {
    require(updated == 1) { "AnkiDroid did not move the inserted note's card." }
}
