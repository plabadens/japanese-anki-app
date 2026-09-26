package dev.pierre.japaneseanki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JapaneseMorphologyTest {
    @Test fun readsConvertToHiraganaWithoutChangingPunctuationOrLongVowels() {
        assertEquals("きょう・たべー", katakanaToHiragana("キョウ・タベー"))
        assertEquals("ゝゞ", katakanaToHiragana("ヽヾ"))
    }

    @Test fun inflectedVerbAndAuxiliaryResolveToVerbLemma() {
        val tokens = listOf(
            token(0, 2, "食べ", "食べる", "動詞-一般", "たべ", "たべる"),
            token(2, 3, "た", "た", "助動詞-タ", "た"),
        )
        assertEquals(AnalyzedLemma("食べる", "たべる"), resolveAnalyzedLemma(tokens, TargetSpan(0, 3)))
    }

    @Test fun selectedSurfaceReadingJoinsInflectedTokensAndPreservesKana() {
        val tokens = listOf(
            token(0, 2, "食べ", "食べる", "動詞-一般", "たべ", "たべる"),
            token(2, 3, "た", "た", "助動詞-タ", "た"),
            token(3, 4, "。", "。", "補助記号-句点", ""),
        )
        assertEquals("たべた", resolveSurfaceReading(tokens, TargetSpan(0, 3)))
        assertNull(resolveSurfaceReading(tokens, TargetSpan(0, 4)))
    }

    @Test fun sahenNounAndSuruResolveAsOneLemma() {
        val tokens = listOf(
            token(0, 2, "勉強", "勉強", "名詞-普通名詞", "べんきょう"),
            token(2, 3, "し", "する", "動詞-非自立可能", "する"),
            token(3, 4, "た", "た", "助動詞-タ", "た"),
        )
        assertEquals(AnalyzedLemma("勉強する", "べんきょうする"), resolveAnalyzedLemma(tokens, TargetSpan(0, 4)))
    }

    @Test fun phrasesWithSeveralContentWordsRemainUnresolvedForDictionaryLookup() {
        val tokens = listOf(
            token(0, 2, "食べ", "食べる", "動詞-一般", "たべ", "たべる"),
            token(2, 3, "て", "て", "助詞-接続助詞", "て"),
            token(3, 4, "み", "みる", "動詞-一般", "み", "みる"),
        )
        assertNull(resolveAnalyzedLemma(tokens, TargetSpan(0, 4)))
        assertEquals(AnalyzedLemma("食べる", "たべる"), resolveAnalyzedLemma(tokens, TargetSpan(0, 3)))
    }

    private fun token(
        start: Int,
        end: Int,
        surface: String,
        lemma: String,
        pos: String,
        surfaceReading: String,
        lemmaReading: String = surfaceReading,
    ) = JapaneseToken(TargetSpan(start, end), surface, lemma, surfaceReading, lemmaReading, pos)
}
