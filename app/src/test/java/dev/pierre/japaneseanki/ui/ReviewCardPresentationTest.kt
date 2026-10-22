package dev.pierre.japaneseanki.ui

import androidx.compose.ui.unit.dp
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
        assertEquals("Edit preparation", ReviewPreparationActionLabel)
    }

    @Test fun audioControlsUsePlayStopAndDistinguishPreparationFromFailure() {
        assertTrue(ReviewAudioControlSize >= 48.dp)
        assertEquals("Play Sentence audio", reviewAudioControlLabel("Sentence", ReviewAudioPresentation.Ready))
        assertEquals("Stop Word audio", reviewAudioControlLabel("Word", ReviewAudioPresentation.Playing))
        assertEquals("Generating Sentence audio", reviewAudioControlLabel("Sentence", ReviewAudioPresentation.Generating))
        assertEquals("Prepare Sentence audio", reviewAudioControlLabel("Sentence", ReviewAudioPresentation.NeedsPreparation))
        assertEquals(
            "Retry Word audio",
            reviewAudioControlLabel("Word", ReviewAudioPresentation.Failed("Network timeout")),
        )
    }
}
