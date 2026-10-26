package dev.pierre.japaneseanki

/**
 * Splits one analysed word into the kanji groups that should carry furigana.
 *
 * AJT aligns a reading to the kanji runs, not to the whole MeCab token: the okurigana stays
 * outside the brackets (`食べた` -> `食[た]べた`) and a mixed compound can split into more than
 * one kanji group (`相合い傘` -> `相合[あいあ]い傘[がさ]`). Sora applies the same alignment to the
 * sentence and to the exported Anki field.
 */
internal fun furiganaSpansForWord(
    span: TargetSpan,
    surface: String,
    reading: String,
): List<FuriganaSpan> {
    if (surface.isEmpty() || reading.isBlank() || !containsKanji(surface)) return emptyList()
    val normalized = katakanaToHiragana(reading)
    val segments = furiganaSegments(surface)
    if (segments.none { it.kind == FuriganaSegmentKind.KANJI }) return emptyList()

    return alignFurigana(segments, normalized, 0, 0, 0, span.start)
        // A malformed or contradictory reading must still show its word reading rather than lose
        // furigana entirely; the normal path never reaches this fallback.
        ?: listOf(FuriganaSpan(span, surface, normalized))
}

private enum class FuriganaSegmentKind { KANJI, KANA, OTHER }

private data class FuriganaSegment(val text: String, val kind: FuriganaSegmentKind)

private fun furiganaSegments(surface: String): List<FuriganaSegment> {
    if (surface.isEmpty()) return emptyList()
    val segments = mutableListOf<FuriganaSegment>()
    var start = 0
    var index = 0
    var kind = furiganaSegmentKind(surface.codePointAt(0))
    while (index < surface.length) {
        val codePoint = surface.codePointAt(index)
        val nextKind = furiganaSegmentKind(codePoint)
        if (nextKind != kind) {
            segments += FuriganaSegment(surface.substring(start, index), kind)
            start = index
            kind = nextKind
        }
        index += Character.charCount(codePoint)
    }
    segments += FuriganaSegment(surface.substring(start), kind)
    return segments
}

private fun alignFurigana(
    segments: List<FuriganaSegment>,
    reading: String,
    segmentIndex: Int,
    readingIndex: Int,
    surfaceOffset: Int,
    spanStart: Int,
): List<FuriganaSpan>? {
    if (segmentIndex == segments.size) {
        return if (readingIndex == reading.length) emptyList() else null
    }
    val segment = segments[segmentIndex]
    return when (segment.kind) {
        FuriganaSegmentKind.KANA -> {
            val kana = katakanaToHiragana(segment.text)
            if (!reading.startsWith(kana, readingIndex)) {
                null
            } else {
                alignFurigana(
                    segments,
                    reading,
                    segmentIndex + 1,
                    readingIndex + kana.length,
                    surfaceOffset + segment.text.length,
                    spanStart,
                )
            }
        }

        FuriganaSegmentKind.OTHER -> {
            // Punctuation and other literals never receive furigana. Consume them from the
            // reading only when the source actually includes them.
            val consumed = if (reading.startsWith(segment.text, readingIndex)) segment.text.length else 0
            alignFurigana(
                segments,
                reading,
                segmentIndex + 1,
                readingIndex + consumed,
                surfaceOffset + segment.text.length,
                spanStart,
            )
        }

        FuriganaSegmentKind.KANJI -> {
            val nextKana = segments.drop(segmentIndex + 1)
                .firstOrNull { it.kind == FuriganaSegmentKind.KANA }
                ?.text
                ?.let(::katakanaToHiragana)
            val candidates = if (nextKana == null) {
                listOf(reading.length)
            } else {
                buildList {
                    var searchFrom = readingIndex
                    while (true) {
                        val at = reading.indexOf(nextKana, searchFrom)
                        if (at < 0) break
                        if (at > readingIndex) add(at)
                        searchFrom = at + 1
                    }
                }.asReversed()
            }
            candidates.forEach { end ->
                val kanaReading = reading.substring(readingIndex, end)
                if (kanaReading.isEmpty()) return@forEach
                val rest = alignFurigana(
                    segments,
                    reading,
                    segmentIndex + 1,
                    end,
                    surfaceOffset + segment.text.length,
                    spanStart,
                ) ?: return@forEach
                return listOf(
                    FuriganaSpan(
                        span = TargetSpan(spanStart + surfaceOffset, spanStart + surfaceOffset + segment.text.length),
                        surface = segment.text,
                        reading = kanaReading,
                    ),
                ) + rest
            }
            null
        }
    }
}

private fun furiganaSegmentKind(codePoint: Int): FuriganaSegmentKind = when {
    Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN -> FuriganaSegmentKind.KANJI
    isKanaCodePoint(codePoint) -> FuriganaSegmentKind.KANA
    else -> FuriganaSegmentKind.OTHER
}

private fun isKanaCodePoint(codePoint: Int): Boolean = codePoint == 0x30FC ||
    when (Character.UnicodeScript.of(codePoint)) {
        Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA -> true
        else -> false
    }
