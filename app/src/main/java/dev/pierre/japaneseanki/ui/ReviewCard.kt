package dev.pierre.japaneseanki.ui

import android.net.Uri
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
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
import dev.pierre.japaneseanki.FuriganaSpan
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
    generatingFields: Set<String> = emptySet(),
    translationUsesFallback: Boolean = false,
    translationInfoExpanded: Boolean = false,
    onToggleTranslationInfo: () -> Unit = {},
    sendPresentation: ReviewSendPresentation? = null,
    onSend: (() -> Unit)? = null,
    onSendStatusAction: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            ReviewFieldRow("Image", onEdit = onEdit) {
                when {
                    imagePath != null && File(imagePath).isFile -> AndroidView(
                        factory = { context -> ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP } },
                        update = { it.setImageURI(Uri.fromFile(File(imagePath))) },
                        modifier = Modifier.size(72.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)),
                    )
                    imageStatus != null -> Text(imageStatus, style = MaterialTheme.typography.bodyMedium)
                    else -> Text("No image", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            ReviewFieldRow("Sentence", onEdit = onEdit, playable = true, generating = "Sentence" in generatingFields) {
                val parts = reviewSentenceParts(sentence, target, sentenceReading, furigana)
                val sentenceLayout = remember(sentence, target, sentenceReading, furigana) { mutableStateOf<TextLayoutResult?>(null) }
                val unresolvedColor = MaterialTheme.colorScheme.onSurfaceVariant
                val annotated = buildAnnotatedString {
                    parts.forEachIndexed { index, part ->
                        if (part.reading != null) {
                            appendInlineContent("ruby-$index", part.text)
                        } else {
                            withStyle(
                                SpanStyle(
                                    color = if (part.highlighted) MaterialTheme.colorScheme.onSecondaryContainer else Color.Unspecified,
                                    background = if (part.highlighted) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                                    fontWeight = if (part.highlighted) FontWeight.SemiBold else FontWeight.Normal,
                                ),
                            ) { append(part.text) }
                        }
                    }
                }
                val inlineRuby = parts.mapIndexedNotNull { index, part ->
                    val reading = part.reading ?: return@mapIndexedNotNull null
                    "ruby-$index" to InlineTextContent(
                        Placeholder(
                            width = part.text.length.coerceAtLeast(1).em,
                            height = 2.4.em,
                            placeholderVerticalAlign = PlaceholderVerticalAlign.AboveBaseline,
                        ),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(reading, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            Text(
                                part.text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (part.highlighted) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                                modifier = if (part.highlighted) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier,
                            )
                        }
                    }
                }.toMap()
                Text(
                    annotated,
                    inlineContent = inlineRuby,
                    style = MaterialTheme.typography.bodyLarge,
                    onTextLayout = { sentenceLayout.value = it },
                    modifier = Modifier.drawWithContent {
                        drawContent()
                        val layout = sentenceLayout.value ?: return@drawWithContent
                        var textOffset = 0
                        parts.forEach { part ->
                            if (part.unresolvedKanji) {
                                part.text.forEachIndexed { charIndex, char ->
                                    if (Character.UnicodeScript.of(char.code) == Character.UnicodeScript.HAN) {
                                        val box = layout.getBoundingBox(textOffset + charIndex)
                                        val dot = 1.dp.toPx()
                                        val gap = 2.dp.toPx()
                                        var x = box.left + dot / 2
                                        while (x < box.right) {
                                            drawCircle(
                                                color = unresolvedColor,
                                                radius = dot / 2,
                                                center = Offset(x, box.bottom + 1.dp.toPx()),
                                            )
                                            x += dot + gap
                                        }
                                    }
                                }
                            }
                            textOffset += part.text.length
                        }
                    },
                )
            }
            ReviewFieldRow("Vocabulary", onEdit = onEdit, playable = true, generating = "Vocabulary" in generatingFields) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RubyText(vocabulary, vocabularyReading)
                    pitch?.let { PitchPatternDiagram(it, PitchDiagramScale.Compact) }
                    if (pitchHasAlternatives || pitch == null) {
                        IconButton(onClick = onTogglePitchInfo, modifier = Modifier.size(48.dp).semantics {
                            contentDescription = if (pitchInfoExpanded) "Dismiss pitch explanation" else if (pitch == null) "Explain missing pitch pattern" else "Explain alternate pitch pattern"
                            stateDescription = if (pitchInfoExpanded) "Expanded" else "Collapsed"
                        }) { Text("!", color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
            }
            ReviewFieldRow("Definition", onEdit = onEdit, generating = "Definition" in generatingFields) { Text(definition.ifBlank { "Add a definition" }, style = MaterialTheme.typography.bodyLarge) }
            ReviewFieldRow("Translation", onEdit = onEdit, playable = true, generating = "Translation" in generatingFields) {
                Text(if ("Translation" in generatingFields) "Translating…" else translation.ifBlank { "Not available yet" }, style = MaterialTheme.typography.bodyLarge)
                if (translationUsesFallback) {
                    IconButton(onClick = onToggleTranslationInfo, modifier = Modifier.size(48.dp).semantics {
                        contentDescription = if (translationInfoExpanded) "Dismiss offline translation explanation" else "Explain offline translation"
                        stateDescription = if (translationInfoExpanded) "Expanded" else "Collapsed"
                    }) { Text("!", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                if (translationInfoExpanded) Text(
                    "This translation was produced offline.",
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(10.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            ReviewFieldRow("Notes", onEdit = onEdit) { Text(notes.ifBlank { "No notes" }, style = MaterialTheme.typography.bodyLarge) }
            ReviewFieldRow("Tags", onEdit = onAddTag) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    tags.filter(String::isNotBlank).forEach { tag ->
                        AssistChip(onClick = onAddTag, label = { Text(tag) }, modifier = Modifier.semantics { contentDescription = "Tag $tag" })
                    }
                    AssistChip(onClick = onAddTag, label = { Text("Add tag") }, modifier = Modifier.semantics { contentDescription = "Add tag" })
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
                        if (presentation.actionLabel != null) TextButton(
                            onClick = { onSendStatusAction?.invoke() }, enabled = onSendStatusAction != null,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text(presentation.actionLabel) }
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
    playable: Boolean = false,
    generating: Boolean = false,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(4.dp))
            content()
        }
        if (playable) {
            if (generating) Box(Modifier.size(48.dp).semantics {
                contentDescription = "Generating $label"
                stateDescription = "In progress"
            }, contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else IconButton(onClick = {}, modifier = Modifier.size(48.dp).semantics { contentDescription = "Play $label audio (not available yet)" }) {
                Text("▶", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onEdit, modifier = Modifier.size(48.dp).semantics { contentDescription = "Edit $label" }) {
            Text("✎", fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
