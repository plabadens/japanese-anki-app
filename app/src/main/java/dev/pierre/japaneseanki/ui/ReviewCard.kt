package dev.pierre.japaneseanki.ui

import android.graphics.Matrix
import android.net.Uri
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.pierre.japaneseanki.CorrectableReading
import dev.pierre.japaneseanki.DictionarySense
import dev.pierre.japaneseanki.FuriganaSpan
import dev.pierre.japaneseanki.JapaneseToken
import dev.pierre.japaneseanki.OcrBounds
import dev.pierre.japaneseanki.PitchAccent
import dev.pierre.japaneseanki.PitchPatternDiagram
import dev.pierre.japaneseanki.TargetSpan
import java.io.File
import kotlin.math.max

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

/** Height of the Review context photo slot in the portrait composition. */
internal val ReviewPhotoHeight = 144.dp

/**
 * Matrix for a cover crop that keeps [focusY] (0 = top, 1 = bottom) of the source image in the
 * middle of the view. Returns the scale and the x/y offsets to apply after scaling.
 */
internal fun focalCropOffsets(
    imageWidth: Int,
    imageHeight: Int,
    viewWidth: Int,
    viewHeight: Int,
    focusY: Float,
): Triple<Float, Float, Float> {
    if (imageWidth <= 0 || imageHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) {
        return Triple(1f, 0f, 0f)
    }
    val scale = max(viewWidth.toFloat() / imageWidth, viewHeight.toFloat() / imageHeight)
    val scaledWidth = imageWidth * scale
    val scaledHeight = imageHeight * scale
    val dx = (viewWidth - scaledWidth) / 2f
    val dy = (viewHeight - scaledHeight) * focusY.coerceIn(0f, 1f)
    return Triple(scale, dx, dy)
}

/** Vertical focal fraction (0 = top) for a photo slot centred on a recognised text block. */
internal fun ocrFocusFraction(bounds: OcrBounds, sourceHeight: Int): Float {
    if (sourceHeight <= 0 || bounds.bottom <= bounds.top) return 0.5f
    val center = (bounds.top + bounds.bottom) / 2f
    return (center / sourceHeight).coerceIn(0f, 1f)
}

/**
 * Review is a composed learning artifact, not a field form:
 * photo → continuous Japanese/ruby → translation directly beneath → vocabulary/pitch/definition.
 */
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
    onTagsChange: (String) -> Unit = {},
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
    definitionStale: Boolean = false,
    onAcknowledgeDefinition: () -> Unit = {},
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
    translationError: String? = null,
    translationStale: Boolean = false,
    translationSuggestion: String? = null,
    translationEditable: Boolean = true,
    editingEnabled: Boolean = true,
    onTranslationChange: (String) -> Unit = {},
    onRetryTranslation: () -> Unit = {},
    onKeepMyTranslation: () -> Unit = {},
    onUseTranslationSuggestion: () -> Unit = {},
    sendPresentation: ReviewSendPresentation? = null,
    onSend: (() -> Unit)? = null,
    onSendStatusAction: (() -> Unit)? = null,
    sendStatusContent: @Composable () -> Unit = {},
    imageFocalY: Float = 0.5f,
    deckLabel: String = "",
    onChooseDeck: (() -> Unit)? = null,
    sendHint: String? = null,
    pitchAlternativeAccents: List<Int> = emptyList(),
    selectedPitchAccent: Int? = null,
    onSelectPitch: (Int) -> Unit = {},
) {
    var editing by remember { mutableStateOf(false) }
    var editingField by remember { mutableStateOf<String?>(null) }
    var correctingSpan by remember { mutableStateOf<CorrectableReading?>(null) }
    var readingDraft by remember { mutableStateOf("") }
    var vocabularyReadingDraft by remember(vocabularyReading) { mutableStateOf(vocabularyReading) }
    var notesDraft by remember(notes) { mutableStateOf(notes) }
    var tagsDraft by remember(tags) { mutableStateOf(tags.joinToString(" ")) }
    var photoExpanded by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (imagePath != null || imageStatus != null) {
                ReviewPhotoSlot(
                    imagePath = imagePath,
                    imageStatus = imageStatus,
                    focusY = imageFocalY,
                    onExpand = { photoExpanded = true },
                )
                if (editing && editingEnabled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onChooseImage, modifier = Modifier.heightIn(min = 48.dp)) { Text("Replace photo") }
                        TextButton(
                            enabled = imageRemovable,
                            onClick = onRemoveImage,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text("Remove photo") }
                        if (!imageRemovable) {
                            Text(
                                "A photo-origin draft keeps its photo.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.CenterVertically),
                            )
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                SentenceWithRuby(
                    sentence = sentence,
                    target = target,
                    sentenceReading = sentenceReading,
                    furigana = furigana,
                    modifier = Modifier.weight(1f),
                )
                ReviewAudioButton("Sentence", sentenceAudio, onToggleSentenceAudio, onRetrySentenceAudio, small = true)
            }
            if (editing && editingEnabled) {
                TextButton(onClick = { editingField = "Sentence" }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Fix sentence reading")
                }
            }

            TranslationBlock(
                translation = translation,
                pending = "Translation" in generatingFields,
                error = translationError,
                stale = translationStale,
                suggestion = translationSuggestion,
                editable = translationEditable && editing && editingEnabled,
                onChange = onTranslationChange,
                onRetry = onRetryTranslation,
                onKeepMine = onKeepMyTranslation,
                onUseSuggestion = onUseTranslationSuggestion,
            )

            FlowCard(Modifier.padding(top = 8.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(vocabulary, style = MaterialTheme.typography.displaySmall)
                            if (vocabularyReading.isNotBlank()) {
                                Text(
                                    vocabularyReading,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                        ReviewAudioButton("Word", vocabularyAudio, onToggleVocabularyAudio, onRetryVocabularyAudio)
                    }
                    pitch?.let {
                        // The study sets the contour at learning scale, with 22 sp katakana
                        // morae labels beneath it.
                        PitchPatternDiagram(pitch = it)
                    } ?: Text(
                        "No pitch pattern is available for this reading.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    if (pitchHasAlternatives || pitch == null) {
                        TextButton(onClick = onTogglePitchInfo, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(if (pitch == null) "Why is pitch missing?" else "Other pitch patterns")
                        }
                        if (pitchInfoExpanded) {
                            Text(
                                if (pitch == null) {
                                    "This reading has no source-listed pattern. Send stays blocked until it is corrected."
                                } else {
                                    "More than one source pattern is listed for this reading; the first is shown. Choose another under Edit."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                    if (editing && editingEnabled && pitchAlternativeAccents.size > 1) {
                        Text(
                            "Source-listed pitch patterns",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pitchAlternativeAccents.forEach { accent ->
                                if (accent == selectedPitchAccent) {
                                    Button(onClick = { onSelectPitch(accent) }) { Text("Accent $accent") }
                                } else {
                                    OutlinedButton(onClick = { onSelectPitch(accent) }) { Text("Accent $accent") }
                                }
                            }
                        }
                    }
                    if (vocabularyReadingError != null) {
                        Text(
                            vocabularyReadingError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Text(definition.ifBlank { "No definition yet." }, style = MaterialTheme.typography.bodyLarge)
                    if (definitionStale) {
                        Text(
                            "The sentence, target or dictionary entry changed after you edited this meaning.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(
                            onClick = onAcknowledgeDefinition,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text("I've reviewed this meaning") }
                    }
                    if (editing && editingEnabled) {
                        TextButton(
                            onClick = { editingField = "Word" },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text("Change word or reading") }
                        TextButton(
                            onClick = { editingField = "Definition" },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text("Edit meaning") }
                    }
                    if (vocabularyAudioProgress != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(vocabularyAudioProgress, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                if (onCancelVocabularyAudio != null) {
                                    TextButton(onClick = onCancelVocabularyAudio, modifier = Modifier.heightIn(min = 48.dp)) {
                                        Text("Cancel")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tags.filter(String::isNotBlank).forEach { tag ->
                        androidx.compose.material3.AssistChip(
                            onClick = { editing = true },
                            label = { Text(tag) },
                            modifier = Modifier.semantics { contentDescription = "Tag $tag" },
                        )
                    }
                }
            }
            if (notes.isNotBlank()) {
                Text(notes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (editing && editingEnabled) {
                OutlinedTextField(
                    value = notesDraft,
                    onValueChange = { notesDraft = it; onNotesChange(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Notes (optional)") },
                    minLines = 2,
                )
                OutlinedTextField(
                    value = tagsDraft,
                    onValueChange = { tagsDraft = it; onTagsChange(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Tags (optional)") },
                    singleLine = true,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    enabled = editingEnabled,
                    onClick = { editing = !editing },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(if (editing) "Done editing" else "Edit") }
                IconButton(
                    enabled = editingEnabled,
                    onClick = { editing = true },
                    modifier = Modifier.size(48.dp).semantics { contentDescription = "Add tag" },
                ) { Icon(MaterialSymbols.Add, contentDescription = null, modifier = Modifier.size(20.dp)) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onChooseDeck?.invoke() }, enabled = onChooseDeck != null && editingEnabled) {
                    Text("Deck · ${deckLabel.ifBlank { "Choose" }}", style = MaterialTheme.typography.bodyMedium)
                    FlowIcons.ChevronDown(Modifier.size(16.dp))
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            sendPresentation?.let { presentation ->
                when (presentation) {
                    ReviewSendPresentation.Ready -> Column {
                        if (sendHint != null) {
                            Text(
                                sendHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        FlowPrimaryButton(
                            text = "Send to AnkiDroid",
                            onClick = { onSend?.invoke() },
                            enabled = onSend != null,
                        )
                    }
                    ReviewSendPresentation.Sending -> Button(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.fillMaxWidth().height(56.dp).semantics { stateDescription = "Sending" },
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Sending…")
                    }
                    is ReviewSendPresentation.Status -> Column(Modifier.fillMaxWidth()) {
                        Text(presentation.message, style = MaterialTheme.typography.bodyMedium)
                        sendStatusContent()
                        if (presentation.actionLabel != null) {
                            FlowPrimaryButton(
                                text = presentation.actionLabel,
                                onClick = { onSendStatusAction?.invoke() },
                                enabled = onSendStatusAction != null,
                                showArrow = false,
                            )
                        }
                    }
                }
            }
        }
    }

    if (photoExpanded && imagePath != null) {
        FullPhotoDialog(imagePath) { photoExpanded = false }
    }

    editingField?.let { field ->
        ModalBottomSheet(onDismissRequest = { editingField = null }) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 640.dp).verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (field) {
                    "Sentence" -> {
                        Text("Sentence reading", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Correct the reading of one kanji span. The sentence text never changes.",
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
                                        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { if (onCorrectReading(row, readingDraft)) correctingSpan = null },
                                            modifier = Modifier.heightIn(min = 48.dp),
                                        ) { Text("Done") }
                                        TextButton(onClick = { correctingSpan = null }, modifier = Modifier.heightIn(min = 48.dp)) {
                                            Text("Cancel")
                                        }
                                    }
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { correctingSpan = row; readingDraft = row.reading },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                ) {
                                    Text(if (row.reading.isBlank()) "${row.surface} · no reading yet" else "${row.surface} · ${row.reading}")
                                }
                            }
                        }
                        TextButton(onClick = { editingField = null; onEdit() }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Change sentence or target")
                        }
                    }
                    "Definition" -> {
                        Text("Edit meaning", style = MaterialTheme.typography.titleLarge)
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
                                            Text(
                                                sense.partsOfSpeech.joinToString(", "),
                                                style = MaterialTheme.typography.bodySmall,
                                            )
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
                        Button(onClick = { editingField = null }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text("Done")
                        }
                    }
                    "Word" -> WordSheet(
                        tokens = tokens,
                        target = target,
                        onSelectToken = { onSelectToken(it); editingField = null },
                        vocabularyReading = vocabularyReading,
                        vocabularyReadingDraft = vocabularyReadingDraft,
                        onVocabularyReadingDraft = { vocabularyReadingDraft = it },
                        onApplyReading = { if (onVocabularyReadingChange(vocabularyReadingDraft)) editingField = null },
                        vocabularyReadingError = vocabularyReadingError,
                        pitchHasAlternatives = pitchHasAlternatives,
                        onEditSentence = { editingField = null; onEdit() },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewPhotoSlot(
    imagePath: String?,
    imageStatus: String?,
    focusY: Float,
    onExpand: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ReviewPhotoHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (imagePath != null && File(imagePath).isFile) {
            AndroidView(
                factory = { context -> ImageView(context).apply { scaleType = ImageView.ScaleType.MATRIX } },
                update = { view ->
                    view.setImageURI(Uri.fromFile(File(imagePath)))
                    view.post {
                        val drawable = view.drawable ?: return@post
                        if (view.width == 0 || view.height == 0) return@post
                        val (scale, dx, dy) = focalCropOffsets(
                            drawable.intrinsicWidth,
                            drawable.intrinsicHeight,
                            view.width,
                            view.height,
                            focusY,
                        )
                        view.imageMatrix = Matrix().apply {
                            setScale(scale, scale)
                            postTranslate(dx, dy)
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .semantics { contentDescription = "View whole photo" }
                    .then(Modifier),
            ) {
                androidx.compose.material3.IconButton(
                    onClick = onExpand,
                    modifier = Modifier.size(40.dp).semantics { contentDescription = "View whole photo" },
                ) { FlowIcons.Expand(Modifier.size(20.dp)) }
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (imageStatus?.startsWith("Preparing", ignoreCase = true) == true) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        imageStatus.orEmpty(),
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun FullPhotoDialog(imagePath: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .semantics { contentDescription = "Full photo" },
        ) {
            AndroidView(
                factory = { context -> ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
                update = { it.setImageURI(Uri.fromFile(File(imagePath))) },
                modifier = Modifier.fillMaxSize(),
            )
            androidx.compose.material3.IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).size(48.dp)
                    .semantics { contentDescription = "Close photo" },
            ) { Text("✕", color = Color.White, fontSize = 24.sp) }
        }
    }
}

/** Continuous Japanese with proportional ruby and one highlighted occurrence. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SentenceWithRuby(
    sentence: String,
    target: TargetSpan,
    sentenceReading: String,
    furigana: List<FuriganaSpan>,
    modifier: Modifier = Modifier,
) {
    val parts = reviewSentenceParts(sentence, target, sentenceReading, furigana)
    val unresolvedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val highlightColor = MaterialTheme.colorScheme.secondaryContainer
    FlowRow(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = sentence
            if (parts.any { it.unresolvedKanji }) {
                stateDescription = "Some kanji have no available reading"
            } else {
                stateDescription = "Target selected: ${target.textIn(sentence)}"
            }
        },
        horizontalArrangement = Arrangement.Start,
        verticalArrangement = Arrangement.Bottom,
    ) {
        parts.forEach { part ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    part.reading ?: "\u00A0",
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                )
                Text(
                    part.text,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (part.highlighted) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier
                        .then(
                            if (part.highlighted) {
                                Modifier.background(highlightColor, RoundedCornerShape(3.dp))
                            } else {
                                Modifier
                            },
                        )
                        .then(
                            if (part.unresolvedKanji) {
                                Modifier.drawDottedUnderline(unresolvedColor)
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

private fun Modifier.drawDottedUnderline(color: Color): Modifier = this.then(
    Modifier.drawBehind {
        val dot = 1.dp.toPx()
        val gap = 2.dp.toPx()
        var x = dot / 2
        val y = size.height - dot
        while (x < size.width) {
            drawCircle(color, dot / 2, Offset(x, y))
            x += dot + gap
        }
    },
)

@Composable
private fun TranslationBlock(
    translation: String,
    pending: Boolean,
    error: String?,
    stale: Boolean,
    suggestion: String?,
    editable: Boolean,
    onChange: (String) -> Unit,
    onRetry: () -> Unit,
    onKeepMine: () -> Unit,
    onUseSuggestion: () -> Unit,
) {
    if (editable) {
        OutlinedTextField(
            value = translation,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Sentence translation") },
            minLines = 2,
        )
    } else {
        Text(
            when {
                translation.isNotBlank() -> translation
                pending -> "Translating…"
                else -> "Translation is required before Send."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = if (translation.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (pending && translation.isBlank()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Text("Translating…", style = MaterialTheme.typography.bodySmall)
        }
    }
    error?.let { message ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text("Retry") }
        }
    }
    if (stale) {
        Text("Your text is for the previous sentence.", style = MaterialTheme.typography.bodySmall)
        Row {
            TextButton(onClick = onKeepMine, modifier = Modifier.heightIn(min = 48.dp)) { Text("Keep my text") }
            if (suggestion != null) {
                TextButton(onClick = onUseSuggestion, modifier = Modifier.heightIn(min = 48.dp)) { Text("Use new suggestion") }
            }
        }
    }
}

@Composable
private fun ReviewAudioButton(
    field: String,
    audio: ReviewAudioPresentation,
    onToggle: (() -> Unit)?,
    onRetry: (() -> Unit)?,
    small: Boolean = false,
) {
    val size = if (small) 44.dp else 48.dp
    var failureExpanded by remember(audio) { mutableStateOf(false) }
    when (audio) {
        ReviewAudioPresentation.Unavailable -> Box(
            Modifier.size(size).semantics {
                contentDescription = reviewAudioControlLabel(field, audio)
                stateDescription = "Unavailable"
            },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                MaterialSymbols.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ReviewAudioPresentation.Ready, ReviewAudioPresentation.Playing -> if (onToggle == null) {
            Box(Modifier.size(size), contentAlignment = Alignment.Center) {}
        } else {
            IconButton(
                onClick = onToggle,
                modifier = Modifier.size(size).semantics {
                    contentDescription = reviewAudioControlLabel(field, audio)
                    stateDescription = if (audio == ReviewAudioPresentation.Playing) "Playing" else "Ready"
                },
            ) {
                Icon(
                    if (audio == ReviewAudioPresentation.Playing) MaterialSymbols.Pause else MaterialSymbols.Play,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        ReviewAudioPresentation.Generating -> Box(
            Modifier.size(size).semantics {
                contentDescription = reviewAudioControlLabel(field, audio)
                stateDescription = "In progress"
            },
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) }
        is ReviewAudioPresentation.Failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(
                onClick = { failureExpanded = !failureExpanded },
                modifier = Modifier.size(size).semantics {
                    contentDescription = reviewAudioControlLabel(field, audio)
                    stateDescription = if (failureExpanded) "Expanded" else "Collapsed"
                },
            ) {
                Icon(
                    MaterialSymbols.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
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
private fun WordSheet(
    tokens: List<JapaneseToken>,
    target: TargetSpan,
    onSelectToken: (TargetSpan) -> Unit,
    vocabularyReading: String,
    vocabularyReadingDraft: String,
    onVocabularyReadingDraft: (String) -> Unit,
    onApplyReading: () -> Unit,
    vocabularyReadingError: String?,
    pitchHasAlternatives: Boolean,
    onEditSentence: () -> Unit,
) {
    Text("Change word", style = MaterialTheme.typography.titleLarge)
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tokens.forEach { token ->
            val selected = token.span == target
            if (selected) {
                Button(onClick = { onSelectToken(token.span) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(token.surface)
                }
            } else {
                OutlinedButton(onClick = { onSelectToken(token.span) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(token.surface)
                }
            }
        }
    }
    TextButton(onClick = onEditSentence, modifier = Modifier.heightIn(min = 48.dp)) {
        Text("Select a multi-word phrase instead")
    }
    Text("Vocabulary reading", style = MaterialTheme.typography.titleSmall)
    Text(
        "Changing the reading re-resolves the dictionary entry, pitch and recording. A reading with no matching entry blocks Send.",
        style = MaterialTheme.typography.bodySmall,
    )
    OutlinedTextField(
        value = vocabularyReadingDraft,
        onValueChange = onVocabularyReadingDraft,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Vocabulary reading (kana)") },
    )
    vocabularyReadingError?.let { message ->
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    if (pitchHasAlternatives) {
        Text(
            "Other pitch patterns for this reading are available from the pitch control on Review.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    Button(
        onClick = onApplyReading,
        enabled = vocabularyReadingDraft.isNotBlank(),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) { Text("Apply reading") }
}
