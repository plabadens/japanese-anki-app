package dev.pierre.japaneseanki.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import dev.pierre.japaneseanki.JapaneseToken
import dev.pierre.japaneseanki.TargetSpan
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [37], qualifiers = "w390dp-h844dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeLaneSmokeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun cardSentenceHasNumericBoundsAndKeepsThemWhenAudioFails() {
        val sentenceAudio = mutableStateOf<ReviewAudioPresentation>(ReviewAudioPresentation.Ready)

        composeRule.setContent {
            MaterialTheme {
                CardScreen(
                    sentence = "猫は好きです。",
                    tokens = listOf(
                        JapaneseToken(
                            span = TargetSpan(0, 1),
                            surface = "猫",
                            lemma = "猫",
                            surfaceReading = "ねこ",
                            lemmaReading = "ねこ",
                            partOfSpeech = "名詞-普通名詞",
                        ),
                    ),
                    selectedSpan = TargetSpan(0, 1),
                    selectionEnabled = true,
                    onSelectSpan = {},
                    furigana = emptyList(),
                    onEditSentence = {},
                    translation = "I like cats.",
                    vocabulary = "猫",
                    vocabularyReading = "ねこ",
                    partOfSpeech = "noun",
                    definition = "cat",
                    pitch = null,
                    pitchStatus = null,
                    pitchPending = false,
                    wordStatus = null,
                    wordAudioEnabled = true,
                    meaningAvailable = false,
                    onChangeMeaning = {},
                    imagePath = null,
                    imageFocusY = 0.5f,
                    notes = "",
                    tags = "",
                    onNotesChange = {},
                    onTagsChange = {},
                    sentenceAudio = sentenceAudio.value,
                    wordAudio = ReviewAudioPresentation.Ready,
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
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        val sentence = composeRule.onNodeWithTag("sentence-layout")
        sentence.assertWidthIsEqualTo(310.dp)
        sentence.assertHeightIsEqualTo(64.dp)
        val readyBounds = sentence.fetchSemanticsNode().boundsInRoot

        sentenceAudio.value = ReviewAudioPresentation.Failed("Synthesis timed out")
        composeRule.waitForIdle()

        sentence.assertWidthIsEqualTo(310.dp)
        sentence.assertHeightIsEqualTo(64.dp)
        assertEquals(readyBounds, sentence.fetchSemanticsNode().boundsInRoot)
    }

    @Test
    fun captureScreenShowsJapaneseAt390By844() {
        composeRule.setContent { JapaneseCaptureScreen() }

        composeRule.onNodeWithTag("capture-screen").assertIsDisplayed()
        composeRule.onNodeWithText("古い本を読みました。").assertIsDisplayed()
        captureRoboImage("src/test/screenshots/capture-screen-japanese.png") {
            JapaneseCaptureScreen()
        }
    }

    @androidx.compose.runtime.Composable
    private fun JapaneseCaptureScreen() {
        FlowTheme(darkTheme = false) {
            Box(Modifier.fillMaxSize().testTag("capture-screen")) {
                InputScreen(
                    captureMode = "Type",
                    onCaptureModeChange = {},
                    editor = TextFieldValue("古い本を読みました。"),
                    onEditorChange = {},
                    onContinue = {},
                    onOpenGallery = {},
                    onChoosePhoto = {},
                    onCaptured = {},
                    onCameraError = {},
                    outputDirectory = File("/unused-capture-golden"),
                )
            }
        }
    }
}
