package dev.pierre.japaneseanki

/**
 * UniDic is a morphological analyzer: its output is the right input for lemma and reading
 * resolution, but not the right set of targets to show as words. 図書 + 館 and 読み + まし + た are
 * one word to a learner; showing the morphemes separately makes the target sheet look broken and
 * forces the user to extend across pieces the dictionary already knows as one unit.
 *
 * This layer keeps the morphemes' combined span/readings but exposes the natural selection unit:
 * prefixes and suffixes join their head, predicates absorb their auxiliaries and connective
 * particles, and particles/punctuation stay separate. The original surface is never rewritten;
 * every grouped token's span still points at exactly the same characters in the sentence.
 */
internal fun groupTokens(
    tokens: List<JapaneseToken>,
    hasDictionaryEntry: (String) -> Boolean = { false },
): List<JapaneseToken> {
    if (tokens.isEmpty()) return emptyList()
    val grouped = mutableListOf<JapaneseToken>()
    var index = 0
    while (index < tokens.size) {
        var current = tokens[index]
        var kind = kindOf(current)
        var nextIndex = index + 1
        while (nextIndex < tokens.size) {
            val next = tokens[nextIndex]
            if (current.span.end != next.span.start) break

            when {
                kind == GroupKind.Prefix && (next.isPrefixToken() || next.isNounToken()) -> {
                    current = mergeCompound(current, next)
                    kind = if (next.isNounToken()) GroupKind.Noun else GroupKind.Prefix
                    nextIndex++
                }

                kind == GroupKind.Noun && next.isSuffixToken() -> {
                    current = mergeCompound(current, next)
                    nextIndex++
                }

                kind == GroupKind.Noun && next.isNounToken() &&
                    hasDictionaryEntry(current.surface + next.surface) -> {
                    current = mergeCompound(current, next)
                    nextIndex++
                }

                kind == GroupKind.Predicate && next.isAuxiliaryToken() -> {
                    current = mergePredicate(current, next)
                    nextIndex++
                }

                kind == GroupKind.Predicate && next.isConnectiveParticle() -> {
                    val following = tokens.getOrNull(nextIndex + 1)
                    current = if (following != null && following.span.start == next.span.end &&
                        following.isNonIndependentVerbToken()
                    ) {
                        nextIndex += 2
                        mergePredicate(mergePredicate(current, next), following)
                    } else {
                        nextIndex++
                        mergePredicate(current, next)
                    }
                }

                else -> break
            }
        }
        grouped += current
        index = nextIndex
    }
    return grouped
}

private enum class GroupKind { Prefix, Noun, Predicate, Other }

private fun kindOf(token: JapaneseToken): GroupKind = when {
    token.isPrefixToken() -> GroupKind.Prefix
    token.isNounToken() -> GroupKind.Noun
    token.isPredicateToken() -> GroupKind.Predicate
    else -> GroupKind.Other
}

private fun mergeCompound(left: JapaneseToken, right: JapaneseToken): JapaneseToken = left.copy(
    span = TargetSpan(left.span.start, right.span.end),
    surface = left.surface + right.surface,
    lemma = left.lemma + right.lemma,
    surfaceReading = left.surfaceReading + right.surfaceReading,
    lemmaReading = left.lemmaReading + right.lemmaReading,
    // A prefix is not the head of the compound. The noun it attaches to determines how a
    // following suffix or further compound may be grouped.
    partOfSpeech = if (left.isPrefixToken()) right.partOfSpeech else left.partOfSpeech,
)

private fun mergePredicate(left: JapaneseToken, right: JapaneseToken): JapaneseToken = left.copy(
    span = TargetSpan(left.span.start, right.span.end),
    surface = left.surface + right.surface,
    // The predicate supplies the lemma (読みました -> 読む); the auxiliaries only extend the
    // surface and its reading. Keeping the first lemma is what makes dictionary lookup resolve
    // the entry the learner actually wants.
    surfaceReading = left.surfaceReading + right.surfaceReading,
)

private fun JapaneseToken.isPrefixToken(): Boolean = partOfSpeech.startsWith("接頭辞")
private fun JapaneseToken.isSuffixToken(): Boolean = partOfSpeech.startsWith("接尾辞")
private fun JapaneseToken.isNounToken(): Boolean = partOfSpeech.startsWith("名詞")
private fun JapaneseToken.isAuxiliaryToken(): Boolean = partOfSpeech.startsWith("助動詞")

private fun JapaneseToken.isPredicateToken(): Boolean =
    partOfSpeech.startsWith("動詞") ||
        partOfSpeech.startsWith("形容詞") ||
        partOfSpeech.startsWith("形状詞")

private fun JapaneseToken.isNonIndependentVerbToken(): Boolean =
    partOfSpeech.startsWith("動詞") && partOfSpeech.contains("非自立")

/** Bound connective particles that are part of the predicate's surface, not a separate target. */
private fun JapaneseToken.isConnectiveParticle(): Boolean =
    partOfSpeech.startsWith("助詞-接続助詞") &&
        surface in setOf("て", "で", "ながら", "たり", "ば", "つつ", "ず")
