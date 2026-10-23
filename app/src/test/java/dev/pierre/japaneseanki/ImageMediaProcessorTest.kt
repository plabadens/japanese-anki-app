package dev.pierre.japaneseanki

import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageMediaProcessorTest {
    @Test
    fun landscapeFrameIsLimitedTo1080OnTheLongEdge() {
        assertEquals(1080 to 810, ImageMediaProcessor.fitWithin(1920, 1440, 1080))
        assertEquals(1080 to 810, ImageMediaProcessor.fitWithin(4032, 3024, 1080))
    }

    @Test
    fun portraitFrameKeepsAspectRatio() {
        assertEquals(810 to 1080, ImageMediaProcessor.fitWithin(2160, 2880, 1080))
    }

    @Test
    fun smallImagesAreNeverUpscaled() {
        assertEquals(320 to 240, ImageMediaProcessor.fitWithin(320, 240, 1080))
        assertEquals(512 to 342, ImageMediaProcessor.fitWithin(512, 342, 1080))
    }

    @Test
    fun extremeAspectStillProducesPositivePixels() {
        val (width, height) = ImageMediaProcessor.fitWithin(4000, 10, 1080)
        assertEquals(1080, width)
        assertTrue(height >= 1)
    }

    @Test
    fun budgetIsTheSpecifiedPreset() {
        assertEquals(1080, ImageMediaProcessor.MAX_EDGE)
        assertEquals(75, ImageMediaProcessor.QUALITY)
    }
}
