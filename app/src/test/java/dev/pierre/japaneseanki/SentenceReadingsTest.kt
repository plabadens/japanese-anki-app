package dev.pierre.japaneseanki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Targeted reading correction (SPEC §4.5, §5.2, §10): a user-corrected reading belongs to one
 * kanji span and must not touch the sentence text, the target selection, or other spans.
 */
class SentenceReadingsTest {
    private val sentence = "昨日は新しい喫茶店に行った。"
    private val target = TargetSpan(6, 9) // 喫茶店
    private val tokens = listOf(
        token(0, 2, "昨日", "きのう"),
        token(2, 3, "は", "は", pos = "助詞-係助詞"),
        token(3, 6, "新しい", "あたらしい", pos = "形容詞-一般"),
        token(6, 9, "喫茶店", "きっさてん"),
        token(9, 10, "に", "に", pos = "助詞-格助詞"),
        token(10, 12, "行っ", "いっ", lemma = "行く", lemmaReading = "いく", pos = "動詞-一般"),
        token(12, 13, "た", "た", pos = "助動詞-タ"),
        token(13, 14, "。", "", pos = "補助記号-句点"),
    )

    @Test fun correctionReplacesTheReadingOfItsSpanOnly() {
        val corrected = SentenceReadings.effective(
            sentence,
            tokens,
            target,
            "きっさてん",
            listOf(ReadingCorrection(TargetSpan(3, 6), "新しい", "あたらし")),
        )

        assertEquals("あたらし", corrected.single { it.span == TargetSpan(3, 6) }.reading)
        assertEquals("きのう", corrected.single { it.span == TargetSpan(0, 2) }.reading)
    }

    @Test fun effectiveReadingsKeepTheSentenceSurfaceAndExcludeOtherSpans() {
        val corrected = SentenceReadings.effective(
            sentence,
            tokens,
            target,
            "きっちゃてん",
            listOf(ReadingCorrection(TargetSpan(0, 2), "昨日", "きのうび")),
        )

        // The stored correction covers only its own span, with the sentence's own surface.
        assertEquals(FuriganaSpan(TargetSpan(0, 2), "昨日", "きのうび"), corrected.first())
        assertTrue(corrected.none { it.span == TargetSpan(2, 3) })
        assertTrue(corrected.none { it.span == TargetSpan(6, 9) }) // target reading is field-held
    }

    @Test fun aCorrectionWhoseSurfaceNoLongerMatchesIsIgnored() {
        val corrected = SentenceReadings.effective(
            sentence,
            tokens,
            target,
            "きっさてん",
            listOf(ReadingCorrection(TargetSpan(0, 2), "今日", "きょう")),
        )

        assertEquals("きのう", corrected.single { it.span == TargetSpan(0, 2) }.reading)
    }

    @Test fun targetReadingStaysInItsOwnFieldAndIsNeverDuplicatedAsRuby() {
        val corrected = SentenceReadings.effective(
            sentence,
            tokens,
            target,
            "きっさてん",
            listOf(ReadingCorrection(TargetSpan(6, 9), "喫茶店", "きっちゃてん")),
        )

        // The target's reading is carried by sentenceReading, not by a competing ruby run.
        assertTrue(corrected.none { it.span == target })
    }

    @Test fun correctableReadingsListOnlyKanjiSpansAndExposeTheTarget() {
        val rows = SentenceReadings.correctable(
            sentence,
            tokens,
            target,
            "きっさてん",
            listOf(ReadingCorrection(TargetSpan(0, 2), "昨日", "きのうび")),
        )

        assertEquals(listOf("昨日", "新しい", "喫茶店", "行っ"), rows.map { it.surface })
        assertEquals(listOf("きのうび", "あたらしい", "きっさてん", "いっ"), rows.map { it.reading })
        assertEquals(listOf(false, false, true, false), rows.map { it.isTarget })
        assertTrue(rows.none { it.unresolved })
    }

    @Test fun aKanjiSpanWithoutAReadingStaysVisibleAsUnresolved() {
        val unresolvedTokens = tokens.map {
            if (it.span == TargetSpan(6, 9)) it.copy(surfaceReading = "", lemmaReading = "") else it
        }

        val rows = SentenceReadings.correctable(sentence, unresolvedTokens, target, "", emptyList())

        val row = rows.single { it.surface == "喫茶店" }
        assertTrue(row.unresolved)
        assertEquals("", row.reading)
        assertTrue(row.isTarget)
    }

    @Test fun correctingTheSameSpanTwiceKeepsOneNormalisedCorrection() {
        val once = SentenceReadings.correct(emptyList(), sentence, TargetSpan(6, 9), "キッサテン")
        val twice = SentenceReadings.correct(once, sentence, TargetSpan(6, 9), "きっちゃてん")

        assertEquals(listOf(ReadingCorrection(TargetSpan(6, 9), "喫茶店", "きっちゃてん")), twice)
    }

    @Test fun correctingAnInvalidSpanLeavesTheListUnchanged() {
        val existing = listOf(ReadingCorrection(TargetSpan(0, 2), "昨日", "きのうび"))
        assertEquals(existing, SentenceReadings.correct(existing, sentence, TargetSpan(2, 3), "は"))
    }

    @Test fun rejectionExplainsWhyACorrectionCannotBeStored() {
        assertTrue(SentenceReadings.rejection(sentence, TargetSpan(6, 9), " ").orEmpty().isNotBlank())
        assertTrue(SentenceReadings.rejection(sentence, TargetSpan(6, 9), "きっ[さ]てん").orEmpty().isNotBlank())
        assertTrue(SentenceReadings.rejection(sentence, TargetSpan(6, 9), "kissa").orEmpty().isNotBlank())
        assertTrue(SentenceReadings.rejection(sentence, TargetSpan(2, 3), "は").orEmpty().isNotBlank())
        assertTrue(SentenceReadings.rejection(sentence, TargetSpan(6, 99), "きっさてん").orEmpty().isNotBlank())
        assertNull(SentenceReadings.rejection(sentence, TargetSpan(6, 9), "きっさてん"))
    }

    @Test fun encodeDecodeRoundTripsCorrectionsForTheSameSentence() {
        val stored = listOf(
            ReadingCorrection(TargetSpan(0, 2), "昨日", "きのうび"),
            ReadingCorrection(TargetSpan(6, 9), "喫茶店", "きっちゃてん"),
        )

        assertEquals(stored, SentenceReadings.decode(SentenceReadings.encode(stored), sentence))
    }

    @Test fun decodeDropsCorrectionsThatNoLongerMatchTheSentence() {
        val encoded = SentenceReadings.encode(
            listOf(
                ReadingCorrection(TargetSpan(0, 2), "昨日", "きのうび"),
                ReadingCorrection(TargetSpan(6, 9), "喫茶店", "きっちゃてん"),
            ),
        )

        // Same offsets, different text: the stale span is dropped, a still-matching span is kept.
        assertEquals(
            listOf(ReadingCorrection(TargetSpan(6, 9), "喫茶店", "きっちゃてん")),
            SentenceReadings.decode(encoded, "今日は新しい喫茶店に行った。"),
        )
        // Reordered text no longer matches either stored surface.
        assertTrue(SentenceReadings.decode(encoded, "喫茶店は昨日新しい。").isEmpty())
    }

    @Test fun decodeIgnoresGarbageAndNonKanaReadings() {
        assertTrue(SentenceReadings.decode("", sentence).isEmpty())
        assertTrue(SentenceReadings.decode("nonsense", sentence).isEmpty())
        assertTrue(SentenceReadings.decode("6\t9\t喫茶店\tkissa", sentence).isEmpty())
        assertTrue(SentenceReadings.decode("6\t9\t喫茶店\tきっ[さ]てん", sentence).isEmpty())
    }

    private fun token(
        start: Int,
        end: Int,
        surface: String,
        reading: String,
        lemma: String = surface,
        lemmaReading: String = reading,
        pos: String = "名詞-普通名詞",
    ) = JapaneseToken(TargetSpan(start, end), surface, lemma, reading, lemmaReading, pos)
}
