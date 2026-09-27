package dev.pierre.japaneseanki

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AzureSpeechSynthesizerTest {
    private val temporaryDirectory = Files.createTempDirectory("azure-speech-test").toFile()

    @After
    fun cleanUp() {
        temporaryDirectory.deleteRecursively()
    }

    @Test
    fun requestUsesAzureEndpointEscapesTextAndCommitsWaveAtomically() {
        val connection = FakeConnection(200, waveBytes())
        val output = File(temporaryDirectory, "speech.wav")
        val synthesizer = AzureSpeechSynthesizer { url ->
            assertEquals("https://eastus.tts.speech.microsoft.com/cognitiveservices/v1", url.toString())
            connection
        }

        val result = synthesizer.synthesizeToFile(
            subscriptionKey = "runtime-only-key",
            region = "eastus",
            voice = "ja-JP-NanamiNeural",
            rate = 1.25f,
            text = "こんにちは <世界> & \"音声\"",
            output = output,
            timeoutMillis = 2_000,
        )

        assertEquals(output, result)
        assertArrayEquals(waveBytes(), output.readBytes())
        assertEquals("POST", connection.requestMethod)
        assertEquals("runtime-only-key", connection.getRequestProperty("Ocp-Apim-Subscription-Key"))
        assertEquals("application/ssml+xml", connection.getRequestProperty("Content-Type"))
        assertEquals("riff-24khz-16bit-mono-pcm", connection.getRequestProperty("X-Microsoft-OutputFormat"))
        assertTrue(connection.getRequestProperty("User-Agent").length < 255)
        val ssml = connection.requestBody.toString(Charsets.UTF_8)
        assertTrue(ssml.contains("voice name=\"ja-JP-NanamiNeural\""))
        assertTrue(ssml.contains("rate=\"+25.00%\""))
        assertTrue(ssml.contains("こんにちは &lt;世界&gt; &amp; &quot;音声&quot;"))
    }

    @Test
    fun redirectsAreDisabledAndEveryRedirectStatusIsRejected() {
        listOf(300, 301, 399).forEach { status ->
            val connection = FakeConnection(status, byteArrayOf())
            val error = assertThrowsAzure(AzureSpeechErrorKind.SERVICE_ERROR) {
                AzureSpeechSynthesizer { connection }
                    .synthesizeToFile("key", "eastus", "ja-JP-NanamiNeural", 1f, "日本語", File(temporaryDirectory, "$status.wav"))
            }
            assertTrue(error.message.orEmpty().contains("redirect"))
            assertFalse(connection.instanceFollowRedirects)
        }
    }

    @Test
    fun invalidEndpointPartsAreRejectedBeforeOpeningConnection() {
        var opened = false
        val synthesizer = AzureSpeechSynthesizer {
            opened = true
            throw AssertionError("connection should not be opened")
        }
        val output = File(temporaryDirectory, "speech.wav")

        val error = assertThrowsAzure(AzureSpeechErrorKind.INVALID_INPUT) {
            synthesizer.synthesizeToFile("key", "eastus/attacker", "ja-JP-NanamiNeural", 1f, "日本語", output)
        }
        assertEquals("Speech region is invalid", error.message)
        assertFalse(opened)

        assertThrowsAzure(AzureSpeechErrorKind.INVALID_INPUT) {
            synthesizer.synthesizeToFile("key", "eastus", "ja-JP-\"/><bad", 1f, "日本語", output)
        }
    }

    @Test
    fun serviceFailuresAreClassifiedWithoutReturningOrChangingOutput() {
        val output = File(temporaryDirectory, "speech.wav")
        val original = byteArrayOf(1, 2, 3)
        output.writeBytes(original)
        val statuses = mapOf(
            400 to AzureSpeechErrorKind.REQUEST_REJECTED,
            401 to AzureSpeechErrorKind.AUTHENTICATION,
            415 to AzureSpeechErrorKind.REQUEST_REJECTED,
            429 to AzureSpeechErrorKind.RATE_LIMITED,
            502 to AzureSpeechErrorKind.SERVICE_UNAVAILABLE,
            503 to AzureSpeechErrorKind.SERVICE_UNAVAILABLE,
        )
        statuses.forEach { (status, kind) ->
            val synthesizer = AzureSpeechSynthesizer { FakeConnection(status, byteArrayOf()) }
            val error = assertThrowsAzure(kind) {
                synthesizer.synthesizeToFile("secret-key", "eastus", "ja-JP-NanamiNeural", 1f, "日本語", output)
            }
            assertFalse(error.message.orEmpty().contains("secret-key"))
            assertArrayEquals(original, output.readBytes())
        }
    }

    @Test
    fun invalidOrPartialAudioDoesNotCreateAUsableOutput() {
        val output = File(temporaryDirectory, "speech.wav")
        val original = byteArrayOf(9, 8, 7)
        output.writeBytes(original)

        listOf(
            byteArrayOf(1, 2, 3, 4, 5),
            waveBytes().copyOf(12),
            waveBytes().copyOf(16).also { it[4] = 8 },
        ).forEach { body ->
            assertThrowsAzure(AzureSpeechErrorKind.INVALID_AUDIO) {
                AzureSpeechSynthesizer { FakeConnection(200, body) }
                    .synthesizeToFile("key", "eastus", "ja-JP-NanamiNeural", 1f, "日本語", output)
            }
            assertArrayEquals(original, output.readBytes())
        }
        assertTrue(temporaryDirectory.listFiles()?.none { it.name.endsWith(".tmp") } == true)

        assertThrowsAzure(AzureSpeechErrorKind.NETWORK) {
            AzureSpeechSynthesizer { FakeConnection(200, waveBytes(), failDuringRead = true) }
                .synthesizeToFile("key", "eastus", "ja-JP-NanamiNeural", 1f, "日本語", output)
        }
        assertArrayEquals(original, output.readBytes())
    }

    private fun assertThrowsAzure(expected: AzureSpeechErrorKind, action: () -> Unit): AzureSpeechException {
        return try {
            action()
            throw AssertionError("expected $expected")
        } catch (error: AzureSpeechException) {
            assertEquals(expected, error.kind)
            error
        }
    }

    private fun waveBytes(): ByteArray = byteArrayOf(
        'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
        40, 0, 0, 0, 'W'.code.toByte(), 'A'.code.toByte(), 'V'.code.toByte(), 'E'.code.toByte(),
        'f'.code.toByte(), 'm'.code.toByte(), 't'.code.toByte(), ' '.code.toByte(),
        16, 0, 0, 0, 1, 0, 1, 0,
        0xC0.toByte(), 0x5D.toByte(), 0, 0, 0x80.toByte(), 0xBB.toByte(), 0, 0,
        2, 0, 16, 0,
        'd'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte(),
        4, 0, 0, 0, 0, 0, 0, 0,
    )

    private class FakeConnection(
        private val status: Int,
        private val body: ByteArray,
        private val failDuringRead: Boolean = false,
    ) : HttpURLConnection(URL("https://eastus.tts.speech.microsoft.com/cognitiveservices/v1")) {
        val requestBody = ByteArrayOutputStream()

        override fun getOutputStream(): ByteArrayOutputStream = requestBody

        override fun getInputStream(): InputStream = if (failDuringRead) {
            object : ByteArrayInputStream(body) {
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    if (pos > 0) throw IOException("simulated partial read")
                    return super.read(buffer, offset, length)
                }
            }
        } else {
            ByteArrayInputStream(body)
        }

        override fun getResponseCode(): Int = status
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
    }
}
