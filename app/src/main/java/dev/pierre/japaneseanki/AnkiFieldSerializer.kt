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

    private fun furiganaUnit(surface: String, reading: String): String? {
        if (surface.isEmpty() || reading.any { it == '[' || it == ']' }) return null
        if (reading.isBlank() || !containsKanji(surface)) return escapeHtml(surface)

        val spans = furiganaSpansForWord(TargetSpan(0, surface.length), surface, reading)
        if (spans.isEmpty()) return fullWordRuby(surface, reading)
        return buildString {
            var cursor = 0
            spans.forEach { span ->
                append(escapeHtml(surface.substring(cursor, span.span.start)))
                append(escapeHtml(span.surface))
                append('[').append(escapeHtml(span.reading)).append(']')
                cursor = span.span.end
            }
            append(escapeHtml(surface.substring(cursor)))
        }
    }

    private fun fullWordRuby(surface: String, reading: String): String =
        "${escapeHtml(surface)}[${escapeHtml(reading)}]"

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
