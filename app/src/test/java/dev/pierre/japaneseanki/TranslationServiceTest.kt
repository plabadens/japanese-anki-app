package dev.pierre.japaneseanki

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.PrintStream
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationServiceTest {
    @Test
    fun azureSuccessUsesDocumentedContractAndMarksCloudSource() {
        val connection = FakeConnection(200, "[{\"translations\":[{\"text\":\"Hello\\nthere\",\"to\":\"en\"}]}]")
        val translator = AzureTranslator(AzureSpeechSynthesizer.ConnectionFactory { url ->
            assertEquals("https://api.cognitive.microsofttranslator.com/translate?api-version=3.0&from=ja&to=en", url.toString())
            connection
        }, 2_000)
        val result = TranslationService(translator, credentials = "secret" to "eastus").translate("こんにちは")
        assertEquals(TranslationResult.Ready("Hello\nthere", TranslationSource.AZURE), result)
        assertEquals("POST", connection.requestMethod)
        assertEquals("secret", connection.getRequestProperty("Ocp-Apim-Subscription-Key"))
        assertEquals("eastus", connection.getRequestProperty("Ocp-Apim-Subscription-Region"))
        assertEquals("application/json; charset=UTF-8", connection.getRequestProperty("Content-Type"))
        assertEquals("[{\"Text\":\"こんにちは\"}]", connection.request.toString(Charsets.UTF_8))
        assertEquals(2_000, connection.connectTimeout)
        assertEquals(2_000, connection.readTimeout)
        assertFalse(connection.instanceFollowRedirects)
    }

    @Test
    fun authorizationQuotaAndNetworkErrorsFallThroughToLocal() {
        val fakeLocal = LocalTranslationFallback { "offline" }
        listOf(401, 403, 429).forEach { status ->
            val result = TranslationService(AzureTranslator(AzureSpeechSynthesizer.ConnectionFactory { FakeConnection(status, "sensitive response") }), fakeLocal, "key" to "eastus").translate("文")
            assertEquals(TranslationResult.Ready("offline", TranslationSource.LOCAL), result)
        }
        val network = TranslationService(AzureTranslator(AzureSpeechSynthesizer.ConnectionFactory { throw IOException("secret/text") }), fakeLocal, "key" to "eastus").translate("文")
        assertEquals(TranslationResult.Ready("offline", TranslationSource.LOCAL), network)
    }

    @Test
    fun localModelDownloadFailureCanBeRetriedAndThenUsedOffline() {
        var downloadAttempts = 0
        val client = object : MlKitTranslationClient {
            override fun downloadModel() {
                downloadAttempts++
                if (downloadAttempts == 1) throw IOException("offline")
            }

            override fun translate(sentence: String): String = "translated:$sentence"
            override fun close() = Unit
        }
        val fallback = MlKitLocalTranslationFallback(client)

        assertEquals(null, fallback.translate("日本語"))
        assertEquals(TranslationModelStatus.ERROR, fallback.modelStatus)
        assertTrue(fallback.retryModelDownload())
        assertEquals(TranslationModelStatus.READY, fallback.modelStatus)
        assertEquals("translated:日本語", fallback.translate("日本語"))
        assertEquals(2, downloadAttempts)
    }

    @Test
    fun translationFailureDoesNotForgetDownloadedModel() {
        var translationFails = true
        var downloadAttempts = 0
        val client = object : MlKitTranslationClient {
            override fun downloadModel() { downloadAttempts++ }
            override fun translate(sentence: String): String {
                if (translationFails) throw IOException("translation failed")
                return "offline result"
            }
            override fun close() = Unit
        }
        val fallback = MlKitLocalTranslationFallback(client)

        assertEquals(null, fallback.translate("日本語"))
        assertEquals(TranslationModelStatus.READY, fallback.modelStatus)
        translationFails = false
        assertEquals("offline result", fallback.translate("日本語"))
        assertEquals(1, downloadAttempts)
    }

    @Test
    fun absentLocalModelReturnsSpecificUnavailableFailure() {
        val result = TranslationService(local = UnavailableLocalTranslationFallback()).translate("文")
        assertEquals(TranslationResult.Failed("Local translation is unavailable"), result)
    }

    @Test
    fun unavailableFallbackCanBeRetriedAndMachineResultIsMarkedLocal() {
        var available = false
        val fallback = LocalTranslationFallback { if (available) "Offline result" else null }
        val service = TranslationService(local = fallback)
        assertEquals(TranslationResult.Failed("Local translation is unavailable"), service.translate("文"))
        available = true
        assertEquals(TranslationResult.Ready("Offline result", TranslationSource.LOCAL), service.translate("文"))
    }

    @Test
    fun resultSourceAndManualSaveClearLocalMark() {
        val localReady = TranslationResult.Ready("local", TranslationSource.LOCAL)
        assertEquals(TranslationSource.LOCAL, (localReady as TranslationResult.Ready).source)
        val edited = TranslationDraft("文", localReady).saveManualEdit("my text")
        assertEquals(TranslationResult.Ready("my text", TranslationSource.MANUAL), edited.result)
        assertTrue(edited.manuallyEdited)
    }

    @Test
    fun sentenceChangeInvalidatesMachineTextButPreservesManualText() {
        val machine = TranslationDraft("old", TranslationResult.Ready("generated", TranslationSource.AZURE))
        assertEquals(TranslationDraft("new", TranslationResult.Pending), machine.sentenceChanged("new"))
        val manual = machine.saveManualEdit("kept")
        val changed = manual.sentenceChanged("new")
        assertEquals("kept", (changed.result as TranslationResult.Ready).text)
        assertTrue(changed.stale)
        assertTrue(changed.manuallyEdited)
    }

    @Test
    fun staleManualTranslationIsKeptUntilExplicitKeepOrSuggestionChoice() {
        val manual = TranslationDraft("old sentence", TranslationResult.Ready("my text", TranslationSource.MANUAL), true)
        val stale = manual.sentenceChanged("new sentence")
        assertTrue(stale.stale)
        assertEquals("my text", (stale.result as TranslationResult.Ready).text)
        assertEquals(TranslationResult.Pending, stale.suggestion)

        val withSuggestion = stale.requestFinished(TranslationResult.Ready("new suggestion", TranslationSource.LOCAL))
        assertEquals("my text", (withSuggestion.result as TranslationResult.Ready).text)
        assertEquals("new suggestion", (withSuggestion.suggestion as TranslationResult.Ready).text)
        assertEquals(TranslationSource.MANUAL, (withSuggestion.keepMyText().result as TranslationResult.Ready).source)
        assertEquals("new suggestion", (withSuggestion.useSuggestion().result as TranslationResult.Ready).text)

        val failedSuggestion = stale.requestStarted().requestFinished(TranslationResult.Failed("unavailable"))
        assertEquals("my text", (failedSuggestion.result as TranslationResult.Ready).text)
        assertEquals("unavailable", (failedSuggestion.suggestion as TranslationResult.Failed).message)
        val retried = failedSuggestion.requestStarted().requestFinished(
            TranslationResult.Ready("replacement", TranslationSource.AZURE),
        )
        assertEquals("replacement", (retried.suggestion as TranslationResult.Ready).text)
    }

    @Test
    fun failuresDoNotExposeCredentialsOrSentence() {
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val originalOut = System.out
        val originalErr = System.err
        val result = try {
            System.setOut(PrintStream(stdout, true, Charsets.UTF_8))
            System.setErr(PrintStream(stderr, true, Charsets.UTF_8))
            TranslationService(
                AzureTranslator(AzureSpeechSynthesizer.ConnectionFactory { FakeConnection(401, "key and sentence") }),
                UnavailableLocalTranslationFallback(),
                "very-secret-key" to "eastus",
            ).translate("private sentence")
        } finally {
            System.setOut(originalOut)
            System.setErr(originalErr)
        }
        assertEquals(TranslationResult.Failed("Local translation is unavailable"), result)
        val message = (result as TranslationResult.Failed).message
        val logs = stdout.toString(Charsets.UTF_8) + stderr.toString(Charsets.UTF_8)
        assertFalse(message.contains("very-secret-key"))
        assertFalse(message.contains("private"))
        assertFalse(logs.contains("very-secret-key"))
        assertFalse(logs.contains("private sentence"))
    }

    private class FakeConnection(private val status: Int, private val response: String) : HttpURLConnection(URL("https://api.cognitive.microsofttranslator.com/")) {
        val request = ByteArrayOutputStream()
        override fun getOutputStream() = request
        override fun getInputStream() = ByteArrayInputStream(response.toByteArray(Charsets.UTF_8))
        override fun getResponseCode() = status
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
    }
}
