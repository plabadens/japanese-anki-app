package dev.pierre.japaneseanki

/** One user-visible interpretation: a JMdict form paired with exactly one viable sense. */
internal data class DictionaryMeaningChoice(
    val entry: DictionaryEntry,
    val senseIndex: Int,
    val definition: String,
)

/**
 * Builds the choices used by the card and its single meaning sheet.
 *
 * The dictionary has already applied JMdict spelling/reading restrictions. Rows without a usable
 * spelling, reading or gloss cannot be selected. Identical visible interpretations are collapsed;
 * their internal entry ids are not useful distinctions to ask a person to make.
 */
internal object DictionaryMeaningChoices {
    fun from(entries: List<DictionaryEntry>): List<DictionaryMeaningChoice> =
        entries.flatMap { entry ->
            entry.senses.mapIndexedNotNull { index, sense ->
                val definition = sense.glosses.map(String::trim).filter(String::isNotEmpty).joinToString("; ")
                if (entry.expression.isBlank() || entry.reading.isBlank() || definition.isBlank()) {
                    null
                } else {
                    DictionaryMeaningChoice(
                        entry = entry,
                        senseIndex = index,
                        definition = definition,
                    )
                }
            }
        }.distinctBy { choice ->
            listOf(
                choice.entry.expression,
                choice.entry.reading,
                choice.definition,
            )
        }

    fun find(
        choices: List<DictionaryMeaningChoice>,
        entryId: Long?,
        expression: String,
        reading: String,
        senseIndex: Int?,
    ): DictionaryMeaningChoice? {
        if (entryId == null || senseIndex == null) return null
        return choices.firstOrNull { choice ->
            choice.entry.id == entryId && choice.entry.expression == expression &&
                choice.entry.reading == reading && choice.senseIndex == senseIndex
        }
    }

    fun hasAlternative(
        choices: List<DictionaryMeaningChoice>,
        selected: DictionaryMeaningChoice?,
    ): Boolean {
        if (selected == null) return choices.size > 1
        return choices.any { choice ->
            choice.entry.expression != selected.entry.expression ||
                choice.entry.reading != selected.entry.reading || choice.definition != selected.definition
        }
    }
}
