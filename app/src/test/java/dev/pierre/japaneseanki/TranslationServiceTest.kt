package dev.pierre.japaneseanki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationServiceTest {
    private class FakeTransport(
        private val status: Int = 200,
        private val body: String = """{"choices":[{"message":{"content":"Hello there"}}]}""",
        private val throwOnSend: Boolean = false,
    ) : TranslatorTransport {
        var url: String? = null
        var apiKey: String? = null
        var json: String? = null
        var timeoutMillis: Int? = null

        override fun post(url: String, apiKey: String, json: ByteArray, timeoutMillis: Int): TranslatorHttpResponse {
            this.url = url
            this.apiKey = apiKey
            this.json = json.toString(Charsets.UTF_8)
            this.timeoutMillis = timeoutMillis
            if (throwOnSend) throw java.io.IOException("connection lost")
            return TranslatorHttpResponse(status, body)
        }
    }

    @Test
    fun openRouterRequestUsesPinnedModelAndFixedPrompt() {
        val transport = FakeTransport()
        val service = TranslationService(OpenRouterTranslator(transport, 2_000), credentials = "or-key")
        val result = service.translate("こんにちは")
        assertEquals(TranslationResult.Ready("Hello there", TranslationSource.OPENROUTER), result)
        assertEquals("https://openrouter.ai/api/v1/chat/completions", transport.url)
        assertEquals("or-key", transport.apiKey)
        assertEquals(2_000, transport.timeoutMillis)
        val body = transport.json.orEmpty()
        assertTrue(body.contains("\"model\":\"google/gemini-2.5-flash\""))
        assertTrue(body.contains(OpenRouterTranslator.SYSTEM_PROMPT))
        assertTrue(body.contains("\"role\":\"user\",\"content\":\"こんにちは\""))
        assertFalse(body.contains("image"))
    }

    @Test
    fun authorizationQuotaAndNetworkFailuresDoNotFallBackAndHideBodies() {
        val cases = listOf(
            401 to TranslatorFailure.AUTHORIZATION,
            403 to TranslatorFailure.AUTHORIZATION,
            429 to TranslatorFailure.QUOTA,
            500 to TranslatorFailure.SERVICE,
        )
        cases.forEach { (status, expected) ->
            val service = TranslationService(
                OpenRouterTranslator(FakeTransport(status, "secret body"), 1000),
                credentials = "k",
            )
            val result = service.translate("文")
            assertTrue("status $status", result is TranslationResult.Failed)
            val message = (result as TranslationResult.Failed).message
            assertFalse(message.contains("secret body"))
        }
        val network = TranslationService(
            OpenRouterTranslator(FakeTransport(throwOnSend = true), 1000),
            credentials = "k",
        ).translate("文")
        assertTrue(network is TranslationResult.Failed)
    }

    @Test
    fun emptyTranslationAndMissingKeyBlock() {
        val empty = TranslationService(OpenRouterTranslator(FakeTransport(body = """{"choices":[{"message":{"content":"  "}}]}"""), 1000), credentials = "k")
            .translate("文")
        assertTrue(empty is TranslationResult.Failed)
        val noKey = TranslationService(credentials = "").translate("文")
        assertTrue(noKey is TranslationResult.Failed)
        assertTrue((noKey as TranslationResult.Failed).message.contains("OpenRouter"))
    }

    @Test
    fun manualEditsSurviveStaleSentenceChanges() {
        val draft = TranslationDraft("古い", TranslationResult.Ready("Old", TranslationSource.OPENROUTER))
        val edited = draft.saveManualEdit("My version")
        val changed = edited.sentenceChanged("新しい")
        assertTrue(changed.stale)
        assertEquals("My version", (changed.result as TranslationResult.Ready).text)
        assertEquals(TranslationSource.MANUAL, (changed.result as TranslationResult.Ready).source)
    }
}
