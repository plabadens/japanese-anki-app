package dev.pierre.japaneseanki

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The repository against the real bundled index, real SQLite and a real asset install. */
@RunWith(AndroidJUnit4::class)
class AjtPitchRepositoryAndroidTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val temporaryFiles = mutableListOf<File>()

    @After fun cleanUp() {
        temporaryFiles.forEach { it.delete() }
        temporaryFiles.clear()
    }

    @Test
    fun bundledIndexKeepsSourceOrderAndSuppliedMarkup() {
        val repository = AjtPitchRepository(openStore = { AjtPitchIndex.open(context) })
        try {
            assertEquals(AjtPitchAvailability.Ready, repository.prepare())
            // Successful preparation is idempotent: a draft reset must not reopen or replace it.
            assertEquals(AjtPitchAvailability.Ready, repository.prepare())
            assertEquals(AjtPitchAvailability.Ready, repository.availability.value)

            val installed = File(context.filesDir, "pitch_ajt-v${AjtPitchIndex.SCHEMA_VERSION}.sqlite")
            assertTrue(installed.isFile)
            val expected = directRecords(installed, "花茣蓙", "はなござ")
            assertEquals(8, expected.size)
            // Source order, including the four duplicated (accent, html) pairs.
            assertEquals(listOf(0, 0, 2, 2, 0, 0, 2, 2), expected.map { it.accent })
            assertEquals(expected, repository.pitch("花茣蓙", "はなござ"))

            val busy = repository.pitch("忙しさ", "いそがしさ").single()
            assertEquals(3, busy.accent)
            assertTrue(busy.html.contains("class=\"nasal\""))
            assertTrue(busy.html.contains("class=\"devoiced\""))
            assertTrue(busy.html.contains("class=\"handakuten\""))

            // The pinned index carries katakana reading keys too; no folding is applied here.
            assertEquals(expected, repository.pitch("花茣蓙", "ハナゴザ"))

            assertEquals(emptyList<AjtPitchRecord>(), repository.pitch("存在しない語", "そんざいしないご"))
            assertEquals(emptyList<AjtPitchRecord>(), repository.pitch(" ", "はなござ"))
        } finally {
            repository.close()
        }
    }

    @Test
    fun emptyIndexIsAnExplicitUnavailableState() {
        val file = temporaryDatabase("empty") { database ->
            database.execSQL(
                """CREATE TABLE pitch_ajt (expression TEXT NOT NULL, reading TEXT NOT NULL,
                   pitch_html TEXT NOT NULL, pitch_number INTEGER NOT NULL, source_order INTEGER NOT NULL)""",
            )
        }
        val handle = AjtPitchIndex(openReadOnly(file), file)
        val repository = AjtPitchRepository(openStore = { handle })

        assertEquals(AjtPitchAvailability.Unavailable(AjtPitchFailure.EMPTY_INDEX), repository.prepare())
        assertEquals(AjtPitchAvailability.Unavailable(AjtPitchFailure.EMPTY_INDEX), repository.availability.value)
        // A validated-but-empty index is unavailable, and its provisional handle is closed.
        assertFalse(handle.isOpen)
        assertEquals(emptyList<AjtPitchRecord>(), repository.pitch("猫", "ねこ"))
    }

    @Test
    fun unusableSchemaIsRejectedAndItsInstallDiscardedForRetry() {
        val file = temporaryDatabase("wrong-schema") { database ->
            database.execSQL("CREATE TABLE something_else (value TEXT NOT NULL)")
        }
        val repository = AjtPitchRepository(
            openStore = { if (file.isFile) AjtPitchIndex(openReadOnly(file), file) else null },
        )

        assertEquals(AjtPitchAvailability.Unavailable(AjtPitchFailure.INVALID_INDEX), repository.prepare())
        // probe() discards the installed copy, so an explicit retry reinstalls from the asset
        // instead of reopening the same unusable file forever.
        assertFalse(file.exists())
        assertEquals(AjtPitchAvailability.Unavailable(AjtPitchFailure.MISSING_INDEX), repository.prepare())
    }

    @Test
    fun repeatedValidationFailuresDoNotLeakDatabaseHandles() {
        val before = openFileDescriptorCount()
        repeat(20) { index ->
            val file = temporaryDatabase("wrong-schema-$index") { database ->
                database.execSQL("CREATE TABLE something_else (value TEXT NOT NULL)")
            }
            val repository = AjtPitchRepository(openStore = { AjtPitchIndex(openReadOnly(file), file) })
            assertEquals(AjtPitchAvailability.Unavailable(AjtPitchFailure.INVALID_INDEX), repository.prepare())
            repository.close()
        }
        val after = openFileDescriptorCount()
        assertTrue("file descriptors grew from $before to $after", after <= before + 2)
    }

    @Test
    fun theProcessSingletonIsReusedAndCanBeReset() {
        AjtPitchRepository.resetForTests()
        try {
            val first = AjtPitchRepository.shared(context)
            assertSame(first, AjtPitchRepository.shared(context))
            assertEquals(AjtPitchAvailability.Ready, first.prepare())
            assertEquals(AjtPitchAvailability.Ready, AjtPitchRepository.shared(context).prepare())

            AjtPitchRepository.resetForTests()
            val second = AjtPitchRepository.shared(context)
            assertEquals(AjtPitchAvailability.NotPrepared, second.availability.value)
            assertEquals(AjtPitchAvailability.Ready, second.prepare())
        } finally {
            AjtPitchRepository.resetForTests()
        }
    }

    private fun directRecords(file: File, expression: String, reading: String): List<AjtPitchRecord> =
        openReadOnly(file).use { database ->
            database.rawQuery(
                "SELECT pitch_html, pitch_number FROM pitch_ajt WHERE expression=? AND reading=? ORDER BY source_order",
                arrayOf(expression, reading),
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(AjtPitchRecord(cursor.getString(0), cursor.getInt(1)))
                }
            }
        }

    private fun temporaryDatabase(label: String, create: (SQLiteDatabase) -> Unit): File {
        val file = File(context.cacheDir, "pitch-$label-${UUID.randomUUID()}.sqlite")
        temporaryFiles += file
        SQLiteDatabase.openOrCreateDatabase(file, null).use(create)
        return file
    }

    private fun openReadOnly(file: File): SQLiteDatabase =
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)

    private fun openFileDescriptorCount(): Int = File("/proc/self/fd").list()?.size ?: 0
}
