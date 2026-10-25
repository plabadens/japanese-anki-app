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
    val frozenRetryReady: Boolean = false,
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

    val liveAnalysis = !input.submissionLocked && !input.completed
    val analyzing = liveAnalysis && input.analyzing
    val analyzerError = input.analyzerError.takeIf { liveAnalysis }
    val held = input.completed || input.duplicateBlocked || input.recoveryRequired || input.submitting
    val hint = when {
        status != null -> status
        input.submitting -> input.copy.sendingHint
        analyzerError != null -> analyzerError
        analyzing -> input.copy.analyzingHint
        !input.hasTarget && !input.submissionLocked -> input.copy.noTargetHint
        input.submitHint != null -> input.submitHint
        input.frozenRetryReady -> input.recoveryMessage
        input.sendBlocked -> input.submitMessage
        input.submitMessage != null -> input.submitMessage
        input.translationFailure != null && !input.submissionLocked -> input.translationFailure
        input.wordAudioGenerating && !input.submissionLocked -> input.copy.wordAudioPreparingHint
        else -> null
    }
    val action = when {
        held -> CardAction.Submit
        input.noWordAction -> CardAction.None
        analyzerError != null -> CardAction.RetryAnalysis
        input.recoveryActionLabel != null -> CardAction.Recovery
        input.frozenRetryReady || (input.submitMessage != null && !input.sendBlocked) -> CardAction.RetrySend
        else -> CardAction.Submit
    }
    val actionLabel = when (action) {
        CardAction.RetryAnalysis -> input.copy.retryActionLabel
        CardAction.RetrySend -> input.copy.retrySendActionLabel
        CardAction.Recovery -> requireNotNull(input.recoveryActionLabel)
        CardAction.None, CardAction.Submit -> input.copy.sendActionLabel
    }
    val actionEnabled = !held && !analyzing && when (action) {
        CardAction.None -> false
        CardAction.RetryAnalysis -> !input.submissionLocked
        CardAction.Recovery -> !input.submissionLocked || input.frozenRetryReady
        CardAction.RetrySend -> input.canSend
        CardAction.Submit -> input.canSend && input.actionHasTarget
    }

    return CardPresentation(
        hint = hint,
        hintPreparing = analyzing,
        status = status,
        actionLabel = actionLabel,
        actionEnabled = actionEnabled,
        action = action,
    )
}
