package dev.pierre.japaneseanki.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewAudioPresentationTest {
    @Test fun defaultAudioPresentationIsUnavailableAndDoesNotClaimPlayback() {
        assertEquals("Sentence audio unavailable", reviewAudioControlLabel("Sentence", ReviewAudioPresentation.Unavailable))
        assertEquals("Vocabulary audio unavailable", reviewAudioControlLabel("Vocabulary", ReviewAudioPresentation.Unavailable))
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
    }
}
