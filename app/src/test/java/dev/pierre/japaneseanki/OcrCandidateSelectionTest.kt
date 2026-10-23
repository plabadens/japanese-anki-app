package dev.pierre.japaneseanki

import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OcrCandidateSelectionTest {
    @Test fun scalesBoundsIntoSourcePixelSpaceWithoutChangingBlockIdentity() {
        assertEquals(
            OcrBounds(20, 10, 220, 60),
            scaleOcrBounds(OcrBounds(10, 5, 110, 30), 200, 100, 400, 200),
        )
    }
}
