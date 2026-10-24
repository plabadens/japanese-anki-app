package dev.pierre.japaneseanki

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.media.MediaPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.util.DataSource
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * JVM contract test for the audio route: files published by [DraftAudioFileStore] from a
 * fixture provider must have a supported container and be accepted by MediaPlayer's local-source
 * lifecycle. Robolectric shadows playback; device codec output belongs to release smoke.
 *
 * No credentials, paid services, NHK download or real Google synthesis: the sentence provider
 * is replaced by a local fixture that writes the exact container under test.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [37], qualifiers = "w390dp-h844dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DraftAudioPlaybackTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun root(): File {
        val root = File(context.filesDir, "audio-playback-${UUID.randomUUID()}")
        assertTrue(root.mkdirs())
        return root
    }

    @Test
    fun materializedWavOggAndMp3FilesPassTheJvmMediaPlayerContract() {
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
                    synthesize = { input, output ->
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
                val result = controller.runAttemptForTest(controller.confirm(item)!!)
                assertTrue("${format.extension} attempt failed: $result", result is DraftAudioAttemptResult.Accepted)
                val ready = controller.state(item) as DraftAudioState.Ready
                assertEquals(format.extension, ready.file.format)
                assertEquals(draftDirectory.canonicalFile, ready.file.file().parentFile)
                assertEquals(format, AudioFileValidator.requireFormat(ready.file.file()))

                val duration = playAndMeasure(ready.file.path)
                assertTrue(
                    "the shadowed MediaPlayer should report a configured duration for ${format.extension}: $duration",
                    duration > 100,
                )
                controller.dispose()
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun playbackLeaseOutlivesTheControllerAndBlocksRetirement() {
        val root = root()
        try {
            val draftDirectory = File(root, "draft").apply { mkdirs() }
            val store = DraftAudioFileStore(draftDirectory, File(root, "audio-staging"))
            val controller = DraftAudioController(
                store = store,
                persistence = MapPersistence(),
                synthesize = { _, output -> writeSineWav(output) },
            )
            controller.setInput(
                DraftAudioInput(
                    item = AudioItem.SENTENCE,
                    identity = "identity-playback",
                    text = "テスト",
                    revision = 0,
                    cacheKey = AudioCacheKey("instrumentation", "テスト", "voice", 1f),
                    expectedFormat = AudioFormat.WAV,
                ),
            )
            controller.runAttemptForTest(controller.confirm(AudioItem.SENTENCE)!!)
            val ready = controller.state(AudioItem.SENTENCE) as DraftAudioState.Ready

            val player = MediaPlayer()
            val lease = store.lease(ready.file, AudioFileOwner.PLAYBACK)
            try {
                ShadowMediaPlayer.addMediaInfo(
                    DataSource.toDataSource(ready.file.path),
                    ShadowMediaPlayer.MediaInfo(600, 0),
                )
                player.setDataSource(ready.file.path)
                player.prepare()
                player.start()
                assertEquals(DraftAudioRetirement.DELAYED_BY_OWNER, store.retire(ready.file))
                assertTrue("playing file must not be deleted", ready.file.file().isFile)
            } finally {
                player.release()
                lease.close()
            }
            assertEquals(DraftAudioRetirement.RETIRED, store.retire(ready.file))
            assertTrue(!ready.file.file().exists())
            controller.dispose()
        } finally {
            root.deleteRecursively()
        }
    }

    private fun playAndMeasure(path: String): Int {
        val player = MediaPlayer()
        try {
            ShadowMediaPlayer.addMediaInfo(
                DataSource.toDataSource(path),
                ShadowMediaPlayer.MediaInfo(600, 0),
            )
            player.setDataSource(path)
            player.prepare()
            assertTrue("prepared audio must report a duration", player.duration > 0)
            player.start()
            return player.duration
        } finally {
            runCatching { player.reset() }
            player.release()
        }
    }

    private fun copyAsset(name: String, destination: File) {
        requireNotNull(javaClass.classLoader?.getResourceAsStream("assets/$name")) {
            "Missing test audio fixture: $name"
        }.use { input ->
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
