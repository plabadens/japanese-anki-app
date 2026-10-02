package dev.pierre.japaneseanki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AnkiFieldContractTest {
    @Test fun parsesTagsWithoutBreakingHierarchy() {
        assertEquals(setOf("JLPT::N4", "japanese::mining"), AnkiFieldContract.parseTags("  JLPT::N4   japanese::mining JLPT::N4 "))
    }

    @Test fun mapsRequiredFieldsByNameAndLeavesExtrasEmpty() {
        val fields = listOf("Custom", "VocabKanji", "SentKanji") +
            AnkiFieldContract.requiredFields.filterNot { it == "VocabKanji" || it == "SentKanji" }
        val mapping = AnkiFieldContract.mapToModelOrder(
            fields,
            mapOf("SentKanji" to "sentence", "VocabKanji" to "lemma"),
        )

        assertTrue(mapping.isValid)
        assertEquals("", mapping.values.first())
        assertEquals("lemma", mapping.values[1])
        assertEquals("sentence", mapping.values[2])
    }

    @Test fun rejectsMissingOrRepeatedRequiredFields() {
        assertFalse(AnkiFieldContract.mapToModelOrder(emptyList(), emptyMap()).isValid)
        assertFalse(
            AnkiFieldContract.mapToModelOrder(
                AnkiFieldContract.requiredFields + "SentKanji",
                emptyMap(),
            ).isValid,
        )
    }

    @Test fun duplicateKeyUsesReorderedModelFieldsAndPreservesTrailingEmptyFields() {
        val modelFields = listOf("Extra", "VocabKanji", "SentKanji", "SentEng", "fm_seen_words")
        val serializedFields = "unused\u001f犬\u001f<b>猫</b>と鳥\u001ftranslation\u001f"
        val key = DuplicateKey.fromModelFields(modelFields, serializedFields)

        assertTrue(key.matches(DuplicateKey.fromFields("猫と鳥", "犬")))
        assertFalse(key.matches(DuplicateKey.fromFields("猫と鳥", "猫")))
        assertEquals("", serializedFields.split('\u001f', limit = Int.MAX_VALUE).last())
    }

    @Test fun duplicateKeyTrimsOuterWhitespaceButPreservesInternalWhitespaceAndDecodesEntities() {
        val key = DuplicateKey.fromFields("  <b>猫</b>&nbsp;と犬  ", " 犬 &#x732B; ")
        assertTrue(key.matches(DuplicateKey.fromFields("猫\u00a0と犬", "犬 猫")))
        assertFalse(key.matches(DuplicateKey.fromFields("猫 と犬", "犬 猫")))
        assertFalse(key.matches(DuplicateKey.fromFields("猫\u00a0 と犬", "犬 猫")))
    }

    @Test fun duplicateKeyFailsClosedForMissingFieldsOrTruncatedSerializedFields() {
        assertThrows(IllegalArgumentException::class.java) {
            DuplicateKey.fromModelFields(listOf("SentKanji"), "sentence")
        }
        assertThrows(IllegalArgumentException::class.java) {
            DuplicateKey.fromModelFields(listOf("SentKanji", "VocabKanji"), "sentence")
        }
    }

    @Test fun duplicateKeyIgnoresOnlyGeneratedMarkupAndOuterWhitespace() {
        val exported = DuplicateKey.fromFields("  <b>猫</b>と犬 &amp;鳥  ", " 犬 ")
        val plain = DuplicateKey.fromFields("猫と犬 &amp;鳥", "犬")
        assertTrue(exported.matches(plain))
        assertTrue(
            exported.matches(
                DuplicateKey.fromFields("<STRONG>猫</STRONG>と犬 &amp;鳥", "犬"),
            ),
        )
        assertTrue(
            exported.matches(
                DuplicateKey.fromFields("<B class=\"target\">猫</B>と犬 &amp;鳥", "犬"),
            ),
        )
        assertFalse(exported.matches(DuplicateKey.fromFields("猫 と犬 &amp;鳥", "犬")))
        assertTrue(exported.matches(DuplicateKey.fromFields("猫と犬 &amp;鳥", "<b>犬</b>")))
        assertFalse(exported.matches(DuplicateKey.fromFields("猫と犬 &amp;鳥", "猫")))
    }
}
