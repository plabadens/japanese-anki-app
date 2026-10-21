package dev.pierre.japaneseanki

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** Test seam for the shared Google connection mechanics; service policy stays in each adapter. */
internal fun interface GoogleHttpConnectionFactory {
    fun open(url: URL): HttpURLConnection
}

internal object DefaultGoogleHttpConnectionFactory : GoogleHttpConnectionFactory {
    override fun open(url: URL): HttpURLConnection = url.openConnection() as HttpURLConnection
}

internal data class GoogleHttpResponse(val code: Int, val body: ByteArray)

/** Bounded status-and-bytes exchange shared by the Google service adapters. */
internal object GoogleHttp {
    fun request(
        url: String,
        apiKey: String,
        timeoutMillis: Int,
        maxResponseBytes: Int,
        body: ByteArray?,
        connectionFactory: GoogleHttpConnectionFactory = DefaultGoogleHttpConnectionFactory,
    ): GoogleHttpResponse {
        require(maxResponseBytes >= 0)
        val connection = connectionFactory.open(URL(url))
        try {
            connection.requestMethod = if (body == null) "GET" else "POST"
            connection.connectTimeout = timeoutMillis
            connection.readTimeout = timeoutMillis
            connection.useCaches = false
            // Credentials must not be forwarded to a redirect target.
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("X-Goog-Api-Key", apiKey)
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.use { readBounded(it, maxResponseBytes) } ?: ByteArray(0)
            return GoogleHttpResponse(code, responseBody)
        } finally {
            connection.disconnect()
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
}