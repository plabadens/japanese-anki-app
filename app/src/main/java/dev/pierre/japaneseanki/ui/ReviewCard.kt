package dev.pierre.japaneseanki.ui

import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes

import android.content.Context
import android.graphics.Matrix
import android.net.Uri
import android.widget.ImageView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.semantics.Role
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
import dev.pierre.japaneseanki.R
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

internal data class ResourceCopy(@StringRes val resourceId: Int, val formatArgs: List<Any> = emptyList()) {
    fun resolve(context: Context): String = context.getString(resourceId, *formatArgs.toTypedArray())
}

@Composable
private fun resourceCopyText(copy: ResourceCopy): String =
    stringResource(copy.resourceId, *copy.formatArgs.toTypedArray())

internal fun reviewAudioControlLabel(field: String, audio: ReviewAudioPresentation): ResourceCopy = when (audio) {
    ReviewAudioPresentation.Unavailable, ReviewAudioPresentation.NeedsPreparation ->
        ResourceCopy(R.string.legacy_card_audio_prepare_control, listOf(field))
    ReviewAudioPresentation.Ready -> ResourceCopy(R.string.legacy_card_audio_play_control, listOf(field))
    ReviewAudioPresentation.Playing -> ResourceCopy(R.string.legacy_card_audio_stop_control, listOf(field))
    ReviewAudioPresentation.Generating -> ResourceCopy(R.string.legacy_card_audio_generating_control, listOf(field))
    is ReviewAudioPresentation.Advisory -> ResourceCopy(
        R.string.legacy_card_audio_advisory_control,
        listOf(audio.actionLabel, field),
    )
    is ReviewAudioPresentation.Failed -> ResourceCopy(R.string.legacy_card_audio_retry_control, listOf(field))
}

internal fun reviewAudioSendBlocker(field: String, audio: ReviewAudioPresentation): ResourceCopy? = when (audio) {
    ReviewAudioPresentation.Ready, ReviewAudioPresentation.Playing -> null
    ReviewAudioPresentation.Generating -> ResourceCopy(R.string.legacy_card_audio_generating_blocker, listOf(field))
    ReviewAudioPresentation.NeedsPreparation ->
        ResourceCopy(R.string.legacy_card_audio_needs_preparation_blocker, listOf(field))
    ReviewAudioPresentation.Unavailable ->
        ResourceCopy(R.string.legacy_card_audio_unavailable_blocker, listOf(field))
    is ReviewAudioPresentation.Advisory -> ResourceCopy(
        R.string.legacy_card_audio_advisory_blocker,
        listOf(audio.headline, audio.message),
    )
    is ReviewAudioPresentation.Failed -> ResourceCopy(R.string.legacy_card_audio_message, listOf(audio.message))
}

internal fun reviewAudioGenerationFailureMessage(field: String, detail: String, noDetails: String): ResourceCopy {
    val safeDetail = detail.ifBlank { noDetails }.substringBefore(" Tap Retry").trim().trimEnd('.')
    return ResourceCopy(R.string.legacy_card_audio_generation_failed, listOf(field, safeDetail))
}

internal fun reviewAudioPlaybackFailureMessage(field: String): ResourceCopy =
    ResourceCopy(R.string.legacy_card_audio_playback_failed, listOf(field))

internal data class TranslationFailurePresentation(@StringRes val headlineResource: Int, val role: ReviewStatusRole)

internal fun reviewTranslationFailureStatus(message: String): TranslationFailurePresentation = when {
    message.contains("Google Cloud API key", ignoreCase = true) || message.contains("credential", ignoreCase = true) ->
        TranslationFailurePresentation(R.string.legacy_card_translation_setup_needed, ReviewStatusRole.Information)
    message.contains("interrupted", ignoreCase = true) ->
        TranslationFailurePresentation(R.string.legacy_card_translation_interrupted, ReviewStatusRole.Error)
    else -> TranslationFailurePresentation(R.string.legacy_card_translation_failed, ReviewStatusRole.Error)
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

internal val ReviewPreparationActionResource = R.string.legacy_card_edit_preparation
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
    onEditNote: () -> Unit = {},
    onEditTags: () -> Unit = {},
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
    sentenceAudioAction: String = "",
    vocabularyAudioAction: String = "",
    sendPresentation: ReviewSendPresentation? = null,
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
    var photoExpanded by remember { mutableStateOf(false) }
    val preSendStatusAndAction = when {
        sentenceAudio is ReviewAudioPresentation.Advisory ->
            ReviewStatusPresentation(sentenceAudio.headline, ReviewStatusRole.Information, sentenceAudio.actionLabel) to onRetrySentenceAudio
        vocabularyAudio is ReviewAudioPresentation.Advisory ->
            ReviewStatusPresentation(vocabularyAudio.headline, ReviewStatusRole.Information, vocabularyAudio.actionLabel) to onRetryVocabularyAudio
        translationFailure?.role == ReviewStatusRole.Information ->
            ReviewStatusPresentation(
                stringResource(translationFailure.headlineResource),
                translationFailure.role,
                openSettingsActionLabel,
            ) to onOpenSettings
        translationFailure != null ->
            ReviewStatusPresentation(
                stringResource(translationFailure.headlineResource),
                translationFailure.role,
                retryActionLabel,
            ) to onRetryTranslation
        translationPending || "Translation" in generatingFields ->
            ReviewStatusPresentation(translationInProgressLabel, ReviewStatusRole.Information) to null
        sentenceAudio is ReviewAudioPresentation.Failed ->
            ReviewStatusPresentation(sentenceAudio.message, ReviewStatusRole.Error, sentenceAudioActionLabel) to onRetrySentenceAudio
        vocabularyAudio is ReviewAudioPresentation.Failed ->
            ReviewStatusPresentation(vocabularyAudio.message, ReviewStatusRole.Error, vocabularyAudioActionLabel) to onRetryVocabularyAudio
        sentenceAudio == ReviewAudioPresentation.NeedsPreparation || sentenceAudio == ReviewAudioPresentation.Unavailable ->
            ReviewStatusPresentation(sentenceAudioNotReadyLabel, ReviewStatusRole.Warning, sentenceAudioActionLabel) to onRetrySentenceAudio
        vocabularyAudio == ReviewAudioPresentation.NeedsPreparation || vocabularyAudio == ReviewAudioPresentation.Unavailable ->
            ReviewStatusPresentation(wordAudioNotReadyLabel, ReviewStatusRole.Warning, vocabularyAudioActionLabel) to onRetryVocabularyAudio
        translationStale ->
            ReviewStatusPresentation(translationNeedsReviewLabel, ReviewStatusRole.Warning, preparationActionLabel) to onEdit
        sendHint != null ->
            ReviewStatusPresentation(sendHint, sendHintRole, sendHintActionLabel ?: preparationActionLabel) to onSendHintAction
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
                ) { Text("Choose sentence") }
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
                            field = stringResource(R.string.legacy_card_sentence_audio_field),
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
                            field = stringResource(R.string.legacy_card_word_audio_field),
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
                        stringResource(R.string.legacy_card_pitch_data_missing_for_this_reading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (pitchHasAlternatives || pitch == null) {
                        TextButton(onClick = onTogglePitchInfo, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(if (pitch == null) stringResource(R.string.legacy_card_why_is_pitch_missing) else stringResource(R.string.legacy_card_other_pitch_patterns))
                        }
                        if (pitchInfoExpanded) {
                            Text(
                                if (pitch == null) {
                                    stringResource(R.string.legacy_card_this_reading_has_no_source_listed_pattern)
                                } else {
                                    stringResource(R.string.legacy_card_more_than_one_source_pattern_is_listed_for)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                    Text(definition.ifBlank { stringResource(R.string.legacy_card_no_definition_yet) }, style = MaterialTheme.typography.bodyLarge)
                    if (definitionStale) {
                        Text(
                            stringResource(R.string.legacy_card_the_meaning_is_for_earlier_input_return_to),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (tags.isEmpty()) {
                TextButton(
                    enabled = editingEnabled,
                    onClick = onEditTags,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Add tags") }
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth()
                        .clickable(enabled = editingEnabled, role = Role.Button, onClick = onEditTags)
                        .semantics { contentDescription = "Edit tags" },
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    tags.filter(String::isNotBlank).forEach { tag ->
                        Text(
                            tag,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
            if (notes.isBlank()) {
                TextButton(
                    enabled = editingEnabled,
                    onClick = onEditNote,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Add note") }
            } else {
                Text(
                    notes,
                    modifier = Modifier.fillMaxWidth()
                        .clickable(enabled = editingEnabled, role = Role.Button, onClick = onEditNote)
                        .semantics { contentDescription = "Edit note" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            TextButton(
                enabled = editingEnabled,
                onClick = onEdit,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(ReviewPreparationActionLabel) }
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
                            text = stringResource(R.string.send),
                            onClick = { onSend?.invoke() },
                            enabled = onSend != null,
                        )
                    }
                    ReviewSendPresentation.Sending -> Button(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.fillMaxWidth().height(56.dp).semantics { stateDescription = sendingStateDescription },
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.sending))
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
    val viewPhotoDescription = stringResource(R.string.legacy_card_view_whole_photo)
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
                    .semantics { contentDescription = "View photo" },
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
    val photoDescription = stringResource(R.string.legacy_card_full_photo)
    val closeDescription = stringResource(R.string.legacy_card_close_photo)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(Color.Black).navigationBarsPadding(),
        ) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Photo",
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp).semantics { contentDescription = "Close" },
                ) { Text("✕", color = Color.White, fontSize = 24.sp) }
            }
            AndroidView(
                factory = { context -> ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
                update = { it.setImageURI(Uri.fromFile(File(imagePath))) },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
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
    val sentenceStateDescription = if (parts.any { it.unresolvedKanji }) {
        stringResource(R.string.legacy_card_some_kanji_have_no_available_reading)
    } else {
        stringResource(R.string.legacy_card_target_selected, target.textIn(sentence).toString())
    }
    FlowRow(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = sentence
            stateDescription = sentenceStateDescription
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
    Text(stringResource(R.string.legacy_card_sentence_translation), style = MaterialTheme.typography.labelLarge)
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
    val description = resourceCopyText(reviewAudioControlLabel(field, audio))
    val needsPreparationState = stringResource(R.string.legacy_card_audio_needs_preparation_state)
    val unavailableState = stringResource(R.string.legacy_card_audio_unavailable_state)
    val playingState = stringResource(R.string.legacy_card_audio_playing_state)
    val readyState = stringResource(R.string.ready)
    val generatingState = stringResource(R.string.legacy_card_audio_in_progress_state)
    val errorState = stringResource(R.string.legacy_card_audio_error_state)
    val setupNeededState = stringResource(R.string.legacy_card_audio_setup_needed_state)
    when (audio) {
        ReviewAudioPresentation.Unavailable, ReviewAudioPresentation.NeedsPreparation -> {
            val state = if (audio == ReviewAudioPresentation.NeedsPreparation) needsPreparationState else unavailableState
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
                        contentDescription = description
                        stateDescription = if (audio == ReviewAudioPresentation.Playing) playingState else readyState
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
                        contentDescription = description
                        stateDescription = if (audio == ReviewAudioPresentation.Playing) playingState else readyState
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
                contentDescription = description
                stateDescription = generatingState
            },
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) }
        is ReviewAudioPresentation.Advisory, is ReviewAudioPresentation.Failed -> {
            val failed = audio is ReviewAudioPresentation.Failed
            val contentDescription = description
            val state = if (failed) errorState else setupNeededState
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
