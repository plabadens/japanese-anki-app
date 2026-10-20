package dev.pierre.japaneseanki

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal enum class PitchDiagramScale { Compact, Full }

@Composable
internal fun PitchPatternDiagram(
    pitch: PitchAccent,
    scale: PitchDiagramScale = PitchDiagramScale.Compact,
    selected: Boolean = false,
) {
    val pitchColor = pitchColor(pitch.patternKind)
    val surface = MaterialTheme.colorScheme.surface
    val outline = MaterialTheme.colorScheme.outline
    val compact = scale == PitchDiagramScale.Compact
    val fullBlockWidth = 28f * LocalDensity.current.fontScale.coerceAtLeast(1f)
    val blockWidth = if (compact) 17.dp else fullBlockWidth.dp
    val blockHeight = if (compact) 18.dp else 26.dp
    val gap = if (compact) 2.dp else 3.dp
    val tailWidth = if (compact) 7.dp else 12.dp
    val totalWidth = blockWidth * pitch.morae.size + gap * (pitch.morae.size - 1) + tailWidth
    val dotRadius = if (compact) 2.5.dp else 3.5.dp
    val lineWidth = if (compact) 1.5.dp else 2.dp
    val description = pitch.accessibilityDescription()
    val selectedModifier = if (selected) {
        Modifier.border(1.dp, outline, RoundedCornerShape(6.dp)).padding(2.dp)
    } else Modifier

    Column(
        modifier = selectedModifier.horizontalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start,
    ) {
        Canvas(
            modifier = Modifier
                .width(totalWidth)
                .height(blockHeight)
                .semantics { contentDescription = description },
        ) {
            val blockPx = blockWidth.toPx()
            val gapPx = gap.toPx()
            val tailPx = tailWidth.toPx()
            val highY = size.height * 0.27f
            val lowY = size.height * 0.73f
            val centers = pitch.highLevels.mapIndexed { index, high ->
                Offset(
                    x = blockPx / 2 + index * (blockPx + gapPx),
                    y = if (high) highY else lowY,
                )
            }
            centers.forEachIndexed { index, _ ->
                val topLeft = Offset(index * (blockPx + gapPx), 0f)
                drawRoundRect(
                    color = surface,
                    topLeft = topLeft,
                    size = androidx.compose.ui.geometry.Size(blockPx, size.height),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                )
                drawRoundRect(
                    color = outline,
                    topLeft = topLeft,
                    size = androidx.compose.ui.geometry.Size(blockPx, size.height),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
            val tailY = if (pitch.accentPosition == pitch.morae.size) lowY else centers.last().y
            val points = centers + Offset(x = centers.last().x + tailPx, y = tailY)
            points.zipWithNext().forEach { (start, end) ->
                drawLine(pitchColor, start, end, strokeWidth = lineWidth.toPx(), cap = StrokeCap.Round)
            }
            centers.forEachIndexed { index, center ->
                drawCircle(pitchColor, radius = dotRadius.toPx(), center = center)
                if (pitchDotIsHollow(isSourceBackedNasalMora = index in pitch.marks.nasalMorae)) {
                    drawCircle(surface, radius = (dotRadius - 0.9.dp).toPx(), center = center)
                }
                if (index in pitch.marks.devoicedMorae) {
                    // Devoiced is a separate source fact from nasalization: a small cross, not a hollow dot.
                    val arm = dotRadius.toPx() * 0.8f
                    drawLine(
                        pitchColor,
                        Offset(center.x - arm, center.y),
                        Offset(center.x + arm, center.y),
                        strokeWidth = lineWidth.toPx(),
                    )
                    drawLine(
                        pitchColor,
                        Offset(center.x, center.y - arm),
                        Offset(center.x, center.y + arm),
                        strokeWidth = lineWidth.toPx(),
                    )
                }
            }
        }
        if (!compact) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                pitch.morae.forEach { mora ->
                    Text(
                        mora,
                        modifier = Modifier.width(blockWidth),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

internal fun pitchDotIsHollow(isSourceBackedNasalMora: Boolean): Boolean =
    isSourceBackedNasalMora

@Composable
private fun pitchColor(pattern: PitchPatternKind): Color {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    return when (pattern) {
        PitchPatternKind.Heiban -> if (dark) Color(0xFF7FA0FF) else Color(0xFF3A5CC4)
        PitchPatternKind.Atamadaka -> if (dark) Color(0xFFFF8A82) else Color(0xFFA6392E)
        PitchPatternKind.Nakadaka -> if (dark) Color(0xFFFFB454) else Color(0xFF7C6414)
        PitchPatternKind.Odaka -> if (dark) Color(0xFFAAD94C) else Color(0xFF266F45)
    }
}

private fun PitchAccent.accessibilityDescription(): String {
    val drop = when {
        accentPosition == 0 -> "no drop within the word"
        accentPosition == morae.size -> "drops after the final mora"
        else -> "drops after mora $accentPosition"
    }
    val markText = marksNarration().let { if (it.isEmpty()) "" else ", $it" }
    return "${morae.joinToString(" ")}$markText, reading $reading, pitch $drop"
}

private fun PitchAccent.marksNarration(): String = buildList {
    if (marks.nasalMorae.isNotEmpty()) {
        add("nasal " + marks.nasalMorae.sorted().joinToString(" ") { morae[it] })
    }
    if (marks.devoicedMorae.isNotEmpty()) {
        add("devoiced " + marks.devoicedMorae.sorted().joinToString(" ") { morae[it] })
    }
}.joinToString(", ")
