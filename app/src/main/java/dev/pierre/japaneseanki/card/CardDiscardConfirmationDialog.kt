package dev.pierre.japaneseanki.card

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import dev.pierre.japaneseanki.ui.FlowDialog

@Composable
internal fun CardDiscardConfirmationDialog(
    visible: Boolean,
    hasWork: Boolean,
    enabled: Boolean,
    title: String,
    workMessage: String,
    emptyMessage: String,
    discardLabel: String,
    keepWorkingLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (!visible) return
    FlowDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(if (hasWork) workMessage else emptyMessage) },
        confirmButton = {
            TextButton(enabled = enabled, onClick = onConfirm) { Text(discardLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(keepWorkingLabel) }
        },
    )
}
