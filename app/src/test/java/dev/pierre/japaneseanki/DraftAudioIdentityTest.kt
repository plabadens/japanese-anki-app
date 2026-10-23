package dev.pierre.japaneseanki

import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A sentence-reading correction invalidates the sentence recording and nothing else; a vocabulary
 * reading correction re-resolves the word recording (SPEC §10 rebuild table).
 */
@RunWith(AndroidJUnit4::class)
class DraftAudioIdentityTest {
    private val sentence = "昨日は新しい喫茶店に行った。"
    private val target = TargetSpan(6, 9)
    private val furigana = listOf(
        FuriganaSpan(TargetSpan(0, 2), "昨日", "きのう"),
        FuriganaSpan(TargetSpan(3, 6), "新しい", "あたらしい"),
    )

    @Test fun identicalInputsProduceStableIdentities() {
        assertEquals(
            DraftAudioIdentity.sentence(sentence, target, "きっさてん", furigana, "ja-JP-Chirp3-HD-Achernar"),
            DraftAudioIdentity.sentence(sentence, target, "きっさてん", furigana, "ja-JP-Chirp3-HD-Achernar"),
        )
        assertEquals(
            DraftAudioIdentity.word(7L, "喫茶店", "きっさてん"),
            DraftAudioIdentity.word(7L, "喫茶店", "きっさてん"),
        )
    }

    @Test fun sentenceRequestIdentityDoesNotDependOnSelectedVocabulary() {
        val hints = listOf(
            dev.pierre.japaneseanki.speech.ReadingHintSpan(0, 2, "昨日", "きのう"),
            dev.pierre.japaneseanki.speech.ReadingHintSpan(6, 9, "喫茶店", "きっさてん"),
        )
        assertEquals(
            DraftAudioIdentity.sentenceRequest(sentence, hints, "voice"),
            DraftAudioIdentity.sentenceRequest(sentence, hints, "voice"),
        )
        assertNotEquals(
            DraftAudioIdentity.sentenceRequest(sentence, hints, "voice-a"),
            DraftAudioIdentity.sentenceRequest(sentence, hints, "voice-b"),
        )
    }

    @Test fun aSentenceReadingCorrectionInvalidatesOnlyTheSentenceRecording() {
        val corrected = listOf(
            FuriganaSpan(TargetSpan(0, 2), "昨日", "きのうび"),
            FuriganaSpan(TargetSpan(3, 6), "新しい", "あたらしい"),
        )

        assertNotEquals(
            DraftAudioIdentity.sentence(sentence, target, "きっさてん", furigana, "voice"),
            DraftAudioIdentity.sentence(sentence, target, "きっさてん", corrected, "voice"),
        )
        assertEquals(
            DraftAudioIdentity.word(7L, "喫茶店", "きっさてん"),
            DraftAudioIdentity.word(7L, "喫茶店", "きっさてん"),
        )
    }

    @Test fun aTargetReadingCorrectionInvalidatesTheSentenceRecording() {
        assertNotEquals(
            DraftAudioIdentity.sentence(sentence, target, "きっさてん", furigana, "voice"),
            DraftAudioIdentity.sentence(sentence, target, "きっちゃてん", furigana, "voice"),
        )
    }

    @Test fun aVocabularyReadingCorrectionChangesTheWordRecordingIdentity() {
        assertNotEquals(
            DraftAudioIdentity.word(7L, "喫茶店", "きっさてん"),
            DraftAudioIdentity.word(7L, "喫茶店", "きっちゃてん"),
        )
        assertNotEquals(
            DraftAudioIdentity.word(7L, "喫茶店", "きっさてん"),
            DraftAudioIdentity.word(8L, "喫茶店", "きっさてん"),
        )
        assertEquals(
            DraftAudioIdentity.word(null, "喫茶店", "きっさてん"),
            DraftAudioIdentity.word(null, "喫茶店", "きっさてん"),
        )
    }

    @Test fun aVoiceChangeInvalidatesOnlyTheSentenceRecording() {
        assertNotEquals(
            DraftAudioIdentity.sentence(sentence, target, "きっさてん", furigana, "voice-a"),
            DraftAudioIdentity.sentence(sentence, target, "きっさてん", furigana, "voice-b"),
        )
        assertEquals(
            DraftAudioIdentity.word(7L, "喫茶店", "きっさてん"),
            DraftAudioIdentity.word(7L, "喫茶店", "きっさてん"),
        )
    }
}
