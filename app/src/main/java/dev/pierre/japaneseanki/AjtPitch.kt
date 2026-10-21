package dev.pierre.japaneseanki

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.util.zip.GZIPInputStream

/** One AJT-sourced pitch record: class-span HTML exactly as supplied upstream plus accent number. */
data class AjtPitchRecord(val html: String, val accent: Int)

/**
 * Handle contract owned by [AjtPitchRepository]. Implementations are opened only by the
 * repository, are queried under the repository lock, and are closed by it when validation fails.
 */
internal interface AjtPitchStore {
    /**
     * Bounded table/schema probe. Returns `true` when the index can answer lookups and `false`
     * when it is valid but holds no records. Throws when the index is unusable; in that case the
     * implementation also discards its installed copy so an explicit retry can reinstall it.
     */
    fun probe(): Boolean

    fun pitch(expression: String, reading: String): List<AjtPitchRecord>

    fun close()
}

/**
 * Local index over AJT Japanese's formatted pitch records (pinned revision), preserving the
 * nested nasal/devoicing markup. A missing record is a dataset miss, not a reason to infer one.
 */
internal class AjtPitchIndex internal constructor(
    private val database: SQLiteDatabase,
    private val installedFile: File,
) : AjtPitchStore {
    /** Exposed so the ownership tests can prove a rejected provisional handle was closed. */
    val isOpen: Boolean get() = database.isOpen
    override fun pitch(expression: String, reading: String): List<AjtPitchRecord> {
        if (expression.isBlank() || reading.isBlank()) return emptyList()
        return database.rawQuery(
            "SELECT pitch_html, pitch_number FROM pitch_ajt WHERE expression=? AND reading=? ORDER BY source_order",
            arrayOf(expression, hiragana(reading)),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val html = cursor.getString(0).orEmpty()
                    if (html.isNotBlank()) add(AjtPitchRecord(html, cursor.getInt(1)))
                }
            }
        }
    }

    /**
     * Schema and row-presence probe. This is deliberately bounded: the schema check reads
     * `PRAGMA table_info` only, and the emptiness check stops at the first row instead of
     * counting every record on each draft.
     */
    override fun probe(): Boolean = try {
        val columns = database.rawQuery("PRAGMA table_info(pitch_ajt)", null).use { cursor ->
            buildSet {
                val nameColumn = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) add(cursor.getString(nameColumn))
            }
        }
        check(REQUIRED_COLUMNS.all(columns::contains)) {
            "AJT pitch index schema is unsupported: $columns"
        }
        database.rawQuery("SELECT 1 FROM pitch_ajt LIMIT 1", null).use { it.moveToFirst() }
    } catch (failure: Exception) {
        // The installed copy cannot answer lookups. Drop it, so an explicit retry reinstalls the
        // bundled asset instead of reopening the same unusable file forever.
        installedFile.delete()
        throw failure
    }

    override fun close() = database.close()

    companion object {
        const val ASSET_NAME = "pitch_ajt.sqlite"
        const val GZIP_ASSET_NAME = "pitch_ajt.sqlite.gz"
        const val SCHEMA_VERSION = 1

        /** Returns an opened, unvalidated handle, or `null` when the bundled index is unavailable. */
        fun open(context: Context): AjtPitchIndex? {
            val target = File(context.filesDir, "pitch_ajt-v$SCHEMA_VERSION.sqlite")
            return try {
                if (!target.isFile || target.length() < MIN_BYTES) install(context, target)
                AjtPitchIndex(
                    SQLiteDatabase.openDatabase(
                        target.absolutePath,
                        null,
                        SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
                    ),
                    target,
                )
            } catch (error: Exception) {
                android.util.Log.e("AjtPitchIndex", "Could not open the bundled AJT pitch index", error)
                // Never leave a half-written or unreadable install behind for the next attempt.
                target.delete()
                null
            }
        }

        private fun install(context: Context, target: File) {
            val temporary = File(context.filesDir, ".${target.name}.tmp")
            temporary.delete()
            try {
                // The Android asset pipeline expands .gz assets, so the packaged name is .sqlite;
                // an installed-source build may still expose the .gz file.
                val plain = runCatching { context.assets.open(ASSET_NAME) }.getOrNull()
                temporary.outputStream().use { out ->
                    if (plain != null) plain.use { it.copyTo(out) }
                    else context.assets.open(GZIP_ASSET_NAME).use { raw ->
                        GZIPInputStream(raw).use { packed -> packed.copyTo(out) }
                    }
                }
                check(temporary.length() > MIN_BYTES) { "Pitch index asset is incomplete" }
                if (!temporary.renameTo(target)) {
                    temporary.copyTo(target, overwrite = true)
                }
            } finally {
                temporary.delete()
            }
        }

        private val REQUIRED_COLUMNS = setOf("expression", "reading", "pitch_html", "pitch_number", "source_order")
        private const val MIN_BYTES = 1024L * 1024L
    }
}
