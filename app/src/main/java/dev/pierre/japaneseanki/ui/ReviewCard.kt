package dev.pierre.japaneseanki.ui

import android.net.Uri
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.pierre.japaneseanki.DictionarySense
import dev.pierre.japaneseanki.CorrectableReading
import dev.pierre.japaneseanki.FuriganaSpan
import dev.pierre.japaneseanki.JapaneseToken
import dev.pierre.japaneseanki.PitchAccent
import dev.pierre.japaneseanki.PitchDiagramScale
import dev.pierre.japaneseanki.PitchPatternDiagram
import dev.pierre.japaneseanki.TargetSpan
import java.io.File

internal sealed interface ReviewSendPresentation {
    data object Ready : ReviewSendPresentation
    data object Sending : ReviewSendPresentation
    data class Status(val message: String, val actionLabel: String? = null) : ReviewSendPresentation
}

/** Presentation-only state supplied by the owner of sentence and vocabulary audio requests. */
internal sealed interface ReviewAudioPresentation {
    data object Unavailable : ReviewAudioPresentation
    data object Ready : ReviewAudioPresentation
    data object Playing : ReviewAudioPresentation
    data object Generating : ReviewAudioPresentation
    data class Failed(val message: String) : ReviewAudioPresentation
}

internal fun reviewAudioControlLabel(field: String, audio: ReviewAudioPresentation): String = when (audio) {
    ReviewAudioPresentation.Unavailable -> "$field audio unavailable"
    ReviewAudioPresentation.Ready -> "Play $field audio"
    ReviewAudioPresentation.Playing -> "Pause $field audio"
    ReviewAudioPresentation.Generating -> "Generating $field audio"
    is ReviewAudioPresentation.Failed -> "Explain unavailable $field audio"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ReviewCard(
    sentence: String,
    target: TargetSpan,
    sentenceReading: String,
    furigana: List<FuriganaSpan>,
    vocabulary: String,
    vocabularyReading: String,
    pitch: PitchAccent?,
    definition: String,
    translation: String,
    notes: String,
    tags: List<String>,
    imagePath: String?,
    imageStatus: String?,
    pitchHasAlternatives: Boolean,
    pitchInfoExpanded: Boolean,
    onTogglePitchInfo: () -> Unit,
    onEdit: () -> Unit,
    onAddTag: () -> Unit,
    onChooseImage: () -> Unit = {},
    onRemoveImage: () -> Unit = {},
    imageRemovable: Boolean = true,
    onDefinitionChange: (String) -> Unit = {},
    onNotesChange: (String) -> Unit = {},
    tokens: List<JapaneseToken> = emptyList(),
    onSelectToken: (TargetSpan) -> Unit = {},
    definitionSenses: List<DictionarySense> = emptyList(),
    selectedSenseIndices: Set<Int> = emptySet(),
    onToggleSense: (Int, Boolean) -> Unit = { _, _ -> },
    correctableReadings: List<CorrectableReading> = emptyList(),
    onCorrectReading: (CorrectableReading, String) -> Boolean = { _, _ -> false },
    readingCorrectionError: String? = null,
    onVocabularyReadingChange: (String) -> Boolean = { false },
    vocabularyReadingError: String? = null,
    vocabularyAudioProgress: String? = null,
    vocabularyAudioProgressFraction: Float? = null,
    onCancelVocabularyAudio: (() -> Unit)? = null,
    generatingFields: Set<String> = emptySet(),
    sentenceAudio: ReviewAudioPresentation = ReviewAudioPresentation.Unavailable,
    vocabularyAudio: ReviewAudioPresentation = ReviewAudioPresentation.Unavailable,
    onToggleSentenceAudio: (() -> Unit)? = null,
    onRetrySentenceAudio: (() -> Unit)? = null,
    onToggleVocabularyAudio: (() -> Unit)? = null,
    onRetryVocabularyAudio: (() -> Unit)? = null,
    translationUsesFallback: Boolean = false,
    translationInfoExpanded: Boolean = false,
    translationError: String? = null,
    translationStale: Boolean = false,
    translationSuggestion: String? = null,
    translationEditable: Boolean = true,
    editingEnabled: Boolean = true,
    onTranslationChange: (String) -> Unit = {},
    onRetryTranslation: () -> Unit = {},
    onKeepMyTranslation: () -> Unit = {},
    onUseTranslationSuggestion: () -> Unit = {},
    onToggleTranslationInfo: () -> Unit = {},
    sendPresentation: ReviewSendPresentation? = null,
    onSend: (() -> Unit)? = null,
    onSendStatusAction: (() -> Unit)? = null,
    sendStatusContent: @androidx.compose.runtime.Composable () -> Unit = {},
) {
    var editingField by remember { mutableStateOf<String?>(null) }
    var notesExpanded by remember { mutableStateOf(false) }
    var correctingSpan by remember { mutableStateOf<CorrectableReading?>(null) }
    var readingDraft by remember { mutableStateOf("") }
    var vocabularyReadingDraft by remember(vocabularyReading) { mutableStateOf(vocabularyReading) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (imagePath != null || imageStatus != null) {
                ReviewFieldRow("Image", onEdit = { editingField = "Image" }, editingEnabled = editingEnabled) {
                when {
                    imagePath != null && File(imagePath).isFile -> AndroidView(
                        factory = { context -> ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP } },
                        update = { it.setImageURI(Uri.fromFile(File(imagePath))) },
                        modifier = Modifier.size(72.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)),
                    )
                    else -> Box(
                        Modifier.size(72.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                            .semantics { contentDescription = imageStatus.orEmpty() },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (imageStatus?.startsWith("Preparing", ignoreCase = true) == true) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Text(
                                imageStatus.orEmpty(),
                                modifier = Modifier.padding(4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                }
            }
            ReviewFieldRow(
                "Sentence", onEdit = { editingField = "Sentence" }, editingEnabled = editingEnabled,
                audio = sentenceAudio, onToggleAudio = onToggleSentenceAudio, onRetryAudio = onRetrySentenceAudio,
            ) {
                val parts = reviewSentenceParts(sentence, target, sentenceReading, furigana)
                val unresolvedColor = MaterialTheme.colorScheme.onSurfaceVariant
                // One flow item per ruby/plain run: each item owns its intrinsic width, so ruby
                // readings and bases can never overlap the neighbouring run. FlowRow wraps
                // between runs, mirroring normal Japanese line breaking.
                FlowRow(
                    modifier = Modifier.fillMaxWidth().clearAndSetSemantics {
                        contentDescription = buildString {
                            append(sentence)
                            if (parts.any { it.unresolvedKanji }) append(". Some kanji have no available reading.")
                        }
                        stateDescription = "Target selected: ${target.textIn(sentence)}"
                    },
                    horizontalArrangement = Arrangement.Start,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    parts.forEach { part ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                part.reading ?: "\u00A0",
                                fontSize = 9.sp,
                                lineHeight = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                            )
                            Text(
                                part.text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (part.highlighted) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier
                                    .then(
                                        if (part.highlighted) {
                                            Modifier.background(
                                                MaterialTheme.colorScheme.secondaryContainer,
                                                RoundedCornerShape(2.dp),
                                            )
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .then(
                                        if (part.unresolvedKanji) {
                                            Modifier.drawBehind {
                                                val dot = 1.dp.toPx()
                                                val gap = 2.dp.toPx()
                                                var x = dot / 2
                                                val y = size.height - dot
                                                while (x < size.width) {
                                                    drawCircle(unresolvedColor, dot / 2, Offset(x, y))
                                                    x += dot + gap
                                                }
                                            }
                                        } else {
                                            Modifier
                                        },
                                    ),
                                softWrap = false,
                            )
                        }
                    }
                }
            }
            ReviewFieldRow(
                "Vocabulary", onEdit = { editingField = "Word" }, editingEnabled = editingEnabled,
                audio = vocabularyAudio, onToggleAudio = onToggleVocabularyAudio, onRetryAudio = onRetryVocabularyAudio,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RubyText(vocabulary, vocabularyReading)
                    pitch?.let { PitchPatternDiagram(it, PitchDiagramScale.Compact) }
                    if (pitchHasAlternatives || pitch == null) {
                        IconButton(onClick = onTogglePitchInfo, modifier = Modifier.size(48.dp).semantics {
                            contentDescription = if (pitchInfoExpanded) "Dismiss pitch explanation" else if (pitch == null) "Explain missing pitch pattern" else "Explain alternate pitch pattern"
                            stateDescription = if (pitchInfoExpanded) "Expanded" else "Collapsed"
                        }) { Icon(MaterialSymbols.ErrorOutline, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                if (pitchInfoExpanded) {
                    Text(
                        if (pitch == null) "No pitch pattern is available for this reading." else "More than one pitch pattern is listed for this reading; the first match is shown.",
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(10.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                vocabularyReadingError?.let { message ->
                    Text(message, style = MaterialTheme.typography.bodySmall)
                }
                if (vocabularyAudioProgress != null) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (vocabularyAudioProgressFraction != null) {
                            LinearProgressIndicator(
                                progress = { vocabularyAudioProgressFraction },
                                modifier = Modifier.fillMaxWidth().semantics {
                                    stateDescription = "Downloading ${(vocabularyAudioProgressFraction * 100).toInt()} percent"
                                },
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().semantics { stateDescription = "Downloading" },
                            )
                        }
                        Text(vocabularyAudioProgress, style = MaterialTheme.typography.bodySmall)
                        if (onCancelVocabularyAudio != null) {
                            TextButton(
                                onClick = onCancelVocabularyAudio,
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) { Text("Cancel download") }
                        }
                    }
                }
            }
            ReviewFieldRow("Definition", onEdit = { editingField = "Definition" }, editingEnabled = editingEnabled, generating = "Definition" in generatingFields) { Text(definition.ifBlank { "Add a definition" }, style = MaterialTheme.typography.bodyLarge) }
            ReviewFieldRow("Translation", onEdit = onEdit, editingEnabled = editingEnabled) {
                val translationPending = "Translation" in generatingFields
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = translation,
                        onValueChange = onTranslationChange,
                        readOnly = !translationEditable,
                        modifier = Modifier.weight(1f),
                        label = {
                            Text(
                                if (translationPending && translation.isBlank()) "Translating sentence…"
                                else "Sentence translation (required)",
                            )
                        },
                        minLines = 2,
                    )
                    if (translationPending) CircularProgressIndicator(
                        Modifier.size(20.dp).semantics {
                            contentDescription = "Generating Translation"
                            stateDescription = "In progress"
                        },
                        strokeWidth = 2.dp,
                    )
                }
                translationError?.let { message ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(enabled = translationEditable, onClick = onRetryTranslation, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Retry")
                        }
                    }
                }
                if (translationStale) {
                    Text("Your text is for the previous sentence.", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(enabled = translationEditable, onClick = onKeepMyTranslation, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Keep my text")
                        }
                        if (translationSuggestion != null) TextButton(
                            enabled = translationEditable,
                            onClick = onUseTranslationSuggestion,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text("Use new suggestion") }
                    }
                }
                if (translationUsesFallback) {
                    IconButton(onClick = onToggleTranslationInfo, modifier = Modifier.size(48.dp).semantics {
                        contentDescription = if (translationInfoExpanded) "Dismiss offline translation explanation" else "Explain offline translation"
                        stateDescription = if (translationInfoExpanded) "Expanded" else "Collapsed"
                    }) { Icon(MaterialSymbols.ErrorOutline, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                if (translationInfoExpanded) Text(
                    "This translation was produced offline.",
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(10.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            ReviewFieldRow("Notes", onEdit = { notesExpanded = !notesExpanded }, editingEnabled = editingEnabled) {
                if (notesExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = notes,
                            onValueChange = onNotesChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Notes") },
                            minLines = 3,
                        )
                        TextButton(onClick = { notesExpanded = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Done") }
                    }
                } else {
                    Text(notes.ifBlank { "No notes" }, style = MaterialTheme.typography.bodyLarge)
                }
            }
            ReviewFieldRow("Tags", onEdit = onAddTag, editingEnabled = editingEnabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    tags.filter(String::isNotBlank).forEach { tag ->
                        AssistChip(enabled = editingEnabled, onClick = onAddTag, label = { Text(tag) }, modifier = Modifier.semantics { contentDescription = "Tag $tag" })
                    }
                    AssistChip(enabled = editingEnabled, onClick = onAddTag, label = { Text("Add tag") }, modifier = Modifier.semantics { contentDescription = "Add tag" })
                }
            }
            sendPresentation?.let { presentation ->
                when (presentation) {
                    ReviewSendPresentation.Ready -> Button(
                        onClick = { onSend?.invoke() }, enabled = onSend != null,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text("Send to AnkiDroid") }
                    ReviewSendPresentation.Sending -> Button(
                        onClick = {}, enabled = false,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { stateDescription = "Sending" },
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Sending…")
                    }
                    is ReviewSendPresentation.Status -> Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text(presentation.message, style = MaterialTheme.typography.bodyMedium)
                        sendStatusContent()
                        if (presentation.actionLabel != null) TextButton(
                            onClick = { onSendStatusAction?.invoke() }, enabled = onSendStatusAction != null,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text(presentation.actionLabel) }
                    }
                }
            }
        }
    }
    editingField?.let { field ->
        ModalBottomSheet(onDismissRequest = { editingField = null }) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (field) {
                    "Image" -> {
                        Text("Edit image", style = MaterialTheme.typography.titleLarge)
                        Text("Choose a different image or remove the current one.", style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { editingField = null; onChooseImage() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Choose image") }
                        TextButton(onClick = { editingField = null; onRemoveImage() }, enabled = imagePath != null && imageRemovable, modifier = Modifier.heightIn(min = 48.dp)) { Text("Remove image") }
                        if (!imageRemovable) {
                            Text("This draft came from a photo, so it keeps an image.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    "Sentence" -> {
                        Text("Edit sentence", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Correct the reading of one kanji span. The sentence itself is never changed.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (correctableReadings.isEmpty()) {
                            Text("No kanji span needs a reading.", style = MaterialTheme.typography.bodyMedium)
                        }
                        correctableReadings.forEach { row ->
                            val active = correctingSpan?.span == row.span
                            if (active) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = readingDraft,
                                        onValueChange = { readingDraft = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        label = { Text("Reading for ${row.surface} (kana)") },
                                    )
                                    readingCorrectionError?.let { message ->
                                        Text(message, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                if (onCorrectReading(row, readingDraft)) correctingSpan = null
                                            },
                                            modifier = Modifier.heightIn(min = 48.dp),
                                        ) { Text("Done") }
                                        TextButton(
                                            onClick = { correctingSpan = null },
                                            modifier = Modifier.heightIn(min = 48.dp),
                                        ) { Text("Cancel") }
                                    }
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        correctingSpan = row
                                        readingDraft = row.reading
                                    },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                ) {
                                    Text(
                                        if (row.reading.isBlank()) {
                                            "${row.surface} · no reading yet"
                                        } else {
                                            "${row.surface} · ${row.reading}"
                                        },
                                    )
                                }
                            }
                        }
                        TextButton(
                            onClick = { editingField = null; onEdit() },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text("Edit sentence or target") }
                    }
                    "Word" -> {
                        Text("Edit word", style = MaterialTheme.typography.titleLarge)
                        Text("Tap a token to change the target.", style = MaterialTheme.typography.bodyMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            tokens.forEach { token ->
                                val selected = token.span == target
                                val onSelect = { onSelectToken(token.span); editingField = null }
                                if (selected) {
                                    Button(onClick = onSelect, modifier = Modifier.heightIn(min = 48.dp)) { Text(token.surface) }
                                } else {
                                    OutlinedButton(onClick = onSelect, modifier = Modifier.heightIn(min = 48.dp)) { Text(token.surface) }
                                }
                            }
                        }
                        TextButton(onClick = { editingField = null; onEdit() }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Select a multi-word phrase instead")
                        }
                        Text("Vocabulary reading", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Changing the reading re-resolves the dictionary entry, pitch and recording. A reading with no matching entry blocks Send.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedTextField(
                            value = vocabularyReadingDraft,
                            onValueChange = { vocabularyReadingDraft = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Vocabulary reading (kana)") },
                        )
                        vocabularyReadingError?.let { message ->
                            Text(message, style = MaterialTheme.typography.bodySmall)
                        }
                        Button(
                            onClick = {
                                if (onVocabularyReadingChange(vocabularyReadingDraft)) editingField = null
                            },
                            enabled = vocabularyReadingDraft.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) { Text("Apply reading") }
                    }
                    "Definition" -> {
                        Text("Edit definition", style = MaterialTheme.typography.titleLarge)
                        if (definitionSenses.isNotEmpty()) {
                            Text("Select the applicable senses:", style = MaterialTheme.typography.titleSmall)
                            definitionSenses.forEachIndexed { index, sense ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = index in selectedSenseIndices,
                                        onCheckedChange = { checked -> onToggleSense(index, checked) },
                                    )
                                    Column {
                                        Text(sense.glosses.joinToString("; "))
                                        if (sense.partsOfSpeech.isNotEmpty()) {
                                            Text(sense.partsOfSpeech.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                        OutlinedTextField(
                            value = definition,
                            onValueChange = onDefinitionChange,
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            label = { Text("Definition") },
                        )
                        Button(onClick = { editingField = null }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Done") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewFieldRow(
    label: String,
    onEdit: () -> Unit,
    audio: ReviewAudioPresentation? = null,
    onToggleAudio: (() -> Unit)? = null,
    onRetryAudio: (() -> Unit)? = null,
    generating: Boolean = false,
    editingEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).semantics(mergeDescendants = false) { contentDescription = label }) {
            content()
        }
        audio?.let { state ->
            ReviewAudioControl(label, state, onToggleAudio, onRetryAudio)
        }
        if (generating) Box(Modifier.size(48.dp).semantics {
            contentDescription = "Generating $label"
            stateDescription = "In progress"
        }, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        IconButton(enabled = editingEnabled, onClick = onEdit, modifier = Modifier.size(48.dp).semantics { contentDescription = "Edit $label" }) {
            Icon(MaterialSymbols.Edit, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ReviewAudioControl(
    field: String,
    audio: ReviewAudioPresentation,
    onToggle: (() -> Unit)?,
    onRetry: (() -> Unit)?,
) {
    var failureExpanded by remember(audio) { mutableStateOf(false) }
    when (audio) {
        ReviewAudioPresentation.Unavailable -> Box(
            Modifier.size(48.dp).semantics {
                contentDescription = reviewAudioControlLabel(field, audio)
                stateDescription = "Unavailable"
            },
            contentAlignment = Alignment.Center,
        ) {
            Icon(MaterialSymbols.ErrorOutline, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ReviewAudioPresentation.Ready, ReviewAudioPresentation.Playing -> if (onToggle == null) Box(
            Modifier.size(48.dp).semantics {
                contentDescription = reviewAudioControlLabel(field, ReviewAudioPresentation.Unavailable)
                stateDescription = "Unavailable"
            },
            contentAlignment = Alignment.Center,
        ) {
            Icon(MaterialSymbols.ErrorOutline, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        } else IconButton(
            onClick = onToggle,
            modifier = Modifier.size(48.dp).semantics {
                contentDescription = reviewAudioControlLabel(field, audio)
                stateDescription = if (audio == ReviewAudioPresentation.Playing) "Playing" else "Ready"
            },
        ) {
            Icon(
                if (audio == ReviewAudioPresentation.Playing) MaterialSymbols.Pause else MaterialSymbols.Play,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ReviewAudioPresentation.Generating -> Box(
            Modifier.size(48.dp).semantics {
                contentDescription = reviewAudioControlLabel(field, audio)
                stateDescription = "In progress"
            },
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        is ReviewAudioPresentation.Failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(
                onClick = { failureExpanded = !failureExpanded },
                modifier = Modifier.size(48.dp).semantics {
                    contentDescription = reviewAudioControlLabel(field, audio)
                    stateDescription = if (failureExpanded) "Expanded" else "Collapsed"
                },
            ) {
                Icon(
                    MaterialSymbols.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (failureExpanded) {
                Text(audio.message, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                onRetry?.let { retry ->
                    TextButton(onClick = retry, modifier = Modifier.heightIn(min = 48.dp)) { Text("Retry") }
                }
            }
        }
    }
}

@Composable
private fun RubyText(surface: String, reading: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (reading.isNotBlank()) Text(reading, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(surface, style = MaterialTheme.typography.bodyLarge)
    }
}
