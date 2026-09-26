package dev.pierre.japaneseanki

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.isSystemInDarkTheme
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextRange
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue

class MainActivity : ComponentActivity() {
    private val sharedText = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedText.value = textFromIntent(intent)
        val preferences = getSharedPreferences("draft", MODE_PRIVATE)
        val ankiDroidInstalled = isAnkiDroidInstalled()
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
            var pitchAccentText by remember { mutableStateOf(preferences.getString("pitch_accent", "").orEmpty()) }
            val pitch = pitchAccentText.toIntOrNull()?.let { PitchAccent.create(vocabularyReading, it) }
            var definition by remember { mutableStateOf(preferences.getString("definition", "").orEmpty()) }
            var sentenceReading by remember { mutableStateOf(preferences.getString("sentence_reading", "").orEmpty()) }
            var leaveSentenceTargetUnannotated by remember { mutableStateOf(preferences.getBoolean("leave_sentence_target_unannotated", false)) }
            var leaveVocabularyUnannotated by remember { mutableStateOf(preferences.getBoolean("leave_vocabulary_unannotated", false)) }
            var dependentFieldsStale by remember { mutableStateOf(preferences.getBoolean("dependent_fields_stale", false)) }
            var translation by remember { mutableStateOf(preferences.getString("translation", "").orEmpty()) }
            var notes by remember { mutableStateOf(preferences.getString("notes", "").orEmpty()) }
            var tags by remember { mutableStateOf(preferences.getString("tags", "").orEmpty()) }
            var showReplace by remember { mutableStateOf(false) }
            val incoming = sharedText.value
            val hasTargetData = vocabulary.isNotBlank() || vocabularyReading.isNotBlank() ||
                pitchAccentText.isNotBlank() || definition.isNotBlank() || sentenceReading.isNotBlank()

            androidx.compose.runtime.LaunchedEffect(incoming) {
                if (incoming != null) {
                    if (editor.text.isBlank()) {
                        editor = TextFieldValue(incoming, selection = TextRange(incoming.length))
                        if (hasTargetData) dependentFieldsStale = true
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
                            if (hasTargetData) dependentFieldsStale = true
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

            val noteDraft = ManualNoteDraft(
                sentence = editor.text,
                target = target,
                sentenceReading = sentenceReading,
                leaveSentenceTargetUnannotated = leaveSentenceTargetUnannotated,
                vocabulary = vocabulary,
                vocabularyReading = vocabularyReading,
                leaveVocabularyUnannotated = leaveVocabularyUnannotated,
                definition = definition,
                pitch = pitch,
                dependentFieldsStale = dependentFieldsStale,
                translation = translation,
                notes = notes,
            )
            val validationErrors = noteDraft.validationErrors()

            androidx.compose.runtime.LaunchedEffect(
                editor.text, editor.selection, target, vocabulary, vocabularyReading, pitchAccentText, definition,
                sentenceReading, leaveSentenceTargetUnannotated, leaveVocabularyUnannotated,
                dependentFieldsStale, translation, notes, tags,
            ) {
                preferences.edit()
                    .putString("sentence", editor.text)
                    .putInt("selection_start", editor.selection.min)
                    .putInt("selection_end", editor.selection.max)
                    .putInt("target_start", target?.start ?: -1)
                    .putInt("target_end", target?.end ?: -1)
                    .putString("vocabulary", vocabulary)
                    .putString("vocabulary_reading", vocabularyReading)
                    .putString("pitch_accent", pitchAccentText)
                    .putString("definition", definition)
                    .putString("sentence_reading", sentenceReading)
                    .putBoolean("leave_sentence_target_unannotated", leaveSentenceTargetUnannotated)
                    .putBoolean("leave_vocabulary_unannotated", leaveVocabularyUnannotated)
                    .putBoolean("dependent_fields_stale", dependentFieldsStale)
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
                                    if (target != null || hasTargetData) dependentFieldsStale = true
                                    target = null
                                }
                                editor = updated
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Japanese sentence") },
                            minLines = 3,
                        )
                        OutlinedButton(
                            onClick = {
                                val newTarget = TargetSpan.fromSelection(
                                    editor.text,
                                    editor.selection.start,
                                    editor.selection.end,
                                )
                                if (newTarget != target && hasTargetData) dependentFieldsStale = true
                                target = newTarget
                                if (vocabulary.isBlank()) vocabulary = newTarget?.textIn(editor.text).orEmpty()
                            },
                            enabled = editor.selection.start != editor.selection.end,
                        ) { Text("Use selected text as target") }
                        Text(
                            target?.textIn(editor.text)?.let { "Target: $it" }
                                ?: "Select text in the sentence to choose a target.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        if (dependentFieldsStale && target?.textIn(editor.text) == null) {
                            Text(
                                "Previous vocabulary and reading work was kept. Select a new target to review it.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        if (target?.textIn(editor.text) != null) {
                            Text("Vocabulary", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = vocabulary,
                                onValueChange = {
                                    if (it != vocabulary && hasTargetData) dependentFieldsStale = true
                                    vocabulary = it
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Lemma or manual vocabulary") },
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = vocabularyReading,
                                onValueChange = {
                                    if (it != vocabularyReading && (pitchAccentText.isNotBlank() || definition.isNotBlank())) {
                                        dependentFieldsStale = true
                                    }
                                    vocabularyReading = it
                                    leaveVocabularyUnannotated = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Vocabulary reading") },
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = pitchAccentText,
                                onValueChange = { value ->
                                    if (value.all(Char::isDigit)) pitchAccentText = value
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Pitch accent position (optional, 0 = heiban)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                            )
                            when {
                                pitch != null -> PitchDiagram(pitch)
                                pitchAccentText.isNotBlank() -> Text(
                                    "Enter a kana reading and an accent position from 0 to its mora count.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Row {
                                Checkbox(
                                    checked = leaveVocabularyUnannotated,
                                    onCheckedChange = {
                                        leaveVocabularyUnannotated = it
                                        if (it && vocabularyReading.isNotBlank()) dependentFieldsStale = true
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
                                        if (it && sentenceReading.isNotBlank()) dependentFieldsStale = true
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
                            if (dependentFieldsStale) {
                                Text(
                                    "Sentence or target changed. Existing manual values were kept; review them before using this note.",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                OutlinedButton(onClick = { dependentFieldsStale = false }) {
                                    Text("I've reviewed these fields")
                                }
                            }
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
                            if (ankiDroidInstalled) "AnkiDroid detected. API permission, model, and deck setup are not connected yet."
                            else "AnkiDroid not detected. Install it before setting up export.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "Draft autosaves on this device. Dictionary lookup is not connected yet.",
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

    private fun isAnkiDroidInstalled(): Boolean = try {
        packageManager.getPackageInfo("com.ichi2.anki", PackageManager.PackageInfoFlags.of(0))
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
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

@Composable
private fun PitchDiagram(pitch: PitchAccent) {
    val color = MaterialTheme.colorScheme.primary
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .semantics { contentDescription = "Pitch contour: ${pitch.contourDescription}" },
        ) {
            val margin = 12.dp.toPx()
            val highY = 8.dp.toPx()
            val lowY = size.height - 8.dp.toPx()
            val step = if (pitch.morae.size < 2) 0f
            else (size.width - margin * 2) / (pitch.morae.size - 1)
            val points = pitch.highLevels.mapIndexed { index, high ->
                Offset(margin + step * index, if (high) highY else lowY)
            }
            points.zipWithNext().forEach { (start, end) ->
                drawLine(color, start, end, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            }
            points.forEach { point -> drawCircle(color, radius = 3.dp.toPx(), center = point) }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            pitch.morae.forEach { mora -> Text(mora, color = color) }
        }
        Text("Accent ${pitch.accentPosition}", style = MaterialTheme.typography.bodySmall)
    }
}
