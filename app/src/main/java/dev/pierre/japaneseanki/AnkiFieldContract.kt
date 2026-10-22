package dev.pierre.japaneseanki

/** Required names are mapped by name; model order and extra fields are user-owned. */
object AnkiFieldContract {
    /** Preserve provider field positions, including empty trailing names, when validating its schema. */
    fun parseModelFieldNames(raw: String?): List<String> =
        raw?.takeIf(String::isNotEmpty)
            ?.split('\u001f', ignoreCase = false, limit = Int.MAX_VALUE)
            .orEmpty()

    fun parseTags(raw: String): Set<String> =
        raw.trim().split(Regex("\\s+")).filter(String::isNotBlank).toSet()

    val requiredFields = listOf(
        "SentKanji",
        "SentFurigana",
        "SentEng",
        "SentAudio",
        "VocabKanji",
        "VocabFurigana",
        "VocabDef",
        "VocabPitch",
        "VocabAudio",
        "Image",
        "Notes",
        "fm_focus_words",
        "fm_new_words",
        "fm_seen_words",
    )

    val optionalFields = listOf("VocabPartOfSpeech")

    fun mapToModelOrder(
        modelFields: List<String>,
        valuesByName: Map<String, String>,
    ): FieldMapping {
        val duplicates = modelFields.groupingBy { it }.eachCount()
            .filterValues { it > 1 }
            .keys
        val missing = requiredFields.filterNot(modelFields::contains)
        val errors = buildList {
            if (missing.isNotEmpty()) add("Missing required fields: ${missing.joinToString()}")
            if (duplicates.isNotEmpty()) add("Ambiguous duplicate fields: ${duplicates.joinToString()}")
        }
        if (errors.isNotEmpty()) return FieldMapping(emptyList(), errors)

        return FieldMapping(
            modelFields.map { field ->
                if (field in requiredFields || field in optionalFields) valuesByName[field].orEmpty() else ""
            },
            emptyList(),
        )
    }
}

data class FieldMapping(val values: List<String>, val errors: List<String>) {
    val isValid: Boolean get() = errors.isEmpty()
}

class DuplicateKey private constructor(val sentence: String, val lemma: String) {
    fun matches(other: DuplicateKey): Boolean =
        sentence == other.sentence && lemma == other.lemma

    companion object {
        /** Select the duplicate fields using the selected model's actual field order. */
        fun fromModelFields(modelFields: List<String>, serializedNoteFields: String): DuplicateKey {
            val sentenceIndex = modelFields.indexOf("SentKanji")
            val lemmaIndex = modelFields.indexOf("VocabKanji")
            require(sentenceIndex >= 0 && lemmaIndex >= 0) {
                "Selected note type is missing required duplicate fields."
            }
            val values = serializedNoteFields.split('\u001f', ignoreCase = false, limit = Int.MAX_VALUE)
            require(sentenceIndex < values.size && lemmaIndex < values.size) {
                "AnkiDroid returned a note with an invalid field list."
            }
            return fromFields(values[sentenceIndex], values[lemmaIndex])
        }

        /** Remove only app-generated highlight markup and HTML escaping. */
        fun fromFields(serializedSentence: String, vocabularyLemma: String): DuplicateKey =
            DuplicateKey(
                sentence = canonicalField(serializedSentence),
                lemma = canonicalField(vocabularyLemma),
            )

        private fun canonicalField(serialized: String): String =
            HtmlEntityDecoder.decode(
                serialized.replace(Regex("(?i)</?(?:b|strong)\\b[^>]*>"), ""),
                NumericEntityPolicy.DUPLICATE_CANONICALIZATION,
            ).trim()
    }
}
