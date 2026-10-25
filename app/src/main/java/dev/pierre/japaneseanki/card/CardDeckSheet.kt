package dev.pierre.japaneseanki.card

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.pierre.japaneseanki.AnkiDeck
import dev.pierre.japaneseanki.ui.FlowSheet
import dev.pierre.japaneseanki.ui.flow

@Composable
internal fun CardDeckSheet(
    visible: Boolean,
    decks: List<AnkiDeck>,
    title: String,
    emptyMessage: String,
    onDismiss: () -> Unit,
    onSelect: (AnkiDeck) -> Unit,
) {
    if (!visible) return
    FlowSheet(
        onDismissRequest = onDismiss,
        horizontalPadding = MaterialTheme.flow.sheetWidePadding,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (decks.isEmpty()) {
            Text(
                emptyMessage,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        decks.forEach { deck ->
            TextButton(
                onClick = { onSelect(deck) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(deck.name) }
        }
    }
}
