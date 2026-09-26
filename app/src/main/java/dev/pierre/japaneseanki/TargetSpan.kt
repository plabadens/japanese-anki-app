package dev.pierre.japaneseanki

data class TargetSpan(val start: Int, val end: Int) {
    fun textIn(sentence: String): String? {
        if (start < 0 || end <= start || end > sentence.length) return null
        if (splitsSurrogate(sentence, start) || splitsSurrogate(sentence, end)) return null
        return sentence.substring(start, end).takeUnless(String::isBlank)
    }

    companion object {
        fun fromSelection(sentence: String, start: Int, end: Int): TargetSpan? {
            val span = TargetSpan(minOf(start, end), maxOf(start, end))
            return span.takeIf { it.textIn(sentence) != null }
        }

        private fun splitsSurrogate(text: String, index: Int): Boolean =
            index in 1 until text.length &&
                text[index - 1].isHighSurrogate() && text[index].isLowSurrogate()
    }
}
