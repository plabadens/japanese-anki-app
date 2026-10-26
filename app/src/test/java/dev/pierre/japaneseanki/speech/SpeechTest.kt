package dev.pierre.japaneseanki.speech

import androidx.test.ext.junit.runners.AndroidJUnit4

import dev.pierre.japaneseanki.ReadingCorrection
import dev.pierre.japaneseanki.runSuspendTest

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
class GoogleSpeechProviderTest {
    private class FakeTransport(
        private val status: Int = 200,
        private val body: ByteArray = ByteArray(0),
    ) : GoogleSpeechTransport {
        var url: String? = null
        var apiKey: String? = null
        var json: String? = null
        var calls = 0

        override suspend fun get(url: String, apiKey: String, timeoutMillis: Int): SpeechHttpResponse {
            this.url = url
            this.apiKey = apiKey
            calls++
            return SpeechHttpResponse(status, body)
        }

        override suspend fun post(url: String, apiKey: String, json: ByteArray, timeoutMillis: Int): SpeechHttpResponse {
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
    fun serializedRequestCarriesExactVoiceAndNoPronunciationHints() {
        // Kanji sentences are the regression case: Google answered 400 INVALID_ARGUMENT to every
        // request that carried input.customPronunciations for a ja-JP Chirp 3: HD voice.
        val json = buildSynthesisJson(SentenceSpeechRequest("悪逆な行い", voice))
        assertTrue(json.contains("\"text\":\"悪逆な行い\""))
        assertTrue(json.contains("\"languageCode\":\"ja-JP\""))
        assertTrue(json.contains("\"name\":\"ja-JP-Chirp3-HD-Achernar\""))
        assertTrue(json.contains("\"audioEncoding\":\"MP3\""))
        assertFalse("Google rejects Japanese custom pronunciations", json.contains("customPronunciations"))
        assertFalse(json.contains("phoneticEncoding"))
        assertFalse(json.contains("<ruby>"))
        assertFalse(json.contains("pitch"))
    }

    @Test
    fun providerSendsAuthHeaderMaterialAndValidatesMp3() {
        val transport = FakeTransport(200, synthesisResponse())
        val provider = GoogleChirpSpeechProvider({ "google-key" }, transport, 1_000)
        val output = File.createTempFile("speech-", ".mp3")
        try {
            runSuspendTest { provider.synthesize(SentenceSpeechRequest("悪逆", voice), output) }
            assertEquals("google-key", transport.apiKey)
            assertEquals("https://texttospeech.googleapis.com/v1/text:synthesize", transport.url)
            assertTrue(output.length() > 0)
        } finally {
            output.delete()
        }
    }

    @Test
    fun rejectionIsReportedAsInvalidRequestWithoutHintsInTheBody() {
        val transport = FakeTransport(400, ByteArray(0))
        val provider = GoogleChirpSpeechProvider({ "k" }, transport, 1_000)
        val output = File.createTempFile("speech-", ".mp3")
        try {
            val error = runSuspendTest {
                runCatching { provider.synthesize(SentenceSpeechRequest("悪逆", voice), output) }
                    .exceptionOrNull()
            }
            assertTrue(error is SpeechException)
            assertEquals(SpeechFailure.INVALID_REQUEST, (error as SpeechException).failure)
            assertEquals(1, transport.calls)
            assertFalse(transport.json.orEmpty().contains("customPronunciations"))
        } finally {
            output.delete()
        }
    }

    @Test
    fun redirectsAndMissingCredentialsFailClosed() {
        val redirect = GoogleChirpSpeechProvider({ "k" }, FakeTransport(302, ByteArray(0)), 1_000)
        val error = runSuspendTest { runCatching { redirect.listVoices() }.exceptionOrNull() }
        assertEquals(SpeechFailure.AUTHORIZATION, (error as SpeechException).failure)

        val noKey = GoogleChirpSpeechProvider({ "" }, FakeTransport(), 1_000)
        val missing = runSuspendTest { runCatching { noKey.listVoices() }.exceptionOrNull() }
        assertEquals(SpeechFailure.AUTHORIZATION, (missing as SpeechException).failure)
    }

    @Test
    fun malformedSynthesisPayloadFailsClosed() {
        val provider = GoogleChirpSpeechProvider({ "k" }, FakeTransport(200, "{".toByteArray()), 1_000)
        val error = runSuspendTest {
            runCatching {
                provider.synthesize(SentenceSpeechRequest("猫", voice), File("unused.mp3"))
            }.exceptionOrNull()
        }

        assertEquals(SpeechFailure.INVALID_RESPONSE, (error as SpeechException).failure)
    }

    @Test
    fun http400DetailsAreRetainedAsSafeStructuredDiagnostics() {
        val errorBody = """{"error":{"status":"INVALID_ARGUMENT","message":"private sentence and key","details":[{"reason":"INVALID_ARGUMENT"}]}}"""
            .toByteArray(Charsets.UTF_8)
        val transport = FakeTransport(400, errorBody)
        val provider = GoogleChirpSpeechProvider({ "never-in-diagnostics" }, transport, 1_000)
        val error = runSuspendTest {
            runCatching {
                provider.synthesize(SentenceSpeechRequest("悪逆", voice), File("unused.mp3"))
            }.exceptionOrNull()
        } as SpeechException

        assertEquals(SpeechFailure.INVALID_REQUEST, error.failure)
        assertFalse("invalid request must not be retried unchanged", error.retryable)
        assertEquals(400, error.diagnostic?.httpStatus)
        assertEquals("INVALID_ARGUMENT", error.diagnostic?.googleStatus)
        assertEquals("INVALID_ARGUMENT", error.diagnostic?.googleReason)
        assertTrue(error.diagnostic?.durationMillis != null && error.diagnostic!!.durationMillis!! >= 0)
        assertEquals("SYNTHESIS", error.diagnostic?.request?.operation?.name)
        assertEquals("悪逆".toByteArray(Charsets.UTF_8).size, error.diagnostic?.request?.inputBytes)
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
        val error = runSuspendTest {
            runCatching {
                GoogleChirpSpeechProvider({ "k" }, transport, 1_000)
                    .synthesize(SentenceSpeechRequest("猫", voice), File("unused.mp3"))
            }.exceptionOrNull()
        } as SpeechException

        assertEquals(SpeechFailure.INVALID_REQUEST, error.failure)
        assertFalse(error.retryable)
        assertEquals(404, error.diagnostic?.httpStatus)
        assertFalse(error.message.orEmpty().contains("private provider detail"))
        assertEquals(1, transport.calls)
    }

    @Test
    fun timeoutAndServiceUnavailableRemainDistinctAndRetryable() {
        val timeoutTransport = object : GoogleSpeechTransport {
            override suspend fun get(url: String, apiKey: String, timeoutMillis: Int): SpeechHttpResponse =
                throw SocketTimeoutException("read timed out")

            override suspend fun post(url: String, apiKey: String, json: ByteArray, timeoutMillis: Int): SpeechHttpResponse =
                throw SocketTimeoutException("read timed out")
        }
        val timeout = runSuspendTest {
            runCatching {
                GoogleChirpSpeechProvider({ "k" }, timeoutTransport, 1_000)
                    .synthesize(SentenceSpeechRequest("猫", voice), File("unused.mp3"))
            }.exceptionOrNull()
        } as SpeechException

        val unavailable = runSuspendTest {
            runCatching {
                GoogleChirpSpeechProvider({ "k" }, FakeTransport(503, ByteArray(0)), 1_000)
                    .synthesize(SentenceSpeechRequest("猫", voice), File("unused.mp3"))
            }.exceptionOrNull()
        } as SpeechException

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
        val error = runSuspendTest {
            runCatching {
                GoogleChirpSpeechProvider({ "k" }, transport, 1_000)
                    .synthesize(SentenceSpeechRequest(oversized, voice), File("unused.mp3"))
            }.exceptionOrNull()
        } as SpeechException

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
