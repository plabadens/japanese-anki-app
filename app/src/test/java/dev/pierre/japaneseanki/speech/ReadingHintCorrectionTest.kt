package dev.pierre.japaneseanki.speech

import androidx.test.ext.junit.runners.AndroidJUnit4

import dev.pierre.japaneseanki.FuriganaSpan
import dev.pierre.japaneseanki.TargetSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The Chirp hint payload must be rebuilt from the corrected readings (SPEC §4.5, §6.2). */
@RunWith(AndroidJUnit4::class)
class ReadingHintCorrectionTest {
    private val voice = CloudVoice("ja-JP-Chirp3-HD-Achernar", "Achernar", listOf("ja-JP"), 24_000)
    private val sentence = "昨日は新しい喫茶店に行った。"
    private val target = TargetSpan(6, 9)

    @Test fun correctedSpanReadingBecomesThePhraseHint() {
        val furigana = listOf(
            FuriganaSpan(TargetSpan(0, 2), "昨日", "きのうび"),
            FuriganaSpan(TargetSpan(3, 6), "新しい", "あたらしい"),
        )

        val result = sentenceHintSpans(sentence, target, "きっさてん", furigana)

        assertEquals(
            listOf(
                ReadingHintSpan(0, 2, "昨日", "きのうび"),
                ReadingHintSpan(3, 6, "新しい", "あたらしい"),
                ReadingHintSpan(6, 9, "喫茶店", "きっさてん"),
            ),
            result,
        )
        val hints = (ReadingHintBuilder.build(sentence, result) as HintResult.Ready).hints
        assertTrue(hints.contains(ReadingHint("昨日", "きのうび")))
    }

    @Test fun theSerialisedRequestCarriesTheCorrectedPronunciation() {
        val furigana = listOf(FuriganaSpan(TargetSpan(0, 2), "昨日", "きのうび"))
        val hints = (ReadingHintBuilder.build(
            sentence,
            sentenceHintSpans(sentence, target, "きっちゃてん", furigana),
        ) as HintResult.Ready).hints

        val json = buildSynthesisJson(SentenceSpeechRequest(sentence, voice, hints))

        assertTrue(json.contains("\"phrase\":\"昨日\""))
        assertTrue(json.contains("\"pronunciation\":\"きのうび\""))
        assertTrue(json.contains("\"phrase\":\"喫茶店\""))
        assertTrue(json.contains("\"pronunciation\":\"きっちゃてん\""))
        assertTrue(json.contains("PHONETIC_ENCODING_JAPANESE_YOMIGANA"))
        // The exported sentence text itself is never rewritten into kana.
        assertTrue(json.contains("\"text\":\"$sentence\""))
    }

    @Test fun aKanaOnlyTargetAddsNoHintAndAnUnresolvedTargetIsImpossible() {
        val kanaOnly = "これはぺんです"
        assertTrue(sentenceHintSpans(kanaOnly, TargetSpan(3, 5), "ぺん", emptyList()).isEmpty())

        val unresolved = sentenceHintSpans(sentence, target, "", emptyList())
        assertTrue(ReadingHintBuilder.build(sentence, unresolved) is HintResult.Impossible)
    }

    @Test fun anUnresolvedNonTargetReadingBlocksThePaidRequestInsteadOfBeingOmitted() {
        val furigana = listOf(FuriganaSpan(TargetSpan(0, 2), "昨日", ""))
        val spans = sentenceHintSpans(sentence, target, "きっさてん", furigana)

        // An unknown reading must block synthesis, never be dropped silently (SPEC §6.2).
        assertTrue(spans.any { it.surface == "昨日" })
        assertTrue(ReadingHintBuilder.build(sentence, spans) is HintResult.Impossible)
    }
}
