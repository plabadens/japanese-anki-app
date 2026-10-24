package dev.pierre.japaneseanki

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
internal fun DraftReadingCorrectionCleanupEffect(
    sentence: String,
    corrections: List<ReadingCorrection>,
    onCorrectionsChanged: (List<ReadingCorrection>) -> Unit,
) {
    LaunchedEffect(sentence) {
        val stale = corrections.filter { it.span.textIn(sentence) != it.surface }
        if (stale.isNotEmpty()) {
            onCorrectionsChanged(corrections.filter { it.span.textIn(sentence) == it.surface })
        }
    }
}

@Composable
internal fun DraftTranslationEffect(
    sentence: String,
    requestRevision: Int,
    retry: Int,
    autoAllowed: Boolean,
    sendLocked: Boolean,
    generationStarted: Boolean,
    lastHandledRetry: Int,
    attemptedSentence: String?,
    currentDraft: () -> TranslationDraft,
    currentRequestRevision: androidx.compose.runtime.State<Int>,
    currentSubmissionLocked: androidx.compose.runtime.State<Boolean>,
    translationService: TranslationService,
    preferences: SharedPreferences,
    onDraftChanged: (TranslationDraft) -> Unit,
    onLastHandledRetryChanged: (Int) -> Unit,
    onAttemptedSentenceChanged: (String?) -> Unit,
    retryNeededMessage: String,
    notStartedMessage: String,
    requestRecordFailedMessage: String,
    unavailableMessage: String,
) {
    LaunchedEffect(sentence, requestRevision, retry, autoAllowed, sendLocked, generationStarted) {
        // Network work starts only after the deliberate Continue, never on typing or token taps.
        if (sentence.isBlank() || sendLocked || !generationStarted) return@LaunchedEffect

        var requestDraft = currentDraft()
        if (requestDraft.sentence != sentence) {
            requestDraft = requestDraft.sentenceChanged(sentence)
            onDraftChanged(requestDraft)
        }
        if (requestDraft.manuallyEdited && !requestDraft.stale) return@LaunchedEffect
        if (!requestDraft.manuallyEdited && !requestDraft.stale && requestDraft.result is TranslationResult.Ready) {
            return@LaunchedEffect
        }

        val explicitRetry = retry != lastHandledRetry
        if (attemptedSentence == sentence && !explicitRetry) {
            val active = if (requestDraft.manuallyEdited && requestDraft.stale) {
                requestDraft.suggestion ?: TranslationResult.Pending
            } else requestDraft.result
            if (active is TranslationResult.Pending) {
                onDraftChanged(requestDraft.requestFinished(TranslationResult.Failed(retryNeededMessage)))
            }
            return@LaunchedEffect
        }
        if (!autoAllowed && !explicitRetry) {
            onDraftChanged(requestDraft.requestFinished(TranslationResult.Failed(notStartedMessage)))
            return@LaunchedEffect
        }

        onLastHandledRetryChanged(retry)
        delay(350)
        if (!translationResponseIsCurrent(
                sentence, requestRevision, sentence,
                currentRequestRevision.value, currentSubmissionLocked.value,
            )
        ) return@LaunchedEffect

        requestDraft = currentDraft().requestStarted()
        onDraftChanged(requestDraft)
        onAttemptedSentenceChanged(sentence)
        val requestRecorded = preferences.edit()
            .putString("translation_attempted_sentence", sentence)
            .putBoolean("translation_pending", true)
            .commit()
        if (!requestRecorded) {
            onDraftChanged(currentDraft().requestFinished(TranslationResult.Failed(requestRecordFailedMessage)))
            return@LaunchedEffect
        }

        val result = try {
            withContext(Dispatchers.IO) { translationService.translate(sentence) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            TranslationResult.Failed(unavailableMessage)
        }
        if (translationResponseIsCurrent(
                sentence, requestRevision, sentence,
                currentRequestRevision.value, currentSubmissionLocked.value,
            )
        ) {
            onDraftChanged(currentDraft().requestFinished(result))
        }
    }
}

@Composable
internal fun DraftTokenizationEffect(
    analyzerReady: Boolean,
    sentence: String,
    analysisRetry: Int,
    draftGeneration: Long,
    sendLocked: Boolean,
    dictionaryReady: Boolean,
    offlineAnalyzer: OfflineJapaneseAnalyzer,
    offlineDictionary: OfflineDictionary,
    isDraftCurrent: (Long) -> Boolean,
    onTokensChanged: (List<JapaneseToken>) -> Unit,
    onLoadingChanged: (Boolean) -> Unit,
    onError: (String) -> Unit,
    errorMessage: String,
) {
    LaunchedEffect(analyzerReady, sentence, analysisRetry, draftGeneration, sendLocked, dictionaryReady) {
        if (sendLocked) return@LaunchedEffect
        val callbackGeneration = draftGeneration
        if (!analyzerReady || sentence.isBlank()) {
            onTokensChanged(emptyList())
            onLoadingChanged(false)
            return@LaunchedEffect
        }
        onLoadingChanged(true)
        onError("")
        try {
            delay(150)
            val analyzed = withContext(Dispatchers.IO) {
                val raw = offlineAnalyzer.analyze(sentence)
                groupTokens(raw) { candidate ->
                    dictionaryReady && runCatching {
                        // One bounded existence probe; readiness and exception policy match lookup.
                        offlineDictionary.hasEntry(candidate)
                    }.getOrDefault(false)
                }
            }
            if (!isDraftCurrent(callbackGeneration)) return@LaunchedEffect
            onTokensChanged(analyzed)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (isDraftCurrent(callbackGeneration)) {
                onTokensChanged(emptyList())
                onError(errorMessage)
            }
        } finally {
            onLoadingChanged(false)
        }
    }
}

@Composable
internal fun DraftTargetVocabularyEffect(
    target: TargetSpan?,
    sentence: String,
    tokens: List<JapaneseToken>,
    vocabulary: String,
    vocabularyReading: String,
    vocabularyReadingError: String?,
    dependentFieldsStale: Boolean,
    sendLocked: Boolean,
    onVocabularyChanged: (String) -> Unit,
    onVocabularyReadingChanged: (String) -> Unit,
) {
    LaunchedEffect(
        target, sentence, tokens, vocabulary, vocabularyReading,
        vocabularyReadingError, dependentFieldsStale,
    ) {
        if (sendLocked) return@LaunchedEffect
        val targetSpan = target ?: return@LaunchedEffect
        val targetText = targetSpan.textIn(sentence) ?: return@LaunchedEffect
        val resolved = resolveAnalyzedLemma(tokens, targetSpan) ?: return@LaunchedEffect
        val shouldResolveSurface = vocabulary.isBlank() || (!dependentFieldsStale && vocabulary == targetText)
        if (shouldResolveSurface) onVocabularyChanged(resolved.expression)
        // A rejected correction remains unresolved; do not overwrite the user's attempt.
        if (vocabularyReadingError == null &&
            (shouldResolveSurface || vocabulary == resolved.expression) && vocabularyReading.isBlank() &&
            containsKanji(resolved.expression)
        ) onVocabularyReadingChanged(resolved.reading)
    }
}

@Composable
internal fun DraftSentenceReadingEffect(
    target: TargetSpan?,
    sentence: String,
    tokens: List<JapaneseToken>,
    sentenceReading: String,
    sendLocked: Boolean,
    onSentenceReadingChanged: (String) -> Unit,
) {
    LaunchedEffect(target, sentence, tokens, sentenceReading) {
        if (sendLocked) return@LaunchedEffect
        val targetSpan = target ?: return@LaunchedEffect
        val targetText = targetSpan.textIn(sentence) ?: return@LaunchedEffect
        if (sentenceReading.isNotBlank() || !containsKanji(targetText)) return@LaunchedEffect
        val resolved = resolveSurfaceReading(sentence, tokens, targetSpan)?.takeIf(String::isNotBlank) ?: return@LaunchedEffect
        onSentenceReadingChanged(resolved)
    }
}

@Composable
internal fun DraftDictionarySearchEffect(
    dictionaryReady: Boolean,
    target: TargetSpan?,
    sentence: String,
    dictionaryRetry: Int,
    tokens: List<JapaneseToken>,
    draftGeneration: Long,
    sendLocked: Boolean,
    analyzedExpression: String?,
    selectedEntryId: Long?,
    vocabulary: String,
    vocabularyReading: String,
    preferences: SharedPreferences,
    offlineDictionary: OfflineDictionary,
    isDraftCurrent: (Long) -> Boolean,
    onEntriesChanged: (List<DictionaryEntry>) -> Unit,
    onHasMoreChanged: (Boolean) -> Unit,
    onSelectedEntryChanged: (DictionaryEntry?) -> Unit,
    onLoadingChanged: (Boolean) -> Unit,
    onError: (String) -> Unit,
    clearUnresolvedVocabulary: () -> Unit,
    commitChoice: (DictionaryMeaningChoice) -> Unit,
    onSheetChanged: (Boolean) -> Unit,
    lookupFailedMessage: String,
) {
    LaunchedEffect(dictionaryReady, target, sentence, dictionaryRetry, tokens, draftGeneration, sendLocked) {
        if (sendLocked) return@LaunchedEffect
        val callbackGeneration = draftGeneration
        val targetText = target?.textIn(sentence)
        if (!dictionaryReady || targetText == null) {
            onEntriesChanged(emptyList())
            onHasMoreChanged(false)
            onSelectedEntryChanged(null)
        } else {
            onLoadingChanged(true)
            try {
                val entries = withContext(Dispatchers.IO) {
                    offlineDictionary.search(targetText, analyzedExpression)
                }
                if (!isDraftCurrent(callbackGeneration)) return@LaunchedEffect
                onEntriesChanged(entries)
                onError("")
                onHasMoreChanged(entries.size == 50)
                val choices = DictionaryMeaningChoices.from(entries)
                val restoredEntry = entries.firstOrNull {
                    it.id == selectedEntryId && it.expression == vocabulary && it.reading == vocabularyReading
                }
                val restoredSenseIndex = restoredEntry?.let { entry ->
                    preferences.getStringSet("dictionary_sense_indices", null)
                        ?.mapNotNull(String::toIntOrNull)
                        ?.filter { it in entry.senses.indices }
                        ?.minOrNull() ?: 0
                }
                val restoredChoice = DictionaryMeaningChoices.find(
                    choices = choices,
                    entryId = restoredEntry?.id,
                    expression = vocabulary,
                    reading = vocabularyReading,
                    senseIndex = restoredSenseIndex,
                )
                when {
                    restoredChoice != null -> commitChoice(restoredChoice)
                    choices.size == 1 -> commitChoice(choices.single())
                    choices.size > 1 -> {
                        // Do not guess between viable readings/meanings.
                        clearUnresolvedVocabulary()
                        onSheetChanged(true)
                    }
                    else -> {
                        // No usable meaning is a source miss, not a chooser or a pagination prompt.
                        clearUnresolvedVocabulary()
                        onSheetChanged(false)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (isDraftCurrent(callbackGeneration)) onError(lookupFailedMessage)
            } finally {
                onLoadingChanged(false)
            }
        }
    }
}

@Composable
internal fun DraftPitchLookupEffect(
    dictionaryReady: Boolean,
    selectedEntryId: Long?,
    selectedEntry: DictionaryEntry?,
    vocabulary: String,
    vocabularyReading: String,
    target: TargetSpan?,
    sentence: String,
    dictionaryRetry: Int,
    draftGeneration: Long,
    sendLocked: Boolean,
    availability: AjtPitchAvailability,
    repository: AjtPitchRepository,
    isDraftCurrent: (Long) -> Boolean,
    onCandidatesChanged: (List<AjtPitchRecord>) -> Unit,
    onPitchAccentChanged: (String) -> Unit,
    onPitchHtmlChanged: (String?) -> Unit,
    onLoadingChanged: (Boolean) -> Unit,
    onError: (String?) -> Unit,
    unavailableMessage: (AjtPitchFailure) -> String,
    missingMessage: String,
) {
    LaunchedEffect(
        dictionaryReady, selectedEntryId, selectedEntry, vocabulary, vocabularyReading,
        target, sentence, dictionaryRetry, draftGeneration, sendLocked, availability,
    ) {
        if (sendLocked) return@LaunchedEffect
        val callbackGeneration = draftGeneration
        val entry = selectedEntry?.takeIf {
            it.id == selectedEntryId && it.expression == vocabulary && it.reading == vocabularyReading
        }
        if (!dictionaryReady || entry == null || vocabulary.isBlank() || vocabularyReading.isBlank() ||
            availability !is AjtPitchAvailability.Ready
        ) {
            onCandidatesChanged(emptyList())
            onPitchAccentChanged("")
            onPitchHtmlChanged(null)
            onLoadingChanged(false)
            onError((availability as? AjtPitchAvailability.Unavailable)?.let { unavailableMessage(it.failure) })
            return@LaunchedEffect
        }
        onPitchAccentChanged("")
        onPitchHtmlChanged(null)
        onCandidatesChanged(emptyList())
        onLoadingChanged(true)
        try {
            val candidates = withContext(Dispatchers.IO) {
                repository.pitch(entry.expression, entry.reading)
            }.filter { PitchAccent.create(entry.reading, it.accent, it.html) != null }
            if (!isDraftCurrent(callbackGeneration)) return@LaunchedEffect
            onCandidatesChanged(candidates)
            onError(if (candidates.isEmpty()) missingMessage else null)
            candidates.firstOrNull()?.let { first ->
                onPitchAccentChanged(first.accent.toString())
                onPitchHtmlChanged(first.html)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (isDraftCurrent(callbackGeneration)) onCandidatesChanged(emptyList())
        } finally {
            onLoadingChanged(false)
        }
    }
}
