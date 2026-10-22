package dev.pierre.japaneseanki.ui

import android.content.Context
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlatformTouchFeedbackTest {
    @Test
    fun everyCommittedEventMapsToOnePlatformFeedbackRequest() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val owner = TestLifecycleOwner()
        val view = RecordingHapticView(context)
        val dispatcher = PlatformTouchFeedbackDispatcher(view, owner)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            owner.moveTo(Lifecycle.State.RESUMED)
            TouchFeedbackEvent.entries.forEach(dispatcher::dispatch)
        }

        val deliveryFeedback = if (Build.VERSION.SDK_INT >= 30) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.CLOCK_TICK
        }
        assertEquals(
            listOf(
                HapticFeedbackConstants.CLOCK_TICK,
                HapticFeedbackConstants.CLOCK_TICK,
                HapticFeedbackConstants.CLOCK_TICK,
                HapticFeedbackConstants.VIRTUAL_KEY,
                deliveryFeedback,
            ),
            view.requests,
        )
    }

    @Test
    fun lifecycleBackgroundSuppressesAllRequests() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val owner = TestLifecycleOwner()
        val view = RecordingHapticView(context)
        val dispatcher = PlatformTouchFeedbackDispatcher(view, owner)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            owner.moveTo(Lifecycle.State.STARTED)
            TouchFeedbackEvent.entries.forEach(dispatcher::dispatch)
        }

        assertEquals(emptyList<Int>(), view.requests)
    }

    private class RecordingHapticView(context: Context) : View(context) {
        val requests = mutableListOf<Int>()

        override fun isShown(): Boolean = true

        override fun performHapticFeedback(feedbackConstant: Int): Boolean {
            requests += feedbackConstant
            return true
        }
    }

    private class TestLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry

        fun moveTo(state: Lifecycle.State) {
            registry.currentState = state
        }
    }
}
