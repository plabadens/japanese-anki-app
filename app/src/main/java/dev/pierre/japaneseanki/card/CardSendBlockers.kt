package dev.pierre.japaneseanki.card

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.pierre.japaneseanki.R

internal data class CardSendBlockerSignals(
    val audioSetupReady: Boolean,
    val imagePending: Boolean,
    val imageAttached: Boolean,
    val imageProcessing: Boolean,
    val imageError: String?,
    val imageReady: Boolean,
    val sendImageReady: Boolean,
    val frozenRetryReady: Boolean,
    val sentenceAudioReady: Boolean,
    val sentenceAudioField: String,
    val sentenceAudioBlocker: String?,
    val wordAudioReady: Boolean,
    val wordAudioField: String,
    val wordAudioBlocker: String?,
    val apiPermissionGranted: Boolean,
    val modelSelected: Boolean,
    val deckSelected: Boolean,
    val fieldsAvailable: Boolean,
    val validationError: String?,
    val mappingValid: Boolean,
    val mappingError: String?,
    val frozenDestinationReady: Boolean,
    val pitchAvailable: Boolean,
    val dictionaryLoading: Boolean,
    val dictionarySearchLoading: Boolean,
    val setupError: String?,
    val setupLoaded: Boolean,
    val setupLoading: Boolean,
)

@Composable
internal fun cardSendBlockers(input: CardSendBlockerSignals): List<String> = buildList {
    if (!input.audioSetupReady) add(stringResource(R.string.audio_setup_needed))
    if (input.imagePending) {
        add(stringResource(R.string.card_the_image_is_still_being_prepared_send_bec))
    } else if (input.imageAttached && !input.imageProcessing && input.imageError == null && !input.imageReady) {
        add(stringResource(R.string.card_the_selected_image_is_not_ready_to_send))
    } else if (!input.sendImageReady) {
        add(stringResource(R.string.card_the_selected_image_is_missing_or_not_ready))
    }
    if (!input.frozenRetryReady && !input.sentenceAudioReady) {
        add(input.sentenceAudioBlocker ?: stringResource(R.string.card_audio_not_ready_to_send, input.sentenceAudioField))
    }
    if (!input.frozenRetryReady && !input.wordAudioReady) {
        add(input.wordAudioBlocker ?: stringResource(R.string.card_audio_not_ready_to_send, input.wordAudioField))
    }
    if (!input.apiPermissionGranted) add(stringResource(R.string.card_allow_ankidroid_api_access_in_settings_bef))
    if (!input.modelSelected) add(stringResource(R.string.card_choose_an_ankidroid_note_type_in_settings))
    if (!input.deckSelected) add(stringResource(R.string.card_choose_an_ankidroid_destination_deck_in_se))
    if (!input.fieldsAvailable && !input.frozenRetryReady) {
        add(stringResource(R.string.card_the_note_fields_are_incomplete_return_to_p))
    }
    input.validationError?.let { add(stringResource(R.string.card_still_needed_1_s, it)) }
    if (!input.mappingValid) {
        add(
            input.mappingError?.let { stringResource(R.string.card_ankidroid_field_mapping, it) }
                ?: stringResource(R.string.card_note_fields_do_not_match_note_type),
        )
    }
    if (!input.frozenDestinationReady) {
        add(stringResource(R.string.card_restore_the_frozen_note_s_ankidroid_note_t))
    }
    if (!input.frozenRetryReady && !input.pitchAvailable) {
        add(stringResource(R.string.card_choose_a_source_listed_pitch_pattern_befor))
    }
    if (!input.frozenRetryReady && (input.dictionaryLoading || input.dictionarySearchLoading)) {
        add(stringResource(R.string.card_the_offline_dictionary_is_still_loading_wa))
    }
    if (input.setupError != null) add(input.setupError)
    else if (!input.setupLoaded) add(stringResource(R.string.card_ankidroid_setup_could_not_be_loaded_open_s))
    if (input.setupLoading) add(stringResource(R.string.card_ankidroid_setup_is_still_loading_wait_for))
}
