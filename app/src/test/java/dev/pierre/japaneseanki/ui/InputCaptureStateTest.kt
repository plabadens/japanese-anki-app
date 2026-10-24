package dev.pierre.japaneseanki.ui

import androidx.test.ext.junit.runners.AndroidJUnit4

import dev.pierre.japaneseanki.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InputCaptureStateTest {
    @Test
    fun fullHeightUsesFullWidthAndEqualWhitespaceAroundTheControlRow() {
        // 844 dp device height less 24 dp status and navigation insets, then the fixed 56 dp bar.
        val layout = cameraCaptureLayoutDp(390f, 740f)

        assertEquals(CameraPreviewSizeDp(390f, 520f), layout.preview)
        assertEquals(74f, layout.equalGap, 0.001f)
        assertEquals(3f / 4f, layout.preview.width / layout.preview.height, 0.0001f)
    }

    @Test
    fun shortScreenShrinksGapsToTheirFloorThenNarrowsThePreview() {
        // 620 dp device height less 24 dp system insets and the fixed 56 dp bar.
        val layout = cameraCaptureLayoutDp(390f, 516f)

        assertEquals(CameraPreviewSizeDp(315f, 420f), layout.preview)
        assertEquals(CAMERA_CAPTURE_GAP_FLOOR_DP.toFloat(), layout.equalGap, 0.001f)
        assertEquals(3f / 4f, layout.preview.width / layout.preview.height, 0.0001f)
    }

    @Test
    fun compactRowKeepsShutterBoundsStableAcrossBusyAndErrorStates() {
        val expectedPreview = cameraCaptureLayoutDp(393f, 740f).preview
        val presentations = listOf(
            captureShutterPresentation(captureInProgress = false, captureFailed = false),
            captureShutterPresentation(captureInProgress = true, captureFailed = false),
            captureShutterPresentation(captureInProgress = false, captureFailed = true),
        )

        assertEquals(3, presentations.size)
        assertEquals(
            listOf(expectedPreview),
            presentations.map { cameraCaptureLayoutDp(393f, 740f).preview }.distinct(),
        )
        assertEquals(listOf(CAMERA_SHUTTER_SIZE_DP), presentations.map { it.diameterDp }.distinct())
        assertEquals(72, CAMERA_CONTROL_ROW_HEIGHT_DP)
        assertEquals(64, CAMERA_SHUTTER_SIZE_DP)
        assertEquals(48, CAMERA_SIDE_SLOT_SIZE_DP)
        assertEquals(56, CAMERA_TOP_BAR_HEIGHT_DP)
        assertEquals(48, CAMERA_MODE_SWITCH_HEIGHT_DP)
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
