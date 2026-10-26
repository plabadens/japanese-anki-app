package dev.pierre.japaneseanki

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dev.pierre.japaneseanki.card.CardSendBlocker
import dev.pierre.japaneseanki.card.CardSendBlockerKind
import dev.pierre.japaneseanki.send.SendOutcome
import dev.pierre.japaneseanki.send.SendRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal sealed interface SubmissionUi {
    data class Editing(val failure: String? = null) : SubmissionUi
    data object Sending : SubmissionUi
    data class Duplicate(val noteId: Long) : SubmissionUi
    data class Checking(val record: SubmissionRecord) : SubmissionUi
    data class Frozen(val record: SubmissionRecord, val retryReady: Boolean = false, val noteId: Long? = record.frozen?.noteId) : SubmissionUi
}
internal enum class CardPrimary { None, Analyze, Setup, Settings, Translation, SentenceAudio, WordAudio, Send, RetrySend }
internal data class CardDecision(val action: CardPrimary, val blocker: CardSendBlocker? = null)
internal data class SharedInput(val text: String? = null, val image: String? = null)

internal data class CardUiState(
    val initialized: Boolean = false,
    val record: DraftRecord = DraftRecord(),
    val local: LocalDictionary = LocalDictionary(false),
    val localLoading: Boolean = true,
    val tokens: List<JapaneseToken> = emptyList(),
    val analyzing: Boolean = false,
    val analysisFailed: Boolean = false,
    val meanings: List<DictionaryMeaningChoice> = emptyList(),
    val meaning: DictionaryMeaningChoice? = null,
    val searching: Boolean = false,
    val lookupFailed: Boolean = false,
    val pitchLoading: Boolean = false,
    val audio: DraftAudioSnapshot? = null,
    val configuration: SavedConfiguration = SavedConfiguration(),
    val connection: AnkiConnection = AnkiConnection(),
    val connectionLoading: Boolean = false,
    val submission: SubmissionUi = SubmissionUi.Editing(),
    val persistenceFailed: Boolean = false,
    val imageLoading: Boolean = false,
    val imageFailed: Boolean = false,
    val ocrLoading: Boolean = false,
    val ocrFailed: Boolean = false,
    val ocrChoices: List<OcrBlock> = emptyList(),
    val imageHeight: Int = 0,
    val pendingReplacement: SharedInput? = null,
    val pendingPhoto: String? = null,
    val delivered: Boolean = false,
) {
    val editingEnabled: Boolean get() = submission is SubmissionUi.Editing || submission is SubmissionUi.Duplicate
    val editor: TextFieldValue get() = TextFieldValue(record.draft.sentence, TextRange(record.selectionStart, record.selectionEnd))
    val frozen: FrozenSubmission? get() = when (val current = submission) {
        is SubmissionUi.Frozen -> current.record.frozen
        is SubmissionUi.Checking -> current.record.frozen
        else -> null
    }
    val displayDraft: DraftModel get() = frozen?.let { saved ->
        record.draft.copy(sentence = saved.sentence, target = TargetSpan(saved.targetStart, saved.targetEnd).takeIf { it.textIn(saved.sentence) != null },
            vocabulary = saved.lemma, vocabularyReading = saved.vocabularyReading, sentenceReading = saved.sentenceReading,
            definition = saved.definition, pitchAccent = saved.pitchAccentText, notes = saved.notes, tags = saved.tags.joinToString(" "))
    } ?: record.draft
    val translation: String get() = frozen?.translation ?: (record.translation.result as? TranslationResult.Ready)?.text.orEmpty()
    val pitch: PitchAccent? get() = displayDraft.pitchAccent.toIntOrNull()?.let {
        PitchAccent.create(displayDraft.vocabularyReading, it, record.pitchHtml)
    }
    val model: AnkiNoteType? get() = connection.models.firstOrNull { it.id == (frozen?.modelId ?: configuration.values.modelId) }
    val deck: AnkiDeck? get() = connection.decks.firstOrNull { it.id == (frozen?.deckId ?: configuration.values.deckId) }
    val imagePath: String? get() = frozen?.media?.path ?: record.imagePath
    fun audioState(item: AudioItem): DraftAudioState? {
        if (frozen != null) return audio?.items?.get(item)
        val expected = when (item) {
            AudioItem.SENTENCE -> configuration.values.voiceName.takeIf(String::isNotBlank)?.let { voiceName ->
                DraftAudioIdentity.sentenceRequest(record.draft.sentence, voiceName)
            }
            AudioItem.VOCABULARY -> meaning?.entry?.let { DraftAudioIdentity.word(it.id, it.expression, it.reading) }
        }
        return audio?.items?.get(item)?.takeIf { expected != null && it.identity == expected }
    }
    fun note(): ManualNoteDraft = ManualNoteDraft(
        sentence = record.draft.sentence, target = record.draft.target, sentenceReading = record.draft.sentenceReading,
        vocabulary = record.draft.vocabulary, vocabularyReading = record.draft.vocabularyReading,
        definition = record.draft.definition, dictionaryEntryResolved = meaning != null,
        pitch = pitch, translation = translation, notes = record.draft.notes,
        vocabularyPartOfSpeech = VocabularyPartOfSpeech.forSelectedSenses(meaning?.entry, setOf(record.senseIndex)),
        sentenceFurigana = SentenceReadings.effective(record.draft.sentence, tokens, record.draft.target, record.draft.sentenceReading, record.draft.readingCorrections),
    )

    /** One projection, not independently synchronized flags/action/hint state holders. */
    val decision: CardDecision get() {
        fun blocked(kind: CardSendBlockerKind, action: CardPrimary = CardPrimary.None, detail: String? = null) =
            CardDecision(action, CardSendBlocker(kind, detail))
        if (submission == SubmissionUi.Sending || submission is SubmissionUi.Checking || submission is SubmissionUi.Duplicate) return CardDecision(CardPrimary.None)
        val pending = submission as? SubmissionUi.Frozen
        if (pending != null && !pending.retryReady) return CardDecision(CardPrimary.None)
        if (pending == null && record.draft.target == null) return blocked(CardSendBlockerKind.Target)
        if (pending == null && audioState(AudioItem.SENTENCE) !is DraftAudioState.Ready &&
            (!configuration.audio.credentialStored || configuration.audio.voice == null))
            return blocked(CardSendBlockerKind.AudioSetup, CardPrimary.Setup)
        if (!connection.permission) return blocked(CardSendBlockerKind.Permission, CardPrimary.Settings)
        if (connectionLoading) return blocked(CardSendBlockerKind.SetupLoading)
        if (connection.failed) return blocked(CardSendBlockerKind.SetupNotLoaded, CardPrimary.Settings)
        if (model == null) return blocked(CardSendBlockerKind.Model, CardPrimary.Settings)
        if (deck == null) return blocked(CardSendBlockerKind.Deck, CardPrimary.Settings)
        if (!AnkiFieldContract.mapToModelOrder(model!!.fields, emptyMap()).isValid)
            return blocked(CardSendBlockerKind.Mapping, CardPrimary.Settings)
        if (pending != null) {
            if (pending.record.frozen == null || model!!.fields != pending.record.frozen.modelFields)
                return blocked(CardSendBlockerKind.FrozenDestination, CardPrimary.Settings)
            return CardDecision(CardPrimary.RetrySend)
        }
        if (analysisFailed) return CardDecision(CardPrimary.Analyze)
        if (analyzing || searching) return blocked(CardSendBlockerKind.DictionaryLoading)
        if (meaning == null) return blocked(CardSendBlockerKind.Validation, detail = "Select a dictionary entry.")
        if (record.translation.result is TranslationResult.Failed) {
            val failure = record.translation.result.failure
            return blocked(CardSendBlockerKind.Translation,
                if (failure in listOf(TranslatorFailure.MISSING_CREDENTIALS, TranslatorFailure.AUTHORIZATION)) CardPrimary.Settings else CardPrimary.Translation)
        }
        if (record.translation.result !is TranslationResult.Ready || record.translation.stale)
            return blocked(CardSendBlockerKind.Preparation)
        if (pitch == null || pitchLoading) return blocked(CardSendBlockerKind.Pitch)
        if (imageLoading) return blocked(CardSendBlockerKind.ImagePreparing)
        if (imageFailed || (record.sourcePath != null && record.imagePath == null)) return blocked(CardSendBlockerKind.ImageMissing)
        for ((item, blocker, action) in listOf(
            Triple(AudioItem.SENTENCE, CardSendBlockerKind.SentenceAudio, CardPrimary.SentenceAudio),
            Triple(AudioItem.VOCABULARY, CardSendBlockerKind.WordAudio, CardPrimary.WordAudio),
        )) if (audioState(item) !is DraftAudioState.Ready) {
            val current = audioState(item)
            return blocked(blocker, if (current is DraftAudioState.Generating || (current is DraftAudioState.Failed && !current.retryable)) CardPrimary.None else action)
        }
        if (persistenceFailed) return blocked(CardSendBlockerKind.Fields)
        note().validationErrors().firstOrNull()?.let { return blocked(CardSendBlockerKind.Validation, detail = it) }
        return CardDecision(if ((submission as? SubmissionUi.Editing)?.failure != null) CardPrimary.RetrySend else CardPrimary.Send)
    }
}

/** One flow-scoped owner for the learner's draft; rotation does not restart jobs or reconstruct state. */
internal class CardViewModel(private val repository: DraftRepository, private val settings: SettingsRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(CardUiState())
    val uiState = mutableState.asStateFlow()
    val outputDirectory get() = repository.outputDirectory
    private var analysisJob: Job? = null
    private var sentenceJob: Job? = null
    private var translationJob: Job? = null
    private var wordJob: Job? = null
    private var imageJob: Job? = null
    private var saveJob: Job? = null
    private var connectionJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                val record = repository.restore()
                val pending = repository.submission()
                val configuration = settings.configuration.first()
                mutableState.update { it.copy(initialized = true, record = record, configuration = configuration, audio = repository.audioSnapshot(),
                    submission = if (pending.phase == null) SubmissionUi.Editing() else SubmissionUi.Frozen(pending, pending.phase == SubmissionPhase.MEDIA_IMPORTED)) }
                launch {
                    settings.configuration.collect { configuration ->
                        val changed = uiState.value.configuration.audio != configuration.audio
                        mutableState.update { it.copy(configuration = configuration) }
                        if (changed && uiState.value.record.activeSentence == uiState.value.record.draft.sentence && uiState.value.editingEnabled) prepareSentence()
                    }
                }
                if (pending.phase == null) prepareLocal() else mutableState.update { it.copy(localLoading = false) }
                refreshConnection()
                if (uiState.value.editingEnabled && record.sourcePath != null && record.imagePath == null) processImage(record.sourcePath, false)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.update { it.copy(initialized = true, localLoading = false, persistenceFailed = true) } }
        }
    }

    fun prepareLocal() {
        viewModelScope.launch {
            mutableState.update { it.copy(localLoading = true) }
            val local = repository.prepareDictionary()
            mutableState.update { it.copy(local = local, localLoading = false) }
            if (local.ready && uiState.value.editingEnabled) analyze()
        }
    }
    fun refreshConnection() {
        if (!uiState.value.initialized || connectionJob?.isActive == true) return
        connectionJob = viewModelScope.launch {
            mutableState.update { it.copy(connectionLoading = true) }
            val connection = repository.connection()
            mutableState.update { it.copy(connection = connection, connectionLoading = false) }
            val pending = uiState.value.submission as? SubmissionUi.Frozen
            if (connection.permission && !connection.failed && pending != null && pending.record.phase in listOf(
                    SubmissionPhase.NOTE_INSERTING, SubmissionPhase.NOTE_INSERTED, SubmissionPhase.DESTINATION_VERIFIED)) {
                mutableState.update { it.copy(submission = SubmissionUi.Checking(pending.record)) }
                handleOutcome(repository.recover())
            }
        }
    }

    fun edit(editor: TextFieldValue) {
        if (!uiState.value.editingEnabled) return
        val old = uiState.value.record
        if (editor.text != old.draft.sentence) {
            analysisJob?.cancel(); sentenceJob?.cancel(); translationJob?.cancel(); wordJob?.cancel()
            mutableState.update { it.copy(record = old.copy(draft = old.draft.withSentence(editor.text), selectionStart = editor.selection.start,
                selectionEnd = editor.selection.end, translation = TranslationDraft(editor.text, TranslationResult.Pending), entryId = null, pitchHtml = null),
                meaning = null, meanings = emptyList(), tokens = emptyList(), analyzing = false, searching = false, pitchLoading = false,
                submission = SubmissionUi.Editing(), delivered = false) }
            analyze()
        } else mutableState.update { it.copy(record = it.record.copy(selectionStart = editor.selection.start, selectionEnd = editor.selection.end)) }
        save()
    }

    fun commit(editor: TextFieldValue = uiState.value.editor, target: TargetSpan? = null): Boolean {
        if (!uiState.value.editingEnabled || !uiState.value.local.ready || editor.text.isBlank()) return false
        edit(editor)
        mutableState.update { it.copy(record = it.record.copy(activeSentence = editor.text, route = AppRoute.Card)) }
        if (target != null) selectWord(target)
        prepareSentence()
        save()
        return true
    }
    fun captureMode(mode: String) { mutableState.update { it.copy(record = it.record.copy(captureMode = mode)) }; save() }
    fun route(route: AppRoute) { mutableState.update { it.copy(record = it.record.copy(route = route)) }; save() }
    fun notes(value: String) { if (uiState.value.editingEnabled) { mutableState.update { it.copy(record = it.record.copy(draft = it.record.draft.copy(notes = value))) }; save() } }
    fun tags(value: String) { if (uiState.value.editingEnabled) { mutableState.update { it.copy(record = it.record.copy(draft = it.record.draft.copy(tags = value))) }; save() } }

    fun analyze() {
        analysisJob?.cancel()
        if (!uiState.value.local.ready || !uiState.value.editingEnabled) return
        val sentence = uiState.value.record.draft.sentence
        if (sentence.isBlank()) return
        analysisJob = viewModelScope.launch {
            mutableState.update { it.copy(analyzing = true, analysisFailed = false) }
            try {
                kotlinx.coroutines.delay(150)
                val tokens = repository.analyze(sentence)
                mutableState.update { it.copy(tokens = tokens, analyzing = false) }
                uiState.value.record.draft.target?.let(::lookupWord)
                if (uiState.value.record.activeSentence == sentence) prepareSentence()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.update { it.copy(analyzing = false, analysisFailed = true) } }
        }
    }

    fun selectWord(target: TargetSpan) {
        val state = uiState.value
        if (!state.editingEnabled || target == state.record.draft.target || target.textIn(state.record.draft.sentence) == null) return
        mutableState.update { it.copy(record = it.record.copy(draft = it.record.draft.withTarget(target), entryId = null, pitchHtml = null),
            meaning = null, meanings = emptyList(), submission = SubmissionUi.Editing()) }
        lookupWord(target)
        save()
    }

    private fun lookupWord(target: TargetSpan) {
        wordJob?.cancel()
        wordJob = viewModelScope.launch {
            mutableState.update { it.copy(searching = true, lookupFailed = false) }
            try {
                val record = uiState.value.record
                val meanings = repository.meanings(record.draft.sentence, target, uiState.value.tokens)
                mutableState.update { it.copy(meanings = meanings, searching = false, audio = repository.audioSnapshot()) }
                val restored = meanings.firstOrNull { it.entry.id == record.entryId && it.senseIndex == record.senseIndex }
                val choice = restored ?: meanings.singleOrNull()
                if (choice != null) prepareMeaning(choice)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.update { it.copy(searching = false, lookupFailed = true) } }
        }
    }

    fun chooseMeaning(choice: DictionaryMeaningChoice) {
        if (!uiState.value.editingEnabled) return
        wordJob?.cancel()
        wordJob = viewModelScope.launch { prepareMeaning(choice) }
    }

    private suspend fun prepareMeaning(choice: DictionaryMeaningChoice) {
        val state = uiState.value
        val draft = state.record.draft.withEntry(choice.entry, setOf(choice.senseIndex)).copy(
            sentenceReading = state.record.draft.target?.let { resolveSurfaceReading(state.record.draft.sentence, state.tokens, it) }.orEmpty(),
        )
        mutableState.update { it.copy(meaning = choice, record = it.record.copy(draft = draft, entryId = choice.entry.id, senseIndex = choice.senseIndex), pitchLoading = true) }
        try {
                val patterns = repository.pitch(choice.entry)
                val pattern = patterns.firstOrNull { it.accent.toString() == draft.pitchAccent } ?: patterns.firstOrNull()
                mutableState.update { it.copy(record = it.record.copy(draft = it.record.draft.copy(pitchAccent = pattern?.accent?.toString() ?: draft.pitchAccent), pitchHtml = pattern?.html ?: it.record.pitchHtml), pitchLoading = false) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.update { it.copy(pitchLoading = false) } }
        save()
        generateAudio(AudioItem.VOCABULARY)
    }

    private fun prepareSentence() {
        if (!uiState.value.editingEnabled || !uiState.value.local.ready || uiState.value.analyzing || uiState.value.record.activeSentence != uiState.value.record.draft.sentence) return
        val record = uiState.value.record
        if ((record.translation.result !is TranslationResult.Ready || record.translation.stale) && translationJob?.isActive != true) {
            translationJob = viewModelScope.launch {
                mutableState.update { it.copy(record = it.record.copy(translation = it.record.translation.requestStarted())) }
                val result = repository.translate(record.draft.sentence)
                mutableState.update { it.copy(record = it.record.copy(translation = it.record.translation.requestFinished(result))) }
                save()
            }
        }
        if (sentenceJob?.isActive == true && uiState.value.audioState(AudioItem.SENTENCE) is DraftAudioState.Generating) return
        sentenceJob?.cancel()
        sentenceJob = viewModelScope.launch { generateAudio(AudioItem.SENTENCE) }
    }

    fun retryTranslation() {
        if (!uiState.value.editingEnabled) return
        mutableState.update { it.copy(record = it.record.copy(translation = it.record.translation.requestStarted())) }
        prepareSentence()
    }
    /**
     * The one "do this audio work now" intent. Audio belongs to the committed sentence, so asking
     * for it on an uncommitted draft commits first; a retryable failure is retried; a permanent one
     * is left to its explanation instead of spinning a control that cannot succeed.
     */
    fun requestAudio(item: AudioItem) {
        val state = uiState.value
        if (!state.editingEnabled) return
        if (state.record.activeSentence != state.record.draft.sentence) {
            commit()
            return
        }
        if (!state.local.ready) return
        val current = state.audioState(item)
        if (current is DraftAudioState.Failed && !current.retryable) return
        viewModelScope.launch { generateAudio(item, retry = current is DraftAudioState.Failed) }
    }
    private suspend fun generateAudio(item: AudioItem, retry: Boolean = false) {
        val state = uiState.value
        if (!state.editingEnabled || state.record.activeSentence != state.record.draft.sentence) return
        try {
            fun publish(snapshot: DraftAudioSnapshot) {
                mutableState.update { current ->
                    current.copy(audio = current.audio?.copy(items = current.audio.items + (item to snapshot.items.getValue(item))) ?: snapshot)
                }
            }
            val result = if (item == AudioItem.SENTENCE) {
                val voice = state.configuration.audio.voice ?: return
                if (!state.configuration.audio.credentialStored || state.analyzing) return
                repository.sentenceAudio(state.record, state.tokens, voice, retry, ::publish)
            } else {
                val entry = state.meaning?.entry ?: return
                repository.wordAudio(entry, retry, ::publish)
            }
            publish(result)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { mutableState.update { it.copy(audio = repository.audioSnapshot()) } }
    }

    private fun save() {
        saveJob?.cancel()
        val record = uiState.value.record
        saveJob = viewModelScope.launch {
            val saved = repository.save(record)
            mutableState.update { it.copy(persistenceFailed = !saved) }
        }
    }

    fun send() {
        val state = uiState.value
        if (state.decision.action !in listOf(CardPrimary.Send, CardPrimary.RetrySend)) return
        val model = state.model ?: return
        val deck = state.deck ?: return
        val frozen = state.frozen
        val fields = frozen?.modelFields?.zip(frozen.orderedFields)?.toMap() ?: state.note().fieldsByName() ?: return
        mutableState.update { it.copy(submission = SubmissionUi.Sending) }
        analysisJob?.cancel(); sentenceJob?.cancel(); translationJob?.cancel(); wordJob?.cancel()
        viewModelScope.launch {
            saveJob?.join()
            val request = SendRequest(state.note(), fields, model, deck, frozen?.tags ?: AnkiFieldContract.parseTags(state.record.draft.tags),
                state.record.draft.pitchAccent, state.imagePath != null, state.imageLoading,
                state.imagePath, liveMediaIdentity(state.imagePath?.let { java.io.File(it) }), null, frozen)
            handleOutcome(repository.submit(request))
        }
    }

    private suspend fun handleOutcome(outcome: SendOutcome) {
        when (outcome) {
            is SendOutcome.Created -> {
                val record = repository.clear(uiState.value.configuration.values.defaultTags)
                mutableState.update { CardUiState(initialized = true, record = record, local = it.local, localLoading = false,
                    configuration = it.configuration, connection = it.connection, audio = repository.audioSnapshot(), delivered = true) }
            }
            is SendOutcome.Duplicate -> mutableState.update { it.copy(submission = SubmissionUi.Duplicate(outcome.noteId)) }
            is SendOutcome.InsertFailed -> mutableState.update { it.copy(submission = SubmissionUi.Editing(outcome.message)) }
            SendOutcome.RetryAvailable -> {
                val record = repository.submission()
                mutableState.update { it.copy(submission = SubmissionUi.Frozen(record, retryReady = true)) }
            }
            else -> {
                val record = repository.submission()
                mutableState.update { it.copy(submission = if (record.phase != null) SubmissionUi.Frozen(record) else SubmissionUi.Editing("Could not send the note.")) }
            }
        }
    }

    fun allowFrozenRetry() {
        val pending = uiState.value.submission as? SubmissionUi.Frozen ?: return
        if (pending.record.phase == SubmissionPhase.MEDIA_IMPORTING || pending.record.phase == SubmissionPhase.MEDIA_IMPORTED)
            mutableState.update { it.copy(submission = pending.copy(retryReady = true)) }
    }
    fun discardPending() {
        if (uiState.value.submission == SubmissionUi.Sending || uiState.value.submission is SubmissionUi.Checking) return
        viewModelScope.launch {
            val old = uiState.value.record
            val snapshot = uiState.value.frozen
            val record = if (snapshot == null) old else old.copy(
                draft = uiState.value.displayDraft,
                translation = TranslationDraft(snapshot.sentence, TranslationResult.Ready(snapshot.translation, TranslationSource.MANUAL), manuallyEdited = true),
                activeSentence = snapshot.sentence, entryId = null,
            )
            if (repository.save(record) && repository.discardSubmission()) {
                mutableState.update { it.copy(record = record, submission = SubmissionUi.Editing()) }
                if (uiState.value.local.ready) analyze() else prepareLocal()
            } else mutableState.update { it.copy(persistenceFailed = true) }
        }
    }
    suspend fun playback(item: AudioItem): Pair<DraftAudioFile, AudioFileLease>? =
        if (uiState.value.audioState(item) is DraftAudioState.Ready) repository.playback(item) else null
    suspend fun licenses(): String = repository.licenses()

    fun receive(input: SharedInput) {
        if (!uiState.value.editingEnabled) return
        if (uiState.value.record.draft.sentence.isNotBlank()) mutableState.update { it.copy(pendingReplacement = input) }
        else applyInput(input)
    }
    fun acceptPhoto(path: String, ocr: Boolean) {
        if (!uiState.value.editingEnabled) return
        if (ocr && uiState.value.record.draft.sentence.isNotBlank()) mutableState.update { it.copy(pendingPhoto = path) }
        else processImage(path, ocr)
    }
    fun dismissReplacement() { mutableState.update { it.copy(pendingReplacement = null, pendingPhoto = null) } }
    fun replaceDraft() {
        val state = uiState.value
        viewModelScope.launch {
            val record = repository.clear(state.configuration.values.defaultTags)
            mutableState.update { it.copy(record = record, pendingReplacement = null, pendingPhoto = null, meaning = null, meanings = emptyList(), submission = SubmissionUi.Editing()) }
            if (state.pendingPhoto != null) processImage(state.pendingPhoto, true)
            else state.pendingReplacement?.let(::applyInput)
        }
    }
    private fun applyInput(input: SharedInput) {
        input.text?.let { captureMode("Type"); edit(TextFieldValue(it)) }
        input.image?.let { uri -> viewModelScope.launch {
            try { val file = repository.copyImage(uri); processImage(file.path, input.text == null) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.update { it.copy(imageFailed = true) } }
        } }
    }
    fun pickImage(uri: String, ocr: Boolean) { viewModelScope.launch {
        try { val file = repository.copyImage(uri); acceptPhoto(file.path, ocr) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { mutableState.update { it.copy(imageFailed = true) } }
    } }
    fun retryImage(ocr: Boolean) { uiState.value.record.sourcePath?.let { processImage(it, ocr) } }
    private fun processImage(path: String, ocr: Boolean) {
        imageJob?.cancel()
        val sentence = uiState.value.record.draft.sentence
        mutableState.update { it.copy(record = it.record.copy(sourcePath = path, imagePath = null), imageLoading = true, imageFailed = false, ocrLoading = ocr, ocrFailed = false) }
        save()
        imageJob = viewModelScope.launch {
            try {
                val image = repository.processImage(java.io.File(path))
                mutableState.update { it.copy(record = it.record.copy(imagePath = image.path), imageLoading = false) }
                save()
                if (ocr) {
                    val result = repository.recognize(java.io.File(path))
                    // OCR cannot overwrite a sentence edited while the camera/file work was running.
                    if (uiState.value.record.draft.sentence != sentence) return@launch
                    val blocks = result.blocks.filter { it.text.isNotBlank() }
                    mutableState.update { it.copy(imageHeight = result.sourceHeight, ocrChoices = if (blocks.size > 1) blocks else emptyList()) }
                    if (blocks.size == 1) commit(TextFieldValue(blocks.single().text))
                    else if (blocks.isEmpty() && result.text.isNotBlank()) commit(TextFieldValue(result.text))
                    else if (blocks.isEmpty()) mutableState.update { it.copy(ocrFailed = true) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.update { it.copy(imageLoading = false, imageFailed = true, ocrFailed = ocr) } }
            finally { mutableState.update { it.copy(ocrLoading = false) } }
        }
    }
    fun chooseOcr(block: OcrBlock) { mutableState.update { it.copy(ocrChoices = emptyList()) }; commit(TextFieldValue(block.text)) }
    fun dismissOcr() { /* Keep the candidates so Capture can reopen the chooser. */ }
    override fun onCleared() { repository.close() }
}
