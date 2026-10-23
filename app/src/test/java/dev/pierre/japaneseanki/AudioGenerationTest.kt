package dev.pierre.japaneseanki

import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AudioGenerationTest {
    private fun validWav(file: File) {
        val data = ByteArray(64)
        val header = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(36 + data.size).put("WAVE".toByteArray())
            .put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
            .putInt(16_000).putInt(32_000).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(data.size)
        file.writeBytes(header.array() + data)
    }

    @Test
    fun cacheKeyIncludesHintsAndVoice() {
        val base = AudioCacheKey("google-chirp3-hd", "悪逆だ", "ja-JP-Chirp3-HD-Achernar", 1f, hintIdentity = "hints=v1:a")
        val differentHints = base.copy(hintIdentity = "hints=v1:b")
        val differentVoice = base.copy(voice = "ja-JP-Chirp3-HD-Charon")
        assertTrue(base.filename() != differentHints.filename())
        assertTrue(base.filename() != differentVoice.filename())
        assertEquals(base.filename(), base.copy().filename())
    }

    @Test
    fun cacheStoresValidAudioAndKeepsDraftCopyOutsideCache() {
        val root = kotlin.io.path.createTempDirectory("audio-cache").toFile()
        try {
            val cache = ReusableAudioCache(File(root, "cache"), maxBytes = 1024 * 1024)
            val source = File(root, "source.wav").also(::validWav)
            val key = AudioCacheKey("nhk-2016", "悪逆", "NHK-2016", 1f)
            val stored = cache.put(key, source)
            assertNotNull(stored)
            assertEquals(stored, cache.get(key))

            val draftDir = File(root, "draft").apply { mkdirs() }
            val draft = cache.copyToActiveDraft(key, draftDir, "word.wav")
            assertTrue(draft.isFile)
            assertTrue(draft.parentFile == draftDir)

            cache.clear()
            assertNull(cache.get(key))
            assertTrue("draft copy must survive cache clearing", draft.isFile)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun cacheRejectsInvalidAudio() {
        val root = kotlin.io.path.createTempDirectory("audio-cache").toFile()
        try {
            val cache = ReusableAudioCache(File(root, "cache"))
            val bad = File(root, "bad.wav").apply { writeText("not audio") }
            var failed = false
            try {
                cache.put(AudioCacheKey("s", "t", "v", 1f), bad)
            } catch (_: Exception) {
                failed = true
            }
            assertTrue(failed)
        } finally {
            root.deleteRecursively()
        }
    }

}
