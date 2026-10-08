package dev.pierre.japaneseanki

internal data class JapaneseToken(
    val span: TargetSpan,
    val surface: String,
    val lemma: String,
    val surfaceReading: String,
    val lemmaReading: String,
    val partOfSpeech: String,
)

internal data class AnalyzedLemma(val expression: String, val reading: String)
data class FuriganaSpan(val span: TargetSpan, val surface: String, val reading: String)
internal fun sentenceFuriganaSpans(
    sentence: String,
    tokens: List<JapaneseToken>,
    target: TargetSpan?,
): List<FuriganaSpan> = tokens.mapNotNull { token ->
        if (!containsKanji(token.surface) || token.span.textIn(sentence) != token.surface ||
            token.surfaceReading.isBlank()
        ) {
            return@mapNotNull null
        }
        if (target?.let { token.span.start < it.end && token.span.end > it.start } == true) {
            return@mapNotNull null
        }
        FuriganaSpan(token.span, token.surface, token.surfaceReading)
    }

internal fun resolveSurfaceReading(
    sentence: String,
    tokens: List<JapaneseToken>,
    target: TargetSpan,
): String? {
    val selected = tokensCoveringTarget(tokens, target) ?: return null
    if (target.textIn(sentence) != selected.joinToString("") { it.surface } ||
        selected.any { it.partOfSpeech.startsWith("補助記号") }
    ) return null
    val readings = selected.map(JapaneseToken::surfaceReading)
    if (selected.indices.any { containsKanji(selected[it].surface) && readings[it].isBlank() }) return null
    return selected.indices.joinToString("") { index ->
        if (containsKanji(selected[index].surface)) readings[index] else selected[index].surface
    }
}

internal fun resolveAnalyzedLemma(tokens: List<JapaneseToken>, target: TargetSpan): AnalyzedLemma? {
    val selected = tokensCoveringTarget(tokens, target) ?: return null
    if (selected.size == 1) {
        val token = selected.single()
        return AnalyzedLemma(token.lemma, token.lemmaReading)
    }

    val content = selected.withIndex().filter {
        it.value.partOfSpeech.startsWith("動詞") || it.value.partOfSpeech.startsWith("形容詞") ||
            it.value.partOfSpeech.startsWith("形状詞")
    }
    if (content.size != 1) return null
    val headIndex = content.single().index
    val head = content.single().value
    val ignoredIndex = if (head.lemma == "する" && head.partOfSpeech.startsWith("動詞")) {
        (headIndex - 1).takeIf { it >= 0 && selected[it].partOfSpeech.startsWith("名詞") }
    } else null
    if (selected.indices.any { index ->
            index != headIndex && index != ignoredIndex &&
                !selected[index].partOfSpeech.startsWith("助詞") &&
                !selected[index].partOfSpeech.startsWith("助動詞")
        }
    ) return null

    if (ignoredIndex != null) {
        val noun = selected[ignoredIndex]
        return AnalyzedLemma(noun.lemma + "する", noun.lemmaReading + "する")
    }
    return AnalyzedLemma(head.lemma, head.lemmaReading)
}

private fun tokensCoveringTarget(tokens: List<JapaneseToken>, target: TargetSpan): List<JapaneseToken>? {
    if (target.start < 0 || target.end <= target.start) return null
    val selected = tokens.filter { it.span.start >= target.start && it.span.end <= target.end }
    if (selected.isEmpty() || selected.first().span.start != target.start ||
        selected.last().span.end != target.end
    ) return null
    var boundary = target.start
    for (token in selected) {
        if (token.span.start != boundary || token.span.end <= token.span.start ||
            token.surface.length != token.span.end - token.span.start
        ) return null
        boundary = token.span.end
    }
    return selected.takeIf { boundary == target.end }
}

internal fun katakanaToHiragana(reading: String): String = buildString(reading.length) {
    reading.forEach { character ->
        append(
            when (character) {
                in '\u30a1'..'\u30f6' -> character - 0x60
                '\u30fd' -> '\u309d'
                '\u30fe' -> '\u309e'
                else -> character
            },
        )
    }
}
