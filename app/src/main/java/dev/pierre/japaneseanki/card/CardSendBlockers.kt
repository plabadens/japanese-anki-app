package dev.pierre.japaneseanki.card

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.pierre.japaneseanki.AnkiDeck
import dev.pierre.japaneseanki.AnkiNoteType
import dev.pierre.japaneseanki.FieldMapping
import dev.pierre.japaneseanki.ManualNoteDraft
import dev.pierre.japaneseanki.R
import dev.pierre.japaneseanki.SetupReadiness
import dev.pierre.japaneseanki.ui.ReviewAudioPresentation
import dev.pierre.japaneseanki.ui.SendState
import dev.pierre.japaneseanki.ui.reviewAudioSendBlocker

@Composable
internal fun cardSendBlockers(
    setupReadiness: SetupReadiness,
    sendState: SendState,
    noteDraft: ManualNoteDraft,
    imageAttached: Boolean,
    imageProcessing: Boolean,
    imageError: String?,
    imageReady: Boolean,
    frozenReceiptReady: Boolean,
    sentenceAudioReady: Boolean,
    sentenceAudio: ReviewAudioPresentation,
    wordAudioReady: Boolean,
    wordAudio: ReviewAudioPresentation,
    apiPermissionGranted: Boolean,
    selectedModel: AnkiNoteType?,
    selectedDeck: AnkiDeck?,
    effectiveMapping: FieldMapping?,
    frozenDestinationReady: Boolean,
    pitchAvailable: Boolean,
    dictionaryLoading: Boolean,
    dictionarySearchLoading: Boolean,
    setupError: String?,
    setupLoaded: Boolean,
    setupLoading: Boolean,
): List<String> {
    val context = LocalContext.current
    val sentenceAudioField = stringResource(R.string.card_sentence_audio_field)
    val wordAudioField = stringResource(R.string.card_word_audio_field)
    val sentenceAudioBlocker = reviewAudioSendBlocker(sentenceAudioField, sentenceAudio)
        ?.resolve(context)
    val wordAudioBlocker = reviewAudioSendBlocker(wordAudioField, wordAudio)
        ?.resolve(context)
    val imagePending = imageAttached && imageProcessing
    val frozenRetryReady = sendState.retryReady
    val sendImageReady = if (frozenRetryReady) frozenReceiptReady else imageReady
    val validationError = noteDraft.validationErrors().firstOrNull()
    val fieldsAvailable = noteDraft.fieldsByName() != null

    return buildList {
        if (!setupReadiness.audioReady) add(stringResource(R.string.audio_setup_needed))
        if (imagePending) {
            add(stringResource(R.string.card_the_image_is_still_being_prepared_send_bec))
        } else if (imageAttached && !imageProcessing && imageError == null && !imageReady) {
            add(stringResource(R.string.card_the_selected_image_is_not_ready_to_send))
        } else if (!sendImageReady) {
            add(stringResource(R.string.card_the_selected_image_is_missing_or_not_ready))
        }
        if (!frozenRetryReady && !sentenceAudioReady) {
            add(sentenceAudioBlocker ?: stringResource(R.string.card_audio_not_ready_to_send, sentenceAudioField))
        }
        if (!frozenRetryReady && !wordAudioReady) {
            add(wordAudioBlocker ?: stringResource(R.string.card_audio_not_ready_to_send, wordAudioField))
        }
        if (!apiPermissionGranted) add(stringResource(R.string.card_allow_ankidroid_api_access_in_settings_bef))
        if (selectedModel == null) add(stringResource(R.string.card_choose_an_ankidroid_note_type_in_settings))
        if (selectedDeck == null) add(stringResource(R.string.card_choose_an_ankidroid_destination_deck_in_se))
        if (!fieldsAvailable && !frozenRetryReady) {
            add(stringResource(R.string.card_the_note_fields_are_incomplete_return_to_p))
        }
        validationError?.let { add(stringResource(R.string.card_still_needed_1_s, it)) }
        if (effectiveMapping?.isValid != true) {
            add(
                effectiveMapping?.errors?.firstOrNull()?.let {
                    stringResource(R.string.card_ankidroid_field_mapping, it)
                } ?: stringResource(R.string.card_note_fields_do_not_match_note_type),
            )
        }
        if (!frozenDestinationReady) {
            add(stringResource(R.string.card_restore_the_frozen_note_s_ankidroid_note_t))
        }
        if (!frozenRetryReady && !pitchAvailable) {
            add(stringResource(R.string.card_choose_a_source_listed_pitch_pattern_befor))
        }
        if (!frozenRetryReady && (dictionaryLoading || dictionarySearchLoading)) {
            add(stringResource(R.string.card_the_offline_dictionary_is_still_loading_wa))
        }
        if (setupError != null) add(setupError)
        else if (!setupLoaded) add(stringResource(R.string.card_ankidroid_setup_could_not_be_loaded_open_s))
        if (setupLoading) add(stringResource(R.string.card_ankidroid_setup_is_still_loading_wait_for))
    }
}
