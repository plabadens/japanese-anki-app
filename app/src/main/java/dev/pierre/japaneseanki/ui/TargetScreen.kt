package dev.pierre.japaneseanki.ui

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pierre.japaneseanki.R
import dev.pierre.japaneseanki.JapaneseToken
import dev.pierre.japaneseanki.CorrectableReading
import dev.pierre.japaneseanki.TargetSpan

/**
 * Target view: tappable word pills above the resolved entry and meaning choices. Selection is a
 * single UTF-16 occurrence span; a pill shows its own surface exactly as it appears in the sentence.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TargetScreen(
    sentence: String,
    tokens: List<JapaneseToken>,
    selectedSpan: TargetSpan?,
    onSelectSpan: (TargetSpan) -> Unit,
    onEditSentence: () -> Unit,
    entryLabel: String?,
    entryDetail: String?,
    entryCount: Int,
    onChooseEntry: () -> Unit,
    senses: List<String>,
    selectedSenses: Set<Int>,
    onToggleSense: (Int, Boolean) -> Unit,
    meaningStatus: String?,
    canContinue: Boolean,
    continueHint: String?,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    locked: Boolean = false,
    tokensLoading: Boolean = false,
    hasMoreEntries: Boolean = false,
    onLoadMoreEntries: (() -> Unit)? = null,
    ocrChoicesAvailable: Boolean = false,
    onReopenOcrChooser: () -> Unit = {},
    vocabularyReading: String,
    onVocabularyReadingChange: (String) -> Unit,
    vocabularyReadingError: String?,
    correctableReadings: List<CorrectableReading>,
    onReadingCorrection: (TargetSpan, String) -> Unit,
    readingCorrectionError: String?,
    definition: String,
    definitionStale: Boolean,
    onDefinitionChange: (String) -> Unit,
    onAcknowledgeDefinition: () -> Unit,
    translation: String,
    translationPending: Boolean,
    translationError: String?,
    translationStale: Boolean,
    onTranslationChange: (String) -> Unit,
    onRetryTranslation: () -> Unit,
    pitchAlternatives: List<Int>,
    selectedPitchAccent: Int?,
    onSelectPitch: (Int) -> Unit,
    imageAttached: Boolean,
    imageProcessing: Boolean,
    imageError: String?,
    onChoosePhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
) {
    val pills = targetPills(tokens)
    var correctionsExpanded by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        if (ocrChoicesAvailable) {
            TextButton(enabled = !locked, onClick = onReopenOcrChooser) {
                Text(stringResource(R.string.legacy_card_choose_text_from_photo))
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (sentence.isNotBlank()) {
                Text(sentence, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.fillMaxWidth())
            }
            if (pills.isEmpty()) {
                Text(
                    when {
                        tokensLoading -> stringResource(R.string.legacy_card_analyzing_the_sentence)
                        else -> stringResource(R.string.legacy_card_no_words_are_available_yet_edit_the_senten)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    pills.forEach { pill ->
                        TargetPillItem(
                            pill = pill,
                            selected = selectedSpan == pill.token.span,
                            enabled = !locked,
                            onClick = { onSelectSpan(pill.token.span) },
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(enabled = !locked, onClick = onEditSentence) {
                    androidx.compose.material3.Icon(
                        MaterialSymbols.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(stringResource(R.string.legacy_card_edit_sentence))
                }
            }
            if (entryLabel != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(entryLabel, style = MaterialTheme.typography.titleLarge)
                    entryDetail?.takeIf(String::isNotBlank)?.let { detail ->
                        Text(
                            "  $detail",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (entryCount > 1) {
                        TextButton(enabled = !locked, onClick = onChooseEntry) {
                            Text(stringResource(R.string.legacy_card_entry_count, entryCount.toString()))
                            Spacer(Modifier.size(4.dp))
                            FlowIcons.ChevronDown(Modifier.size(16.dp))
                        }
                    }
                }
            }
            meaningStatus?.let { status ->
                Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (senses.isNotEmpty()) {
                Text(stringResource(R.string.legacy_card_meaning), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                senses.forEachIndexed { index, gloss ->
                    MeaningCard(
                        gloss = gloss,
                        checked = index in selectedSenses,
                        enabled = !locked,
                        onCheckedChange = { checked -> onToggleSense(index, checked) },
                    )
                }
            }
            TextButton(enabled = !locked, onClick = { correctionsExpanded = !correctionsExpanded }) {
                Text(if (correctionsExpanded) stringResource(R.string.legacy_card_hide_preparation_corrections) else stringResource(R.string.legacy_card_edit_preparation_corrections))
            }
            if (correctionsExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.legacy_card_preparation_corrections), style = MaterialTheme.typography.titleMedium)
                    TextButton(enabled = !locked, onClick = onChoosePhoto) {
                        Text(if (imageAttached) stringResource(R.string.legacy_card_replace_photo) else stringResource(R.string.attach_photo))
                    }
                    if (imageAttached) {
                        TextButton(enabled = !locked && !imageProcessing, onClick = onRemovePhoto) {
                            Text(stringResource(R.string.remove_photo))
                        }
                        Text(
                            if (imageProcessing) stringResource(R.string.legacy_card_preparing_photo) else stringResource(R.string.legacy_card_photo_attached),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    imageError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                    if (correctableReadings.isNotEmpty()) {
                        Text(stringResource(R.string.legacy_card_sentence_readings), style = MaterialTheme.typography.labelLarge)
                        correctableReadings.forEach { item ->
                            OutlinedTextField(
                                value = item.reading,
                                onValueChange = { onReadingCorrection(item.span, it) },
                                enabled = !locked,
                                label = { Text(stringResource(R.string.legacy_card_reading_label, item.surface)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        readingCorrectionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                    OutlinedTextField(
                        value = vocabularyReading,
                        onValueChange = onVocabularyReadingChange,
                        enabled = !locked,
                        label = { Text(stringResource(R.string.legacy_card_vocabulary_reading)) },
                        singleLine = true,
                        isError = vocabularyReadingError != null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    vocabularyReadingError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    OutlinedTextField(
                        value = definition,
                        onValueChange = onDefinitionChange,
                        enabled = !locked,
                        label = { Text(stringResource(R.string.legacy_card_meaning)) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (definitionStale) {
                        Text(stringResource(R.string.legacy_card_the_meaning_may_be_stale_for_the_current_t), color = MaterialTheme.colorScheme.error)
                        TextButton(enabled = !locked, onClick = onAcknowledgeDefinition) { Text(stringResource(R.string.legacy_card_keep_this_meaning)) }
                    }
                    OutlinedTextField(
                        value = translation,
                        onValueChange = onTranslationChange,
                        enabled = !locked,
                        label = { Text(stringResource(R.string.legacy_card_translation)) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (translationPending) {
                        Text(stringResource(R.string.legacy_card_translation_is_being_prepared), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    translationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (translationStale) Text(stringResource(R.string.legacy_card_this_translation_is_for_an_earlier_sentenc), color = MaterialTheme.colorScheme.error)
                    if (translationError != null) {
                        TextButton(enabled = !locked, onClick = onRetryTranslation) { Text(stringResource(R.string.legacy_card_retry_translation)) }
                    }
                    if (pitchAlternatives.isNotEmpty()) {
                        Text(stringResource(R.string.legacy_card_pitch_accent), style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pitchAlternatives.forEach { accent ->
                                OutlinedButton(
                                    enabled = !locked,
                                    onClick = { onSelectPitch(accent) },
                                ) {
                                    Text(
                                        if (accent == selectedPitchAccent) {
                                            stringResource(R.string.legacy_card_selected_pitch_accent, accent)
                                        } else "$accent",
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (hasMoreEntries && onLoadMoreEntries != null) {
                TextButton(
                    onClick = onLoadMoreEntries,
                    enabled = !locked,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.legacy_card_load_more_dictionary_matches)) }
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!canContinue && continueHint != null) {
                Text(
                    continueHint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FlowPrimaryButton(text = stringResource(R.string.legacy_card_continue_action), onClick = onContinue, enabled = canContinue && !locked)
        }
    }
}

@Composable
private fun TargetPillItem(
    pill: TargetPill,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (pill.prefix.isNotEmpty()) TargetPunctuation(pill.prefix)
        WordPill(
            surface = pill.token.surface,
            spokenSurface = pill.token.surface,
            selected = selected,
            enabled = enabled,
            onClick = onClick,
        )
        if (pill.suffix.isNotEmpty()) TargetPunctuation(pill.suffix)
    }
}

/** Punctuation stays beside its word but outside the selectable pill and its outline. */
@Composable
private fun TargetPunctuation(value: String) {
    Text(
        value,
        style = MaterialTheme.typography.headlineMedium.copy(
            fontSize = 24.sp,
            fontWeight = FontWeight.Normal,
        ),
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun WordPill(
    surface: String,
    spokenSurface: String = surface,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    val spokenDescription = stringResource(R.string.legacy_card_word_accessibility, spokenSurface)
    val selectionDescription = stringResource(
        if (selected) R.string.legacy_card_target_selected_state else R.string.legacy_card_not_selected_state,
    )
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        // The study keeps the pill's own surface tint and marks selection with a 2 dp primary
        // outline, so the word stays legible and selection is not colour-only.
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
        modifier = Modifier
            .padding(if (selected) 0.dp else 2.dp)
            .semantics {
                contentDescription = spokenDescription
                stateDescription = selectionDescription
            },
    ) {
        Text(
            surface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A meaning is selected by an explicit check plus a contrasting outline, never colour alone. */
@Composable
private fun MeaningCard(
    gloss: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val selectionDescription = stringResource(
        if (checked) R.string.legacy_card_meaning_selected_state else R.string.legacy_card_not_selected_state,
    )
    Surface(
        onClick = { onCheckedChange(!checked) },
        enabled = enabled,
        shape = shape,
        color = if (checked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(
            if (checked) 2.dp else 1.dp,
            if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .semantics {
                contentDescription = gloss
                stateDescription = selectionDescription
            },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .then(
                        if (checked) {
                            Modifier
                        } else {
                            Modifier.border(BorderStroke(2.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(6.dp))
                        },
                    )
                    .then(
                        if (checked) {
                            Modifier.background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                        } else {
                            Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) FlowIcons.Check(Modifier.size(16.dp), MaterialTheme.colorScheme.onPrimary)
            }
        Text(
            gloss,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        }
    }
}
