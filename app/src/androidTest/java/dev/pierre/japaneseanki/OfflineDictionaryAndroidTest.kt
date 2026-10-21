package dev.pierre.japaneseanki

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real [OfflineDictionary] adapter against the real bundled index and against small
 * same-schema fixtures for the corruption and orphan cases that bundled data does not contain.
 */
@RunWith(AndroidJUnit4::class)
class OfflineDictionaryAndroidTest {
    @Test
    fun existenceAgreesWithSearchOnBundledData() {
        val sample = mutableListOf<Pair<String, String>>()
        openReadOnly(bundledFile).use { database ->
            database.rawQuery("SELECT expression, reading FROM forms ORDER BY random() LIMIT 150", null).use { cursor ->
                while (cursor.moveToNext()) sample += cursor.getString(0) to cursor.getString(1)
            }
        }
        assertTrue(sample.isNotEmpty())
        sample.forEach { (expression, reading) ->
            assertEquals(
                "expression '$expression'",
                bundled.search(expression, limit = 1).isNotEmpty(),
                bundled.hasEntry(expression),
            )
            if (reading != expression) {
                assertEquals(
                    "reading '$reading'",
                    bundled.search(reading, limit = 1).isNotEmpty(),
                    bundled.hasEntry(reading),
                )
            }
        }
    }

    @Test
    fun knownTermsBlanksAndMetacharacters() {
        assertTrue(bundled.hasEntry("猫"))
        assertTrue(bundled.hasEntry("ねこ"))
        assertTrue("surrounding whitespace is trimmed", bundled.hasEntry("  猫  "))
        assertTrue(bundled.hasEntry("忙しい"))

        assertFalse(bundled.hasEntry(""))
        assertFalse(bundled.hasEntry("   "))
        assertFalse(bundled.hasEntry("\t\n"))
        assertFalse(bundled.hasEntry("存在しない語"))
        assertFalse(bundled.hasEntry("' OR 1=1 --"))
        assertFalse(bundled.hasEntry("猫' OR '1'='1"))
        assertFalse(bundled.hasEntry("%"))
    }

    @Test
    fun restrictedEntriesAreStillPresentForSelection() {
        val restricted = mutableListOf<Pair<String, String>>()
        openReadOnly(bundledFile).use { database ->
            database.rawQuery(
                """SELECT f.expression, f.reading FROM forms f JOIN entries e ON e.id=f.entry_id
                   WHERE e.senses LIKE '%"stagk":["%' OR e.senses LIKE '%"stagr":["%' LIMIT 25""",
                null,
            ).use { cursor ->
                while (cursor.moveToNext()) restricted += cursor.getString(0) to cursor.getString(1)
            }
        }
        assertTrue("expected sense-restricted entries in the bundled data", restricted.isNotEmpty())
        restricted.forEach { (expression, reading) ->
            assertTrue(expression, bundled.hasEntry(expression))
            assertTrue(expression, bundled.hasEntry(reading))
            assertTrue(
                "restricted senses are filtered during selection, not existence",
                bundled.search(expression, limit = 1).isNotEmpty(),
            )
        }
    }

    @Test
    fun existenceQueryUsesIndexesWithoutSortingOrJsonWork() {
        val plan = mutableListOf<String>()
        openReadOnly(bundledFile).use { database ->
            database.rawQuery("EXPLAIN QUERY PLAN $HAS_ENTRY_SQL", null).use { cursor ->
                while (cursor.moveToNext()) plan += cursor.getString(3)
            }
        }
        val joined = plan.joinToString("\n")
        assertTrue(joined, joined.contains("MULTI-INDEX OR"))
        assertTrue(joined, joined.contains("forms_expression"))
        assertTrue(joined, joined.contains("forms_reading"))
        assertFalse(joined, joined.contains("TEMP B-TREE"))
        assertFalse(joined, joined.contains("SCAN f"))
    }

    @Test
    fun orphanFormsAreNotValidEntries() {
        val file = syntheticDictionary("orphan")
        val dictionary = OfflineDictionary(context, file)
        try {
            dictionary.prepare()
            openReadWrite(file).use { database ->
                database.execSQL("INSERT INTO forms (expression, reading, entry_id) VALUES (?, ?, ?)", arrayOf("zzz孤児", "zzzこじ", 987654321L))
                database.rawQuery("SELECT 1 FROM forms WHERE expression='zzz孤児'", null).use { cursor ->
                    assertTrue("fixture must contain the orphan form", cursor.moveToFirst())
                }
            }
            assertFalse(dictionary.hasEntry("zzz孤児"))
            assertFalse(dictionary.hasEntry("zzzこじ"))
        } finally {
            dictionary.close()
            file.delete()
        }
    }

    @Test
    fun corruptSensesKeepExistenceButBreakSelection() {
        val file = syntheticDictionary("corrupt")
        val dictionary = OfflineDictionary(context, file)
        try {
            dictionary.prepare()
            openReadWrite(file).use { database ->
                database.execSQL("UPDATE entries SET senses='not json' WHERE id=1")
            }
            // Approved boundary: existence is a grouping hint and no longer fails on content that
            // selection cannot parse. Selection still fails closed, so no incomplete note exports.
            assertTrue(dictionary.hasEntry("猫"))
            assertTrue(runCatching { dictionary.search("猫") }.isFailure)
        } finally {
            dictionary.close()
            file.delete()
        }
    }

    @Test
    fun blanksAreFalseBeforeAnyDatabaseIsRequired() {
        val file = File(context.cacheDir, "dictionary-unprepared-${UUID.randomUUID()}.sqlite")
        val dictionary = OfflineDictionary(context, file)
        try {
            assertFalse(dictionary.hasEntry(""))
            assertFalse(dictionary.hasEntry(" \t "))
            assertTrue(runCatching { dictionary.hasEntry("猫") }.isFailure)
        } finally {
            dictionary.close()
            file.delete()
            File(context.filesDir, "japanese-dictionary.tmp").delete()
        }
    }

    private fun syntheticDictionary(label: String): File {
        val file = File(context.cacheDir, "dictionary-$label-${UUID.randomUUID()}.sqlite")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { database ->
            database.execSQL("PRAGMA user_version=2")
            database.execSQL("CREATE TABLE entries (id INTEGER PRIMARY KEY, senses TEXT NOT NULL)")
            database.execSQL(
                """CREATE TABLE forms (expression TEXT NOT NULL, reading TEXT NOT NULL,
                   entry_id INTEGER NOT NULL REFERENCES entries(id),
                   PRIMARY KEY(expression, reading, entry_id)) WITHOUT ROWID""",
            )
            database.execSQL("CREATE INDEX forms_expression ON forms(expression)")
            database.execSQL("CREATE INDEX forms_reading ON forms(reading)")
            database.execSQL("CREATE TABLE metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL) WITHOUT ROWID")
            database.execSQL("INSERT INTO metadata VALUES ('schema_version','2')")
            database.execSQL(
                "INSERT INTO entries (id, senses) VALUES (1, ?)",
                arrayOf("""[{"glosses":["cat"],"pos":["noun"],"misc":[],"stagk":[],"stagr":[]}]"""),
            )
            database.execSQL("INSERT INTO forms (expression, reading, entry_id) VALUES ('猫','ねこ',1)")
        }
        return file
    }

    private fun openReadOnly(file: File): SQLiteDatabase =
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)

    private fun openReadWrite(file: File): SQLiteDatabase =
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)

    companion object {
        private lateinit var context: Context
        private lateinit var bundledFile: File
        private lateinit var bundled: OfflineDictionary

        @BeforeClass @JvmStatic fun installBundledIndex() {
            context = InstrumentationRegistry.getInstrumentation().targetContext
            bundledFile = File(context.cacheDir, "dictionary-bundled-${UUID.randomUUID()}.sqlite")
            bundled = OfflineDictionary(context, bundledFile)
            bundled.prepare()
        }

        @AfterClass @JvmStatic fun removeBundledIndex() {
            bundled.close()
            bundledFile.delete()
        }
    }
}
