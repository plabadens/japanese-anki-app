package dev.pierre.japaneseanki.speech

import dev.pierre.japaneseanki.FuriganaSpan
import dev.pierre.japaneseanki.JapaneseToken
import dev.pierre.japaneseanki.ReadingCorrection
import dev.pierre.japaneseanki.SentenceReadings
import dev.pierre.japaneseanki.TargetSpan
import dev.pierre.japaneseanki.containsKanji
import java.security.MessageDigest

/** A single resolved surface/reading pair used to build Japanese yomigana hints. */
data class ReadingHintSpan(val start: Int, val end: Int, val surface: String, val reading: String)

/** A hint exactly as Google Cloud expects it: phrase as written, pronunciation in kana. */
data class ReadingHint(val phrase: String, val reading: String)

sealed interface HintResult {
    data class Ready(val hints: List<ReadingHint>) : HintResult

    /** The sentence cannot be expressed with phrase-level hints; generation must not fall back. */
    data class Impossible(val reason: String) : HintResult
}

/**
 * The sentence's hint spans in sentence order: every kanji-containing furigana run (corrected
 * readings included) plus the selected target with its own reading. An unresolved kanji span is
 * passed through so [ReadingHintBuilder] can reject the request instead of dropping it.
 */
fun sentenceHintSpans(
    sentence: String,
    target: TargetSpan,
    targetReading: String,
    furigana: List<FuriganaSpan>,
): List<ReadingHintSpan> = buildList {
    furigana.forEach { span ->
        val surface = span.span.textIn(sentence) ?: return@forEach
        if (!containsKanji(surface)) return@forEach
        add(ReadingHintSpan(span.span.start, span.span.end, surface, span.reading))
    }
    val targetSurface = target.textIn(sentence) ?: return@buildList
    if (containsKanji(targetSurface)) {
        add(ReadingHintSpan(target.start, target.end, targetSurface, targetReading))
    }
}

/**
 * Reading hints for the committed sentence, independent of which word is selected. Every kanji
 * token is represented, including unresolved readings, so [ReadingHintBuilder] can block rather
 * than silently sending a request without required hints. Corrections replace overlapping token
 * spans; ordinary word selection does not change this list.
 */
internal fun sentenceHintSpans(
    sentence: String,
    tokens: List<JapaneseToken>,
    corrections: List<ReadingCorrection>,
): List<ReadingHintSpan> {
    val usableCorrections = corrections.mapNotNull { correction ->
        val surface = correction.span.textIn(sentence) ?: return@mapNotNull null
        if (surface != correction.surface || SentenceReadings.rejection(sentence, correction.span, correction.reading) != null) {
            return@mapNotNull null
        }
        ReadingHintSpan(correction.span.start, correction.span.end, surface, correction.reading)
    }
    val spans = buildList {
        tokens.forEach { token ->
            val surface = token.span.textIn(sentence) ?: return@forEach
            if (surface != token.surface || !containsKanji(surface)) return@forEach
            if (usableCorrections.any { it.start < token.span.end && it.end > token.span.start }) return@forEach
            add(ReadingHintSpan(token.span.start, token.span.end, surface, token.surfaceReading))
        }
        addAll(usableCorrections)
    }.sortedBy(ReadingHintSpan::start)

    val covered = BooleanArray(sentence.length)
    spans.forEach { span ->
        for (offset in span.start.coerceAtLeast(0) until span.end.coerceAtMost(sentence.length)) {
            covered[offset] = true
        }
    }
    val unresolved = mutableListOf<ReadingHintSpan>()
    var offset = 0
    while (offset < sentence.length) {
        if (covered[offset] || !containsKanji(sentence[offset].toString())) {
            offset++
            continue
        }
        val start = offset
        while (offset < sentence.length && !covered[offset] && containsKanji(sentence[offset].toString())) offset++
        unresolved += ReadingHintSpan(start, offset, sentence.substring(start, offset), "")
    }
    return (spans + unresolved).sortedBy(ReadingHintSpan::start)
}

/**
 * Pure, independently tested builder for `input.customPronunciations`.
 *
 * Google's pronunciation overrides are keyed by phrase, not by offset. A sentence that uses the
 * same surface with two different readings therefore cannot be represented; that case is reported
 * as [HintResult.Impossible] instead of dropping one hint and guessing.
 */
object ReadingHintBuilder {
    /** Bump when the payload shape or normalization changes; it participates in the cache key. */
    const val VERSION = 1

    fun build(sentence: String, spans: List<ReadingHintSpan>): HintResult {
        if (sentence.isBlank()) return HintResult.Impossible("The sentence is empty.")
        val usable = spans.sortedBy { it.start }
        var previousEnd = -1
        val hints = LinkedHashMap<String, String>()
        for (span in usable) {
            if (span.start < 0 || span.end > sentence.length || span.start >= span.end) {
                return HintResult.Impossible("A reading hint does not match the sentence.")
            }
            if (span.start < previousEnd) return HintResult.Impossible("Reading hints overlap.")
            previousEnd = span.end
            if (sentence.substring(span.start, span.end) != span.surface) {
                return HintResult.Impossible("A reading hint does not match the sentence text.")
            }
            val reading = normalizeReading(span.reading)
            if (reading.isEmpty()) return HintResult.Impossible("A kanji span has no resolved reading.")
            if (!containsKanji(span.surface)) continue
            if (!reading.all(::isKana)) {
                return HintResult.Impossible("A resolved reading is not kana-only.")
            }
            val existing = hints[span.surface]
            if (existing == null) {
                hints[span.surface] = reading
            } else if (existing != reading) {
                return HintResult.Impossible(
                    "The sentence uses ${span.surface} with two different readings; hint overrides are phrase-wide.",
                )
            }
        }
        return HintResult.Ready(hints.map { ReadingHint(it.key, it.value) })
    }

    /** Stable cache fragment: order-independent over hints, sensitive to every phrase and reading. */
    fun cacheFragment(hints: List<ReadingHint>): String {
        if (hints.isEmpty()) return "hints=v$VERSION:none"
        val canonical = hints.sortedBy { it.phrase }.joinToString("\u0001") { "${it.phrase}\u0002${it.reading}" }
        return "hints=v$VERSION:${sha256(canonical)}"
    }

    fun normalizeReading(reading: String): String = reading.trim()
        .replace(' ', ' ')
        .replace('\u3000', ' ')
        .filterNot { it == ' ' }

    private fun isKana(char: Char): Boolean = when (Character.UnicodeScript.of(char.code)) {
        Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA -> true
        else -> char == 'ー' || char == 'ｰ'
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

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
    val hints: List<ReadingHint>,
    val hintVersion: Int = ReadingHintBuilder.VERSION,
    val speakingRate: Double = DEFAULT_SPEAKING_RATE,
    val languageCode: String = JAPANESE_LANGUAGE_CODE,
) {
    init {
        require(text.isNotBlank())
        require(speakingRate in 0.25..4.0)
    }

    /** Source identity for the reusable cache; a hint or voice change produces a new entry. */
    val cacheIdentity: String
        get() = listOf(
            "google-chirp3",
            voice.name,
            languageCode,
            java.lang.Double.toString(speakingRate),
            ReadingHintBuilder.cacheFragment(hints),
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
    fun listVoices(): List<CloudVoice>

    /** Synthesizes into [destination] (MP3). Returns the completed file. */
    fun synthesize(request: SentenceSpeechRequest, destination: java.io.File): java.io.File
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
