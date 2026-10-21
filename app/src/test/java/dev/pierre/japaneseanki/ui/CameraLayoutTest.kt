package dev.pierre.japaneseanki.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression cover for the landscape capture layout: the 4:3 frame is height-limited in landscape,
 * and sizing it from the screen width (the old portrait-only rule) produced a frame taller than the
 * screen, which pushed the shutter off-screen and cropped the preview.
 */
class CameraLayoutTest {
    @Test
    fun landscapeFrameFitsWithinTheScreenHeight() {
        val screenHeightDp = 411
        val frameHeight = landscapeViewfinderHeightDp(screenHeightDp)
        assertEquals(261, frameHeight)
        assertTrue("frame ${frameHeight}dp must leave room on a ${screenHeightDp}dp screen", frameHeight < screenHeightDp)
        // A 4:3 frame at this height is still wider than the 110dp shutter column.
        assertTrue((frameHeight * 4 / 3) > 110)
    }

    @Test
    fun shortScreensKeepAUsableMinimumFrame() {
        assertTrue(landscapeViewfinderHeightDp(200) >= MIN_LANDSCAPE_FRAME_DP)
        assertTrue(landscapeViewfinderHeightDp(0) >= MIN_LANDSCAPE_FRAME_DP)
    }
}
