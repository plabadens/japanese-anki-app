package dev.pierre.japaneseanki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class DraftModelTest {
    @Test
    fun withSentenceReplacesTextAndDropsTheOldTargetAndWordCluster() {
        val original = draft()

        val updated = original.withSentence("猫が眠る")

        assertEquals("猫が眠る", updated.sentence)
        assertNull(updated.target)
        assertEquals("", updated.vocabulary)
        assertEquals("", updated.vocabularyReading)
        assertEquals("", updated.definition)
        assertEquals("", updated.pitchAccent)
        assertEquals("", updated.sentenceReading)
        assertSame(original.readingCorrections, updated.readingCorrections)
    }

    @Test
    fun withSentenceReturnsTheSameModelWhenTextIsUnchanged() {
        val original = draft()
        assertSame(original, original.withSentence(original.sentence))
    }

    @Test
    fun withTargetClearsTheWordClusterButPreservesIndependentFields() {
        val original = draft()
        val selected = TargetSpan(2, 3)

        val updated = original.withTarget(selected)

        assertEquals(selected, updated.target)
        assertEquals("", updated.vocabulary)
        assertEquals("", updated.vocabularyReading)
        assertEquals("", updated.definition)
        assertEquals("", updated.pitchAccent)
        assertEquals("", updated.sentenceReading)
        assertEquals(original.notes, updated.notes)
        assertEquals(original.tags, updated.tags)
    }

    @Test
    fun withEntryUsesOnlyRequestedSensesAndClearsPitchAccent() {
        val entry = DictionaryEntry(
            id = 7,
            expression = "猫",
            reading = "ねこ",
            senses = listOf(
                DictionarySense(listOf("cat", "feline"), emptyList(), emptyList()),
                DictionarySense(listOf("a cat"), emptyList(), emptyList()),
            ),
        )

        val updated = draft().withEntry(entry.copy(expression = "犬", reading = "いぬ"), setOf(1, 0, 42))

        assertEquals("犬", updated.vocabulary)
        assertEquals("いぬ", updated.vocabularyReading)
        assertEquals("cat; feline\na cat", updated.definition)
        assertEquals("", updated.pitchAccent)
    }

    @Test
    fun withEntryPreservesPitchForTheSamePronunciation() {
        val original = draft()
        val updated = original.withEntry(
            DictionaryEntry(
                id = 8,
                expression = original.vocabulary,
                reading = original.vocabularyReading,
                senses = listOf(DictionarySense(listOf("feline"), emptyList(), emptyList())),
            ),
            setOf(0),
        )

        assertEquals("1", updated.pitchAccent)
        assertEquals("feline", updated.definition)
    }

    @Test
    fun clearedResetsCardFieldsAndUsesDefaultTagsWithoutDroppingCorrections() {
        val original = draft()
        val cleared = original.cleared("review")

        assertEquals(DraftModel(readingCorrections = original.readingCorrections, tags = "review"), cleared)
    }

    @Test
    fun withDefinitionChangesOnlyDefinition() {
        val original = draft()
        val updated = original.withDefinition("A feline.")

        assertEquals("A feline.", updated.definition)
        assertEquals(original.copy(definition = "A feline."), updated)
    }

    private fun draft() = DraftModel(
        sentence = "昨日、猫が寝た。",
        target = TargetSpan(3, 4),
        vocabulary = "猫",
        vocabularyReading = "ねこ",
        definition = "cat",
        pitchAccent = "1",
        sentenceReading = "ねこ",
        readingCorrections = listOf(ReadingCorrection(TargetSpan(3, 4), "猫", "ねこ")),
        notes = "note",
        tags = "tag",
    )
}
