package dev.pierre.japaneseanki.ui

import dev.pierre.japaneseanki.FuriganaSpan
import dev.pierre.japaneseanki.TargetSpan

internal data class ReviewSentencePart(
    val text: String,
    val reading: String? = null,
    val highlighted: Boolean = false,
    val unresolvedKanji: Boolean = false,
)

internal fun reviewSentenceParts(
    sentence: String,
    target: TargetSpan,
    targetReading: String,
    furigana: List<FuriganaSpan>,
): List<ReviewSentencePart> {
    if (target.textIn(sentence) == null) return listOf(ReviewSentencePart(sentence))
    val readings = furigana.filter { it.span.textIn(sentence) == it.surface && it.reading.isNotBlank() }
        .associateBy { it.span.start }
    val parts = mutableListOf<ReviewSentencePart>()
    var index = 0
    while (index < sentence.length) {
        val annotation = readings[index]?.takeIf { it.span.end <= sentence.length }
        if (annotation != null) {
            val isTarget = annotation.span.start == target.start && annotation.span.end == target.end
            parts += ReviewSentencePart(
                annotation.surface,
                if (isTarget && targetReading.isNotBlank()) targetReading else annotation.reading,
                highlighted = isTarget,
            )
            index = annotation.span.end
            continue
        }
        if (index == target.start && targetReading.isNotBlank()) {
            parts += ReviewSentencePart(target.textIn(sentence).orEmpty(), targetReading, highlighted = true)
            index = target.end
            continue
        }
        val codePoint = sentence.codePointAt(index)
        val count = Character.charCount(codePoint)
        val text = sentence.substring(index, index + count)
        val unresolved = Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN &&
            !(index >= target.start && index < target.end && targetReading.isNotBlank())
        val highlighted = index >= target.start && index < target.end
        val previous = parts.lastOrNull()
        if (previous != null && previous.reading == null && previous.highlighted == highlighted &&
            previous.unresolvedKanji == unresolved
        ) {
            parts[parts.lastIndex] = previous.copy(text = previous.text + text)
        } else {
            parts += ReviewSentencePart(text, highlighted = highlighted, unresolvedKanji = unresolved)
        }
        index += count
    }
    return parts
}
