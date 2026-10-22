package dev.pierre.japaneseanki.ui

import java.util.concurrent.atomic.AtomicBoolean

/** A portrait 4:3 sensor preview is displayed upright in a 3:4 viewport. */
internal data class CameraPreviewSizeDp(val width: Float, val height: Float)

/**
 * Fit the largest 3:4 preview inside the available pane. The preview is sized from pane constraints
 * only; transient status, capture and picker state must never participate in this calculation.
 */
internal fun cameraPreviewSizeDp(availableWidth: Float, availableHeight: Float): CameraPreviewSizeDp {
    require(availableWidth.isFinite() && availableWidth >= 0f) { "availableWidth must be finite and non-negative" }
    require(availableHeight.isFinite() && availableHeight >= 0f) { "availableHeight must be finite and non-negative" }
    val width = minOf(availableWidth, availableHeight * 3f / 4f)
    return CameraPreviewSizeDp(width = width, height = width * 4f / 3f)
}

internal const val CAMERA_CONTROL_ROW_HEIGHT_DP = 96
internal const val CAMERA_SHUTTER_SIZE_DP = 80
internal const val INPUT_STATUS_SLOT_HEIGHT_DP = 88
internal const val INPUT_STATUS_OVERLAY_MAX_HEIGHT_DP = 112

internal fun captureActionsEnabled(screenEnabled: Boolean, captureInProgress: Boolean): Boolean =
    screenEnabled && !captureInProgress

internal data class CaptureShutterPresentation(
    val diameterDp: Int,
    val showProgress: Boolean,
    val contentDescription: String,
    val stateDescription: String,
)

/** Keep the button's measured bounds identical in ready, pending and failed states. */
internal fun captureShutterPresentation(captureInProgress: Boolean, captureFailed: Boolean) =
    when {
        captureInProgress -> CaptureShutterPresentation(
            diameterDp = CAMERA_SHUTTER_SIZE_DP,
            showProgress = true,
            contentDescription = "Saving photo",
            stateDescription = "Capture in progress",
        )
        captureFailed -> CaptureShutterPresentation(
            diameterDp = CAMERA_SHUTTER_SIZE_DP,
            showProgress = false,
            contentDescription = "Capture photo",
            stateDescription = "Capture failed",
        )
        else -> CaptureShutterPresentation(
            diameterDp = CAMERA_SHUTTER_SIZE_DP,
            showProgress = false,
            contentDescription = "Capture photo",
            stateDescription = "Ready",
        )
    }

/** Prevent rapid taps from starting concurrent CameraX writes before Compose recomposes. */
internal class CaptureOperationGate {
    private val active = AtomicBoolean(false)

    val isActive: Boolean
        get() = active.get()

    fun tryStart(): Boolean = active.compareAndSet(false, true)

    fun finish(): Boolean = active.compareAndSet(true, false)
}

internal enum class PhotoRetryPath {
    RETRY_SOURCE,
    REOPEN_TYPED_ATTACHMENT_PICKER,
}

internal fun photoRetryPath(captureMode: String): PhotoRetryPath =
    if (captureMode == "Photo") PhotoRetryPath.RETRY_SOURCE else PhotoRetryPath.REOPEN_TYPED_ATTACHMENT_PICKER

internal data class InputRecoveryActions(
    val retryOcr: Boolean,
    val reopenOcrChoices: Boolean,
    val retryImage: Boolean,
)

/**
 * Keep recovery actions available after a chooser/picker is dismissed. Their callbacks own the
 * actual resume path (reuse the retained source when possible, otherwise reopen the system picker).
 */
internal fun inputRecoveryActions(
    ocrLoading: Boolean,
    ocrFailed: Boolean,
    hasOcrChoices: Boolean,
    imageFailed: Boolean,
): InputRecoveryActions = InputRecoveryActions(
    retryOcr = ocrFailed && !ocrLoading,
    reopenOcrChoices = hasOcrChoices && !ocrLoading,
    retryImage = imageFailed,
)
