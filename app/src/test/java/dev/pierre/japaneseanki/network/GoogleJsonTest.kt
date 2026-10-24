package dev.pierre.japaneseanki.network

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleJsonTest {
    @Serializable
    private data class Payload(val text: String)

    @Test
    fun roundTripsSerializablePayload() {
        val payload = Payload("こんにちは")

        assertEquals(payload, GoogleJson.decodeFromString<Payload>(GoogleJson.encodeToString(payload)))
    }

    @Test
    fun ignoresUnknownKeys() {
        assertEquals(Payload("hello"), GoogleJson.decodeFromString<Payload>("""{"text":"hello","extra":1}"""))
    }

    @Test(expected = SerializationException::class)
    fun truncatedPayloadFailsInsteadOfReturningAnEmptyObject() {
        GoogleJson.decodeFromString<Payload>("""{"text":"unfinished""")
    }
}
