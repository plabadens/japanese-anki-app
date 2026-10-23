package dev.pierre.japaneseanki.ui

import dev.pierre.japaneseanki.JapaneseToken
import dev.pierre.japaneseanki.TargetSpan
import dev.pierre.japaneseanki.isPunctuationToken

/** Which boundary of a selected span to move. */
internal enum class SpanEdge { Left, Right }

/**
 * A visible target unit. [token] carries the selectable span. [prefix]/[suffix] hold adjacent
 * punctuation so `。` is displayed inline but never becomes part of the selectable pill or wraps
 * onto a line by itself.
 */
internal data class TargetPill(
    val token: JapaneseToken,
    val prefix: String = "",
    val suffix: String = "",
) {
    val label: String get() = prefix + token.surface + suffix
}

/** Builds inline target units from analyzer tokens without making punctuation selectable. */
internal fun targetPills(tokens: List<JapaneseToken>): List<TargetPill> {
    val pills = mutableListOf<TargetPill>()
    var leadingPunctuation = ""
    tokens.forEach { token ->
        if (token.surface.isBlank()) {
            if (pills.isEmpty()) {
                leadingPunctuation += token.surface
            } else {
                val previous = pills.removeAt(pills.lastIndex)
                pills += previous.copy(suffix = previous.suffix + token.surface)
            }
        } else if (token.isPunctuationToken()) {
            if (pills.isEmpty() || token.partOfSpeech.contains("括弧開")) {
                leadingPunctuation += token.surface
            } else {
                val previous = pills.removeAt(pills.lastIndex)
                pills += previous.copy(suffix = previous.suffix + token.surface)
            }
        } else {
            pills += TargetPill(token, prefix = leadingPunctuation)
            leadingPunctuation = ""
        }
    }
    if (leadingPunctuation.isNotEmpty() && pills.isNotEmpty()) {
        val previous = pills.removeAt(pills.lastIndex)
        pills += previous.copy(suffix = previous.suffix + leadingPunctuation)
    }
    return pills
}

/** Surface text covered by [span], or null when it does not align with the tokens. */
internal fun spanSurface(tokens: List<JapaneseToken>, span: TargetSpan): String? {
    val covered = tokens.filter { it.span.start >= span.start && it.span.end <= span.end }
    if (covered.isEmpty() || covered.first().span.start != span.start ||
        covered.last().span.end != span.end
    ) return null
    return covered.joinToString("") { it.surface }.takeUnless(String::isBlank)
}

/**
 * Grows [span] by one neighbouring word on [edge]. Blank and punctuation tokens are skipped so an
 * extension never produces an empty or punctuation-only selection; null means the selection
 * already reaches the last selectable word.
 */
internal fun extendSpan(tokens: List<JapaneseToken>, span: TargetSpan, edge: SpanEdge): TargetSpan? =
    when (edge) {
        SpanEdge.Left -> tokens
            .filter { it.span.end <= span.start && it.surface.isNotBlank() && !it.isPunctuationToken() }
            .maxByOrNull { it.span.end }
            ?.let { TargetSpan(it.span.start, span.end) }

        SpanEdge.Right -> tokens
            .filter { it.span.start >= span.end && it.surface.isNotBlank() && !it.isPunctuationToken() }
            .minByOrNull { it.span.start }
            ?.let { TargetSpan(span.start, it.span.end) }
    }

/** Drops the outermost token on [edge]; null when the span is already a single token. */
internal fun trimSpan(tokens: List<JapaneseToken>, span: TargetSpan, edge: SpanEdge): TargetSpan? =
    when (edge) {
        SpanEdge.Left -> tokens
            .filter { it.span.start == span.start && it.span.end < span.end }
            .minByOrNull { it.span.end }
            ?.let { TargetSpan(it.span.end, span.end) }

        SpanEdge.Right -> tokens
            .filter { it.span.end == span.end && it.span.start > span.start }
            .maxByOrNull { it.span.start }
            ?.let { TargetSpan(span.start, it.span.start) }
    }
