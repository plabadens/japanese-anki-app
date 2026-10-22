package dev.pierre.japaneseanki.speech

import dev.pierre.japaneseanki.GoogleApiProblem
import dev.pierre.japaneseanki.DefaultGoogleHttpConnectionFactory
import dev.pierre.japaneseanki.GoogleHttp
import dev.pierre.japaneseanki.GoogleHttpConnectionFactory
import dev.pierre.japaneseanki.classifyGoogleApiError
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Base64
import javax.net.ssl.SSLException

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
    private val connectionFactory: GoogleHttpConnectionFactory

    constructor() : this(DefaultGoogleHttpConnectionFactory)

    internal constructor(connectionFactory: GoogleHttpConnectionFactory) {
        this.connectionFactory = connectionFactory
    }

    override fun get(url: String, apiKey: String, timeoutMillis: Int): SpeechHttpResponse =
        request(url, apiKey, timeoutMillis, body = null)

    override fun post(url: String, apiKey: String, json: ByteArray, timeoutMillis: Int): SpeechHttpResponse =
        request(url, apiKey, timeoutMillis, body = json)

    private fun request(url: String, apiKey: String, timeoutMillis: Int, body: ByteArray?): SpeechHttpResponse {
        val response = GoogleHttp.request(
            url = url,
            apiKey = apiKey,
            timeoutMillis = timeoutMillis,
            maxResponseBytes = MAX_RESPONSE_BYTES,
            body = body,
            connectionFactory = connectionFactory,
        )
        return SpeechHttpResponse(response.code, response.body)
    }

    companion object {
        internal const val MAX_RESPONSE_BYTES = 32 * 1024 * 1024
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

    override fun listVoices(): List<CloudVoice> = diagnosedRequest(
        SpeechRequestMetadata(SpeechOperation.VOICE_LIST),
    ) { updateStatus ->
        val key = requireCredential()
        val response = transport.get(VOICES_URL, key, timeoutMillis)
        updateStatus(response.code)
        requireSuccess(response, "Voice listing", SpeechRequestMetadata(SpeechOperation.VOICE_LIST))
        parseJapaneseChirpVoices(response.body.toString(Charsets.UTF_8))
    }

    override fun synthesize(request: SentenceSpeechRequest, destination: File): File {
        val json = buildSynthesisJson(request)
        val body = json.toByteArray(Charsets.UTF_8)
        val metadata = synthesisRequestMetadata(request, synthesisContentBytes(request), body.size)
        val audio = diagnosedRequest(metadata) { updateStatus ->
            val key = requireCredential()
            val response = transport.post(SYNTHESIZE_URL, key, body, timeoutMillis)
            updateStatus(response.code)
            requireSuccess(response, "Speech synthesis", metadata)
            decodeAudioResponse(response.body)
        }
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

    private inline fun <T> diagnosedRequest(
        metadata: SpeechRequestMetadata,
        block: (updateStatus: (Int) -> Unit) -> T,
    ): T {
        val started = System.nanoTime()
        var status: Int? = null
        try {
            return block { status = it }
        } catch (error: SpeechException) {
            val previous = error.diagnostic
            throw error.withDiagnostic(
                SpeechDiagnostic(
                    category = error.failure,
                    httpStatus = previous?.httpStatus ?: status,
                    googleStatus = previous?.googleStatus,
                    googleReason = previous?.googleReason,
                    exceptionClass = previous?.exceptionClass,
                    durationMillis = elapsedMillis(started),
                    request = metadata,
                ),
            )
        } catch (error: SocketTimeoutException) {
            throw transportFailure(SpeechFailure.TIMEOUT, operationName(metadata) + " timed out.", error, metadata, status, started)
        } catch (error: UnknownHostException) {
            throw transportFailure(SpeechFailure.DNS, operationName(metadata) + " service host could not be resolved.", error, metadata, status, started)
        } catch (error: SSLException) {
            throw transportFailure(SpeechFailure.TLS, "A secure connection to the ${operationName(metadata).lowercase()} service failed.", error, metadata, status, started)
        } catch (error: IOException) {
            throw transportFailure(SpeechFailure.NETWORK, operationName(metadata) + " service is unavailable.", error, metadata, status, started)
        }
    }

    private fun operationName(metadata: SpeechRequestMetadata): String = when (metadata.operation) {
        SpeechOperation.VOICE_LIST -> "Voice listing"
        SpeechOperation.SYNTHESIS -> "Speech synthesis"
    }

    private fun transportFailure(
        failure: SpeechFailure,
        message: String,
        error: Exception,
        metadata: SpeechRequestMetadata,
        status: Int?,
        started: Long,
    ) = SpeechException(
        failure = failure,
        message = message,
        diagnostic = SpeechDiagnostic(
            category = failure,
            httpStatus = status,
            googleStatus = null,
            googleReason = null,
            exceptionClass = error.javaClass.simpleName,
            durationMillis = elapsedMillis(started),
            request = metadata,
        ),
    )

    private fun elapsedMillis(started: Long): Long =
        ((System.nanoTime() - started).coerceAtLeast(0L) / 1_000_000L)

    private fun requireSuccess(response: SpeechHttpResponse, operation: String, metadata: SpeechRequestMetadata) {
        if (response.code in 200..299) return
        if (response.code in 300..399) {
            throw SpeechException(
                SpeechFailure.AUTHORIZATION, "$operation was redirected; the credential was not forwarded.",
                diagnostic = SpeechDiagnostic(
                    SpeechFailure.AUTHORIZATION, response.code, null, null, null, null, metadata,
                ),
            )
        }
        val apiError = dev.pierre.japaneseanki.parseGoogleApiError(response.body.toString(Charsets.UTF_8))
        val failure = when (classifyGoogleApiError(response.code, apiError)) {
            GoogleApiProblem.AUTHENTICATION -> SpeechFailure.AUTHORIZATION
            GoogleApiProblem.API_NOT_ENABLED -> SpeechFailure.API_NOT_ENABLED
            GoogleApiProblem.BILLING -> SpeechFailure.BILLING
            GoogleApiProblem.QUOTA -> SpeechFailure.QUOTA
            GoogleApiProblem.INVALID_REQUEST -> SpeechFailure.INVALID_REQUEST
            GoogleApiProblem.NONE -> when {
                response.code in 500..599 -> SpeechFailure.NETWORK
                response.code in 400..499 -> SpeechFailure.INVALID_REQUEST
                else -> SpeechFailure.INVALID_RESPONSE
            }
        }
        throw SpeechException(
            failure,
            messageFor(failure, operation),
            diagnostic = SpeechDiagnostic(
                category = failure,
                httpStatus = response.code,
                googleStatus = apiError.status.takeIf(String::isNotBlank),
                googleReason = apiError.reason.takeIf(String::isNotBlank),
                exceptionClass = null,
                durationMillis = null,
                request = metadata,
            ),
        )
    }

    private fun messageFor(failure: SpeechFailure, operation: String): String = when (failure) {
        SpeechFailure.NETWORK -> "$operation service is unavailable."
        SpeechFailure.TIMEOUT -> "$operation timed out."
        SpeechFailure.DNS -> "$operation service host could not be resolved."
        SpeechFailure.TLS -> "A secure connection to the $operation service failed."
        SpeechFailure.AUTHORIZATION -> "$operation authorization failed."
        SpeechFailure.API_NOT_ENABLED ->
            "The Cloud Text-to-Speech API is not enabled for this Google Cloud project."
        SpeechFailure.BILLING -> "Billing is not enabled for this Google Cloud project."
        SpeechFailure.QUOTA -> "$operation quota was reached."
        SpeechFailure.INVALID_REQUEST -> "$operation request was rejected by Google Cloud."
        SpeechFailure.INVALID_RESPONSE -> "$operation failed."
        SpeechFailure.INVALID_AUDIO -> "$operation failed."
        SpeechFailure.SETUP -> "$operation is not configured."
        SpeechFailure.SOURCE_MISSING -> "No source audio is available for this request."
        SpeechFailure.UNKNOWN -> "$operation failed."
        SpeechFailure.CANCELLED -> "$operation was cancelled."
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
    val contentBytes = synthesisContentBytes(request)
    if (contentBytes > MAX_TTS_CONTENT_BYTES) {
        throw SpeechException(
            SpeechFailure.INVALID_REQUEST,
            "Speech input exceeds the 5,000-byte Cloud Text-to-Speech content limit.",
            diagnostic = SpeechDiagnostic(
                category = SpeechFailure.INVALID_REQUEST,
                httpStatus = null,
                googleStatus = null,
                googleReason = null,
                exceptionClass = null,
                durationMillis = 0,
                request = synthesisRequestMetadata(request, contentBytes, requestBytes = null),
            ),
        )
    }
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
    val serialized = buildString {
        append("{\"input\":").append(input)
        append(",\"voice\":{\"languageCode\":").append(MiniJson.quote(request.languageCode))
        append(",\"name\":").append(MiniJson.quote(request.voice.name)).append('}')
        append(",\"audioConfig\":{\"audioEncoding\":\"MP3\",\"speakingRate\":")
        append(java.lang.Double.toString(request.speakingRate))
        append('}')
        append('}')
    }
    return serialized
}

private fun synthesisContentBytes(request: SentenceSpeechRequest): Int {
    val bytes = request.text.toByteArray(Charsets.UTF_8).size.toLong() + request.hints.sumOf { hint ->
        hint.phrase.toByteArray(Charsets.UTF_8).size.toLong() + hint.reading.toByteArray(Charsets.UTF_8).size.toLong()
    }
    return bytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

private fun synthesisRequestMetadata(
    request: SentenceSpeechRequest,
    inputBytes: Int,
    requestBytes: Int?,
): SpeechRequestMetadata = SpeechRequestMetadata(
    operation = SpeechOperation.SYNTHESIS,
    inputBytes = inputBytes,
    requestBytes = requestBytes,
    voiceName = request.voice.name.takeIf(::isSafeVoiceId),
    hintCount = request.hints.size,
)

private fun isSafeVoiceId(value: String): Boolean =
    value.length in 1..128 && value.all {
        it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it in "_.-"
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
private const val MAX_TTS_CONTENT_BYTES = 5_000
private const val MIN_MP3_BYTES = 256
private const val MAX_AUDIO_BYTES = 20L * 1024 * 1024
