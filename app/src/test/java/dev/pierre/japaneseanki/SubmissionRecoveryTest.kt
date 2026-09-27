package dev.pierre.japaneseanki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
    fun phasesMakeMediaImportUncertaintyDistinctFromNoteInsertion() {
        assertEquals("media_importing", SubmissionPhase.MEDIA_IMPORTING.persisted)
        assertEquals("media_imported", SubmissionPhase.MEDIA_IMPORTED.persisted)
        assertEquals("started", SubmissionPhase.NOTE_INSERTING.persisted)
        assertEquals(SubmissionPhase.MEDIA_IMPORTING, SubmissionPhase.fromPersisted("media_importing"))
    }
}
