package dev.pierre.japaneseanki

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
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
        assertTrue(connection.request.toString(Charsets.UTF_8).contains("こんにちは"))
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
    fun absentLocalModelReturnsSpecificUnavailableFailure() {
        val result = TranslationService(local = UnavailableLocalTranslationFallback()).translate("文")
        assertEquals(TranslationResult.Failed("Local translation is unavailable"), result)
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
    fun failuresDoNotExposeCredentialsOrSentence() {
        val result = TranslationService(
            AzureTranslator(AzureSpeechSynthesizer.ConnectionFactory { FakeConnection(401, "key and sentence") }),
            UnavailableLocalTranslationFallback(),
            "very-secret-key" to "eastus",
        ).translate("private sentence")
        assertEquals(TranslationResult.Failed("Local translation is unavailable"), result)
        val message = (result as TranslationResult.Failed).message
        assertFalse(message.contains("secret"))
        assertFalse(message.contains("private"))
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
