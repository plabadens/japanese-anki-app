package dev.pierre.japaneseanki

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Html
import androidx.core.content.FileProvider
import java.io.File
import java.math.BigInteger
import java.security.MessageDigest

/** Provider constants reference AnkiDroid v2.24.0, commit ebcf8e0e34921628b9b8a496c66ffd4adbb3705f. */
class AnkiDroidGateway(
    private val resolver: ContentResolver,
    private val context: Context? = null,
) {
    /** Imports a completed durable AVIF through AnkiDroid's v2.24.0 media provider. */
    fun importProcessedImage(file: File): String {
        val appContext = requireNotNull(context) {
            "A Context is required to grant AnkiDroid temporary access to image media."
        }.applicationContext
        require(file.isFile) { "Processed image file does not exist." }
        val approvedDirectory = File(appContext.filesDir, MEDIA_DIRECTORY).canonicalFile
        val canonicalFile = file.canonicalFile
        require(canonicalFile.parentFile == approvedDirectory) {
            "Processed image must be stored in the durable draft media directory."
        }
        val preferredName = AnkiMediaImporter.sanitizeFilename(canonicalFile.name)
        require(canonicalFile.name == preferredName) { "Processed image filename is unsafe." }

        val fileUri = FileProvider.getUriForFile(appContext, MEDIA_FILE_PROVIDER_AUTHORITY, canonicalFile)
        appContext.grantUriPermission(ANKI_PACKAGE, fileUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val importedUri = try {
            val values = ContentValues().apply {
                put(MEDIA_FILE_URI, fileUri.toString())
                put(MEDIA_PREFERRED_NAME, preferredName)
            }
            resolver.insert(MEDIA_URI, values)
                ?: error("AnkiDroid did not return an imported media URI.")
        } finally {
            appContext.revokeUriPermission(fileUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return AnkiMediaImporter.importedFilename(importedUri.toString())
            ?: error("AnkiDroid returned an unsafe media URI.")
    }
    fun noteTypes(): List<AnkiNoteType> = resolver.query(
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
                        fields = rawFields.split(FIELD_SEPARATOR).filter(String::isNotEmpty),
                    ),
                )
            }
        }
    } ?: error("AnkiDroid did not return note types.")

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
            buildList {
                while (it.moveToNext()) add(AnkiCardLocation(it.getInt(ordColumn), it.getLong(deckColumn)))
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
            check(updated == 1) { "AnkiDroid did not move the inserted note's card." }
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
            "Selected note type is missing required fields."
        }
        val sentence = fieldsByName["SentKanji"] ?: error("Missing SentKanji value.")
        val lemma = fieldsByName["VocabKanji"] ?: error("Missing VocabKanji value.")
        val duplicateKey = DuplicateKey.fromFields(sentence, lemma)
        val sentenceIndex = model.fields.indexOf("SentKanji")
        require(sentenceIndex >= 0) { "Selected note type is missing required duplicate fields." }

        val projection = arrayOf(NOTE_ID, NOTE_FIELDS)
        val selection: String
        val args: Array<String>
        if (sentenceIndex == 0) {
            selection = "$NOTE_MODEL_ID=? AND $NOTE_CHECKSUM=?"
            args = arrayOf(model.id.toString(), firstFieldChecksum(sentence).toString())
        } else {
            // Anki stores a checksum only for the first field; preserve correctness for reordered models.
            selection = "$NOTE_MODEL_ID=?"
            args = arrayOf(model.id.toString())
        }
        val candidates = resolver.query(NOTES_V2_URI, projection, selection, args, null)
            ?: error("AnkiDroid could not complete the duplicate query.")
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
                val cards = resolver.query(cardsUri, arrayOf(CARD_DECK_ID), null, null, null)
                    ?: error("AnkiDroid could not check a matching note's deck.")
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
        } catch (error: RuntimeException) {
            throw AnkiProviderUncertainException("AnkiDroid note insertion outcome is uncertain.", error)
        } ?: error("AnkiDroid did not return the inserted note URI.")
        return parseInsertedNoteId(returnedUri.toString())
    }

    fun decks(): List<AnkiDeck> = resolver.query(
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
    } ?: error("AnkiDroid did not return decks.")

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
        private const val NOTE_CHECKSUM = "csum"
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

        private fun firstFieldChecksum(html: String): Long {
            val stripped = html.replace(Regex("<.*?>"), "").replace("&nbsp;", " ")
            val plainText = Html.fromHtml(stripped, Html.FROM_HTML_MODE_LEGACY).toString()
            val digest = MessageDigest.getInstance("SHA-1")
                .digest(plainText.toByteArray(Charsets.UTF_8))
            return BigInteger(1, digest).toString(16).padStart(40, '0').take(8).toLong(16)
        }
    }
}

data class AnkiNoteType(val id: Long, val name: String, val fields: List<String>)
data class AnkiDeck(val id: Long, val name: String)
data class AnkiCardLocation(val ord: Int, val deckId: Long)

internal class AnkiProviderUncertainException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

internal fun parseInsertedNoteId(uri: String): Long {
    val noteId = uri.substringBefore('?').substringAfterLast('/').toLongOrNull()
    require(noteId != null && noteId > 0) { "AnkiDroid returned an invalid inserted note URI." }
    return noteId
}
