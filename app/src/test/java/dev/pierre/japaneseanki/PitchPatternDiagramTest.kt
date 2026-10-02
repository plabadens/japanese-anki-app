package dev.pierre.japaneseanki

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PitchPatternDiagramTest {
    @Test fun dotsStayFilledWithoutSourceBackedNasalization() {
        assertFalse(pitchDotIsHollow(isSourceBackedNasalMora = false))
        assertTrue(pitchDotIsHollow(isSourceBackedNasalMora = true))
    }
}
