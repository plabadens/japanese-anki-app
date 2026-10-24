package dev.pierre.japaneseanki.card

import androidx.compose.runtime.Composable
import dev.pierre.japaneseanki.DictionaryMeaningChoice
import dev.pierre.japaneseanki.ui.DictionaryMeaningSheet
import dev.pierre.japaneseanki.ui.TouchFeedbackEvent
import dev.pierre.japaneseanki.ui.rememberChangeFeedback

@Composable
internal fun CardMeaningPicker(
    visible: Boolean,
    targetAvailable: Boolean,
    choices: List<DictionaryMeaningChoice>,
    enabled: Boolean,
    selectedEntryId: Long?,
    vocabulary: String,
    vocabularyReading: String,
    selectedSenseIndices: Set<Int>,
    onDismiss: () -> Unit,
    onChoose: (DictionaryMeaningChoice) -> Unit,
) {
    val selectionFeedback = rememberChangeFeedback(
        event = TouchFeedbackEvent.MEANING_SELECTION,
        enabled = enabled,
    )
    if (visible && targetAvailable && choices.size > 1) {
        DictionaryMeaningSheet(
            choices = choices,
            enabled = enabled,
            onDismissRequest = onDismiss,
            onSelect = { choice ->
                val changed = selectedEntryId != choice.entry.id ||
                    vocabulary != choice.entry.expression ||
                    vocabularyReading != choice.entry.reading ||
                    selectedSenseIndices != setOf(choice.senseIndex)
                if (changed) {
                    selectionFeedback(
                        listOf(
                            choice.entry.id,
                            choice.entry.expression,
                            choice.entry.reading,
                            choice.senseIndex,
                        ),
                    )
                }
                onChoose(choice)
            },
        )
    }
}
