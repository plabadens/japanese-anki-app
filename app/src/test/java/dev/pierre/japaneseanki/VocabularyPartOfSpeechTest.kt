package dev.pierre.japaneseanki

import org.junit.Assert.assertEquals
import org.junit.Test

class VocabularyPartOfSpeechTest {
    private fun sense(vararg pos: String) =
        DictionarySense(glosses = emptyList(), partsOfSpeech = pos.toList(), notes = emptyList())

    @Test
    fun selectedSenseInheritsTheLatestExplicitJmdictPos() {
        val entry = DictionaryEntry(
            id = 10,
            expression = "猫",
            reading = "ねこ",
            senses = listOf(sense("n"), sense(), sense("n", "adj-no")),
        )

        assertEquals(listOf("n"), VocabularyPartOfSpeech.forSelectedSenses(entry, setOf(1)))
        assertEquals(listOf("n", "adj-no"), VocabularyPartOfSpeech.forSelectedSenses(entry, setOf(2)))
    }

    @Test
    fun selectedSensesDeduplicateStablyInSourceSenseOrder() {
        val entry = DictionaryEntry(
            id = 11,
            expression = "読む",
            reading = "よむ",
            senses = listOf(sense("v5m", "vt"), sense("v5m"), sense()),
        )

        // Set iteration order is deliberately not the source order.
        assertEquals(listOf("v5m", "vt"), VocabularyPartOfSpeech.forSelectedSenses(entry, setOf(2, 0, 1)))
    }

    @Test
    fun missingPosIsNotGuessedBeforeAnyExplicitTag() {
        val entry = DictionaryEntry(
            id = 12,
            expression = "語",
            reading = "ご",
            senses = listOf(sense(), sense("  "), sense("n"), sense()),
        )

        assertEquals(emptyList<String>(), VocabularyPartOfSpeech.forSelectedSenses(entry, setOf(0, 1)))
        assertEquals(listOf("n"), VocabularyPartOfSpeech.forSelectedSenses(entry, setOf(3)))
    }

    @Test
    fun invalidSenseIndicesAreIgnoredAndNeverBorrowedAcrossEntries() {
        val first = DictionaryEntry(13, "語", "ご", listOf(sense("n")))
        val second = DictionaryEntry(14, "別語", "べつご", listOf(sense()))

        assertEquals(listOf("n"), VocabularyPartOfSpeech.forSelectedSenses(first, setOf(0, 4, -1)))
        assertEquals(emptyList<String>(), VocabularyPartOfSpeech.forSelectedSenses(second, setOf(0)))
        assertEquals(emptyList<String>(), VocabularyPartOfSpeech.forSelectedSenses(second, setOf(-1, 1)))
    }

    @Test
    fun noEntryOrNoSelectedSenseProducesNoPos() {
        val entry = DictionaryEntry(15, "犬", "いぬ", listOf(sense("n")))

        assertEquals(emptyList<String>(), VocabularyPartOfSpeech.forSelectedSenses(null, setOf(0)))
        assertEquals(emptyList<String>(), VocabularyPartOfSpeech.forSelectedSenses(entry, emptySet()))
    }
}
