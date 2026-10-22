package dev.pierre.japaneseanki

/** Native rendering data derived from one source HTML pattern. Never invents marks. */
data class AjtPitchMarks(
    val nasalMorae: Set<Int>,
    val devoicedMorae: Set<Int>,
) {
    val isEmpty: Boolean get() = nasalMorae.isEmpty() && devoicedMorae.isEmpty()
}

/**
 * Parses AJT class-span HTML into mora marks. The degree symbol inside a handakuten span is an
 * annotation, not a mora, and small kana join the preceding mora.
 */
object AjtPitchHtml {
    private val CLASS = Regex("class=\"([a-z_]+)\"")

    fun marks(html: String, reading: String): AjtPitchMarks {
        val nasal = mutableSetOf<Int>()
        val devoiced = mutableSetOf<Int>()
        val stack = ArrayDeque<String>()
        val morae = moraeOf(reading)
        var moraIndex = -1
        var position = 0
        while (position < html.length) {
            val char = html[position]
            if (char == '<') {
                val end = html.indexOf('>', position)
                if (end < 0) break
                val tag = html.substring(position + 1, end).trim()
                if (tag.startsWith("/")) {
                    if (stack.isNotEmpty()) stack.removeLast()
                } else {
                    val name = CLASS.find(tag)?.groupValues?.get(1)
                        ?: tag.substringBefore(' ').trim()
                    if (name.isNotEmpty()) stack.addLast(name)
                }
                position = end + 1
                continue
            }
            if (char == '&') {
                val end = html.indexOf(';', position)
                if (end < 0) break
                // Numeric entity: a handakuten degree mark is an annotation, never a mora.
                position = end + 1
                continue
            }
            if (char == '\u00b0') {
                position++
                continue
            }
            if (char !in SMALL_KANA) {
                moraIndex++
                if (moraIndex >= morae.size) return AjtPitchMarks(nasal, devoiced)
            }
            if (moraIndex >= 0) {
                if ("nasal" in stack) nasal += moraIndex
                if ("devoiced" in stack) devoiced += moraIndex
            }
            position++
        }
        return AjtPitchMarks(nasal, devoiced)
    }

    fun moraeOf(reading: String): List<String> {
        val morae = mutableListOf<String>()
        reading.forEach { char ->
            if (char in SMALL_KANA && morae.isNotEmpty()) morae[morae.lastIndex] += char
            else morae += char.toString()
        }
        return morae
    }

    private val SMALL_KANA = setOf(
        'ぁ', 'ぃ', 'ぅ', 'ぇ', 'ぉ', 'ゃ', 'ゅ', 'ょ', 'ゎ',
        'ァ', 'ィ', 'ゥ', 'ェ', 'ォ', 'ャ', 'ュ', 'ョ', 'ヮ',
    )
}

/** AJT indexes hiragana readings; MeCab and JMdict inputs may be katakana. */
internal fun hiragana(reading: String): String = buildString(reading.length) {
    reading.forEach { char ->
        append(if (char in 'ァ'..'ヶ') char - 0x60 else char)
    }
}

/** AJT's formatted lookup key converts kana in the headword to katakana before querying. */
internal fun katakana(expression: String): String = buildString(expression.length) {
    expression.forEach { char ->
        append(
            when (char) {
                in 'ぁ'..'ゖ' -> char + 0x60
                'ゝ' -> 'ヽ'
                'ゞ' -> 'ヾ'
                else -> char
            },
        )
    }
}
