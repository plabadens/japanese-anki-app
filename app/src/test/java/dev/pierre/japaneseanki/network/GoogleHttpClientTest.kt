package dev.pierre.japaneseanki.network

import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GoogleHttpClientTest {
    @Test
    fun successfulResponseReturnsStatusAndBytesAndSendsCredentialHeader() {
        val server = MockWebServer()
        server.start()
        try {
            val expected = byteArrayOf(0, 1, 2, 0xff.toByte())
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(okio.Buffer().write(expected)),
            )

            val response = runBlocking {
                request(server.url("/test").toString(), "test-key", 2_000, 128)
            }
            val recorded = server.takeRequest()

            assertEquals(200, response.code)
            assertArrayEquals(expected, response.body)
            assertEquals("GET", recorded.method)
            assertEquals("test-key", recorded.getHeader("X-Goog-Api-Key"))
            assertEquals("application/json", recorded.getHeader("Accept"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun nonSuccessResponseReturnsItsErrorBody() {
        val server = MockWebServer()
        server.start()
        try {
            val errorBody = """{"error":{"message":"invalid key"}}"""
            server.enqueue(MockResponse().setResponseCode(400).setBody(errorBody))

            val response = runBlocking {
                request(server.url("/test").toString(), "test-key", 2_000, 128)
            }
            val recorded = server.takeRequest()

            assertEquals(400, response.code)
            assertArrayEquals(errorBody.toByteArray(Charsets.UTF_8), response.body)
            assertEquals("test-key", recorded.getHeader("X-Goog-Api-Key"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun redirectIsReturnedWithoutForwardingCredentialOrReplayingPostBody() {
        val serverA = MockWebServer()
        val serverB = MockWebServer()
        serverA.start()
        serverB.start()
        try {
            serverA.enqueue(
                MockResponse()
                    .setResponseCode(302)
                    .addHeader("Location", serverB.url("/credential-target"))
                    .setBody("redirect body"),
            )
            val requestBody = """{"text":"こんにちは"}""".toByteArray(Charsets.UTF_8)

            val response = runBlocking {
                request(serverA.url("/translate").toString(), "test-key", 2_000, 128, requestBody)
            }
            val recordedA = serverA.takeRequest()

            assertEquals(302, response.code)
            assertArrayEquals("redirect body".toByteArray(), response.body)
            assertEquals("POST", recordedA.method)
            assertEquals("test-key", recordedA.getHeader("X-Goog-Api-Key"))
            assertEquals("application/json; charset=utf-8", recordedA.getHeader("Content-Type"))
            assertArrayEquals(requestBody, recordedA.body.readByteArray())
            assertEquals(0, serverB.requestCount)
            assertNull(serverB.takeRequest(100, TimeUnit.MILLISECONDS))
        } finally {
            serverA.shutdown()
            serverB.shutdown()
        }
    }

    @Test
    fun oversizedResponseThrowsBoundedReadFailure() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(200).setBody("0123456789abcdef"))

            val failure = try {
                runBlocking {
                    request(server.url("/large").toString(), "test-key", 2_000, 8)
                }
                throw AssertionError("Expected an oversized response to fail")
            } catch (exception: IOException) {
                exception
            }
            val recorded = server.takeRequest()

            assertEquals("Response too large", failure.message)
            assertEquals("test-key", recorded.getHeader("X-Goog-Api-Key"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun readTimeoutFailsWithinTwiceConfiguredTimeout() {
        val server = MockWebServer()
        server.start()
        try {
            val timeoutMillis = 400
            server.enqueue(
                MockResponse()
                    .setBody("slow body")
                    .setBodyDelay(timeoutMillis * 3L, TimeUnit.MILLISECONDS),
            )
            val startedAt = System.nanoTime()

            val failure = try {
                runBlocking {
                    request(server.url("/slow").toString(), "test-key", timeoutMillis, 128)
                }
                throw AssertionError("Expected the response read to time out")
            } catch (exception: IOException) {
                exception
            }
            val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
            val recorded = server.takeRequest(2, TimeUnit.SECONDS)
                ?: throw AssertionError("MockWebServer did not record the timed-out request")

            assertTrue("Expected an I/O timeout, got $failure", failure is InterruptedIOException)
            assertEquals("test-key", recorded.getHeader("X-Goog-Api-Key"))
            assertTrue("Read took ${elapsedMillis}ms for a ${timeoutMillis}ms timeout", elapsedMillis < timeoutMillis * 2L)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun identicalRequestsAreNotServedFromCache() {
        val server = MockWebServer()
        server.start()
        try {
            repeat(2) {
                server.enqueue(MockResponse().setResponseCode(200).setBody("same response"))
            }

            val first = runBlocking {
                request(server.url("/same").toString(), "test-key", 2_000, 128)
            }
            val second = runBlocking {
                request(server.url("/same").toString(), "test-key", 2_000, 128)
            }

            assertEquals(200, first.code)
            assertEquals(200, second.code)
            assertArrayEquals(first.body, second.body)
            assertEquals(2, server.requestCount)
            repeat(2) {
                val recorded = server.takeRequest()
                assertEquals("test-key", recorded.getHeader("X-Goog-Api-Key"))
            }
        } finally {
            server.shutdown()
        }
    }
}
