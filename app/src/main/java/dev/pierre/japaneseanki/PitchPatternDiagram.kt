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
import kotlin.math.hypot

internal data class PitchDiagramPoint(val x: Float, val y: Float)

internal data class PitchDiagramConnector(
    val fromCenter: PitchDiagramPoint,
    val start: PitchDiagramPoint,
    val toCenter: PitchDiagramPoint,
    val end: PitchDiagramPoint,
)

internal data class PitchDiagramGeometry(
    val centers: List<PitchDiagramPoint>,
    val connectors: List<PitchDiagramConnector>,
    val markerRadiusPx: Float,
    val hollowMarkerCenterlineRadiusPx: Float,
    val lineWidthPx: Float,
) {
    val solidMarkerRadiusPx: Float get() = markerRadiusPx
    val startAt: PitchDiagramPoint? get() = connectors.firstOrNull()?.start ?: centers.firstOrNull()
    val endAt: PitchDiagramPoint? get() = connectors.lastOrNull()?.end ?: centers.lastOrNull()
}

/** Geometry shared by rendering and JVM tests; connectors stop at each marker's outer edge. */
internal fun pitchDiagramGeometry(
    highLevels: List<Boolean>,
    blockWidthPx: Float,
    gapPx: Float,
    highY: Float,
    lowY: Float,
    markerRadiusPx: Float,
    lineWidthPx: Float,
): PitchDiagramGeometry {
    require(blockWidthPx > 0f && gapPx >= 0f)
    require(markerRadiusPx > lineWidthPx / 2f && lineWidthPx > 0f)

    val centers = highLevels.mapIndexed { index, high ->
        PitchDiagramPoint(
            x = blockWidthPx / 2f + index * (blockWidthPx + gapPx),
            y = if (high) highY else lowY,
        )
    }
    val clippedRadius = markerRadiusPx + lineWidthPx / 2f
    val connectors = centers.zipWithNext().map { (from, to) ->
        val dx = to.x - from.x
        val dy = to.y - from.y
        val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        require(distance > 2f * clippedRadius) { "Pitch mora markers overlap." }
        val unitX = dx / distance
        val unitY = dy / distance
        PitchDiagramConnector(
            fromCenter = from,
            start = PitchDiagramPoint(from.x + unitX * clippedRadius, from.y + unitY * clippedRadius),
            toCenter = to,
            end = PitchDiagramPoint(to.x - unitX * clippedRadius, to.y - unitY * clippedRadius),
        )
    }
    return PitchDiagramGeometry(
        centers = centers,
        connectors = connectors,
        markerRadiusPx = markerRadiusPx,
        hollowMarkerCenterlineRadiusPx = markerRadiusPx - lineWidthPx / 2f,
        lineWidthPx = lineWidthPx,
    )
}

/** Katakana is a display-only convention for the diagram; source readings remain untouched. */
internal fun pitchDiagramLabel(mora: String): String = buildString(mora.length) {
    mora.forEach { char ->
        append(if (char in '\u3041'..'\u3096') char + 0x60 else char)
    }
}

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
    val chartHeight = 32.dp
    val totalWidth = blockWidth * pitch.morae.size + gap * (pitch.morae.size - 1).coerceAtLeast(0)
    val markerRadius = 4.5.dp
    val lineWidth = 2.dp
    val labels = pitch.morae.map(::pitchDiagramLabel)
    val description = pitch.accessibilityDescription(labels)

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
            if (pitch.morae.isEmpty()) return@Canvas
            val geometry = pitchDiagramGeometry(
                highLevels = pitch.highLevels,
                blockWidthPx = blockWidth.toPx(),
                gapPx = gap.toPx(),
                highY = size.height * 0.27f,
                lowY = size.height * 0.73f,
                markerRadiusPx = markerRadius.toPx(),
                lineWidthPx = lineWidth.toPx(),
            )
            geometry.connectors.forEach { connector ->
                drawLine(
                    color = pitchColor,
                    start = Offset(connector.start.x, connector.start.y),
                    end = Offset(connector.end.x, connector.end.y),
                    strokeWidth = geometry.lineWidthPx,
                    cap = StrokeCap.Butt,
                )
            }
            geometry.centers.forEachIndexed { index, center ->
                val point = Offset(center.x, center.y)
                if (pitchDotIsHollow(isSourceBackedNasalMora = index in pitch.marks.nasalMorae)) {
                    // The ring's outer radius equals a solid marker's radius.
                    drawCircle(
                        pitchColor,
                        radius = geometry.hollowMarkerCenterlineRadiusPx,
                        center = point,
                        style = Stroke(width = geometry.lineWidthPx),
                    )
                } else {
                    drawCircle(pitchColor, radius = geometry.solidMarkerRadiusPx, center = point)
                }
                if (index in pitch.marks.devoicedMorae) {
                    // Devoiced is a separate source fact from nasalization: a small cross.
                    val arm = geometry.markerRadiusPx * 0.8f
                    drawLine(
                        pitchColor,
                        Offset(center.x - arm, center.y),
                        Offset(center.x + arm, center.y),
                        strokeWidth = geometry.lineWidthPx,
                    )
                    drawLine(
                        pitchColor,
                        Offset(center.x, center.y - arm),
                        Offset(center.x, center.y + arm),
                        strokeWidth = geometry.lineWidthPx,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            labels.forEach { label ->
                Text(
                    label,
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

internal fun pitchDotIsHollow(isSourceBackedNasalMora: Boolean): Boolean = isSourceBackedNasalMora

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

private fun PitchAccent.accessibilityDescription(labels: List<String>): String {
    val drop = when {
        accentPosition == 0 -> "no drop within the word"
        accentPosition == morae.size -> "drops after the final mora"
        else -> "drops after mora $accentPosition"
    }
    val markText = marksNarration().let { if (it.isEmpty()) "" else ", $it" }
    return "${labels.joinToString(" ")}$markText, reading $reading, pitch $drop"
}

private fun PitchAccent.marksNarration(): String = buildList {
    if (marks.nasalMorae.isNotEmpty()) {
        add("nasal " + marks.nasalMorae.sorted().joinToString(" ") { morae[it] })
    }
    if (marks.devoicedMorae.isNotEmpty()) {
        add("devoiced " + marks.devoicedMorae.sorted().joinToString(" ") { morae[it] })
    }
}.joinToString(", ")
