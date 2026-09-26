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

internal fun resolveSurfaceReading(tokens: List<JapaneseToken>, target: TargetSpan): String? {
    val selected = tokens.filter { it.span.start >= target.start && it.span.end <= target.end }
    if (selected.isEmpty() || selected.first().span.start != target.start ||
        selected.last().span.end != target.end ||
        selected.sumOf { it.span.end - it.span.start } != target.end - target.start ||
        selected.any { it.partOfSpeech.startsWith("補助記号") } ||
        selected.any { containsKanji(it.surface) && it.surfaceReading.isBlank() }
    ) return null
    return selected.joinToString("") { if (containsKanji(it.surface)) it.surfaceReading else it.surface }
}

internal fun resolveAnalyzedLemma(tokens: List<JapaneseToken>, target: TargetSpan): AnalyzedLemma? {
    val selected = tokens.filter { it.span.start >= target.start && it.span.end <= target.end }
    if (selected.isEmpty() || selected.first().span.start != target.start ||
        selected.last().span.end != target.end || selected.joinToString("") { it.surface }.length != target.end - target.start
    ) return null
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
