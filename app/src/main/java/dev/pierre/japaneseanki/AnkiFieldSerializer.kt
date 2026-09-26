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

    fun plainText(text: String): String = escapeHtml(text)
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace("\n", "<br>")

    fun definition(senses: List<String>): String {
        val usable = senses.filter(String::isNotBlank)
        return when (usable.size) {
            0 -> ""
            1 -> escapeHtml(usable.single())
            else -> usable.joinToString(separator = "", prefix = "<ol>", postfix = "</ol>") {
                "<li>${escapeHtml(it)}</li>"
            }
        }
    }

    fun audioReference(filename: String): String? =
        safeMediaName(filename)?.let { "[sound:$it]" }

    fun imageReference(filename: String): String? =
        safeMediaName(filename)?.let { "<img src=\"${escapeHtml(it)}\">" }

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
                    else -> char.toString()
                },
            )
        }
    }
}
