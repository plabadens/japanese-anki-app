package dev.pierre.japaneseanki

import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Item 1: shared format lookup, cache independence, atomic draft materialization. */
@RunWith(AndroidJUnit4::class)
class AudioReliabilityTest {
    private fun root(): File = kotlin.io.path.createTempDirectory("audio-reliability").toFile()

    private fun key(text: String = "悪逆だ") =
        AudioCacheKey("google-chirp3-hd", text, "ja-JP-Chirp3-HD-Achernar", 1f)

    @Test
    fun detectsAndValidatesAllThreeFormats() {
        val root = root()
        try {
            val wav = AudioFixtures.wav(File(root, "a.wav"))
            val ogg = AudioFixtures.ogg(File(root, "a.ogg"))
            val mp3 = AudioFixtures.mp3(File(root, "a.mp3"))
            assertEquals(AudioFormat.WAV, AudioFileValidator.detect(wav))
            assertEquals(AudioFormat.OGG, AudioFileValidator.detect(ogg))
            assertEquals(AudioFormat.MP3, AudioFileValidator.detect(mp3))
            assertEquals(AudioFormat.WAV, AudioFileValidator.requireFormat(wav))
            assertEquals(AudioFormat.OGG, AudioFileValidator.requireFormat(ogg))
            assertEquals(AudioFormat.MP3, AudioFileValidator.requireFormat(mp3))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun extensionMustMatchContent() {
        val root = root()
        try {
            // The original defect in reverse: WAV bytes in a file named .mp3.
            val disguised = File(root, "sentence.mp3").also { AudioFixtures.wav(it) }
            try {
                AudioFileValidator.requireFormat(disguised)
                fail("mismatched extension must be rejected")
            } catch (_: IOException) {
                // expected
            }
            assertEquals(AudioFormat.WAV, AudioFileValidator.requireContentFormat(disguised))
            val unknown = File(root, "audio.flac").also { AudioFixtures.ogg(it) }
            try {
                AudioFileValidator.requireFormat(unknown)
                fail("unsupported extension must be rejected")
            } catch (_: IOException) {
                // expected
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun truncatedMp3AndGarbageAreRejected() {
        val root = root()
        try {
            val truncated = File(root, "cut.mp3")
            AudioFixtures.mp3(truncated)
            truncated.writeBytes(truncated.readBytes().copyOf(200))
            try {
                AudioFileValidator.requireContentFormat(truncated)
                fail("truncated MP3 must be rejected")
            } catch (_: IOException) {
                // expected
            }
            val garbage = File(root, "noise.wav").also { it.writeBytes(ByteArray(200) { 0x41 }) }
            assertNull(AudioFileValidator.detect(garbage))
            try {
                AudioFileValidator.requireContentFormat(garbage)
                fail("garbage must be rejected")
            } catch (_: IOException) {
                // expected
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun mp3CacheRoundTripAndDraftCopy() {
        val root = root()
        try {
            val cache = ReusableAudioCache(File(root, "cache"), maxBytes = 1024 * 1024)
            val source = AudioFixtures.mp3(File(root, "produced.mp3"))
            val stored = cache.put(key(), source)
            assertNotNull("MP3 must be stored", stored)
            assertEquals(AudioFormat.MP3.extension, stored!!.extension)

            // This lookup used to miss because only wav/ogg were probed.
            val cached = cache.get(key())
            assertNotNull("MP3 cache lookup must succeed", cached)
            assertTrue(AudioFileValidator.requireFormat(cached!!) == AudioFormat.MP3)

            val draftDir = File(root, "draft").apply { mkdirs() }
            val draft = cache.copyToActiveDraft(key(), draftDir, "sentence-1.mp3")
            assertTrue(draft.isFile)
            assertEquals(AudioFormat.MP3, AudioFileValidator.requireFormat(draft))
            assertTrue(draft.parentFile == draftDir)

            cache.clear()
            assertNull(cache.get(key()))
            assertTrue("durable draft must survive cache clearing", draft.isFile)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun zeroBudgetCacheStoresNothingButDraftStillMaterializes() {
        val root = root()
        try {
            val cache = ReusableAudioCache(File(root, "cache"), maxBytes = 0)
            val source = AudioFixtures.wav(File(root, "source.wav"))
            assertNull(cache.put(key(), source))
            assertNull(cache.get(key()))
            assertTrue(cache.store(key(), source) is AudioCacheStoreResult.Skipped)
            val draft = AudioDraftMaterializer.materialize(source, File(root, "draft"), "sentence-1.wav")
            assertTrue(draft.isFile)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun recordingLargerThanTheCacheBudgetIsSkipped() {
        val root = root()
        try {
            val source = AudioFixtures.wav(File(root, "source.wav"), sampleFrames = 2_000)
            val cache = ReusableAudioCache(File(root, "cache"), maxBytes = 100)
            assertNull(cache.put(key(), source))
            assertTrue(cache.store(key(), source) is AudioCacheStoreResult.Skipped)
            val draft = AudioDraftMaterializer.materialize(source, File(root, "draft"), "sentence-1.wav")
            assertEquals(source.length(), draft.length())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun pinnedEntrySurvivesEvictionPressureAndStillMaterializes() {
        val root = root()
        try {
            val first = AudioFixtures.wav(File(root, "first.wav"), sampleFrames = 2_000)
            val second = AudioFixtures.wav(File(root, "second.wav"), sampleFrames = 2_000)
            val cache = ReusableAudioCache(File(root, "cache"), maxBytes = (first.length() * 1.5).toLong())
            val firstKey = key("一")
            assertNotNull(cache.put(firstKey, first))

            val lease = cache.open(firstKey)
            assertNotNull(lease)
            try {
                // The existing entry is pinned, so eviction pressure cannot delete the file
                // currently being copied to the durable draft.
                runCatching { cache.put(key("二"), second) }
                assertTrue("pinned cache entry must survive pressure", lease!!.file.isFile)
                val draft = AudioDraftMaterializer.materialize(lease.file, File(root, "draft"), "word-1.wav")
                assertEquals(AudioFormat.WAV, AudioFileValidator.requireFormat(draft))
            } finally {
                lease!!.close()
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun clearAndEvictionSkipPinnedEntriesUntilReleased() {
        val root = root()
        try {
            val cache = ReusableAudioCache(File(root, "cache"), maxBytes = 1024 * 1024)
            val source = AudioFixtures.ogg(File(root, "source.ogg"))
            cache.put(key(), source)
            val lease = cache.open(key())
            assertNotNull(lease)
            cache.clear()
            assertTrue("leased entry must survive clear()", lease!!.file.isFile)
            lease.close()
            cache.clear()
            assertNull(cache.get(key()))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun concurrentClearDuringMaterializationCannotTruncateTheDraft() {
        val root = root()
        try {
            val cache = ReusableAudioCache(File(root, "cache"), maxBytes = 8 * 1024 * 1024)
            val source = AudioFixtures.wav(File(root, "source.wav"), sampleFrames = 200_000)
            cache.put(key(), source)
            val lease = cache.open(key()) ?: error("expected a cache hit")
            val started = CountDownLatch(1)
            val clearer = Thread {
                started.countDown()
                repeat(200) { cache.clear() }
            }
            clearer.start()
            val draft = try {
                started.await(2, TimeUnit.SECONDS)
                AudioDraftMaterializer.materialize(lease.file, File(root, "draft"), "sentence-race.wav")
            } finally {
                lease.close()
                clearer.join(5_000)
            }
            assertEquals(source.length(), draft.length())
            assertEquals(AudioFormat.WAV, AudioFileValidator.requireFormat(draft))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun invalidProviderOutputIsNotCachedOrSwallowed() {
        val root = root()
        try {
            val cache = ReusableAudioCache(File(root, "cache"))
            val bad = File(root, "bad.mp3").also { it.writeBytes(ByteArray(400)) }
            try {
                cache.store(key(), bad)
                fail("invalid audio must not be swallowed as a cache failure")
            } catch (_: IOException) {
                // expected
            }
            assertNull(cache.get(key()))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun cacheWriteFailureIsNonFatalAndSanitized() {
        val root = root()
        try {
            val failingMove = object : AudioFileOperations {
                override fun move(source: File, destination: File) = throw IOException("rename failed")
                override fun delete(file: File): Boolean = file.delete()
                override fun sync(file: File) = Unit
            }
            val cache = ReusableAudioCache(File(root, "cache"), operations = failingMove)
            val source = AudioFixtures.mp3(File(root, "source.mp3"))
            val result = cache.store(key(), source)
            assertTrue(result is AudioCacheStoreResult.Failed)
            val reason = (result as AudioCacheStoreResult.Failed).reason
            assertFalse("diagnostics must not leak paths", reason.contains(root.name))
            assertFalse(reason.contains(source.name))
            assertTrue("no partial cache entry", File(root, "cache").listFiles().orEmpty().none { it.isFile })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun materializerPublishesAtomicallyAndCleansPartialOutputOnFailure() {
        val root = root()
        try {
            val source = AudioFixtures.ogg(File(root, "source.ogg"))
            val draftDir = File(root, "draft")

            val failingMove = object : AudioFileOperations {
                override fun move(source: File, destination: File) = throw IOException("rename failed")
                override fun delete(file: File): Boolean = file.delete()
                override fun sync(file: File) = Unit
            }
            try {
                AudioDraftMaterializer.materialize(source, draftDir, "word.wav", failingMove)
                fail("rename failure must surface")
            } catch (_: IOException) {
                // expected
            }
            assertFalse("no partial draft at the destination", File(draftDir, "word.wav").exists())
            assertTrue("temporary files are cleaned up", draftDir.listFiles().orEmpty().isEmpty())

            // Source is Ogg; a .wav destination must be rejected before publishing.
            try {
                AudioDraftMaterializer.materialize(source, draftDir, "word.wav")
                fail("extension mismatch must be rejected")
            } catch (_: IOException) {
                // expected
            }
            val published = AudioDraftMaterializer.materialize(source, draftDir, "word.ogg")
            assertEquals(AudioFormat.OGG, AudioFileValidator.requireFormat(published))
            assertTrue(published.parentFile == draftDir.canonicalFile)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun cacheKeyIgnoresFilenameAndStaysStable() {
        val base = key()
        assertEquals(base.filename(), base.copy().filename())
        assertTrue(base.filename() != base.copy(hintIdentity = "hints=v1:a").filename())
    }
}
