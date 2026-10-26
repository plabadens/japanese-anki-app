package dev.pierre.japaneseanki

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.pierre.japaneseanki.card.CardMeaningPicker
import dev.pierre.japaneseanki.card.CardSentenceEditorSheet
import dev.pierre.japaneseanki.card.SentenceOcrChoicesSheet
import dev.pierre.japaneseanki.card.cardSendBlockerMessage
import dev.pierre.japaneseanki.ui.*
import kotlinx.coroutines.launch

/** Route binding only. Screens receive values and callbacks; AndroidX owns the back stack. */
@Composable
internal fun JapaneseAnkiApp(
    card: CardViewModel,
    settings: SettingsViewModel,
    cameraGranted: Boolean,
    incoming: SharedInput?,
    consumeInput: () -> Unit,
    onExit: () -> Unit,
    onRequestCamera: () -> Unit,
    onConnectAnki: () -> Unit,
    onGallery: () -> Unit,
    onAttachment: () -> Unit,
    onPlay: (Pair<DraftAudioFile, AudioFileLease>) -> Unit,
    onOpenNote: (Long) -> Unit,
) {
    val state by card.uiState.collectAsStateWithLifecycle()
    val form by settings.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val nav = rememberNavController()
    var saving by remember { mutableStateOf(false) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var workingEditor by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var showMeanings by rememberSaveable { mutableStateOf(false) }
    var showOcr by rememberSaveable { mutableStateOf(false) }
    var licenses by remember { mutableStateOf("") }
    var initialRoute by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(state.initialized, form.loading, state.localLoading) {
        if (initialRoute == null && state.initialized && !form.loading &&
            (!state.configuration.onboarded || !state.localLoading || state.submission is SubmissionUi.Frozen)) {
            initialRoute = when {
                state.submission is SubmissionUi.Frozen -> "Card"
                !state.configuration.onboarded || !state.local.ready -> "Setup"
                state.record.route == AppRoute.Card -> "Card"
                else -> "Capture"
            }
        }
    }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val busy = state.submission == SubmissionUi.Sending || saving

    LaunchedEffect(state.initialized, incoming) {
        if (state.initialized && incoming != null) { card.receive(incoming); consumeInput() }
    }
    LaunchedEffect(state.meanings) { if (state.meanings.size > 1 && state.meaning == null) showMeanings = true }
    LaunchedEffect(state.ocrChoices) { if (state.ocrChoices.size > 1) showOcr = true }
    LaunchedEffect(route) { if (route == "Sources") licenses = card.licenses() }
    LaunchedEffect(state.delivered) {
        if (state.delivered) nav.navigate("Capture") { popUpTo(nav.graph.id) { inclusive = true }; launchSingleTop = true }
    }
    LaunchedEffect(state.record.route) {
        // OCR is a committed input too. UI navigation observes that event without owning its preparation.
        if (state.initialized && state.record.route == AppRoute.Card && route == "Capture") nav.navigate("Card") { launchSingleTop = true }
    }
    fun open(route: String) { if (!busy) nav.navigate(route) { launchSingleTop = true } }
    fun pop() { if (!nav.popBackStack()) onExit() }
    fun closeForm(finish: Boolean = false) {
        if (busy) return
        saving = true
        scope.launch {
            try {
                if (if (finish) settings.finishSetup() else settings.flush()) {
                    if (finish) nav.navigate("Capture") { popUpTo(nav.graph.id) { inclusive = true } }
                    else pop()
                }
            } finally { saving = false }
        }
    }
    fun cardBack() {
        if (busy) return
        if (!state.editingEnabled) onExit()
        else { card.route(AppRoute.Capture); nav.navigate("Capture") { popUpTo(nav.graph.id) { inclusive = true } } }
    }
    fun perform(action: CardPrimary) {
        when (action) {
            CardPrimary.None -> Unit
            CardPrimary.Analyze -> card.analyze()
            CardPrimary.Setup -> open("Setup")
            CardPrimary.Settings -> open("Settings")
            CardPrimary.Translation -> card.retryTranslation()
            CardPrimary.SentenceAudio -> card.retryAudio(AudioItem.SENTENCE)
            CardPrimary.WordAudio -> card.retryAudio(AudioItem.VOCABULARY)
            CardPrimary.Send, CardPrimary.RetrySend -> card.send()
        }
    }

    if (initialRoute == null) {
        FlowPage { androidx.compose.material3.CircularProgressIndicator() }
        return
    }
    val start = initialRoute!!
    val settingsState = form.copy(
        error = form.problem?.let { problemMessage(it) },
        loading = form.loading || saving,
        ankiConnected = state.connection.permission && !state.connection.failed,
        ankiInstalled = state.connection.installed,
        onboardingIncomplete = !state.configuration.onboarded,
    )
    NavHost(navController = nav, startDestination = start) {
        composable("Capture") {
            FlowPage {
                InputScreen(
                    captureMode = state.record.captureMode, onCaptureModeChange = card::captureMode,
                    editor = state.editor, onEditorChange = card::edit,
                    onContinue = { if (card.commit()) open("Card") },
                    onOpenGallery = onGallery, onChoosePhoto = onAttachment,
                    onCaptured = { card.acceptPhoto(it.path, true) }, onCameraError = {},
                    outputDirectory = card.outputDirectory, modifier = Modifier.weight(1f).fillMaxWidth(),
                    enabled = state.editingEnabled, cameraPermissionGranted = cameraGranted, onRequestCamera = onRequestCamera,
                    ocrLoading = state.ocrLoading,
                    ocrError = if (state.ocrFailed) stringResource(R.string.legacy_capture_could_not_read_this_photo_type_or_edit_the) else null,
                    onRetryOcr = { if (state.record.sourcePath != null) card.retryImage(true) else onGallery() },
                    ocrChoicesAvailable = state.ocrChoices.size > 1, onReopenOcrChooser = { showOcr = true },
                    imageStatus = when {
                        state.imageLoading -> stringResource(R.string.legacy_capture_preparing_photo_for_anki)
                        state.imageFailed -> stringResource(R.string.legacy_capture_this_photo_could_not_be_prepared_for_anki)
                        state.record.imagePath != null -> stringResource(R.string.legacy_capture_photo_attached)
                        else -> null
                    },
                    imageFailed = state.imageFailed, onRetryImage = { card.retryImage(false) },
                    confirmationMessage = if (state.delivered) stringResource(R.string.card_note_sent_to_ankidroid) else null,
                    onSettings = { open("Settings") },
                )
            }
        }
        composable("Card") {
            BackHandler { cardBack() }
            val sentenceAudio = audioPresentation(state, AudioItem.SENTENCE)
            val wordAudio = audioPresentation(state, AudioItem.VOCABULARY)
            val decision = state.decision
            val blocker = cardSendBlockerMessage(decision.blocker, sentenceAudio, wordAudio)
            val checking = state.submission is SubmissionUi.Checking
            val pending = when (val current = state.submission) {
                is SubmissionUi.Frozen -> current
                is SubmissionUi.Checking -> SubmissionUi.Frozen(current.record)
                else -> null
            }
            val recoveryMessage = pending?.let {
                if (checking) stringResource(R.string.card_checking_the_saved_note_id_and_destination)
                else if (it.record.phase == SubmissionPhase.MEDIA_IMPORTING) stringResource(
                    R.string.card_send_recovery_media_preparation_interrupted,
                    it.record.operation?.label?.replaceFirstChar(Char::uppercase) ?: stringResource(R.string.card_media_preparation),
                ) else stringResource(R.string.card_pending_send_needs_review)
            }
            val hint = when {
                state.submission == SubmissionUi.Sending -> stringResource(R.string.sending)
                pending != null -> if (pending.retryReady) listOfNotNull(blocker ?: recoveryMessage, stringResource(R.string.card_frozen_send_read_only)).joinToString(" ") else recoveryMessage
                state.submission is SubmissionUi.Duplicate -> stringResource(R.string.card_matching_note_exists)
                state.analysisFailed -> stringResource(R.string.card_could_not_analyze_this_sentence_select_a_t)
                else -> blocker
            }
            FlowPage(topBar = {
                FlowTopBar(title = stringResource(R.string.card), onBack = ::cardBack, onSettings = { open("Settings") }, navigationEnabled = !busy)
            }) {
                val draft = state.displayDraft
                CardScreen(
                    sentence = draft.sentence, tokens = state.tokens, selectedSpan = draft.target,
                    selectionEnabled = state.editingEnabled,
                    onSelectSpan = { card.selectWord(it) },
                    furigana = if (pending == null) SentenceReadings.effective(draft.sentence, state.tokens, draft.target, draft.sentenceReading, draft.readingCorrections) else emptyList(),
                    onEditSentence = { workingEditor = state.editor; showEditor = true },
                    translation = state.translation, vocabulary = draft.vocabulary, vocabularyReading = draft.vocabularyReading,
                    partOfSpeech = englishPartOfSpeechLabel(VocabularyPartOfSpeech.forSelectedSenses(state.meaning?.entry, setOf(state.record.senseIndex))),
                    definition = draft.definition, pitch = state.pitch, pitchStatus = null, pitchPending = state.pitchLoading,
                    wordStatus = when {
                        pending != null -> null
                        state.searching -> stringResource(R.string.card_dictionary_searching)
                        state.lookupFailed -> stringResource(R.string.card_dictionary_lookup_failed_retry_before_sele)
                        state.meaning == null && draft.target != null -> stringResource(R.string.no_entry)
                        else -> null
                    },
                    wordAudioEnabled = state.meaning != null && state.editingEnabled,
                    meaningAvailable = state.meanings.size > 1 && state.editingEnabled,
                    onChangeMeaning = { showMeanings = true }, imagePath = state.imagePath, imageFocusY = 0.5f,
                    notes = draft.notes, tags = draft.tags, onNotesChange = card::notes, onTagsChange = card::tags,
                    sentenceAudio = sentenceAudio, wordAudio = wordAudio,
                    onToggleSentenceAudio = { scope.launch { card.playback(AudioItem.SENTENCE)?.let(onPlay) } },
                    onRetrySentenceAudio = { perform(if (state.configuration.audio.voice == null) CardPrimary.Settings else CardPrimary.SentenceAudio) },
                    onToggleWordAudio = { scope.launch { card.playback(AudioItem.VOCABULARY)?.let(onPlay) } },
                    onRetryWordAudio = { card.retryAudio(AudioItem.VOCABULARY) },
                    hint = hint, hintPreparing = state.analyzing || state.searching,
                    actionLabel = stringResource(when (decision.action) {
                        CardPrimary.Setup -> R.string.finish_setup
                        CardPrimary.Settings -> R.string.card_open_settings_action
                        CardPrimary.Analyze, CardPrimary.Translation, CardPrimary.SentenceAudio, CardPrimary.WordAudio -> R.string.retry
                        CardPrimary.RetrySend -> R.string.card_retry_send
                        else -> R.string.send
                    }),
                    actionEnabled = decision.action != CardPrimary.None && !busy,
                    actionLoading = state.submission == SubmissionUi.Sending,
                    onAction = { perform(decision.action) }, statusMessage = null,
                    statusContent = {
                        if (pending != null && !checking) {
                            pending.noteId?.let { id -> TextButton(onClick = { onOpenNote(id) }) { Text(stringResource(R.string.card_open_matching_note_in_ankidroid)) } }
                            if (!pending.retryReady) {
                                if (pending.record.phase in listOf(SubmissionPhase.MEDIA_IMPORTING, SubmissionPhase.MEDIA_IMPORTED)) {
                                    TextButton(onClick = card::allowFrozenRetry) { Text(stringResource(R.string.card_retry_media_import_may_duplicate)) }
                                } else TextButton(onClick = card::refreshConnection) { Text(stringResource(R.string.card_retry_pending_send_check)) }
                            }
                            TextButton(onClick = card::discardPending) { Text(stringResource(
                                if (pending.record.phase in listOf(SubmissionPhase.MEDIA_IMPORTING, SubmissionPhase.MEDIA_IMPORTED) || pending.retryReady)
                                    R.string.card_discard_interrupted_send_keep_draft else R.string.card_i_checked_resolve_outcome,
                            )) }
                        }
                        (state.submission as? SubmissionUi.Duplicate)?.let { duplicate ->
                            TextButton(onClick = { onOpenNote(duplicate.noteId) }) { Text(stringResource(R.string.card_open_matching_note_in_ankidroid)) }
                            TextButton(onClick = card::discardPending) { Text(stringResource(R.string.card_edit_and_recheck)) }
                        }
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }
        composable("Settings") {
            SettingsScreen(
                state = settingsState, noteTypes = state.connection.models, decks = state.connection.decks,
                onBack = { closeForm() }, onFinishSetup = { scope.launch { if (settings.flush()) open("Setup") } }, onConnectAnki = onConnectAnki,
                onModelChange = { settings.valuesChanged(form.values.copy(modelId = it)) },
                onDeckChange = { settings.valuesChanged(form.values.copy(deckId = it)) },
                onDefaultTagsChange = { settings.valuesChanged(form.values.copy(defaultTags = it)) },
                onGoogleKeyChange = settings::keyChanged, onVoiceChange = { settings.valuesChanged(form.values.copy(voiceName = it)) },
                onReloadVoices = settings::reloadVoices, onClearCache = settings::clearCache,
                onOpenSources = { open("Sources") },
            )
            BackHandler { closeForm() }
        }
        composable("Setup") {
            SetupScreen(
                state = SetupUi(
                    audio = AudioSetupUi(form.googleKey, form.credentialStored, form.values.voiceName, form.voiceCatalog.voices,
                        form.loading || saving, form.voiceCatalog.loading, errorResource = form.problem?.let { R.string.save_failed }),
                    anki = AnkiSetupUi(state.connection.installed, state.connection.permission && !state.connection.failed,
                        state.connectionLoading, state.connection.models.filter { AnkiFieldContract.mapToModelOrder(it.fields, emptyMap()).isValid },
                        state.connection.decks, form.values.modelId, form.values.deckId),
                    cameraPermissionGranted = cameraGranted, cameraDenied = false,
                    dictionaryPreparing = state.localLoading, dictionaryErrorResource = if (!state.local.ready && !state.localLoading) R.string.dictionary_install_failed else null,
                    audioReady = form.credentialStored && form.voiceCatalog.selectedVoiceForSynthesis(form.values.voiceName) != null,
                    ankiReady = state.connection.permission && state.model != null && state.deck != null,
                ),
                onBack = if (state.configuration.onboarded) ({ closeForm() }) else null,
                onKeyChange = settings::keyChanged, onVoiceChange = { settings.valuesChanged(form.values.copy(voiceName = it)) },
                onLoadVoices = settings::reloadVoices, onRequestCamera = onRequestCamera, onConnectAnki = onConnectAnki,
                onSelectNoteType = { settings.valuesChanged(form.values.copy(modelId = it)) },
                onSelectDeck = { settings.valuesChanged(form.values.copy(deckId = it)) },
                onRetryDictionary = card::prepareLocal, onFinish = { closeForm(true) },
            )
            BackHandler(enabled = state.configuration.onboarded) { closeForm() }
        }
        composable("Sources") {
            FlowPage(topBar = { FlowTopBar(title = stringResource(R.string.sources), onBack = ::pop) }) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(MaterialTheme.flow.screenPadding), verticalArrangement = Arrangement.spacedBy(MaterialTheme.flow.sectionGap)) {
                    Text(stringResource(R.string.legacy_settings_jmdict_source_notice, state.local.notices["jmdict_source"].orEmpty()))
                    Text(stringResource(R.string.legacy_settings_kanjium_source_notice, state.local.notices["kanjium_commit"].orEmpty()))
                    Text(stringResource(R.string.legacy_settings_mecab_0_996_taku_kudo_and_nippon_telegraph))
                    Text(stringResource(R.string.legacy_settings_unidic_2_1_2_the_unidic_consortium_and))
                    Text(licenses)
                }
            }
        }
    }
    BackHandler(enabled = busy) { /* An external write or Settings flush must finish before navigation. */ }
    if (showEditor && state.editingEnabled) CardSentenceEditorSheet(
        draft = workingEditor, editor = state.editor, onDraftChanged = { workingEditor = it },
        onDismiss = { workingEditor = state.editor; showEditor = false },
        onUseSelection = { edited ->
            val selection = edited.selection
            if (selection.start != selection.end && card.commit(edited, TargetSpan(selection.min, selection.max))) showEditor = false
        },
        onDone = { if (card.commit(it)) showEditor = false },
        selectionHint = stringResource(R.string.legacy_settings_select_part_of_the_sentence_then_use_it_as),
        useSelectionLabel = stringResource(R.string.legacy_settings_use_selection_as_target),
    )
    CardMeaningPicker(
        visible = showMeanings, targetAvailable = state.record.draft.target != null,
        choices = state.meanings, enabled = state.editingEnabled, selectedEntryId = state.meaning?.entry?.id,
        vocabulary = state.record.draft.vocabulary, vocabularyReading = state.record.draft.vocabularyReading,
        selectedSenseIndices = setOf(state.record.senseIndex), onDismiss = { showMeanings = false },
        onChoose = { card.chooseMeaning(it); showMeanings = false },
    )
    SentenceOcrChoicesSheet(visible = showOcr, choices = state.ocrChoices, enabled = state.editingEnabled,
        onDismiss = { showOcr = false }, onChoose = { card.chooseOcr(it); showOcr = false })
    if (state.pendingReplacement != null || state.pendingPhoto != null) FlowDialog(
        onDismissRequest = card::dismissReplacement, title = { Text(stringResource(R.string.replace_title)) },
        text = { Text(stringResource(R.string.replace_body)) },
        confirmButton = { TextButton(onClick = card::replaceDraft) { Text(stringResource(R.string.replace)) } },
        dismissButton = { TextButton(onClick = card::dismissReplacement) { Text(stringResource(R.string.keep_draft)) } },
    )
}

@Composable
private fun problemMessage(problem: SettingsProblem): String = stringResource(when (problem) {
    SettingsProblem.Save -> R.string.save_failed
    SettingsProblem.Load -> R.string.legacy_settings_could_not_load_saved_settings
    SettingsProblem.InvalidKey -> R.string.key_required
    SettingsProblem.Voices -> R.string.voices_failed
    SettingsProblem.Cache -> R.string.legacy_settings_could_not_clear_the_cache
})

private fun audioPresentation(state: CardUiState, item: AudioItem): ReviewAudioPresentation = when (val audio = state.audioState(item)) {
    is DraftAudioState.Ready -> ReviewAudioPresentation.Ready
    is DraftAudioState.Generating -> ReviewAudioPresentation.Generating
    is DraftAudioState.Failed -> if (audio.diagnostic?.category == "SOURCE_MISSING") ReviewAudioPresentation.SourceMissing else ReviewAudioPresentation.Failed(audio.message)
    is DraftAudioState.NeedsContinue -> ReviewAudioPresentation.NeedsPreparation
    else -> ReviewAudioPresentation.Unavailable
}
