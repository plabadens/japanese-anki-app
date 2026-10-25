package dev.pierre.japaneseanki.card

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.pierre.japaneseanki.R

internal data class CardHintActionEffects(
    val openSettings: () -> Unit,
    val enterSetup: () -> Unit,
    val retryTranslation: () -> Unit,
    val retrySentenceAudio: () -> Unit,
    val retryWordAudio: () -> Unit,
)

internal data class CardHintAction(val label: String, val invoke: () -> Unit)

/** Actions follow the typed blocker, never English error substrings or unrelated live state. */
@Composable
internal fun cardHintAction(
    blocker: CardSendBlocker?,
    sentenceAudioLabel: String,
    wordAudioLabel: String,
    effects: CardHintActionEffects,
): CardHintAction? = when (blocker?.kind) {
    CardSendBlockerKind.Permission, CardSendBlockerKind.Model, CardSendBlockerKind.Deck,
    CardSendBlockerKind.Mapping, CardSendBlockerKind.FrozenDestination,
    CardSendBlockerKind.SetupError, CardSendBlockerKind.SetupNotLoaded ->
        CardHintAction(stringResource(R.string.card_open_settings_action), effects.openSettings)
    CardSendBlockerKind.AudioSetup -> CardHintAction(stringResource(R.string.finish_setup), effects.enterSetup)
    CardSendBlockerKind.SentenceAudio -> CardHintAction(sentenceAudioLabel, effects.retrySentenceAudio)
    CardSendBlockerKind.WordAudio -> CardHintAction(wordAudioLabel, effects.retryWordAudio)
    CardSendBlockerKind.Translation -> CardHintAction(stringResource(R.string.retry), effects.retryTranslation)
    else -> null
}
