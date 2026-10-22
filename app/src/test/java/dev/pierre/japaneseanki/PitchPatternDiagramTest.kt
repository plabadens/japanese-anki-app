package dev.pierre.japaneseanki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class PitchPatternDiagramTest {
    @Test fun dotsStayFilledWithoutSourceBackedNasalization() {
        assertFalse(pitchDotIsHollow(isSourceBackedNasalMora = false))
        assertTrue(pitchDotIsHollow(isSourceBackedNasalMora = true))
    }

    @Test fun katakanaLabelsDoNotRewriteCanonicalReadingOrSourceMarkup() {
        val source = "<span class=\"low_rise\">う</span><span class=\"high\">け</span>"
        val pitch = PitchAccent.create("うけ", 0, source)!!

        assertEquals(listOf("ウ", "ケ"), pitch.morae.map(::pitchDiagramLabel))
        assertEquals("うけ", pitch.reading)
        assertEquals("$source <span class=\"pitch_number\">0</span>", pitch.fieldMarkup)
        assertEquals("リョ", pitchDiagramLabel("りょ"))
        assertEquals("キョウ", pitchDiagramLabel("きょう"))
    }

    @Test fun connectorsAreClippedOutsideEqualDiameterMarkersAndEndAtTheFinalCircle() {
        val geometry = pitchDiagramGeometry(
            highLevels = listOf(false, true, false),
            blockWidthPx = 28f,
            gapPx = 3f,
            highY = 8f,
            lowY = 24f,
            markerRadiusPx = 4.5f,
            lineWidthPx = 2f,
        )

        assertEquals(3, geometry.centers.size)
        assertEquals(2, geometry.connectors.size)
        assertEquals(
            geometry.markerRadiusPx + geometry.lineWidthPx / 2f,
            distance(geometry.startAt!!, geometry.centers.first()),
            0.0001f,
        )
        assertEquals(
            geometry.markerRadiusPx + geometry.lineWidthPx / 2f,
            distance(geometry.endAt!!, geometry.centers.last()),
            0.0001f,
        )
        assertEquals(
            geometry.solidMarkerRadiusPx,
            geometry.hollowMarkerCenterlineRadiusPx + geometry.lineWidthPx / 2f,
            0.0001f,
        )
        geometry.connectors.forEach { connector ->
            assertEquals(geometry.markerRadiusPx + geometry.lineWidthPx / 2f, distance(connector.start, connector.fromCenter), 0.0001f)
            assertEquals(geometry.markerRadiusPx + geometry.lineWidthPx / 2f, distance(connector.end, connector.toCenter), 0.0001f)
        }
    }

    @Test fun oneMoraPitchHasNoTrailingContourOrAddedIndicator() {
        val geometry = pitchDiagramGeometry(
            highLevels = listOf(false),
            blockWidthPx = 28f,
            gapPx = 3f,
            highY = 8f,
            lowY = 24f,
            markerRadiusPx = 4.5f,
            lineWidthPx = 2f,
        )

        assertTrue(geometry.connectors.isEmpty())
        assertEquals(geometry.centers.single(), geometry.endAt)
    }

    private fun distance(a: PitchDiagramPoint, b: PitchDiagramPoint): Float =
        hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()
}
