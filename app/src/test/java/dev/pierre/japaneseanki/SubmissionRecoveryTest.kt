package dev.pierre.japaneseanki

import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertThrows
import org.junit.runner.RunWith

import java.io.File
import java.nio.file.Files

@RunWith(AndroidJUnit4::class)
class SubmissionRecoveryTest {
    @Test
    fun restoredDraftTagsPreferLastSentTagsBeforeSettingsDefaults() {
        assertEquals("last note", initialDraftTags(null, "last note"))
        assertEquals("saved draft", initialDraftTags("saved draft", "last note"))
        assertEquals("", initialDraftTags("", "last note"))
        assertEquals("", initialDraftTags(null, null))
    }

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
        val identity = MediaIdentity("/tmp/draft/image.webp", 42L, 7L)
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
    fun noteInsertedRecoveryIsReadOnlyAndNeverClaimsAnotherDeckIsComplete() {
        assertEquals(
            NoteInsertedRecoveryDecision.NEEDS_REVIEW,
            noteInsertedRecoveryDecision(123L, listOf(AnkiCardLocation(0, 1L)), 2L),
        )
        assertEquals(
            NoteInsertedRecoveryDecision.COMPLETE,
            noteInsertedRecoveryDecision(123L, listOf(AnkiCardLocation(0, 2L)), 2L),
        )
        assertEquals(
            NoteInsertedRecoveryDecision.INDETERMINATE,
            noteInsertedRecoveryDecision(123L, emptyList(), 2L),
        )
        assertEquals(
            NoteInsertedRecoveryDecision.FAIL,
            noteInsertedRecoveryDecision(123L, listOf(AnkiCardLocation(0, 1L)), 0L),
        )
        assertEquals(
            NoteInsertedRecoveryDecision.INDETERMINATE,
            noteInsertedRecoveryDecision(null, listOf(AnkiCardLocation(0, 2L)), 2L),
        )
    }

    @Test
    fun frozenAudioIdentitiesReceiptsAndOmissionsRoundTripIndependently() {
        val preferences = MemoryPreferences()
        val snapshot = FrozenSubmission(
            revision = 2L, modelId = 3L, deckId = 4L, modelFields = listOf("Sentence"),
            orderedFields = listOf("日本語"), sentence = "日本語", lemma = "日本語",
            vocabularyReading = "にほんご", sentenceReading = "にほんご", definition = "Japanese",
            translation = "Japanese", notes = "", tags = emptySet(), pitchAccentText = "",
            targetStart = 0, targetEnd = 3, media = MediaIdentity("/tmp/image.webp", 10L, 11L),
            importedFilename = "image.webp", noteId = null,
            sentenceAudio = FrozenAudioItem(MediaIdentity("/draft/sentence.wav", 20L, 21L), "sentence.wav"),
            wordAudio = FrozenAudioItem(MediaIdentity("/draft/word.ogg", 30L, 31L), omitted = true),
        )
        preferences.update(PreferenceChanges().putFrozenSubmission(snapshot, SubmissionPhase.MEDIA_IMPORTED))
        val restored = readFrozenSubmission(preferences.values)
        assertEquals(snapshot.sentenceAudio, restored?.sentenceAudio)
        assertEquals(snapshot.wordAudio, restored?.wordAudio)
        assertEquals(snapshot.media, restored?.media)
        assertEquals(snapshot.importedFilename, restored?.importedFilename)
    }

    @Test
    fun importedAudioRequiresCompleteIdentityWhenPersisted() {
        val preferences = MemoryPreferences()
        val snapshot = FrozenSubmission(
            revision = 1L, modelId = 3L, deckId = 4L, modelFields = listOf("Sentence"),
            orderedFields = listOf("日本語"), sentence = "日本語", lemma = "日本語",
            vocabularyReading = "", sentenceReading = "", definition = "", translation = "",
            notes = "", tags = emptySet(), pitchAccentText = "", targetStart = 0, targetEnd = 3,
            media = null, importedFilename = null, noteId = null,
            sentenceAudio = FrozenAudioItem(null, "sentence.wav"),
        )
        assertThrows(IllegalArgumentException::class.java) {
            PreferenceChanges().putFrozenSubmission(snapshot, SubmissionPhase.MEDIA_IMPORTED)
        }
    }

    @Test
    fun restoredMalformedAudioReceiptsAreRejectedForReuse() {
        val preferences = MemoryPreferences()
        val snapshot = FrozenSubmission(
            revision = 1L, modelId = 3L, deckId = 4L, modelFields = listOf("Sentence"),
            orderedFields = listOf("日本語"), sentence = "日本語", lemma = "日本語",
            vocabularyReading = "", sentenceReading = "", definition = "", translation = "",
            notes = "", tags = emptySet(), pitchAccentText = "", targetStart = 0, targetEnd = 3,
            media = null, importedFilename = null, noteId = null,
        )
        preferences.update(PreferenceChanges().putFrozenSubmission(snapshot, SubmissionPhase.MEDIA_IMPORTED)
            .putString(SubmissionRecoveryKeys.SENTENCE_AUDIO_IMPORTED_FILENAME, "sentence.wav")
            .putString(SubmissionRecoveryKeys.SENTENCE_AUDIO_PATH, "/tmp/source.wav")
            .putLong(SubmissionRecoveryKeys.SENTENCE_AUDIO_SIZE, -1L)
            .putLong(SubmissionRecoveryKeys.SENTENCE_AUDIO_MODIFIED, 1L))
        val receipt = readFrozenSubmission(preferences.values)!!.sentenceAudio
        assertEquals(AudioReceiptValidation.INVALID_RECEIPT, validateAudioReceipt(receipt, File("/tmp/source.wav")))
    }

    @Test
    fun audioReceiptValidationRejectsMissingAndChangedSourcesAndAcceptsExactRoundTrip() {
        val directory = createTempDir(prefix = "audio-receipt-")
        try {
            val source = File(directory, "source.wav").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val identity = MediaIdentity.fromFile(source)
            val validReceipt = FrozenAudioItem(identity, "source.wav")
            assertEquals(AudioReceiptValidation.VALID, validateAudioReceipt(validReceipt, source))
            assertEquals(AudioReceiptValidation.SOURCE_MISSING, validateAudioReceipt(validReceipt, File(directory, "missing.wav")))
            source.writeBytes(byteArrayOf(1, 2, 3, 4))
            assertEquals(AudioReceiptValidation.SOURCE_CHANGED, validateAudioReceipt(validReceipt, source))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun legacyFrozenImageSubmissionRestoresWithAudioOmittedAsOptional() {
        val preferences = MemoryPreferences()
        val legacy = FrozenSubmission(
            revision = 1L, modelId = 3L, deckId = 4L, modelFields = listOf("Sentence"),
            orderedFields = listOf("日本語"), sentence = "日本語", lemma = "日本語",
            vocabularyReading = "", sentenceReading = "", definition = "", translation = "",
            notes = "", tags = emptySet(), pitchAccentText = "", targetStart = 0, targetEnd = 3,
            media = MediaIdentity("/tmp/image.webp", 10L, 11L), importedFilename = "image.webp", noteId = null,
        )
        preferences.update(PreferenceChanges().putFrozenSubmission(legacy, SubmissionPhase.MEDIA_IMPORTED))
        val restored = readFrozenSubmission(preferences.values)!!
        assertEquals(legacy.media, restored.media)
        assertEquals("image.webp", restored.importedFilename)
        assertEquals(FrozenAudioItem(null), restored.sentenceAudio)
        assertEquals(FrozenAudioItem(null), restored.wordAudio)
    }

    @Test
    fun liveReceiptMatchesOnlyTheExactSavedIdentityAndFilename() {
        val saved = MediaIdentity("/tmp/draft/image-1.webp", 4096L, 1_700_000_000L)
        assertEquals(
            "image-1.webp",
            matchImportedReceipt(saved, "image-1.webp", saved.copy(), AnkiMediaImporter::sanitizeFilename),
        )
        // Every component of the identity is load-bearing: each single-field change must miss.
        assertNull(matchImportedReceipt(saved, "image-1.webp", saved.copy(path = "/tmp/draft/other.webp"), AnkiMediaImporter::sanitizeFilename))
        assertNull(matchImportedReceipt(saved, "image-1.webp", saved.copy(size = 4097L), AnkiMediaImporter::sanitizeFilename))
        assertNull(matchImportedReceipt(saved, "image-1.webp", saved.copy(modified = saved.modified + 1), AnkiMediaImporter::sanitizeFilename))
    }

    @Test
    fun liveReceiptRequiresBothIdentitiesAndASafeFilename() {
        val identity = MediaIdentity("/tmp/draft/clip.wav", 12L, 34L)
        val sanitize = AnkiMediaImporter::sanitizeAudioFilename
        assertNull(matchImportedReceipt(null, "clip.wav", identity, sanitize))
        assertNull(matchImportedReceipt(identity, "clip.wav", null, sanitize))
        assertNull(matchImportedReceipt(null, null, null, sanitize))
        assertNull(matchImportedReceipt(identity, null, identity, sanitize))
        assertEquals("clip.wav", matchImportedReceipt(identity, "clip.wav", identity, sanitize))
        // Unsafe and wrong-extension names never survive the slot's own sanitizer.
        assertNull(matchImportedReceipt(identity, "../clip.wav", identity, sanitize))
        assertNull(matchImportedReceipt(identity, ".hidden.wav", identity, sanitize))
        assertNull(matchImportedReceipt(identity, "clip.mp4", identity, sanitize))
        assertNull(matchImportedReceipt(identity, "clip.wav", identity, AnkiMediaImporter::sanitizeFilename))
        // An incomplete legacy receipt cannot describe real media.
        assertNull(matchImportedReceipt(MediaIdentity("/tmp/draft/clip.wav", -1L, -1L), "clip.wav", identity, sanitize))
    }

    @Test
    fun liveReceiptMatcherNeverTouchesTheFileSystem() {
        val gone = MediaIdentity("/nonexistent-receipt/draft/image-9.webp", 77L, 88L)
        assertEquals(
            "image-9.webp",
            matchImportedReceipt(gone, "image-9.webp", gone, AnkiMediaImporter::sanitizeFilename),
        )
    }

    @Test
    fun receiptPreferencesAdapterReadsItsOwnKeysAndIgnoresLegacyDefaults() {
        val preferences = MemoryPreferences()
        val keys = ImportedReceiptKeys("image_path", "image_filename", "image_size", "image_modified")
        val identity = MediaIdentity("/tmp/draft/image-2.webp", 512L, 99L)
        assertNull(importedReceiptFromPreferences(preferences.values, keys, identity, AnkiMediaImporter::sanitizeFilename))
        preferences.update(PreferenceChanges()
            .putString(keys.path, identity.path)
            .putString(keys.filename, "image-2.webp")
            .putLong(keys.size, identity.size)
            .putLong(keys.modified, identity.modified))
        assertEquals(
            "image-2.webp",
            importedReceiptFromPreferences(preferences.values, keys, identity, AnkiMediaImporter::sanitizeFilename),
        )
        // The audio slot's prefix is a different receipt entirely.
        val audioKeys = ImportedReceiptKeys("audio_path", "audio_filename", "audio_size", "audio_modified")
        assertNull(importedReceiptFromPreferences(preferences.values, audioKeys, identity, AnkiMediaImporter::sanitizeFilename))
    }

    @Test
    fun receiptPreferencesAdapterDoesNotStatWhenTheCallerSuppliesIdentity() {
        val preferences = MemoryPreferences()
        val keys = ImportedReceiptKeys("image_path", "image_filename", "image_size", "image_modified")
        val missing = MediaIdentity("/nonexistent-receipt/image-3.webp", 55L, 66L)
        preferences.update(PreferenceChanges()
            .putString(keys.path, missing.path)
            .putString(keys.filename, "image-3.webp")
            .putLong(keys.size, missing.size)
            .putLong(keys.modified, missing.modified))
        // The identity is the caller's: nothing stats the nonexistent path.
        assertEquals(
            "image-3.webp",
            importedReceiptFromPreferences(preferences.values, keys, missing, AnkiMediaImporter::sanitizeFilename),
        )
        // Without an identity there is no hidden fallback stat; the receipt simply does not match.
        assertNull(importedReceiptFromPreferences(preferences.values, keys, null, AnkiMediaImporter::sanitizeFilename))
    }

    @Test
    fun liveMediaIdentityIsNullForMissingNonFileAndUnstatablePaths() {
        val directory = Files.createTempDirectory("media-identity-").toFile()
        try {
            val file = File(directory, "present.webp").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            assertNull(liveMediaIdentity(null))
            assertNull(liveMediaIdentity(File(directory, "missing.webp")))
            assertNull(liveMediaIdentity(directory))
            val loop = File(directory, "loop.webp")
            Files.createSymbolicLink(loop.toPath(), loop.toPath())
            assertNull(liveMediaIdentity(loop))
            assertEquals(MediaIdentity.fromFile(file), liveMediaIdentity(file))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun frozenImageReceiptToleratesADeletedExportButNotAChangedSource() {
        val frozen = MediaIdentity("/tmp/draft/frozen.webp", 900L, 1_600_000_000L)
        // Already imported with no image at all: nothing to verify.
        assertTrue(frozenImagePermitsImport(null, null, null, null, false, null))
        // Deleted local export: the import already lives in AnkiDroid.
        assertTrue(frozenImagePermitsImport("frozen.webp", frozen, frozen, "frozen.webp", false, null))
        // Present and identical: still fine.
        assertTrue(frozenImagePermitsImport("frozen.webp", frozen, frozen, "frozen.webp", true, frozen))
        // Present but re-encoded by a new export: rejected.
        assertFalse(frozenImagePermitsImport("frozen.webp", frozen, frozen, "frozen.webp", true, frozen.copy(size = 901L)))
        assertFalse(frozenImagePermitsImport("frozen.webp", frozen, frozen, "frozen.webp", true, frozen.copy(modified = 1L)))
        // Receipt no longer describes the frozen media: rejected even when the file is gone.
        assertFalse(frozenImagePermitsImport("frozen.webp", frozen, frozen.copy(path = "/tmp/draft/other.webp"), "frozen.webp", false, null))
        assertFalse(frozenImagePermitsImport("frozen.webp", frozen, frozen, "other.webp", false, null))
        assertFalse(frozenImagePermitsImport("frozen.webp", null, null, "frozen.webp", false, null))
        assertFalse(frozenImagePermitsImport("frozen.webp", MediaIdentity("", -1L, -1L), frozen, "frozen.webp", true, frozen))
    }

    @Test
    fun audioReceiptRecoveryKeepsItsOwnClassifications() {
        val directory = Files.createTempDirectory("audio-recovery-").toFile()
        try {
            val source = File(directory, "source.ogg").apply { writeBytes(byteArrayOf(7, 8, 9)) }
            val identity = MediaIdentity.fromFile(source)
            assertEquals(AudioReceiptValidation.NO_RECEIPT, validateAudioReceipt(FrozenAudioItem(identity, null), source))
            assertEquals(AudioReceiptValidation.VALID, validateAudioReceipt(FrozenAudioItem(identity, "source.ogg"), source))
            assertEquals(
                AudioReceiptValidation.INVALID_RECEIPT,
                validateAudioReceipt(FrozenAudioItem(identity, "../source.ogg"), source),
            )
            assertEquals(
                AudioReceiptValidation.INVALID_RECEIPT,
                validateAudioReceipt(FrozenAudioItem(identity, "source.mp4"), source),
            )
            assertEquals(
                AudioReceiptValidation.SOURCE_MISSING,
                validateAudioReceipt(FrozenAudioItem(identity, "source.ogg"), File(directory, "gone.ogg")),
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    private class MemoryPreferences {
        var values = PreferenceValues()
            private set
        fun update(changes: PreferenceChanges) {
            values = values.changed(changes)
        }
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
