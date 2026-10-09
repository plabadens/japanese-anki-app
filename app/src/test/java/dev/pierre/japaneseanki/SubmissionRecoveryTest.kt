package dev.pierre.japaneseanki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.Assert.assertThrows
import android.content.SharedPreferences
import java.io.File

class SubmissionRecoveryTest {
    @Test
    fun restoredDraftTagsPreferLastSentTagsBeforeSettingsDefaults() {
        assertEquals("last note", initialDraftTags(null, "last note", "settings default"))
        assertEquals("saved draft", initialDraftTags("saved draft", "last note", "settings default"))
        assertEquals("", initialDraftTags("", "last note", "settings default"))
        assertEquals("settings default", initialDraftTags(null, null, "settings default"))
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
    fun frozenAudioIdentitiesReceiptsAndOmissionsRoundTripIndependently() {
        val preferences = MemoryPreferences()
        val snapshot = FrozenSubmission(
            revision = 2L, modelId = 3L, deckId = 4L, modelFields = listOf("Sentence"),
            orderedFields = listOf("日本語"), sentence = "日本語", lemma = "日本語",
            vocabularyReading = "にほんご", sentenceReading = "にほんご", definition = "Japanese",
            translation = "Japanese", notes = "", tags = emptySet(), pitchAccentText = "",
            targetStart = 0, targetEnd = 3, media = MediaIdentity("/tmp/image.avif", 10L, 11L),
            importedFilename = "image.avif", noteId = null,
            sentenceAudio = FrozenAudioItem(MediaIdentity("/draft/sentence.wav", 20L, 21L), "sentence.wav"),
            wordAudio = FrozenAudioItem(MediaIdentity("/draft/word.ogg", 30L, 31L), omitted = true),
        )
        assertEquals(true, preferences.edit().putFrozenSubmission(snapshot, SubmissionPhase.MEDIA_IMPORTED).commit())
        val restored = readFrozenSubmission(preferences)
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
            preferences.edit().putFrozenSubmission(snapshot, SubmissionPhase.MEDIA_IMPORTED)
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
        preferences.edit().putFrozenSubmission(snapshot, SubmissionPhase.MEDIA_IMPORTED)
            .putString(SubmissionRecoveryKeys.SENTENCE_AUDIO_IMPORTED_FILENAME, "sentence.wav")
            .putString(SubmissionRecoveryKeys.SENTENCE_AUDIO_PATH, "/tmp/source.wav")
            .putLong(SubmissionRecoveryKeys.SENTENCE_AUDIO_SIZE, -1L)
            .putLong(SubmissionRecoveryKeys.SENTENCE_AUDIO_MODIFIED, 1L).commit()
        val receipt = readFrozenSubmission(preferences)!!.sentenceAudio
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
            media = MediaIdentity("/tmp/image.avif", 10L, 11L), importedFilename = "image.avif", noteId = null,
        )
        preferences.edit().putFrozenSubmission(legacy, SubmissionPhase.MEDIA_IMPORTED).commit()
        val restored = readFrozenSubmission(preferences)!!
        assertEquals(legacy.media, restored.media)
        assertEquals("image.avif", restored.importedFilename)
        assertEquals(FrozenAudioItem(null), restored.sentenceAudio)
        assertEquals(FrozenAudioItem(null), restored.wordAudio)
    }

    private class MemoryPreferences : SharedPreferences {
        private val values = mutableMapOf<String, Any>()
        override fun getAll(): MutableMap<String, *> = values.toMutableMap()
        override fun getString(key: String, defValue: String?): String? = values[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
            (values[key] as? Set<String>)?.toMutableSet() ?: defValues
        override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
        override fun contains(key: String): Boolean = key in values
        override fun edit(): SharedPreferences.Editor = MemoryEditor()
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        private inner class MemoryEditor : SharedPreferences.Editor {
            private val changes = mutableMapOf<String, Any?>()
            private var clear = false
            override fun putString(key: String, value: String?): SharedPreferences.Editor = set(key, value)
            override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor = set(key, values?.toSet())
            override fun putInt(key: String, value: Int): SharedPreferences.Editor = set(key, value)
            override fun putLong(key: String, value: Long): SharedPreferences.Editor = set(key, value)
            override fun putFloat(key: String, value: Float): SharedPreferences.Editor = set(key, value)
            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = set(key, value)
            override fun remove(key: String): SharedPreferences.Editor = set(key, null)
            override fun clear(): SharedPreferences.Editor { clear = true; return this }
            override fun commit(): Boolean {
                if (clear) values.clear()
                changes.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
                return true
            }
            override fun apply() { commit() }
            private fun set(key: String, value: Any?): SharedPreferences.Editor { changes[key] = value; return this }
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
