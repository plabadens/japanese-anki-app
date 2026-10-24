package dev.pierre.japaneseanki.network

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import okhttp3.Callback
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody

private val googleHttpClient = OkHttpClient.Builder()
    .followRedirects(false)
    .followSslRedirects(false)
    .cache(null)
    .retryOnConnectionFailure(false)
    .build()

internal data class GoogleHttpResponse(val code: Int, val body: ByteArray)

internal suspend fun request(
    url: String,
    apiKey: String,
    timeoutMillis: Int,
    maxResponseBytes: Int,
    body: ByteArray? = null,
): GoogleHttpResponse {
    require(timeoutMillis > 0) { "timeoutMillis must be positive" }
    require(maxResponseBytes >= 0) { "maxResponseBytes must not be negative" }

    val client = googleHttpClient.newBuilder()
        .connectTimeout(timeoutMillis.toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(timeoutMillis.toLong(), TimeUnit.MILLISECONDS)
        .callTimeout(timeoutMillis.toLong(), TimeUnit.MILLISECONDS)
        .build()
    val request = Request.Builder()
        .url(url)
        .header("X-Goog-Api-Key", apiKey)
        .header("Accept", "application/json")
        .apply {
            if (body != null) {
                header("Content-Type", "application/json; charset=utf-8")
                post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            }
        }
        .build()

    val call = client.newCall(request)
    return suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val result = response.use {
                        val responseBody = it.body?.byteStream()?.use { stream ->
                            readBounded(stream, maxResponseBytes)
                        } ?: ByteArray(0)
                        GoogleHttpResponse(it.code, responseBody)
                    }
                    continuation.resume(result)
                } catch (error: IOException) {
                    continuation.resumeWithException(error)
                }
            }
        })
    }
}

private fun readBounded(input: InputStream, limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        if (read > limit - total) throw IOException("Response too large")
        total += read
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}
