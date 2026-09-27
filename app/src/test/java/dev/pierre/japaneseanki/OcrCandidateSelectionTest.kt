package dev.pierre.japaneseanki

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrCandidateSelectionTest {
    @Test fun choosesHigherConfidenceAndUsesHorizontalOnTie() {
        val horizontal = OcrCandidate("横", listOf(OcrBlock("横", 70f, OcrBounds(1, 2, 3, 4))), 70f)
        val vertical = OcrCandidate("縦", listOf(OcrBlock("縦", 90f, OcrBounds(5, 6, 7, 8))), 90f)
        assertEquals(vertical, chooseOcrCandidate(horizontal, vertical))
        assertEquals(horizontal, chooseOcrCandidate(horizontal, horizontal.copy(meanConfidence = 70f)))
    }

    @Test fun nonblankCandidateWinsAndGeometryIsPreserved() {
        val block = OcrBlock("文章", 82f, OcrBounds(10, 20, 110, 60))
        val recognized = OcrCandidate("文章", listOf(block), 82f)
        assertEquals(recognized, chooseOcrCandidate(recognized, OcrCandidate(" ", emptyList(), 100f)))
        assertEquals(OcrBounds(10, 20, 110, 60), recognized.blocks.single().bounds)
    }
}
