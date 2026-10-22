package dev.pierre.japaneseanki.ui

import android.graphics.Matrix
import android.net.Uri
import android.widget.ImageView
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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

internal enum class ReviewStatusRole { Information, Warning, Error }

internal data class ReviewStatusPresentation(
    val headline: String,
    val role: ReviewStatusRole,
    val actionLabel: String? = null,
)

/** Presentation-only state supplied by the owner of sentence and vocabulary audio requests. */
internal sealed interface ReviewAudioPresentation {
    data object Unavailable : ReviewAudioPresentation
    data object NeedsPreparation : ReviewAudioPresentation
    data object Ready : ReviewAudioPresentation
    data object Playing : ReviewAudioPresentation
    data object Generating : ReviewAudioPresentation
    data class Advisory(val headline: String, val message: String, val actionLabel: String) : ReviewAudioPresentation
    data class Failed(val message: String) : ReviewAudioPresentation
}

internal fun reviewAudioControlLabel(field: String, audio: ReviewAudioPresentation): String = when (audio) {
    ReviewAudioPresentation.Unavailable, ReviewAudioPresentation.NeedsPreparation -> "Prepare $field audio"
    ReviewAudioPresentation.Ready -> "Play $field audio"
    ReviewAudioPresentation.Playing -> "Stop $field audio"
    ReviewAudioPresentation.Generating -> "Generating $field audio"
    is ReviewAudioPresentation.Advisory -> "${audio.actionLabel} for $field audio"
    is ReviewAudioPresentation.Failed -> "Retry $field audio"
}

internal fun reviewAudioSendBlocker(field: String, audio: ReviewAudioPresentation): String? = when (audio) {
    ReviewAudioPresentation.Ready, ReviewAudioPresentation.Playing -> null
    ReviewAudioPresentation.Generating -> "$field audio is being generated. Wait for it to finish."
    ReviewAudioPresentation.NeedsPreparation ->
        "$field audio has not been generated. Tap the speaker control to generate it."
    ReviewAudioPresentation.Unavailable ->
        "$field audio is unavailable. Tap the speaker control to check what is needed."
    is ReviewAudioPresentation.Advisory -> "${audio.headline}: ${audio.message}"
    is ReviewAudioPresentation.Failed -> audio.message
}

internal fun reviewAudioGenerationFailureMessage(field: String, detail: String): String =
    "$field audio not generated: ${detail.ifBlank { "No diagnostic details are available." }.substringBefore(" Tap Retry").trim().trimEnd('.')}"

internal fun reviewAudioPlaybackFailureMessage(field: String): String =
    "$field audio could not be played on this device"

internal fun reviewTranslationFailureStatus(message: String): ReviewStatusPresentation = when {
    message.contains("Google Cloud API key", ignoreCase = true) || message.contains("credential", ignoreCase = true) ->
        ReviewStatusPresentation("Translation setup needed", ReviewStatusRole.Information)
    message.contains("interrupted", ignoreCase = true) ->
        ReviewStatusPresentation("Translation interrupted", ReviewStatusRole.Error)
    else -> ReviewStatusPresentation("Translation failed", ReviewStatusRole.Error)
}

internal enum class ReviewEditableField { Notes, Tags }

internal fun reviewEditableFields(editing: Boolean, editingEnabled: Boolean): Set<ReviewEditableField> =
    if (editing && editingEnabled) ReviewEditableField.entries.toSet() else emptySet()

/** Callback surface for Review's only editable fields. */
internal class ReviewFieldCallbacks(
    private val onNotesChange: (String) -> Unit,
    private val onTagsChange: (String) -> Unit,
) {
    fun update(field: ReviewEditableField, value: String) {
        when (field) {
            ReviewEditableField.Notes -> onNotesChange(value)
            ReviewEditableField.Tags -> onTagsChange(value)
        }
    }
}

internal const val ReviewPreparationActionLabel = "Edit preparation"
internal val ReviewAudioControlSize = 56.dp

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
 * Review is a composed learning artifact. Preparation owns all content corrections; only notes
 * and tags remain editable here.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
@Suppress("UNUSED_PARAMETER") // Legacy MainActivity arguments are inert until the integration call site is narrowed.
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
    ocrChoicesAvailable: Boolean = false,
    onReopenOcrChooser: () -> Unit = {},
    pitchHasAlternatives: Boolean,
    pitchInfoExpanded: Boolean,
    onTogglePitchInfo: () -> Unit,
    onEdit: () -> Unit,
    onTagsChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    editingEnabled: Boolean = true,
    translationPending: Boolean = false,
    translationError: String? = null,
    translationStale: Boolean = false,
    onRetryTranslation: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    sentenceAudio: ReviewAudioPresentation = ReviewAudioPresentation.Unavailable,
    vocabularyAudio: ReviewAudioPresentation = ReviewAudioPresentation.Unavailable,
    onToggleSentenceAudio: (() -> Unit)? = null,
    onRetrySentenceAudio: (() -> Unit)? = null,
    onToggleVocabularyAudio: (() -> Unit)? = null,
    onRetryVocabularyAudio: (() -> Unit)? = null,
    sentenceAudioAction: String = "Retry",
    vocabularyAudioAction: String = "Retry",
    sendPresentation: ReviewSendPresentation? = null,
    primaryActionLabel: String = "Send to AnkiDroid",
    onSend: (() -> Unit)? = null,
    onSendStatusAction: (() -> Unit)? = null,
    sendStatusContent: @Composable () -> Unit = {},
    imageFocalY: Float = 0.5f,
    sendHint: String? = null,
    sendHintActionLabel: String? = null,
    sendHintRole: ReviewStatusRole = ReviewStatusRole.Warning,
    onSendHintAction: (() -> Unit)? = null,
    // Deprecated caller inputs retained temporarily so this component branch remains buildable
    // before MainActivity's integration-owned call-site cleanup. None expose Review controls.
    generatingFields: Set<String> = emptySet(),
    translationSuggestion: String? = null,
    translationEditable: Boolean = false,
    onTranslationChange: (String) -> Unit = {},
    onKeepMyTranslation: () -> Unit = {},
    onUseTranslationSuggestion: () -> Unit = {},
    onChooseImage: () -> Unit = {},
    onRemoveImage: () -> Unit = {},
    imageRemovable: Boolean = true,
    onDefinitionChange: (String) -> Unit = {},
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
    deckLabel: String = "",
    onChooseDeck: (() -> Unit)? = null,
    pitchAlternativeAccents: List<Int> = emptyList(),
    selectedPitchAccent: Int? = null,
    onSelectPitch: (Int) -> Unit = {},
) {
    var editing by remember { mutableStateOf(false) }
    var notesDraft by remember(notes) { mutableStateOf(notes) }
    var tagsDraft by remember(tags) { mutableStateOf(tags.joinToString(" ")) }
    var photoExpanded by remember { mutableStateOf(false) }
    val editableFields = reviewEditableFields(editing, editingEnabled)
    val fieldCallbacks = remember(onNotesChange, onTagsChange) {
        ReviewFieldCallbacks(onNotesChange, onTagsChange)
    }
    val preSendStatusAndAction = when {
        sentenceAudio is ReviewAudioPresentation.Advisory ->
            ReviewStatusPresentation(sentenceAudio.headline, ReviewStatusRole.Information, sentenceAudio.actionLabel) to onRetrySentenceAudio
        vocabularyAudio is ReviewAudioPresentation.Advisory ->
            ReviewStatusPresentation(vocabularyAudio.headline, ReviewStatusRole.Information, vocabularyAudio.actionLabel) to onRetryVocabularyAudio
        translationError != null && reviewTranslationFailureStatus(translationError).role == ReviewStatusRole.Information ->
            reviewTranslationFailureStatus(translationError).copy(actionLabel = "Open Settings") to onOpenSettings
        translationError != null ->
            reviewTranslationFailureStatus(translationError).copy(actionLabel = "Retry") to onRetryTranslation
        translationPending || "Translation" in generatingFields ->
            ReviewStatusPresentation("Translation in progress", ReviewStatusRole.Information) to null
        sentenceAudio is ReviewAudioPresentation.Failed ->
            ReviewStatusPresentation(sentenceAudio.message, ReviewStatusRole.Error, sentenceAudioAction) to onRetrySentenceAudio
        vocabularyAudio is ReviewAudioPresentation.Failed ->
            ReviewStatusPresentation(vocabularyAudio.message, ReviewStatusRole.Error, vocabularyAudioAction) to onRetryVocabularyAudio
        sentenceAudio == ReviewAudioPresentation.NeedsPreparation || sentenceAudio == ReviewAudioPresentation.Unavailable ->
            ReviewStatusPresentation("Sentence audio is not ready", ReviewStatusRole.Warning, sentenceAudioAction) to onRetrySentenceAudio
        vocabularyAudio == ReviewAudioPresentation.NeedsPreparation || vocabularyAudio == ReviewAudioPresentation.Unavailable ->
            ReviewStatusPresentation("Word audio is not ready", ReviewStatusRole.Warning, vocabularyAudioAction) to onRetryVocabularyAudio
        translationStale ->
            ReviewStatusPresentation("Translation needs review", ReviewStatusRole.Warning, ReviewPreparationActionLabel) to onEdit
        sendHint != null ->
            ReviewStatusPresentation(sendHint, sendHintRole, sendHintActionLabel ?: ReviewPreparationActionLabel) to onSendHintAction
        else -> null to null
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (ocrChoicesAvailable) {
                TextButton(
                    enabled = editingEnabled,
                    onClick = onReopenOcrChooser,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Choose text from photo") }
            }
            if (imagePath != null || imageStatus != null) {
                ReviewPhotoSlot(
                    imagePath = imagePath,
                    imageStatus = imageStatus,
                    focusY = imageFocalY,
                    onExpand = { photoExpanded = true },
                )
            }

            FlowCard {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SentenceWithRuby(
                            sentence = sentence,
                            target = target,
                            sentenceReading = sentenceReading,
                            furigana = furigana,
                            modifier = Modifier.weight(1f),
                        )
                        ReviewAudioButton(
                            field = "Sentence",
                            audio = sentenceAudio,
                            onToggle = onToggleSentenceAudio,
                            onRetry = onRetrySentenceAudio,
                        )
                    }
                    ReviewAudioNotice(sentenceAudio, Modifier.fillMaxWidth().height(24.dp))
                }
            }

            TranslationBlock(
                translation = translation,
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
                        ReviewAudioButton(
                            field = "Word",
                            audio = vocabularyAudio,
                            onToggle = onToggleVocabularyAudio,
                            onRetry = onRetryVocabularyAudio,
                        )
                    }
                    ReviewAudioNotice(vocabularyAudio, Modifier.fillMaxWidth().height(24.dp))
                    pitch?.let {
                        // Labels are katakana; the underlying source reading and HTML stay intact.
                        PitchPatternDiagram(pitch = it)
                    } ?: Text(
                        "Pitch data missing for this reading.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (pitchHasAlternatives || pitch == null) {
                        TextButton(onClick = onTogglePitchInfo, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(if (pitch == null) "Why is pitch missing?" else "Other pitch patterns")
                        }
                        if (pitchInfoExpanded) {
                            Text(
                                if (pitch == null) {
                                    "This reading has no source-listed pattern. Return to preparation to correct the reading."
                                } else {
                                    "More than one source pattern is listed for this reading. Choose another during preparation."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                    Text(definition.ifBlank { "No definition yet." }, style = MaterialTheme.typography.bodyLarge)
                    if (definitionStale) {
                        Text(
                            "The meaning is for earlier input. Return to preparation to review it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (editableFields.isEmpty()) {
                if (tags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        tags.filter(String::isNotBlank).forEach { tag ->
                            AssistChip(
                                onClick = { editing = true },
                                enabled = editingEnabled,
                                label = { Text(tag) },
                                modifier = Modifier.semantics { contentDescription = "Tag $tag" },
                            )
                        }
                    }
                }
                if (notes.isNotBlank()) {
                    Text(notes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                OutlinedTextField(
                    value = notesDraft,
                    onValueChange = { value ->
                        notesDraft = value
                        fieldCallbacks.update(ReviewEditableField.Notes, value)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Notes (optional)") },
                    minLines = 2,
                )
                OutlinedTextField(
                    value = tagsDraft,
                    onValueChange = { value ->
                        tagsDraft = value
                        fieldCallbacks.update(ReviewEditableField.Tags, value)
                    },
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
                    onClick = { onEdit() },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(ReviewPreparationActionLabel) }
                Spacer(Modifier.weight(1f))
                TextButton(
                    enabled = editingEnabled,
                    onClick = { editing = !editing },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(if (editing) "Done" else "Edit notes & tags") }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(72.dp)) {
                if (sendPresentation == ReviewSendPresentation.Ready) {
                    ReviewStatusRegion(
                        status = preSendStatusAndAction.first,
                        onAction = preSendStatusAndAction.second,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            sendPresentation?.let { presentation ->
                when (presentation) {
                    ReviewSendPresentation.Ready -> {
                        FlowPrimaryButton(
                            text = primaryActionLabel,
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
            IconButton(
                onClick = onExpand,
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(48.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
                    .semantics { contentDescription = "View whole photo" },
            ) { FlowIcons.Expand(Modifier.size(22.dp)) }
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
            IconButton(
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
                            if (part.highlighted) Modifier.background(highlightColor, RoundedCornerShape(3.dp))
                            else Modifier,
                        )
                        .then(
                            if (part.unresolvedKanji) Modifier.drawDottedUnderline(unresolvedColor) else Modifier,
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
) {
    Text("Sentence translation", style = MaterialTheme.typography.labelLarge)
    if (translation.isNotBlank()) {
        Text(translation, style = MaterialTheme.typography.bodyLarge)
    } else {
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ReviewAudioButton(
    field: String,
    audio: ReviewAudioPresentation,
    onToggle: (() -> Unit)?,
    onRetry: (() -> Unit)?,
) {
    val controlSize = ReviewAudioControlSize
    when (audio) {
        ReviewAudioPresentation.Unavailable, ReviewAudioPresentation.NeedsPreparation -> {
            val description = reviewAudioControlLabel(field, audio)
            val state = if (audio == ReviewAudioPresentation.NeedsPreparation) "Needs preparation" else "Unavailable"
            if (onRetry == null) {
                Box(
                    Modifier.size(controlSize).semantics {
                        contentDescription = description
                        stateDescription = state
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    AudioControlGlyph(
                        playing = false,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp),
                    )
                }
            } else {
                IconButton(
                    onClick = onRetry,
                    modifier = Modifier.size(controlSize).semantics {
                        contentDescription = description
                        stateDescription = state
                    },
                ) {
                    AudioControlGlyph(
                        playing = false,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
        ReviewAudioPresentation.Ready, ReviewAudioPresentation.Playing -> {
            if (onToggle == null) {
                Box(
                    Modifier.size(controlSize).semantics {
                        contentDescription = reviewAudioControlLabel(field, audio)
                        stateDescription = if (audio == ReviewAudioPresentation.Playing) "Playing" else "Ready"
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    AudioControlGlyph(
                        playing = audio == ReviewAudioPresentation.Playing,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                    )
                }
            } else {
                IconButton(
                    onClick = onToggle,
                    modifier = Modifier.size(controlSize).semantics {
                        contentDescription = reviewAudioControlLabel(field, audio)
                        stateDescription = if (audio == ReviewAudioPresentation.Playing) "Playing" else "Ready"
                    },
                ) {
                    AudioControlGlyph(
                        playing = audio == ReviewAudioPresentation.Playing,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
        ReviewAudioPresentation.Generating -> Box(
            Modifier.size(controlSize).semantics {
                contentDescription = reviewAudioControlLabel(field, audio)
                stateDescription = "In progress"
            },
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) }
        is ReviewAudioPresentation.Advisory, is ReviewAudioPresentation.Failed -> {
            val failed = audio is ReviewAudioPresentation.Failed
            val contentDescription = reviewAudioControlLabel(field, audio)
            val state = if (failed) "Error" else "Setup needed"
            val symbol = if (failed) MaterialSymbols.ErrorOutline else MaterialSymbols.InfoOutline
            val tint = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            if (onRetry == null) {
                Box(
                    Modifier.size(controlSize).semantics {
                        this.contentDescription = contentDescription
                        stateDescription = state
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(symbol, contentDescription = null, modifier = Modifier.size(26.dp), tint = tint)
                }
            } else {
                IconButton(
                    onClick = onRetry,
                    modifier = Modifier.size(controlSize).semantics {
                        this.contentDescription = contentDescription
                        stateDescription = state
                    },
                ) {
                    Icon(symbol, contentDescription = null, modifier = Modifier.size(26.dp), tint = tint)
                }
            }
        }
    }
}

@Composable
private fun ReviewAudioNotice(audio: ReviewAudioPresentation, modifier: Modifier = Modifier) {
    val failed = audio is ReviewAudioPresentation.Failed
    val advisory = audio as? ReviewAudioPresentation.Advisory
    val message = when {
        advisory != null -> advisory.message
        audio is ReviewAudioPresentation.Failed -> audio.message.substringBefore(':').trim()
        else -> null
    }
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        if (message != null) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(8.dp),
                color = if (failed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (failed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Box(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 2.dp), contentAlignment = Alignment.CenterStart) {
                    Text(message, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ReviewStatusRegion(
    status: ReviewStatusPresentation?,
    onAction: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (status == null) return
    val isError = status.role == ReviewStatusRole.Error
    val background = when (status.role) {
        ReviewStatusRole.Error -> MaterialTheme.colorScheme.errorContainer
        ReviewStatusRole.Warning -> MaterialTheme.colorScheme.tertiaryContainer
        ReviewStatusRole.Information -> MaterialTheme.colorScheme.surfaceVariant
    }
    val foreground = when (status.role) {
        ReviewStatusRole.Error -> MaterialTheme.colorScheme.onErrorContainer
        ReviewStatusRole.Warning -> MaterialTheme.colorScheme.onTertiaryContainer
        ReviewStatusRole.Information -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = background,
        contentColor = foreground,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (isError) MaterialSymbols.ErrorOutline else MaterialSymbols.InfoOutline,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (isError) MaterialTheme.colorScheme.error else foreground,
            )
            Text(
                status.headline,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
            )
            if (status.actionLabel != null && onAction != null) {
                TextButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(status.actionLabel, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun AudioControlGlyph(playing: Boolean, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val speaker = Path().apply {
            moveTo(size.width * 0.08f, size.height * 0.39f)
            lineTo(size.width * 0.30f, size.height * 0.39f)
            lineTo(size.width * 0.54f, size.height * 0.18f)
            lineTo(size.width * 0.54f, size.height * 0.82f)
            lineTo(size.width * 0.30f, size.height * 0.61f)
            lineTo(size.width * 0.08f, size.height * 0.61f)
            close()
        }
        drawPath(speaker, color)
        if (playing) {
            drawRect(
                color,
                topLeft = Offset(size.width * 0.69f, size.height * 0.36f),
                size = Size(size.width * 0.20f, size.height * 0.28f),
            )
        } else {
            val waveStroke = Stroke(width = size.minDimension * 0.065f, cap = StrokeCap.Round)
            drawArc(
                color = color,
                startAngle = -55f,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = Offset(size.width * 0.39f, size.height * 0.30f),
                size = Size(size.width * 0.38f, size.height * 0.40f),
                style = waveStroke,
            )
            drawArc(
                color = color,
                startAngle = -55f,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = Offset(size.width * 0.39f, size.height * 0.15f),
                size = Size(size.width * 0.58f, size.height * 0.70f),
                style = waveStroke,
            )
        }
    }
}
