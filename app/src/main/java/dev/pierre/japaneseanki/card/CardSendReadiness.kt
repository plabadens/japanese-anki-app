package dev.pierre.japaneseanki.card

import dev.pierre.japaneseanki.FieldMapping
import dev.pierre.japaneseanki.ManualNoteDraft
import dev.pierre.japaneseanki.ui.SendState

internal enum class CardSendBlockerKind {
    Target, AudioSetup, ImagePreparing, ImageMissing, SentenceAudio, WordAudio,
    Permission, Model, Deck, Fields, Validation, Mapping, FrozenDestination,
    Pitch, Translation, DictionaryLoading, SetupError, SetupNotLoaded, SetupLoading, Preparation,
}

internal data class CardSendBlocker(val kind: CardSendBlockerKind, val detail: String? = null)

internal data class CardSendReadiness(val canSend: Boolean, val blocker: CardSendBlocker?)

internal data class CardSendReadinessInput(
    val sendState: SendState,
    val noteDraft: ManualNoteDraft,
    val hasTarget: Boolean,
    val audioSetupReady: Boolean,
    val imagePending: Boolean,
    val imageReady: Boolean,
    val frozenReceiptReady: Boolean,
    val sentenceAudioReady: Boolean,
    val wordAudioReady: Boolean,
    val apiPermissionGranted: Boolean,
    val modelSelected: Boolean,
    val deckSelected: Boolean,
    val mapping: FieldMapping?,
    val frozenFieldsAvailable: Boolean,
    val frozenDestinationReady: Boolean,
    val pitchAvailable: Boolean,
    val dictionaryLoading: Boolean,
    val preparationReady: Boolean,
    val translationFailed: Boolean,
    val setupError: String?,
    val setupLoaded: Boolean,
    val setupLoading: Boolean,
)

/** The same decision drives Send eligibility, its explanation and its preparation action. */
internal fun cardSendReadiness(input: CardSendReadinessInput): CardSendReadiness {
    val retry = input.sendState.retryReady
    // Pending writes, duplicates and completed notes have recovery/status UI, not live-draft blockers.
    if (!input.sendState.editingEnabled && !retry) return CardSendReadiness(false, null)
    fun blocked(kind: CardSendBlockerKind, detail: String? = null) =
        CardSendReadiness(false, CardSendBlocker(kind, detail))
    with(input) {
        if (!retry && !hasTarget) return blocked(CardSendBlockerKind.Target)
        if (!retry && !audioSetupReady) return blocked(CardSendBlockerKind.AudioSetup)
        if (!apiPermissionGranted) return blocked(CardSendBlockerKind.Permission)
        if (!modelSelected) return blocked(CardSendBlockerKind.Model)
        if (!deckSelected) return blocked(CardSendBlockerKind.Deck)
        if (setupError != null) return blocked(CardSendBlockerKind.SetupError, setupError)
        if (setupLoading) return blocked(CardSendBlockerKind.SetupLoading)
        if (!setupLoaded) return blocked(CardSendBlockerKind.SetupNotLoaded)
        if (mapping?.isValid != true) return blocked(CardSendBlockerKind.Mapping, mapping?.errors?.firstOrNull())
        if (retry) {
            if (!frozenDestinationReady) return blocked(CardSendBlockerKind.FrozenDestination)
            if (!frozenFieldsAvailable) return blocked(CardSendBlockerKind.Fields)
            if (!frozenReceiptReady) return blocked(CardSendBlockerKind.ImageMissing)
            return CardSendReadiness(true, null)
        }
        if (imagePending) return blocked(CardSendBlockerKind.ImagePreparing)
        if (!imageReady) return blocked(CardSendBlockerKind.ImageMissing)
        if (dictionaryLoading) return blocked(CardSendBlockerKind.DictionaryLoading)
        if (translationFailed) return blocked(CardSendBlockerKind.Translation)
        noteDraft.validationErrors().firstOrNull()?.let { return blocked(CardSendBlockerKind.Validation, it) }
        if (noteDraft.fieldsByName() == null) return blocked(CardSendBlockerKind.Fields)
        if (!pitchAvailable) return blocked(CardSendBlockerKind.Pitch)
        if (!sentenceAudioReady) return blocked(CardSendBlockerKind.SentenceAudio)
        if (!wordAudioReady) return blocked(CardSendBlockerKind.WordAudio)
        if (!preparationReady) return blocked(CardSendBlockerKind.Preparation)
        return CardSendReadiness(true, null)
    }
}
