package dev.pierre.japaneseanki


internal data class RestoredTranslationDraft(
    val draft: TranslationDraft,
    val attemptedSentence: String?,
)

internal fun restoreTranslationDraft(preferences: PreferenceValues): RestoredTranslationDraft {
    val text = preferences.getString("translation", "").orEmpty()
    val source = preferences.getString("translation_source", null)
        ?.let { saved -> TranslationSource.entries.firstOrNull { it.name == saved } }
    val wasPending = preferences.getBoolean("translation_pending", false)
    val translationError = preferences.getString("translation_error", null)
    val suggestionError = preferences.getString("translation_suggestion_error", null)
    val manuallyEdited = preferences.getBoolean(
        "translation_manual",
        source == TranslationSource.MANUAL || (text.isNotBlank() && source == null),
    )
    val stale = preferences.getBoolean("translation_stale", false)
    val suggestionText = preferences.getString("translation_suggestion", null)
    val suggestionSource = preferences.getString("translation_suggestion_source", null)
        ?.let { saved -> TranslationSource.entries.firstOrNull { it.name == saved } }
    val committedSentence = preferences.getString("committed_sentence", null)
    val committedTranslationWasNotAttempted = !manuallyEdited && text.isBlank() &&
        !committedSentence.isNullOrBlank() &&
        committedSentence == preferences.getString("sentence", "").orEmpty() &&
        preferences.getString("translation_attempted_sentence", null) != committedSentence
    fun savedFailure(key: String, message: String): TranslationResult.Failed = TranslationResult.Failed(
        message,
        TranslatorFailure.entries.firstOrNull { it.name == preferences.getString(key, null) }
            ?: TranslatorFailure.UNKNOWN,
    )
    val interrupted = TranslationResult.Failed("Translation was interrupted. Tap Retry to continue.", TranslatorFailure.INTERRUPTED)
    return RestoredTranslationDraft(
        draft = TranslationDraft(
            sentence = preferences.getString(
                "translation_sentence",
                preferences.getString("sentence", "").orEmpty(),
            ).orEmpty(),
            result = when {
                manuallyEdited -> TranslationResult.Ready(text, TranslationSource.MANUAL)
                source != null && text.isNotBlank() -> TranslationResult.Ready(text, source)
                text.isNotBlank() -> TranslationResult.Ready(text, TranslationSource.MANUAL)
                wasPending -> interrupted
                translationError != null -> savedFailure("translation_failure", translationError)
                committedTranslationWasNotAttempted -> interrupted
                else -> TranslationResult.Pending
            },
            manuallyEdited = manuallyEdited,
            stale = stale,
            suggestion = when {
                suggestionText != null && suggestionSource != null ->
                    TranslationResult.Ready(suggestionText, suggestionSource)
                wasPending && manuallyEdited && stale -> interrupted
                suggestionError != null -> savedFailure("translation_suggestion_failure", suggestionError)
                else -> null
            },
        ),
        attemptedSentence = preferences.getString("translation_attempted_sentence", null),
    )
}

internal fun restoreDraftModel(
    preferences: PreferenceValues,
    sentence: String,
    defaultTags: String,
): DraftModel = DraftModel(
    sentence = sentence,
    target = TargetSpan(
        preferences.getInt("target_start", -1),
        preferences.getInt("target_end", -1),
    ).takeIf { it.textIn(sentence) != null },
    vocabulary = preferences.getString("vocabulary", "").orEmpty(),
    vocabularyReading = preferences.getString("vocabulary_reading", "").orEmpty(),
    definition = preferences.getString("definition", "").orEmpty(),
    pitchAccent = preferences.getString("pitch_accent", "").orEmpty(),
    sentenceReading = preferences.getString("sentence_reading", "").orEmpty(),
    readingCorrections = SentenceReadings.decode(
        preferences.getString("sentence_reading_corrections", null),
        preferences.getString("sentence", "").orEmpty(),
    ),
    notes = preferences.getString("notes", "").orEmpty(),
    tags = seedFreshNoteTags(preferences.getString("tags", null), defaultTags),
)

internal fun persistDraftPreferences(
    storage: AppPreferenceStorage,
    selectionStart: Int,
    selectionEnd: Int,
    draft: DraftModel,
    pitchHtmlText: String?,
    definitionManuallyEdited: Boolean,
    dependentFieldsStale: Boolean,
    translation: String,
    translationDraft: TranslationDraft,
    activeTranslationResult: TranslationResult,
    translationAttemptedSentence: String?,
    cardInvalidated: Boolean,
    selectedModelId: Long?,
    selectedDeckId: Long?,
    selectedDictionaryEntryId: Long?,
    selectedSenseIndices: Set<Int>,
) {
    storage.update(PreferenceFile.DRAFT, PreferenceChanges()
        .putString("sentence", draft.sentence)
        .putInt("selection_start", selectionStart)
        .putInt("selection_end", selectionEnd)
        .putInt("target_start", draft.target?.start ?: -1)
        .putInt("target_end", draft.target?.end ?: -1)
        .putString("vocabulary", draft.vocabulary)
        .putString("vocabulary_reading", draft.vocabularyReading)
        .putString("pitch_accent", draft.pitchAccent)
        .putString("pitch_html", pitchHtmlText)
        .putString("definition", draft.definition)
        .putBoolean("definition_manual", definitionManuallyEdited)
        .putBoolean("definition_manual_stale", dependentFieldsStale && definitionManuallyEdited)
        .remove("dependent_fields_stale")
        .putString("sentence_reading", draft.sentenceReading)
        .putString("sentence_reading_corrections", SentenceReadings.encode(draft.readingCorrections))
        .remove("leave_sentence_target_unannotated")
        .remove("leave_vocabulary_unannotated")
        .putString("translation", translation)
        .putString("translation_sentence", translationDraft.sentence)
        .putString("translation_source", (translationDraft.result as? TranslationResult.Ready)?.source?.name)
        .putBoolean("translation_manual", translationDraft.manuallyEdited)
        .putBoolean("translation_stale", translationDraft.stale)
        .putBoolean("translation_pending", activeTranslationResult is TranslationResult.Pending)
        .putString("translation_error", (activeTranslationResult as? TranslationResult.Failed)?.message)
        .putString("translation_failure", (activeTranslationResult as? TranslationResult.Failed)?.failure?.name)
        .putString("translation_suggestion", (translationDraft.suggestion as? TranslationResult.Ready)?.text)
        .putString(
            "translation_suggestion_source",
            (translationDraft.suggestion as? TranslationResult.Ready)?.source?.name,
        )
        .putString("translation_suggestion_error", (translationDraft.suggestion as? TranslationResult.Failed)?.message)
        .putString("translation_suggestion_failure", (translationDraft.suggestion as? TranslationResult.Failed)?.failure?.name)
        .putString("translation_attempted_sentence", translationAttemptedSentence)
        .putBoolean("card_invalidated", cardInvalidated)
        .putString("notes", draft.notes)
        .putString("tags", draft.tags)
        .putLong("model_id", selectedModelId ?: -1)
        .putLong("deck_id", selectedDeckId ?: -1)
        .putLong("dictionary_entry_id", selectedDictionaryEntryId ?: -1)
        .putStringSet("dictionary_sense_indices", selectedSenseIndices.mapTo(mutableSetOf()) { it.toString() })
    )
}
