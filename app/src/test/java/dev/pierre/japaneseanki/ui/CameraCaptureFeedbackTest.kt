package dev.pierre.japaneseanki.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraCaptureFeedbackTest {
    @Test
    fun failedCaptureAndEmptySaveAreSilentWhileSuccessfulSaveDispatchesOnce() {
        val destination = File("build/test-captures/shutter-feedback-test.jpg")
        assertTrue(destination.parentFile?.mkdirs() == true || destination.parentFile?.isDirectory == true)
        destination.writeBytes(byteArrayOf())
        val events = mutableListOf<TouchFeedbackEvent>()
        val dispatcher = TouchFeedbackDispatcher { event -> events += event }
        var failures = 0
        var successes = 0

        handleCaptureSave(
            destination = null,
            feedback = dispatcher,
            onFailure = { failures++ },
            onSuccess = { successes++ },
        )
        assertEquals(1, failures)
        assertEquals(0, successes)
        assertTrue(events.isEmpty())

        handleCaptureSave(
            destination = destination,
            feedback = dispatcher,
            onFailure = { failures++ },
            onSuccess = { successes++ },
        )
        assertEquals(2, failures)
        assertEquals(0, successes)
        assertTrue(events.isEmpty())

        destination.writeBytes(byteArrayOf(1))
        handleCaptureSave(
            destination = destination,
            feedback = dispatcher,
            onFailure = { failures++ },
            onSuccess = { saved ->
                assertEquals(destination, saved)
                successes++
            },
        )

        assertEquals(2, failures)
        assertEquals(1, successes)
        assertEquals(listOf(TouchFeedbackEvent.SHUTTER_ACCEPTED), events)
        destination.delete()
    }
}
