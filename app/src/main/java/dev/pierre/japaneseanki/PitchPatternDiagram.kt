package dev.pierre.japaneseanki

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/**
 * Source-backed pitch contour: katakana morae joined by straight lines with the study's
 * diagram-specific 22 sp labels and no axis legend. The mora cells are spacing only — the
 * study draws no boxes around them — so the contour reads as one shape rather than a table.
 */
@Composable
internal fun PitchPatternDiagram(pitch: PitchAccent, modifier: Modifier = Modifier) {
    val pitchColor = pitchColor(pitch.patternKind)
    val blockWidth = 28.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    val gap = 3.dp
    val tailWidth = 12.dp
    val chartHeight = 32.dp
    val totalWidth = blockWidth * pitch.morae.size + gap * (pitch.morae.size - 1) + tailWidth
    val dotRadius = 4.5.dp
    val lineWidth = 2.dp
    val description = pitch.accessibilityDescription()

    Column(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start,
    ) {
        Canvas(
            modifier = Modifier
                .width(totalWidth)
                .height(chartHeight)
                .semantics { contentDescription = description },
        ) {
            val blockPx = blockWidth.toPx()
            val gapPx = gap.toPx()
            val tailPx = tailWidth.toPx()
            val highY = size.height * 0.27f
            val lowY = size.height * 0.73f
            if (pitch.morae.isEmpty()) return@Canvas
            val centers = pitch.highLevels.mapIndexed { index, high ->
                Offset(
                    x = blockPx / 2 + index * (blockPx + gapPx),
                    y = if (high) highY else lowY,
                )
            }
            val tailY = if (pitch.accentPosition == pitch.morae.size) lowY else centers.last().y
            val points = centers + Offset(x = centers.last().x + tailPx, y = tailY)
            points.zipWithNext().forEach { (start, end) ->
                drawLine(pitchColor, start, end, strokeWidth = lineWidth.toPx(), cap = StrokeCap.Round)
            }
            centers.forEachIndexed { index, center ->
                if (pitchDotIsHollow(isSourceBackedNasalMora = index in pitch.marks.nasalMorae)) {
                    // A source-backed nasal mora is a ring, not a filled dot; a ring reads the
                    // same on any card tint, unlike punching a hole with a guessed background.
                    drawCircle(
                        pitchColor,
                        radius = dotRadius.toPx(),
                        center = center,
                        style = Stroke(width = lineWidth.toPx()),
                    )
                } else {
                    drawCircle(pitchColor, radius = dotRadius.toPx(), center = center)
                }
                if (index in pitch.marks.devoicedMorae) {
                    // Devoiced is a separate source fact from nasalization: a small cross.
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
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            pitch.morae.forEach { mora ->
                Text(
                    mora,
                    modifier = Modifier.width(blockWidth),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
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
