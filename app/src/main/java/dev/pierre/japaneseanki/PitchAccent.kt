package dev.pierre.japaneseanki

internal enum class PitchPatternKind { Heiban, Atamadaka, Nakadaka, Odaka }

class PitchAccent private constructor(
    val reading: String,
    val accentPosition: Int,
    val morae: List<String>,
    /** Exact source-supplied AJT class-span HTML, or null when no source pattern is available. */
    val sourceHtml: String? = null,
) {
    internal val patternKind: PitchPatternKind
        get() = when {
            accentPosition == 0 -> PitchPatternKind.Heiban
            accentPosition == 1 -> PitchPatternKind.Atamadaka
            accentPosition == morae.size -> PitchPatternKind.Odaka
            else -> PitchPatternKind.Nakadaka
        }

    /** Source phonetic marks, parsed from the supplied HTML; never inferred. */
    val marks: AjtPitchMarks by lazy {
        sourceHtml?.let { AjtPitchHtml.marks(it, reading) } ?: AjtPitchMarks(emptySet(), emptySet())
    }

    val fieldMarkup: String by lazy {
        sourceHtml?.let { "$it <span class=\"pitch_number\">$accentPosition</span>" }
            ?: derivedMarkup()
    }

    private fun derivedMarkup(): String {
        val levels = pitchClasses(accentPosition, morae.size)
        return morae.indices.joinToString(separator = "") { index ->
            "<span class=\"${levels[index]}\">${morae[index]}</span>"
        } + " <span class=\"pitch_number\">$accentPosition</span>"
    }

    val highLevels: List<Boolean> by lazy {
        pitchClasses(accentPosition, morae.size).map { it == "high" || it == "high_drop" }
    }

    val contourDescription: String by lazy {
        "${morae.joinToString(" ")} — " + highLevels.joinToString(" ") { if (it) "high" else "low" }
    }

    companion object {
        private val smallKana = setOf(
            'ぁ', 'ぃ', 'ぅ', 'ぇ', 'ぉ', 'ゃ', 'ゅ', 'ょ', 'ゎ',
            'ァ', 'ィ', 'ゥ', 'ェ', 'ォ', 'ャ', 'ュ', 'ョ', 'ヮ',
        )

        fun create(reading: String, accentPosition: Int, sourceHtml: String? = null): PitchAccent? {
            val normalized = reading.trim()
            if (normalized.isEmpty() || !normalized.all(::isKana)) return null
            val morae = mutableListOf<String>()
            normalized.forEach { char ->
                if (char in smallKana && morae.isNotEmpty()) {
                    morae[morae.lastIndex] += char
                } else {
                    morae += char.toString()
                }
            }
            if (accentPosition !in 0..morae.size) return null
            if (sourceHtml != null && !sourceHtmlFits(sourceHtml, normalized)) return null
            return PitchAccent(normalized, accentPosition, morae, sourceHtml)
        }

        /**
         * The source pattern must cover exactly the resolved reading. A pattern for another
         * reading or a different mora count is rejected instead of exported beside this word.
         */
        private fun sourceHtmlFits(html: String, reading: String): Boolean {
            val text = html.replace(Regex("<[^>]*>"), "")
                .replace("&#176;", "")
                .replace("\u00b0", "")
            val htmlMorae = AjtPitchHtml.moraeOf(katakanaToHiragana(text))
            val readMorae = AjtPitchHtml.moraeOf(katakanaToHiragana(reading))
            if (htmlMorae.size != readMorae.size) return false
            // Nasalised morae are written with the unvoiced kana plus a degree mark, so compare
            // base kana rather than requiring identical voicing.
            return htmlMorae.zip(readMorae).all { (source, resolved) ->
                source.map(::baseKana) == resolved.map(::baseKana)
            }
        }

        private fun baseKana(char: Char): Char {
            val hiragana = if (char in 'ァ'..'ヶ') char - 0x60 else char
            val index = VOICED_HIRAGANA.indexOf(hiragana)
            return if (index >= 0) BASE_HIRAGANA[index] else hiragana
        }

        private const val VOICED_HIRAGANA = "がぎぐげござじずぜぞだぢづでどばびぶべぼぱぴぷぺぽ"
        private const val BASE_HIRAGANA = "かきくけこさしすせそたちつてとはひふへほはひふへほ"

        private fun isKana(char: Char): Boolean =
            Character.UnicodeScript.of(char.code) in setOf(
                Character.UnicodeScript.HIRAGANA,
                Character.UnicodeScript.KATAKANA,
            ) || char == 'ー' || char == 'ｰ'

        private fun pitchClasses(accent: Int, moraCount: Int): List<String> = when {
            accent == 0 -> listOf("low_rise") + List(moraCount - 1) { "high" }
            accent == 1 -> listOf("high_drop") + List(moraCount - 1) { "low" }
            accent == moraCount -> listOf("low_rise") + List(moraCount - 1) { "high" }
            else -> buildList {
                add("low_rise")
                for (index in 2..moraCount) {
                    add(
                        when {
                            index < accent -> "high"
                            index == accent -> "high_drop"
                            else -> "low"
                        },
                    )
                }
            }
        }
    }
}
