package dev.pierre.japaneseanki.speech

import dev.pierre.japaneseanki.network.GoogleJson
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleSpeechDtoTest {
    @Test
    fun requestSerializationPreservesSpecialSentenceTextVerbatim() {
        val sentence = "A \"quote\", a back\\slash,\n a tab\t, emoji 😀, and 日本語"
        val request = SentenceSpeechRequest(
            text = sentence,
            voice = CloudVoice("ja-JP-Chirp3-HD-Achernar", "Achernar", listOf("ja-JP"), 24_000),
            hints = emptyList(),
        )

        val serialized = buildSynthesisJson(request)
        val decoded = GoogleJson.decodeFromString<GoogleSpeechSynthesisRequestDto>(serialized)

        assertEquals(sentence, decoded.input.text)
    }

    @Test
    fun parsesRepresentativeSynthesisResponse() {
        val response = GoogleJson.decodeFromString<GoogleSpeechSynthesisResponseDto>(
            """{"audioContent":"AQIDBA=="}""",
        )

        assertEquals("AQIDBA==", response.audioContent)
    }

    @Test
    fun ignoresExtraUnknownResponseFields() {
        val response = GoogleJson.decodeFromString<GoogleSpeechSynthesisResponseDto>(
            """{"audioContent":"AQIDBA==","unknown":{"future":true}}""",
        )

        assertEquals("AQIDBA==", response.audioContent)
    }

    @Test(expected = SerializationException::class)
    fun truncatedResponseThrowsInsteadOfProducingAnEmptyResult() {
        GoogleJson.decodeFromString<GoogleSpeechSynthesisResponseDto>("""{"audioContent":"unfinished""")
    }
}
