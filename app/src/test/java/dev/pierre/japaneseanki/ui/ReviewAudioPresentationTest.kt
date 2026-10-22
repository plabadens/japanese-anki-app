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
            "Explain unavailable Vocabulary audio",
            reviewAudioControlLabel("Vocabulary", ReviewAudioPresentation.Failed("No source available")),
        )
    }

    @Test fun failedAudioCarriesTheExplanationForRetryPresentation() {
        val failed = ReviewAudioPresentation.Failed("Audio could not be generated.")

        assertEquals("Audio could not be generated.", failed.message)
        assertEquals("Explain unavailable Sentence audio", reviewAudioControlLabel("Sentence", failed))
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
            "Sentence audio generation failed: The audio service timed out. Tap Retry.",
            reviewAudioGenerationFailureMessage("Sentence", "The audio service timed out. Tap Retry."),
        )
        assertEquals(
            "Sentence audio could not be played on this device. Tap Retry to try again.",
            reviewAudioPlaybackFailureMessage("Sentence"),
        )
    }

    @Test fun readyAudioDoesNotBlockSend() {
        assertEquals(null, reviewAudioSendBlocker("Sentence", ReviewAudioPresentation.Ready))
        assertEquals(null, reviewAudioSendBlocker("Word", ReviewAudioPresentation.Playing))
    }
}
