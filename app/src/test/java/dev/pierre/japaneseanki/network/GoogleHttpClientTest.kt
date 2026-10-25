package dev.pierre.japaneseanki.network

import dev.pierre.japaneseanki.HttpTranslatorTransport
import dev.pierre.japaneseanki.speech.HttpGoogleSpeechTransport
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.yield
import dev.pierre.japaneseanki.runSuspendTest
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

            val response = runSuspendTest {
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

            val response = runSuspendTest {
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

            val response = runSuspendTest {
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
                runSuspendTest {
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
                runSuspendTest {
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

            val first = runSuspendTest {
                request(server.url("/same").toString(), "test-key", 2_000, 128)
            }
            val second = runSuspendTest {
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

    @Test
    fun serviceTransportsUseRecordedRequestsForSpeechAndTranslation() = runSuspendTest {
        val server = MockWebServer()
        server.start()
        try {
            val voicesResponse = """{"voices":[]}"""
            server.enqueue(MockResponse().setResponseCode(200).setBody(voicesResponse))
            val voicesResult = HttpGoogleSpeechTransport().get(
                server.url("/v1/voices?languageCode=ja-JP").toString(), "speech-key", 2_000,
            )
            val voicesRequest = server.takeRequest()
            assertEquals(200, voicesResult.code)
            assertEquals(voicesResponse, voicesResult.body.toString(Charsets.UTF_8))
            assertEquals("GET", voicesRequest.method)
            assertEquals("/v1/voices?languageCode=ja-JP", voicesRequest.path)
            assertEquals("speech-key", voicesRequest.getHeader("X-Goog-Api-Key"))
            assertEquals("application/json", voicesRequest.getHeader("Accept"))
            assertArrayEquals(ByteArray(0), voicesRequest.body.readByteArray())

            val speechResponse = """{"audioContent":"AQID"}"""
            server.enqueue(MockResponse().setResponseCode(200).setBody(speechResponse))
            val speechBody = """{"input":{"text":"猫"}}""".toByteArray(Charsets.UTF_8)
            val speechResult = HttpGoogleSpeechTransport().post(
                server.url("/v1/text:synthesize").toString(), "speech-key", speechBody, 2_000,
            )
            val speechRequest = server.takeRequest()
            assertEquals(200, speechResult.code)
            assertEquals(speechResponse, speechResult.body.toString(Charsets.UTF_8))
            assertEquals("POST", speechRequest.method)
            assertEquals("/v1/text:synthesize", speechRequest.path)
            assertEquals("speech-key", speechRequest.getHeader("X-Goog-Api-Key"))
            assertEquals("application/json; charset=utf-8", speechRequest.getHeader("Content-Type"))
            assertArrayEquals(speechBody, speechRequest.body.readByteArray())

            val translationResponse = """{"data":{"translations":[{"translatedText":"cat"}]}}"""
            server.enqueue(MockResponse().setResponseCode(200).setBody(translationResponse))
            val translationBody = """{"q":"猫","source":"ja","target":"en","format":"text","model":"nmt"}"""
                .toByteArray(Charsets.UTF_8)
            val translationResult = HttpTranslatorTransport().post(
                server.url("/language/translate/v2").toString(), "translation-key", translationBody, 2_000,
            )
            val translationRequest = server.takeRequest()
            assertEquals(200, translationResult.code)
            assertEquals(translationResponse, translationResult.body)
            assertEquals("POST", translationRequest.method)
            assertEquals("/language/translate/v2", translationRequest.path)
            assertEquals("translation-key", translationRequest.getHeader("X-Goog-Api-Key"))
            assertEquals("application/json; charset=utf-8", translationRequest.getHeader("Content-Type"))
            assertArrayEquals(translationBody, translationRequest.body.readByteArray())
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun cancellingSuspendedRequestCancelsOkHttpCall() = runSuspendTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setBody("slow body")
                    .setBodyDelay(10, TimeUnit.SECONDS),
            )
            coroutineScope {
                val pending = async {
                    request(server.url("/cancel").toString(), "test-key", 20_000, 128)
                }
                yield()
                val recorded = server.takeRequest(2, TimeUnit.SECONDS)
                    ?: throw AssertionError("MockWebServer did not record the request")
                assertEquals("test-key", recorded.getHeader("X-Goog-Api-Key"))

                pending.cancelAndJoin()
                assertTrue(pending.isCancelled)
            }
        } finally {
            server.shutdown()
        }
    }
}
