package dev.pierre.japaneseanki

import dev.pierre.japaneseanki.network.GoogleJson
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Test

class TranslationServiceDtoTest {
    @Test
    fun requestPreservesQuotesBackslashesNewlinesEmojiAndJapanese() {
        val sentence = """Quotes: "hello"; slash: \\; newline:
🌸 日本語"""

        val decoded = GoogleJson.decodeFromString<TranslationRequestDto>(
            GoogleTranslationClient.buildRequestBody(sentence),
        )

        assertEquals(TranslationRequestDto(sentence, "ja", "en", "text", "nmt"), decoded)
    }

    @Test
    fun parsesTypicalTranslationResponse() {
        val response = GoogleJson.decodeFromString<TranslationResponseDto>(
            """{"data":{"translations":[{"translatedText":"Good morning."}]}}""",
        )

        assertEquals(listOf(TranslatedTextDto("Good morning.")), response.data.translations)
    }

    @Test
    fun ignoresUnknownFieldsAtEachResponseLevel() {
        val response = GoogleJson.decodeFromString<TranslationResponseDto>(
            """{"extra":true,"data":{"other":2,"translations":[{"translatedText":"Hello.","detectedSourceLanguage":"ja","extra":3}]}}""",
        )

        assertEquals(listOf(TranslatedTextDto("Hello.")), response.data.translations)
    }

    @Test
    fun emptyTranslationsKeepsTheExistingMissingTranslationOutcome() {
        val transport = object : TranslatorTransport {
            override suspend fun post(
                url: String,
                apiKey: String,
                json: ByteArray,
                timeoutMillis: Int,
            ) = TranslatorHttpResponse(200, """{"data":{"translations":[]}}""")
        }
        val service = TranslationService(GoogleTranslationClient(transport), credential = { "key" })

        val result = runBlocking { service.translate("文") }

        assertEquals(
            TranslationResult.Failed("Translation did not return exactly one result."),
            result,
        )
    }
}
