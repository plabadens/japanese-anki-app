package dev.pierre.japaneseanki.ui

import dev.pierre.japaneseanki.FuriganaSpan
import dev.pierre.japaneseanki.TargetSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewSentencePresentationTest {
    @Test fun highlightsExactTargetAndUsesItsReadingAsRuby() {
        val parts = reviewSentenceParts("猫と猫", TargetSpan(2, 3), "ねこ", emptyList())
        assertTrue(parts.any { it.text == "猫" && it.reading == "ねこ" && it.highlighted })
        assertEquals("猫", parts.filter { it.reading != null }.single().text)
    }

    @Test fun leavesUnresolvedKanjiMarkedAndShowsKnownReadingInline() {
        val parts = reviewSentenceParts(
            "東京へ行く",
            TargetSpan(2, 3),
            "へ",
            listOf(FuriganaSpan(TargetSpan(0, 2), "東京", "とうきょう")),
        )
        assertEquals(ReviewSentencePart("東京", "とうきょう"), parts[0])
        assertTrue(parts.any { it.text == "行" && it.unresolvedKanji })
        assertTrue(parts.any { it.text == "へ" && it.highlighted })
    }
}
