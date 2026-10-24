package dev.pierre.japaneseanki

import androidx.compose.runtime.Immutable

@Immutable
data class DraftModel(
    val sentence: String = "",
    val target: TargetSpan? = null,
    val vocabulary: String = "",
    val vocabularyReading: String = "",
    val definition: String = "",
    val pitchAccent: String = "",
    val sentenceReading: String = "",
    val readingCorrections: List<ReadingCorrection> = emptyList(),
    val notes: String = "",
    val tags: String = "",
) {
    fun withSentence(text: String): DraftModel = if (text == sentence) {
        this
    } else {
        copy(sentence = text).withTarget(null)
    }

    /** Changing the selected occurrence invalidates every field derived from the old word. */
    fun withTarget(span: TargetSpan?): DraftModel = if (span == target) {
        this
    } else {
        copy(
            target = span,
            vocabulary = "",
            vocabularyReading = "",
            definition = "",
            pitchAccent = "",
            sentenceReading = "",
        )
    }

    fun withEntry(entry: DictionaryEntry, senses: Set<Int>): DraftModel {
        val samePronunciation = vocabulary == entry.expression && vocabularyReading == entry.reading
        return copy(
            vocabulary = entry.expression,
            vocabularyReading = entry.reading,
            definition = senses.sorted().mapNotNull { index ->
                entry.senses.getOrNull(index)?.glosses?.map(String::trim)?.filter(String::isNotEmpty)
                    ?.joinToString("; ")?.takeIf(String::isNotEmpty)
            }.joinToString("\n"),
            pitchAccent = if (samePronunciation) pitchAccent else "",
        )
    }

    fun withDefinition(text: String): DraftModel = copy(definition = text)

    fun cleared(defaultTags: String): DraftModel = DraftModel(
        readingCorrections = readingCorrections,
        tags = defaultTags,
    )
}

/** Compose adapter: individual call sites can stay legible while one immutable model owns the state. */
internal class DraftModelField<T>(
    private val state: androidx.compose.runtime.MutableState<DraftModel>,
    private val read: (DraftModel) -> T,
    private val reduce: (DraftModel, T) -> DraftModel,
) : kotlin.properties.ReadWriteProperty<Any?, T> {
    override fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): T = read(state.value)

    override fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: T) {
        state.value = reduce(state.value, value)
    }
}

internal fun <T> androidx.compose.runtime.MutableState<DraftModel>.field(
    read: (DraftModel) -> T,
    reduce: (DraftModel, T) -> DraftModel,
): DraftModelField<T> = DraftModelField(this, read, reduce)

internal fun DraftModel.withVocabulary(text: String): DraftModel = copy(vocabulary = text)
internal fun DraftModel.withVocabularyReading(text: String): DraftModel = copy(vocabularyReading = text)
internal fun DraftModel.withPitchAccent(text: String): DraftModel = copy(pitchAccent = text)
internal fun DraftModel.withSentenceReading(text: String): DraftModel = copy(sentenceReading = text)
internal fun DraftModel.withReadingCorrections(corrections: List<ReadingCorrection>): DraftModel =
    copy(readingCorrections = corrections)
internal fun DraftModel.withNotes(text: String): DraftModel = copy(notes = text)
internal fun DraftModel.withTags(text: String): DraftModel = copy(tags = text)
