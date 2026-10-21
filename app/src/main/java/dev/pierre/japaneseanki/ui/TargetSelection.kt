package dev.pierre.japaneseanki.ui

import dev.pierre.japaneseanki.JapaneseToken
import dev.pierre.japaneseanki.TargetSpan

/**
 * Which boundary of a selected span to move. The study's Target view lets the user drag across
 * word pills; native pills use these two edges plus explicit Extend/Trim controls so the same
 * contiguous selection is reachable with touch and with TalkBack (SPEC.md §4.3.3).
 */
internal enum class SpanEdge { Left, Right }

/** Surface text covered by [span], or null when it does not align with the tokens. */
internal fun spanSurface(tokens: List<JapaneseToken>, span: TargetSpan): String? {
    val covered = tokens.filter { it.span.start >= span.start && it.span.end <= span.end }
    if (covered.isEmpty() || covered.first().span.start != span.start ||
        covered.last().span.end != span.end
    ) return null
    return covered.joinToString("") { it.surface }.takeUnless(String::isBlank)
}

/**
 * Grows [span] by one neighbouring token on [edge]. Blank tokens (whitespace) are skipped so an
 * extension never produces an empty selection; null means the selection already reaches the end.
 */
internal fun extendSpan(tokens: List<JapaneseToken>, span: TargetSpan, edge: SpanEdge): TargetSpan? =
    when (edge) {
        SpanEdge.Left -> tokens
            .filter { it.span.end <= span.start && it.surface.isNotBlank() }
            .maxByOrNull { it.span.end }
            ?.let { TargetSpan(it.span.start, span.end) }

        SpanEdge.Right -> tokens
            .filter { it.span.start >= span.end && it.surface.isNotBlank() }
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
