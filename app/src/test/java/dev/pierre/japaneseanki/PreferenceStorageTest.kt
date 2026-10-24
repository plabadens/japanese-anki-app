package dev.pierre.japaneseanki

import dev.pierre.japaneseanki.speech.CloudVoice
import dev.pierre.japaneseanki.speech.encodeVoices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferenceStorageTest {
    @Test
    fun delayedOlderDraftWriteCannotResurrectTextAfterNewerDraft() {
        val storage = MemoryAppPreferenceStorage(deferUpdates = true)

        writeDraft(storage, "older sentence")
        writeDraft(storage, "newer sentence")
        storage.flushQueuedUpdates()

        assertEquals("newer sentence", storage.durable(PreferenceFile.DRAFT).getString("sentence", ""))
    }

    @Test
    fun voiceCacheSaveQueuesWithoutWaitingForDisk() {
        val storage = MemoryAppPreferenceStorage(deferUpdates = true)
        val voices = listOf(CloudVoice("ja-JP-Test", "Japanese Test", listOf("ja-JP"), 24_000))
        val persistence = PreferenceBackedSettingsPersistence(storage)

        assertTrue(persistence.saveVoiceCache(voices))
        assertEquals(
            encodeVoices(voices),
            storage.settings.value.getString(SettingsPreferenceKeys.VOICE_CACHE, null),
        )
        assertNull(storage.durable(PreferenceFile.SETTINGS).getString(SettingsPreferenceKeys.VOICE_CACHE, null))
    }

    @Test
    fun failedPreferenceReadKeepsNamedDraftAndRecoveryDefaults() = runSuspendTest {
        val unreadableDraft = PreferenceValues().changed(
            PreferenceChanges()
                .putString("sentence", "must not leak through a failed read")
                .putString("translation", "stale translation")
                .putString(SubmissionRecoveryKeys.STATUS, SubmissionPhase.NOTE_INSERTING.persisted),
        )
        val storage = MemoryAppPreferenceStorage(initialDraft = unreadableDraft, failReads = true)
        storage.load()

        val restoredTranslation = restoreTranslationDraft(storage.draft.value)
        val restoredDraft = restoreDraftModel(storage.draft.value, sentence = "", defaultTags = "")
        assertEquals("", restoredDraft.sentence)
        assertEquals("", restoredDraft.vocabulary)
        assertEquals("", restoredDraft.tags)
        assertTrue(restoredTranslation.draft.result is TranslationResult.Pending)
        assertNull(restoredTranslation.attemptedSentence)
        assertNull(readFrozenSubmission(storage.draft.value))
    }

    private fun writeDraft(storage: AppPreferenceStorage, sentence: String) {
        val translationDraft = TranslationDraft(sentence, TranslationResult.Pending)
        persistDraftPreferences(
            storage = storage,
            selectionStart = 0,
            selectionEnd = 0,
            draft = DraftModel(sentence = sentence),
            pitchHtmlText = null,
            definitionManuallyEdited = false,
            dependentFieldsStale = false,
            translation = "",
            translationDraft = translationDraft,
            activeTranslationResult = TranslationResult.Pending,
            translationAttemptedSentence = null,
            cardInvalidated = false,
            selectedModelId = null,
            selectedDeckId = null,
            selectedDictionaryEntryId = null,
            selectedSenseIndices = emptySet(),
        )
    }
}
