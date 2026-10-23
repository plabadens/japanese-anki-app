package dev.pierre.japaneseanki.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.pierre.japaneseanki.AnkiDeck
import dev.pierre.japaneseanki.AnkiNoteType
import dev.pierre.japaneseanki.R
import dev.pierre.japaneseanki.speech.CloudVoice

/**
 * One page with a fixed primary action. Everything required is visible at once; the camera is the
 * only optional item, so it sits in its own section instead of gating the page.
 *
 * A voice is a real 48 dp row, never a read-only text field with a click modifier: a read-only
 * OutlinedTextField consumes the tap and the list never opens.
 */
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

internal data class SetupUi(
    val audio: AudioSetupUi,
    val anki: AnkiSetupUi,
    val cameraPermissionGranted: Boolean,
    val cameraDenied: Boolean,
    val dictionaryPreparing: Boolean,
    val dictionaryErrorResource: Int?,
    val audioReady: Boolean,
    val ankiReady: Boolean,
)

@Composable
internal fun SetupScreen(
    state: SetupUi,
    onBack: (() -> Unit)?,
    onKeyChange: (String) -> Unit,
    onVoiceChange: (String) -> Unit,
    onLoadVoices: () -> Unit,
    onRequestCamera: () -> Unit,
    onConnectAnki: () -> Unit,
    onSelectNoteType: (Long) -> Unit,
    onSelectDeck: (Long) -> Unit,
    onRetryDictionary: () -> Unit,
    onFinish: () -> Unit,
) {
    val finishEnabled = state.audioReady && state.ankiReady && !state.dictionaryPreparing &&
        state.dictionaryErrorResource == null && !state.audio.loading
    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        if (onBack != null) {
            BackRow(onBack)
        }
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SetupHeader(state)
            SectionLabel(stringResource(R.string.required))
            AudioCard(state = state.audio, onKeyChange = onKeyChange, onVoiceChange = onVoiceChange, onLoadVoices = onLoadVoices)
            AnkiCard(
                state = state.anki,
                onConnect = onConnectAnki,
                onSelectNoteType = onSelectNoteType,
                onSelectDeck = onSelectDeck,
            )
            SectionLabel(stringResource(R.string.optional))
            CameraRow(
                granted = state.cameraPermissionGranted,
                denied = state.cameraDenied,
                onRequest = onRequestCamera,
            )
        }
        FinishBar(
            enabled = finishEnabled,
            reason = finishReason(state),
            dictionaryFailed = state.dictionaryErrorResource != null,
            onRetryDictionary = onRetryDictionary,
            onFinish = onFinish,
        )
    }
}

/** Back affordance without an app-bar title: the state headline is the only heading. */
@Composable
private fun BackRow(onBack: () -> Unit) {
    val backLabel = stringResource(R.string.back)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.semantics { contentDescription = backLabel },
        ) { FlowIcons.Back(Modifier.size(24.dp)) }
    }
}

@Composable
private fun SetupHeader(state: SetupUi) {
    val headline: Int
    val body: Int
    when {
        state.dictionaryPreparing -> {
            headline = R.string.setup_dictionary_preparing
            body = R.string.setup_dictionary_preparing_body
        }
        state.dictionaryErrorResource != null -> {
            headline = R.string.setup_dictionary_failed
            body = state.dictionaryErrorResource ?: R.string.setup_dictionary_failed_body
        }
        state.audioReady && state.ankiReady -> {
            headline = R.string.setup_ready
            body = R.string.setup_ready_body
        }
        !state.audioReady && !state.ankiReady -> {
            headline = R.string.setup_two_steps_left
            body = R.string.setup_two_steps_left_body
        }
        !state.audioReady -> {
            headline = R.string.setup_audio_left
            body = R.string.setup_audio_left_body
        }
        else -> {
            headline = R.string.setup_anki_left
            body = R.string.setup_anki_left_body
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(headline), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun finishReason(state: SetupUi): Int? = when {
    state.dictionaryPreparing -> null
    state.dictionaryErrorResource != null -> null
    !state.audioReady && !state.ankiReady -> R.string.setup_needs_both
    !state.audioReady -> R.string.setup_needs_audio
    !state.ankiReady -> R.string.setup_needs_anki
    else -> null
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
private fun AudioCard(
    state: AudioSetupUi,
    onKeyChange: (String) -> Unit,
    onVoiceChange: (String) -> Unit,
    onLoadVoices: () -> Unit,
) {
    var listOpen by remember { mutableStateOf(false) }
    val selected = state.voices.firstOrNull { it.name == state.selectedVoice }
    FlowCard {
        Column(
            Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.sentence_audio), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = state.key,
                onValueChange = onKeyChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.google_key)) },
                enabled = !state.loading,
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    autoCorrectEnabled = false,
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
            when {
                state.loadingVoices -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    LinearProgressIndicator(Modifier.width(72.dp))
                    Text(stringResource(R.string.loading_voices), style = MaterialTheme.typography.bodyMedium)
                }
                state.voices.isNotEmpty() && listOpen -> VoiceList(
                    voices = state.voices,
                    selectedName = state.selectedVoice,
                    onSelect = { name ->
                        onVoiceChange(name)
                        listOpen = false
                    },
                    onReload = onLoadVoices,
                )
                state.voices.isNotEmpty() -> VoiceField(
                    voice = selected,
                    onClick = { listOpen = true },
                )
                state.key.isNotBlank() || state.credentialStored -> {
                    Text(
                        stringResource(R.string.voice_none_loaded),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                    )
                    TextButton(
                        onClick = onLoadVoices,
                        enabled = !state.loading,
                    ) { Text(stringResource(R.string.load_voices)) }
                }
                else -> Unit
            }
            if (state.errorResource != null && !state.loadingVoices) {
                Text(
                    stringResource(state.errorResource),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun VoiceField(voice: CloudVoice?, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.sentence_voice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(
                        R.string.voice_summary,
                        voice?.displayName.orEmpty(),
                        voice?.name.orEmpty(),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FlowIcons.ChevronDown(
                Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun VoiceList(
    voices: List<CloudVoice>,
    selectedName: String,
    onSelect: (String) -> Unit,
    onReload: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        voices.forEach { voice ->
            Row(
                Modifier.fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.RadioButton) { onSelect(voice.name) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                RadioButton(selected = voice.name == selectedName, onClick = null)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(voice.displayName, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        voice.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        TextButton(onClick = onReload) { Text(stringResource(R.string.reload_voices)) }
    }
}

@Composable
private fun AnkiCard(
    state: AnkiSetupUi,
    onConnect: () -> Unit,
    onSelectNoteType: (Long) -> Unit,
    onSelectDeck: (Long) -> Unit,
) {
    var noteTypePickerOpen by remember { mutableStateOf(false) }
    var deckPickerOpen by remember { mutableStateOf(false) }
    val selectedNoteType = state.noteTypes.firstOrNull { it.id == state.selectedNoteTypeId }
    val selectedDeck = state.decks.firstOrNull { it.id == state.selectedDeckId }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
    ) {
        Column(
            Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.anki), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(if (state.connected) R.string.connected else R.string.not_connected),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (state.connected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (!state.connected) {
                Text(
                    stringResource(R.string.anki_required_detail),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onConnect, enabled = !state.loading) {
                    Text(stringResource(if (state.installed) R.string.connect_anki else R.string.install_anki))
                }
            } else {
                if (state.noteTypes.isEmpty()) {
                    Text(
                        stringResource(R.string.anki_no_note_types),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    PickerRow(
                        label = stringResource(R.string.note_type),
                        value = selectedNoteType?.name.orEmpty(),
                        interactive = !state.loading && state.noteTypes.size > 1,
                        onClick = { noteTypePickerOpen = true },
                    )
                }
                if (state.decks.isEmpty()) {
                    Text(
                        stringResource(R.string.anki_no_decks),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    PickerRow(
                        label = stringResource(R.string.deck),
                        value = selectedDeck?.name.orEmpty(),
                        interactive = !state.loading && state.decks.size > 1,
                        onClick = { deckPickerOpen = true },
                    )
                }
            }
        }
    }
    if (noteTypePickerOpen) {
        SelectionDialog(
            titleResource = R.string.note_type,
            options = state.noteTypes.map { it.id to it.name },
            selectedId = state.selectedNoteTypeId,
            onSelect = { id ->
                noteTypePickerOpen = false
                onSelectNoteType(id)
            },
            onDismiss = { noteTypePickerOpen = false },
        )
    }
    if (deckPickerOpen) {
        SelectionDialog(
            titleResource = R.string.deck,
            options = state.decks.map { it.id to it.name },
            selectedId = state.selectedDeckId,
            onSelect = { id ->
                deckPickerOpen = false
                onSelectDeck(id)
            },
            onDismiss = { deckPickerOpen = false },
        )
    }
}

@Composable
private fun PickerRow(label: String, value: String, interactive: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    val content: @Composable () -> Unit = {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    value,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (interactive) {
                FlowIcons.ChevronDown(
                    Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (interactive) {
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            border = border,
            content = content,
        )
    } else {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            border = border,
            content = content,
        )
    }
}

@Composable
private fun CameraRow(granted: Boolean, denied: Boolean, onRequest: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                RoundedCornerShape(20.dp),
            )
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(40.dp),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                FlowIcons.Gallery(Modifier.size(20.dp), MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.camera_capture),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                when {
                    granted -> stringResource(R.string.camera_allowed)
                    denied -> stringResource(R.string.camera_denied)
                    else -> stringResource(R.string.camera_optional_detail)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (denied && !granted) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        if (granted) {
            Icon(
                MaterialSymbols.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp).size(20.dp),
            )
        } else {
            TextButton(onClick = onRequest) { Text(stringResource(R.string.allow_camera)) }
        }
    }
}

@Composable
private fun FinishBar(
    enabled: Boolean,
    reason: Int?,
    dictionaryFailed: Boolean,
    onRetryDictionary: () -> Unit,
    onFinish: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            reason?.let {
                Text(
                    stringResource(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FlowPrimaryButton(
                text = stringResource(R.string.finish_setup),
                onClick = onFinish,
                enabled = enabled,
            )
            if (dictionaryFailed) {
                TextButton(onClick = onRetryDictionary, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.retry))
                }
            }
        }
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
            Column(
                Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
            ) {
                options.forEach { (id, label) ->
                    Row(
                        Modifier.fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable(role = Role.RadioButton) { onSelect(id) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = id == selectedId, onClick = null)
                        Text(label, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
