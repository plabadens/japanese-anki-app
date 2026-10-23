package dev.pierre.japaneseanki.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.pierre.japaneseanki.AnkiDeck
import dev.pierre.japaneseanki.AnkiNoteType
import dev.pierre.japaneseanki.R
import dev.pierre.japaneseanki.speech.CloudVoice

internal enum class OnboardingStep { DICTIONARY, CAMERA, AUDIO, ANKIDROID, READY }

internal enum class DictionaryTaskPhase { IDLE, DOWNLOADING, VERIFYING, INSTALLING, WAITING_WIFI, WAITING_MOBILE, PAUSED, FAILED, CANCELLED, READY }

internal data class DictionaryTaskUi(
    val phase: DictionaryTaskPhase,
    val downloadedBytes: String? = null,
    val progressFraction: Float? = null,
    val hasStarted: Boolean = false,
    val ready: Boolean = false,
    val canPause: Boolean = false,
    val canResume: Boolean = false,
    val canCancel: Boolean = false,
    val canRetry: Boolean = false,
    val failureMessageResource: Int? = null,
)

internal data class AudioSetupUi(
    val key: String,
    val credentialStored: Boolean,
    val selectedVoice: String,
    val voices: List<CloudVoice>,
    val loading: Boolean,
    val loadingVoices: Boolean,
    val errorResource: Int? = null,
)

internal data class AnkiSetupUi(
    val installed: Boolean,
    val connected: Boolean,
    val loading: Boolean,
    val noteTypes: List<AnkiNoteType>,
    val decks: List<AnkiDeck>,
    val selectedNoteTypeId: Long?,
    val selectedDeckId: Long?,
)

@Composable
internal fun OnboardingScreen(
    step: OnboardingStep,
    dictionary: DictionaryTaskUi,
    dictionarySize: String,
    cameraPermissionGranted: Boolean,
    cameraDenied: Boolean,
    audio: AudioSetupUi,
    anki: AnkiSetupUi,
    dictionaryReady: Boolean,
    audioReady: Boolean,
    cameraReady: Boolean,
    ankiReady: Boolean,
    onBack: () -> Unit,
    onDownload: () -> Unit,
    onPauseDownload: () -> Unit,
    onResumeDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onRetryDownload: () -> Unit,
    onNext: () -> Unit,
    onRequestCamera: () -> Unit,
    onUseTyping: () -> Unit,
    onKeyChange: (String) -> Unit,
    onVoiceChange: (String) -> Unit,
    onReloadVoices: () -> Unit,
    onConnectAnki: () -> Unit,
    onSelectNoteType: (Long) -> Unit,
    onSelectDeck: (Long) -> Unit,
    onSetupAnkiLater: () -> Unit,
    onFinish: () -> Unit,
    onOpenDownloads: () -> Unit,
) {
    val title = when (step) {
        OnboardingStep.DICTIONARY -> R.string.dictionary
        OnboardingStep.CAMERA -> R.string.camera
        OnboardingStep.AUDIO -> R.string.audio
        OnboardingStep.ANKIDROID -> R.string.anki
        OnboardingStep.READY -> if (dictionaryReady && audioReady) R.string.ready else R.string.finish_setup
    }
    Column(Modifier.fillMaxSize()) {
        FlowTopBar(
            title = stringResource(title),
            onBack = if (step == OnboardingStep.DICTIONARY) null else onBack,
            modifier = Modifier.statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
        )
        Column(
            Modifier.fillMaxWidth().weight(1f).navigationBarsPadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (step) {
                OnboardingStep.DICTIONARY -> DictionarySetupContent(
                    state = dictionary,
                    formattedSize = dictionarySize,
                    onDownload = onDownload,
                    onPause = onPauseDownload,
                    onResume = onResumeDownload,
                    onCancel = onCancelDownload,
                    onRetry = onRetryDownload,
                    onNext = onNext,
                )
                OnboardingStep.CAMERA -> {
                    Text(stringResource(R.string.camera_purpose))
                    if (cameraDenied && !cameraPermissionGranted) {
                        Text(stringResource(R.string.camera_denied), color = MaterialTheme.colorScheme.error)
                    }
                    if (!cameraPermissionGranted) {
                        Button(onClick = onRequestCamera, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.allow_camera))
                        }
                        TextButton(onClick = onUseTyping, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.use_typing))
                        }
                    } else {
                        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.next))
                        }
                    }
                }
                OnboardingStep.AUDIO -> AudioSetupContent(
                    state = audio,
                    onKeyChange = onKeyChange,
                    onVoiceChange = onVoiceChange,
                    onReloadVoices = onReloadVoices,
                    onNext = onNext,
                )
                OnboardingStep.ANKIDROID -> AnkiSetupContent(
                    state = anki,
                    onConnect = onConnectAnki,
                    onSelectNoteType = onSelectNoteType,
                    onSelectDeck = onSelectDeck,
                    onNext = onNext,
                    onSetupLater = onSetupAnkiLater,
                )
                OnboardingStep.READY -> {
                    ReadinessRow(
                        label = stringResource(R.string.dictionary),
                        ready = dictionaryReady,
                        state = if (dictionaryReady) stringResource(R.string.ready) else stringResource(R.string.dictionary_needed),
                    )
                    ReadinessRow(
                        label = stringResource(R.string.camera_typing),
                        ready = cameraReady,
                        state = if (cameraReady) stringResource(R.string.ready) else stringResource(R.string.camera_denied),
                    )
                    ReadinessRow(
                        label = stringResource(R.string.audio),
                        ready = audioReady,
                        state = if (audioReady) stringResource(R.string.ready) else stringResource(R.string.audio_setup_needed),
                    )
                    ReadinessRow(
                        label = stringResource(R.string.anki),
                        ready = ankiReady,
                        state = if (ankiReady) stringResource(R.string.ready) else stringResource(R.string.not_connected),
                    )
                    Button(
                        onClick = onFinish,
                        enabled = dictionaryReady && audioReady,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.finish)) }
                    if (!dictionaryReady || !audioReady) {
                        TextButton(onClick = onOpenDownloads, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.downloads))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DictionarySetupContent(
    state: DictionaryTaskUi,
    formattedSize: String,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onNext: () -> Unit,
) {
    Text(stringResource(R.string.download_size, formattedSize))
    DictionaryTaskStatus(state)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            state.ready -> Unit
            state.canRetry -> TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            state.canResume -> TextButton(onClick = onResume) { Text(stringResource(R.string.resume)) }
            state.canPause -> TextButton(onClick = onPause) { Text(stringResource(R.string.pause)) }
            !state.hasStarted || state.phase == DictionaryTaskPhase.CANCELLED || state.phase == DictionaryTaskPhase.IDLE ->
                TextButton(onClick = onDownload) { Text(stringResource(R.string.download)) }
        }
        if (state.canCancel) TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
    }
    Button(
        onClick = onNext,
        enabled = state.hasStarted || state.ready,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.next)) }
}

@Composable
private fun DictionaryTaskStatus(state: DictionaryTaskUi) {
    val phaseResource = when (state.phase) {
        DictionaryTaskPhase.DOWNLOADING -> R.string.downloading
        DictionaryTaskPhase.VERIFYING -> R.string.verifying
        DictionaryTaskPhase.INSTALLING -> R.string.installing
        DictionaryTaskPhase.WAITING_WIFI, DictionaryTaskPhase.WAITING_MOBILE -> R.string.waiting_wifi
        DictionaryTaskPhase.PAUSED -> R.string.paused
        DictionaryTaskPhase.CANCELLED -> R.string.cancelled
        DictionaryTaskPhase.FAILED -> state.failureMessageResource ?: R.string.dictionary_failed
        DictionaryTaskPhase.READY -> R.string.dictionary_ready
        DictionaryTaskPhase.IDLE -> null
    }
    if (phaseResource != null) Text(stringResource(phaseResource))
    if (state.phase == DictionaryTaskPhase.DOWNLOADING) {
        val progress = state.progressFraction
        if (progress == null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        else LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    } else if (state.phase == DictionaryTaskPhase.VERIFYING || state.phase == DictionaryTaskPhase.INSTALLING) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
    state.downloadedBytes?.let { Text(it) }
}

@Composable
internal fun DownloadsContent(
    state: DictionaryTaskUi,
    formattedSize: String,
    noticeResource: Int?,
    setupIncomplete: Boolean,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onFinishSetup: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        noticeResource?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.onSurface) }
        Text(stringResource(R.string.dictionary), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.download_size, formattedSize))
        DictionaryTaskStatus(state)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                state.ready -> Unit
                state.canRetry -> TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                state.canResume -> TextButton(onClick = onResume) { Text(stringResource(R.string.resume)) }
                state.canPause -> TextButton(onClick = onPause) { Text(stringResource(R.string.pause)) }
                !state.hasStarted || state.phase == DictionaryTaskPhase.CANCELLED || state.phase == DictionaryTaskPhase.IDLE ->
                    TextButton(onClick = onDownload) { Text(stringResource(R.string.download)) }
            }
            if (state.canCancel) TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        }
        if (setupIncomplete) {
            TextButton(onClick = onFinishSetup, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.finish_setup))
            }
        }
    }
}

@Composable
private fun AudioSetupContent(
    state: AudioSetupUi,
    onKeyChange: (String) -> Unit,
    onVoiceChange: (String) -> Unit,
    onReloadVoices: () -> Unit,
    onNext: () -> Unit,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = state.key,
        onValueChange = onKeyChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.google_key)) },
        enabled = !state.loading,
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
    )
    OutlinedTextField(
        value = state.voices.firstOrNull { it.name == state.selectedVoice }?.displayName ?: state.selectedVoice,
        onValueChange = {},
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, enabled = !state.loading) {
            if (state.voices.isNotEmpty()) pickerOpen = true
        },
        label = { Text(stringResource(R.string.sentence_voice)) },
        readOnly = true,
        enabled = !state.loading && state.voices.isNotEmpty(),
        singleLine = true,
    )
    if (state.loadingVoices) Text(stringResource(R.string.loading_voices))
    if (state.errorResource != null) Text(stringResource(state.errorResource), color = MaterialTheme.colorScheme.error)
    TextButton(
        onClick = onReloadVoices,
        enabled = !state.loading && !state.loadingVoices && state.key.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.reload_voices)) }
    Button(
        onClick = onNext,
        enabled = !state.loading,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.next)) }
    if (pickerOpen) {
        AlertDialog(
            onDismissRequest = { pickerOpen = false },
            title = { Text(stringResource(R.string.sentence_voice)) },
            text = {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                    state.voices.forEach { voice ->
                        Row(
                            Modifier.fillMaxWidth().clickable(role = Role.RadioButton) {
                                onVoiceChange(voice.name)
                                pickerOpen = false
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = voice.name == state.selectedVoice,
                                onClick = {
                                    onVoiceChange(voice.name)
                                    pickerOpen = false
                                },
                            )
                            Text(voice.displayName)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickerOpen = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun AnkiSetupContent(
    state: AnkiSetupUi,
    onConnect: () -> Unit,
    onSelectNoteType: (Long) -> Unit,
    onSelectDeck: (Long) -> Unit,
    onNext: () -> Unit,
    onSetupLater: () -> Unit,
) {
    var notePickerOpen by remember { mutableStateOf(false) }
    var deckPickerOpen by remember { mutableStateOf(false) }
    Text(stringResource(if (state.connected) R.string.connected else R.string.not_connected))
    if (!state.connected) {
        TextButton(onClick = onConnect, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (state.installed) R.string.connect_anki else R.string.install_anki))
        }
    } else {
        OutlinedTextField(
            value = state.noteTypes.firstOrNull { it.id == state.selectedNoteTypeId }?.name.orEmpty(),
            onValueChange = {},
            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, enabled = !state.loading) {
                if (state.noteTypes.size > 1) notePickerOpen = true
            },
            label = { Text(stringResource(R.string.note_type)) },
            enabled = !state.loading && state.noteTypes.size > 1,
            readOnly = true,
            singleLine = true,
        )
        OutlinedTextField(
            value = state.decks.firstOrNull { it.id == state.selectedDeckId }?.name.orEmpty(),
            onValueChange = {},
            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, enabled = !state.loading) {
                if (state.decks.isNotEmpty()) deckPickerOpen = true
            },
            label = { Text(stringResource(R.string.deck)) },
            enabled = !state.loading && state.decks.isNotEmpty(),
            readOnly = true,
            singleLine = true,
        )
    }
    Button(
        onClick = onNext,
        enabled = state.connected && state.selectedNoteTypeId != null && state.selectedDeckId != null && !state.loading,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.next)) }
    TextButton(onClick = onSetupLater, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.setup_later))
    }
    if (notePickerOpen) {
        SelectionDialog(
            titleResource = R.string.note_type,
            options = state.noteTypes.map { it.id to it.name },
            selectedId = state.selectedNoteTypeId,
            onSelect = { onSelectNoteType(it); notePickerOpen = false },
            onDismiss = { notePickerOpen = false },
        )
    }
    if (deckPickerOpen) {
        SelectionDialog(
            titleResource = R.string.deck,
            options = state.decks.map { it.id to it.name },
            selectedId = state.selectedDeckId,
            onSelect = { onSelectDeck(it); deckPickerOpen = false },
            onDismiss = { deckPickerOpen = false },
        )
    }
}

@Composable
private fun SelectionDialog(
    titleResource: Int,
    options: List<Pair<Long, String>>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleResource)) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                options.forEach { (id, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.RadioButton) { onSelect(id) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = id == selectedId, onClick = { onSelect(id) })
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun ReadinessRow(label: String, ready: Boolean, state: String) {
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) { stateDescription = state }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Icon(
            imageVector = if (ready) MaterialSymbols.CheckCircle else MaterialSymbols.InfoOutline,
            contentDescription = null,
            tint = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
