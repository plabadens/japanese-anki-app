package dev.pierre.japaneseanki

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.FileOutputStream

/** One-time private copy of the bundled, read-only JMdict/Kanjium index. */
class OfflineDictionary(
    context: Context,
    private val databaseFile: File = File(context.applicationContext.filesDir, "japanese-dictionary-v2.sqlite"),
) : AutoCloseable {
    private val appContext: Context = context.applicationContext
    @Volatile private var database: SQLiteDatabase? = null

    @Synchronized
    fun prepare(): Map<String, String> = synchronized(installationLock) {
        database?.let { return metadata(it) }
        if (databaseFile.exists()) {
            database = openValidatedDatabase(databaseFile)
            if (database != null) return metadata(database!!)
            databaseFile.delete()
        }

        val temporaryFile = File(appContext.filesDir, "japanese-dictionary.tmp")
        temporaryFile.delete()
        try {
            // Android's asset pipeline expands the source .gz and strips its suffix.
            appContext.assets.open("dictionary.sqlite").buffered().use { input ->
                FileOutputStream(temporaryFile).use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                    output.fd.sync()
                }
            }
            check(temporaryFile.renameTo(databaseFile)) { "Could not install the offline dictionary." }
            val opened = openValidatedDatabase(databaseFile)
                ?: error("The bundled offline dictionary is invalid.")
            database = opened
            metadata(opened)
        } catch (failure: Exception) {
            temporaryFile.delete()
            databaseFile.delete()
            throw failure
        }
    }

    /**
     * Bounded existence probe for token grouping: does any entry have this exact surface as an
     * expression or reading? Trims the input, answers `false` for blanks without requiring a
     * prepared database, keeps the forms-to-entries join (an orphan form is not a match), uses
     * the existing indexes and selects a constant with `LIMIT 1` - no ordering, no sense JSON.
     *
     * Existence is a grouping hint, not content validation. A corrupt `senses` value can make
     * this return `true` for an entry that [search] would refuse to parse; selection still goes
     * through [search], which surfaces the malformed entry instead of exporting a partial note.
     */
    fun hasEntry(surface: String): Boolean {
        val query = surface.trim()
        if (query.isEmpty()) return false
        val db = database ?: error("Offline dictionary is not ready.")
        return db.rawQuery(HAS_ENTRY_SQL, arrayOf(query, query)).use { it.moveToFirst() }
    }

    fun search(surface: String, lemma: String? = null, limit: Int = 50, offset: Int = 0): List<DictionaryEntry> {
        val query = surface.trim()
        if (query.isEmpty()) return emptyList()
        require(limit in 1..100 && offset >= 0)
        val terms = listOfNotNull(query, lemma?.trim()?.takeIf(String::isNotEmpty)).distinct()
        val placeholders = terms.joinToString(",") { "?" }
        val lemmaTerm = terms.getOrElse(1) { query }
        val selectionArgs = terms + terms + listOf(query, query, lemmaTerm, limit.toString(), offset.toString())
        val db = database ?: error("Offline dictionary is not ready.")
        return db.rawQuery(
            """SELECT e.id, f.expression, f.reading, e.senses
               FROM forms f JOIN entries e ON e.id=f.entry_id
               WHERE f.expression IN ($placeholders) OR f.reading IN ($placeholders)
               ORDER BY CASE WHEN f.expression=? THEN 0 WHEN f.reading=? THEN 1
                             WHEN f.expression=? THEN 2 ELSE 3 END,
                        f.expression, f.reading, e.id
               LIMIT ? OFFSET ?""".trimIndent(),
            selectionArgs.toTypedArray(),
        ).use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow("id")
            val expressionColumn = cursor.getColumnIndexOrThrow("expression")
            val readingColumn = cursor.getColumnIndexOrThrow("reading")
            val sensesColumn = cursor.getColumnIndexOrThrow("senses")
            buildList {
                while (cursor.moveToNext()) {
                    val expression = cursor.getString(expressionColumn).orEmpty()
                    val reading = cursor.getString(readingColumn).orEmpty()
                    add(
                        DictionaryEntry(
                            id = cursor.getLong(idColumn),
                            expression = expression,
                            reading = reading,
                            senses = parseSenses(cursor.getString(sensesColumn).orEmpty(), expression, reading),
                        ),
                    )
                }
            }
        }
    }

    override fun close() {
        database?.close()
        database = null
    }

    private fun openValidatedDatabase(file: File): SQLiteDatabase? {
        val db = try {
            SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
            )
        } catch (_: Exception) {
            return null
        }
        return try {
            val version = db.rawQuery("PRAGMA user_version", null).use { cursor ->
                check(cursor.moveToFirst())
                cursor.getInt(0)
            }
            check(version == 2) { "Offline dictionary schema version is unsupported." }
            check(metadata(db)["schema_version"] == "2")
            db.rawQuery("SELECT 1 FROM entries LIMIT 1", null).use { cursor ->
                check(cursor.moveToFirst()) { "Offline dictionary has no entries." }
            }
            db
        } catch (_: Exception) {
            db.close()
            null
        }
    }

    private fun metadata(db: SQLiteDatabase): Map<String, String> =
        db.rawQuery("SELECT key, value FROM metadata", null).use { cursor ->
            val keyColumn = cursor.getColumnIndexOrThrow("key")
            val valueColumn = cursor.getColumnIndexOrThrow("value")
            buildMap {
                while (cursor.moveToNext()) {
                    put(cursor.getString(keyColumn), cursor.getString(valueColumn))
                }
            }
        }

    private fun parseSenses(raw: String, expression: String, reading: String): List<DictionarySense> {
        val array = org.json.JSONArray(raw)
        return buildList {
            for (index in 0 until array.length()) {
                val sense = array.getJSONObject(index)
                val kanjiRestrictions = sense.stringList("stagk")
                val readingRestrictions = sense.stringList("stagr")
                if (kanjiRestrictions.isNotEmpty() && expression !in kanjiRestrictions) continue
                if (readingRestrictions.isNotEmpty() && reading !in readingRestrictions) continue
                add(
                    DictionarySense(
                        glosses = sense.stringList("glosses"),
                        partsOfSpeech = sense.stringList("pos"),
                        notes = sense.stringList("misc"),
                    ),
                )
            }
        }
    }

    private fun org.json.JSONObject.stringList(key: String): List<String> {
        val array = optJSONArray(key) ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                array.optString(index).takeIf(String::isNotBlank)?.let(::add)
            }
        }
    }

    companion object {
        private val installationLock = Any()
    }
}

data class DictionaryEntry(
    val id: Long,
    val expression: String,
    val reading: String,
    val senses: List<DictionarySense>,
)

data class DictionarySense(
    val glosses: List<String>,
    val partsOfSpeech: List<String>,
    val notes: List<String>,
)

/**
 * Existence probe shared by [OfflineDictionary.hasEntry] and its query-plan test: one constant
 * column, both lookup keys, the forms-to-entries join, and no ordering or sense parsing.
 */
internal const val HAS_ENTRY_SQL = """SELECT 1 FROM forms f JOIN entries e ON e.id=f.entry_id
               WHERE f.expression=? OR f.reading=? LIMIT 1"""
