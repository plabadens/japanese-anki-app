package dev.pierre.japaneseanki.speech

import androidx.test.ext.junit.runners.AndroidJUnit4

import dev.pierre.japaneseanki.JapaneseToken
import dev.pierre.japaneseanki.ReadingCorrection
import dev.pierre.japaneseanki.TargetSpan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.SocketTimeoutException
import java.util.Base64

@RunWith(AndroidJUnit4::class)
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

@RunWith(AndroidJUnit4::class)
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
    fun http400DetailsAreRetainedAsSafeStructuredDiagnostics() {
        val errorBody = """{"error":{"status":"INVALID_ARGUMENT","message":"private sentence and key","details":[{"reason":"INVALID_ARGUMENT"}]}}"""
            .toByteArray(Charsets.UTF_8)
        val transport = FakeTransport(400, errorBody)
        val provider = GoogleChirpSpeechProvider({ "never-in-diagnostics" }, transport, 1_000)
        val error = runCatching {
            provider.synthesize(SentenceSpeechRequest("悪逆", voice, listOf(ReadingHint("悪逆", "アクギャク"))), File("unused.mp3"))
        }.exceptionOrNull() as SpeechException

        assertEquals(SpeechFailure.INVALID_REQUEST, error.failure)
        assertFalse("invalid request must not be retried unchanged", error.retryable)
        assertEquals(400, error.diagnostic?.httpStatus)
        assertEquals("INVALID_ARGUMENT", error.diagnostic?.googleStatus)
        assertEquals("INVALID_ARGUMENT", error.diagnostic?.googleReason)
        assertTrue(error.diagnostic?.durationMillis != null && error.diagnostic!!.durationMillis!! >= 0)
        assertEquals("SYNTHESIS", error.diagnostic?.request?.operation?.name)
        assertEquals(1, error.diagnostic?.request?.hintCount)
        assertTrue((error.diagnostic?.request?.inputBytes ?: 0) > "悪逆".toByteArray(Charsets.UTF_8).size)
        val diagnostics = error.diagnostic.toString()
        assertFalse(diagnostics.contains("private sentence"))
        assertFalse(diagnostics.contains("never-in-diagnostics"))
        assertFalse(diagnostics.contains("悪逆"))
        assertEquals(1, transport.calls)
    }

    @Test
    fun unknownClientErrorsArePermanentAndDoNotExposeProviderProse() {
        val response = """{"error":{"status":"NOT_FOUND","message":"private provider detail"}}"""
            .toByteArray(Charsets.UTF_8)
        val transport = FakeTransport(404, response)
        val error = runCatching {
            GoogleChirpSpeechProvider({ "k" }, transport, 1_000)
                .synthesize(SentenceSpeechRequest("猫", voice, emptyList()), File("unused.mp3"))
        }.exceptionOrNull() as SpeechException

        assertEquals(SpeechFailure.INVALID_REQUEST, error.failure)
        assertFalse(error.retryable)
        assertEquals(404, error.diagnostic?.httpStatus)
        assertFalse(error.message.orEmpty().contains("private provider detail"))
        assertEquals(1, transport.calls)
    }

    @Test
    fun timeoutAndServiceUnavailableRemainDistinctAndRetryable() {
        val timeoutTransport = object : GoogleSpeechTransport {
            override fun get(url: String, apiKey: String, timeoutMillis: Int): SpeechHttpResponse =
                throw SocketTimeoutException("read timed out")

            override fun post(url: String, apiKey: String, json: ByteArray, timeoutMillis: Int): SpeechHttpResponse =
                throw SocketTimeoutException("read timed out")
        }
        val timeout = runCatching {
            GoogleChirpSpeechProvider({ "k" }, timeoutTransport, 1_000)
                .synthesize(SentenceSpeechRequest("猫", voice, emptyList()), File("unused.mp3"))
        }.exceptionOrNull() as SpeechException

        val unavailable = runCatching {
            GoogleChirpSpeechProvider({ "k" }, FakeTransport(503, ByteArray(0)), 1_000)
                .synthesize(SentenceSpeechRequest("猫", voice, emptyList()), File("unused.mp3"))
        }.exceptionOrNull() as SpeechException

        assertEquals(SpeechFailure.TIMEOUT, timeout.failure)
        assertEquals("SocketTimeoutException", timeout.diagnostic?.exceptionClass)
        assertTrue(timeout.retryable)
        assertNull(timeout.diagnostic?.httpStatus)
        assertEquals(SpeechFailure.NETWORK, unavailable.failure)
        assertEquals(503, unavailable.diagnostic?.httpStatus)
        assertNull(unavailable.diagnostic?.exceptionClass)
        assertTrue(unavailable.retryable)
        assertFalse(timeout.failure == unavailable.failure)
    }

    @Test
    fun synthesisEnforcesUtf8ContentLimitWithoutCallingTransport() {
        val transport = FakeTransport()
        val oversized = "猫".repeat(1_668) // 5,004 UTF-8 bytes; UTF-16 length alone would miss this.
        val error = runCatching {
            GoogleChirpSpeechProvider({ "k" }, transport, 1_000)
                .synthesize(SentenceSpeechRequest(oversized, voice, emptyList()), File("unused.mp3"))
        }.exceptionOrNull() as SpeechException

        assertEquals(SpeechFailure.INVALID_REQUEST, error.failure)
        assertFalse(error.retryable)
        assertEquals(0, transport.calls)
        assertEquals(5_004, error.diagnostic?.request?.inputBytes)
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

@RunWith(AndroidJUnit4::class)
class SentenceHintSpansTest {
    private fun token(start: Int, end: Int, surface: String, reading: String) = JapaneseToken(
        span = TargetSpan(start, end),
        surface = surface,
        lemma = surface,
        surfaceReading = reading,
        lemmaReading = reading,
        partOfSpeech = "名詞",
    )

    @Test
    fun unresolvedKanjiRemainsInSentenceHintsAndBlocksGeneration() {
        val sentence = "昨日は猫"
        val hints = sentenceHintSpans(
            sentence,
            listOf(token(0, 2, "昨日", "きのう"), token(2, 3, "は", "は"), token(3, 4, "猫", "")),
            emptyList(),
        )

        assertEquals(listOf("昨日", "猫"), hints.map(ReadingHintSpan::surface))
        assertEquals("", hints.last().reading)
        assertTrue(ReadingHintBuilder.build(sentence, hints) is HintResult.Impossible)
    }

    @Test
    fun readingCorrectionReplacesOnlyItsSentenceOccurrence() {
        val sentence = "猫は猫"
        val tokens = listOf(token(0, 1, "猫", "ねこ"), token(1, 2, "は", "は"), token(2, 3, "猫", ""))
        val hints = sentenceHintSpans(
            sentence,
            tokens,
            listOf(ReadingCorrection(TargetSpan(2, 3), "猫", "ねこ")),
        )

        assertEquals(listOf(0, 2), hints.map(ReadingHintSpan::start))
        assertEquals(HintResult.Ready(listOf(ReadingHint("猫", "ねこ"))), ReadingHintBuilder.build(sentence, hints))
    }
}
