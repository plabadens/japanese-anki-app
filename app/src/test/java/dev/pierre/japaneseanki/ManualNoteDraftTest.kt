package dev.pierre.japaneseanki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualNoteDraftTest {
    private fun draft(
        sentenceReading: String = "",
        vocabularyReading: String = "",
    ) = ManualNoteDraft(
        sentence = "猫が走る",
        target = TargetSpan(0, 1),
        sentenceReading = sentenceReading,
        vocabulary = "猫",
        dictionaryEntryResolved = true,
        vocabularyReading = vocabularyReading,
        definition = "cat",
        translation = "The cat runs.",
        pitch = vocabularyReading.takeIf(String::isNotBlank)?.let { PitchAccent.create(it, 0) },
    )

    @Test fun unresolvedKanjiTargetWithoutPitchBlocksExport() {
        val unresolved = draft()
        assertTrue(unresolved.validationErrors().any { it.contains("pitch pattern") })
        assertNull(unresolved.fieldsByName())
    }

    @Test fun serializesManualFieldsWithControlledTargetMarkup() {
        val fields = draft(sentenceReading = "ねこ", vocabularyReading = "ねこ").fieldsByName()
        assertNotNull(fields)
        assertEquals("<b>猫</b>が走る", fields!!["SentKanji"])
        assertEquals("<b>猫[ねこ]</b>が走る", fields["SentFurigana"])
        assertEquals("猫[ねこ]", fields["VocabFurigana"])
        assertEquals("cat", fields["VocabDef"])
        assertEquals("", fields["fm_focus_words"])
        assertEquals(AnkiFieldContract.requiredFields.toSet(), fields.keys)
    }

    @Test fun analyzedTargetFuriganaDoesNotBlockSendingAndKeepsOutsideAnnotation() {
        val sentence = "昨日は学校"
        val target = TargetSpan(0, 2)
        val tokens = listOf(
            JapaneseToken(TargetSpan(0, 2), "昨日", "昨日", "きのう", "きのう", "名詞-普通名詞"),
            JapaneseToken(TargetSpan(2, 3), "は", "は", "は", "は", "助詞-係助詞"),
            JapaneseToken(TargetSpan(3, 5), "学校", "学校", "がっこう", "がっこう", "名詞-普通名詞"),
        )
        val annotations = sentenceFuriganaSpans(sentence, tokens, target)
        val note = draft(sentenceReading = "きのう", vocabularyReading = "きのう").copy(
            sentence = sentence,
            target = target,
            sentenceFurigana = annotations,
        )

        assertEquals(listOf(FuriganaSpan(TargetSpan(3, 5), "学校", "がっこう")), annotations)
        assertTrue(note.validationErrors().isEmpty())
        assertEquals("<b>昨日[きのう]</b>は学校[がっこう]", note.fieldsByName()!!["SentFurigana"])
    }

    @Test fun nonTargetFuriganaDoesNotChangeTheTargetOccurrenceOrSentenceText() {
        val fields = draft(sentenceReading = "ねこ", vocabularyReading = "ねこ")
            .copy(sentenceFurigana = listOf(FuriganaSpan(TargetSpan(2, 4), "走る", "はしる")))
            .fieldsByName()
        assertEquals("<b>猫[ねこ]</b>が走[はし]る", fields!!["SentFurigana"])
        assertEquals("<b>猫</b>が走る", fields["SentKanji"])
    }

    @Test fun unresolvedNonTargetFuriganaIsOptionalButReservedSyntaxIsRejected() {
        val base = draft(sentenceReading = "ねこ", vocabularyReading = "ねこ")
        val unresolved = base.copy(
            sentenceFurigana = listOf(FuriganaSpan(TargetSpan(2, 4), "走る", "")),
        )
        assertTrue(unresolved.validationErrors().isEmpty())
        assertEquals("<b>猫[ねこ]</b>が走る", unresolved.fieldsByName()!!["SentFurigana"])
        assertFalse(
            base.copy(sentenceFurigana = listOf(FuriganaSpan(TargetSpan(2, 4), "走る", "[はし]")))
                .validationErrors().isEmpty(),
        )
    }

    @Test fun definitionLinesRemainSeparateSenseItems() {
        val fields = draft(sentenceReading = "ねこ", vocabularyReading = "ねこ")
            .copy(definition = "cat\nfeline")
            .fieldsByName()
        assertEquals("<ol><li>cat</li><li>feline</li></ol>", fields!!["VocabDef"])
    }

    @Test fun kanaOnlyContentDoesNotRequireManualReadingConfirmation() {
        val kana = draft(vocabularyReading = "ねこ")
            .copy(sentence = "ねこがはしる", target = TargetSpan(0, 2), vocabulary = "ねこ")
        assertTrue(kana.validationErrors().isEmpty())
        assertNotNull(kana.fieldsByName())
    }

    @Test fun missingAndStalePitchBlockExport() {
        val unknown = draft().fieldsByName()
        assertNull(unknown)
        assertTrue(draft().validationErrors().any { it.contains("pitch pattern") })

        val stalePitch = PitchAccent.create("ねこ", 0)!!
        val stale = draft(vocabularyReading = "ねこ")
            .copy(vocabularyReading = "いぬ", pitch = stalePitch)
        assertTrue(stale.validationErrors().any { it.contains("Pitch does not match") })
        assertNull(stale.fieldsByName())
    }

    @Test fun typedVocabularyWithoutDictionaryEntryCannotSendOrExportPitch() {
        val pitch = PitchAccent.create("ねこ", 0)!!
        val unresolved = draft(vocabularyReading = "ねこ").copy(
            dictionaryEntryResolved = false,
            pitch = pitch,
        )
        assertTrue(unresolved.validationErrors().any { it.contains("dictionary entry") })
        assertNull(unresolved.fieldsByName())
    }

    @Test fun missingPitchIsRequiredEvenWhenReadingsAreOptional() {
        val optional = draft().copy(
            sentenceReading = "",
            vocabularyReading = "",
            pitch = null,
            dependentFieldsStale = false,
        )
        assertTrue(optional.validationErrors().any { it.contains("pitch pattern") })
        assertNull(optional.fieldsByName())
    }

    @Test fun validPitchSerializesAndRequiredNoteFieldsStillValidate() {
        val pitch = PitchAccent.create("ねこ", 0)!!
        val fields = draft(vocabularyReading = "ねこ").copy(pitch = pitch).fieldsByName()
        assertNotNull(fields)
        assertEquals(pitch.fieldMarkup, fields!!["VocabPitch"])

        assertNull(draft().copy(definition = "").fieldsByName())
        val staleRequiredFields = draft().copy(definition = "manually corrected", dependentFieldsStale = true)
        assertTrue(staleRequiredFields.validationErrors().any { it.contains("Review your definition") })
        assertNull(staleRequiredFields.fieldsByName())
        assertFalse(draft().copy(sentence = "").validationErrors().isEmpty())
        assertFalse(draft().copy(target = TargetSpan(10, 12)).validationErrors().isEmpty())
        assertFalse(draft().copy(vocabulary = "").validationErrors().isEmpty())
        assertFalse(draft().copy(translation = "   ").validationErrors().isEmpty())
        assertNull(draft().copy(translation = "").fieldsByName())
    }
}
