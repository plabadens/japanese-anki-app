package dev.pierre.japaneseanki.card

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.pierre.japaneseanki.OcrBlock
import dev.pierre.japaneseanki.R
import dev.pierre.japaneseanki.ui.FlowSheet

@Composable
internal fun SentenceOcrChoicesSheet(
    visible: Boolean,
    choices: List<OcrBlock>,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onChoose: (OcrBlock) -> Unit,
) {
    if (!visible || choices.size <= 1) return
    FlowSheet(
        onDismissRequest = onDismiss,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.choose_sentence), style = MaterialTheme.typography.titleLarge)
        choices.forEach { block ->
            OutlinedButton(
                enabled = enabled,
                onClick = { onChoose(block) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(block.text.trim()) }
        }
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.cancel)) }
    }
}
