package dev.pierre.japaneseanki

import dev.pierre.japaneseanki.network.GoogleJson
import dev.pierre.japaneseanki.network.request
import java.io.IOException
import java.util.concurrent.CancellationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

enum class TranslationSource { GOOGLE, MANUAL }

sealed interface TranslationResult {
    data class Ready(val text: String, val source: TranslationSource) : TranslationResult
    data object Pending : TranslationResult
    data class Failed(val message: String) : TranslationResult
}

/** A manual edit is preserved as stale when its source sentence changes. */
data class TranslationDraft(
    val sentence: String,
    val result: TranslationResult,
    val manuallyEdited: Boolean = false,
    val stale: Boolean = false,
    val suggestion: TranslationResult? = null,
) {
    fun sentenceChanged(newSentence: String): TranslationDraft {
        if (newSentence == sentence) return this
        return if (manuallyEdited && result is TranslationResult.Ready) {
            copy(sentence = newSentence, stale = true, suggestion = TranslationResult.Pending)
        } else {
            TranslationDraft(newSentence, TranslationResult.Pending)
        }
    }

    fun requestStarted(): TranslationDraft = if (manuallyEdited && stale) {
        copy(suggestion = TranslationResult.Pending)
    } else {
        copy(result = TranslationResult.Pending, suggestion = null)
    }

    fun requestFinished(outcome: TranslationResult): TranslationDraft = if (manuallyEdited && stale) {
        copy(suggestion = outcome)
    } else {
        copy(result = outcome, manuallyEdited = false, stale = false, suggestion = null)
    }

    fun keepMyText(): TranslationDraft {
        val current = result as? TranslationResult.Ready ?: return this
        return copy(
            result = TranslationResult.Ready(current.text, TranslationSource.MANUAL),
            manuallyEdited = true,
            stale = false,
            suggestion = null,
        )
    }

    fun useSuggestion(): TranslationDraft {
        val readySuggestion = suggestion as? TranslationResult.Ready ?: return this
        return copy(result = readySuggestion, manuallyEdited = false, stale = false, suggestion = null)
    }

    fun saveManualEdit(text: String): TranslationDraft = copy(
        result = TranslationResult.Ready(text, TranslationSource.MANUAL),
        manuallyEdited = true,
        stale = false,
        suggestion = null,
    )
}

data class TranslatorHttpResponse(val code: Int, val body: String)

/**
 * Injectable Cloud Translation HTTP boundary. Tests assert the exact serialized request, that the
 * credential travels only as a header argument, and that no redirect is followed.
 */
interface TranslatorTransport {
    suspend fun post(url: String, apiKey: String, json: ByteArray, timeoutMillis: Int): TranslatorHttpResponse
}

/** Bounded, non-redirecting HTTPS transport; the key is sent only as `X-Goog-Api-Key`. */
class HttpTranslatorTransport : TranslatorTransport {
    override suspend fun post(url: String, apiKey: String, json: ByteArray, timeoutMillis: Int): TranslatorHttpResponse {
        val response = request(
            url = url,
            apiKey = apiKey,
            timeoutMillis = timeoutMillis,
            maxResponseBytes = MAX_RESPONSE_BYTES,
            body = json,
        )
        return TranslatorHttpResponse(response.code, response.body.toString(Charsets.UTF_8))
    }

    companion object {
        const val MAX_RESPONSE_BYTES = 256 * 1024
    }
}

/**
 * The sole translation path: Google Cloud Translation Basic v2 with its default NMT model.
 *
 * Only the sentence is sent. The credential is supplied per call from the app's shared Google
 * Cloud store, placed in the `X-Goog-Api-Key` header, never in the URL, and never forwarded on a
 * redirect. Failures expose a fixed failure class and a sanitized message; response bodies, the
 * credential, and the sentence never appear in errors.
 */
class GoogleTranslationClient(
    private val transport: TranslatorTransport = HttpTranslatorTransport(),
    private val timeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
) {
    init {
        require(timeoutMillis in 1..MAX_TIMEOUT_MILLIS)
    }

    suspend fun translate(apiKey: String, sentence: String): String {
        require(apiKey.isNotBlank()) { "Translation credentials are missing" }
        require(sentence.isNotBlank() && sentence.length <= MAX_SENTENCE_CHARS) {
            "Sentence is invalid for translation"
        }
        val payload = buildRequestBody(sentence).toByteArray(Charsets.UTF_8)
        val response = try {
            transport.post(ENDPOINT, apiKey, payload, timeoutMillis)
        } catch (_: IOException) {
            throw TranslatorException(TranslatorFailure.NETWORK, "Translation service could not be reached.")
        }
        requireSuccess(response)
        return parseTranslation(response.body)
    }

    private fun requireSuccess(response: TranslatorHttpResponse) {
        if (response.code in 200..299) return
        if (response.code in 300..399) {
            throw TranslatorException(
                TranslatorFailure.NETWORK,
                "Translation redirect was refused; the credential was not forwarded.",
            )
        }
        val failure = when (classifyGoogleApiError(response.code, response.body)) {
            GoogleApiProblem.AUTHENTICATION -> TranslatorFailure.AUTHORIZATION
            GoogleApiProblem.API_NOT_ENABLED -> TranslatorFailure.API_NOT_ENABLED
            GoogleApiProblem.BILLING -> TranslatorFailure.BILLING
            GoogleApiProblem.QUOTA -> TranslatorFailure.QUOTA
            GoogleApiProblem.INVALID_REQUEST -> TranslatorFailure.INVALID_REQUEST
            GoogleApiProblem.NONE ->
                if (response.code in 500..599) TranslatorFailure.NETWORK else TranslatorFailure.INVALID_RESPONSE
        }
        throw TranslatorException(failure, messageFor(failure))
    }

    private fun messageFor(failure: TranslatorFailure): String = when (failure) {
        TranslatorFailure.AUTHORIZATION -> "Google Cloud rejected the API key for translation."
        TranslatorFailure.API_NOT_ENABLED ->
            "The Cloud Translation API is not enabled for this Google Cloud project."
        TranslatorFailure.BILLING -> "Billing is not enabled for this Google Cloud project."
        TranslatorFailure.QUOTA -> "Google Cloud translation quota was reached."
        TranslatorFailure.NETWORK -> "Translation service could not be reached."
        TranslatorFailure.INVALID_REQUEST -> "Google Cloud rejected the translation request."
        TranslatorFailure.INVALID_RESPONSE -> "Translation response was invalid."
    }

    private fun parseTranslation(body: String): String {
        val translations = runCatching {
            GoogleJson.decodeFromString<TranslationResponseDto>(body).data.translations
        }.getOrNull() ?: throw invalidResponse()
        if (translations.size != 1) {
            throw TranslatorException(
                TranslatorFailure.INVALID_RESPONSE, "Translation did not return exactly one result.",
            )
        }
        val text = translations.single().translatedText.let(::unescapeTranslationEntities).trim()
        if (text.isEmpty()) {
            throw TranslatorException(
                TranslatorFailure.INVALID_RESPONSE, "Translation returned no English text.",
            )
        }
        return text
    }

    private fun invalidResponse() = TranslatorException(
        TranslatorFailure.INVALID_RESPONSE, "Translation response was invalid.",
    )

    companion object {
        const val ENDPOINT = "https://translation.googleapis.com/language/translate/v2"
        const val SOURCE_LANGUAGE = "ja"
        const val TARGET_LANGUAGE = "en"
        const val FORMAT = "text"

        /** The one model this app requests; there is no provider or model picker. */
        const val MODEL = "nmt"
        const val DEFAULT_TIMEOUT_MILLIS = 20_000
        const val MAX_TIMEOUT_MILLIS = 60_000
        private const val MAX_SENTENCE_CHARS = 20_000

        /** Exact outbound body: one plain-text sentence, explicit languages and the NMT model. */
        fun buildRequestBody(sentence: String): String = GoogleJson.encodeToString(
            TranslationRequestDto(
                q = sentence,
                source = SOURCE_LANGUAGE,
                target = TARGET_LANGUAGE,
                format = FORMAT,
                model = MODEL,
            ),
        )
    }
}

/** Google escapes `&`, `<`, `>` and quotes in `translatedText`; the app stores plain text. */
internal fun unescapeTranslationEntities(text: String): String {
    return HtmlEntityDecoder.decode(text, NumericEntityPolicy.TRANSLATION)
}

enum class TranslatorFailure {
    NETWORK,
    AUTHORIZATION,

    /** The Cloud Translation API is not enabled for the project. */
    API_NOT_ENABLED,

    /** The project has no active billing account. */
    BILLING,
    QUOTA,
    INVALID_REQUEST,
    INVALID_RESPONSE,
}

class TranslatorException(val failure: TranslatorFailure, message: String) : Exception(message)

/**
 * An in-flight translation may only be applied to the revision it was started for: the sentence
 * text and the request revision must still match, and submission must not have started.
 */
internal fun translationResponseIsCurrent(
    requestSentence: String,
    requestRevision: Int,
    currentSentence: String,
    currentRevision: Int,
    submissionLocked: Boolean,
): Boolean = !submissionLocked && requestSentence == currentSentence && requestRevision == currentRevision

/**
 * The workflow's translation entry point. The credential comes from the same protected Google
 * Cloud store the speech adapter uses; requests are explicit and are never restarted after
 * process death.
 */
class TranslationService(
    private val translator: GoogleTranslationClient = GoogleTranslationClient(),
    private val credential: () -> String? = { null },
) {
    suspend fun translate(sentence: String): TranslationResult {
        if (sentence.isBlank()) return TranslationResult.Failed("Sentence is empty.")
        val key = credential()?.trim().orEmpty()
        if (key.isEmpty()) return TranslationResult.Failed("Add your Google Cloud API key in Settings.")
        return try {
            TranslationResult.Ready(translator.translate(key, sentence), TranslationSource.GOOGLE)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: TranslatorException) {
            TranslationResult.Failed(error.message ?: "Translation failed.")
        } catch (_: Exception) {
            TranslationResult.Failed("Translation failed.")
        }
    }
}
