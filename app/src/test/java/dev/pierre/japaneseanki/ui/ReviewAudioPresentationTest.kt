package dev.pierre.japaneseanki.ui

import dev.pierre.japaneseanki.R
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewAudioPresentationTest {
    @Test fun defaultAudioPresentationOffersPreparationInsteadOfADeadControl() {
        assertEquals(
            ResourceCopy(R.string.legacy_card_audio_prepare_control, listOf("Sentence")),
            reviewAudioControlLabel("Sentence", ReviewAudioPresentation.Unavailable),
        )
        assertEquals(
            ResourceCopy(R.string.legacy_card_audio_prepare_control, listOf("Word")),
            reviewAudioControlLabel("Word", ReviewAudioPresentation.NeedsPreparation),
        )
        assertEquals(
            ResourceCopy(R.string.legacy_card_audio_needs_preparation_blocker, listOf("Word")),
            reviewAudioSendBlocker("Word", ReviewAudioPresentation.NeedsPreparation),
        )
    }

    @Test fun labelsExposeReadyPlayingGeneratingAndFailedStates() {
        assertEquals(ResourceCopy(R.string.legacy_card_audio_play_control, listOf("Sentence")), reviewAudioControlLabel("Sentence", ReviewAudioPresentation.Ready))
        assertEquals(ResourceCopy(R.string.legacy_card_audio_stop_control, listOf("Word")), reviewAudioControlLabel("Word", ReviewAudioPresentation.Playing))
        assertEquals(ResourceCopy(R.string.legacy_card_audio_generating_control, listOf("Sentence")), reviewAudioControlLabel("Sentence", ReviewAudioPresentation.Generating))
        assertEquals(
            ResourceCopy(R.string.legacy_card_audio_retry_control, listOf("Vocabulary")),
            reviewAudioControlLabel("Vocabulary", ReviewAudioPresentation.Failed("No source available")),
        )
    }

    @Test fun failedAudioCarriesItsDynamicExplanation() {
        val failed = ReviewAudioPresentation.Failed("Audio could not be generated.")
        assertEquals("Audio could not be generated.", failed.message)
        assertEquals(ResourceCopy(R.string.legacy_card_audio_retry_control, listOf("Sentence")), reviewAudioControlLabel("Sentence", failed))
        assertEquals(
            ResourceCopy(R.string.legacy_card_audio_message, listOf("Audio could not be generated.")),
            reviewAudioSendBlocker("Sentence", failed),
        )
    }

    @Test fun failureMessagesNameTheAudioStageAndOfferAnAction() {
        assertEquals(
            ResourceCopy(R.string.legacy_card_audio_message, listOf("No Google Cloud API key is saved. Add one and choose a verified sentence voice in Settings.")),
            reviewAudioSendBlocker(
                "Sentence",
                ReviewAudioPresentation.Failed("No Google Cloud API key is saved. Add one and choose a verified sentence voice in Settings."),
            ),
        )
        assertEquals(
            ResourceCopy(R.string.legacy_card_audio_generation_failed, listOf("Sentence", "The audio service timed out")),
            reviewAudioGenerationFailureMessage("Sentence", "The audio service timed out. Tap Retry.", "No diagnostic details are available."),
        )
        assertEquals(
            ResourceCopy(R.string.legacy_card_audio_playback_failed, listOf("Sentence")),
            reviewAudioPlaybackFailureMessage("Sentence"),
        )
    }

    @Test fun setupAdvisoryIsInformationalAndNamesItsRoute() {
        val advisory = ReviewAudioPresentation.Advisory(
            headline = "Sentence audio setup needed",
            message = "No Google Cloud API key is saved.",
            actionLabel = "Open Settings",
        )
        assertEquals(
            ResourceCopy(R.string.legacy_card_audio_advisory_control, listOf("Open Settings", "Sentence")),
            reviewAudioControlLabel("Sentence", advisory),
        )
        assertEquals(
            ResourceCopy(R.string.legacy_card_audio_advisory_blocker, listOf("Sentence audio setup needed", "No Google Cloud API key is saved.")),
            reviewAudioSendBlocker("Sentence", advisory),
        )
    }

    @Test fun translationSetupAndInterruptedGenerationUseDifferentRolesAndHeadlines() {
        assertEquals(
            TranslationFailurePresentation(R.string.legacy_card_translation_setup_needed, ReviewStatusRole.Information),
            reviewTranslationFailureStatus("Add your Google Cloud API key in Settings."),
        )
        assertEquals(
            TranslationFailurePresentation(R.string.legacy_card_translation_interrupted, ReviewStatusRole.Error),
            reviewTranslationFailureStatus("Translation was interrupted. Tap Retry to continue."),
        )
    }

    @Test fun readyAudioDoesNotBlockSend() {
        assertEquals(null, reviewAudioSendBlocker("Sentence", ReviewAudioPresentation.Ready))
        assertEquals(null, reviewAudioSendBlocker("Word", ReviewAudioPresentation.Playing))
    }
}
