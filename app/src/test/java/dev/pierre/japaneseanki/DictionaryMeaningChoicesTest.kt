package dev.pierre.japaneseanki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryMeaningChoicesTest {
    private fun sense(gloss: String, vararg pos: String) =
        DictionarySense(glosses = listOf(gloss), partsOfSpeech = pos.toList(), notes = emptyList())

    @Test
    fun oneViableMeaningResolvesWithoutAsking() {
        val entry = DictionaryEntry(1, "本", "ほん", listOf(sense("book", "n")))
        val choices = DictionaryMeaningChoices.from(listOf(entry))

        assertEquals(1, choices.size)
        assertEquals(entry, choices.single().entry)
        assertEquals(0, choices.single().senseIndex)
        assertEquals("book", choices.single().definition)
        assertFalse(DictionaryMeaningChoices.hasAlternative(choices, choices.single()))
    }

    @Test
    fun distinctEntriesAndSensesRemainSeparateChoices() {
        val entry = DictionaryEntry(1, "本", "ほん", listOf(sense("book", "n"), sense("volume", "n")))
        val homograph = DictionaryEntry(2, "本", "もと", listOf(sense("origin", "n")))
        val choices = DictionaryMeaningChoices.from(listOf(entry, homograph))

        assertEquals(listOf("book", "volume", "origin"), choices.map { it.definition })
        assertTrue(DictionaryMeaningChoices.hasAlternative(choices, choices.first()))
        assertNull(DictionaryMeaningChoices.find(choices, 99, "本", "ほん", 0))
        assertEquals(homograph, DictionaryMeaningChoices.find(choices, 2, "本", "もと", 0)?.entry)
    }

    @Test
    fun unusableGlossesAndIndistinguishableRowsDoNotCreateFalseAmbiguity() {
        val first = DictionaryEntry(
            1,
            "本",
            "ほん",
            listOf(sense("book", "n"), sense("  ", "n"), sense("book", "n")),
        )
        val duplicateForm = DictionaryEntry(2, "本", "ほん", listOf(sense("book", "pn")))
        val missingReading = DictionaryEntry(3, "本", "", listOf(sense("volume", "n")))
        val choices = DictionaryMeaningChoices.from(listOf(first, duplicateForm, missingReading))

        assertEquals(1, choices.size)
        assertEquals("book", choices.single().definition)
        assertFalse(DictionaryMeaningChoices.hasAlternative(choices, choices.single()))
    }

    @Test
    fun unresolvedMultipleChoicesCanBeReopenedAfterSheetDismissal() {
        val entry = DictionaryEntry(1, "本", "ほん", listOf(sense("book", "n"), sense("volume", "n")))
        val choices = DictionaryMeaningChoices.from(listOf(entry))

        assertTrue(DictionaryMeaningChoices.hasAlternative(choices, selected = null))
        assertFalse(DictionaryMeaningChoices.hasAlternative(choices.take(1), selected = null))
    }
}
