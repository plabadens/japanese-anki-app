package dev.pierre.japaneseanki.ui

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.pierre.japaneseanki.DictionaryEntry
import dev.pierre.japaneseanki.DictionaryMeaningChoice
import dev.pierre.japaneseanki.DictionarySense
import dev.pierre.japaneseanki.JapaneseToken
import dev.pierre.japaneseanki.PitchAccent
import dev.pierre.japaneseanki.R
import dev.pierre.japaneseanki.TargetSpan
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardSurfaceBehaviorTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun wordCardAndMeaningSheetKeepTheSelectedChoiceAcrossDismissal() {
        val selected = mutableStateOf(choice(index = 0, definition = "garden lantern"))
        val choices = mutableStateOf(listOf(selected.value))
        val showMeaningSheet = mutableStateOf(false)
        val missMode = mutableStateOf(0)
        val fontScale = mutableStateOf(1f)
        val output = File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "card-surface-evidence",
        ).apply { mkdirs() }

        composeRule.setContent {
            val baseConfiguration = LocalConfiguration.current
            val scaledConfiguration = remember(baseConfiguration, fontScale.value) {
                Configuration(baseConfiguration).apply { this.fontScale = fontScale.value }
            }
            val baseDensity = LocalDensity.current
            val scaledDensity = remember(baseDensity, fontScale.value) {
                androidx.compose.ui.unit.Density(baseDensity.density, fontScale.value)
            }
            CompositionLocalProvider(
                LocalConfiguration provides scaledConfiguration,
                LocalDensity provides scaledDensity,
            ) {
                MaterialTheme {
                    Box(Modifier.fillMaxSize()) {
                        CardScreen(
                            sentence = "灯籠に触らないで下さい",
                            tokens = listOf(
                                JapaneseToken(
                                    span = TargetSpan(0, 2),
                                    surface = "灯籠",
                                    lemma = "灯籠",
                                    surfaceReading = "とうろう",
                                    lemmaReading = "とうろう",
                                    partOfSpeech = "名詞-普通名詞",
                                ),
                            ),
                            selectedSpan = TargetSpan(0, 2),
                            selectionEnabled = true,
                            onSelectSpan = {},
                            furigana = emptyList(),
                            onEditSentence = {},
                            translation = "Please do not touch the lantern.",
                            vocabulary = selected.value.entry.expression,
                            vocabularyReading = selected.value.entry.reading,
                            partOfSpeech = "noun",
                            definition = selected.value.definition,
                            pitch = if (missMode.value == 0) PitchAccent.create("とうろう", 2) else null,
                            pitchStatus = if (missMode.value == 2) stringResource(R.string.no_pitch) else null,
                            pitchPending = missMode.value == 1,
                            wordStatus = when (missMode.value) {
                                1 -> stringResource(R.string.no_entry)
                                2 -> stringResource(R.string.no_recording)
                                else -> null
                            },
                            wordAudioEnabled = missMode.value == 0,
                            meaningAvailable = choices.value.size > 1,
                            onChangeMeaning = { showMeaningSheet.value = true },
                            imagePath = null,
                            imageFocusY = 0.5f,
                            notes = "",
                            tags = "",
                            onNotesChange = {},
                            onTagsChange = {},
                            sentenceAudio = ReviewAudioPresentation.Ready,
                            wordAudio = if (missMode.value == 0) {
                                ReviewAudioPresentation.Ready
                            } else {
                                ReviewAudioPresentation.SourceMissing
                            },
                            onToggleSentenceAudio = {},
                            onRetrySentenceAudio = {},
                            onToggleWordAudio = {},
                            onRetryWordAudio = {},
                            hint = "",
                            hintPreparing = false,
                            actionLabel = "Continue",
                            actionEnabled = true,
                            actionLoading = false,
                            onAction = {},
                            statusMessage = null,
                            statusContent = {},
                        )
                        if (showMeaningSheet.value) {
                            DictionaryMeaningSheet(
                                choices = choices.value,
                                enabled = true,
                                onDismissRequest = { showMeaningSheet.value = false },
                                onSelect = {
                                    selected.value = it
                                    showMeaningSheet.value = false
                                },
                            )
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithText("garden lantern").assertIsDisplayed()
        composeRule.onNodeWithText("noun").assertIsDisplayed()
        composeRule.onAllNodesWithText("Change meaning").assertCountEquals(0)
        composeRule.onAllNodesWithText("Load more dictionary matches").assertCountEquals(0)
        composeRule.onAllNodesWithText("Other pitch patterns").assertCountEquals(0)
        capture(output, "resolved-silent.png")

        choices.value = listOf(
            selected.value,
            choice(index = 1, definition = "paper lantern"),
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Change meaning").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Choose meaning").assertIsDisplayed()
        composeRule.onNodeWithText("paper lantern").assertIsDisplayed()
        capture(output, "ambiguity-sheet.png")

        pressBack()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Choose meaning").assertCountEquals(0)
        composeRule.onNodeWithText("garden lantern").assertIsDisplayed()
        capture(output, "dismissed-preserves-choice.png")

        composeRule.onNodeWithText("Change meaning").performClick()
        composeRule.onNodeWithText("paper lantern").performClick()
        composeRule.waitForIdle()
        assertEquals("paper lantern", selected.value.definition)
        composeRule.onAllNodesWithText("Choose meaning").assertCountEquals(0)
        composeRule.onNodeWithText("Change meaning").assertIsDisplayed()

        missMode.value = 1
        composeRule.waitForIdle()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.no_entry)).assertIsDisplayed()
        capture(output, "source-miss-no-entry.png")

        missMode.value = 2
        composeRule.waitForIdle()
        val noRecording = composeRule.activity.getString(R.string.no_recording)
        composeRule.onNodeWithText(noRecording).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.no_pitch)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(noRecording).assertIsNotEnabled()
        composeRule.onAllNodesWithText("Retry").assertCountEquals(0)
        capture(output, "source-miss-no-pitch-or-recording.png")

        fontScale.value = 2f
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Continue").assertIsDisplayed()
        capture(output, "source-miss-200-percent.png")
    }

    private fun choice(index: Int, definition: String): DictionaryMeaningChoice {
        val senses = listOf(sense("garden lantern"), sense("paper lantern"))
        val entry = DictionaryEntry(1L, "灯籠", "とうろう", senses)
        return DictionaryMeaningChoice(entry, index, definition)
    }

    private fun sense(gloss: String) = DictionarySense(
        glosses = listOf(gloss),
        partsOfSpeech = listOf("n"),
        notes = emptyList(),
    )

    private fun capture(directory: File, name: String) {
        composeRule.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val file = File(directory, name)
        file.outputStream().use { output ->
            assertTrue("Could not encode screenshot $name", bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        assertTrue("Screenshot was not written: $file", file.isFile && file.length() > 0)
    }
}
