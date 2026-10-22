package dev.pierre.japaneseanki.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardSurfacePresentationTest {
    @Test fun photoBandKeepsItsSixteenByNineCeilingAndShrinksOnShortScreens() {
        assertEquals(219.dp, cardPhotoBandHeight(width = 390.dp, availableHeight = 700.dp, fontScale = 1f))
        assertEquals(96.dp, cardPhotoBandHeight(width = 390.dp, availableHeight = 500.dp, fontScale = 1f))
        assertEquals(96.dp, cardPhotoBandHeight(width = 390.dp, availableHeight = 700.dp, fontScale = 2f))
        assertEquals(180.dp, cardPhotoBandHeight(width = 320.dp, availableHeight = 700.dp, fontScale = 1f))
    }

    @Test fun partOfSpeechUsesOnePlainEnglishLabelFromSelectedSenses() {
        assertEquals("noun", englishPartOfSpeechLabel(listOf("n", "n-pr")))
        assertEquals("verb", englishPartOfSpeechLabel(listOf("v1")))
        assertEquals("adjective", englishPartOfSpeechLabel(listOf("adj-i")))
        assertEquals("adverb", englishPartOfSpeechLabel(listOf("adv")))
        assertNull(englishPartOfSpeechLabel(listOf("unknown-tag")))
    }

    @Test fun photoCoverCropCentersAndHonorsTheSelectedVerticalFocus() {
        val (scale, dx, centerDy) = focalCropOffsets(
            imageWidth = 400,
            imageHeight = 400,
            viewWidth = 400,
            viewHeight = 219,
            focusY = 0.5f,
        )
        assertEquals(1f, scale, 0f)
        assertEquals(0f, dx, 0f)
        assertEquals(-90.5f, centerDy, 0.001f)

        val (_, _, focusedDy) = focalCropOffsets(400, 400, 400, 219, focusY = 0.55f)
        assertEquals(-110.5f, focusedDy, 0.001f)

        val (_, _, topDy) = focalCropOffsets(400, 400, 400, 219, focusY = 0f)
        val (_, _, bottomDy) = focalCropOffsets(400, 400, 400, 219, focusY = 1f)
        assertEquals(0f, topDy, 0.001f)
        assertEquals(-181f, bottomDy, 0.001f)
    }
}
