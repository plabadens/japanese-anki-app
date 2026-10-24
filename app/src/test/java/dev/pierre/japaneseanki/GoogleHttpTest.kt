package dev.pierre.japaneseanki

import androidx.test.ext.junit.runners.AndroidJUnit4

import dev.pierre.japaneseanki.speech.GoogleChirpSpeechProvider
import dev.pierre.japaneseanki.speech.HttpGoogleSpeechTransport
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

@RunWith(AndroidJUnit4::class)
class GoogleHttpTest {
    private class TrackingInputStream(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        var closed = false

        override fun close() {
            closed = true
            super.close()
        }
    }

    private class FailingInputStream : InputStream() {
        var closed = false

        override fun read(): Int = throw IOException("injected read failure")

        override fun close() {
            closed = true
        }
    }

    private class RecordingOutputStream(private val failWrites: Boolean) : OutputStream() {
        private val bytes = ByteArrayOutputStream()
        var closed = false
            private set

        override fun write(value: Int) {
            if (failWrites) throw IOException("injected write failure")
            bytes.write(value)
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            if (failWrites) throw IOException("injected write failure")
            bytes.write(buffer, offset, length)
        }

        override fun close() {
            closed = true
            bytes.close()
        }

        fun bytes(): ByteArray = bytes.toByteArray()
    }

    private class FakeConnection(
        url: URL,
        var status: Int = 200,
        var input: InputStream = TrackingInputStream(ByteArray(0)),
        var error: InputStream? = null,
        private val failWrites: Boolean = false,
    ) : HttpURLConnection(url) {
        var disconnected = false
        var outputOpened = false
        var output: RecordingOutputStream? = null
        var fixedLength: Int? = null
        var responseFailure: IOException? = null

        override fun connect() = Unit

        override fun disconnect() {
            disconnected = true
        }

        override fun usingProxy(): Boolean = false

        override fun getInputStream(): InputStream = input

        override fun getErrorStream(): InputStream? = error

        override fun getResponseCode(): Int {
            responseFailure?.let { throw it }
            return status
        }

        override fun getOutputStream(): OutputStream {
            outputOpened = true
            return RecordingOutputStream(failWrites).also { output = it }
        }

        override fun setFixedLengthStreamingMode(contentLength: Int) {
            fixedLength = contentLength
            super.setFixedLengthStreamingMode(contentLength)
        }
    }

    private fun factory(connection: FakeConnection) = GoogleHttpConnectionFactory { openedUrl ->
        assertEquals("https://google.example/v1/test", openedUrl.toString())
        connection
    }

    private fun request(
        connection: FakeConnection,
        body: ByteArray? = null,
        limit: Int = 32,
    ) = GoogleHttp.request(
        url = "https://google.example/v1/test",
        apiKey = "test-key",
        timeoutMillis = 1_234,
        maxResponseBytes = limit,
        body = body,
        connectionFactory = factory(connection),
    )

    @Test
    fun postSetsCredentialHeadersMethodTimeoutsRedirectPolicyAndFixedLengthBody() {
        val responseStream = TrackingInputStream("ok".toByteArray())
        val connection = FakeConnection(URL("https://google.example/v1/test"), input = responseStream)
        val body = "{\"q\":\"文\"}".toByteArray(Charsets.UTF_8)

        val response = request(connection, body = body)

        assertEquals(200, response.code)
        assertArrayEquals("ok".toByteArray(), response.body)
        assertEquals("POST", connection.requestMethod)
        assertEquals("test-key", connection.getRequestProperty("X-Goog-Api-Key"))
        assertEquals("application/json", connection.getRequestProperty("Accept"))
        assertEquals("application/json; charset=utf-8", connection.getRequestProperty("Content-Type"))
        assertEquals(1_234, connection.connectTimeout)
        assertEquals(1_234, connection.readTimeout)
        assertFalse(connection.instanceFollowRedirects)
        assertFalse(connection.useCaches)
        assertTrue(connection.doOutput)
        assertTrue(connection.outputOpened)
        assertArrayEquals(body, connection.output?.bytes())
        assertEquals(body.size, connection.fixedLength)
        assertTrue(connection.output?.closed == true)
        assertTrue(responseStream.closed)
        assertTrue(connection.disconnected)
    }

    @Test
    fun getIsBodylessAndRedirectResponseIsReturnedWithoutFollowingIt() {
        val errorStream = TrackingInputStream("redirect response".toByteArray())
        val connection = FakeConnection(
            URL("https://google.example/v1/test"),
            status = 302,
            error = errorStream,
        )

        val response = request(connection)

        assertEquals(302, response.code)
        assertArrayEquals("redirect response".toByteArray(), response.body)
        assertEquals("GET", connection.requestMethod)
        assertEquals("test-key", connection.getRequestProperty("X-Goog-Api-Key"))
        assertNull(connection.getRequestProperty("Content-Type"))
        assertFalse(connection.doOutput)
        assertFalse(connection.outputOpened)
        assertFalse(connection.instanceFollowRedirects)
        assertTrue(errorStream.closed)
        assertTrue(connection.disconnected)
    }

    @Test
    fun exactResponseLimitIsAcceptedAndOneByteOverIsRejectedForSuccessAndErrorStreams() {
        val successStream = TrackingInputStream(byteArrayOf(1, 2, 3))
        val success = FakeConnection(URL("https://google.example/v1/test"), input = successStream)
        assertArrayEquals(byteArrayOf(1, 2, 3), request(success, limit = 3).body)
        assertTrue(successStream.closed)
        assertTrue(success.disconnected)

        val oversizedSuccessStream = TrackingInputStream(byteArrayOf(1, 2, 3, 4))
        val oversizedSuccess = FakeConnection(
            URL("https://google.example/v1/test"),
            input = oversizedSuccessStream,
        )
        assertTrue(runCatching { request(oversizedSuccess, limit = 3) }.exceptionOrNull() is IOException)
        assertTrue(oversizedSuccessStream.closed)
        assertTrue(oversizedSuccess.disconnected)

        val exactErrorStream = TrackingInputStream(byteArrayOf(1, 2, 3))
        val exactError = FakeConnection(
            URL("https://google.example/v1/test"),
            status = 403,
            error = exactErrorStream,
        )
        assertArrayEquals(byteArrayOf(1, 2, 3), request(exactError, limit = 3).body)
        assertTrue(exactErrorStream.closed)
        assertTrue(exactError.disconnected)

        val oversizedErrorStream = TrackingInputStream(byteArrayOf(1, 2, 3, 4))
        val oversizedError = FakeConnection(
            URL("https://google.example/v1/test"),
            status = 500,
            error = oversizedErrorStream,
        )
        assertTrue(runCatching { request(oversizedError, limit = 3) }.exceptionOrNull() is IOException)
        assertTrue(oversizedErrorStream.closed)
        assertTrue(oversizedError.disconnected)
    }

    @Test
    fun missingErrorStreamIsAnEmptyBodyAndConnectionStillDisconnects() {
        val connection = FakeConnection(URL("https://google.example/v1/test"), status = 401)
        val response = request(connection)

        assertEquals(401, response.code)
        assertArrayEquals(ByteArray(0), response.body)
        assertTrue(connection.disconnected)
    }

    @Test
    fun readWriteAndResponseCodeFailuresCloseOwnedResourcesAndDisconnect() {
        val readFailure = FailingInputStream()
        val readConnection = FakeConnection(URL("https://google.example/v1/test"), input = readFailure)
        assertTrue(runCatching { request(readConnection) }.exceptionOrNull() is IOException)
        assertTrue(readFailure.closed)
        assertTrue(readConnection.disconnected)

        val writeConnection = FakeConnection(
            URL("https://google.example/v1/test"),
            failWrites = true,
        )
        assertTrue(
            runCatching { request(writeConnection, body = byteArrayOf(1, 2)) }
                .exceptionOrNull() is IOException,
        )
        assertTrue(writeConnection.output?.closed == true)
        assertTrue(writeConnection.disconnected)

        val responseCodeConnection = FakeConnection(URL("https://google.example/v1/test")).also {
            it.responseFailure = IOException("injected response-code failure")
        }
        assertTrue(runCatching { request(responseCodeConnection) }.exceptionOrNull() is IOException)
        assertTrue(responseCodeConnection.disconnected)
    }

    @Test
    fun bothServiceAdaptersUseTheSharedConnectionPrimitiveAndKeepSeparateLimits() {
        assertEquals(256 * 1024, HttpTranslatorTransport.MAX_RESPONSE_BYTES)
        assertEquals(32 * 1024 * 1024, HttpGoogleSpeechTransport.MAX_RESPONSE_BYTES)

        val translationServer = MockWebServer()
        translationServer.start()
        try {
            val translationBody = """{"data":{"translations":[{"translatedText":"Hello."}]}}"""
            translationServer.enqueue(
                MockResponse().setResponseCode(200).setBody(translationBody),
            )
            val request = """{"q":"文"}""".toByteArray(Charsets.UTF_8)
            val response = runBlocking {
                HttpTranslatorTransport().post(
                    translationServer.url("/translate").toString(), "test-key", request, 1_000,
                )
            }
            val recorded = translationServer.takeRequest()
            assertEquals(200, response.code)
            assertEquals(translationBody, response.body)
            assertEquals("POST", recorded.method)
            assertEquals("test-key", recorded.getHeader("X-Goog-Api-Key"))
            assertArrayEquals(request, recorded.body.readByteArray())
        } finally {
            translationServer.shutdown()
        }

        val voiceList = """{"voices":[]}""".toByteArray()
        val speechStream = TrackingInputStream(voiceList)
        val speechConnection = FakeConnection(
            URL("https://texttospeech.googleapis.com/v1/voices?languageCode=ja-JP"),
            input = speechStream,
        )
        val speechProvider = GoogleChirpSpeechProvider(
            credential = { "test-key" },
            transport = HttpGoogleSpeechTransport(GoogleHttpConnectionFactory { speechConnection }),
            timeoutMillis = 1_000,
        )
        assertTrue(speechProvider.listVoices().isEmpty())
        assertEquals("GET", speechConnection.requestMethod)
        assertEquals("test-key", speechConnection.getRequestProperty("X-Goog-Api-Key"))
        assertTrue(speechConnection.disconnected)
    }
}