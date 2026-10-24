package dev.pierre.japaneseanki.card

import dev.pierre.japaneseanki.AnalyzedLemma
import dev.pierre.japaneseanki.JapaneseToken
import dev.pierre.japaneseanki.TargetSpan

internal data class CardTargetSelectionSnapshot(
    val target: TargetSpan?,
    val sentence: String,
    val tokens: List<JapaneseToken>,
    val vocabulary: String,
    val vocabularyReading: String,
    val locked: Boolean,
    val hasManualDefinition: Boolean,
)

/** Applies one target-selection transition and delegates only the surrounding side effects. */
internal class CardTargetSelectionCoordinator(
    private val snapshot: () -> CardTargetSelectionSnapshot,
    private val invalidateCard: () -> Unit,
    private val markDefinitionStale: () -> Unit,
    private val resetDictionarySelection: () -> Unit,
    private val setTarget: (TargetSpan?) -> Unit,
    private val setSentenceReading: (String) -> Unit,
    private val authorizeWord: (String, TargetSpan) -> Unit,
    private val setVocabulary: (String) -> Unit,
    private val setVocabularyReading: (String) -> Unit,
    private val resolveSurfaceReading: (String, List<JapaneseToken>, TargetSpan) -> String?,
    private val resolveAnalyzedLemma: (List<JapaneseToken>, TargetSpan) -> AnalyzedLemma?,
    private val containsKanji: (String) -> Boolean,
) {
    fun select(selected: TargetSpan?) {
        val current = snapshot()
        if (current.locked) return
        val changed = selected != current.target
        if (changed) {
            invalidateCard()
            if (current.hasManualDefinition) markDefinitionStale()
            resetDictionarySelection()
        }
        setTarget(selected)
        if (changed) {
            setSentenceReading(selected?.let {
                resolveSurfaceReading(current.sentence, current.tokens, it)
            }.orEmpty())
        }
        if (selected != null) {
            val surface = selected.textIn(current.sentence).orEmpty()
            authorizeWord("${selected.start}:${selected.end}:$surface", selected)
            if (current.vocabulary.isBlank()) {
                val lemma = resolveAnalyzedLemma(current.tokens, selected)
                setVocabulary(lemma?.expression ?: surface)
                if (lemma != null && current.vocabularyReading.isBlank() && containsKanji(lemma.expression)) {
                    setVocabularyReading(lemma.reading)
                }
            }
        }
    }
}
