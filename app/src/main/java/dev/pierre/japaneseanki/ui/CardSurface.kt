package dev.pierre.japaneseanki.ui

import android.content.Context
import android.graphics.Matrix
import android.net.Uri
import android.widget.ImageView
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.pierre.japaneseanki.FuriganaSpan
import dev.pierre.japaneseanki.JapaneseToken
import dev.pierre.japaneseanki.OcrBounds
import dev.pierre.japaneseanki.PitchAccent
import dev.pierre.japaneseanki.PitchPatternDiagram
import dev.pierre.japaneseanki.R
import dev.pierre.japaneseanki.TargetSpan
import java.io.File
import kotlin.math.max
import kotlinx.coroutines.delay

internal data class ResourceCopy(@StringRes val resourceId: Int, val formatArgs: List<Any> = emptyList()) {
    fun resolve(context: Context): String = context.getString(resourceId, *formatArgs.toTypedArray())
}

/** Presentation-only state supplied by the owner of sentence and vocabulary audio requests. */
internal sealed interface ReviewAudioPresentation {
    data object Unavailable : ReviewAudioPresentation
    data object NeedsPreparation : ReviewAudioPresentation
    data object SourceMissing : ReviewAudioPresentation
    data object Ready : ReviewAudioPresentation
    data object Playing : ReviewAudioPresentation
    data object Generating : ReviewAudioPresentation
    data class Advisory(val headline: String, val message: String, val actionLabel: String) : ReviewAudioPresentation
    data class Failed(val message: String) : ReviewAudioPresentation
}

internal fun reviewAudioControlLabel(field: String, audio: ReviewAudioPresentation): ResourceCopy = when (audio) {
    ReviewAudioPresentation.SourceMissing -> ResourceCopy(R.string.no_recording)
    ReviewAudioPresentation.Unavailable, ReviewAudioPresentation.NeedsPreparation ->
        ResourceCopy(R.string.card_audio_prepare_control, listOf(field))
    ReviewAudioPresentation.Ready -> ResourceCopy(R.string.card_audio_play_control, listOf(field))
    ReviewAudioPresentation.Playing -> ResourceCopy(R.string.card_audio_stop_control, listOf(field))
    ReviewAudioPresentation.Generating -> ResourceCopy(R.string.card_audio_generating_control, listOf(field))
    is ReviewAudioPresentation.Advisory -> ResourceCopy(
        R.string.card_audio_advisory_control,
        listOf(audio.actionLabel, field),
    )
    is ReviewAudioPresentation.Failed -> ResourceCopy(R.string.card_audio_retry_control, listOf(field))
}

internal fun reviewAudioSendBlocker(field: String, audio: ReviewAudioPresentation): ResourceCopy? = when (audio) {
    ReviewAudioPresentation.SourceMissing -> ResourceCopy(R.string.no_recording)
    ReviewAudioPresentation.Ready, ReviewAudioPresentation.Playing -> null
    ReviewAudioPresentation.Generating -> ResourceCopy(R.string.card_audio_generating_blocker, listOf(field))
    ReviewAudioPresentation.NeedsPreparation -> ResourceCopy(R.string.card_audio_needs_preparation_blocker, listOf(field))
    ReviewAudioPresentation.Unavailable -> ResourceCopy(R.string.card_audio_unavailable_blocker, listOf(field))
    is ReviewAudioPresentation.Advisory -> ResourceCopy(
        R.string.card_audio_advisory_blocker,
        listOf(audio.headline, audio.message),
    )
    is ReviewAudioPresentation.Failed -> ResourceCopy(R.string.card_audio_message, listOf(audio.message))
}

internal fun reviewAudioGenerationFailureMessage(field: String, detail: String, noDetails: String): ResourceCopy {
    val safeDetail = detail.ifBlank { noDetails }.substringBefore(" Tap Retry").trim().trimEnd('.')
    return ResourceCopy(R.string.card_audio_generation_failed, listOf(field, safeDetail))
}

internal fun reviewAudioPlaybackFailureMessage(field: String): ResourceCopy =
    ResourceCopy(R.string.card_audio_playback_failed, listOf(field))

internal data class TranslationFailurePresentation(@StringRes val headlineResource: Int)

internal fun reviewTranslationFailureStatus(message: String): TranslationFailurePresentation = when {
    message.contains("Google Cloud API key", ignoreCase = true) || message.contains("credential", ignoreCase = true) ->
        TranslationFailurePresentation(R.string.card_translation_setup_needed)
    message.contains("interrupted", ignoreCase = true) ->
        TranslationFailurePresentation(R.string.card_translation_interrupted)
    else -> TranslationFailurePresentation(R.string.card_translation_failed)
}

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
    val dy = (viewHeight / 2f - scaledHeight * focusY.coerceIn(0f, 1f))
        .coerceIn(viewHeight - scaledHeight, 0f)
    return Triple(scale, dx, dy)
}

internal fun ocrFocusFraction(bounds: OcrBounds, sourceHeight: Int): Float {
    if (sourceHeight <= 0 || bounds.bottom <= bounds.top) return 0.5f
    val center = (bounds.top + bounds.bottom) / 2f
    return (center / sourceHeight).coerceIn(0f, 1f)
}

internal fun cardPhotoBandHeight(width: Dp, availableHeight: Dp, fontScale: Float): Dp =
    minOf(width * (9f / 16f), if (fontScale >= 2f || availableHeight < 560.dp) 96.dp else 219.dp)

internal fun englishPartOfSpeechLabel(tags: List<String>): String? = when {
    tags.any { it.startsWith("adj") } -> "adjective"
    tags.any { it.startsWith("adv") } -> "adverb"
    tags.any { it.startsWith("n") } -> "noun"
    tags.any { it.startsWith("v") } -> "verb"
    tags.any { it == "prt" } -> "particle"
    tags.any { it == "pref" } -> "prefix"
    tags.any { it == "suf" } -> "suffix"
    tags.any { it == "conj" } -> "conjunction"
    tags.any { it == "int" } -> "interjection"
    tags.any { it == "exp" } -> "expression"
    tags.any { it == "ctr" } -> "counter"
    tags.any { it == "pn" } -> "pronoun"
    else -> null
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CardScreen(
    sentence: String,
    tokens: List<JapaneseToken>,
    selectedSpan: TargetSpan?,
    selectionEnabled: Boolean,
    onSelectSpan: (TargetSpan) -> Unit,
    furigana: List<FuriganaSpan>,
    onEditSentence: () -> Unit,
    translation: String,
    vocabulary: String,
    vocabularyReading: String,
    partOfSpeech: String?,
    definition: String,
    pitch: PitchAccent?,
    pitchStatus: String?,
    pitchPending: Boolean,
    wordStatus: String?,
    wordAudioEnabled: Boolean,
    meaningAvailable: Boolean,
    onChangeMeaning: () -> Unit,
    imagePath: String?,
    imageFocusY: Float,
    notes: String,
    tags: String,
    onNotesChange: (String) -> Unit,
    onTagsChange: (String) -> Unit,
    sentenceAudio: ReviewAudioPresentation,
    wordAudio: ReviewAudioPresentation,
    onToggleSentenceAudio: () -> Unit,
    onRetrySentenceAudio: () -> Unit,
    onToggleWordAudio: () -> Unit,
    onRetryWordAudio: () -> Unit,
    hint: String?,
    hintPreparing: Boolean,
    actionLabel: String,
    actionEnabled: Boolean,
    actionLoading: Boolean,
    onAction: () -> Unit,
    statusMessage: String?,
    statusContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    var photoExpanded by remember { mutableStateOf(false) }
    var editingNotes by remember { mutableStateOf(false) }
    var editingTags by remember { mutableStateOf(false) }
    var audioHintLoading by remember { mutableStateOf(false) }
    LaunchedEffect(selectedSpan, wordAudio, hintPreparing) {
        audioHintLoading = false
        if (hintPreparing || (selectedSpan != null && wordAudio == ReviewAudioPresentation.Generating)) {
            delay(200)
            audioHintLoading = true
        }
    }
    val fontScale = LocalConfiguration.current.fontScale
    val density = LocalDensity.current
    val hintHeight = with(density) { MaterialTheme.typography.bodySmall.lineHeight.toDp().coerceAtLeast(24.dp) }

    BoxWithConstraints(modifier) {
        val bandHeight = cardPhotoBandHeight(maxWidth, maxHeight, fontScale)
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                if (imagePath != null && File(imagePath).isFile) {
                    CardPhotoBand(
                        imagePath = imagePath,
                        height = bandHeight,
                        focusY = imageFocusY,
                        onOpen = { photoExpanded = true },
                    )
                }
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 4.dp),
                    verticalArrangement = Arrangement.Top,
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                    ) {
                        SelectableSentence(
                            sentence = sentence,
                            tokens = tokens,
                            selectedSpan = selectedSpan,
                            selectionEnabled = selectionEnabled,
                            onSelectSpan = onSelectSpan,
                            furigana = furigana,
                            modifier = Modifier.weight(1f),
                        )
                        CardAudioButton(
                            field = stringResource(R.string.card_sentence_audio_field),
                            audio = sentenceAudio,
                            onToggle = onToggleSentenceAudio,
                            onRetry = onRetrySentenceAudio,
                        )
                    }
                    TextButton(
                        onClick = onEditSentence,
                        enabled = selectionEnabled,
                        modifier = Modifier.heightIn(min = 40.dp),
                    ) { Text(stringResource(R.string.edit_sentence)) }
                    if (translation.isNotBlank()) {
                        Text(
                            translation,
                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    } else {
                        Spacer(Modifier.height(24.dp))
                    }

                    // The word card owns this slot. Reserving its minimum height prevents late
                    // dictionary/audio results from moving the sentence or translation.
                    Box(Modifier.fillMaxWidth().heightIn(min = 152.dp).padding(top = 8.dp)) {
                        if (selectedSpan != null) {
                            FlowCard {
                                Column(
                                    Modifier.fillMaxWidth().padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Row(verticalAlignment = Alignment.Top) {
                                        Column(Modifier.weight(1f)) {
                                            Text(vocabulary, style = MaterialTheme.typography.displaySmall)
                                            if (vocabularyReading.isNotBlank()) {
                                                Text(
                                                    vocabularyReading,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                )
                                            }
                                            partOfSpeech?.let {
                                                Text(it, style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                        CardAudioButton(
                                            field = stringResource(R.string.card_word_audio_field),
                                            audio = wordAudio,
                                            onToggle = onToggleWordAudio,
                                            onRetry = onRetryWordAudio,
                                            enabled = wordAudioEnabled,
                                            unavailableMessage = wordStatus,
                                        )
                                    }
                                    when {
                                        pitch != null -> PitchPatternDiagram(pitch = pitch)
                                        pitchStatus != null -> Text(
                                            pitchStatus,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        pitchPending -> Spacer(Modifier.height(24.dp))
                                        else -> Text(
                                            stringResource(R.string.no_pitch),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (definition.isNotBlank()) {
                                        Text(definition, style = MaterialTheme.typography.bodyLarge)
                                    }
                                    wordStatus?.let {
                                        Text(
                                            it,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (meaningAvailable) {
                                        TextButton(
                                            onClick = onChangeMeaning,
                                            modifier = Modifier.heightIn(min = 40.dp),
                                        ) { Text(stringResource(R.string.change_meaning)) }
                                    }
                                }
                            }
                        }
                    }

                    if (selectedSpan != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = { editingNotes = !editingNotes },
                                modifier = Modifier.heightIn(min = 40.dp),
                            ) { Text(stringResource(if (notes.isBlank()) R.string.add_note else R.string.edit_note)) }
                            TextButton(
                                onClick = { editingTags = !editingTags },
                                modifier = Modifier.heightIn(min = 40.dp),
                            ) { Text(stringResource(if (tags.isBlank()) R.string.add_tags else R.string.edit_tags)) }
                        }
                        if (notes.isNotBlank() && !editingNotes) {
                            Text(notes, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (tags.isNotBlank() && !editingTags) {
                            Text(tags, style = MaterialTheme.typography.bodySmall)
                        }
                        if (editingNotes) {
                            OutlinedTextField(
                                value = notes,
                                onValueChange = onNotesChange,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.note)) },
                                minLines = 2,
                            )
                        }
                        if (editingTags) {
                            OutlinedTextField(
                                value = tags,
                                onValueChange = onTagsChange,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.tags)) },
                                singleLine = true,
                            )
                        }
                    }

                    if (statusMessage != null) {
                        Text(
                            statusMessage,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        statusContent()
                    }
                }
            }

            CardFooter(
                hint = if (
                    (wordAudio == ReviewAudioPresentation.Generating || hintPreparing) && !audioHintLoading
                ) null else hint,
                hintLoading = audioHintLoading,
                hintHeight = hintHeight,
                actionLabel = actionLabel,
                actionEnabled = actionEnabled,
                actionLoading = actionLoading,
                onAction = onAction,
            )
        }
    }
    if (photoExpanded && imagePath != null && File(imagePath).isFile) {
        FullPhotoDialog(imagePath = imagePath, onDismiss = { photoExpanded = false })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SelectableSentence(
    sentence: String,
    tokens: List<JapaneseToken>,
    selectedSpan: TargetSpan?,
    selectionEnabled: Boolean,
    onSelectSpan: (TargetSpan) -> Unit,
    furigana: List<FuriganaSpan>,
    modifier: Modifier = Modifier,
) {
    val readingBySpan = furigana.associateBy { it.span }
    val selectedLabel = stringResource(R.string.card_word_selected_state)
    val unselectedLabel = stringResource(R.string.card_word_not_selected_state)
    val readingBandHeight = with(LocalDensity.current) { 13.sp.toDp().coerceAtLeast(14.dp) }
    if (tokens.isEmpty() || sentence.isBlank()) {
        Text(sentence, modifier = modifier, style = MaterialTheme.typography.headlineMedium)
        return
    }
    FlowRow(
        modifier = modifier.testTag("sentence-layout").semantics { isTraversalGroup = true },
        horizontalArrangement = Arrangement.Start,
        verticalArrangement = Arrangement.Bottom,
    ) {
        targetPills(tokens).forEachIndexed { index, pill ->
            val token = pill.token
            val isSelected = token.span == selectedSpan
            val selectedFill = MaterialTheme.colorScheme.secondaryContainer
            val selectedText = MaterialTheme.colorScheme.onSecondaryContainer
            val selectedUnderline = MaterialTheme.colorScheme.primary
            val reading = readingBySpan[token.span]?.reading
                ?: token.surfaceReading.takeIf { containsKanjiForCard(token.surface) }
            val baseStart = pill.prefix.length
            val baseEnd = baseStart + token.surface.length
            val display = buildAnnotatedString {
                append(pill.prefix)
                if (isSelected) {
                    pushStyle(SpanStyle(color = selectedText, background = selectedFill))
                }
                append(token.surface)
                if (isSelected) pop()
                append(pill.suffix)
            }
            val layoutResult = remember(token.span, display.text) { mutableStateOf<TextLayoutResult?>(null) }
            val interactionSource = remember(token.span) { MutableInteractionSource() }
            Column(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable(
                        enabled = selectionEnabled,
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { onSelectSpan(token.span) },
                    )
                    .testTag("sentence-word-${token.span.start}-${token.span.end}")
                    .semantics(mergeDescendants = true) {
                        contentDescription = token.surface
                        stateDescription = if (isSelected) selectedLabel else unselectedLabel
                        selected = isSelected
                        traversalIndex = index.toFloat()
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Box(Modifier.heightIn(min = readingBandHeight), contentAlignment = Alignment.Center) {
                    if (!reading.isNullOrBlank()) {
                        Text(
                            reading,
                            fontSize = 11.sp,
                            lineHeight = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = display,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("sentence-word-text-${token.span.start}-${token.span.end}")
                        .pointerInput(selectionEnabled, baseStart, baseEnd, layoutResult.value) {
                            if (selectionEnabled) {
                                detectTapGestures { position ->
                                    val textLayout = layoutResult.value ?: return@detectTapGestures
                                    val minimumTouchHeight = 48.dp.toPx()
                                    val hitBaseText = (baseStart until baseEnd).any { offset ->
                                        val character = textLayout.getBoundingBox(offset)
                                        val verticalExpansion = ((minimumTouchHeight - character.height) / 2f).coerceAtLeast(0f)
                                        val top = (character.top - verticalExpansion).coerceAtLeast(0f)
                                        val bottom = (character.bottom + verticalExpansion).coerceAtMost(size.height.toFloat())
                                        position.x >= character.left && position.x <= character.right &&
                                            position.y >= top && position.y <= bottom
                                    }
                                    if (hitBaseText) onSelectSpan(token.span)
                                }
                            }
                        }
                        .drawBehind {
                            if (isSelected) {
                                val textLayout = layoutResult.value ?: return@drawBehind
                                val strokeWidth = 2.dp.toPx()
                                (baseStart until baseEnd)
                                    .groupBy(textLayout::getLineForOffset)
                                    .forEach { (line, offsets) ->
                                        val boxes = offsets.map(textLayout::getBoundingBox)
                                        val left = boxes.minOf { it.left }
                                        val right = boxes.maxOf { it.right }
                                        val y = textLayout.getLineBottom(line) - strokeWidth / 2f
                                        drawLine(
                                            color = selectedUnderline,
                                            start = Offset(left, y),
                                            end = Offset(right, y),
                                            strokeWidth = strokeWidth,
                                        )
                                    }
                            }
                        },
                    style = MaterialTheme.typography.headlineMedium,
                    onTextLayout = { layoutResult.value = it },
                )
            }
        }
    }
}

private fun containsKanjiForCard(value: String): Boolean = value.any {
    Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN
}

@Composable
private fun CardPhotoBand(imagePath: String, height: Dp, focusY: Float, onOpen: () -> Unit) {
    val description = stringResource(R.string.view_photo)
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clipToBounds()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onOpen)
            .semantics { contentDescription = description },
    ) {
        AndroidView(
            factory = { context -> ImageView(context).apply { scaleType = ImageView.ScaleType.MATRIX } },
            update = { view ->
                view.setImageURI(Uri.fromFile(File(imagePath)))
                view.post {
                    val drawable = view.drawable ?: return@post
                    if (view.width <= 0 || view.height <= 0) return@post
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
    }
}

@Composable
private fun FullPhotoDialog(imagePath: String, onDismiss: () -> Unit) {
    val description = stringResource(R.string.card_full_photo)
    val closeDescription = stringResource(R.string.card_close_photo)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color.Black).semantics { contentDescription = description },
        ) {
            AndroidView(
                factory = { context -> ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
                update = { it.setImageURI(Uri.fromFile(File(imagePath))) },
                modifier = Modifier.fillMaxSize(),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).size(48.dp)
                    .semantics { contentDescription = closeDescription },
            ) { FlowIcons.Close(Modifier.size(24.dp), tint = Color.White) }
        }
    }
}

@Composable
private fun CardAudioButton(
    field: String,
    audio: ReviewAudioPresentation,
    onToggle: () -> Unit,
    onRetry: () -> Unit,
    enabled: Boolean = true,
    unavailableMessage: String? = null,
) {
    val description = if (!enabled && unavailableMessage != null) unavailableMessage else {
        reviewAudioControlLabel(field, audio).let { copy ->
            stringResource(copy.resourceId, *copy.formatArgs.toTypedArray())
        }
    }
    val state = when {
        !enabled && unavailableMessage != null -> unavailableMessage
        audio == ReviewAudioPresentation.SourceMissing -> stringResource(R.string.no_recording)
        else -> when (audio) {
        ReviewAudioPresentation.Unavailable -> stringResource(R.string.card_audio_unavailable_state)
        ReviewAudioPresentation.NeedsPreparation -> stringResource(R.string.card_audio_needs_preparation_state)
        ReviewAudioPresentation.Ready -> stringResource(R.string.ready)
        ReviewAudioPresentation.Playing -> stringResource(R.string.card_audio_playing_state)
        ReviewAudioPresentation.Generating -> stringResource(R.string.card_audio_in_progress_state)
        is ReviewAudioPresentation.Advisory -> audio.headline
        is ReviewAudioPresentation.Failed -> stringResource(R.string.card_audio_error_state)
        ReviewAudioPresentation.SourceMissing -> stringResource(R.string.no_recording)
        }
    }
    val action = if (audio == ReviewAudioPresentation.Ready || audio == ReviewAudioPresentation.Playing) onToggle else onRetry
    val glyphColor = when (audio) {
        is ReviewAudioPresentation.Failed -> MaterialTheme.colorScheme.error
        is ReviewAudioPresentation.Advisory -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.primary
    }
    IconButton(
        onClick = action,
        enabled = enabled && audio != ReviewAudioPresentation.Generating && audio != ReviewAudioPresentation.SourceMissing,
        modifier = Modifier.size(48.dp).semantics {
            contentDescription = description
            stateDescription = state
        },
    ) {
        AudioControlGlyph(
            playing = audio == ReviewAudioPresentation.Playing,
            color = glyphColor,
            modifier = Modifier.size(24.dp),
        )
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
            drawRect(color, Offset(size.width * 0.69f, size.height * 0.36f), Size(size.width * 0.20f, size.height * 0.28f))
        } else {
            val waveStroke = Stroke(width = size.minDimension * 0.065f, cap = StrokeCap.Round)
            drawArc(color, -55f, 110f, false, Offset(size.width * 0.39f, size.height * 0.30f), Size(size.width * 0.38f, size.height * 0.40f), style = waveStroke)
            drawArc(color, -55f, 110f, false, Offset(size.width * 0.39f, size.height * 0.15f), Size(size.width * 0.58f, size.height * 0.70f), style = waveStroke)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CardFooter(
    hint: String?,
    hintLoading: Boolean,
    hintHeight: Dp,
    actionLabel: String,
    actionEnabled: Boolean,
    actionLoading: Boolean,
    onAction: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().height(hintHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (hintLoading) LoadingIndicator(Modifier.size(16.dp))
            Text(
                hint.orEmpty(),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(8.dp))
        FlowPrimaryButton(
            text = actionLabel,
            onClick = onAction,
            enabled = actionEnabled && !actionLoading,
            showArrow = false,
        )
    }
}
