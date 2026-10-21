package dev.pierre.japaneseanki

import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class AndroidPlatformSmokeTest {
    @Test
    fun androidSqliteCreatesAndQueriesRows() {
        val database = SQLiteDatabase.create(null)
        try {
            database.execSQL("CREATE TABLE smoke (value TEXT NOT NULL)")
            database.execSQL("INSERT INTO smoke (value) VALUES (?)", arrayOf("日本語"))

            database.rawQuery("SELECT value FROM smoke", null).use { cursor ->
                assertTrue("Expected one SQLite row", cursor.moveToFirst())
                assertEquals("日本語", cursor.getString(0))
                assertTrue("Expected exactly one SQLite row", cursor.isLast)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun androidBitmapEncodesAndDecodesWebp() {
        val bitmap = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888)
        try {
            val encoded = java.io.ByteArrayOutputStream()
            assertTrue("WebP compression failed", bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 75, encoded))

            val decoded = BitmapFactory.decodeByteArray(encoded.toByteArray(), 0, encoded.size())
            try {
                assertTrue("WebP output did not decode", decoded != null)
                assertEquals(3, decoded!!.width)
                assertEquals(2, decoded.height)
            } finally {
                decoded?.recycle()
            }
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun androidMediaPlayerPlaysLocalAudioWithoutNetwork() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val wav = File.createTempFile("platform-smoke-", ".wav", context.cacheDir)
        val player = MediaPlayer()
        val completed = CountDownLatch(1)
        val playbackError = AtomicReference<String?>(null)

        try {
            wav.writeBytes(silentPcmWav(durationMillis = 1_000))
            player.setDataSource(wav.absolutePath)
            player.setVolume(0f, 0f)
            player.setOnCompletionListener { completed.countDown() }
            player.setOnErrorListener { _, what, extra ->
                playbackError.set("MediaPlayer error what=$what extra=$extra")
                completed.countDown()
                true
            }
            player.prepare()
            player.start()

            assertTrue("Local MediaPlayer playback did not complete", completed.await(10, TimeUnit.SECONDS))
            assertNull(playbackError.get())
        } finally {
            player.release()
            wav.delete()
        }
    }

    private fun silentPcmWav(durationMillis: Int): ByteArray {
        val sampleRate = 8_000
        val channels = 1
        val bitsPerSample = 16
        val dataSize = sampleRate * durationMillis / 1_000 * channels * bitsPerSample / 8
        return ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(StandardCharsets.US_ASCII))
            putInt(36 + dataSize)
            put("WAVE".toByteArray(StandardCharsets.US_ASCII))
            put("fmt ".toByteArray(StandardCharsets.US_ASCII))
            putInt(16)
            putShort(1)
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(sampleRate * channels * bitsPerSample / 8)
            putShort((channels * bitsPerSample / 8).toShort())
            putShort(bitsPerSample.toShort())
            put("data".toByteArray(StandardCharsets.US_ASCII))
            putInt(dataSize)
            put(ByteArray(dataSize))
        }.array()
    }
}