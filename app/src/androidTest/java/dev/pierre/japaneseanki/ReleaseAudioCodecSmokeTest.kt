package dev.pierre.japaneseanki

import android.media.MediaPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-only proof that Android's real decoder accepts the WAV, OGG, and MP3 files published
 * by DraftAudioFileStore. ShadowMediaPlayer cannot prove codec support; run this as part of the
 * signed-APK release smoke as well as the supplemental connected-device check.
 */
@RunWith(AndroidJUnit4::class)
class ReleaseAudioCodecSmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val fixtures = instrumentation.context.assets

    private fun root(): File {
        val root = File(context.filesDir, "audio-codec-smoke-${UUID.randomUUID()}")
        assertTrue(root.mkdirs())
        return root
    }

    @Test
    fun materializedWavOggAndMp3FilesDecodeAndPlayOnDevice() {
        val root = root()
        try {
            val draftDirectory = File(root, "draft").apply { mkdirs() }
            val stagingDirectory = File(root, "audio-staging").apply { mkdirs() }
            assertTrue(
                "staging must stay outside the FileProvider draft root",
                !stagingDirectory.canonicalPath.startsWith(draftDirectory.canonicalPath + File.separator),
            )
            val store = DraftAudioFileStore(draftDirectory, stagingDirectory)
            for (format in AudioFormat.entries) {
                val item = AudioItem.SENTENCE
                val controller = DraftAudioController(
                    store = store,
                    persistence = MapPersistence(),
                    synthesize = { _, output ->
                        when (format) {
                            AudioFormat.WAV -> writeSineWav(output)
                            AudioFormat.OGG -> copyAsset("tone.ogg", output)
                            AudioFormat.MP3 -> copyAsset("tone.mp3", output)
                        }
                    },
                )
                val key = AudioCacheKey("instrumentation", "テスト-${format.extension}", "voice", 1f)
                controller.setInput(
                    DraftAudioInput(
                        item = item,
                        identity = "identity-${format.extension}",
                        text = "テスト-${format.extension}",
                        revision = 0,
                        cacheKey = key,
                        expectedFormat = format,
                    ),
                )
                val result = controller.runAttempt(controller.confirm(item)!!)
                assertTrue("${format.extension} attempt failed: $result", result is DraftAudioAttemptResult.Accepted)
                val ready = controller.state(item) as DraftAudioState.Ready
                assertEquals(format.extension, ready.file.format)
                assertEquals(draftDirectory.canonicalFile, ready.file.file().parentFile)
                assertEquals(format, AudioFileValidator.requireFormat(ready.file.file()))

                val duration = playAndMeasure(ready.file.path)
                assertTrue(
                    "the platform decoder must play ${format.extension} audio, duration=$duration",
                    duration > 100,
                )
                controller.dispose()
            }
        } finally {
            root.deleteRecursively()
        }
    }

    private fun playAndMeasure(path: String): Int {
        val player = MediaPlayer()
        try {
            player.setDataSource(path)
            player.prepare()
            assertTrue("prepared audio must report a duration", player.duration > 0)
            player.start()
            Thread.sleep(200)
            return player.duration
        } finally {
            runCatching { player.reset() }
            player.release()
        }
    }

    private fun copyAsset(name: String, destination: File) {
        fixtures.open(name).use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        }
    }

    /** 0.6 s of a 440 Hz sine at 16 kHz mono, 16-bit PCM, written as a canonical WAV. */
    private fun writeSineWav(file: File) {
        val sampleRate = 16_000
        val samples = (sampleRate * 0.6).toInt()
        val data = ByteArray(samples * 2)
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        for (index in 0 until samples) {
            val value = (kotlin.math.sin(2.0 * Math.PI * 440.0 * index / sampleRate) * 12_000).toInt()
            buffer.putShort(value.toShort())
        }
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(36 + data.size).put("WAVE".toByteArray())
            .put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
            .putInt(sampleRate).putInt(sampleRate * 2).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(data.size)
        file.writeBytes(header.array() + data)
    }

    private class MapPersistence : DraftAudioPersistence {
        private val values = HashMap<String, String>()

        override fun read(key: String): String? = values[key]

        override fun write(changes: Map<String, String?>): Boolean {
            for ((key, value) in changes) {
                if (value == null) values.remove(key) else values[key] = value
            }
            return true
        }
    }
}
