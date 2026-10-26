package dev.pierre.japaneseanki

import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Furigana belongs to the kanji group, not to the whole lookup token. These are the AJT-style
 * alignment cases requested for the Card sentence, including the original spaces.
 */
@RunWith(AndroidJUnit4::class)
class FuriganaSpansTest {
    @Test
    fun ajtAlignmentKeepsOkuriganaOutsideTheBrackets() {
        assertEquals("食[た]べた", renderWord("食べた", "たべた"))
        assertEquals("痛[いた]い", renderWord("痛い", "いたい"))
        assertEquals("引[ひ]き出[だ]し", renderWord("引き出し", "ひきだし"))
        assertEquals("相合[あいあ]い傘[がさ]", renderWord("相合い傘", "あいあいがさ"))
        assertEquals("お金[かね]", renderWord("お金", "おかね"))
        assertEquals("猫[ねこ]。", renderWord("猫。", "ねこ"))
    }

    @Test
    fun adjacentKanjiLookupTokensJoinIntoOneFuriganaGroup() {
        val sentence = "雰囲気と部分"
        val tokens = listOf(
            token(sentence, 0, 2, "ふんい"),
            token(sentence, 2, 3, "き"),
            token(sentence, 3, 4, "と"),
            token(sentence, 4, 5, "ぶ"),
            token(sentence, 5, 6, "ぶん"),
        )

        assertEquals(
            "雰囲気[ふんいき]と部分[ぶぶん]",
            render(sentence, sentenceFuriganaSpans(sentence, tokens, target = null)),
        )
    }

    @Test
    fun exampleOneKeepsItsSpacesAndGroups() {
        val sentence = "頭の、どの部分が 痛いですか。"
        val tokens = listOf(
            token(sentence, 0, 1, "あたま"),
            token(sentence, 1, 2, "の"),
            token(sentence, 2, 3, ""),
            token(sentence, 3, 5, "どの"),
            token(sentence, 5, 7, "ぶぶん"),
            token(sentence, 7, 8, "が"),
            token(sentence, 9, 11, "いたい"),
            token(sentence, 11, 13, "です"),
            token(sentence, 13, 14, "か"),
            token(sentence, 14, 15, ""),
        )

        assertEquals(
            "頭[あたま]の、どの部分[ぶぶん]が 痛[いた]いですか。",
            render(sentence, sentenceFuriganaSpans(sentence, tokens, target = null)),
        )
    }

    @Test
    fun exampleTwoKeepsItsSpacesAndGroups() {
        val sentence = "彼には 特別な雰囲気がある。"
        val tokens = listOf(
            token(sentence, 0, 1, "かれ"),
            token(sentence, 1, 2, "に"),
            token(sentence, 2, 3, "は"),
            token(sentence, 4, 6, "とくべつ"),
            token(sentence, 6, 7, "な"),
            token(sentence, 7, 10, "ふんいき"),
            token(sentence, 10, 11, "が"),
            token(sentence, 11, 13, "ある"),
            token(sentence, 13, 14, ""),
        )

        assertEquals(
            "彼[かれ]には 特別[とくべつ]な雰囲気[ふんいき]がある。",
            render(sentence, sentenceFuriganaSpans(sentence, tokens, target = null)),
        )
    }

    private fun renderWord(surface: String, reading: String): String {
        val span = TargetSpan(0, surface.length)
        return render(surface, furiganaSpansForWord(span, surface, reading))
    }

    private fun render(sentence: String, spans: List<FuriganaSpan>): String {
        val sorted = spans.sortedBy { it.span.start }
        var cursor = 0
        return buildString {
            sorted.forEach { span ->
                append(sentence, cursor, span.span.start)
                append(sentence, span.span.start, span.span.end)
                append('[').append(span.reading).append(']')
                cursor = span.span.end
            }
            append(sentence, cursor, sentence.length)
        }
    }

    private fun token(sentence: String, start: Int, end: Int, reading: String): JapaneseToken {
        val surface = sentence.substring(start, end)
        val pos = if (surface.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }) {
            "名詞-普通名詞"
        } else {
            "助詞-格助詞"
        }
        return JapaneseToken(
            span = TargetSpan(start, end),
            surface = surface,
            lemma = surface,
            surfaceReading = reading,
            lemmaReading = reading,
            partOfSpeech = pos,
        )
    }
}
