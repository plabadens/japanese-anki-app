package dev.pierre.japaneseanki

/** One dictionary entry as far as reading resolution is concerned. */
data class ReadingCandidate(val id: Long, val expression: String, val reading: String)

/**
 * Vocabulary-reading correction (SPEC §4.5, §5.3). A corrected reading is only accepted when it
 * names exactly one of the entries that were looked up for this word; nothing is invented.
 */
internal object VocabularyReadings {
    fun normalize(reading: String): String = reading
        .filterNot { it == ' ' || it == '\u3000' }
        .let(::katakanaToHiragana)

    fun selectEntry(candidates: List<ReadingCandidate>, expression: String, reading: String): ReadingCandidate? {
        val wanted = normalize(reading)
        if (wanted.isEmpty()) return null
        val expressionText = expression.trim()
        if (expressionText.isEmpty()) return null
        return candidates
            .filter { it.expression == expressionText && normalize(it.reading) == wanted }
            .singleOrNull()
    }
}

/**
 * The vocabulary fields shown for the current target. When a lookup is unresolved none of these
 * may come from a previous word (ACCEPTANCE v0.3 known defect 1).
 */
data class VocabularyView(
    val entryId: Long? = null,
    val vocabulary: String = "",
    val reading: String = "",
    val definition: String = "",
    val pitchAccent: String = "",
    val senseIndices: Set<Int> = emptySet(),
) {
    val resolved: Boolean get() = entryId != null
}

internal object VocabularyResolution {
    /** Several entries were found and none is chosen: the display must stay unresolved. */
    fun isUnresolved(entryCount: Int, selectedEntryId: Long?): Boolean =
        entryCount > 1 && selectedEntryId == null

    fun unresolved(): VocabularyView = VocabularyView()
}
