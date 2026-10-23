package dev.pierre.japaneseanki.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import dev.pierre.japaneseanki.AnkiDeck
import dev.pierre.japaneseanki.AnkiFieldContract
import dev.pierre.japaneseanki.AnkiNoteType
import dev.pierre.japaneseanki.SettingsValues
import dev.pierre.japaneseanki.VoiceCatalogProvenance
import dev.pierre.japaneseanki.VoiceCatalogState
import dev.pierre.japaneseanki.R
import dev.pierre.japaneseanki.speech.CloudVoice

/** Secret is deliberately held outside the settings data class to avoid accidental data-class logs. */
internal class SettingsScreenState(
    val values: SettingsValues,
    val googleKey: String,
    val credentialStored: Boolean,
    val loading: Boolean = false,
    val credentialUnreadable: Boolean = false,
    val error: String? = null,
    val voiceCatalog: VoiceCatalogState = VoiceCatalogState(),
    val ankiConnected: Boolean = false,
    val ankiInstalled: Boolean = false,
    val onboardingIncomplete: Boolean = false,
    val cacheBusy: Boolean = false,
    val cacheMessage: String? = null,
)

/** Autosaved Settings surface. Back flushes pending changes; no Save action is shown. */
@Composable
internal fun SettingsScreen(
    state: SettingsScreenState,
    noteTypes: List<AnkiNoteType>,
    decks: List<AnkiDeck>,
    onBack: () -> Unit,
    onFinishSetup: () -> Unit,
    onConnectAnki: () -> Unit,
    onModelChange: (Long?) -> Unit,
    onDeckChange: (Long?) -> Unit,
    onDefaultTagsChange: (String) -> Unit,
    onGoogleKeyChange: (String) -> Unit,
    onVoiceChange: (String) -> Unit,
    onReloadVoices: () -> Unit,
    onClearCache: () -> Unit,
    onOpenSources: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var modelPickerOpen by remember { mutableStateOf(false) }
    var deckPickerOpen by remember { mutableStateOf(false) }
    var voicePickerOpen by remember { mutableStateOf(false) }
    var showKey by remember { mutableStateOf(false) }
    val showKeyLabel = stringResource(R.string.show_key)
    val hideKeyLabel = stringResource(R.string.hide_key)
    var clearCacheConfirm by remember { mutableStateOf(false) }

    val compatibleNoteTypes = remember(noteTypes) {
        noteTypes.filter { model ->
            AnkiFieldContract.mapToModelOrder(model.fields, emptyMap()).isValid
        }
    }
    val selectedModel = compatibleNoteTypes.firstOrNull { it.id == state.values.modelId }
    val selectedDeck = decks.firstOrNull { it.id == state.values.deckId }
    val selectedVoice = state.voiceCatalog.voices.firstOrNull { it.name == state.values.voiceName }

    Column(modifier.fillMaxSize().imePadding()) {
        FlowTopBar(
            title = stringResource(R.string.settings),
            onBack = onBack,
            modifier = Modifier.statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
        )
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).navigationBarsPadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.onboardingIncomplete) {
                TextButton(onClick = onFinishSetup, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.finish_setup))
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(stringResource(R.string.anki), style = MaterialTheme.typography.bodyLarge)
                Text(if (state.ankiConnected) stringResource(R.string.connected) else stringResource(R.string.not_connected))
                if (!state.ankiConnected) {
                    TextButton(onClick = onConnectAnki) {
                        Text(if (state.ankiInstalled) stringResource(R.string.connect_anki) else stringResource(R.string.install_anki))
                    }
                }
            }
            PickerField(
                label = stringResource(R.string.note_type),
                value = selectedModel?.name.orEmpty(),
                enabled = !state.loading && compatibleNoteTypes.isNotEmpty(),
                onClick = { modelPickerOpen = true },
            )
            PickerField(
                label = stringResource(R.string.deck),
                value = selectedDeck?.name.orEmpty(),
                enabled = !state.loading && decks.isNotEmpty(),
                onClick = { deckPickerOpen = true },
            )

            OutlinedTextField(
                value = state.googleKey,
                onValueChange = onGoogleKeyChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.google_key)) },
                singleLine = true,
                enabled = !state.loading,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { showKey = !showKey },
                    enabled = !state.loading,
                    modifier = Modifier.semantics {
                        contentDescription = if (showKey) hideKeyLabel else showKeyLabel
                    },
                ) { Text(if (showKey) hideKeyLabel else showKeyLabel) }
            }
            PickerField(
                label = stringResource(R.string.sentence_voice),
                value = selectedVoice?.displayName ?: state.values.voiceName,
                enabled = !state.loading,
                onClick = { voicePickerOpen = true },
            )
            TextButton(
                enabled = !state.loading && !state.voiceCatalog.loading,
                onClick = onReloadVoices,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.reload_voices)) }
            state.voiceCatalog.error?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error)
            }
            if (state.voiceCatalog.selectedVoiceMissing(state.values.voiceName)) {
                Text(stringResource(R.string.voice_unavailable), color = MaterialTheme.colorScheme.error)
            }
            if (state.voiceCatalog.loading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MotionAwareProgressIndicator(
                        modifier = Modifier.padding(2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        diameter = 40.dp,
                        stateDescription = stringResource(R.string.loading_voices),
                    )
                    Text(stringResource(R.string.loading_voices))
                }
            }

            OutlinedTextField(
                value = state.values.defaultTags,
                onValueChange = onDefaultTagsChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.default_tags)) },
                minLines = 1,
                maxLines = 3,
                enabled = !state.loading,
            )

            TextButton(
                enabled = !state.cacheBusy,
                onClick = { clearCacheConfirm = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.clear_cache)) }
            state.cacheMessage?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (message == "Could not clear the cache.") {
                    TextButton(onClick = onClearCache) { Text(stringResource(R.string.retry)) }
                }
            }
            TextButton(onClick = onOpenSources, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.sources))
            }
            state.error?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error)
                if (message == "Could not save your changes.") {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.retry)) }
                }
            }
        }
    }

    if (modelPickerOpen) {
        PickerDialog(
            title = stringResource(R.string.note_type),
            selectedId = state.values.modelId,
            rows = compatibleNoteTypes.map { PickerRow(it.id, it.name) },
            onSelect = { id -> modelPickerOpen = false; onModelChange(id) },
            onDismiss = { modelPickerOpen = false },
        )
    }
    if (deckPickerOpen) {
        PickerDialog(
            title = stringResource(R.string.deck),
            selectedId = state.values.deckId,
            rows = decks.map { PickerRow(it.id, it.name) },
            onSelect = { id -> deckPickerOpen = false; onDeckChange(id) },
            onDismiss = { deckPickerOpen = false },
        )
    }
    if (voicePickerOpen) {
        val voiceRows = state.voiceCatalog.voices.mapIndexed { index, voice ->
            PickerRow(index.toLong(), "${voice.displayName} · ${voice.name}")
        }
        PickerDialog(
            title = stringResource(R.string.sentence_voice),
            selectedId = state.voiceCatalog.voices.indexOfFirst { it.name == state.values.voiceName }
                .takeIf { it >= 0 }?.toLong(),
            rows = voiceRows,
            emptyMessage = when {
                state.voiceCatalog.error != null -> state.voiceCatalog.error
                !state.credentialStored -> stringResource(R.string.key_required)
                state.voiceCatalog.provenance == VoiceCatalogProvenance.AUTHORITATIVE ->
                    stringResource(R.string.voices_empty)
                else -> null
            },
            cachedMessage = if (state.voiceCatalog.provenance == VoiceCatalogProvenance.CACHED) {
                stringResource(R.string.cached_voices)
            } else null,
            loadingMessage = if (state.voiceCatalog.loading) stringResource(R.string.loading_voices) else null,
            onSelect = { index ->
                state.voiceCatalog.voices.getOrNull(index.toInt())?.let { onVoiceChange(it.name) }
                voicePickerOpen = false
            },
            onDismiss = { voicePickerOpen = false },
        )
    }
    if (clearCacheConfirm) {
        AlertDialog(
            onDismissRequest = { clearCacheConfirm = false },
            title = { Text(stringResource(R.string.clear_cache_title)) },
            text = { Text(stringResource(R.string.clear_cache_body)) },
            confirmButton = {
                TextButton(onClick = { clearCacheConfirm = false; onClearCache() }) {
                    Text(stringResource(R.string.clear_cache))
                }
            },
            dismissButton = {
                TextButton(onClick = { clearCacheConfirm = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

private data class PickerRow(val id: Long, val label: String)

@Composable
private fun PickerField(
    label: String,
    value: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val contentColor = MaterialTheme.colorScheme.onSurface.let {
        if (enabled) it else it.copy(alpha = 0.38f)
    }
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.let {
        if (enabled) it else it.copy(alpha = 0.38f)
    }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(
            enabled = enabled,
            role = Role.Button,
            onClick = onClick,
        ),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = if (enabled) 1f else 0.38f),
        ),
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = labelColor)
            Text(value, style = MaterialTheme.typography.bodyLarge, color = contentColor)
        }
    }
}

@Composable
private fun PickerDialog(
    title: String,
    selectedId: Long?,
    rows: List<PickerRow>,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
    emptyMessage: String? = null,
    cachedMessage: String? = null,
    loadingMessage: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
            ) {
                loadingMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                cachedMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                emptyMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(row.id) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = row.id == selectedId, onClick = { onSelect(row.id) })
                        Text(row.label, modifier = Modifier.weight(1f))
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
