package dev.pierre.japaneseanki.card

import dev.pierre.japaneseanki.DictionaryEntry
import dev.pierre.japaneseanki.DictionaryMeaningChoice
import dev.pierre.japaneseanki.DictionarySense
import dev.pierre.japaneseanki.TargetSpan
import dev.pierre.japaneseanki.ui.CardAction
import dev.pierre.japaneseanki.ui.CardPresentationCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardScreenStateHolderTest {
    @Test
    fun wordTapSelectsTargetAndResolvesItsText() {
        val holder = holder()
        holder.onWordTapped(WORD)
        assertEquals(WORD, holder.state.selectedSpan)
        assertEquals("猫", holder.state.targetWord)
    }

    @Test
    fun tappingDifferentWordReplacesSelection() {
        val holder = holder(initialSpan = WORD)
        holder.onWordTapped(SECOND_WORD)
        assertEquals(SECOND_WORD, holder.state.selectedSpan)
        assertEquals("犬", holder.state.targetWord)
    }

    @Test
    fun tappingSameWordTwiceDispatchesOnlyOnce() {
        var taps = 0
        val holder = holder(onWordTapped = { taps++ })
        holder.onWordTapped(WORD)
        holder.onWordTapped(WORD)
        assertEquals(1, taps)
    }

    @Test
    fun choosingMeaningUpdatesChoiceWithoutClearingTarget() {
        val holder = holder(initialSpan = WORD)
        holder.onMeaningChosen(CHOICE)
        assertEquals(WORD, holder.state.selectedSpan)
        assertEquals("猫", holder.state.targetWord)
        assertEquals(CHOICE, holder.state.meaningChoice)
    }

    @Test
    fun primaryActionDispatchesExactlyOnceWhenEnabled() {
        var dispatches = 0
        val holder = holder(initialSpan = WORD, dispatch = { dispatches++ })
        holder.onPrimaryAction()
        assertEquals(1, dispatches)
    }

    @Test
    fun primaryActionDoesNotDispatchWhenLocked() {
        var dispatches = 0
        val holder = holder(initialSpan = WORD, locked = true, dispatch = { dispatches++ })
        holder.onPrimaryAction()
        assertEquals(0, dispatches)
    }

    @Test
    fun editingSentenceToCommittedTextDoesNotMarkSentenceInvalidated() {
        val holder = holder(sentence = "犬", initialSpan = TargetSpan(0, 1), committedSentence = "犬")
        holder.onSentenceChanged("猫")
        holder.onSentenceChanged("犬")
        assertFalse(holder.state.sentenceDiffersFromCommitted)
    }

    @Test
    fun editingSentenceAwayFromCommittedTextMarksSentenceInvalidated() {
        val holder = holder(sentence = "犬", initialSpan = TargetSpan(0, 1), committedSentence = "犬")
        holder.onSentenceChanged("猫")
        assertTrue(holder.state.sentenceDiffersFromCommitted)
    }

    @Test
    fun sentenceIntentDoesNothingForUnchangedText() {
        val holder = holder(sentence = "猫", initialSpan = WORD, committedSentence = "猫")
        holder.onSentenceChanged("猫")
        assertEquals("猫", holder.state.sentence)
        assertFalse(holder.state.sentenceDiffersFromCommitted)
    }

    @Test
    fun lockedHolderRejectsWordAndMeaningIntents() {
        var taps = 0
        var choices = 0
        val holder = holder(
            initialSpan = WORD,
            locked = true,
            onWordTapped = { taps++ },
            onMeaningChosen = { choices++ },
        )
        holder.onWordTapped(SECOND_WORD)
        holder.onMeaningChosen(CHOICE)
        assertEquals(0, taps)
        assertEquals(0, choices)
        assertEquals(WORD, holder.state.selectedSpan)
        assertNull(holder.state.meaningChoice)
    }

    private fun holder(
        sentence: String = SENTENCE,
        initialSpan: TargetSpan? = null,
        meaning: DictionaryMeaningChoice? = null,
        committedSentence: String? = sentence,
        locked: Boolean = false,
        onWordTapped: (TargetSpan) -> Unit = {},
        onMeaningChosen: (DictionaryMeaningChoice) -> Unit = {},
        dispatch: (CardAction) -> Unit = {},
    ) = CardScreenStateHolder(
        sentence = sentence,
        selectedSpan = initialSpan,
        meaningChoice = meaning,
        committedSentence = committedSentence,
        presentationContext = context(
            canSend = true,
            submissionLocked = locked,
        ),
        canSelectWords = { !locked },
        isSubmissionLocked = { locked },
        onWordTapped = onWordTapped,
        onMeaningChosen = onMeaningChosen,
        dispatchPrimaryAction = dispatch,
    )

    private fun context(
        canSend: Boolean = false,
        submissionLocked: Boolean = false,
    ) = CardScreenPresentationContext(
        analyzerError = null,
        analyzing = false,
        completed = false,
        duplicateBlocked = false,
        recoveryRequired = false,
        submitting = false,
        sendBlocked = false,
        submitMessage = null,
        submitHint = null,
        submitHintActionLabel = null,
        translationFailure = null,
        wordAudioGenerating = false,
        canSend = canSend,
        hasTarget = true,
        actionHasTarget = true,
        submissionLocked = submissionLocked,
        editingEnabled = !submissionLocked,
        frozenRetryReady = false,
        recoveryMessage = null,
        copy = COPY,
    )

    private companion object {
        const val SENTENCE = "猫と犬"
        val WORD = TargetSpan(0, 1)
        val SECOND_WORD = TargetSpan(2, 3)
        val CHOICE = DictionaryMeaningChoice(
            entry = DictionaryEntry(
                id = 1,
                expression = "猫",
                reading = "ねこ",
                senses = listOf(DictionarySense(listOf("cat"), emptyList(), emptyList())),
            ),
            senseIndex = 0,
            definition = "cat",
        )
        val COPY = CardPresentationCopy(
            noTargetHint = "Choose a word",
            analyzingHint = "Analyzing",
            sendingHint = "Sending",
            wordAudioPreparingHint = "Preparing audio",
            completedStatusFallback = "Sent",
            duplicateStatusFallback = "Duplicate",
            recoveryStatusFallback = "Recovery required",
            sendActionLabel = "Send",
            retryActionLabel = "Retry",
            retrySendActionLabel = "Retry send",
        )
    }
}
