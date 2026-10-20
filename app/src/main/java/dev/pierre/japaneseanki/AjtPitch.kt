package dev.pierre.japaneseanki

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.util.zip.GZIPInputStream

/** One AJT-sourced pitch record: class-span HTML exactly as supplied upstream plus accent number. */
data class AjtPitchRecord(val html: String, val accent: Int)

/**
 * Local index over AJT Japanese's formatted pitch records (pinned revision), preserving the
 * nested nasal/devoicing markup. A missing record is a dataset miss, not a reason to infer one.
 */
class AjtPitchIndex private constructor(private val database: SQLiteDatabase) {
    fun pitch(expression: String, reading: String): List<AjtPitchRecord> {
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

    fun close() = database.close()

    companion object {
        const val ASSET_NAME = "pitch_ajt.sqlite"
        const val GZIP_ASSET_NAME = "pitch_ajt.sqlite.gz"
        const val SCHEMA_VERSION = 1

        fun open(context: Context): AjtPitchIndex? = try {
            val target = File(context.filesDir, "pitch_ajt-v$SCHEMA_VERSION.sqlite")
            if (!target.isFile || target.length() < MIN_BYTES) {
                val temporary = File(context.filesDir, ".${target.name}.tmp")
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
                    temporary.delete()
                }
            }
            val db = SQLiteDatabase.openDatabase(
                target.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
            )
            db.rawQuery("SELECT COUNT(*) FROM pitch_ajt LIMIT 1", null).use { it.moveToFirst() }
            AjtPitchIndex(db)
        } catch (error: Exception) {
            android.util.Log.e("AjtPitchIndex", "Could not open the bundled AJT pitch index", error)
            null
        }

        private const val MIN_BYTES = 1024L * 1024L
    }
}
