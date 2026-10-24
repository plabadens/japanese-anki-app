package dev.pierre.japaneseanki.card

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.pierre.japaneseanki.R
import dev.pierre.japaneseanki.DictionaryEntry
import dev.pierre.japaneseanki.DictionaryMeaningChoice
import dev.pierre.japaneseanki.DictionaryMeaningChoices
import dev.pierre.japaneseanki.FuriganaSpan
import dev.pierre.japaneseanki.JapaneseToken
import dev.pierre.japaneseanki.PitchAccent
import dev.pierre.japaneseanki.TargetSpan
import dev.pierre.japaneseanki.ui.CardAction
import dev.pierre.japaneseanki.VocabularyPartOfSpeech
import dev.pierre.japaneseanki.ui.CardPresentationCopy
import dev.pierre.japaneseanki.ui.CardScreen
import dev.pierre.japaneseanki.ui.FlowStatusText
import dev.pierre.japaneseanki.ui.ReviewAudioPresentation
import dev.pierre.japaneseanki.ui.englishPartOfSpeechLabel

@Composable
internal fun cardScreenPresentationContext(
    analyzerError: String?,
    analyzing: Boolean,
    completed: Boolean,
    duplicateBlocked: Boolean,
    recoveryRequired: Boolean,
    submitting: Boolean,
    sendBlocked: Boolean,
    submitMessage: String?,
    submitHint: String?,
    submitHintActionLabel: String,
    editPreparationLabel: String,
    translationFailure: String?,
    wordAudioGenerating: Boolean,
    hasTarget: Boolean,
    actionHasTarget: Boolean,
    canSend: Boolean,
    submissionLocked: Boolean,
    editingEnabled: Boolean,
    frozenRetryReady: Boolean,
    recoveryMessage: String?,
) = CardScreenPresentationContext(
    analyzerError = analyzerError,
    analyzing = analyzing,
    completed = completed,
    duplicateBlocked = duplicateBlocked,
    recoveryRequired = recoveryRequired,
    submitting = submitting,
    sendBlocked = sendBlocked,
    submitMessage = submitMessage,
    submitHint = submitHint,
    submitHintActionLabel = submitHintActionLabel,
    editPreparationLabel = editPreparationLabel,
    translationFailure = translationFailure,
    wordAudioGenerating = wordAudioGenerating,
    hasTarget = hasTarget,
    actionHasTarget = actionHasTarget,
    canSend = canSend,
    submissionLocked = submissionLocked,
    editingEnabled = editingEnabled,
    frozenRetryReady = frozenRetryReady,
    recoveryMessage = recoveryMessage,
    copy = CardPresentationCopy(
        noTargetHint = stringResource(R.string.card_hint_word),
        analyzingHint = stringResource(R.string.card_analyzing_the_sentence),
        sendingHint = stringResource(R.string.sending),
        wordAudioPreparingHint = stringResource(R.string.card_hint_preparing),
        completedStatusFallback = stringResource(R.string.card_note_sent_to_ankidroid),
        duplicateStatusFallback = stringResource(R.string.card_matching_note_exists),
        recoveryStatusFallback = stringResource(R.string.card_pending_send_needs_review),
        sendActionLabel = stringResource(R.string.send),
        retryActionLabel = stringResource(R.string.retry),
        retrySendActionLabel = stringResource(R.string.card_retry_send),
    ),
)

internal data class CardScreenDetailInput(
    val selectedSpan: TargetSpan?,
    val dictionaryLoading: Boolean,
    val dictionarySearchLoading: Boolean,
    val dictionaryError: String?,
    val selectedEntry: DictionaryEntry?,
    val selectedEntryId: Long?,
    val vocabulary: String,
    val vocabularyReading: String,
    val meaningChoices: List<DictionaryMeaningChoice>,
    val selectedMeaning: DictionaryMeaningChoice?,
    val dictionaryReady: Boolean,
    val vocabularyReadingError: String?,
    val wordAudio: ReviewAudioPresentation,
    val pitch: PitchAccent?,
    val pitchLookupLoading: Boolean,
    val pitchIndexError: String?,
    val selectedSenseIndices: Set<Int>,
    val sendLocked: Boolean,
)

private data class CardScreenDetails(
    val selectedEntryIsResolved: Boolean,
    val partOfSpeech: String?,
    val wordStatus: String?,
    val pitchPending: Boolean,
    val pitchStatus: String?,
    val wordAudioEnabled: Boolean,
    val meaningAvailable: Boolean,
)

@Composable
private fun deriveCardScreenDetails(input: CardScreenDetailInput): CardScreenDetails {
    val selectedEntryIsResolved = input.selectedEntry?.let {
        it.id == input.selectedEntryId && it.expression == input.vocabulary && it.reading == input.vocabularyReading
    } == true
    val wordStatus = when {
        input.selectedSpan == null -> null
        input.dictionaryLoading || input.dictionarySearchLoading -> stringResource(R.string.card_dictionary_searching)
        input.dictionaryError != null -> input.dictionaryError
        input.selectedEntry == null && input.meaningChoices.size > 1 ->
            stringResource(R.string.card_choose_dictionary_entry_hint)
        input.selectedEntry == null && input.dictionaryReady -> stringResource(R.string.no_entry)
        input.vocabularyReadingError != null -> input.vocabularyReadingError
        input.wordAudio == ReviewAudioPresentation.SourceMissing -> stringResource(R.string.no_recording)
        else -> null
    }
    val pitchPending = input.selectedSpan != null && input.pitch == null && (
        input.dictionaryLoading || input.dictionarySearchLoading || !selectedEntryIsResolved || input.pitchLookupLoading
    )
    val pitchStatus = when {
        input.selectedSpan == null || input.pitch != null || pitchPending -> null
        !selectedEntryIsResolved -> null
        input.pitchIndexError != null -> input.pitchIndexError
        else -> stringResource(R.string.no_pitch)
    }
    return CardScreenDetails(
        selectedEntryIsResolved = selectedEntryIsResolved,
        partOfSpeech = englishPartOfSpeechLabel(
            VocabularyPartOfSpeech.forSelectedSenses(input.selectedEntry, input.selectedSenseIndices),
        ),
        wordStatus = wordStatus,
        pitchPending = pitchPending,
        pitchStatus = pitchStatus,
        wordAudioEnabled = selectedEntryIsResolved && !input.sendLocked,
        meaningAvailable = DictionaryMeaningChoices.hasAlternative(input.meaningChoices, input.selectedMeaning) &&
            !input.sendLocked,
    )
}

/** Activity-provided content and effects; card decisions remain in [CardScreenStateHolder]. */
internal data class CardScreenContentModel(
    val sentence: String,
    val tokens: List<JapaneseToken>,
    val selectionEnabled: Boolean,
    val onSelectSpan: (TargetSpan) -> Unit,
    val furigana: List<FuriganaSpan>,
    val onEditSentence: () -> Unit,
    val translation: String,
    val vocabulary: String,
    val vocabularyReading: String,
    val details: CardScreenDetailInput,
    val definition: String,
    val pitch: PitchAccent?,
    val onChangeMeaning: () -> Unit,
    val imagePath: String?,
    val imageFocusY: Float,
    val notes: String,
    val tags: String,
    val onNotesChange: (String) -> Unit,
    val onTagsChange: (String) -> Unit,
    val sentenceAudio: ReviewAudioPresentation,
    val wordAudio: ReviewAudioPresentation,
    val onToggleSentenceAudio: () -> Unit,
    val onRetrySentenceAudio: () -> Unit,
    val onToggleWordAudio: () -> Unit,
    val onRetryWordAudio: () -> Unit,
    val actionLoading: Boolean,
    val statusContent: @Composable () -> Unit,
    val modifier: Modifier,
)

@Composable
internal fun CardScreenContent(
    holder: CardScreenStateHolder,
    model: CardScreenContentModel,
) {
    val state = holder.state
    val presentation = state.presentation
    val details = deriveCardScreenDetails(model.details)
    CardScreen(
        sentence = model.sentence,
        tokens = model.tokens,
        selectedSpan = state.selectedSpan,
        selectionEnabled = model.selectionEnabled,
        onSelectSpan = model.onSelectSpan,
        furigana = model.furigana,
        onEditSentence = model.onEditSentence,
        translation = model.translation,
        vocabulary = model.vocabulary,
        vocabularyReading = model.vocabularyReading,
        partOfSpeech = details.partOfSpeech,
        definition = model.definition,
        pitch = model.pitch,
        pitchStatus = details.pitchStatus,
        pitchPending = details.pitchPending,
        wordStatus = details.wordStatus,
        wordAudioEnabled = details.wordAudioEnabled,
        meaningAvailable = details.meaningAvailable,
        onChangeMeaning = model.onChangeMeaning,
        imagePath = model.imagePath,
        imageFocusY = model.imageFocusY,
        notes = model.notes,
        tags = model.tags,
        onNotesChange = model.onNotesChange,
        onTagsChange = model.onTagsChange,
        sentenceAudio = model.sentenceAudio,
        wordAudio = model.wordAudio,
        onToggleSentenceAudio = model.onToggleSentenceAudio,
        onRetrySentenceAudio = model.onRetrySentenceAudio,
        onToggleWordAudio = model.onToggleWordAudio,
        onRetryWordAudio = model.onRetryWordAudio,
        hint = presentation.hint,
        hintPreparing = presentation.hintPreparing,
        actionLabel = presentation.actionLabel,
        actionEnabled = presentation.actionEnabled,
        actionLoading = model.actionLoading,
        onAction = holder::onPrimaryAction,
        statusMessage = presentation.status?.takeIf { it != presentation.hint },
        statusContent = model.statusContent,
        modifier = model.modifier,
    )
}

@Composable
internal fun rememberCardScreenStateHolder(
    sentence: String,
    selectedSpan: TargetSpan?,
    meaningChoice: DictionaryMeaningChoice?,
    committedSentence: String?,
    presentationContext: CardScreenPresentationContext,
    canSelectWords: () -> Boolean,
    isSubmissionLocked: () -> Boolean,
    onWordTapped: (TargetSpan) -> Unit,
    onMeaningChosen: (DictionaryMeaningChoice) -> Unit,
    dispatchPrimaryAction: (CardAction) -> Unit,
): CardScreenStateHolder {
    val latestDispatcher = rememberUpdatedState(dispatchPrimaryAction)
    val holder = remember {
        CardScreenStateHolder(
            sentence = sentence,
            selectedSpan = selectedSpan,
            meaningChoice = meaningChoice,
            committedSentence = committedSentence,
            presentationContext = presentationContext,
            canSelectWords = canSelectWords,
            isSubmissionLocked = isSubmissionLocked,
            onWordTapped = onWordTapped,
            onMeaningChosen = onMeaningChosen,
            dispatchPrimaryAction = { action -> latestDispatcher.value(action) },
        )
    }
    holder.update(sentence, selectedSpan, meaningChoice, committedSentence, presentationContext)
    return holder
}

@Composable
internal fun CardNoTargetRecoveryContent(
    visible: Boolean,
    submitMessage: String?,
    recoveryMessage: String?,
    recoveryContent: @Composable () -> Unit,
) {
    if (!visible) return
    submitMessage?.let {
        FlowStatusText(
            it,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    recoveryMessage?.let {
        FlowStatusText(
            it,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    recoveryContent()
}
