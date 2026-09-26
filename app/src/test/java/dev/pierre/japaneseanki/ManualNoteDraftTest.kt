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

    @Test fun kanjiTargetAndVocabularyRequireReadingsOrExplicitOmission() {
        assertEquals(2, draft().validationErrors().size)
        assertTrue(draft(leaveSentenceTargetUnannotated = true).validationErrors().isEmpty())
        assertTrue(draft(leaveVocabularyUnannotated = true).validationErrors().isEmpty())
        assertTrue(
            draft(
                sentenceReading = "ねこ",
                vocabularyReading = "ねこ",
            ).validationErrors().isEmpty(),
        )
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

    @Test fun kanaOnlyContentDoesNotRequireManualReadingConfirmation() {
        val kana = draft().copy(sentence = "ねこがはしる", target = TargetSpan(0, 2), vocabulary = "ねこ")
        assertTrue(kana.validationErrors().isEmpty())
        assertNotNull(kana.fieldsByName())
    }

    @Test fun missingDefinitionAndInvalidTargetBlockSerialization() {
        assertNull(draft().copy(definition = "").fieldsByName())
        assertFalse(draft().copy(target = TargetSpan(10, 12)).validationErrors().isEmpty())
    }
}
