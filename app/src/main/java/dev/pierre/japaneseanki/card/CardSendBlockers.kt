package dev.pierre.japaneseanki.card

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.pierre.japaneseanki.R
import dev.pierre.japaneseanki.ui.ReviewAudioPresentation
import dev.pierre.japaneseanki.ui.reviewAudioSendBlocker

@Composable
internal fun cardSendBlockerMessage(
    blocker: CardSendBlocker?,
    sentenceAudio: ReviewAudioPresentation,
    wordAudio: ReviewAudioPresentation,
): String? {
    if (blocker == null) return null
    return when (blocker.kind) {
        CardSendBlockerKind.Target -> stringResource(R.string.card_hint_word)
        CardSendBlockerKind.AudioSetup -> stringResource(R.string.audio_setup_needed)
        CardSendBlockerKind.ImagePreparing -> stringResource(R.string.card_the_image_is_still_being_prepared_send_bec)
        CardSendBlockerKind.ImageMissing -> stringResource(R.string.card_the_selected_image_is_missing_or_not_ready)
        CardSendBlockerKind.SentenceAudio, CardSendBlockerKind.WordAudio -> {
            val sentence = blocker.kind == CardSendBlockerKind.SentenceAudio
            val field = stringResource(if (sentence) R.string.card_sentence_audio_field else R.string.card_word_audio_field)
            reviewAudioSendBlocker(field, if (sentence) sentenceAudio else wordAudio)?.resolve(LocalContext.current)
                ?: stringResource(R.string.card_audio_not_ready_to_send, field)
        }
        CardSendBlockerKind.Permission -> stringResource(R.string.card_allow_ankidroid_api_access_in_settings_bef)
        CardSendBlockerKind.Model -> stringResource(R.string.card_choose_an_ankidroid_note_type_in_settings)
        CardSendBlockerKind.Deck -> stringResource(R.string.card_choose_an_ankidroid_destination_deck_in_se)
        CardSendBlockerKind.Fields -> stringResource(R.string.card_the_note_fields_are_incomplete_return_to_p)
        CardSendBlockerKind.Validation -> stringResource(R.string.card_still_needed_1_s, blocker.detail.orEmpty())
        CardSendBlockerKind.Mapping -> blocker.detail?.let { stringResource(R.string.card_ankidroid_field_mapping, it) }
            ?: stringResource(R.string.card_note_fields_do_not_match_note_type)
        CardSendBlockerKind.FrozenDestination -> stringResource(R.string.card_restore_the_frozen_note_s_ankidroid_note_t)
        CardSendBlockerKind.Pitch -> stringResource(R.string.card_choose_a_source_listed_pitch_pattern_befor)
        CardSendBlockerKind.DictionaryLoading -> stringResource(R.string.card_the_offline_dictionary_is_still_loading_wa)
        CardSendBlockerKind.SetupError -> blocker.detail
        CardSendBlockerKind.SetupNotLoaded -> stringResource(R.string.card_ankidroid_setup_could_not_be_loaded_open_s)
        CardSendBlockerKind.SetupLoading -> stringResource(R.string.card_ankidroid_setup_is_still_loading_wait_for)
        CardSendBlockerKind.Translation -> stringResource(R.string.card_translation_failed)
        CardSendBlockerKind.Preparation -> stringResource(R.string.card_translation_is_being_prepared)
    }
}
