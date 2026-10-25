package dev.pierre.japaneseanki.card

import dev.pierre.japaneseanki.AnkiFieldContract
import dev.pierre.japaneseanki.ManualNoteDraft
import dev.pierre.japaneseanki.PitchAccent
import dev.pierre.japaneseanki.TargetSpan
import dev.pierre.japaneseanki.ui.RecoveryReason
import dev.pierre.japaneseanki.ui.SendPhase
import dev.pierre.japaneseanki.ui.SendState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardSendReadinessTest {
    @Test
    fun readyDraftHasNoBlockerAndCanSend() {
        val result = cardSendReadiness(ready())
        assertTrue(result.canSend)
        assertNull(result.blocker)
    }

    @Test
    fun frozenRetryValidatesFrozenFieldsNotTheUnresolvedLiveDictionaryOrGenerationState() {
        val input = ready().copy(
            sendState = SendState(phase = SendPhase.Preparing),
            noteDraft = ready().noteDraft.copy(dictionaryEntryResolved = false, pitch = null, translation = ""),
            audioSetupReady = false,
            imagePending = true,
            imageReady = false,
            sentenceAudioReady = false,
            wordAudioReady = false,
            pitchAvailable = false,
            dictionaryLoading = true,
            translationFailed = true,
            preparationReady = false,
        )
        assertFalse(input.sendState.editingEnabled) // The frozen submission must remain immutable.
        assertTrue(input.noteDraft.validationErrors().contains("Select a dictionary entry."))
        val result = cardSendReadiness(input)
        assertTrue(result.canSend)
        assertNull(result.blocker)
    }

    @Test
    fun frozenRetryStillRequiresItsExactDestinationFieldsAndImageReceipt() {
        val retry = ready().copy(sendState = SendState(phase = SendPhase.Preparing))
        listOf(
            retry.copy(frozenDestinationReady = false) to CardSendBlockerKind.FrozenDestination,
            retry.copy(frozenFieldsAvailable = false) to CardSendBlockerKind.Fields,
            retry.copy(frozenReceiptReady = false) to CardSendBlockerKind.ImageMissing,
            retry.copy(apiPermissionGranted = false) to CardSendBlockerKind.Permission,
        ).forEach { (input, reason) ->
            val result = cardSendReadiness(input)
            assertFalse(reason.name, result.canSend)
            assertEquals(reason, result.blocker?.kind)
        }
    }

    @Test
    fun pendingWritesDuplicatesAndCompletedNotesNeverShowLiveDraftValidation() {
        listOf(
            SendState(phase = SendPhase.Submitting),
            SendState(phase = SendPhase.Inserted, recovery = RecoveryReason.MediaInterrupted),
            SendState(duplicateNoteId = 51),
            SendState(phase = SendPhase.Verified, completedNoteId = 51),
        ).forEach { state ->
            val result = cardSendReadiness(ready().copy(
                sendState = state,
                noteDraft = ready().noteDraft.copy(dictionaryEntryResolved = false),
            ))
            assertFalse(result.canSend)
            assertNull("Recovery owns the explanation, not the live dictionary", result.blocker)
        }
    }

    @Test
    fun everyOrdinaryEligibilityFailureHasAnExplanationFromTheSameDecision() {
        val ready = ready()
        listOf(
            ready.copy(hasTarget = false) to CardSendBlockerKind.Target,
            ready.copy(audioSetupReady = false) to CardSendBlockerKind.AudioSetup,
            ready.copy(imagePending = true) to CardSendBlockerKind.ImagePreparing,
            ready.copy(imageReady = false) to CardSendBlockerKind.ImageMissing,
            ready.copy(sentenceAudioReady = false) to CardSendBlockerKind.SentenceAudio,
            ready.copy(wordAudioReady = false) to CardSendBlockerKind.WordAudio,
            ready.copy(modelSelected = false) to CardSendBlockerKind.Model,
            ready.copy(deckSelected = false) to CardSendBlockerKind.Deck,
            ready.copy(mapping = null) to CardSendBlockerKind.Mapping,
            ready.copy(dictionaryLoading = true) to CardSendBlockerKind.DictionaryLoading,
            ready.copy(noteDraft = ready.noteDraft.copy(dictionaryEntryResolved = false)) to CardSendBlockerKind.Validation,
            ready.copy(translationFailed = true) to CardSendBlockerKind.Translation,
            ready.copy(preparationReady = false) to CardSendBlockerKind.Preparation,
            ready.copy(setupError = "Permission lost") to CardSendBlockerKind.SetupError,
            ready.copy(setupLoaded = false) to CardSendBlockerKind.SetupNotLoaded,
            ready.copy(setupLoading = true) to CardSendBlockerKind.SetupLoading,
        ).forEach { (input, reason) ->
            val result = cardSendReadiness(input)
            assertFalse(reason.name, result.canSend)
            assertEquals(reason, result.blocker?.kind)
        }
    }

    private fun ready() = CardSendReadinessInput(
        sendState = SendState(),
        noteDraft = ManualNoteDraft(
            sentence = "猫が走る", target = TargetSpan(0, 1), sentenceReading = "ねこ", vocabulary = "猫",
            vocabularyReading = "ねこ", definition = "cat", dictionaryEntryResolved = true,
            pitch = PitchAccent.create("ねこ", 0), translation = "The cat runs.",
        ),
        hasTarget = true, audioSetupReady = true, imagePending = false, imageReady = true,
        frozenReceiptReady = true, sentenceAudioReady = true, wordAudioReady = true,
        apiPermissionGranted = true, modelSelected = true, deckSelected = true,
        mapping = AnkiFieldContract.mapToModelOrder(AnkiFieldContract.requiredFields, emptyMap()),
        frozenFieldsAvailable = true, frozenDestinationReady = true, pitchAvailable = true,
        dictionaryLoading = false, preparationReady = true, translationFailed = false,
        setupError = null, setupLoaded = true, setupLoading = false,
    )
}
