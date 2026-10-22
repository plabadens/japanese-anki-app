package dev.pierre.japaneseanki.ui

import dev.pierre.japaneseanki.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputCaptureStateTest {
    @Test
    fun portraitPreviewUsesFullWidthAtTheUprightFourByThreeSensorRatio() {
        val bounds = cameraPreviewSizeDp(400f)

        assertEquals(CameraPreviewSizeDp(400f, 1600f / 3f), bounds)
    }

    @Test
    fun shortPanesKeepTheFullViewportAndLeaveOverflowToScrolling() {
        val bounds = cameraPreviewSizeDp(320f)

        assertEquals(CameraPreviewSizeDp(320f, 1280f / 3f), bounds)
        assertTrue(bounds.height > 400f)
        assertEquals(3f / 4f, bounds.width / bounds.height, 0.0001f)
    }

    @Test
    fun busyAndErrorFeedbackKeepPreviewAndShutterBoundsStable() {
        val expectedPreview = cameraPreviewSizeDp(393f)
        val presentations = listOf(
            captureShutterPresentation(captureInProgress = false, captureFailed = false),
            captureShutterPresentation(captureInProgress = true, captureFailed = false),
            captureShutterPresentation(captureInProgress = false, captureFailed = true),
        )

        assertEquals(3, presentations.size)
        assertEquals(
            listOf(expectedPreview),
            presentations.map { cameraPreviewSizeDp(393f) }.distinct(),
        )
        assertEquals(listOf(CAMERA_SHUTTER_SIZE_DP), presentations.map { it.diameterDp }.distinct())
        assertEquals(CAMERA_CONTROL_ROW_HEIGHT_DP, 96)
        assertFalse(presentations[0].showProgress)
        assertTrue(presentations[1].showProgress)
        assertFalse(presentations[2].showProgress)
        assertEquals(R.string.legacy_capture_capture_in_progress, presentations[1].stateDescription)
        assertEquals(R.string.legacy_capture_capture_failed, presentations[2].stateDescription)
    }

    @Test
    fun everySiblingCaptureActionLocksWhilePhotoIsBeingSaved() {
        assertTrue(captureActionsEnabled(screenEnabled = true, captureInProgress = false))
        assertFalse(captureActionsEnabled(screenEnabled = true, captureInProgress = true))
        assertFalse(captureActionsEnabled(screenEnabled = false, captureInProgress = false))
    }

    @Test
    fun captureGateRejectsConcurrentStartsAndCanBeReusedAfterCompletion() {
        val gate = CaptureOperationGate()

        assertFalse(gate.isActive)
        assertTrue(gate.tryStart())
        assertTrue(gate.isActive)
        assertFalse(gate.tryStart())
        assertTrue(gate.finish())
        assertFalse(gate.isActive)
        assertFalse(gate.finish())
        assertTrue(gate.tryStart())
    }

    @Test
    fun typedAttachmentRetryReopensPickerInsteadOfReprocessingThroughOcr() {
        assertEquals(PhotoRetryPath.REOPEN_TYPED_ATTACHMENT_PICKER, photoRetryPath("Type"))
        assertEquals(PhotoRetryPath.RETRY_SOURCE, photoRetryPath("Photo"))
    }

    @Test
    fun failedOcrHasAVisibleRetryEvenWhenTheOriginalPickerWasDismissed() {
        val actions = inputRecoveryActions(
            ocrLoading = false,
            ocrFailed = true,
            hasOcrChoices = false,
            imageFailed = false,
        )

        assertTrue(actions.retryOcr)
        assertFalse(actions.reopenOcrChoices)
    }

    @Test
    fun dismissedOcrChooserCanBeReopenedWhileChoicesRemain() {
        val actions = inputRecoveryActions(
            ocrLoading = false,
            ocrFailed = false,
            hasOcrChoices = true,
            imageFailed = false,
        )

        assertTrue(actions.reopenOcrChoices)
        assertFalse(actions.retryOcr)
    }

    @Test
    fun pendingOcrDoesNotOfferStaleRetryOrChooserActions() {
        val actions = inputRecoveryActions(
            ocrLoading = true,
            ocrFailed = true,
            hasOcrChoices = true,
            imageFailed = false,
        )

        assertFalse(actions.retryOcr)
        assertFalse(actions.reopenOcrChoices)
    }

    @Test
    fun typedAttachmentFailureShowsImageRetryWithoutStartingOcr() {
        val actions = inputRecoveryActions(
            ocrLoading = false,
            ocrFailed = false,
            hasOcrChoices = false,
            imageFailed = true,
        )

        assertTrue(actions.retryImage)
        assertFalse(actions.retryOcr)
        assertFalse(actions.reopenOcrChoices)
    }
}
