package dev.pierre.japaneseanki.speech


/** A voice returned by the Cloud Text-to-Speech voice list, reduced to what the app displays. */
data class CloudVoice(
    val name: String,
    val displayName: String,
    val languageCodes: List<String>,
    val naturalSampleRateHz: Int?,
) {
    init {
        require(name.isNotBlank())
    }
}

/** Everything the workflow knows about one sentence synthesis; the provider owns all Google JSON. */
data class SentenceSpeechRequest(
    val text: String,
    val voice: CloudVoice,
    val speakingRate: Double = DEFAULT_SPEAKING_RATE,
    val languageCode: String = JAPANESE_LANGUAGE_CODE,
) {
    init {
        require(text.isNotBlank())
        require(speakingRate in 0.25..4.0)
    }

    /** Source identity for the reusable cache; a voice change produces a new entry. */
    val cacheIdentity: String
        get() = listOf(
            "google-chirp3",
            voice.name,
            languageCode,
            java.lang.Double.toString(speakingRate),
        ).joinToString("\u0000")

    companion object {
        const val JAPANESE_LANGUAGE_CODE = "ja-JP"
        const val DEFAULT_SPEAKING_RATE = 1.0
    }
}

enum class SpeechFailure {
    NETWORK,
    TIMEOUT,
    DNS,
    TLS,
    AUTHORIZATION,
    API_NOT_ENABLED,
    BILLING,
    QUOTA,
    INVALID_REQUEST,
    INVALID_RESPONSE,
    INVALID_AUDIO,
    SETUP,
    SOURCE_MISSING,
    UNKNOWN,
    CANCELLED,
}

/** Safe request metadata: content, credentials, response bodies, and media are never retained. */
data class SpeechRequestMetadata(
    val operation: SpeechOperation,
    val inputBytes: Int? = null,
    val requestBytes: Int? = null,
    val voiceName: String? = null,
    val hintCount: Int? = null,
)

enum class SpeechOperation { VOICE_LIST, SYNTHESIS }

data class SpeechDiagnostic(
    val category: SpeechFailure,
    val httpStatus: Int?,
    val googleStatus: String?,
    val googleReason: String?,
    val exceptionClass: String?,
    val durationMillis: Long?,
    val request: SpeechRequestMetadata,
)

val SpeechFailure.retryable: Boolean
    get() = when (this) {
        SpeechFailure.NETWORK, SpeechFailure.TIMEOUT, SpeechFailure.DNS, SpeechFailure.TLS,
        SpeechFailure.QUOTA, SpeechFailure.INVALID_RESPONSE, SpeechFailure.INVALID_AUDIO -> true
        else -> false
    }

/** Sanitized provider failure; diagnostic fields contain only bounded enums, codes, and counts. */
class SpeechException(
    val failure: SpeechFailure,
    message: String,
    cause: Throwable? = null,
    val diagnostic: SpeechDiagnostic? = null,
    val retryable: Boolean = failure.retryable,
) : Exception(message, cause) {
    internal fun withDiagnostic(value: SpeechDiagnostic): SpeechException = SpeechException(
        failure = failure,
        message = message ?: "Speech request failed.",
        diagnostic = value,
        retryable = retryable,
    )
}

/** Replaceable sentence-speech boundary. The workflow never builds Google JSON itself. */
interface SentenceSpeechProvider {
    /** Lists Japanese-capable Chirp 3 HD voices. Throws [SpeechException] on failure. */
    suspend fun listVoices(): List<CloudVoice>

    /** Synthesizes into [destination] (MP3). Returns the completed file. */
    suspend fun synthesize(request: SentenceSpeechRequest, destination: java.io.File): java.io.File
}

/** Serializes a fetched voice list for offline display. */
internal fun encodeVoices(voices: List<CloudVoice>): String = voices.joinToString(separator = "\n") { voice ->
    listOf(
        voice.name,
        voice.displayName,
        voice.languageCodes.joinToString(","),
        voice.naturalSampleRateHz?.toString().orEmpty(),
    ).joinToString("\t")
}

/** Decodes the cached list; a corrupt entry yields an empty list rather than a crash. */
internal fun decodeVoices(encoded: String?): List<CloudVoice> {
    if (encoded.isNullOrBlank()) return emptyList()
    return encoded.lineSequence().mapNotNull { line ->
        val parts = line.split('\t')
        if (parts.size < 4 || parts[0].isBlank()) return@mapNotNull null
        CloudVoice(
            name = parts[0],
            displayName = parts[1].ifBlank { parts[0].substringAfterLast('-') },
            languageCodes = parts[2].split(',').filter(String::isNotBlank),
            naturalSampleRateHz = parts[3].toIntOrNull(),
        )
    }.toList()
}
