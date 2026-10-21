package dev.pierre.japaneseanki.ui

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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pierre.japaneseanki.JapaneseToken
import dev.pierre.japaneseanki.TargetSpan

/**
 * Target view: tappable word pills above the resolved entry and meaning choices. Selection is a
 * single UTF-16 occurrence span; a pill shows its own surface exactly as it appears in the sentence.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TargetScreen(
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
) {
    val pills = targetPills(tokens)
    Column(modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (pills.isEmpty()) {
                Text(
                    when {
                        tokensLoading -> "Analyzing the sentence…"
                        else -> "No words are available yet. Edit the sentence to prepare the selection."
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
            if (selectedSpan != null) {
                SpanControls(
                    target = selectedSpan,
                    tokens = tokens,
                    enabled = !locked,
                    onSpanChange = onSelectSpan,
                )
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
                    Text("  Edit sentence")
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
                            Text("$entryCount entries")
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
                Text("Meaning", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                senses.forEachIndexed { index, gloss ->
                    MeaningCard(
                        gloss = gloss,
                        checked = index in selectedSenses,
                        enabled = !locked,
                        onCheckedChange = { checked -> onToggleSense(index, checked) },
                    )
                }
            }
            if (hasMoreEntries && onLoadMoreEntries != null) {
                TextButton(
                    onClick = onLoadMoreEntries,
                    enabled = !locked,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Load more dictionary matches") }
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
            FlowPrimaryButton(text = "Continue", onClick = onContinue, enabled = canContinue && !locked)
        }
    }
}

/**
 * Boundary adjustment for a contiguous selection: extend or trim one word at a time. The study
 * reaches the same spans by dragging across pills; labelled controls keep it operable with
 * TalkBack and switch access (SPEC.md §4.3.3).
 */
@Composable
private fun SpanControls(
    target: TargetSpan,
    tokens: List<JapaneseToken>,
    enabled: Boolean,
    onSpanChange: (TargetSpan) -> Unit,
) {
    val surface = spanSurface(tokens, target).orEmpty()
    val extendLeft = extendSpan(tokens, target, SpanEdge.Left)
    val extendRight = extendSpan(tokens, target, SpanEdge.Right)
    val trimLeft = trimSpan(tokens, target, SpanEdge.Left)
    val trimRight = trimSpan(tokens, target, SpanEdge.Right)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            if (surface.isBlank()) "Selection" else "Selected: $surface",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                enabled = enabled && extendLeft != null,
                onClick = { extendLeft?.let(onSpanChange) },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("◀ Extend") }
            TextButton(
                enabled = enabled && extendRight != null,
                onClick = { extendRight?.let(onSpanChange) },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("Extend ▶") }
            if (trimLeft != null || trimRight != null) {
                TextButton(
                    enabled = enabled,
                    onClick = { (trimLeft ?: trimRight)?.let(onSpanChange) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Trim") }
            }
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
                contentDescription = "Word $spokenSurface"
                stateDescription = if (selected) "Selected target" else "Not selected"
            },
    ) {
        Text(
            surface,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = 24.sp,
                fontWeight = FontWeight.Normal,
            ),
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
                stateDescription = if (checked) "Selected meaning" else "Not selected"
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
            Text(gloss, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
