package dev.pierre.japaneseanki

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrCandidateSelectionTest {
    @Test fun scalesBoundsIntoSourcePixelSpaceWithoutChangingBlockIdentity() {
        assertEquals(
            OcrBounds(20, 10, 220, 60),
            scaleOcrBounds(OcrBounds(10, 5, 110, 30), 200, 100, 400, 200),
        )
    }
}
