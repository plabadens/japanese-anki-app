package dev.pierre.japaneseanki

/** A user-corrected reading for one kanji span. The span, not the surface, is its identity. */
data class ReadingCorrection(val span: TargetSpan, val surface: String, val reading: String)

/** A kanji span the user may correct, with the reading currently in effect. */
data class CorrectableReading(
    val span: TargetSpan,
    val surface: String,
    val reading: String,
    val isTarget: Boolean,
) {
    val unresolved: Boolean get() = reading.isBlank()
}

/**
 * Targeted reading correction (SPEC §4.5, §5.2, §10).
 *
 * Corrections are stored per span and never edit the sentence: the source text stays exactly as
 * captured/typed. A correction for the selected target is held in the draft's `sentenceReading`
 * field instead, so the two cannot disagree.
 */
internal object SentenceReadings {
    private const val FIELD_SEPARATOR = "\t"
    private const val RECORD_SEPARATOR = "\n"

    /** Automatic furigana for the sentence with user corrections applied, in sentence order. */
    fun effective(
        sentence: String,
        tokens: List<JapaneseToken>,
        target: TargetSpan?,
        targetReading: String,
        corrections: List<ReadingCorrection>,
    ): List<FuriganaSpan> {
        val automatic = sentenceFuriganaSpans(sentence, tokens, target)
        val usable = corrections.mapNotNull { correction ->
            val surface = correction.span.textIn(sentence) ?: return@mapNotNull null
            if (surface != correction.surface) return@mapNotNull null
            if (target != null && overlaps(correction.span, target)) return@mapNotNull null
            if (rejection(sentence, correction.span, correction.reading) != null) return@mapNotNull null
            FuriganaSpan(correction.span, surface, normalizeReading(correction.reading))
        }
        if (usable.isEmpty()) return automatic

        val corrected = automatic.filter { run -> usable.none { overlaps(it.span, run.span) } } + usable
        return corrected.sortedBy { it.span.start }
    }

    /** Kanji spans the user can correct, including the selected target's own reading field. */
    fun correctable(
        sentence: String,
        tokens: List<JapaneseToken>,
        target: TargetSpan?,
        targetReading: String,
        corrections: List<ReadingCorrection>,
    ): List<CorrectableReading> {
        val stored = corrections.associateBy { it.span.start }
        val rows = mutableListOf<CorrectableReading>()
        tokens.forEach { token ->
            val surface = token.span.textIn(sentence) ?: return@forEach
            if (surface != token.surface || !containsKanji(surface)) return@forEach
            if (target != null && overlaps(token.span, target)) return@forEach
            val correction = stored[token.span.start]
                ?.takeIf { it.surface == surface && rejection(sentence, it.span, it.reading) == null }
            rows += CorrectableReading(
                span = token.span,
                surface = surface,
                reading = correction?.let { normalizeReading(it.reading) } ?: token.surfaceReading,
                isTarget = false,
            )
        }
        if (target != null) {
            val surface = target.textIn(sentence)
            if (surface != null && containsKanji(surface)) {
                rows += CorrectableReading(target, surface, targetReading.trim(), isTarget = true)
            }
        }
        return rows.sortedBy { it.span.start }
    }

    /** Store one correction, replacing any earlier correction for the same span. */
    fun correct(
        corrections: List<ReadingCorrection>,
        sentence: String,
        span: TargetSpan,
        reading: String,
    ): List<ReadingCorrection> {
        if (rejection(sentence, span, reading) != null) return corrections
        val surface = span.textIn(sentence) ?: return corrections
        return (corrections.filterNot { it.span == span } + ReadingCorrection(span, surface, normalizeReading(reading)))
            .sortedBy { it.span.start }
    }

    /** Why a correction cannot be stored, or null when it is usable. */
    fun rejection(sentence: String, span: TargetSpan, reading: String): String? {
        val surface = span.textIn(sentence) ?: return "Select a span inside the sentence."
        if (!containsKanji(surface)) return "Only kanji spans need a reading."
        val normalized = normalizeReading(reading)
        if (normalized.isEmpty()) return "Enter the reading in kana."
        if (normalized.any { it == '[' || it == ']' }) return "Readings cannot contain brackets."
        if (!normalized.all(::isKana)) return "Use kana only for the reading."
        return null
    }

    /** Draft persistence: one tab-separated record per correction, one per line. */
    fun encode(corrections: List<ReadingCorrection>): String = corrections
        .sortedBy { it.span.start }
        .joinToString(RECORD_SEPARATOR) { "${it.span.start}$FIELD_SEPARATOR${it.span.end}$FIELD_SEPARATOR${it.surface}$FIELD_SEPARATOR${it.reading}" }

    /**
     * Reload corrections for [sentence]. A record whose span no longer covers the same surface is
     * dropped rather than pasted onto a different word after an edit.
     */
    fun decode(encoded: String?, sentence: String): List<ReadingCorrection> {
        if (encoded.isNullOrBlank()) return emptyList()
        return encoded.split(RECORD_SEPARATOR).mapNotNull { line ->
            val parts = line.split(FIELD_SEPARATOR)
            if (parts.size != 4) return@mapNotNull null
            val start = parts[0].toIntOrNull() ?: return@mapNotNull null
            val end = parts[1].toIntOrNull() ?: return@mapNotNull null
            val span = TargetSpan(start, end)
            val surface = span.textIn(sentence) ?: return@mapNotNull null
            if (surface != parts[2]) return@mapNotNull null
            if (rejection(sentence, span, parts[3]) != null) return@mapNotNull null
            ReadingCorrection(span, surface, normalizeReading(parts[3]))
        }.sortedBy { it.span.start }
    }

    fun normalizeReading(reading: String): String = reading
        .filterNot { it == ' ' || it == '\u3000' }
        .let(::katakanaToHiragana)

    private fun overlaps(first: TargetSpan, second: TargetSpan): Boolean =
        first.start < second.end && first.end > second.start

    private fun isKana(char: Char): Boolean = when (Character.UnicodeScript.of(char.code)) {
        Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA -> true
        else -> char == 'ー' || char == 'ｰ'
    }
}
