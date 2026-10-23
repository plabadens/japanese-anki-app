package dev.pierre.japaneseanki

import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnkiFieldContractTest {
    @Test fun providerFieldNamesPreserveEmptyPositionsAndTrailingFields() {
        assertEquals(listOf("Extra", "SentKanji", "", "VocabKanji", ""),
            AnkiFieldContract.parseModelFieldNames("Extra\u001fSentKanji\u001f\u001fVocabKanji\u001f"))
        assertEquals(emptyList<String>(), AnkiFieldContract.parseModelFieldNames(null))
        assertEquals(emptyList<String>(), AnkiFieldContract.parseModelFieldNames(""))
    }

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

    @Test fun duplicateEntityScannerPreservesItsDistinctNumericPolicyAndOrdering() {
        assertEquals("x\u0000y", DuplicateKey.fromFields("x&#0;y", "lemma").sentence)
        assertEquals("\uD7FF", DuplicateKey.fromFields("&#55295;", "lemma").sentence)
        assertEquals("&#55296;", DuplicateKey.fromFields("&#55296;", "lemma").sentence)
        assertEquals("&#57343;", DuplicateKey.fromFields("&#57343;", "lemma").sentence)
        assertEquals("\uE000", DuplicateKey.fromFields("&#57344;", "lemma").sentence)
        assertEquals("\uD83D\uDE00", DuplicateKey.fromFields("&#128512;", "lemma").sentence)
        assertEquals("\uDBFF\uDFFF", DuplicateKey.fromFields("&#1114111;", "lemma").sentence)
        assertEquals("&#1114112;", DuplicateKey.fromFields("&#1114112;", "lemma").sentence)
        assertEquals("&#-1;", DuplicateKey.fromFields("&#-1;", "lemma").sentence)
        assertEquals("&#x-1;", DuplicateKey.fromFields("&#x-1;", "lemma").sentence)
        assertEquals("A", DuplicateKey.fromFields("&#+65;", "lemma").sentence)
        assertEquals("A", DuplicateKey.fromFields("&#x+41;", "lemma").sentence)
        assertEquals("A", DuplicateKey.fromFields("&#X41;", "lemma").sentence)
        assertEquals("'", DuplicateKey.fromFields("&#39;", "lemma").sentence)
        assertEquals("&#2147483648;", DuplicateKey.fromFields("&#2147483648;", "lemma").sentence)
        assertEquals("A", DuplicateKey.fromFields("&#x000000041;", "lemma").sentence)
        assertEquals("&#x0000000041;", DuplicateKey.fromFields("&#x0000000041;", "lemma").sentence)
        assertEquals("&AMP;", DuplicateKey.fromFields("&AMP;", "lemma").sentence)
        assertEquals("&missing;", DuplicateKey.fromFields("&missing;", "lemma").sentence)
        assertEquals("&amp", DuplicateKey.fromFields("&amp", "lemma").sentence)
        assertEquals("&<", DuplicateKey.fromFields("&amp;&lt;", "lemma").sentence)
        assertEquals("&lt;", DuplicateKey.fromFields("&amp;lt;", "lemma").sentence)
        assertEquals("<b>猫</b>", DuplicateKey.fromFields("&lt;b&gt;猫&lt;/b&gt;", "lemma").sentence)
    }

    @Test fun duplicateKeyMatchesUseTheExactPreservedUtf16CanonicalValue() {
        val surrogateEntity = DuplicateKey.fromFields("&#55296;", "lemma")
        assertTrue(surrogateEntity.matches(DuplicateKey.fromFields("&#55296;", "lemma")))
        assertFalse(surrogateEntity.matches(DuplicateKey.fromFields("\uD800", "lemma")))
        assertTrue(DuplicateKey.fromFields("  猫&#32;犬  ", "lemma").matches(
            DuplicateKey.fromFields("猫 犬", "lemma"),
        ))
        assertFalse(DuplicateKey.fromFields("猫&#32;犬", "lemma").matches(
            DuplicateKey.fromFields("猫犬", "lemma"),
        ))
    }
}
