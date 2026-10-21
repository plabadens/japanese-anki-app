package dev.pierre.japaneseanki

import dev.pierre.japaneseanki.speech.MiniJson

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

internal data class GoogleApiErrorDetails(val status: String, val reason: String, val message: String)

internal fun parseGoogleApiError(body: String): GoogleApiErrorDetails {
    val root = runCatching { MiniJson.parse(body) as? Map<*, *> }.getOrNull()
    val error = root?.get("error") as? Map<*, *> ?: return GoogleApiErrorDetails("", "", "")
    val details = error["details"] as? List<*> ?: emptyList<Any?>()
    val reason = details.asSequence()
        .mapNotNull { it as? Map<*, *> }
        .mapNotNull { it["reason"] as? String }
        .firstOrNull()
        .orEmpty()
    return GoogleApiErrorDetails(
        status = (error["status"] as? String).orEmpty().uppercase(),
        reason = reason.uppercase(),
        message = (error["message"] as? String).orEmpty(),
    )
}

internal fun classifyGoogleApiError(code: Int, body: String): GoogleApiProblem =
    classifyGoogleApiError(code, parseGoogleApiError(body))

internal fun classifyGoogleApiError(code: Int, error: GoogleApiErrorDetails): GoogleApiProblem {
    val message = error.message.lowercase()
    return when {
        error.reason in KEY_REASONS -> GoogleApiProblem.AUTHENTICATION
        error.reason == "SERVICE_DISABLED" -> GoogleApiProblem.API_NOT_ENABLED
        error.reason in BILLING_REASONS -> GoogleApiProblem.BILLING
        "billing" in message -> GoogleApiProblem.BILLING
        error.reason in QUOTA_REASONS || code == 429 || error.status == "RESOURCE_EXHAUSTED" ->
            GoogleApiProblem.QUOTA
        code == 401 -> GoogleApiProblem.AUTHENTICATION
        code == 403 -> if ("disabled" in message || "not enabled" in message) {
            GoogleApiProblem.API_NOT_ENABLED
        } else {
            GoogleApiProblem.AUTHENTICATION
        }
        code == 400 || error.status == "INVALID_ARGUMENT" -> GoogleApiProblem.INVALID_REQUEST
        else -> GoogleApiProblem.NONE
    }
}

private val KEY_REASONS = setOf(
    "API_KEY_INVALID", "API_KEY_EXPIRED", "API_KEY_SERVICE_BLOCKED", "CONSUMER_INVALID",
)
private val BILLING_REASONS = setOf("BILLING_DISABLED", "BILLING_NOT_ENABLED")
private val QUOTA_REASONS = setOf("RATE_LIMIT_EXCEEDED", "QUOTA_EXCEEDED", "DAILY_LIMIT_EXCEEDED")
