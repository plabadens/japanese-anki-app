package dev.pierre.japaneseanki

import dev.pierre.japaneseanki.card.CardSendBlockerKind
import dev.pierre.japaneseanki.speech.CloudVoice
import org.junit.Assert.*
import org.junit.Test

class CardDecisionTest {
    @Test fun preparedCardCanSendWithoutAFreshVoiceCatalog() {
        val ready = ready()
        assertEquals(CardPrimary.Send, ready.decision.action)
        // Reopening a prepared card must not require a new network voice list; the saved name and
        // the already-validated audio file are enough.
        assertEquals(CardPrimary.Send, ready.copy(configuration = ready.configuration.copy(
            audio = ready.configuration.audio.copy(voice = null),
        )).decision.action)
    }
    @Test fun audioFromAnotherSentenceOrEntryNeverMakesTheCurrentCardReady() {
        val ready = ready()
        val stale = DraftAudioState.Ready("wrong input", DraftAudioFile("/fixture", "mp3", 1, 1))
        for (item in AudioItem.entries) {
            val state = ready.copy(audio = ready.audio!!.copy(items = ready.audio.items + (item to stale)))
            assertNotEquals(CardPrimary.Send, state.decision.action)
            assertNull(state.audioState(item))
        }
    }
    @Test fun everyLiveBlockerDrivesItsOwnActionInsteadOfAnUnrelatedRetry() {
        val ready = ready()
        assertEquals(CardSendBlockerKind.Target, ready.copy(record = ready.record.copy(draft = ready.record.draft.withTarget(null))).decision.blocker?.kind)
        assertEquals(CardPrimary.Settings, ready.copy(connection = ready.connection.copy(permission = false)).decision.action)
        assertEquals(CardPrimary.None, ready.copy(meaning = null).decision.action)
        assertEquals(CardPrimary.Translation, ready.copy(record = ready.record.copy(translation = TranslationDraft("猫", TranslationResult.Failed("localized")))).decision.action)
        assertEquals(CardPrimary.Settings, ready.copy(record = ready.record.copy(translation = TranslationDraft("猫", TranslationResult.Failed("localized", TranslatorFailure.AUTHORIZATION)))).decision.action)
    }
    @Test fun frozenRetryUsesItsOwnDestinationNotChangedDefaultsOrLivePreparation() {
        val ready = ready()
        val note = ready.note()
        val frozen = FrozenSubmission(1, 100, 200, AnkiFieldContract.requiredFields,
            AnkiFieldContract.requiredFields.map { note.fieldsByName()!![it].orEmpty() }, "猫", "猫", "ねこ", "ねこ", "cat", "A cat.", "", emptySet(), "0", 0, 1, null, null, null)
        val state = ready.copy(
            record = DraftRecord(), meaning = null, tokens = emptyList(), audio = null,
            configuration = SavedConfiguration(SettingsValues(modelId = 999, deckId = 888)),
            submission = SubmissionUi.Frozen(SubmissionRecord(SubmissionPhase.MEDIA_IMPORTED, frozen, null), retryReady = true),
        )
        assertEquals(CardPrimary.RetrySend, state.decision.action)
        assertFalse(state.editingEnabled)
        assertNull(state.decision.blocker)
        assertEquals(100L, state.model?.id)
    }
    @Test fun pendingWritesAndChecksCannotOfferLiveDraftActions() {
        val ready = ready()
        for (submission in listOf(SubmissionUi.Sending, SubmissionUi.Checking(SubmissionRecord(SubmissionPhase.NOTE_INSERTING, null, null)),
            SubmissionUi.Frozen(SubmissionRecord(SubmissionPhase.NOTE_INSERTING, null, null)))) {
            val state = ready.copy(submission = submission)
            assertEquals(CardPrimary.None, state.decision.action)
            assertFalse(state.editingEnabled)
        }
    }
    private fun ready(): CardUiState {
        val entry = DictionaryEntry(1, "猫", "ねこ", listOf(DictionarySense(listOf("cat"), emptyList(), emptyList())))
        val voice = CloudVoice("ja-JP-Chirp3-HD-Aoede", "Aoede", listOf("ja-JP"), 24_000)
        val draft = DraftModel("猫", TargetSpan(0, 1), "猫", "ねこ", "cat", "0", "ねこ")
        val sentenceIdentity = DraftAudioIdentity.sentenceRequest("猫", voice.name)
        val wordIdentity = DraftAudioIdentity.word(1, "猫", "ねこ")
        val file = DraftAudioFile("/fixture", "mp3", 1, 1)
        return CardUiState(
            initialized = true, record = DraftRecord(draft = draft, translation = TranslationDraft("猫", TranslationResult.Ready("A cat.", TranslationSource.GOOGLE)), activeSentence = "猫"),
            local = LocalDictionary(true), localLoading = false, meaning = DictionaryMeaningChoice(entry, 0, "cat"),
            configuration = SavedConfiguration(SettingsValues(100, 200, voiceName = voice.name), AudioConfiguration(voice, true), true),
            connection = AnkiConnection(true, true, listOf(AnkiNoteType(100, "Sentence", AnkiFieldContract.requiredFields)), listOf(AnkiDeck(200, "Japanese"))),
            audio = DraftAudioSnapshot(1, mapOf(AudioItem.SENTENCE to DraftAudioState.Ready(sentenceIdentity, file), AudioItem.VOCABULARY to DraftAudioState.Ready(wordIdentity, file)), null),
        )
    }
}
