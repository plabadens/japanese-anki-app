package dev.pierre.japaneseanki

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioGenerationTest {
    // Device gate: cancellation and serialization require an instrumented test with a real TTS engine.
    @Test fun cacheKeyIncludesProviderTextVoiceAndRateWithoutExposingTextInFilename() {
        val base = AudioCacheKey("azure", "猫", "ja-JP-NanamiNeural", 1f)
        assertNotEquals(base, base.copy(source = "android:engine"))
        assertNotEquals(base, base.copy(text = "犬"))
        assertNotEquals(base, base.copy(voice = "other"))
        assertNotEquals(base, base.copy(rate = 1.1f))
        assertFalse(base.filename().contains("猫"))
        assertEquals(base.filename(), base.copy().filename())
    }

    @Test fun cacheEvictsLeastRecentlyUsedReusableFilesAndKeepsDraftCopyAfterClear() {
        val root = Files.createTempDirectory("audio-cache-test").toFile()
        try {
            val cacheDir = File(root, "cache")
            val draftDir = File(root, "draft")
            val cache = ReusableAudioCache(cacheDir, maxBytes = 6)
            val first = AudioCacheKey("android:e", "一", "voice", 1f)
            val second = AudioCacheKey("android:e", "二", "voice", 1f)
            val third = AudioCacheKey("android:e", "三", "voice", 1f)
            assertTrue(cache.put(first, source(root, "first", "111")) != null)
            Thread.sleep(5)
            assertTrue(cache.put(second, source(root, "second", "222")) != null)
            Thread.sleep(5)
            assertTrue(cache.get(first) != null) // first is now more recent
            assertTrue(cache.put(third, source(root, "third", "333")) != null)
            assertNull(cache.get(second))
            assertTrue(cache.sizeBytes() <= 6)
            val activeDraft = cache.copyToActiveDraft(first, draftDir, "sentence.wav")
            cache.clear()
            assertEquals(0L, cache.sizeBytes())
            assertTrue(activeDraft.isFile)
            assertEquals("111", activeDraft.readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun oversizedFileIsNotCached() {
        val root = Files.createTempDirectory("audio-cache-limit").toFile()
        try {
            val cache = ReusableAudioCache(File(root, "cache"), maxBytes = 2)
            assertNull(cache.put(AudioCacheKey("source", "text", "voice", 1f), source(root, "large", "123")))
            assertEquals(0L, cache.sizeBytes())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun obsoleteAudioCompletionCannotReplaceNewRevisionAndFailureCanBeRetriedExplicitly() {
        val result = RevisionedAudioResult()
        val oldRequest = AudioRequestIdentity(AudioItem.SENTENCE, 0, "猫です")
        val currentRequest = AudioRequestIdentity(AudioItem.SENTENCE, 1, "犬です")
        result.begin(oldRequest)
        result.begin(currentRequest)
        assertFalse(result.complete(oldRequest, File("old.wav")))
        assertTrue(result.fail(currentRequest, retryable = true))
        assertTrue(result.state is AudioGenerationState.Failed)
        result.begin(currentRequest) // explicit retry of the failed item
        assertTrue(result.complete(currentRequest, File("new.wav")))
        assertEquals("new.wav", (result.state as AudioGenerationState.Ready).file.name)
    }

    private fun source(root: File, name: String, content: String): File = File(root, name).apply { writeText(content) }
}
