package dev.pierre.japaneseanki

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.isSystemInDarkTheme
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

class MainActivity : ComponentActivity() {
    private val sharedText = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedText.value = textFromIntent(intent)
        val preferences = getSharedPreferences("draft", MODE_PRIVATE)
        setContent {
            var editor by remember {
                mutableStateOf(
                    TextFieldValue(
                        text = preferences.getString("sentence", "").orEmpty(),
                        selection = TextRange(
                            preferences.getInt("selection_start", 0),
                            preferences.getInt("selection_end", 0),
                        ).coerceIn(0, preferences.getString("sentence", "").orEmpty().length),
                    ),
                )
            }
            var target by remember {
                mutableStateOf(
                    TargetSpan(
                        preferences.getInt("target_start", -1),
                        preferences.getInt("target_end", -1),
                    ).takeIf { it.textIn(editor.text) != null },
                )
            }
            var showReplace by remember { mutableStateOf(false) }
            val incoming = sharedText.value

            androidx.compose.runtime.LaunchedEffect(incoming) {
                if (incoming != null) {
                    if (editor.text.isBlank()) {
                        editor = TextFieldValue(incoming, selection = TextRange(incoming.length))
                        target = null
                        sharedText.value = null
                    } else {
                        showReplace = true
                    }
                }
            }
            if (showReplace && incoming != null) {
                AlertDialog(
                    onDismissRequest = { showReplace = false; sharedText.value = null },
                    title = { Text("Replace current sentence?") },
                    text = { Text("The current draft will be replaced by the shared text.") },
                    confirmButton = {
                        TextButton(onClick = {
                            editor = TextFieldValue(incoming, selection = TextRange(incoming.length))
                            target = null
                            showReplace = false
                            sharedText.value = null
                        }) { Text("Replace") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showReplace = false; sharedText.value = null }) {
                            Text("Keep draft")
                        }
                    },
                )
            }

            androidx.compose.runtime.LaunchedEffect(editor.text, editor.selection, target) {
                preferences.edit()
                    .putString("sentence", editor.text)
                    .putInt("selection_start", editor.selection.min)
                    .putInt("selection_end", editor.selection.max)
                    .putInt("target_start", target?.start ?: -1)
                    .putInt("target_end", target?.end ?: -1)
                    .apply()
            }

            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("Japanese Anki", style = MaterialTheme.typography.headlineMedium)
                        OutlinedTextField(
                            value = editor,
                            onValueChange = { updated ->
                                if (updated.text != editor.text) target = null
                                editor = updated
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Japanese sentence") },
                            minLines = 3,
                        )
                        OutlinedButton(
                            onClick = {
                                target = TargetSpan.fromSelection(
                                    editor.text,
                                    editor.selection.start,
                                    editor.selection.end,
                                )
                            },
                            enabled = editor.selection.start != editor.selection.end,
                        ) { Text("Use selected text as target") }
                        Text(
                            target?.textIn(editor.text)?.let { "Target: $it" }
                                ?: "Select text in the sentence to choose a target.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            "Draft autosaves on this device. Dictionary enrichment and AnkiDroid setup are next.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(onClick = {}, enabled = false) {
                            Text("Send to AnkiDroid — setup required")
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedText.value = textFromIntent(intent)
    }

    private fun textFromIntent(intent: Intent?): String? {
        if (intent == null) return null
        val value = when (intent.action) {
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
            Intent.ACTION_SEND -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
            else -> null
        }
        return value?.toString()?.takeIf(String::isNotBlank)
    }
}

private fun TextRange.coerceIn(min: Int, max: Int): TextRange =
    TextRange(start.coerceIn(min, max), end.coerceIn(min, max))
