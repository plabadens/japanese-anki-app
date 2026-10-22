package dev.pierre.japaneseanki

import org.junit.Assert.assertEquals
import org.junit.Test

class SubmissionRecoveryDecisionTest {
    @Test
    fun mismatchedDestinationNeedsReviewAndOnlyVerifiedDestinationCompletes() {
        assertEquals(
            NoteInsertedRecoveryDecision.NEEDS_REVIEW,
            noteInsertedRecoveryDecision(123L, listOf(AnkiCardLocation(0, 1L)), 2L),
        )
        assertEquals(
            NoteInsertedRecoveryDecision.COMPLETE,
            noteInsertedRecoveryDecision(123L, listOf(AnkiCardLocation(0, 2L)), 2L),
        )
        assertEquals(
            NoteInsertedRecoveryDecision.NEEDS_REVIEW,
            noteInsertedRecoveryDecision(
                123L,
                listOf(AnkiCardLocation(0, 2L), AnkiCardLocation(1, 3L)),
                2L,
            ),
        )
    }

    @Test
    fun missingOrMalformedRecoveryEvidenceNeverCompletes() {
        assertEquals(
            NoteInsertedRecoveryDecision.INDETERMINATE,
            noteInsertedRecoveryDecision(null, listOf(AnkiCardLocation(0, 2L)), 2L),
        )
        assertEquals(
            NoteInsertedRecoveryDecision.INDETERMINATE,
            noteInsertedRecoveryDecision(123L, emptyList(), 2L),
        )
        assertEquals(
            NoteInsertedRecoveryDecision.FAIL,
            noteInsertedRecoveryDecision(123L, listOf(AnkiCardLocation(-1, 2L)), 2L),
        )
        assertEquals(
            NoteInsertedRecoveryDecision.FAIL,
            noteInsertedRecoveryDecision(123L, listOf(AnkiCardLocation(0, 0L)), 2L),
        )
    }
}
