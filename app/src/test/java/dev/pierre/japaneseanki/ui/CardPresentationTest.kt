package dev.pierre.japaneseanki.ui

import dev.pierre.japaneseanki.VoiceCatalogProvenance
import dev.pierre.japaneseanki.VoiceCatalogState
import dev.pierre.japaneseanki.sentenceVoiceForSynthesis
import dev.pierre.japaneseanki.speech.CloudVoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardPresentationTest {
    @Test
    fun preparedOfflineDraftCanSendWithTheSavedCatalog() {
        val voice = CloudVoice("ja-JP-Chirp3-HD-Aoede", "Aoede", listOf("ja-JP"), 24_000)
        val selected = sentenceVoiceForSynthesis(
            credentialStored = true,
            credentialUnreadable = false,
            voiceName = voice.name,
            catalog = VoiceCatalogState(
                voices = listOf(voice),
                provenance = VoiceCatalogProvenance.CACHED,
            ),
        )

        assertEquals(voice, selected)
        // Reopening with the same identity retains both already-generated audio artifacts.
        val sentenceAudioReady = true
        val wordAudioReady = true
        val canSend = selected != null && sentenceAudioReady && wordAudioReady
        assertTrue(cardPresentation(input(canSend = canSend)).actionEnabled)
    }

    @Test
    fun missingCatalogKeepsSendDisabledAndShowsSetupReason() {
        val reason = "The selected sentence voice is not verified. Reload voices and choose a verified voice in Settings."
        val selected = sentenceVoiceForSynthesis(
            credentialStored = true,
            credentialUnreadable = false,
            voiceName = "ja-JP-Chirp3-HD-Aoede",
            catalog = VoiceCatalogState(),
        )
        val result = cardPresentation(
            input(
                canSend = selected != null,
                submitHint = reason,
            ),
        )

        assertFalse(result.actionEnabled)
        assertEquals(reason, result.hint)
    }

    @Test
    fun derivesHintStatusAndPrimaryActionForEachCardState() {
        val states = listOf(
            State(
                name = "no target",
                input = input(hasTarget = false, actionHasTarget = false, noWordAction = true),
                label = "Send",
                enabled = false,
                action = CardAction.None,
                hint = "Choose a word",
                status = null,
            ),
            State(
                name = "analyzing",
                input = input(analyzing = true),
                label = "Send",
                enabled = false,
                action = CardAction.Submit,
                hint = "Analyzing",
                status = null,
                hintPreparing = true,
            ),
            State(
                name = "analyzer error",
                input = input(analyzerError = "Analysis failed"),
                label = "Retry",
                enabled = true,
                action = CardAction.RetryAnalysis,
                hint = "Analysis failed",
                status = null,
            ),
            State(
                name = "sending",
                input = input(submitting = true, submissionLocked = true, canSend = false),
                label = "Send",
                enabled = false,
                action = CardAction.Submit,
                hint = "Sending",
                status = null,
            ),
            State(
                name = "duplicate blocked",
                input = input(
                    duplicateBlocked = true,
                    sendBlocked = true,
                    submitMessage = "Matching note exists",
                    submissionLocked = true,
                    canSend = false,
                ),
                label = "Send",
                enabled = false,
                action = CardAction.Submit,
                hint = "Matching note exists",
                status = "Matching note exists",
            ),
            State(
                name = "recovery required",
                input = input(
                    recoveryRequired = true,
                    recoveryMessage = "Pending send needs review",
                    submissionLocked = true,
                    canSend = false,
                ),
                label = "Send",
                enabled = false,
                action = CardAction.Submit,
                hint = "Pending send needs review",
                status = "Pending send needs review",
            ),
            State(
                name = "completed",
                input = input(completed = true, submitMessage = "Sent to AnkiDroid", canSend = false),
                label = "Send",
                enabled = false,
                action = CardAction.RetrySend,
                hint = "Sent to AnkiDroid",
                status = "Sent to AnkiDroid",
            ),
            State(
                name = "send blocked message",
                input = input(
                    sendBlocked = true,
                    submitMessage = "Blocked: finish setup",
                    canSend = false,
                ),
                label = "Send",
                enabled = false,
                action = CardAction.Submit,
                hint = "Blocked: finish setup",
                status = null,
            ),
            State(
                name = "word audio generating",
                input = input(
                    wordAudioGenerating = true,
                    submitHint = "Word audio is not ready",
                    canSend = false,
                ),
                label = "Send",
                enabled = false,
                action = CardAction.Submit,
                hint = "Preparing audio",
                status = null,
            ),
            State(
                name = "retry-send",
                input = input(submitMessage = "Temporary send failure", canSend = true),
                label = "Retry send",
                enabled = true,
                action = CardAction.RetrySend,
                hint = "Temporary send failure",
                status = null,
            ),
            State(
                name = "recovery action label",
                input = input(
                    recoveryActionLabel = "Retry audio",
                    submitHint = "Audio is not ready",
                    canSend = false,
                ),
                label = "Retry audio",
                enabled = true,
                action = CardAction.Recovery,
                hint = "Audio is not ready",
                status = null,
            ),
            State(
                name = "nominal",
                input = input(),
                label = "Send",
                enabled = true,
                action = CardAction.Submit,
                hint = null,
                status = null,
            ),
        )

        states.forEach { state ->
            val result = cardPresentation(state.input)
            assertEquals("${state.name}: label", state.label, result.actionLabel)
            assertEquals("${state.name}: enabled", state.enabled, result.actionEnabled)
            assertEquals("${state.name}: action", state.action, result.action)
            assertEquals("${state.name}: hint", state.hint, result.hint)
            assertEquals("${state.name}: status", state.status, result.status)
            assertEquals("${state.name}: hintPreparing", state.hintPreparing, result.hintPreparing)
        }
    }

    private fun input(
        hasTarget: Boolean = true,
        analyzerError: String? = null,
        analyzing: Boolean = false,
        completed: Boolean = false,
        duplicateBlocked: Boolean = false,
        recoveryRequired: Boolean = false,
        submitting: Boolean = false,
        sendBlocked: Boolean = false,
        submitMessage: String? = null,
        recoveryActionLabel: String? = null,
        translationFailure: String? = null,
        wordAudioGenerating: Boolean = false,
        submitHint: String? = null,
        noWordAction: Boolean = false,
        canSend: Boolean = true,
        submissionLocked: Boolean = false,
        actionHasTarget: Boolean = hasTarget,
        recoveryMessage: String? = null,
    ) = CardPresentationInput(
        hasTarget = hasTarget,
        analyzerError = analyzerError,
        analyzing = analyzing,
        completed = completed,
        duplicateBlocked = duplicateBlocked,
        recoveryRequired = recoveryRequired,
        submitting = submitting,
        sendBlocked = sendBlocked,
        submitMessage = submitMessage,
        recoveryActionLabel = recoveryActionLabel,
        translationFailure = translationFailure,
        wordAudioGenerating = wordAudioGenerating,
        submitHint = submitHint,
        noWordAction = noWordAction,
        canSend = canSend,
        submissionLocked = submissionLocked,
        actionHasTarget = actionHasTarget,
        recoveryMessage = recoveryMessage,
        copy = COPY,
    )

    private data class State(
        val name: String,
        val input: CardPresentationInput,
        val label: String,
        val enabled: Boolean,
        val action: CardAction,
        val hint: String?,
        val status: String?,
        val hintPreparing: Boolean = false,
    )

    private companion object {
        val COPY = CardPresentationCopy(
            noTargetHint = "Choose a word",
            analyzingHint = "Analyzing",
            sendingHint = "Sending",
            wordAudioPreparingHint = "Preparing audio",
            completedStatusFallback = "Note sent to AnkiDroid",
            duplicateStatusFallback = "Matching note exists",
            recoveryStatusFallback = "Pending send needs review",
            sendActionLabel = "Send",
            retryActionLabel = "Retry",
            retrySendActionLabel = "Retry send",
        )
    }
}
