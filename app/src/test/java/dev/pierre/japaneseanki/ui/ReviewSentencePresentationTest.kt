package dev.pierre.japaneseanki.ui

import dev.pierre.japaneseanki.FuriganaSpan
import dev.pierre.japaneseanki.JapaneseToken
import dev.pierre.japaneseanki.TargetSpan
import dev.pierre.japaneseanki.sentenceFuriganaSpans
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewSentencePresentationTest {
    @Test fun highlightsExactTargetAndUsesItsReadingAsRuby() {
        val parts = reviewSentenceParts("猫と猫", TargetSpan(2, 3), "ねこ", emptyList())
        assertTrue(parts.any { it.text == "猫" && it.reading == "ねこ" && it.highlighted })
        assertEquals("猫", parts.filter { it.reading != null }.single().text)
    }

    @Test fun highlightsFuriganaAnnotatedTargetOccurrence() {
        val parts = reviewSentenceParts(
            "猫と猫",
            TargetSpan(2, 3),
            "ねこ",
            listOf(FuriganaSpan(TargetSpan(0, 1), "猫", "ねこ"), FuriganaSpan(TargetSpan(2, 3), "猫", "ねこ")),
        )
        assertEquals(2, parts.count { it.reading == "ねこ" })
        assertTrue(parts.single { it.highlighted }.text == "猫")
    }

    @Test fun multiTokenTargetUsesTargetReadingAndRetainsFuriganaOutsideTarget() {
        val sentence = "東京へ行く"
        val target = TargetSpan(0, 3)
        val tokens = listOf(
            JapaneseToken(TargetSpan(0, 2), "東京", "東京", "とうきょう", "とうきょう", "名詞-固有名詞"),
            JapaneseToken(TargetSpan(2, 3), "へ", "へ", "へ", "へ", "助詞-格助詞"),
            JapaneseToken(TargetSpan(3, 5), "行く", "行く", "いく", "いく", "動詞-一般"),
        )
        val furigana = sentenceFuriganaSpans(sentence, tokens, target)
        val parts = reviewSentenceParts(sentence, target, "とうきょうへ", furigana)

        assertEquals(listOf(FuriganaSpan(TargetSpan(3, 5), "行く", "いく")), furigana)
        assertEquals(ReviewSentencePart("東京へ", "とうきょうへ", highlighted = true), parts[0])
        assertEquals(ReviewSentencePart("行く", "いく"), parts[1])
    }

    @Test fun invalidTargetThatSplitsSupplementaryCharacterLeavesSentenceIntact() {
        val sentence = "A😀B"
        val parts = reviewSentenceParts(sentence, TargetSpan(1, 2), "", emptyList())
        assertEquals(listOf(ReviewSentencePart(sentence)), parts)
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
