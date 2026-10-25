package dev.pierre.japaneseanki.card

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import dev.pierre.japaneseanki.R
import dev.pierre.japaneseanki.TargetSpan
import dev.pierre.japaneseanki.ui.FlowSheet
import dev.pierre.japaneseanki.ui.flow

@Composable
internal fun CardSentenceEditorSheet(
    draft: TextFieldValue?,
    editor: TextFieldValue,
    onDraftChanged: (TextFieldValue) -> Unit,
    onDismiss: () -> Unit,
    onUseSelection: (TextFieldValue) -> Unit,
    onDone: (TextFieldValue) -> Unit,
    selectionHint: String,
    useSelectionLabel: String,
) {
    val edited = draft ?: editor
    FlowSheet(
        onDismissRequest = onDismiss,
        maxHeight = MaterialTheme.flow.tallSheetMaxHeight,
        horizontalPadding = MaterialTheme.flow.sheetWidePadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.edit_sentence), style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = edited,
            onValueChange = onDraftChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.sentence)) },
            minLines = 3,
        )
        Text(selectionHint, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                enabled = edited.selection.start != edited.selection.end,
                onClick = { onUseSelection(edited) },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) { Text(useSelectionLabel) }
            Button(
                onClick = { onDone(edited) },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.done)) }
        }
    }
}

internal fun useCardSentenceSelectionAsTarget(
    edited: TextFieldValue,
    updateEditor: (TextFieldValue) -> Unit,
    commitSentence: (String, () -> Unit) -> Boolean,
    selectTarget: (TargetSpan?) -> Unit,
    onCommitted: () -> Unit,
) {
    if (edited.text.isBlank() || !commitSentence(edited.text) { updateEditor(edited) }) return
    val selected = TargetSpan.fromSelection(edited.text, edited.selection.start, edited.selection.end)
    selectTarget(selected)
    onCommitted()
}

internal fun commitCardSentenceEdit(
    edited: TextFieldValue,
    updateEditor: (TextFieldValue) -> Unit,
    commitSentence: (String, () -> Unit) -> Boolean,
    onCommitted: () -> Unit,
) {
    if (edited.text.isBlank() || !commitSentence(edited.text) { updateEditor(edited) }) return
    onCommitted()
}
