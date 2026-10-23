package dev.pierre.japaneseanki

import androidx.test.ext.junit.runners.AndroidJUnit4

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Nhk2016PronunciationSourceTest {
    @Test fun bundledIndexLookupIsOfflineAndOnlyTheRecordingUsesNetwork() {
        val root = Files.createTempDirectory("nhk-bundled-index").toFile()
        try {
            val urls = mutableListOf<String>()
            val index = """{"meta":{"version":2},"headwords":{"猫":["20170616125901.ogg","20170616125902.ogg"],"犬":["20170616125903.ogg"]}}"""
            val source = Nhk2016PronunciationSource(
                readIndexText = { index },
                spellingFor = { "猫" },
                connectionFactory = { url ->
                    urls += url.toString()
                    FakeConnection(url, validOgg())
                },
            )

            assertEquals("20170616125901.ogg", source.findRecording("猫"))
            assertTrue("index lookup must not open a network connection", urls.isEmpty())

            val output = File(root, "draft/word.ogg")
            val request = AudioSynthesisRequest(AudioItem.VOCABULARY, "cat", 0, "entry")
            assertEquals(output, source.synthesize(request, output))
            assertEquals(
                listOf("https://raw.githubusercontent.com/Ajatt-Tools/nhk_2016_pronunciations_index/${Nhk2016PronunciationSource.SOURCE_VERSION}/media/20170616125901.ogg"),
                urls,
            )
            assertTrue(AudioFileValidator.requireSupported(output).isFile)
            assertEquals("GPL-3.0 (index repository only; recording rights are not verified)", Nhk2016PronunciationSource.SOURCE_LICENSE)
        } finally { root.deleteRecursively() }
    }

    @Test fun unresolvedEntryAndMissingExactSpellingDoNotFetchRecording() {
        val urls = mutableListOf<String>()
        val source = Nhk2016PronunciationSource(
            readIndexText = { """{"headwords":{"猫":["20170616125901.ogg"]}}""" },
            spellingFor = { "ねこ" },
            connectionFactory = { url -> urls += url.toString(); FakeConnection(url, validOgg()) },
        )

        assertTrue(runCatching {
            source.synthesize(AudioSynthesisRequest(AudioItem.VOCABULARY, "cat", 0), File("unused.ogg"))
        }.isFailure)
        val missing = runCatching {
            source.synthesize(AudioSynthesisRequest(AudioItem.VOCABULARY, "cat", 0, "entry"), File("unused.ogg"))
        }.exceptionOrNull()
        assertTrue(missing is AudioSourceMissingException)
        assertTrue(urls.isEmpty())
    }

    @Test fun rejectsMalformedBundledIndexAndMalformedOgg() {
        val root = Files.createTempDirectory("nhk-malformed-bundle").toFile()
        try {
            val source = Nhk2016PronunciationSource(readIndexText = { "{bad" })
            val failure = runCatching { source.findRecording("猫") }.exceptionOrNull()
            assertTrue(failure is java.io.IOException)
            assertTrue(failure!!.message.orEmpty().contains("Bundled NHK"))
            assertTrue(runCatching {
                AudioFileValidator.requireSupported(File(root, "bad.ogg").also { it.writeBytes("OggS".toByteArray()) })
            }.isFailure)
        } finally { root.deleteRecursively() }
    }

    @Test fun completedOggRecordingCanBeCachedAndCopiedToDraftWithMatchingExtension() {
        val root = Files.createTempDirectory("nhk-ogg-cache").toFile()
        try {
            val cache = ReusableAudioCache(File(root, "cache"))
            val key = AudioCacheKey("NHK-2016", "悪逆", "NHK-2016", 1f)
            val media = File(root, "recording.ogg").also { it.writeBytes(validOgg()) }
            assertTrue(cache.put(key, media)?.extension == "ogg")
            val draft = cache.copyToActiveDraft(key, File(root, "draft"), "vocabulary.ogg")
            assertEquals("ogg", draft.extension)
            assertTrue(AudioFileValidator.requireSupported(draft).isFile)
            assertTrue(runCatching { cache.copyToActiveDraft(key, File(root, "other"), "vocabulary.wav") }.isFailure)
            assertFalse(File(root, "index.json").exists())
        } finally { root.deleteRecursively() }
    }

    private fun validOgg(): ByteArray {
        val page = ByteArrayOutputStream()
        page.write("OggS".toByteArray())
        page.write(0)
        page.write(ByteArray(21))
        page.write(1)
        page.write(1)
        page.write(0)
        return page.toByteArray()
    }

    private class FakeConnection(
        url: URL,
        private val body: ByteArray,
    ) : HttpURLConnection(url) {
        override fun getResponseCode() = HTTP_OK
        override fun getInputStream(): InputStream = ByteArrayInputStream(body)
        override fun getContentLengthLong() = body.size.toLong()
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
    }
}
