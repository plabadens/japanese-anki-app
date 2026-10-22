package dev.pierre.japaneseanki.ui

import androidx.compose.ui.unit.dp
import dev.pierre.japaneseanki.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewCardPresentationTest {
    @Test fun reviewExposesOnlyNotesAndTagsAsInlineEditableFields() {
        assertEquals(
            setOf(ReviewEditableField.Notes, ReviewEditableField.Tags),
            reviewEditableFields(editing = true, editingEnabled = true),
        )
        assertEquals(emptySet<ReviewEditableField>(), reviewEditableFields(editing = false, editingEnabled = true))
        assertEquals(emptySet<ReviewEditableField>(), reviewEditableFields(editing = true, editingEnabled = false))
    }

    @Test fun reviewFieldCallbacksCanOnlyUpdateNotesAndTags() {
        val changed = mutableListOf<Pair<ReviewEditableField, String>>()
        val callbacks = ReviewFieldCallbacks(
            onNotesChange = { changed += ReviewEditableField.Notes to it },
            onTagsChange = { changed += ReviewEditableField.Tags to it },
        )

        callbacks.update(ReviewEditableField.Notes, "Useful note")
        callbacks.update(ReviewEditableField.Tags, "grammar jlpt-n3")

        assertEquals(
            listOf(
                ReviewEditableField.Notes to "Useful note",
                ReviewEditableField.Tags to "grammar jlpt-n3",
            ),
            changed,
        )
    }

    @Test fun preparationCorrectionsAreNotReviewControls() {
        assertFalse(reviewEditableFields(editing = true, editingEnabled = true).any {
            it.name in setOf("Translation", "Reading", "Meaning", "Pitch", "Photo")
        })
        assertEquals(R.string.legacy_card_edit_preparation, ReviewPreparationActionResource)
    }

    @Test fun audioControlsUsePlayStopAndDistinguishPreparationFromFailure() {
        assertTrue(ReviewAudioControlSize >= 48.dp)
        assertEquals(ResourceCopy(R.string.legacy_card_audio_play_control, listOf("Sentence")), reviewAudioControlLabel("Sentence", ReviewAudioPresentation.Ready))
        assertEquals(ResourceCopy(R.string.legacy_card_audio_stop_control, listOf("Word")), reviewAudioControlLabel("Word", ReviewAudioPresentation.Playing))
        assertEquals(ResourceCopy(R.string.legacy_card_audio_generating_control, listOf("Sentence")), reviewAudioControlLabel("Sentence", ReviewAudioPresentation.Generating))
        assertEquals(ResourceCopy(R.string.legacy_card_audio_prepare_control, listOf("Sentence")), reviewAudioControlLabel("Sentence", ReviewAudioPresentation.NeedsPreparation))
        assertEquals(
            ResourceCopy(R.string.legacy_card_audio_retry_control, listOf("Word")),
            reviewAudioControlLabel("Word", ReviewAudioPresentation.Failed("Network timeout")),
        )
    }
}
