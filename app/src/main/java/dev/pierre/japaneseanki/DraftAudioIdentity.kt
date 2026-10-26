package dev.pierre.japaneseanki


/**
 * Identities that decide whether a retained audio file still matches the draft (SPEC §10).
 *
 * A sentence-reading correction changes the sentence recording and nothing else; a vocabulary
 * reading correction changes the word recording (its entry, pitch and NHK recording identity).
 */
internal object DraftAudioIdentity {
    /** Identity of the sentence recording: the spoken text and the voice that speaks it. */
    fun sentenceRequest(sentence: String, voiceName: String): String =
        listOf(sentence, voiceName).joinToString("\u0000")

    fun sentence(
        sentence: String,
        target: TargetSpan?,
        sentenceReading: String,
        furigana: List<FuriganaSpan>,
        voiceName: String,
    ): String = listOf(
        sentence,
        (target?.start ?: -1).toString(),
        (target?.end ?: -1).toString(),
        sentenceReading,
        furigana.joinToString(";") { "${it.span.start}:${it.span.end}:${it.surface}:${it.reading}" },
        voiceName,
    ).joinToString("\u0000")

    fun word(entryId: Long?, vocabulary: String, vocabularyReading: String): String = listOf(
        entryId?.toString().orEmpty(),
        vocabulary,
        vocabularyReading,
    ).joinToString("\u0000")
}
