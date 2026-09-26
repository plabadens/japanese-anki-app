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
        leaveSentenceTargetUnannotated: Boolean = false,
        vocabularyReading: String = "",
        leaveVocabularyUnannotated: Boolean = false,
    ) = ManualNoteDraft(
        sentence = "猫が走る",
        target = TargetSpan(0, 1),
        sentenceReading = sentenceReading,
        leaveSentenceTargetUnannotated = leaveSentenceTargetUnannotated,
        vocabulary = "猫",
        vocabularyReading = vocabularyReading,
        leaveVocabularyUnannotated = leaveVocabularyUnannotated,
        definition = "cat",
    )

    @Test fun unresolvedKanjiTargetAndVocabularyAreSendableAsOriginalText() {
        val unresolved = draft()
        assertTrue(unresolved.validationErrors().isEmpty())

        val fields = unresolved.fieldsByName()
        assertNotNull(fields)
        assertEquals("<b>猫</b>が走る", fields!!["SentKanji"])
        assertEquals("<b>猫</b>が走る", fields["SentFurigana"])
        assertEquals("猫", fields["VocabFurigana"])
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
        val kana = draft().copy(sentence = "ねこがはしる", target = TargetSpan(0, 2), vocabulary = "ねこ")
        assertTrue(kana.validationErrors().isEmpty())
        assertNotNull(kana.fieldsByName())
    }

    @Test fun unknownPitchIsEmptyAndStalePitchDoesNotBlockOrSerialize() {
        val unknown = draft().fieldsByName()
        assertNotNull(unknown)
        assertEquals("", unknown!!["VocabPitch"])

        val stalePitch = PitchAccent.create("ねこ", 0)!!
        val stale = draft(vocabularyReading = "ねこ")
            .copy(vocabularyReading = "いぬ", pitch = stalePitch)
        assertTrue(stale.validationErrors().isEmpty())
        assertEquals("", stale.fieldsByName()!!["VocabPitch"])
    }

    @Test fun validPitchSerializesAndRequiredNoteFieldsStillValidate() {
        val pitch = PitchAccent.create("ねこ", 0)!!
        val fields = draft(vocabularyReading = "ねこ").copy(pitch = pitch).fieldsByName()
        assertNotNull(fields)
        assertEquals(pitch.fieldMarkup, fields!!["VocabPitch"])

        assertNull(draft().copy(definition = "").fieldsByName())
        assertFalse(draft().copy(sentence = "").validationErrors().isEmpty())
        assertFalse(draft().copy(target = TargetSpan(10, 12)).validationErrors().isEmpty())
        assertFalse(draft().copy(vocabulary = "").validationErrors().isEmpty())
    }
}
