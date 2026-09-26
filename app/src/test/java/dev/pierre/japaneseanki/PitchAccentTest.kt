package dev.pierre.japaneseanki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PitchAccentTest {
    @Test fun contractedKanaAndMoraCharactersAreCountedCorrectly() {
        assertEquals(listOf("しゅ", "っ", "ちょ", "う"), PitchAccent.create("しゅっちょう", 0)?.morae)
        assertEquals(listOf("ガ", "ッ", "コ", "ー"), PitchAccent.create("ガッコー", 0)?.morae)
    }

    @Test fun acceptsHeibanZeroAndSerializesAnkiClasses() {
        val pitch = PitchAccent.create("そうりょ", 0)!!
        assertEquals(listOf("そ", "う", "りょ"), pitch.morae)
        assertEquals(
            "<span class=\"low_rise\">そ</span><span class=\"high\">う</span>" +
                "<span class=\"high\">りょ</span> <span class=\"pitch_number\">0</span>",
            pitch.fieldMarkup,
        )
        assertTrue(pitch.contourDescription.contains("low high high"))
    }

    @Test fun handlesAtamadakaAndInternalDropPatterns() {
        assertEquals(
            listOf("high_drop", "low", "low", "low"),
            classes("がっこう", 1),
        )
        assertEquals(
            listOf("low_rise", "high_drop", "low", "low"),
            classes("がっこう", 2),
        )
        assertEquals(
            listOf("low_rise", "high", "high", "high"),
            classes("がっこう", 4),
        )
    }

    @Test fun rejectsInvalidReadingsAndOutOfRangeAccents() {
        assertNull(PitchAccent.create("かなabc", 0))
        assertNull(PitchAccent.create("かな", 3))
        assertNull(PitchAccent.create("かな", -1))
        assertFalse(PitchAccent.create("かな", 1)!!.fieldMarkup.contains("<script"))
    }

    @Test fun draftWillNotExportPitchForAStaleReading() {
        val pitch = PitchAccent.create("ねこ", 0)!!
        val draft = ManualNoteDraft(
            sentence = "猫",
            target = TargetSpan(0, 1),
            sentenceReading = "ねこ",
            leaveSentenceTargetUnannotated = false,
            vocabulary = "猫",
            vocabularyReading = "ねこ",
            leaveVocabularyUnannotated = false,
            definition = "cat",
            pitch = pitch,
        )
        assertTrue(draft.validationErrors().isEmpty())
        assertEquals(pitch.fieldMarkup, draft.fieldsByName()?.get("VocabPitch"))
        assertFalse(draft.copy(vocabularyReading = "びょう").validationErrors().isEmpty())
    }

    private fun classes(reading: String, accent: Int): List<String> =
        PitchAccent.create(reading, accent)!!.fieldMarkup
            .split("<span class=\"")
            .drop(1)
            .takeWhile { !it.startsWith("pitch_number") }
            .map { it.substringBefore("\"") }
}
