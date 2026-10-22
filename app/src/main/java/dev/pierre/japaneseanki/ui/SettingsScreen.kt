package dev.pierre.japaneseanki.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
)

/**
 * Autosaved Settings surface. The owner supplies debounced persistence and flushes it before Back;
 * this composable intentionally has no Save action and never calls a provider itself.
 */
@Composable
internal fun SettingsScreen(
    state: SettingsScreenState,
    noteTypes: List<AnkiNoteType>,
    decks: List<AnkiDeck>,
    onBack: () -> Unit,
    onModelChange: (Long?) -> Unit,
    onDeckChange: (Long?) -> Unit,
    onDefaultTagsChange: (String) -> Unit,
    onGoogleKeyChange: (String) -> Unit,
    onVoiceChange: (String) -> Unit,
    onReloadVoices: () -> Unit,
    modifier: Modifier = Modifier,
    additionalSections: @Composable ColumnScope.() -> Unit = {},
) {
    var modelMenu by remember { mutableStateOf(false) }
    var deckMenu by remember { mutableStateOf(false) }
    var voiceMenu by remember { mutableStateOf(false) }
    var showKey by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize().imePadding()) {
        FlowTopBar(
            title = "Settings",
            onBack = onBack,
            modifier = Modifier.statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).navigationBarsPadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("AnkiDroid", style = MaterialTheme.typography.titleMedium)
            Text("Note type", style = MaterialTheme.typography.titleSmall)
            Column {
                OutlinedButton(
                    onClick = { modelMenu = true },
                    enabled = !state.loading && noteTypes.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        noteTypes.firstOrNull { it.id == state.values.modelId }?.name
                            ?: if (state.values.modelId == null) "Choose note type" else "Previously selected note type unavailable",
                    )
                }
                DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                    noteTypes.forEach { model ->
                        DropdownMenuItem(
                            text = { Text(model.name) },
                            onClick = { modelMenu = false; onModelChange(model.id) },
                        )
                    }
                }
            }
            noteTypes.firstOrNull { it.id == state.values.modelId }?.let { model ->
                AnkiFieldContract.mapToModelOrder(model.fields, emptyMap()).errors.forEach {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }

            Text("Destination deck", style = MaterialTheme.typography.titleSmall)
            Column {
                OutlinedButton(
                    onClick = { deckMenu = true },
                    enabled = !state.loading && decks.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        decks.firstOrNull { it.id == state.values.deckId }?.name
                            ?: if (state.values.deckId == null) "Choose destination deck" else "Previously selected deck unavailable",
                    )
                }
                DropdownMenu(expanded = deckMenu, onDismissRequest = { deckMenu = false }) {
                    decks.forEach { deck ->
                        DropdownMenuItem(
                            text = { Text(deck.name) },
                            onClick = { deckMenu = false; onDeckChange(deck.id) },
                        )
                    }
                }
            }
            if (state.loading) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.padding(2.dp), strokeWidth = 2.dp)
                    Text("Loading saved settings…")
                }
            }
            OutlinedTextField(
                value = state.values.defaultTags,
                onValueChange = onDefaultTagsChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Default tags") },
                supportingText = { Text("Applied to each new note. Per-note edits do not change these defaults.") },
                minLines = 2,
                maxLines = 4,
                enabled = !state.loading,
            )

            HorizontalDivider()
            Text("Google Cloud", style = MaterialTheme.typography.titleMedium)
            Text(
                "One API key for sentence speech and translation. It is encrypted with Android Keystore, stays on this device, and is sent only to Google Cloud in an authentication header. Entering or saving it sends no request.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = state.googleKey,
                onValueChange = onGoogleKeyChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Google Cloud API key") },
                singleLine = true,
                enabled = !state.loading,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = showKey, onCheckedChange = { showKey = it }, enabled = !state.loading)
                Text("Show key")
                Text(
                    if (state.credentialStored) "Key saved securely" else "No key saved",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (state.credentialUnreadable) {
                Text(
                    "The saved Google Cloud key could not be opened. Enter it again.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Text("Sentence voice · Chirp 3 HD", style = MaterialTheme.typography.titleSmall)
            when {
                state.voiceCatalog.loading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(2.dp), strokeWidth = 2.dp)
                    Text("Listing Japanese voices…")
                }
                state.voiceCatalog.error != null -> Text(
                    state.voiceCatalog.error + if (state.voiceCatalog.hasCachedList)
                        " Showing the cached list; it is not verified for the current key."
                    else "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                state.voiceCatalog.provenance == VoiceCatalogProvenance.CACHED -> Text(
                    "Showing a cached voice list. It has not been verified for the current key.",
                    style = MaterialTheme.typography.bodySmall,
                )
                state.voiceCatalog.provenance == VoiceCatalogProvenance.AUTHORITATIVE && state.voiceCatalog.voices.isEmpty() -> Text(
                    "No Japanese Chirp 3 HD voices were returned.",
                    style = MaterialTheme.typography.bodySmall,
                )
                state.voiceCatalog.provenance == VoiceCatalogProvenance.NONE -> Text(
                    if (state.googleKey.isBlank()) "Add a key to list voices." else "Voice list not checked yet.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Column {
                val selectedVoice = state.voiceCatalog.voices.firstOrNull { it.name == state.values.voiceName }
                OutlinedButton(
                    onClick = { voiceMenu = true },
                    enabled = !state.loading && state.voiceCatalog.voices.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        selectedVoice?.let { "${it.displayName} · ${it.name}" }
                            ?: if (state.values.voiceName.isBlank()) "Choose sentence voice"
                            else "Saved voice not in this list",
                    )
                }
                DropdownMenu(
                    expanded = voiceMenu,
                    onDismissRequest = { voiceMenu = false },
                    modifier = Modifier.heightIn(max = 320.dp),
                ) {
                    state.voiceCatalog.voices.forEach { voice: CloudVoice ->
                        DropdownMenuItem(
                            text = { Text("${voice.displayName} · ${voice.name}") },
                            onClick = { voiceMenu = false; onVoiceChange(voice.name) },
                        )
                    }
                }
            }
            if (state.voiceCatalog.selectedVoiceMissing(state.values.voiceName)) {
                Text(
                    "The saved voice is not in Google's current list. Select a listed voice before generating sentence audio.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(
                enabled = state.credentialStored && !state.voiceCatalog.loading && !state.loading,
                onClick = onReloadVoices,
                modifier = Modifier.semantics { contentDescription = "Reload voices" },
            ) { Text("Reload voices") }
            Text(
                "Voice listing does not synthesize audio. It makes no speech-generation request.",
                style = MaterialTheme.typography.bodySmall,
            )

            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            additionalSections()
        }
    }
}
