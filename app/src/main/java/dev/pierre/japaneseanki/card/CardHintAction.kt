package dev.pierre.japaneseanki.card

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.pierre.japaneseanki.R

internal data class CardHintActionSignals(
    val translationError: String?,
    val audioSetupReady: Boolean,
    val sentenceAudioReady: Boolean,
    val wordAudioReady: Boolean,
    val apiPermissionGranted: Boolean,
    val modelSelected: Boolean,
    val deckSelected: Boolean,
    val setupError: Boolean,
)

internal data class CardHintActionEffects(
    val openSettings: () -> Unit,
    val enterSetup: () -> Unit,
    val retryTranslation: () -> Unit,
    val retrySentenceAudio: () -> Unit,
    val retryWordAudio: () -> Unit,
    val editPreparation: () -> Unit,
)

internal data class CardHintAction(
    val label: String,
    val invoke: () -> Unit,
)

@Composable
internal fun cardHintAction(
    signals: CardHintActionSignals,
    sentenceAudioLabel: String,
    wordAudioLabel: String,
    effects: CardHintActionEffects,
): CardHintAction {
    val needsSettings = signals.translationError?.let {
        it.contains("Google Cloud API key", ignoreCase = true) || it.contains("credential", ignoreCase = true)
    } == true
    val needsSettingsForSetup = !signals.apiPermissionGranted || !signals.modelSelected ||
        !signals.deckSelected || signals.setupError
    val label = when {
        needsSettings -> stringResource(R.string.card_open_settings_action)
        !signals.audioSetupReady -> stringResource(R.string.finish_setup)
        signals.translationError != null -> stringResource(R.string.retry)
        !signals.sentenceAudioReady -> sentenceAudioLabel
        !signals.wordAudioReady -> wordAudioLabel
        needsSettingsForSetup -> stringResource(R.string.card_open_settings_action)
        else -> stringResource(R.string.card_edit_preparation)
    }
    val action = when {
        needsSettings -> effects.openSettings
        !signals.audioSetupReady -> effects.enterSetup
        signals.translationError != null -> effects.retryTranslation
        !signals.sentenceAudioReady -> effects.retrySentenceAudio
        !signals.wordAudioReady -> effects.retryWordAudio
        needsSettingsForSetup -> effects.openSettings
        else -> effects.editPreparation
    }
    return CardHintAction(label, action)
}
