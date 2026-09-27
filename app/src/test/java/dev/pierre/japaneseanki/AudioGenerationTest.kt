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
        val root = Files.createTempDirectory(java.nio.file.Path.of("build"), "audio-cache-test").toFile()
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
        val root = Files.createTempDirectory(java.nio.file.Path.of("build"), "audio-cache-limit").toFile()
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

    @Test fun vocabularyUsesRecordingThenAzureThenOfflineAndSentenceSkipsRecording() {
        val root = Files.createTempDirectory(java.nio.file.Path.of("build"), "audio-chain-order").toFile()
        try {
            val calls = mutableListOf<String>()
            fun binding(source: AudioSource, fails: Boolean): AudioProviderBinding = AudioProviderBinding(
                AudioSourceIdentity(source, source.name, engine = "engine", voice = "voice", rate = 1f),
                AudioProvider { _, output ->
                    calls += source.name
                    if (fails) throw IllegalStateException("unavailable")
                    writeValidWav(output)
                },
            )
            val chain = AudioSourceChain(
                AudioSourceConfiguration(recordedPronunciationEnabled = true, azureConfigured = true),
                binding(AudioSource.RECORDED_PRONUNCIATION, true),
                binding(AudioSource.AZURE, false),
                binding(AudioSource.ANDROID_OFFLINE, false),
            )
            val vocabulary = chain.generate(AudioSynthesisRequest(AudioItem.VOCABULARY, "猫", 0), File(root, "word"))
            assertEquals(listOf("RECORDED_PRONUNCIATION", "AZURE"), calls)
            assertTrue(vocabulary is AudioChainResult.Ready && vocabulary.source == AudioSource.AZURE)
            calls.clear()
            val sentence = chain.generate(AudioSynthesisRequest(AudioItem.SENTENCE, "猫です", 0), File(root, "sentence"))
            assertEquals(listOf("AZURE"), calls)
            assertTrue(sentence is AudioChainResult.Ready)
        } finally { root.deleteRecursively() }
    }

    @Test fun azureFailureFallsBackToOfflineProvider() {
        val root = Files.createTempDirectory(java.nio.file.Path.of("build"), "audio-offline-fallback").toFile()
        try {
            val calls = mutableListOf<AudioSource>()
            fun binding(source: AudioSource, fails: Boolean) = AudioProviderBinding(
                AudioSourceIdentity(source, source.name, voice = "voice", rate = 1f),
                AudioProvider { _, output ->
                    calls += source
                    if (fails) throw IllegalStateException()
                    writeValidWav(output)
                },
            )
            val chain = AudioSourceChain(
                AudioSourceConfiguration(azureConfigured = true), null,
                binding(AudioSource.AZURE, true), binding(AudioSource.ANDROID_OFFLINE, false),
            )
            val result = chain.generate(AudioSynthesisRequest(AudioItem.SENTENCE, "text", 2), root)
            assertEquals(listOf(AudioSource.AZURE, AudioSource.ANDROID_OFFLINE), calls)
            assertTrue(result is AudioChainResult.Ready && result.source == AudioSource.ANDROID_OFFLINE)
        } finally { root.deleteRecursively() }
    }

    @Test fun recordingsAreDisabledByDefaultAndCacheSeparatesEngine() {
        assertFalse(AudioSourceConfiguration().recordedPronunciationEnabled)
        val base = AudioCacheKey("provider", "text", "voice", 1f, engine = "engine-one")
        assertNotEquals(base, base.copy(engine = "engine-two"))
    }

    @Test fun allUnavailableReturnsFailureAndUnchangedReadyRequestDoesNotRestart() {
        val root = Files.createTempDirectory(java.nio.file.Path.of("build"), "audio-chain-failure").toFile()
        try {
            val chain = AudioSourceChain(AudioSourceConfiguration(), null, null, null)
            assertTrue(chain.generate(AudioSynthesisRequest(AudioItem.SENTENCE, "text", 1), root) is AudioChainResult.Failed)
            val result = RevisionedAudioResult()
            val request = AudioRequestIdentity(AudioItem.SENTENCE, 1, "text")
            assertTrue(result.beginIfNeeded(request))
            assertTrue(result.complete(request, source(root, "ready.wav", "wav")))
            assertFalse(result.beginIfNeeded(request))
            assertFalse(result.retry(request))
            val failedRequest = AudioRequestIdentity(AudioItem.SENTENCE, 2, "changed")
            assertTrue(result.beginIfNeeded(failedRequest))
            assertTrue(result.fail(failedRequest, true))
            assertTrue(result.retry(failedRequest))
        } finally { root.deleteRecursively() }
    }

    private fun writeValidWav(file: File): File {
        val dataSize = 2
        val bytes = java.nio.ByteBuffer.allocate(46).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()).putInt(38).put("WAVE".toByteArray())
        bytes.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(8000)
        bytes.putInt(16000).putShort(2).putShort(16)
        bytes.put("data".toByteArray()).putInt(dataSize).putShort(0)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes.array())
        return file
    }

    private fun source(root: File, name: String, content: String): File = File(root, name).apply { writeText(content) }
}
