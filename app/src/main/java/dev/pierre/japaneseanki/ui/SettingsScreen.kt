package dev.pierre.japaneseanki.ui

import androidx.compose.ui.res.stringResource

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
import dev.pierre.japaneseanki.R
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
            title = stringResource(R.string.settings),
            onBack = onBack,
            modifier = Modifier.statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).navigationBarsPadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.anki), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.note_type), style = MaterialTheme.typography.titleSmall)
            Column {
                OutlinedButton(
                    onClick = { modelMenu = true },
                    enabled = !state.loading && noteTypes.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        noteTypes.firstOrNull { it.id == state.values.modelId }?.name
                            ?: if (state.values.modelId == null) stringResource(R.string.legacy_settings_choose_note_type) else stringResource(R.string.legacy_settings_previously_selected_note_type_unavailable),
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

            Text(stringResource(R.string.legacy_settings_destination_deck), style = MaterialTheme.typography.titleSmall)
            Column {
                OutlinedButton(
                    onClick = { deckMenu = true },
                    enabled = !state.loading && decks.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        decks.firstOrNull { it.id == state.values.deckId }?.name
                            ?: if (state.values.deckId == null) stringResource(R.string.legacy_settings_choose_destination_deck) else stringResource(R.string.legacy_settings_previously_selected_deck_unavailable),
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
                    MotionAwareProgressIndicator(
                        modifier = Modifier.padding(2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        diameter = 40.dp,
                        strokeWidth = 2.dp,
                        stateDescription = stringResource(R.string.legacy_settings_loading_saved_settings),
                    )
                    Text(stringResource(R.string.legacy_settings_loading_saved_settings))
                }
            }
            OutlinedTextField(
                value = state.values.defaultTags,
                onValueChange = onDefaultTagsChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.default_tags)) },
                supportingText = { Text(stringResource(R.string.legacy_settings_applied_to_each_new_note_per_note_edits_do)) },
                minLines = 2,
                maxLines = 4,
                enabled = !state.loading,
            )

            HorizontalDivider()
            Text(stringResource(R.string.legacy_settings_google_cloud), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.legacy_settings_one_api_key_for_sentence_speech_and_transl),
                style = MaterialTheme.typography.bodySmall,
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
                Checkbox(checked = showKey, onCheckedChange = { showKey = it }, enabled = !state.loading)
                Text(stringResource(R.string.legacy_settings_show_key))
                Text(
                    if (state.credentialStored) stringResource(R.string.legacy_settings_key_saved_securely) else stringResource(R.string.legacy_settings_no_key_saved),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (state.credentialUnreadable) {
                Text(
                    stringResource(R.string.legacy_settings_the_saved_google_cloud_key_could_not_be_op),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Text(stringResource(R.string.legacy_settings_sentence_voice_chirp_3_hd), style = MaterialTheme.typography.titleSmall)
            when {
                state.voiceCatalog.loading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MotionAwareProgressIndicator(
                        modifier = Modifier.padding(2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        diameter = 40.dp,
                        strokeWidth = 2.dp,
                        stateDescription = stringResource(R.string.legacy_settings_listing_japanese_voices),
                    )
                    Text(stringResource(R.string.legacy_settings_listing_japanese_voices))
                }
                state.voiceCatalog.error != null -> Text(
                    state.voiceCatalog.error + if (state.voiceCatalog.hasCachedList)
                        stringResource(R.string.legacy_settings_showing_the_cached_list_it_is_not_verified)
                    else "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                state.voiceCatalog.provenance == VoiceCatalogProvenance.CACHED -> Text(
                    stringResource(R.string.legacy_settings_showing_a_cached_voice_list_it_has_not_bee),
                    style = MaterialTheme.typography.bodySmall,
                )
                state.voiceCatalog.provenance == VoiceCatalogProvenance.AUTHORITATIVE && state.voiceCatalog.voices.isEmpty() -> Text(
                    stringResource(R.string.legacy_settings_no_japanese_chirp_3_hd_voices_were_returne),
                    style = MaterialTheme.typography.bodySmall,
                )
                state.voiceCatalog.provenance == VoiceCatalogProvenance.NONE -> Text(
                    if (state.googleKey.isBlank()) stringResource(R.string.legacy_settings_add_a_key_to_list_voices) else stringResource(R.string.legacy_settings_voice_list_not_checked_yet),
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
                            ?: if (state.values.voiceName.isBlank()) stringResource(R.string.legacy_settings_choose_sentence_voice)
                            else stringResource(R.string.legacy_settings_saved_voice_not_in_this_list),
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
                    stringResource(R.string.legacy_settings_the_saved_voice_is_not_in_google_s_current),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            val reloadVoicesLabel = stringResource(R.string.reload_voices)
            TextButton(
                enabled = state.credentialStored && !state.voiceCatalog.loading && !state.loading,
                onClick = onReloadVoices,
                modifier = Modifier.semantics { contentDescription = reloadVoicesLabel },
            ) { Text(stringResource(R.string.reload_voices)) }
            Text(
                stringResource(R.string.legacy_settings_voice_listing_does_not_synthesize_audio_it),
                style = MaterialTheme.typography.bodySmall,
            )

            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            additionalSections()
        }
    }
}
