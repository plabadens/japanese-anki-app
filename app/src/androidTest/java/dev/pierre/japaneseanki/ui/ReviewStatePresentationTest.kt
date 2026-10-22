package dev.pierre.japaneseanki.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.pierre.japaneseanki.PitchAccent
import dev.pierre.japaneseanki.TargetSpan
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReviewStatePresentationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun capturesAllReviewStatusStatesWithoutMovingContentOrSend() {
        val audio = mutableStateOf<ReviewAudioPresentation>(
            ReviewAudioPresentation.Advisory(
                headline = "Sentence audio setup needed",
                message = "No Google Cloud API key is saved.",
                actionLabel = "Open Settings",
            ),
        )
        val sendHint = mutableStateOf<String?>("Sentence audio setup needed")
        val sendEnabled = mutableStateOf(false)
        val output = File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "review-state-evidence",
        ).apply { mkdirs() }
        val sentence = "灯籠に触らないで下さい"

        composeRule.setContent {
            MaterialTheme {
                ReviewCard(
                    sentence = sentence,
                    target = TargetSpan(0, 2),
                    sentenceReading = "とうろう",
                    furigana = emptyList(),
                    vocabulary = "灯籠",
                    vocabularyReading = "とうろう",
                    pitch = PitchAccent.create("とうろう", 2),
                    definition = "garden lantern; hanging lantern",
                    translation = "Please do not touch the lantern.",
                    notes = "",
                    tags = emptyList(),
                    imagePath = null,
                    imageStatus = null,
                    pitchHasAlternatives = false,
                    pitchInfoExpanded = false,
                    onTogglePitchInfo = {},
                    onEdit = {},
                    onTagsChange = {},
                    onNotesChange = {},
                    sentenceAudio = audio.value,
                    vocabularyAudio = ReviewAudioPresentation.Ready,
                    onRetrySentenceAudio = {},
                    onToggleVocabularyAudio = {},
                    sentenceAudioAction = "Retry",
                    sendPresentation = ReviewSendPresentation.Ready,
                    onSend = if (sendEnabled.value) ({}) else null,
                    sendHint = sendHint.value,
                    sendHintActionLabel = "Open Settings",
                    sendHintRole = ReviewStatusRole.Warning,
                    onSendHintAction = {},
                    onOpenSettings = {},
                )
            }
        }

        val sentenceNode = composeRule.onNodeWithContentDescription(sentence)
        val wordNode = composeRule.onNodeWithText("garden lantern; hanging lantern")
        val sendNode = composeRule.onNodeWithText("Send to AnkiDroid")
        val sentenceBounds = sentenceNode.fetchSemanticsNode().boundsInRoot
        val wordBounds = wordNode.fetchSemanticsNode().boundsInRoot
        val sendBounds = sendNode.fetchSemanticsNode().boundsInRoot

        sendNode.assertIsNotEnabled()
        composeRule.onNodeWithText("Sentence audio setup needed").assertIsDisplayed()
        composeRule.onNodeWithText("Open Settings").assertIsDisplayed()
        assertTrue(
            "pre-send status belongs directly above the disabled Send action",
            composeRule.onNodeWithText("Sentence audio setup needed").fetchSemanticsNode().boundsInRoot.bottom <= sendBounds.top,
        )
        capture(output, "no-credential-blocked.png")

        audio.value = ReviewAudioPresentation.Failed(
            "Sentence audio not generated: Audio generation was interrupted",
        )
        sendHint.value = null
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Retry").assertIsDisplayed()
        capture(output, "generation-interrupted.png")
        assertEquals(sentenceBounds, sentenceNode.fetchSemanticsNode().boundsInRoot)
        assertEquals(wordBounds, wordNode.fetchSemanticsNode().boundsInRoot)
        assertEquals(sendBounds, sendNode.fetchSemanticsNode().boundsInRoot)

        audio.value = ReviewAudioPresentation.Failed("Sentence audio could not be played on this device")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Retry").assertIsDisplayed()
        capture(output, "playback-failure.png")
        assertEquals(sentenceBounds, sentenceNode.fetchSemanticsNode().boundsInRoot)
        assertEquals(wordBounds, wordNode.fetchSemanticsNode().boundsInRoot)
        assertEquals(sendBounds, sendNode.fetchSemanticsNode().boundsInRoot)

        audio.value = ReviewAudioPresentation.Ready
        sendHint.value = "Translation is required before Send."
        composeRule.waitForIdle()
        sendNode.assertIsNotEnabled()
        composeRule.onNodeWithText("Translation is required before Send.").assertIsDisplayed()
        capture(output, "blocked-send.png")
        assertEquals(sentenceBounds, sentenceNode.fetchSemanticsNode().boundsInRoot)
        assertEquals(wordBounds, wordNode.fetchSemanticsNode().boundsInRoot)
        assertEquals(sendBounds, sendNode.fetchSemanticsNode().boundsInRoot)

        sendHint.value = null
        sendEnabled.value = true
        composeRule.waitForIdle()
        sendNode.assertIsEnabled()
        capture(output, "ready-send.png")
        assertEquals(sentenceBounds, sentenceNode.fetchSemanticsNode().boundsInRoot)
        assertEquals(wordBounds, wordNode.fetchSemanticsNode().boundsInRoot)
        assertEquals(sendBounds, sendNode.fetchSemanticsNode().boundsInRoot)

        val evidenceHoldSeconds = InstrumentationRegistry.getArguments()
            .getString("evidence_hold_seconds")?.toLongOrNull() ?: 0L
        if (evidenceHoldSeconds > 0) Thread.sleep(evidenceHoldSeconds * 1_000)
    }

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
