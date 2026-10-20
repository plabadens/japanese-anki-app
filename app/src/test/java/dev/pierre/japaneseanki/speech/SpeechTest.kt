package dev.pierre.japaneseanki.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Base64

class SpeechHintBuilderTest {
    @Test
    fun kanjiSpansBecomePhraseHintsAndKanaNeedsNone() {
        val sentence = "悪逆な行いだ"
        val result = ReadingHintBuilder.build(
            sentence,
            listOf(
                ReadingHintSpan(0, 2, "悪逆", "アクギャク"),
                ReadingHintSpan(2, 3, "な", "な"),
            ),
        )
        assertTrue(result is HintResult.Ready)
        assertEquals(listOf(ReadingHint("悪逆", "アクギャク")), (result as HintResult.Ready).hints)
    }

    @Test
    fun kanaOnlySentenceNeedsNoHints() {
        val result = ReadingHintBuilder.build("これはぺんです", listOf(ReadingHintSpan(0, 3, "これは", "これは")))
        assertEquals(HintResult.Ready(emptyList()), result)
    }

    @Test
    fun sameSurfaceWithDifferentReadingsIsImpossible() {
        val sentence = "今日は今日"
        val result = ReadingHintBuilder.build(
            sentence,
            listOf(
                ReadingHintSpan(0, 2, "今日", "キョウ"),
                ReadingHintSpan(3, 5, "今日", "コンニチ"),
            ),
        )
        assertTrue(result is HintResult.Impossible)
    }

    @Test
    fun overlappingOrMismatchedSpansAreRejected() {
        assertTrue(
            ReadingHintBuilder.build(
                "悪逆",
                listOf(
                    ReadingHintSpan(0, 2, "悪逆", "アクギャク"),
                    ReadingHintSpan(1, 2, "逆", "ギャク"),
                ),
            ) is HintResult.Impossible,
        )
        assertTrue(
            ReadingHintBuilder.build("悪逆", listOf(ReadingHintSpan(0, 2, "悪", "アク"))) is HintResult.Impossible,
        )
    }

    @Test
    fun unresolvedOrNonKanaReadingIsRejected() {
        assertTrue(
            ReadingHintBuilder.build("悪逆", listOf(ReadingHintSpan(0, 2, "悪逆", ""))) is HintResult.Impossible,
        )
        assertTrue(
            ReadingHintBuilder.build("悪逆", listOf(ReadingHintSpan(0, 2, "悪逆", "akugyaku"))) is HintResult.Impossible,
        )
    }

    @Test
    fun cacheFragmentIsStableAndOrderIndependent() {
        val a = ReadingHintBuilder.cacheFragment(listOf(ReadingHint("悪", "アク"), ReadingHint("逆", "ギャク")))
        val b = ReadingHintBuilder.cacheFragment(listOf(ReadingHint("逆", "ギャク"), ReadingHint("悪", "アク")))
        val c = ReadingHintBuilder.cacheFragment(listOf(ReadingHint("悪", "アク")))
        assertEquals(a, b)
        assertTrue(a != c)
        assertTrue(a.contains("v${ReadingHintBuilder.VERSION}"))
    }
}

class GoogleSpeechProviderTest {
    private class FakeTransport(
        private val status: Int = 200,
        private val body: ByteArray = ByteArray(0),
    ) : GoogleSpeechTransport {
        var url: String? = null
        var apiKey: String? = null
        var json: String? = null
        var calls = 0

        override fun get(url: String, apiKey: String, timeoutMillis: Int): SpeechHttpResponse {
            this.url = url
            this.apiKey = apiKey
            calls++
            return SpeechHttpResponse(status, body)
        }

        override fun post(url: String, apiKey: String, json: ByteArray, timeoutMillis: Int): SpeechHttpResponse {
            this.url = url
            this.apiKey = apiKey
            this.json = json.toString(Charsets.UTF_8)
            calls++
            return SpeechHttpResponse(status, body)
        }
    }

    private val voice = CloudVoice("ja-JP-Chirp3-HD-Achernar", "Achernar", listOf("ja-JP"), 24_000)

    private fun synthesisResponse(): ByteArray {
        val mp3 = ByteArray(512).also {
            it[0] = 0x49; it[1] = 0x44; it[2] = 0x33; it[3] = 4
        }
        val base64 = Base64.getEncoder().encodeToString(mp3)
        return """{"audioContent":"$base64"}""".toByteArray(Charsets.UTF_8)
    }

    @Test
    fun serializedRequestCarriesExactVoiceAndYomiganaHints() {
        val hints = listOf(ReadingHint("悪逆", "アクギャク"))
        val request = SentenceSpeechRequest("悪逆な行い", voice, hints)
        val json = buildSynthesisJson(request)
        assertTrue(json.contains("\"text\":\"悪逆な行い\""))
        assertTrue(json.contains("\"phrase\":\"悪逆\""))
        assertTrue(json.contains("\"phoneticEncoding\":\"PHONETIC_ENCODING_JAPANESE_YOMIGANA\""))
        assertTrue(json.contains("\"pronunciation\":\"アクギャク\""))
        assertTrue(json.contains("\"languageCode\":\"ja-JP\""))
        assertTrue(json.contains("\"name\":\"ja-JP-Chirp3-HD-Achernar\""))
        assertTrue(json.contains("\"audioEncoding\":\"MP3\""))
        assertFalse(json.contains("<ruby>"))
        assertFalse(json.contains("pitch"))
    }

    @Test
    fun kanaOnlyRequestOmitsCustomPronunciations() {
        val json = buildSynthesisJson(SentenceSpeechRequest("これはぺんです", voice, emptyList()))
        assertFalse(json.contains("customPronunciations"))
    }

    @Test
    fun providerSendsAuthHeaderMaterialAndValidatesMp3() {
        val transport = FakeTransport(200, synthesisResponse())
        val provider = GoogleChirpSpeechProvider({ "google-key" }, transport, 1_000)
        val output = File.createTempFile("speech-", ".mp3")
        try {
            val request = SentenceSpeechRequest("悪逆", voice, listOf(ReadingHint("悪逆", "アクギャク")))
            provider.synthesize(request, output)
            assertEquals("google-key", transport.apiKey)
            assertEquals("https://texttospeech.googleapis.com/v1/text:synthesize", transport.url)
            assertTrue(transport.json.orEmpty().contains("アクギャク"))
            assertTrue(output.length() > 0)
        } finally {
            output.delete()
        }
    }

    @Test
    fun rejectedHintsFailWithoutUnhintedRetry() {
        val transport = FakeTransport(400, ByteArray(0))
        val provider = GoogleChirpSpeechProvider({ "k" }, transport, 1_000)
        val output = File.createTempFile("speech-", ".mp3")
        try {
            val error = runCatching {
                provider.synthesize(
                    SentenceSpeechRequest("悪逆", voice, listOf(ReadingHint("悪逆", "アクギャク"))),
                    output,
                )
            }.exceptionOrNull()
            assertTrue(error is SpeechException)
            assertEquals(SpeechFailure.INVALID_REQUEST, (error as SpeechException).failure)
            assertEquals(1, transport.calls)
            assertTrue(transport.json.orEmpty().contains("customPronunciations"))
        } finally {
            output.delete()
        }
    }

    @Test
    fun redirectsAndMissingCredentialsFailClosed() {
        val redirect = GoogleChirpSpeechProvider({ "k" }, FakeTransport(302, ByteArray(0)), 1_000)
        val error = runCatching { redirect.listVoices() }.exceptionOrNull()
        assertEquals(SpeechFailure.AUTHORIZATION, (error as SpeechException).failure)

        val noKey = GoogleChirpSpeechProvider({ "" }, FakeTransport(), 1_000)
        val missing = runCatching { noKey.listVoices() }.exceptionOrNull()
        assertEquals(SpeechFailure.AUTHORIZATION, (missing as SpeechException).failure)
    }

    @Test
    fun voiceListKeepsOnlyJapaneseChirpHdVoices() {
        val body = """
            {"voices":[
              {"name":"ja-JP-Chirp3-HD-Achernar","languageCodes":["ja-JP"],"ssmlGender":"FEMALE","naturalSampleRateHertz":24000},
              {"name":"ja-JP-Chirp3-HD-Charon","languageCodes":["ja-JP"],"ssmlGender":"MALE","naturalSampleRateHertz":24000},
              {"name":"ja-JP-Standard-A","languageCodes":["ja-JP"],"ssmlGender":"FEMALE"},
              {"name":"en-US-Chirp3-HD-Achernar","languageCodes":["en-US"],"ssmlGender":"FEMALE"}
            ]}
        """.trimIndent()
        val voices = parseJapaneseChirpVoices(body)
        assertEquals(listOf("ja-JP-Chirp3-HD-Achernar", "ja-JP-Chirp3-HD-Charon"), voices.map { it.name })
        assertEquals("Achernar · FEMALE", voices.first().displayName)
        assertEquals(24_000, voices.first().naturalSampleRateHz)
    }

    @Test
    fun cachedVoiceListRoundTrips() {
        val voices = listOf(voice)
        assertEquals(voices, decodeVoices(encodeVoices(voices)))
        assertEquals(emptyList<CloudVoice>(), decodeVoices("garbage"))
    }
}
