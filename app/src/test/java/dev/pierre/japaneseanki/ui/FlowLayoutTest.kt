package dev.pierre.japaneseanki.ui

import dev.pierre.japaneseanki.OcrBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowLayoutTest {
    @Test
    fun landscapePhotoFillsTheReviewSlotWithoutLetterboxing() {
        // 1440x1080 landscape photo in a 360x144 dp slot: cover scale is driven by width.
        val (scale, dx, dy) = focalCropOffsets(1440, 1080, 360, 144, 0.5f)
        assertEquals(0.25f, scale, 0.0001f)
        assertEquals(0f, dx, 0.0001f)
        // Scaled height 270, slot 144: the focal point is centred, so the top is cropped by 63.
        assertEquals(-63f, dy, 0.0001f)
    }

    @Test
    fun textbookFixtureAlsoFillsTheSlot() {
        // The landscape fixture used for acceptance is 900x675, so cover still comes from width.
        val (scale, dx, _) = focalCropOffsets(900, 675, 360, 144, 0.4f)
        assertEquals(0.4f, scale, 0.0001f)
        assertEquals(0f, dx, 0.0001f)
        // Fit would have produced a 108 dp-high letterboxed band; cover fills all 144 dp.
        assertTrue(675 * scale >= 144f)
    }

    @Test
    fun portraitPhotoCropsVerticallyAroundTheFocalPoint() {
        val (scale, _, centre) = focalCropOffsets(1080, 1440, 360, 144, 0.5f)
        assertEquals(1f / 3f, scale, 0.0001f)
        val (_, _, top) = focalCropOffsets(1080, 1440, 360, 144, 0f)
        val (_, _, bottom) = focalCropOffsets(1080, 1440, 360, 144, 1f)
        assertEquals(0f, top, 0.0001f)
        assertEquals(-336f, bottom, 0.0001f)
        assertTrue(centre < top && centre > bottom)
    }

    @Test
    fun focalFractionIsClampedAndDegenerateSizesAreSafe() {
        val (_, _, clamped) = focalCropOffsets(100, 100, 50, 50, 4f)
        assertEquals(0f, clamped, 0.0001f)
        assertEquals(Triple(1f, 0f, 0f), focalCropOffsets(0, 100, 50, 50, 0.5f))
        assertEquals(Triple(1f, 0f, 0f), focalCropOffsets(100, 100, 0, 50, 0.5f))
    }

    @Test
    fun ocrBlockCentresThePhotoOnTheSelectedLine() {
        val bounds = OcrBounds(left = 100, top = 400, right = 900, bottom = 600)
        assertEquals(0.5f, ocrFocusFraction(bounds, 1000), 0.0001f)
        assertEquals(0.1f, ocrFocusFraction(bounds, 5000), 0.0001f)
        // A missing or nonsensical source height falls back to the centred slot.
        assertEquals(0.5f, ocrFocusFraction(bounds, 0), 0.0001f)
        assertEquals(0.5f, ocrFocusFraction(OcrBounds(0, 500, 10, 400), 1000), 0.0001f)
    }
}
