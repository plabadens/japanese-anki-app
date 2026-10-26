package dev.pierre.japaneseanki.card

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.pierre.japaneseanki.DictionaryMeaningChoice
import dev.pierre.japaneseanki.TargetSpan
import dev.pierre.japaneseanki.ui.CardAction
import dev.pierre.japaneseanki.ui.CardPresentation
import dev.pierre.japaneseanki.ui.CardPresentationCopy
import dev.pierre.japaneseanki.ui.CardPresentationInput
import dev.pierre.japaneseanki.ui.cardPresentation

/** Inputs owned by the surrounding activity/coordinators, not by the card's decision logic. */
internal data class CardScreenPresentationContext(
    val analyzerError: String?,
    val analyzing: Boolean,
    val completed: Boolean,
    val duplicateBlocked: Boolean,
    val recoveryRequired: Boolean,
    val submitting: Boolean,
    val sendBlocked: Boolean,
    val submitMessage: String?,
    val submitHint: String?,
    val submitHintActionLabel: String?,
    val translationFailure: String?,
    val wordAudioGenerating: Boolean,
    val hasTarget: Boolean,
    val actionHasTarget: Boolean,
    val canSend: Boolean,
    val submissionLocked: Boolean,
    val editingEnabled: Boolean,
    val frozenRetryReady: Boolean,
    val recoveryMessage: String?,
    val copy: CardPresentationCopy,
)

/** The immutable snapshot read by the card UI. */
internal data class CardScreenState(
    val sentence: String,
    val selectedSpan: TargetSpan?,
    val targetWord: String?,
    val meaningChoice: DictionaryMeaningChoice?,
    val sentenceDiffersFromCommitted: Boolean,
    val presentation: CardPresentation,
)

/**
 * Card-specific state and decisions. This is deliberately a plain class: it needs no Activity,
 * ViewModel, lifecycle owner, or device to exercise its transitions in JVM tests.
 */
internal class CardScreenStateHolder(
    sentence: String,
    selectedSpan: TargetSpan?,
    meaningChoice: DictionaryMeaningChoice?,
    committedSentence: String?,
    presentationContext: CardScreenPresentationContext,
    private val canSelectWords: () -> Boolean,
    private val isSubmissionLocked: () -> Boolean,
    private val onWordTapped: (TargetSpan) -> Unit,
    private val onMeaningChosen: (DictionaryMeaningChoice) -> Unit,
    private val dispatchPrimaryAction: (CardAction) -> Unit,
) {
    var state by mutableStateOf(
        buildState(sentence, selectedSpan, meaningChoice, committedSentence, presentationContext),
    )
        private set

    private var committedSentence: String? = committedSentence
    private var context: CardScreenPresentationContext = presentationContext

    /** Syncs values whose source of truth remains the draft/send coordinators. */
    fun update(
        sentence: String,
        selectedSpan: TargetSpan?,
        meaningChoice: DictionaryMeaningChoice?,
        committedSentence: String?,
        presentationContext: CardScreenPresentationContext,
    ) {
        this.committedSentence = committedSentence
        this.context = presentationContext
        state = buildState(sentence, selectedSpan, meaningChoice, committedSentence, presentationContext)
    }

    fun onWordTapped(span: TargetSpan) {
        if (!canSelectWords() || isSubmissionLocked() || span == state.selectedSpan) return
        onWordTapped.invoke(span)
        state = buildState(
            sentence = state.sentence,
            selectedSpan = span,
            meaningChoice = null,
            committedSentence = committedSentence,
            presentationContext = context,
        )
    }

    fun onMeaningChosen(choice: DictionaryMeaningChoice) {
        if (isSubmissionLocked()) return
        onMeaningChosen.invoke(choice)
        state = buildState(
            sentence = state.sentence,
            selectedSpan = state.selectedSpan,
            meaningChoice = choice,
            committedSentence = committedSentence,
            presentationContext = context,
        )
    }

    fun onPrimaryAction() {
        val snapshot = state
        if (!snapshot.presentation.actionEnabled) return
        if (isSubmissionLocked() && !context.frozenRetryReady) return
        // The coordinator's click handler is constructed by the caller here, never during render.
        dispatchPrimaryAction(snapshot.presentation.action)
    }

    fun onSentenceChanged(text: String) {
        if (text == state.sentence) return
        state = buildState(
            sentence = text,
            selectedSpan = state.selectedSpan,
            meaningChoice = state.meaningChoice,
            committedSentence = committedSentence,
            presentationContext = context,
        )
    }

    private fun buildState(
        sentence: String,
        selectedSpan: TargetSpan?,
        meaningChoice: DictionaryMeaningChoice?,
        committedSentence: String?,
        presentationContext: CardScreenPresentationContext,
    ): CardScreenState {
        val targetWord = selectedSpan?.textIn(sentence)
        val input = CardPresentationInput(
            hasTarget = presentationContext.hasTarget,
            analyzerError = presentationContext.analyzerError,
            analyzing = presentationContext.analyzing,
            completed = presentationContext.completed,
            duplicateBlocked = presentationContext.duplicateBlocked,
            recoveryRequired = presentationContext.recoveryRequired,
            submitting = presentationContext.submitting,
            sendBlocked = presentationContext.sendBlocked,
            submitMessage = presentationContext.submitMessage,
            recoveryActionLabel = presentationContext.submitHintActionLabel,
            translationFailure = presentationContext.translationFailure,
            wordAudioGenerating = presentationContext.wordAudioGenerating,
            submitHint = presentationContext.submitHint,
            noWordAction = targetWord == null && presentationContext.analyzerError == null &&
                !presentationContext.analyzing &&
                presentationContext.editingEnabled &&
                presentationContext.submitMessage == null,
            canSend = presentationContext.canSend,
            submissionLocked = presentationContext.submissionLocked,
            actionHasTarget = presentationContext.actionHasTarget,
            frozenRetryReady = presentationContext.frozenRetryReady,
            recoveryMessage = presentationContext.recoveryMessage,
            copy = presentationContext.copy,
        )
        return CardScreenState(
            sentence = sentence,
            selectedSpan = selectedSpan,
            targetWord = targetWord,
            meaningChoice = meaningChoice,
            sentenceDiffersFromCommitted = sentence != committedSentence,
            presentation = cardPresentation(input),
        )
    }
}
