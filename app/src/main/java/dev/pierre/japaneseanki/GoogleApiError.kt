package dev.pierre.japaneseanki

import dev.pierre.japaneseanki.network.GoogleJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString

/**
 * What a Google Cloud API error response says went wrong, independent of the operation.
 *
 * Speech and translation share this classification so one project problem is reported the same
 * way by both services, and each service still fails independently.
 */
internal enum class GoogleApiProblem {
    /** Nothing actionable identified; the caller falls back to its status-code mapping. */
    NONE,
    AUTHENTICATION,
    API_NOT_ENABLED,
    BILLING,
    QUOTA,
    INVALID_REQUEST,
}

internal data class GoogleApiErrorDetails(
    val status: String,
    val reason: String,
    val mentionsBilling: Boolean = false,
    val mentionsDisabledApi: Boolean = false,
)

@Serializable
internal data class GoogleApiErrorResponseDto(
    val error: GoogleApiErrorBodyDto? = null,
)

@Serializable
internal data class GoogleApiErrorBodyDto(
    val details: List<GoogleApiErrorDetailDto>? = null,
    val message: String? = null,
    val status: String? = null,
)

@Serializable
internal data class GoogleApiErrorDetailDto(
    val reason: String? = null,
)

internal fun parseGoogleApiError(body: String): GoogleApiErrorDetails {
    val error = runCatching { GoogleJson.decodeFromString<GoogleApiErrorResponseDto>(body).error }
        .getOrNull() ?: return GoogleApiErrorDetails("", "")
    val reason = error.details.orEmpty().asSequence()
        .mapNotNull(GoogleApiErrorDetailDto::reason)
        .mapNotNull(::safeGoogleToken)
        .firstOrNull()
        .orEmpty()
    val message = error.message.orEmpty().lowercase()
    return GoogleApiErrorDetails(
        status = safeGoogleToken(error.status).orEmpty(),
        reason = reason,
        // Provider prose is used only for classification; it is never retained or exposed.
        mentionsBilling = "billing" in message,
        mentionsDisabledApi = "disabled" in message || "not enabled" in message,
    )
}

internal fun classifyGoogleApiError(code: Int, body: String): GoogleApiProblem =
    classifyGoogleApiError(code, parseGoogleApiError(body))

internal fun classifyGoogleApiError(code: Int, error: GoogleApiErrorDetails): GoogleApiProblem {
    return when {
        error.reason in KEY_REASONS -> GoogleApiProblem.AUTHENTICATION
        error.reason == "SERVICE_DISABLED" -> GoogleApiProblem.API_NOT_ENABLED
        error.reason in BILLING_REASONS -> GoogleApiProblem.BILLING
        error.mentionsBilling -> GoogleApiProblem.BILLING
        error.reason in QUOTA_REASONS || code == 429 || error.status == "RESOURCE_EXHAUSTED" ->
            GoogleApiProblem.QUOTA
        code == 401 -> GoogleApiProblem.AUTHENTICATION
        code == 403 -> if (error.mentionsDisabledApi) {
            GoogleApiProblem.API_NOT_ENABLED
        } else {
            GoogleApiProblem.AUTHENTICATION
        }
        code == 400 || error.status == "INVALID_ARGUMENT" -> GoogleApiProblem.INVALID_REQUEST
        else -> GoogleApiProblem.NONE
    }
}

private fun safeGoogleToken(value: String?): String? = value
    ?.takeIf { it.length in 1..80 && it.all { char -> char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' || char in "_.-" } }
    ?.uppercase()

private val KEY_REASONS = setOf(
    "API_KEY_INVALID", "API_KEY_EXPIRED", "API_KEY_SERVICE_BLOCKED", "CONSUMER_INVALID",
)
private val BILLING_REASONS = setOf("BILLING_DISABLED", "BILLING_NOT_ENABLED")
private val QUOTA_REASONS = setOf("RATE_LIMIT_EXCEEDED", "QUOTA_EXCEEDED", "DAILY_LIMIT_EXCEEDED")
