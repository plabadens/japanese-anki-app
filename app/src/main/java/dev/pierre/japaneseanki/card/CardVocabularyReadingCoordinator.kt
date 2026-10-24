package dev.pierre.japaneseanki.card

import dev.pierre.japaneseanki.DictionaryEntry
import dev.pierre.japaneseanki.VocabularyReadings

internal data class CardVocabularySnapshot(
    val locked: Boolean,
    val entries: List<DictionaryEntry>,
    val vocabulary: String,
    val vocabularyReading: String,
    val selectedEntry: DictionaryEntry?,
)

internal data class CardVocabularyErrorCopy(
    val kanaRequired: String,
    val noDictionaryEntry: String,
    val noPitchPattern: String,
    val readingNotListed: (word: String, reading: String) -> String,
    val thisWord: String,
)

internal data class CardVocabularyEffects(
    val clearAudio: () -> Unit,
    val clearPitch: () -> Unit,
    val invalidateCard: () -> Unit,
    val setVocabularyReading: (String) -> Unit,
    val setVocabularyReadingError: (String?) -> Unit,
    val setSelectedEntry: (DictionaryEntry?) -> Unit,
    val setSelectedEntryId: (Long?) -> Unit,
    val setSelectedSenses: (Set<Int>) -> Unit,
    val setPitchError: (String?) -> Unit,
    val setDefinition: (String) -> Unit,
    val setDefinitionManuallyEdited: (Boolean) -> Unit,
    val setDefinitionProvenanceKnown: (Boolean) -> Unit,
)

/** Pure dictionary resolution with state mutations supplied by the activity as narrow effects. */
internal class CardVocabularyReadingCoordinator(
    private val snapshot: () -> CardVocabularySnapshot,
    private val effects: CardVocabularyEffects,
    private val errorCopy: CardVocabularyErrorCopy,
) {
    fun apply(raw: String): Boolean {
        val current = snapshot()
        if (current.locked) return false
        val corrected = VocabularyReadings.normalize(raw)
        val match = VocabularyReadings.selectEntry(
            current.entries.map { dev.pierre.japaneseanki.ReadingCandidate(it.id, it.expression, it.reading) },
            current.vocabulary,
            corrected,
        )
        if (match == null) {
            effects.clearAudio()
            effects.clearPitch()
            effects.setVocabularyReading("")
            effects.setVocabularyReadingError(
                when {
                    corrected.isEmpty() -> errorCopy.kanaRequired
                    current.entries.isEmpty() -> errorCopy.noDictionaryEntry
                    else -> errorCopy.readingNotListed(current.vocabulary.ifBlank { errorCopy.thisWord }, corrected)
                },
            )
            effects.setSelectedEntry(null)
            effects.setSelectedEntryId(null)
            effects.setSelectedSenses(emptySet())
            effects.setPitchError(errorCopy.noPitchPattern)
            return false
        }
        val entry = current.entries.first { it.id == match.id }
        effects.setVocabularyReadingError(null)
        if (current.vocabularyReading == entry.reading && current.selectedEntry?.id == entry.id) return true

        effects.invalidateCard()
        effects.clearAudio()
        effects.clearPitch()
        effects.setVocabularyReading(entry.reading)
        if (current.selectedEntry?.id != entry.id) {
            effects.setSelectedEntry(entry)
            effects.setSelectedEntryId(entry.id)
            val senses = if (entry.senses.isEmpty()) emptySet() else setOf(0)
            effects.setSelectedSenses(senses)
            effects.setDefinition(
                senses.sorted().mapNotNull { index ->
                    entry.senses.getOrNull(index)?.glosses?.joinToString("; ")
                }.joinToString("\n"),
            )
            effects.setDefinitionManuallyEdited(false)
            effects.setDefinitionProvenanceKnown(true)
        }
        return true
    }
}
