package dev.pierre.japaneseanki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vocabulary-reading correction re-resolves the dictionary entry, pitch and recording identity
 * (SPEC §4.5, §5.3) and the ambiguity reset that stops a previous word's data from being shown
 * (ACCEPTANCE v0.3 known defect 1).
 */
class VocabularyReadingsTest {
    private val candidates = listOf(
        ReadingCandidate(1, "喫茶", "きっさ"),
        ReadingCandidate(2, "喫茶", "きっちゃ"),
        ReadingCandidate(3, "喫茶店", "きっさてん"),
    )

    @Test fun normalisesKatakanaSpacesAndSurroundingWhitespace() {
        assertEquals("きっさ", VocabularyReadings.normalize(" キッサ "))
        assertEquals("きっさてん", VocabularyReadings.normalize("きっさ てん"))
        assertEquals("", VocabularyReadings.normalize("   "))
    }

    @Test fun resolvesTheSingleEntryThatCarriesTheCorrectedReading() {
        assertEquals(
            ReadingCandidate(2, "喫茶", "きっちゃ"),
            VocabularyReadings.selectEntry(candidates, "喫茶", "キッチャ"),
        )
    }

    @Test fun aReadingWithNoMatchingEntryStaysUnresolved() {
        assertNull(VocabularyReadings.selectEntry(candidates, "喫茶", "きっさてん"))
        assertNull(VocabularyReadings.selectEntry(candidates, "喫茶", "かっさ"))
    }

    @Test fun anExpressionThatDoesNotMatchItselfStaysUnresolved() {
        assertNull(VocabularyReadings.selectEntry(candidates, "喫茶店", "きっちゃ"))
    }

    @Test fun twoEntriesSharingOneReadingAreNotSilentlyCollapsed() {
        val duplicated = candidates + ReadingCandidate(4, "喫茶", "きっちゃ")
        assertNull(VocabularyReadings.selectEntry(duplicated, "喫茶", "きっちゃ"))
    }

    @Test fun anAmbiguousLookupWithoutAChoiceIsUnresolved() {
        assertTrue(VocabularyResolution.isUnresolved(entryCount = 2, selectedEntryId = null))
        assertFalse(VocabularyResolution.isUnresolved(entryCount = 2, selectedEntryId = 2L))
        assertFalse(VocabularyResolution.isUnresolved(entryCount = 1, selectedEntryId = null))
        assertFalse(VocabularyResolution.isUnresolved(entryCount = 0, selectedEntryId = null))
    }

    @Test fun theUnresolvedViewCarriesNothingFromAPreviousWord() {
        val previous = VocabularyView(
            entryId = 9,
            vocabulary = "食べる",
            reading = "たべる",
            definition = "to eat",
            pitchAccent = "3",
            senseIndices = setOf(0),
        )
        val cleared = VocabularyResolution.unresolved()

        assertFalse(cleared.resolved)
        assertNull(cleared.entryId)
        assertEquals("", cleared.vocabulary)
        assertEquals("", cleared.reading)
        assertEquals("", cleared.definition)
        assertEquals("", cleared.pitchAccent)
        assertTrue(cleared.senseIndices.isEmpty())
        assertTrue(previous.resolved)
    }
}
