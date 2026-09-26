package dev.pierre.japaneseanki

data class ManualNoteDraft(
    val sentence: String,
    val target: TargetSpan?,
    val sentenceReading: String,
    val leaveSentenceTargetUnannotated: Boolean,
    val vocabulary: String,
    val vocabularyReading: String,
    val leaveVocabularyUnannotated: Boolean,
    val definition: String,
    val pitch: PitchAccent? = null,
    val dependentFieldsStale: Boolean = false,
    val translation: String = "",
    val sentenceAudioFilename: String? = null,
    val vocabularyAudioFilename: String? = null,
    val imageFilename: String? = null,
    val notes: String = "",
) {
    fun validationErrors(): List<String> = buildList {
        val targetText = target?.textIn(sentence)
        if (sentence.isBlank()) add("Enter a sentence.")
        if (targetText == null) add("Select a valid target in the sentence.")
        if (vocabulary.isBlank()) add("Enter vocabulary or a dictionary lemma.")
        if (definition.isBlank()) add("Enter a definition.")
        if (dependentFieldsStale) add("Review vocabulary, readings, and pitch after changing the sentence or target.")

        if (targetText != null && containsKanji(targetText) &&
            sentenceReading.isBlank() && !leaveSentenceTargetUnannotated
        ) add("Add the target reading or explicitly leave it unannotated.")
        if (containsKanji(vocabulary) && vocabularyReading.isBlank() &&
            !leaveVocabularyUnannotated
        ) add("Add the vocabulary reading or explicitly leave it unannotated.")
        if (sentenceReading.any { it == '[' || it == ']' }) add("Target reading contains reserved furigana syntax.")
        if (vocabularyReading.any { it == '[' || it == ']' }) add("Vocabulary reading contains reserved furigana syntax.")
        if (pitch != null && pitch.reading != vocabularyReading.trim()) {
            add("Pitch pattern is stale for the current vocabulary reading.")
        }
        if (sentenceAudioFilename != null && AnkiFieldSerializer.audioReference(sentenceAudioFilename) == null) {
            add("Sentence audio filename is not a safe media reference.")
        }
        if (vocabularyAudioFilename != null && AnkiFieldSerializer.audioReference(vocabularyAudioFilename) == null) {
            add("Vocabulary audio filename is not a safe media reference.")
        }
        if (imageFilename != null && AnkiFieldSerializer.imageReference(imageFilename) == null) {
            add("Image filename is not a safe media reference.")
        }
    }

    fun fieldsByName(): Map<String, String>? {
        if (validationErrors().isNotEmpty()) return null
        val selectedTarget = target ?: return null
        val sentKanji = AnkiFieldSerializer.highlightedSentence(sentence, selectedTarget) ?: return null
        val sentFurigana = AnkiFieldSerializer.highlightedFuriganaSentence(
            sentence,
            selectedTarget,
            sentenceReading,
        ) ?: return null
        val vocabFurigana = AnkiFieldSerializer.furiganaWord(vocabulary, vocabularyReading) ?: return null

        return mapOf(
            "SentKanji" to sentKanji,
            "SentFurigana" to sentFurigana,
            "SentEng" to AnkiFieldSerializer.plainText(translation),
            "SentAudio" to sentenceAudioFilename?.let { AnkiFieldSerializer.audioReference(it) }.orEmpty(),
            "VocabKanji" to AnkiFieldSerializer.plainText(vocabulary),
            "VocabFurigana" to vocabFurigana,
            "VocabDef" to AnkiFieldSerializer.definition(definition.split(Regex("\\r\\n|\\n|\\r"))),
            "VocabPitch" to pitch?.fieldMarkup.orEmpty(),
            "VocabAudio" to vocabularyAudioFilename?.let { AnkiFieldSerializer.audioReference(it) }.orEmpty(),
            "Image" to imageFilename?.let { AnkiFieldSerializer.imageReference(it) }.orEmpty(),
            "Notes" to AnkiFieldSerializer.plainText(notes),
            "fm_focus_words" to "",
            "fm_new_words" to "",
            "fm_seen_words" to "",
        )
    }

}

internal fun containsKanji(text: String): Boolean {
    var index = 0
    while (index < text.length) {
        val codePoint = text.codePointAt(index)
        if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN) return true
        index += Character.charCount(codePoint)
    }
    return false
}
