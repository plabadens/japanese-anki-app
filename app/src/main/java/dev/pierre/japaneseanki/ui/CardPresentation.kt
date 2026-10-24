package dev.pierre.japaneseanki.ui

import androidx.compose.runtime.Immutable

enum class CardAction { None, RetryAnalysis, RetrySend, Recovery, Submit }

@Immutable
data class CardPresentationCopy(
    val noTargetHint: String,
    val analyzingHint: String,
    val sendingHint: String,
    val wordAudioPreparingHint: String,
    val completedStatusFallback: String,
    val duplicateStatusFallback: String,
    val recoveryStatusFallback: String,
    val sendActionLabel: String,
    val retryActionLabel: String,
    val retrySendActionLabel: String,
)

@Immutable
data class CardPresentationInput(
    val hasTarget: Boolean,
    val analyzerError: String?,
    val analyzing: Boolean,
    val completed: Boolean,
    val duplicateBlocked: Boolean,
    val recoveryRequired: Boolean,
    val submitting: Boolean,
    val sendBlocked: Boolean,
    val submitMessage: String?,
    val recoveryActionLabel: String?,
    val translationFailure: String?,
    val wordAudioGenerating: Boolean,
    val submitHint: String?,
    val noWordAction: Boolean,
    val canSend: Boolean,
    val submissionLocked: Boolean,
    val actionHasTarget: Boolean,
    val recoveryMessage: String?,
    val copy: CardPresentationCopy,
)

@Immutable
data class CardPresentation(
    val hint: String?,
    val hintPreparing: Boolean,
    val status: String?,
    val actionLabel: String,
    val actionEnabled: Boolean,
    val action: CardAction,
)

fun cardPresentation(input: CardPresentationInput): CardPresentation {
    val status = when {
        input.completed -> input.submitMessage ?: input.copy.completedStatusFallback
        input.duplicateBlocked -> input.submitMessage ?: input.copy.duplicateStatusFallback
        input.recoveryRequired -> input.recoveryMessage ?: input.copy.recoveryStatusFallback
        else -> null
    }

    val hint = when {
        input.analyzerError != null -> input.analyzerError
        input.analyzing -> input.copy.analyzingHint
        !input.hasTarget -> input.copy.noTargetHint
        input.completed || input.duplicateBlocked || input.recoveryRequired -> status
        input.submitting -> input.copy.sendingHint
        input.sendBlocked -> input.submitMessage
        input.submitMessage != null -> input.submitMessage
        input.translationFailure != null -> input.translationFailure
        input.wordAudioGenerating -> input.copy.wordAudioPreparingHint
        input.submitHint != null -> input.submitHint
        else -> null
    }

    val actionLabel = when {
        input.noWordAction -> input.copy.sendActionLabel
        input.analyzerError != null -> input.copy.retryActionLabel
        input.recoveryRequired || input.duplicateBlocked || input.completed || input.submitting ->
            input.copy.sendActionLabel
        input.submitMessage != null && !input.sendBlocked -> input.copy.retrySendActionLabel
        input.recoveryActionLabel != null -> input.recoveryActionLabel
        else -> input.copy.sendActionLabel
    }

    val actionEnabled = when {
        input.noWordAction -> false
        input.submitting || input.analyzing || input.completed || input.duplicateBlocked || input.recoveryRequired -> false
        input.analyzerError != null -> !input.submissionLocked
        input.submitMessage != null && !input.sendBlocked -> input.canSend
        input.recoveryActionLabel != null -> !input.submissionLocked
        else -> input.canSend && input.actionHasTarget
    }

    val action = when {
        input.noWordAction -> CardAction.None
        input.analyzerError != null -> CardAction.RetryAnalysis
        input.submitMessage != null && !input.sendBlocked -> CardAction.RetrySend
        input.recoveryActionLabel != null -> CardAction.Recovery
        else -> CardAction.Submit
    }

    return CardPresentation(
        hint = hint,
        hintPreparing = input.analyzing,
        status = status,
        actionLabel = actionLabel,
        actionEnabled = actionEnabled,
        action = action,
    )
}
