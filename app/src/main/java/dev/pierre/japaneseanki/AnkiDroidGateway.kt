package dev.pierre.japaneseanki

import android.content.ContentResolver
import android.net.Uri

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
        private val BASE_URI = Uri.parse("content://$AUTHORITY")
        private val MODELS_URI = Uri.withAppendedPath(BASE_URI, "models")
        private val DECKS_URI = Uri.withAppendedPath(BASE_URI, "decks")
    }
}

data class AnkiNoteType(val id: Long, val name: String, val fields: List<String>)
data class AnkiDeck(val id: Long, val name: String)
