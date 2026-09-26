package dev.pierre.japaneseanki

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
            var vocabulary by remember { mutableStateOf(preferences.getString("vocabulary", "").orEmpty()) }
            var vocabularyReading by remember { mutableStateOf(preferences.getString("vocabulary_reading", "").orEmpty()) }
            var definition by remember { mutableStateOf(preferences.getString("definition", "").orEmpty()) }
            var sentenceReading by remember { mutableStateOf(preferences.getString("sentence_reading", "").orEmpty()) }
            var leaveSentenceTargetUnannotated by remember { mutableStateOf(preferences.getBoolean("leave_sentence_target_unannotated", false)) }
            var leaveVocabularyUnannotated by remember { mutableStateOf(preferences.getBoolean("leave_vocabulary_unannotated", false)) }
            var translation by remember { mutableStateOf(preferences.getString("translation", "").orEmpty()) }
            var notes by remember { mutableStateOf(preferences.getString("notes", "").orEmpty()) }
            var tags by remember { mutableStateOf(preferences.getString("tags", "").orEmpty()) }
            var showReplace by remember { mutableStateOf(false) }
            val incoming = sharedText.value

            androidx.compose.runtime.LaunchedEffect(incoming) {
                if (incoming != null) {
                    if (editor.text.isBlank()) {
                        editor = TextFieldValue(incoming, selection = TextRange(incoming.length))
                        target = null
                        vocabulary = ""
                        vocabularyReading = ""
                        definition = ""
                        sentenceReading = ""
                        leaveSentenceTargetUnannotated = false
                        leaveVocabularyUnannotated = false
                        translation = ""
                        notes = ""
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
                            vocabulary = ""
                            vocabularyReading = ""
                            definition = ""
                            sentenceReading = ""
                            leaveSentenceTargetUnannotated = false
                            leaveVocabularyUnannotated = false
                            translation = ""
                            notes = ""
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

            val noteDraft = ManualNoteDraft(
                sentence = editor.text,
                target = target,
                sentenceReading = sentenceReading,
                leaveSentenceTargetUnannotated = leaveSentenceTargetUnannotated,
                vocabulary = vocabulary,
                vocabularyReading = vocabularyReading,
                leaveVocabularyUnannotated = leaveVocabularyUnannotated,
                definition = definition,
                translation = translation,
                notes = notes,
            )
            val validationErrors = noteDraft.validationErrors()

            androidx.compose.runtime.LaunchedEffect(
                editor.text, editor.selection, target, vocabulary, vocabularyReading, definition,
                sentenceReading, leaveSentenceTargetUnannotated, leaveVocabularyUnannotated,
                translation, notes, tags,
            ) {
                preferences.edit()
                    .putString("sentence", editor.text)
                    .putInt("selection_start", editor.selection.min)
                    .putInt("selection_end", editor.selection.max)
                    .putInt("target_start", target?.start ?: -1)
                    .putInt("target_end", target?.end ?: -1)
                    .putString("vocabulary", vocabulary)
                    .putString("vocabulary_reading", vocabularyReading)
                    .putString("definition", definition)
                    .putString("sentence_reading", sentenceReading)
                    .putBoolean("leave_sentence_target_unannotated", leaveSentenceTargetUnannotated)
                    .putBoolean("leave_vocabulary_unannotated", leaveVocabularyUnannotated)
                    .putString("translation", translation)
                    .putString("notes", notes)
                    .putString("tags", tags)
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
                                if (updated.text != editor.text) {
                                    target = null
                                    vocabulary = ""
                                    vocabularyReading = ""
                                    definition = ""
                                    sentenceReading = ""
                                    leaveSentenceTargetUnannotated = false
                                    leaveVocabularyUnannotated = false
                                }
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
                                vocabulary = target?.textIn(editor.text).orEmpty()
                                vocabularyReading = ""
                                definition = ""
                                sentenceReading = ""
                                leaveSentenceTargetUnannotated = false
                                leaveVocabularyUnannotated = false
                            },
                            enabled = editor.selection.start != editor.selection.end,
                        ) { Text("Use selected text as target") }
                        Text(
                            target?.textIn(editor.text)?.let { "Target: $it" }
                                ?: "Select text in the sentence to choose a target.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        if (target?.textIn(editor.text) != null) {
                            Text("Vocabulary", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = vocabulary,
                                onValueChange = { vocabulary = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Lemma or manual vocabulary") },
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = vocabularyReading,
                                onValueChange = { vocabularyReading = it; leaveVocabularyUnannotated = false },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Vocabulary reading") },
                                singleLine = true,
                            )
                            Row {
                                Checkbox(
                                    checked = leaveVocabularyUnannotated,
                                    onCheckedChange = {
                                        leaveVocabularyUnannotated = it
                                        if (it) vocabularyReading = ""
                                    },
                                )
                                Text("Leave vocabulary without furigana")
                            }
                            OutlinedTextField(
                                value = sentenceReading,
                                onValueChange = { sentenceReading = it; leaveSentenceTargetUnannotated = false },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Selected sentence span reading") },
                                singleLine = true,
                            )
                            Row {
                                Checkbox(
                                    checked = leaveSentenceTargetUnannotated,
                                    onCheckedChange = {
                                        leaveSentenceTargetUnannotated = it
                                        if (it) sentenceReading = ""
                                    },
                                )
                                Text("Leave selected sentence span unannotated")
                            }
                            OutlinedTextField(
                                value = definition,
                                onValueChange = { definition = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("English definition (required)") },
                                minLines = 2,
                            )
                            OutlinedTextField(
                                value = translation,
                                onValueChange = { translation = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Sentence translation (optional)") },
                                minLines = 2,
                            )
                            OutlinedTextField(
                                value = notes,
                                onValueChange = { notes = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Notes (optional)") },
                                minLines = 2,
                            )
                            OutlinedTextField(
                                value = tags,
                                onValueChange = { tags = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Tags (optional)") },
                                singleLine = true,
                            )
                        }
                        if (target != null) {
                            if (validationErrors.isEmpty()) {
                                Text("Required note text is complete.", style = MaterialTheme.typography.bodyMedium)
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Still needed", style = MaterialTheme.typography.titleSmall)
                                    validationErrors.forEach { issue ->
                                        Text("• $issue", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                        Text(
                            "Draft autosaves on this device. Dictionary lookup and AnkiDroid setup are not connected yet.",
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
