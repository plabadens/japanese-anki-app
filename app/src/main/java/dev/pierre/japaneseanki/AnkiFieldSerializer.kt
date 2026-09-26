package dev.pierre.japaneseanki

/** Controlled HTML/Anki markup construction for plain user text and imported media. */
object AnkiFieldSerializer {
    fun highlightedSentence(sentence: String, target: TargetSpan): String? {
        val targetText = target.textIn(sentence) ?: return null
        return buildString(sentence.length + 7) {
            append(escapeHtml(sentence.substring(0, target.start)))
            append("<b>")
            append(escapeHtml(targetText))
            append("</b>")
            append(escapeHtml(sentence.substring(target.end)))
        }
    }

    fun highlightedFuriganaSentence(
        sentence: String,
        target: TargetSpan,
        reading: String,
    ): String? = highlightedFuriganaSentence(sentence, target, reading, emptyList())

    fun highlightedFuriganaSentence(
        sentence: String,
        target: TargetSpan,
        reading: String,
        sentenceFurigana: List<FuriganaSpan>,
    ): String? {
        val targetText = target.textIn(sentence) ?: return null
        val targetEvent = FuriganaSpan(target, targetText, reading)
        val otherEvents = sentenceFurigana.filter { annotation ->
            annotation.reading.isNotBlank() &&
                !(annotation.span.start < target.end && annotation.span.end > target.start)
        }
        val events = (otherEvents + targetEvent).sortedBy { it.span.start }
        var cursor = 0
        return buildString {
            events.forEach { event ->
                val eventSurface = event.span.textIn(sentence) ?: return null
                if (eventSurface != event.surface || event.span.start < cursor) return null
                append(escapeHtml(sentence.substring(cursor, event.span.start)))
                val marked = furiganaUnit(eventSurface, event.reading) ?: return null
                if (event == targetEvent) append("<b>$marked</b>") else append(marked)
                cursor = event.span.end
            }
            append(escapeHtml(sentence.substring(cursor)))
        }
    }

    fun furiganaWord(word: String, reading: String): String? = furiganaUnit(word, reading)

    fun plainText(text: String): String = escapeHtml(text)
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace("\n", "<br>")

    fun definition(senses: List<String>): String {
        val usable = senses.filter(String::isNotBlank)
        return when (usable.size) {
            0 -> ""
            1 -> plainText(usable.single())
            else -> usable.joinToString(separator = "", prefix = "<ol>", postfix = "</ol>") {
                "<li>${plainText(it)}</li>"
            }
        }
    }

    fun audioReference(filename: String): String? =
        safeMediaName(filename)?.let { "[sound:$it]" }

    fun imageReference(filename: String): String? =
        safeMediaName(filename)?.let { "<img src=\"${escapeHtml(it)}\">" }

    private enum class FuriganaSegmentKind { KANJI, KANA, OTHER }
    private data class FuriganaSegment(val text: String, val kind: FuriganaSegmentKind)

    private fun furiganaUnit(surface: String, reading: String): String? {
        if (surface.isEmpty() || reading.any { it == '[' || it == ']' }) return null
        if (reading.isBlank() || !containsKanji(surface)) return escapeHtml(surface)

        val segments = buildList {
            var start = 0
            var kind = segmentKind(surface.codePointAt(0))
            var index = Character.charCount(surface.codePointAt(0))
            while (index < surface.length) {
                val nextKind = segmentKind(surface.codePointAt(index))
                if (nextKind != kind) {
                    add(FuriganaSegment(surface.substring(start, index), kind))
                    start = index
                    kind = nextKind
                }
                index += Character.charCount(surface.codePointAt(index))
            }
            add(FuriganaSegment(surface.substring(start), kind))
        }
        val rendered = StringBuilder()
        var readingIndex = 0
        segments.forEachIndexed { index, segment ->
            when (segment.kind) {
                FuriganaSegmentKind.KANA -> {
                    val kana = katakanaToHiragana(segment.text)
                    if (!reading.startsWith(kana, readingIndex)) return fullWordRuby(surface, reading)
                    rendered.append(escapeHtml(segment.text))
                    readingIndex += kana.length
                }
                FuriganaSegmentKind.OTHER -> rendered.append(escapeHtml(segment.text))
                FuriganaSegmentKind.KANJI -> {
                    val nextKana = segments.drop(index + 1).firstOrNull {
                        it.kind == FuriganaSegmentKind.KANA
                    }
                    if (nextKana == null && segments.drop(index + 1).any {
                            it.kind == FuriganaSegmentKind.KANJI
                        }
                    ) return fullWordRuby(surface, reading)
                    val end = nextKana?.let {
                        reading.indexOf(katakanaToHiragana(it.text), readingIndex)
                    } ?: reading.length
                    if (end <= readingIndex) return fullWordRuby(surface, reading)
                    rendered.append(escapeHtml(segment.text))
                        .append('[').append(escapeHtml(reading.substring(readingIndex, end))).append(']')
                    readingIndex = end
                }
            }
        }
        return if (readingIndex == reading.length) rendered.toString() else fullWordRuby(surface, reading)
    }

    private fun fullWordRuby(surface: String, reading: String): String =
        "${escapeHtml(surface)}[${escapeHtml(reading)}]"

    private fun segmentKind(codePoint: Int): FuriganaSegmentKind = when {
        isKana(codePoint) -> FuriganaSegmentKind.KANA
        Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN -> FuriganaSegmentKind.KANJI
        else -> FuriganaSegmentKind.OTHER
    }

    private fun isKana(codePoint: Int): Boolean = codePoint == 0x30FC ||
        when (Character.UnicodeScript.of(codePoint)) {
            Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA -> true
            else -> false
        }

    private fun safeMediaName(name: String): String? {
        if (name.isBlank() || name == "." || name == "..") return null
        if (name.any { it == '/' || it == '\\' || it == '[' || it == ']' || it.isISOControl() }) return null
        return name
    }

    private fun escapeHtml(text: String): String = buildString(text.length) {
        text.forEach { char ->
            append(
                when (char) {
                    '&' -> "&amp;"
                    '<' -> "&lt;"
                    '>' -> "&gt;"
                    '"' -> "&quot;"
                    '\'' -> "&#39;"
                    '\u001f' -> "�"
                    else -> char.toString()
                },
            )
        }
    }
}
