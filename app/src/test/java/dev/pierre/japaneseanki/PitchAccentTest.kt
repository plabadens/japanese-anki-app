package dev.pierre.japaneseanki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PitchAccentTest {
    private val akugyakuHtml = "<span class=\"low_rise\">ア</span><span class=\"high\">ク" +
        "<span class=\"nasal\">キ<span class=\"handakuten\">&#176;</span></span>ャク</span>"
    private val uketsukeHtml = "<span class=\"low_rise\">ウ</span><span class=\"high\">ケ" +
        "<span class=\"devoiced\">ツ</span>ケ</span>"

    @Test fun sourceHtmlIsExportedVerbatimWithPitchNumber() {
        val pitch = PitchAccent.create("アクギャク", 0, akugyakuHtml)!!
        assertEquals("$akugyakuHtml <span class=\"pitch_number\">0</span>", pitch.fieldMarkup)
        assertEquals(4, pitch.morae.size)
    }

    @Test fun nestedNasalMarkIsParsedOntoItsOwnMora() {
        val pitch = PitchAccent.create("アクギャク", 0, akugyakuHtml)!!
        assertEquals(setOf(2), pitch.marks.nasalMorae)
        assertTrue(pitch.marks.devoicedMorae.isEmpty())
    }

    @Test fun handakutenMarkIsNotAMora() {
        val pitch = PitchAccent.create("アクギャク", 0, akugyakuHtml)!!
        assertEquals(listOf("ア", "ク", "ギャ", "ク"), pitch.morae)
    }

    @Test fun devoicedMarkIsPreserved() {
        val html = "<span class=\"high_drop\">オ</span><span class=\"low\"><span class=\"devoiced\">ク</span>セ</span>"
        val pitch = PitchAccent.create("オクセ", 1, html)!!
        assertEquals(setOf(1), pitch.marks.devoicedMorae)
    }

    @Test fun user受付FixturePreservesDevoicedTsAndExactHtml() {
        val pitch = PitchAccent.create("うけつけ", 0, uketsukeHtml)!!

        assertEquals("$uketsukeHtml <span class=\"pitch_number\">0</span>", pitch.fieldMarkup)
        assertEquals(setOf(2), pitch.marks.devoicedMorae)
        assertTrue(pitch.marks.nasalMorae.isEmpty())
        assertEquals("ウケツケ", katakana("うけつけ"))
    }

    @Test fun htmlForADifferentReadingIsRejected() {
        assertNull(PitchAccent.create("アクギャク", 0, "<span class=\"high\">サクラ</span>"))
    }

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

    @Test fun identifiesTheFourPitchPatternColors() {
        assertEquals(PitchPatternKind.Heiban, PitchAccent.create("がっこう", 0)!!.patternKind)
        assertEquals(PitchPatternKind.Atamadaka, PitchAccent.create("がっこう", 1)!!.patternKind)
        assertEquals(PitchPatternKind.Nakadaka, PitchAccent.create("がっこう", 2)!!.patternKind)
        assertEquals(PitchPatternKind.Odaka, PitchAccent.create("がっこう", 4)!!.patternKind)
    }

    @Test fun rejectsInvalidReadingsAndOutOfRangeAccents() {
        assertNull(PitchAccent.create("かなabc", 0))
        assertNull(PitchAccent.create("かな", 3))
        assertNull(PitchAccent.create("かな", -1))
        assertFalse(PitchAccent.create("かな", 1)!!.fieldMarkup.contains("<script"))
    }

    @Test fun stalePitchBlocksExport() {
        val pitch = PitchAccent.create("ねこ", 0)!!
        val draft = ManualNoteDraft(
            sentence = "猫",
            target = TargetSpan(0, 1),
            sentenceReading = "ねこ",
            vocabulary = "猫",
            vocabularyReading = "ねこ",
            definition = "cat",
            translation = "A cat.",
            dictionaryEntryResolved = true,
            pitch = pitch,
        )
        assertTrue(draft.validationErrors().isEmpty())
        assertEquals(pitch.fieldMarkup, draft.fieldsByName()?.get("VocabPitch"))

        val mismatchedPitch = draft.copy(vocabularyReading = "びょう")
        assertTrue(mismatchedPitch.validationErrors().any { it.contains("Pitch does not match") })
        assertNull(mismatchedPitch.fieldsByName())
    }

    private fun classes(reading: String, accent: Int): List<String> =
        PitchAccent.create(reading, accent)!!.fieldMarkup
            .split("<span class=\"")
            .drop(1)
            .takeWhile { !it.startsWith("pitch_number") }
            .map { it.substringBefore("\"") }
}
