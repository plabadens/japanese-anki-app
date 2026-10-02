package dev.pierre.japaneseanki

import android.content.Context
import android.content.SharedPreferences
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import java.net.HttpURLConnection
import java.net.URL

enum class TranslationSource { AZURE, LOCAL, MANUAL }

sealed interface TranslationResult {
    data class Ready(val text: String, val source: TranslationSource) : TranslationResult
    data object Pending : TranslationResult
    data class Failed(val message: String) : TranslationResult
}

fun interface LocalTranslationFallback { fun translate(sentence: String): String? }

enum class TranslationModelStatus { NOT_DOWNLOADED, DOWNLOADING, READY, ERROR }

/** ML Kit downloads its ja→en model on demand and translates locally once that download completes. */
class MlKitLocalTranslationFallback(@Suppress("UNUSED_PARAMETER") context: Context) : LocalTranslationFallback {
    private val translator: Translator = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.JAPANESE)
            .setTargetLanguage(TranslateLanguage.ENGLISH)
            .build(),
    )

    @Volatile var modelStatus: TranslationModelStatus = TranslationModelStatus.NOT_DOWNLOADED
        private set

    override fun translate(sentence: String): String? = synchronized(lock) {
        modelStatus = TranslationModelStatus.DOWNLOADING
        try {
            Tasks.await(translator.downloadModelIfNeeded(DownloadConditions.Builder().build()))
            modelStatus = TranslationModelStatus.READY
            Tasks.await(translator.translate(sentence))
        } catch (_: Exception) {
            modelStatus = TranslationModelStatus.ERROR
            null
        }
    }

    /** Retry the initial model download; subsequent calls translate offline from the downloaded model. */
    fun retryModelDownload(): Boolean = synchronized(lock) {
        modelStatus = TranslationModelStatus.DOWNLOADING
        try {
            Tasks.await(translator.downloadModelIfNeeded(DownloadConditions.Builder().build()))
            modelStatus = TranslationModelStatus.READY
            true
        } catch (_: Exception) {
            modelStatus = TranslationModelStatus.ERROR
            false
        }
    }

    fun close() = translator.close()

    private companion object { val lock = Any() }
}

/** Explicit unavailable implementation for callers/tests that have not wired an Android context. */
class UnavailableLocalTranslationFallback : LocalTranslationFallback {
    override fun translate(sentence: String): String? = null
}

/** A manual edit is preserved as stale when its source sentence changes. */
data class TranslationDraft(
    val sentence: String,
    val result: TranslationResult,
    val manuallyEdited: Boolean = false,
    val stale: Boolean = false,
) {
    fun sentenceChanged(newSentence: String): TranslationDraft {
        if (newSentence == sentence) return this
        return if (manuallyEdited && result is TranslationResult.Ready) copy(sentence = newSentence, stale = true)
        else TranslationDraft(newSentence, TranslationResult.Pending)
    }

    /** Saving an edit clears the local-fallback explanatory mark regardless of prior source. */
    fun saveManualEdit(text: String): TranslationDraft = copy(
        result = if (text.isBlank()) TranslationResult.Failed("Translation is empty")
        else TranslationResult.Ready(text, TranslationSource.MANUAL),
        manuallyEdited = true,
        stale = false,
    )
}

/** Azure Translator REST adapter. Errors contain no response bodies or credential/content data. */
class AzureTranslator(
    private val connectionFactory: AzureSpeechSynthesizer.ConnectionFactory = AzureSpeechSynthesizer.ConnectionFactory { url -> url.openConnection() as HttpURLConnection },
    private val timeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
) {
    init { require(timeoutMillis in 1..MAX_TIMEOUT_MILLIS) }

    fun translate(subscriptionKey: String, region: String, sentence: String): String {
        require(subscriptionKey.isNotBlank()) { "Translator credentials are missing" }
        require(REGION.matches(region)) { "Translator region is invalid" }
        require(sentence.isNotBlank() && sentence.length <= MAX_SENTENCE_CHARS) { "Sentence is invalid for translation" }
        val endpoint = URL("https://api.cognitive.microsofttranslator.com/translate?api-version=3.0&from=ja&to=en")
        val connection = try { connectionFactory.open(endpoint) } catch (_: Exception) {
            throw TranslatorException(TranslatorFailure.NETWORK, "Translator service could not be reached")
        }
        try {
            val payload = "[{\"Text\":\"${escapeJson(sentence)}\"}]".toByteArray(Charsets.UTF_8)
            try {
                connection.requestMethod = "POST"
                connection.instanceFollowRedirects = false
                connection.doOutput = true
                connection.useCaches = false
                connection.connectTimeout = timeoutMillis
                connection.readTimeout = timeoutMillis
                connection.setRequestProperty("Ocp-Apim-Subscription-Key", subscriptionKey)
                connection.setRequestProperty("Ocp-Apim-Subscription-Region", region)
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connection.setFixedLengthStreamingMode(payload.size)
                connection.outputStream.use { it.write(payload) }
            } catch (_: Exception) {
                throw TranslatorException(TranslatorFailure.NETWORK, "Translator request could not be sent")
            }
            val status = try { connection.responseCode } catch (_: Exception) {
                throw TranslatorException(TranslatorFailure.NETWORK, "Translator service could not be reached")
            }
            when (status) {
                200 -> Unit
                401, 403 -> throw TranslatorException(TranslatorFailure.AUTHORIZATION, "Translator authorization failed")
                429 -> throw TranslatorException(TranslatorFailure.QUOTA, "Translator quota was reached")
                else -> throw TranslatorException(TranslatorFailure.SERVICE, "Translator service could not complete the request")
            }
            val response = try { connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() } }
            catch (_: Exception) { throw TranslatorException(TranslatorFailure.NETWORK, "Translator response could not be read") }
            return extractTranslatedText(response)
        } finally { connection.disconnect() }
    }

    /** Minimal request for Settings: Translator authorization is checked independently of Speech. */
    fun validateAuthorization(subscriptionKey: String, region: String) { translate(subscriptionKey, region, AUTHORIZATION_PROBE) }

    private fun extractTranslatedText(json: String): String {
        val key = "\"text\""
        val found = json.indexOf(key)
        val colon = if (found >= 0) json.indexOf(':', found + key.length) else -1
        if (colon < 0) invalidResponse()
        var i = colon + 1
        while (i < json.length && json[i].isWhitespace()) i++
        if (i >= json.length || json[i++] != '"') invalidResponse()
        val out = StringBuilder()
        while (i < json.length) {
            val c = json[i++]
            if (c == '"') return out.toString().takeIf(String::isNotBlank) ?: invalidResponse()
            if (c != '\\') out.append(c) else {
                if (i >= json.length) break
                when (val escaped = json[i++]) {
                    '"', '\\', '/' -> out.append(escaped)
                    'b' -> out.append('\b')
                    'f' -> out.append('\u000c')
                    'n' -> out.append('\n')
                    'r' -> out.append('\r')
                    't' -> out.append('\t')
                    'u' -> {
                        if (i + 4 > json.length) break
                        val code = json.substring(i, i + 4).toIntOrNull(16) ?: break
                        out.append(code.toChar()); i += 4
                    }
                    else -> break
                }
            }
        }
        invalidResponse()
    }

    private fun invalidResponse(): Nothing = throw TranslatorException(TranslatorFailure.INVALID_RESPONSE, "Translator returned an invalid response")

    private fun escapeJson(value: String) = buildString {
        value.forEach { c -> when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (c.code < 0x20) append("\\u%04x".format(c.code)) else append(c)
        } }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 15_000
        const val MAX_TIMEOUT_MILLIS = 60_000
        private const val MAX_SENTENCE_CHARS = 20_000
        private const val AUTHORIZATION_PROBE = "日本語"
        private val REGION = Regex("[A-Za-z0-9-]{1,63}")
    }
}

enum class TranslatorFailure { NETWORK, AUTHORIZATION, QUOTA, SERVICE, INVALID_RESPONSE }
class TranslatorException(val failure: TranslatorFailure, message: String) : Exception(message)

/** Requests are explicit and in-memory; they are never persisted/restarted after process death. */
class TranslationService(
    private val translator: AzureTranslator = AzureTranslator(),
    private val local: LocalTranslationFallback = UnavailableLocalTranslationFallback(),
    private val credentials: Pair<String, String>? = null,
) {
    fun translate(sentence: String): TranslationResult {
        if (sentence.isBlank()) return TranslationResult.Failed("Sentence is empty")
        val (key, region) = credentials ?: ("" to "")
        if (key.isNotBlank() && region.isNotBlank()) {
            try { return TranslationResult.Ready(translator.translate(key, region, sentence), TranslationSource.AZURE) }
            catch (_: Exception) { /* Degrade without disclosing credentials or sentence. */ }
        }
        return try {
            local.translate(sentence)?.takeIf(String::isNotBlank)?.let { TranslationResult.Ready(it, TranslationSource.LOCAL) }
                ?: TranslationResult.Failed("Local translation is unavailable")
        } catch (_: Exception) { TranslationResult.Failed("Local translation is unavailable") }
    }

    companion object {
        /** Uses the existing encrypted settings value and does not alter its storage semantics. */
        fun fromSettings(
            settings: SharedPreferences,
            translator: AzureTranslator = AzureTranslator(),
            local: LocalTranslationFallback = UnavailableLocalTranslationFallback(),
        ): TranslationService {
            val key = try { EncryptedSecretStore.decrypt(settings.getString("azure_key_encrypted", null)) }
                catch (_: Exception) { "" }
            val region = settings.getString("azure_region", "").orEmpty()
            return TranslationService(translator, local, key to region)
        }
    }
}
