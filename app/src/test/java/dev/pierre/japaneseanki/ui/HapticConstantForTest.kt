package dev.pierre.japaneseanki.ui

import android.view.HapticFeedbackConstants
import org.junit.Assert.assertEquals
import org.junit.Test

class HapticConstantForTest {
    @Test
    fun everyEventMapsCorrectlyOnApi29And30() {
        val shared = mapOf(
            TouchFeedbackEvent.WORD_SELECTION to HapticFeedbackConstants.CLOCK_TICK,
            TouchFeedbackEvent.MEANING_SELECTION to HapticFeedbackConstants.CLOCK_TICK,
            TouchFeedbackEvent.MODE_SELECTION to HapticFeedbackConstants.CLOCK_TICK,
            TouchFeedbackEvent.SHUTTER_ACCEPTED to HapticFeedbackConstants.VIRTUAL_KEY,
        )
        TouchFeedbackEvent.entries.forEach { event ->
            val api29Expected = shared[event] ?: HapticFeedbackConstants.CLOCK_TICK
            assertEquals("API 29: $event", api29Expected, hapticConstantFor(event, 29))

            val api30Expected = shared[event] ?: HapticFeedbackConstants.CONFIRM
            assertEquals("API 30: $event", api30Expected, hapticConstantFor(event, 30))
        }
    }
}
