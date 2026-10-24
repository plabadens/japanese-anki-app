package dev.pierre.japaneseanki.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [37], qualifiers = "w390dp-h844dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChangeFeedbackTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dispatchesSynchronouslyOnlyForNewEnabledKeys() {
        val events = mutableListOf<TouchFeedbackEvent>()
        val enabled = mutableStateOf(true)
        val feedback = AtomicReference<((Any?) -> Unit)?>(null)
        val compositionCount = AtomicInteger()
        val dispatcher = TouchFeedbackDispatcher { event -> events += event }
        composeRule.setContent {
            compositionCount.incrementAndGet()
            CompositionLocalProvider(LocalTouchFeedbackDispatcher provides dispatcher) {
                feedback.set(
                    rememberChangeFeedback(
                        event = TouchFeedbackEvent.WORD_SELECTION,
                        enabled = enabled.value,
                    ),
                )
            }
        }

        composeRule.runOnIdle {
            val dispatch = checkNotNull(feedback.get())
            val before = compositionCount.get()
            dispatch("first")
            dispatch("first")
            dispatch("second")
            assertEquals(listOf(TouchFeedbackEvent.WORD_SELECTION, TouchFeedbackEvent.WORD_SELECTION), events)
            assertEquals("Feedback must not require recomposition", before, compositionCount.get())
        }

        enabled.value = false
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            checkNotNull(feedback.get())("suppressed")
            assertEquals(2, events.size)
        }

        enabled.value = true
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            checkNotNull(feedback.get())("suppressed")
            assertEquals("A disabled call must not record its key", 3, events.size)
        }
    }
}