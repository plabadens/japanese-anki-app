package dev.pierre.japaneseanki

import androidx.test.ext.junit.runners.AndroidJUnit4

import dev.pierre.japaneseanki.ui.spanSurface
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The analyzer returns UniDic morphemes; the target sheet must expose the units a learner mines.
 * These tests use the exact raw segmentation observed on the API-37 device for
 * 図書館で本を読みました。.
 */
@RunWith(AndroidJUnit4::class)
class TokenGroupingTest {
    private val sentence = "図書館で本を読みました。"

    private fun raw(
        start: Int,
        end: Int,
        lemma: String,
        pos: String,
        reading: String,
        lemmaReading: String = reading,
    ) = JapaneseToken(
        span = TargetSpan(start, end),
        surface = sentence.substring(start, end),
        lemma = lemma,
        surfaceReading = reading,
        lemmaReading = lemmaReading,
        partOfSpeech = pos,
    )

    private val morphemes = listOf(
        raw(0, 2, "図書", "名詞-普通名詞-一般", "としょ"),
        raw(2, 3, "館", "接尾辞-名詞的-一般", "かん"),
        raw(3, 4, "で", "助詞-格助詞", "で"),
        raw(4, 5, "本", "名詞-普通名詞-一般", "ほん"),
        raw(5, 6, "を", "助詞-格助詞", "を"),
        raw(6, 8, "読む", "動詞-一般", "よみ", "よむ"),
        raw(8, 10, "ます", "助動詞-マス", "まし", "ます"),
        raw(10, 11, "た", "助動詞-タ", "た", "た"),
        raw(11, 12, "。", "補助記号-句点", ""),
    )

    @Test
    fun compoundNounAndPolitePastBecomeSingleSelectionUnits() {
        val grouped = groupTokens(morphemes)

        assertEquals(
            listOf("図書館", "で", "本", "を", "読みました", "。"),
            grouped.map { it.surface },
        )
        val library = grouped.first()
        assertEquals("図書館", library.lemma)
        assertEquals("としょかん", library.surfaceReading)
        assertEquals("としょかん", library.lemmaReading)

        val read = grouped.single { it.surface == "読みました" }
        assertEquals("読む", read.lemma)
        assertEquals("よむ", read.lemmaReading)
        assertEquals("よみました", read.surfaceReading)
    }

    @Test
    fun groupingLeavesTheRawMorphemeListAvailableForIdentityReconciliation() {
        val raw = morphemes.toMutableList()
        val before = raw.toList()

        val grouped = groupTokens(raw)

        assertEquals(before, raw)
        assertEquals(listOf("図書館", "で", "本", "を", "読みました", "。"), grouped.map { it.surface })
        assertEquals(TargetSpan(0, 2), raw.first().span)
        assertEquals(TargetSpan(0, 3), grouped.first().span)
    }

    @Test
    fun groupedPredicateStillResolvesTheDictionaryLemmaAndSurfaceReading() {
        val grouped = groupTokens(morphemes)

        assertEquals(AnalyzedLemma("読む", "よむ"), resolveAnalyzedLemma(grouped, TargetSpan(6, 11)))
        assertEquals("よみました", resolveSurfaceReading(sentence, grouped, TargetSpan(6, 11)))
        assertEquals("読みました", spanSurface(grouped, TargetSpan(6, 11)))
    }

    @Test
    fun furiganaUsesTheGroupedWordNotTheMorphemePieces() {
        val grouped = groupTokens(morphemes)

        assertEquals(
            listOf(
                FuriganaSpan(TargetSpan(0, 3), "図書館", "としょかん"),
                FuriganaSpan(TargetSpan(6, 11), "読みました", "よみました"),
            ),
            sentenceFuriganaSpans(sentence, grouped, TargetSpan(4, 5)),
        )
    }

    @Test
    fun teFormAndFollowingAuxiliaryVerbBecomeOneUnit() {
        val text = "食べている。"
        val raw = listOf(
            token(text, 0, 2, "食べる", "動詞-一般", "たべ", "たべる"),
            token(text, 2, 3, "て", "助詞-接続助詞", "て", "て"),
            token(text, 3, 5, "いる", "動詞-非自立可能", "いる", "いる"),
            token(text, 5, 6, "。", "補助記号-句点", ""),
        )

        val grouped = groupTokens(raw)

        assertEquals(listOf("食べている", "。"), grouped.map { it.surface })
        assertEquals("食べる", grouped.first().lemma)
        assertEquals("たべている", grouped.first().surfaceReading)
    }

    @Test
    fun prefixAndCounterAttachToTheirHead() {
        val text = "お茶を3冊。"
        val raw = listOf(
            token(text, 0, 1, "お", "接頭辞", "お", "お"),
            token(text, 1, 2, "茶", "名詞-普通名詞-一般", "ちゃ", "ちゃ"),
            token(text, 2, 3, "を", "助詞-格助詞", "を", "を"),
            token(text, 3, 4, "3", "名詞-数詞", "さん", "さん"),
            token(text, 4, 5, "冊", "接尾辞-名詞的-助数詞", "さつ", "さつ"),
            token(text, 5, 6, "。", "補助記号-句点", ""),
        )

        assertEquals(
            listOf("お茶", "を", "3冊", "。"),
            groupTokens(raw).map { it.surface },
        )
    }

    @Test
    fun dictionaryCompoundJoinsOnlyWhenTheDictionaryKnowsIt() {
        val text = "東京大学。"
        val raw = listOf(
            token(text, 0, 2, "東京", "名詞-固有名詞-地名-一般", "とうきょう", "とうきょう"),
            token(text, 2, 4, "大学", "名詞-普通名詞-一般", "だいがく", "だいがく"),
            token(text, 4, 5, "。", "補助記号-句点", ""),
        )

        assertEquals(listOf("東京", "大学", "。"), groupTokens(raw).map { it.surface })
        assertEquals(
            listOf("東京大学", "。"),
            groupTokens(raw) { it == "東京大学" }.map { it.surface },
        )
    }

    @Test
    fun aGapOrAParticlePreventsAcrossTheBoundaryMerge() {
        val text = "食べてから。"
        val raw = listOf(
            token(text, 0, 2, "食べる", "動詞-一般", "たべ", "たべる"),
            token(text, 2, 3, "て", "助詞-接続助詞", "て", "て"),
            token(text, 3, 5, "から", "助詞-接続助詞", "から", "から"),
            token(text, 5, 6, "。", "補助記号-句点", ""),
        )

        assertEquals(listOf("食べて", "から", "。"), groupTokens(raw).map { it.surface })
    }

    private fun token(
        text: String,
        start: Int,
        end: Int,
        lemma: String,
        pos: String,
        reading: String,
        lemmaReading: String = reading,
    ) = JapaneseToken(
        span = TargetSpan(start, end),
        surface = text.substring(start, end),
        lemma = lemma,
        surfaceReading = reading,
        lemmaReading = lemmaReading,
        partOfSpeech = pos,
    )
}
