package dev.pierre.japaneseanki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.Assert.assertThrows

class SubmissionRecoveryTest {
    @Test
    fun encodedFieldsRoundTripIncludingEmptyAndMarkupValues() {
        val values = listOf("", "<b>日本語</b>", "line one\nline two", "& | .")
        assertEquals(values, decodeSubmissionValues(encodeSubmissionValues(values)))
    }

    @Test
    fun invalidEncodedFieldsDoNotBecomeAResumableSnapshot() {
        assertNull(decodeSubmissionValues("not-valid-base64%%%"))
    }

    @Test
    fun tagsAndMediaIdentityRemainStableForFrozenRetries() {
        val tags = setOf("JLPT::N4", "mine")
        assertEquals(tags, decodeSubmissionValues(encodeSubmissionValues(tags.toList().sorted()))?.toSet())
        val identity = MediaIdentity("/tmp/draft/image.avif", 42L, 7L)
        assertEquals(identity, identity.copy())
    }

    @Test
    fun lateDraftCallbacksAreRejectedAfterSentenceEditOrSendLock() {
        assertEquals(true, draftCallbackIsCurrent(4L, 4L, false))
        assertEquals(false, draftCallbackIsCurrent(4L, 5L, false))
        assertEquals(false, draftCallbackIsCurrent(4L, 4L, true))
        assertEquals(true, ocrResultIsCurrent(4L, 4L, 8L, 8L, false))
        assertEquals(false, ocrResultIsCurrent(4L, 4L, 8L, 9L, false))
    }

    @Test
    fun consumedIntentPayloadMustStillMatchBeforeClearingRawIntent() {
        assertEquals(true, intentPayloadMatches("text", "content://old", "text", "content://old"))
        assertEquals(false, intentPayloadMatches("new text", "content://new", "text", "content://old"))
    }

    @Test
    fun newSubmissionRevisionRemainsMonotonicAfterPendingStateIsCleared() {
        assertEquals(1L, nextSubmissionRevision(0L, 0L))
        assertEquals(5L, nextSubmissionRevision(4L, 1L))
        assertEquals(8L, nextSubmissionRevision(2L, 7L))
    }

    @Test
    fun insertProviderOutcomesDistinguishNullFromThrownCalls() {
        assertThrows(AnkiProviderDefiniteInsertException::class.java) {
            requireReturnedNoteUri(null)
        }
        val cause = IllegalStateException("provider unavailable")
        val uncertain = AnkiProviderUncertainException("uncertain", cause)
        assertEquals(cause, uncertain.cause)
    }

    @Test
    fun insertedNoteIdAndBrowserSearchAreEncodedFromProviderValues() {
        assertEquals(123L, parseInsertedNoteId("content://com.ichi2.anki.flashcards/notes/123"))
        assertEquals(123L, parseInsertedNoteId("content://com.ichi2.anki.flashcards/notes/123?source=insert"))
        listOf("123", "other/123", "content://other/notes/123", "content://com.ichi2.anki.flashcards/notes/0").forEach { malformed ->
            assertThrows(IllegalArgumentException::class.java) { parseInsertedNoteId(malformed) }
        }
        assertEquals("anki://x-callback-url/browser?search=nid%3A123", ankiBrowserUriText(123L))
    }

    @Test
    fun malformedCardValuesAndUpdateCountsFailClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            requireCardProjection(-1, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            requireCardProjection(1, -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateCardLocation(-1, 2L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateCardLocation(0, 0L)
        }
        assertThrows(IllegalArgumentException::class.java) { requireCardMoveCount(0) }
        assertThrows(IllegalArgumentException::class.java) { requireCardMoveCount(2) }
        requireCardMoveCount(1)
    }

    @Test
    fun noteInsertedRecoveryChoosesMoveCompleteResolveOrFail() {
        assertEquals(
            NoteInsertedRecoveryDecision.MOVE,
            noteInsertedRecoveryDecision(123L, listOf(AnkiCardLocation(0, 1L)), 2L),
        )
        assertEquals(
            NoteInsertedRecoveryDecision.COMPLETE,
            noteInsertedRecoveryDecision(123L, listOf(AnkiCardLocation(0, 2L)), 2L),
        )
        assertEquals(
            NoteInsertedRecoveryDecision.RESOLVE,
            noteInsertedRecoveryDecision(123L, emptyList(), 2L),
        )
        assertEquals(
            NoteInsertedRecoveryDecision.FAIL,
            noteInsertedRecoveryDecision(123L, listOf(AnkiCardLocation(0, 1L)), 0L),
        )
    }

    @Test
    fun phasesMakeMediaImportUncertaintyDistinctFromNoteInsertion() {
        assertEquals("media_importing", SubmissionPhase.MEDIA_IMPORTING.persisted)
        assertEquals("media_imported", SubmissionPhase.MEDIA_IMPORTED.persisted)
        assertEquals("started", SubmissionPhase.NOTE_INSERTING.persisted)
        assertEquals("note_inserted", SubmissionPhase.NOTE_INSERTED.persisted)
        assertEquals("destination_verified", SubmissionPhase.DESTINATION_VERIFIED.persisted)
        assertEquals(
            listOf(
                SubmissionPhase.MEDIA_IMPORTING,
                SubmissionPhase.MEDIA_IMPORTED,
                SubmissionPhase.NOTE_INSERTING,
                SubmissionPhase.NOTE_INSERTED,
                SubmissionPhase.DESTINATION_VERIFIED,
            ),
            SubmissionPhase.entries,
        )
        assertEquals(SubmissionPhase.MEDIA_IMPORTING, SubmissionPhase.fromPersisted("media_importing"))
    }
}
