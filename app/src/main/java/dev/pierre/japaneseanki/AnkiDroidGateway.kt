package dev.pierre.japaneseanki

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.text.Html
import java.math.BigInteger
import java.security.MessageDigest

/** Provider constants reference AnkiDroid v2.24.1, commit 9f579c10bb151146728220729c510acbbd8faba7. */
class AnkiDroidGateway(private val resolver: ContentResolver) {
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
    ): Boolean = matchingNote(model, deck.id, fieldsByName)

    /** Used only to reconcile an interrupted insert whose destination is not yet known. */
    fun hasMatchingNoteInAnyDeck(
        model: AnkiNoteType,
        fieldsByName: Map<String, String>,
    ): Boolean = matchingNote(model, null, fieldsByName)

    private fun matchingNote(
        model: AnkiNoteType,
        destinationDeckId: Long?,
        fieldsByName: Map<String, String>,
    ): Boolean {
        val sentenceIndex = model.fields.indexOf("SentKanji")
        val lemmaIndex = model.fields.indexOf("VocabKanji")
        require(sentenceIndex >= 0 && lemmaIndex >= 0) { "Selected note type is missing required fields." }
        val sentence = fieldsByName["SentKanji"] ?: error("Missing SentKanji value.")
        val lemma = fieldsByName["VocabKanji"] ?: error("Missing VocabKanji value.")
        val duplicateKey = DuplicateKey.fromFields(sentence, lemma)

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
                val values = cursor.getString(fieldsColumn).orEmpty()
                    .split(FIELD_SEPARATOR, ignoreCase = false, limit = Int.MAX_VALUE)
                if (sentenceIndex >= values.size || lemmaIndex >= values.size) {
                    error("AnkiDroid returned a note with an invalid field list.")
                }
                if (!duplicateKey.matches(DuplicateKey.fromFields(values[sentenceIndex], values[lemmaIndex]))) {
                    continue
                }
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
                        if (destinationDeckId == null || cardCursor.getLong(deckColumn) == destinationDeckId) return true
                    }
                }
            }
        }
        return false
    }

    /** Add one note using AnkiDroid's supported bulkInsert deck parameter. */
    fun insertNote(
        model: AnkiNoteType,
        deck: AnkiDeck,
        orderedFields: List<String>,
        tags: Set<String>,
    ): Int {
        require(orderedFields.size == model.fields.size) { "Field count does not match selected note type." }
        val values = ContentValues().apply {
            put(NOTE_MODEL_ID, model.id)
            put(NOTE_FIELDS, orderedFields.joinToString(FIELD_SEPARATOR.toString()))
            put(NOTE_TAGS, tags.joinToString(" "))
        }
        val uri = NOTES_URI.buildUpon()
            .appendQueryParameter(DECK_ID_QUERY_PARAM, deck.id.toString())
            .build()
        return resolver.bulkInsert(uri, arrayOf(values))
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
        private const val CARD_DECK_ID = "deck_id"
        private const val DECK_ID_QUERY_PARAM = "deckId"
        private val BASE_URI = Uri.parse("content://$AUTHORITY")
        private val MODELS_URI = Uri.withAppendedPath(BASE_URI, "models")
        private val DECKS_URI = Uri.withAppendedPath(BASE_URI, "decks")
        private val NOTES_URI = Uri.withAppendedPath(BASE_URI, "notes")
        private val NOTES_V2_URI = Uri.withAppendedPath(BASE_URI, "notes_v2")

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
