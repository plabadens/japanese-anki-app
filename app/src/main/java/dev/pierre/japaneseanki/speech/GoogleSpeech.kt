package dev.pierre.japaneseanki.speech

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Base64

/** Minimal HTTP result; headers are not needed and response bodies stay in memory. */
data class SpeechHttpResponse(val code: Int, val body: ByteArray)

/**
 * Injectable HTTP boundary so request serialization, auth headers, redirect refusal, and error
 * mapping can be exercised without a Google key or network.
 */
interface GoogleSpeechTransport {
    fun get(url: String, apiKey: String, timeoutMillis: Int): SpeechHttpResponse
    fun post(url: String, apiKey: String, json: ByteArray, timeoutMillis: Int): SpeechHttpResponse
}

class HttpGoogleSpeechTransport : GoogleSpeechTransport {
    override fun get(url: String, apiKey: String, timeoutMillis: Int): SpeechHttpResponse =
        request(url, apiKey, timeoutMillis, body = null)

    override fun post(url: String, apiKey: String, json: ByteArray, timeoutMillis: Int): SpeechHttpResponse =
        request(url, apiKey, timeoutMillis, body = json)

    private fun request(url: String, apiKey: String, timeoutMillis: Int, body: ByteArray?): SpeechHttpResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = timeoutMillis
            connection.readTimeout = timeoutMillis
            connection.useCaches = false
            // A redirect would hand the credential to another host; refuse instead of following.
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("X-Goog-Api-Key", apiKey)
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { input -> readBounded(input, MAX_RESPONSE_BYTES) } ?: ByteArray(0)
            return SpeechHttpResponse(code, bytes)
        } finally {
            connection.disconnect()
        }
    }

    private fun readBounded(input: java.io.InputStream, limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) throw IOException("Response too large")
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    companion object {
        private const val MAX_RESPONSE_BYTES = 32 * 1024 * 1024
    }
}

/**
 * Production Google Cloud Text-to-Speech adapter for Chirp 3 HD.
 *
 * The credential is supplied per call from the app's encrypted store. It is never written to logs,
 * never placed in a URL, and never forwarded on a redirect.
 */
class GoogleChirpSpeechProvider(
    private val credential: () -> String?,
    private val transport: GoogleSpeechTransport = HttpGoogleSpeechTransport(),
    private val timeoutMillis: Int = 20_000,
) : SentenceSpeechProvider {

    override fun listVoices(): List<CloudVoice> {
        val key = requireCredential()
        val response = transport.get(VOICES_URL, key, timeoutMillis)
        requireSuccess(response, "Voice listing")
        return parseJapaneseChirpVoices(response.body.toString(Charsets.UTF_8))
    }

    override fun synthesize(request: SentenceSpeechRequest, destination: File): File {
        val key = requireCredential()
        val payload = buildSynthesisJson(request)
        val response = transport.post(SYNTHESIZE_URL, key, payload.toByteArray(Charsets.UTF_8), timeoutMillis)
        requireSuccess(response, "Speech synthesis")
        val audio = decodeAudioResponse(response.body)
        val parent = destination.absoluteFile.parentFile ?: throw SpeechException(
            SpeechFailure.INVALID_AUDIO, "Audio destination has no directory",
        )
        if (!parent.exists() && !parent.mkdirs()) {
            throw SpeechException(SpeechFailure.INVALID_AUDIO, "Could not create the audio directory")
        }
        val temporary = File(parent, ".${destination.name}.${System.nanoTime()}.tmp.mp3")
        try {
            temporary.writeBytes(audio)
            requireMp3(temporary)
            try {
                Files.move(
                    temporary.toPath(), destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temporary.delete()
        }
        return destination
    }

    private fun requireCredential(): String {
        val key = credential()?.trim().orEmpty()
        if (key.isEmpty()) {
            throw SpeechException(SpeechFailure.AUTHORIZATION, "Add your Google Cloud API key in Settings.")
        }
        return key
    }

    private fun requireSuccess(response: SpeechHttpResponse, operation: String) {
        when (response.code) {
            in 200..299 -> Unit
            in 300..399 -> throw SpeechException(
                SpeechFailure.AUTHORIZATION, "$operation was redirected; the credential was not forwarded.",
            )
            401, 403 -> throw SpeechException(SpeechFailure.AUTHORIZATION, "$operation authorization failed.")
            429 -> throw SpeechException(SpeechFailure.QUOTA, "$operation quota was reached.")
            400 -> throw SpeechException(
                SpeechFailure.INVALID_REQUEST, "$operation request was rejected by Google Cloud.",
            )
            in 500..599 -> throw SpeechException(SpeechFailure.NETWORK, "$operation service is unavailable.")
            else -> throw SpeechException(SpeechFailure.INVALID_RESPONSE, "$operation failed.")
        }
    }

    private fun decodeAudioResponse(body: ByteArray): ByteArray {
        val json = runCatching { MiniJson.parse(body.toString(Charsets.UTF_8)) as? Map<*, *> }.getOrNull()
            ?: throw SpeechException(SpeechFailure.INVALID_RESPONSE, "Speech response was not JSON.")
        val encoded = (json["audioContent"] as? String)?.takeIf(String::isNotBlank)
            ?: throw SpeechException(SpeechFailure.INVALID_RESPONSE, "Speech response contained no audio.")
        val audio = runCatching { Base64.getDecoder().decode(encoded) }.getOrNull()
            ?: throw SpeechException(SpeechFailure.INVALID_RESPONSE, "Speech audio could not be decoded.")
        if (audio.size > MAX_AUDIO_BYTES) {
            throw SpeechException(SpeechFailure.INVALID_AUDIO, "Speech audio was too large.")
        }
        return audio
    }
}

/** Serializes the exact outbound synthesis request. Kept pure for tests. */
fun buildSynthesisJson(request: SentenceSpeechRequest): String {
    require(request.text.isNotBlank())
    val input = StringBuilder()
    input.append("{\"text\":").append(MiniJson.quote(request.text))
    if (request.hints.isNotEmpty()) {
        val hints = request.hints.joinToString(separator = ",", prefix = "[", postfix = "]") { hint ->
            buildString {
                append("{\"phrase\":").append(MiniJson.quote(hint.phrase))
                append(",\"phoneticEncoding\":\"PHONETIC_ENCODING_JAPANESE_YOMIGANA\"")
                append(",\"pronunciation\":").append(MiniJson.quote(hint.reading))
                append('}')
            }
        }
        input.append(",\"customPronunciations\":{\"pronunciations\":").append(hints).append('}')
    }
    input.append('}')
    return buildString {
        append("{\"input\":").append(input)
        append(",\"voice\":{\"languageCode\":").append(MiniJson.quote(request.languageCode))
        append(",\"name\":").append(MiniJson.quote(request.voice.name)).append('}')
        append(",\"audioConfig\":{\"audioEncoding\":\"MP3\",\"speakingRate\":")
        append(java.lang.Double.toString(request.speakingRate))
        append('}')
        append('}')
    }
}

/** Parses `GET /v1/voices` and keeps only Japanese-capable Chirp 3 HD entries. */
fun parseJapaneseChirpVoices(json: String): List<CloudVoice> {
    val root = runCatching { MiniJson.parse(json) as? Map<*, *> }.getOrNull()
        ?: throw SpeechException(SpeechFailure.INVALID_RESPONSE, "Voice list was not JSON.")
    val voices = root["voices"] as? List<*> ?: return emptyList()
    return voices.mapNotNull { entry ->
        val voice = entry as? Map<*, *> ?: return@mapNotNull null
        val name = voice["name"] as? String ?: return@mapNotNull null
        val codes = (voice["languageCodes"] as? List<*>)?.mapNotNull { it as? String }.orEmpty()
        if (codes.none { it.equals("ja-JP", ignoreCase = true) }) return@mapNotNull null
        if (!name.contains(CHIRP3_HD_MARKER, ignoreCase = true)) return@mapNotNull null
        val provided = (voice["ssmlGender"] as? String)?.trim().orEmpty()
        val display = name.substringAfterLast('-').ifBlank { name }
        CloudVoice(
            name = name,
            displayName = if (provided.isEmpty()) display else "$display · $provided",
            languageCodes = codes,
            naturalSampleRateHz = (voice["naturalSampleRateHertz"] as? Double)?.toInt(),
        )
    }.distinctBy(CloudVoice::name).sortedBy(CloudVoice::name)
}

/** Structural MP3 check: ID3v2 tag or MPEG audio frame sync, sane size, no truncated header. */
internal fun requireMp3(file: File) {
    if (!file.isFile || file.length() < MIN_MP3_BYTES || file.length() > MAX_AUDIO_BYTES) {
        throw SpeechException(SpeechFailure.INVALID_AUDIO, "Speech audio was not a usable MP3 file.")
    }
    val header = ByteArray(4)
    val read = file.inputStream().use { it.read(header) }
    if (read < 4) throw SpeechException(SpeechFailure.INVALID_AUDIO, "Speech audio was not a usable MP3 file.")
    val id3 = header[0].toInt() == 0x49 && header[1].toInt() == 0x44 && header[2].toInt() == 0x33
    val frameSync = (header[0].toInt() and 0xff) == 0xff && (header[1].toInt() and 0xe0) == 0xe0
    if (!id3 && !frameSync) {
        throw SpeechException(SpeechFailure.INVALID_AUDIO, "Speech audio was not a usable MP3 file.")
    }
}

private const val VOICES_URL = "https://texttospeech.googleapis.com/v1/voices?languageCode=ja-JP"
private const val SYNTHESIZE_URL = "https://texttospeech.googleapis.com/v1/text:synthesize"
private const val CHIRP3_HD_MARKER = "Chirp3-HD"
private const val MIN_MP3_BYTES = 256
private const val MAX_AUDIO_BYTES = 20L * 1024 * 1024
