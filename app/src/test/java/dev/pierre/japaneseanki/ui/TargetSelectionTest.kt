package dev.pierre.japaneseanki.ui

import dev.pierre.japaneseanki.JapaneseToken
import dev.pierre.japaneseanki.TargetSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TargetSelectionTest {
    // 図書館 | で | 本 | を | 読みました | 。   (UTF-16 indices 0..12)
    private val sentence = "図書館で本を読みました。"

    private fun token(start: Int, end: Int): JapaneseToken =
        JapaneseToken(
            span = TargetSpan(start, end),
            surface = sentence.substring(start, end),
            lemma = "",
            surfaceReading = "",
            lemmaReading = "",
            partOfSpeech = "名詞",
        )

    private val tokens = listOf(
        token(0, 3),
        token(3, 4),
        token(4, 5),
        token(5, 6),
        token(6, 11),
        token(11, 12),
    )

    private val punctuated = tokens.dropLast(1) + JapaneseToken(
        span = TargetSpan(11, 12),
        surface = "。",
        lemma = "。",
        surfaceReading = "",
        lemmaReading = "",
        partOfSpeech = "補助記号-句点",
    )

    @Test
    fun extendingRightAddsTheNextTokenAndKeepsTheOccurrenceContiguous() {
        val extended = extendSpan(tokens, TargetSpan(4, 5), SpanEdge.Right)
        assertEquals(TargetSpan(4, 6), extended)
        assertEquals("本を", extended?.textIn(sentence))
    }

    @Test
    fun extendingLeftAddsThePreviousToken() {
        val extended = extendSpan(tokens, TargetSpan(4, 5), SpanEdge.Left)
        assertEquals(TargetSpan(3, 5), extended)
        assertEquals("で本", extended?.textIn(sentence))
    }

    @Test
    fun aSpanAtTheSentenceBoundaryStopsGrowing() {
        assertNull(extendSpan(tokens, TargetSpan(0, 3), SpanEdge.Left))
        assertNull(extendSpan(tokens, TargetSpan(11, 12), SpanEdge.Right))
    }

    @Test
    fun trimDropsOneTokenAtATimeAndStopsAtASingleToken() {
        val wide = TargetSpan(3, 6)
        assertEquals(TargetSpan(4, 6), trimSpan(tokens, wide, SpanEdge.Left))
        assertEquals(TargetSpan(3, 5), trimSpan(tokens, wide, SpanEdge.Right))
        assertNull(trimSpan(tokens, TargetSpan(4, 5), SpanEdge.Left))
        assertNull(trimSpan(tokens, TargetSpan(4, 5), SpanEdge.Right))
    }

    @Test
    fun extensionSkipsBlankTokensAndEmptyTokenLists() {
        val spaced = listOf(
            token(0, 3),
            JapaneseToken(
                span = TargetSpan(3, 4),
                surface = " ",
                lemma = "",
                surfaceReading = "",
                lemmaReading = "",
                partOfSpeech = "空白",
            ),
            token(4, 5),
        )
        assertEquals(TargetSpan(0, 5), extendSpan(spaced, TargetSpan(0, 3), SpanEdge.Right))
        assertNull(extendSpan(emptyList(), TargetSpan(0, 1), SpanEdge.Right))
    }

    @Test
    fun spanSurfaceReportsOnlyAlignedSelections() {
        assertEquals("本を", spanSurface(tokens, TargetSpan(4, 6)))
        assertNull("a span that starts mid-token is not a pill selection", spanSurface(tokens, TargetSpan(2, 5)))
    }

    @Test
    fun extendingPastTheLastWordDoesNotSelectPunctuation() {
        assertNull(extendSpan(punctuated, TargetSpan(6, 11), SpanEdge.Right))
    }

    @Test
    fun punctuationIsAttachedToThePreviousPillAndStaysOutOfItsSpan() {
        val pills = targetPills(punctuated)

        assertEquals(listOf("図書館", "で", "本", "を", "読みました。"), pills.map { it.label })
        assertEquals("読みました", pills.last().token.surface)
        assertEquals(TargetSpan(6, 11), pills.last().token.span)
    }
}
