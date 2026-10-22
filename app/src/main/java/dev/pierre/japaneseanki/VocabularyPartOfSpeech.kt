package dev.pierre.japaneseanki

/**
 * Resolves available JMdict POS for explicitly selected senses of one dictionary entry.
 * JMdict's DTD permits POS tags to carry forward to later senses until replaced; an entry with no
 * earlier POS remains unknown rather than borrowing a tag from another entry or from MeCab.
 */
internal object VocabularyPartOfSpeech {
    fun forSelectedSenses(
        entry: DictionaryEntry?,
        selectedSenseIndices: Set<Int>,
    ): List<String> {
        if (entry == null || selectedSenseIndices.isEmpty()) return emptyList()
        val selected = selectedSenseIndices.filter { it >= 0 }.toSet()
        if (selected.isEmpty()) return emptyList()

        val result = linkedSetOf<String>()
        var inherited = emptyList<String>()
        entry.senses.forEachIndexed { index, sense ->
            val explicit = sense.partsOfSpeech.map(String::trim).filter(String::isNotEmpty)
            if (explicit.isNotEmpty()) inherited = explicit
            if (index in selected) inherited.forEach(result::add)
        }
        return result.toList()
    }
}
