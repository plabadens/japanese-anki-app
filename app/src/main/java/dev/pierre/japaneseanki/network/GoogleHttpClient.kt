package dev.pierre.japaneseanki.network

import dev.pierre.japaneseanki.GoogleHttpResponse
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private val googleHttpClient = OkHttpClient.Builder()
    .followRedirects(false)
    .followSslRedirects(false)
    .cache(null)
    .retryOnConnectionFailure(false)
    .build()

internal suspend fun request(
    url: String,
    apiKey: String,
    timeoutMillis: Int,
    maxResponseBytes: Int,
    body: ByteArray? = null,
): GoogleHttpResponse = withContext(Dispatchers.IO) {
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

    client.newCall(request).execute().use { response ->
        val responseBody = response.body?.byteStream()?.use { readBounded(it, maxResponseBytes) }
            ?: ByteArray(0)
        GoogleHttpResponse(response.code, responseBody)
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
