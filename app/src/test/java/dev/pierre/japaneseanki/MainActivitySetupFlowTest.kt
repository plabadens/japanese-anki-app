package dev.pierre.japaneseanki

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The first-run dialog must be wired into MainActivity, not merely render in isolation. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [37], qualifiers = "w390dp-h844dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainActivitySetupFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun freshLaunchShowsTheSetupFlowInItsFullScreenDialog() {
        composeRule.onNodeWithText("Sentence audio").assertIsDisplayed()
        composeRule.onNodeWithText("Install AnkiDroid").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Finish setup").assertIsNotEnabled()
    }
}
