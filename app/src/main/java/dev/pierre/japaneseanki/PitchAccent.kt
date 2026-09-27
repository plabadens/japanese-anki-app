package dev.pierre.japaneseanki

internal enum class PitchPatternKind { Heiban, Atamadaka, Nakadaka, Odaka }

class PitchAccent private constructor(
    val reading: String,
    val accentPosition: Int,
    val morae: List<String>,
) {
    internal val patternKind: PitchPatternKind
        get() = when {
            accentPosition == 0 -> PitchPatternKind.Heiban
            accentPosition == 1 -> PitchPatternKind.Atamadaka
            accentPosition == morae.size -> PitchPatternKind.Odaka
            else -> PitchPatternKind.Nakadaka
        }

    val fieldMarkup: String by lazy {
        val levels = pitchClasses(accentPosition, morae.size)
        morae.indices.joinToString(separator = "") { index ->
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

        fun create(reading: String, accentPosition: Int): PitchAccent? {
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
            return PitchAccent(normalized, accentPosition, morae)
        }

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
