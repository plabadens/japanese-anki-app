package dev.pierre.japaneseanki.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewAudioPresentationTest {
    @Test fun defaultAudioPresentationOffersPreparationInsteadOfADeadControl() {
        assertEquals("Prepare Sentence audio", reviewAudioControlLabel("Sentence", ReviewAudioPresentation.Unavailable))
        assertEquals("Prepare Word audio", reviewAudioControlLabel("Word", ReviewAudioPresentation.NeedsPreparation))
        assertEquals(
            "Word audio has not been generated. Tap the speaker control to generate it.",
            reviewAudioSendBlocker("Word", ReviewAudioPresentation.NeedsPreparation),
        )
    }

    @Test fun labelsExposeReadyPlayingGeneratingAndFailedStates() {
        assertEquals("Play Sentence audio", reviewAudioControlLabel("Sentence", ReviewAudioPresentation.Ready))
        assertEquals("Stop Word audio", reviewAudioControlLabel("Word", ReviewAudioPresentation.Playing))
        assertEquals("Generating Sentence audio", reviewAudioControlLabel("Sentence", ReviewAudioPresentation.Generating))
        assertEquals(
            "Retry Vocabulary audio",
            reviewAudioControlLabel("Vocabulary", ReviewAudioPresentation.Failed("No source available")),
        )
    }

    @Test fun failedAudioCarriesTheExplanationForRetryPresentation() {
        val failed = ReviewAudioPresentation.Failed("Audio could not be generated.")

        assertEquals("Audio could not be generated.", failed.message)
        assertEquals("Retry Sentence audio", reviewAudioControlLabel("Sentence", failed))
        assertEquals("Audio could not be generated.", reviewAudioSendBlocker("Sentence", failed))
    }

    @Test fun failureMessagesNameTheAudioStageAndOfferAnAction() {
        assertEquals(
            "No Google Cloud API key is saved. Add one and choose a verified sentence voice in Settings.",
            reviewAudioSendBlocker(
                "Sentence",
                ReviewAudioPresentation.Failed(
                    "No Google Cloud API key is saved. Add one and choose a verified sentence voice in Settings.",
                ),
            ),
        )
        assertEquals(
            "Sentence audio not generated: The audio service timed out",
            reviewAudioGenerationFailureMessage("Sentence", "The audio service timed out. Tap Retry."),
        )
        assertEquals(
            "Sentence audio could not be played on this device",
            reviewAudioPlaybackFailureMessage("Sentence"),
        )
    }

    @Test fun setupAdvisoryIsInformationalAndNamesItsRoute() {
        val advisory = ReviewAudioPresentation.Advisory(
            headline = "Sentence audio setup needed",
            message = "No Google Cloud API key is saved.",
            actionLabel = "Open Settings",
        )

        assertEquals("Open Settings for Sentence audio", reviewAudioControlLabel("Sentence", advisory))
        assertEquals(
            "Sentence audio setup needed: No Google Cloud API key is saved.",
            reviewAudioSendBlocker("Sentence", advisory),
        )
    }

    @Test fun translationSetupAndInterruptedGenerationUseDifferentRolesAndHeadlines() {
        assertEquals(
            ReviewStatusPresentation("Translation setup needed", ReviewStatusRole.Information),
            reviewTranslationFailureStatus("Add your Google Cloud API key in Settings."),
        )
        assertEquals(
            ReviewStatusPresentation("Translation interrupted", ReviewStatusRole.Error),
            reviewTranslationFailureStatus("Translation was interrupted. Tap Retry to continue."),
        )
    }

    @Test fun readyAudioDoesNotBlockSend() {
        assertEquals(null, reviewAudioSendBlocker("Sentence", ReviewAudioPresentation.Ready))
        assertEquals(null, reviewAudioSendBlocker("Word", ReviewAudioPresentation.Playing))
    }
}
