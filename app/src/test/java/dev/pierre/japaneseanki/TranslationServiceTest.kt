package dev.pierre.japaneseanki

import dev.pierre.japaneseanki.speech.CloudVoice
import dev.pierre.japaneseanki.speech.GoogleChirpSpeechProvider
import dev.pierre.japaneseanki.speech.GoogleSpeechTransport
import dev.pierre.japaneseanki.speech.MiniJson
import dev.pierre.japaneseanki.speech.SpeechException
import dev.pierre.japaneseanki.speech.SpeechFailure
import dev.pierre.japaneseanki.speech.SpeechHttpResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.CancellationException

class TranslationServiceTest {
    private class FakeTranslationTransport(
        private val status: Int = 200,
        private val body: String = translationBody("It's a nice day."),
        private val failure: Exception? = null,
    ) : TranslatorTransport {
        var url: String? = null
        var apiKey: String? = null
        var json: String? = null
        var timeoutMillis: Int? = null
        var calls = 0

        override fun post(url: String, apiKey: String, json: ByteArray, timeoutMillis: Int): TranslatorHttpResponse {
            calls++
            this.url = url
            this.apiKey = apiKey
            this.json = json.toString(Charsets.UTF_8)
            this.timeoutMillis = timeoutMillis
            failure?.let { throw it }
            return TranslatorHttpResponse(status, body)
        }
    }

    private class FakeSpeechTransport(
        private val status: Int = 200,
        private val body: String = voicesBody,
    ) : GoogleSpeechTransport {
        var url: String? = null
        var apiKey: String? = null
        var json: String? = null
        var calls = 0

        override fun get(url: String, apiKey: String, timeoutMillis: Int): SpeechHttpResponse {
            calls++
            this.url = url
            this.apiKey = apiKey
            return SpeechHttpResponse(status, body.toByteArray(Charsets.UTF_8))
        }

        override fun post(url: String, apiKey: String, json: ByteArray, timeoutMillis: Int): SpeechHttpResponse {
            calls++
            this.url = url
            this.apiKey = apiKey
            this.json = json.toString(Charsets.UTF_8)
            return SpeechHttpResponse(status, body.toByteArray(Charsets.UTF_8))
        }
    }

    private fun service(
        transport: FakeTranslationTransport,
        key: String? = "google-key",
        timeoutMillis: Int = 2_000,
    ) = TranslationService(GoogleTranslationClient(transport, timeoutMillis), credential = { key })

    // --- request contract -------------------------------------------------

    @Test
    fun basicV2NmtRequestIsSerializedExactly() {
        val transport = FakeTranslationTransport()
        val result = service(transport).translate("今日はいい天気ですね。")

        assertEquals(TranslationResult.Ready("It's a nice day.", TranslationSource.GOOGLE), result)
        assertEquals("https://translation.googleapis.com/language/translate/v2", transport.url)
        assertFalse(transport.url.orEmpty().contains("?"))
        assertEquals(2_000, transport.timeoutMillis)

        val sent = MiniJson.parse(transport.json.orEmpty()) as Map<*, *>
        assertEquals(
            setOf("q", "source", "target", "format", "model"),
            sent.keys.map { it.toString() }.toSet(),
        )
        assertEquals("今日はいい天気ですね。", sent["q"])
        assertEquals("ja", sent["source"])
        assertEquals("en", sent["target"])
        assertEquals("text", sent["format"])
        assertEquals("nmt", sent["model"])
    }

    @Test
    fun payloadCarriesOnlyTheSentenceAndTheCredentialStaysInTheHeader() {
        val transport = FakeTranslationTransport()
        service(transport).translate("猫が走った")

        assertEquals("google-key", transport.apiKey)
        val body = transport.json.orEmpty()
        // No dictionary meaning, reading hint, note, image, prompt, or provider plumbing.
        listOf("prompt", "messages", "temperature", "image", "photo", "notes", "definition", "google-key")
            .forEach { assertFalse("payload contained $it", body.contains(it)) }
        assertEquals("猫が走った", (MiniJson.parse(body) as Map<*, *>)["q"])
    }

    // --- response handling ------------------------------------------------

    @Test
    fun htmlEscapedResponseBecomesPlainTextAndExportEscapesIt() {
        val transport = FakeTranslationTransport(
            body = translationBody("&lt;b&gt;Tom &amp; Jerry&lt;/b&gt; &#39;ok&#39;"),
        )
        val result = service(transport).translate("文")
        assertEquals(TranslationResult.Ready("<b>Tom & Jerry</b> 'ok'", TranslationSource.GOOGLE), result)
        val exported = AnkiFieldSerializer.plainText((result as TranslationResult.Ready).text)
        assertTrue(exported.contains("&lt;b&gt;"))
        assertFalse(exported.contains("<b>"))
    }

    @Test
    fun emptyMalformedAndAmbiguousResponsesBlock() {
        val bodies = listOf(
            "",
            "not json",
            "{}",
            """{"data":{}}""",
            """{"data":{"translations":[]}}""",
            """{"data":{"translations":[{"translatedText":"   "}]}}""",
            """{"data":{"translations":[{"translatedText":7}]}}""",
            """{"data":{"translations":[{"translatedText":"one"},{"translatedText":"two"}]}}""",
        )
        bodies.forEach { body ->
            val result = service(FakeTranslationTransport(body = body)).translate("文")
            assertTrue("body: $body", result is TranslationResult.Failed)
            val message = (result as TranslationResult.Failed).message
            assertTrue(message.isNotBlank())
            assertFalse(message.contains("translatedText"))
        }
    }

    // --- failure classification ------------------------------------------

    @Test
    fun defaultsWithNoCredentialSendNothing() {
        val transport = FakeTranslationTransport()
        val result = service(transport, key = null).translate("文")
        assertTrue(result is TranslationResult.Failed)
        assertTrue((result as TranslationResult.Failed).message.contains("Google Cloud API key"))
        assertEquals(0, transport.calls)
    }

    @Test
    fun failuresAreSpecificSanitizedAndNeverFallBack() {
        data class Case(val status: Int, val body: String, val expected: String)

        val serviceDisabled = googleError(403, "PERMISSION_DENIED", "SERVICE_DISABLED")
        val billingDisabled = googleError(403, "PERMISSION_DENIED", "BILLING_DISABLED")
        val keyInvalid = googleError(400, "INVALID_ARGUMENT", "API_KEY_INVALID")
        val cases = listOf(
            Case(302, "secret body", "redirect"),
            Case(401, "secret body", "rejected the API key"),
            Case(403, "secret body", "rejected the API key"),
            Case(403, serviceDisabled, "Cloud Translation API is not enabled"),
            Case(403, billingDisabled, "Billing is not enabled"),
            Case(400, keyInvalid, "rejected the API key"),
            Case(429, "secret body", "quota"),
            Case(500, "secret body", "could not be reached"),
        )
        cases.forEach { case ->
            val result = service(FakeTranslationTransport(case.status, case.body)).translate("秘密の文")
            assertTrue("status ${case.status}", result is TranslationResult.Failed)
            val message = (result as TranslationResult.Failed).message
            assertTrue("status ${case.status}: $message", message.contains(case.expected))
            assertFalse(message.contains("secret body"))
            assertFalse(message.contains("google-key"))
            assertFalse(message.contains("秘密の文"))
        }
    }

    @Test
    fun translationAndSpeechRedirectsKeepTheirDistinctFailureClasses() {
        val translationError = assertThrows(TranslatorException::class.java) {
            GoogleTranslationClient(FakeTranslationTransport(status = 302), timeoutMillis = 1_000)
                .translate("test-key", "文")
        }
        assertEquals(TranslatorFailure.NETWORK, translationError.failure)

        val speechError = assertThrows(SpeechException::class.java) {
            GoogleChirpSpeechProvider(
                credential = { "test-key" },
                transport = FakeSpeechTransport(status = 302),
                timeoutMillis = 1_000,
            ).listVoices()
        }
        assertEquals(SpeechFailure.AUTHORIZATION, speechError.failure)
    }

    @Test
    fun timeoutAndNetworkFailuresAreRetryableFailuresNotResults() {
        val timeout = service(FakeTranslationTransport(failure = SocketTimeoutException("read timed out"))).translate("文")
        assertTrue(timeout is TranslationResult.Failed)
        assertEquals("Translation service could not be reached.", (timeout as TranslationResult.Failed).message)

        val offline = service(FakeTranslationTransport(failure = IOException("no route to host"))).translate("文")
        assertTrue(offline is TranslationResult.Failed)
    }

    @Test
    fun cancellationIsNotConvertedIntoAFailedTranslation() {
        val transport = FakeTranslationTransport(failure = CancellationException())
        assertThrows(CancellationException::class.java) {
            service(transport).translate("文")
        }
    }

    @Test
    fun obsoleteResponsesCannotMutateANewerRevision() {
        // Same revision, same sentence: the result may be applied.
        assertTrue(translationResponseIsCurrent("文", 4, "文", 4, submissionLocked = false))
        // The sentence changed while the request was in flight.
        assertFalse(translationResponseIsCurrent("古い文", 4, "新しい文", 5, submissionLocked = false))
        // A retry/edit started a newer revision for the same text.
        assertFalse(translationResponseIsCurrent("文", 4, "文", 5, submissionLocked = false))
        // Submission froze the revision.
        assertFalse(translationResponseIsCurrent("文", 4, "文", 4, submissionLocked = true))
    }

    // --- one shared credential, independent service checks ----------------

    @Test
    fun bothAdaptersUseTheSameProtectedCredentialInHeadersOnly() {
        val speechTransport = FakeSpeechTransport()
        val translationTransport = FakeTranslationTransport()
        val credential = { "shared-google-key" }
        val speech = GoogleChirpSpeechProvider(credential, speechTransport, 1_000)
        val translation = TranslationService(GoogleTranslationClient(translationTransport, 1_000), credential)

        // Entering or saving a key only builds the adapters; no paid request is started.
        assertEquals(0, speechTransport.calls)
        assertEquals(0, translationTransport.calls)

        assertEquals(listOf("ja-JP-Chirp3-HD-Achernar"), speech.listVoices().map(CloudVoice::name))
        assertTrue(translation.translate("文") is TranslationResult.Ready)

        assertEquals("shared-google-key", speechTransport.apiKey)
        assertEquals("shared-google-key", translationTransport.apiKey)
        listOfNotNull(speechTransport.url, translationTransport.url).forEach { url ->
            assertFalse(url.contains("shared-google-key"))
            assertFalse(url.contains("key="))
        }
        assertFalse(speechTransport.json.orEmpty().contains("shared-google-key"))
        assertFalse(translationTransport.json.orEmpty().contains("shared-google-key"))
        assertEquals(1, speechTransport.calls)
        assertEquals(1, translationTransport.calls)
    }

    @Test
    fun speechNetworkFailureIsSpecific() {
        val offline = object : GoogleSpeechTransport {
            override fun get(url: String, apiKey: String, timeoutMillis: Int): SpeechHttpResponse =
                throw IOException("network unreachable")

            override fun post(url: String, apiKey: String, json: ByteArray, timeoutMillis: Int): SpeechHttpResponse =
                throw IOException("network unreachable")
        }
        val error = assertThrows(SpeechException::class.java) {
            GoogleChirpSpeechProvider({ "k" }, offline, 1_000).listVoices()
        }
        assertEquals(SpeechFailure.NETWORK, error.failure)
        assertEquals("Voice listing service is unavailable.", error.message)
    }

    @Test
    fun speechAndTranslationChecksFailIndependently() {
        val credential = { "k" }

        val speechError = assertThrows(SpeechException::class.java) {
            GoogleChirpSpeechProvider(
                credential,
                FakeSpeechTransport(403, googleError(403, "PERMISSION_DENIED", "SERVICE_DISABLED")),
                1_000,
            ).listVoices()
        }
        assertEquals(SpeechFailure.API_NOT_ENABLED, speechError.failure)
        assertTrue(
            TranslationService(GoogleTranslationClient(FakeTranslationTransport(), 1_000), credential)
                .translate("文") is TranslationResult.Ready,
        )

        val translation = TranslationService(
            GoogleTranslationClient(FakeTranslationTransport(403, googleError(403, "PERMISSION_DENIED", "SERVICE_DISABLED")), 1_000),
            credential,
        ).translate("文")
        assertTrue((translation as TranslationResult.Failed).message.contains("Cloud Translation API is not enabled"))
        assertEquals(1, GoogleChirpSpeechProvider(credential, FakeSpeechTransport(), 1_000).listVoices().size)

        val speechBilling = assertThrows(SpeechException::class.java) {
            GoogleChirpSpeechProvider(
                credential,
                FakeSpeechTransport(403, googleError(403, "PERMISSION_DENIED", "BILLING_DISABLED")),
                1_000,
            ).listVoices()
        }
        assertEquals(SpeechFailure.BILLING, speechBilling.failure)
    }

    // --- draft preservation and export blocking ---------------------------

    @Test
    fun meaningOnlyChangesKeepTheSentenceTranslation() {
        val draft = TranslationDraft("猫が走る", TranslationResult.Ready("The cat runs.", TranslationSource.GOOGLE))
        // A target/meaning-only edit does not touch this draft; the same sentence keeps its value.
        assertEquals(draft, draft.sentenceChanged("猫が走る"))
        assertFalse(draft.sentenceChanged("猫が走る").stale)
    }

    @Test
    fun manualCorrectionsSurviveAStaleSentenceAndRetry() {
        val edited = TranslationDraft("古い", TranslationResult.Ready("Old", TranslationSource.GOOGLE))
            .saveManualEdit("My version")
        val changed = edited.sentenceChanged("新しい")
        assertTrue(changed.stale)
        assertEquals("My version", (changed.result as TranslationResult.Ready).text)

        val started = changed.requestStarted()
        assertEquals("My version", (started.result as TranslationResult.Ready).text)
        assertEquals(TranslationResult.Pending, started.suggestion)

        val finished = started.requestFinished(TranslationResult.Ready("New", TranslationSource.GOOGLE))
        assertEquals("My version", (finished.result as TranslationResult.Ready).text)
        assertEquals("New", (finished.suggestion as TranslationResult.Ready).text)

        val kept = finished.keepMyText()
        assertEquals("My version", (kept.result as TranslationResult.Ready).text)
        assertEquals(TranslationSource.MANUAL, (kept.result as TranslationResult.Ready).source)
        assertEquals("New", (finished.useSuggestion().result as TranslationResult.Ready).text)
    }

    @Test
    fun failedOrMissingTranslationBlocksExport() {
        val base = ManualNoteDraft(
            sentence = "猫が走る",
            target = TargetSpan(0, 1),
            sentenceReading = "ねこ",
            vocabulary = "猫",
            vocabularyReading = "ねこ",
            definition = "cat",
            dictionaryEntryResolved = true,
            pitch = PitchAccent.create("ねこ", 0),
            translation = "",
        )
        assertNull(base.fieldsByName())
        assertTrue(base.validationErrors().any { it.contains("English translation is required") })

        val complete = base.copy(translation = "The cat runs.")
        assertNotNull(complete.fieldsByName())
        assertEquals("The cat runs.", complete.fieldsByName()!!["SentEng"])
    }

    @Test
    fun translationEntityScannerPreservesItsNumericAndOnePassPolicy() {
        val cases = listOf(
            "&amp;" to "&",
            "&lt;&gt;&quot;&apos;&nbsp;" to "<>\"'\u00a0",
            "&AMP;" to "&AMP;",
            "&unknown;" to "&unknown;",
            "&amp" to "&amp",
            "&#0;" to "&#0;",
            "&#55295;" to "\uD7FF",
            "&#55296;" to "\uD800",
            "&#57343;" to "\uDFFF",
            "&#57344;" to "\uE000",
            "&#128512;" to "\uD83D\uDE00",
            "&#1114111;" to "\uDBFF\uDFFF",
            "&#1114112;" to "&#1114112;",
            "&#-1;" to "&#-1;",
            "&#x-1;" to "&#x-1;",
            "&#+65;" to "A",
            "&#x+41;" to "A",
            "&#X41;" to "A",
            "&#2147483648;" to "&#2147483648;",
            "&#x000000041;" to "A",
            "&#x0000000041;" to "&#x0000000041;",
            "&amp;&lt;" to "&<",
            "&amp;lt;" to "&lt;",
        )

        cases.forEach { (encoded, expected) ->
            assertEquals(encoded, expected, unescapeTranslationEntities(encoded))
        }
    }

    private companion object {
        fun translationBody(text: String) =
            """{"data":{"translations":[{"translatedText":"$text","detectedSourceLanguage":"ja"}]}}"""

        fun googleError(code: Int, status: String, reason: String) =
            """{"error":{"code":$code,"status":"$status","message":"Google API error","details":[{"@type":"type.googleapis.com/google.rpc.ErrorInfo","reason":"$reason"}]}}"""

        const val voicesBody =
            """{"voices":[{"name":"ja-JP-Chirp3-HD-Achernar","languageCodes":["ja-JP"],"ssmlGender":"FEMALE","naturalSampleRateHertz":24000}]}"""
    }
}
