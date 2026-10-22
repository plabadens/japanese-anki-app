package dev.pierre.japaneseanki.ui

import java.util.concurrent.atomic.AtomicBoolean
import androidx.annotation.StringRes
import dev.pierre.japaneseanki.R

/** A portrait 4:3 sensor preview is displayed upright in a 3:4 viewport. */
internal data class CameraPreviewSizeDp(val width: Float, val height: Float)

/**
 * The sensor viewport always uses the full available width. Short panes scroll their controls;
 * they do not silently narrow the camera image to make the controls fit.
 */
internal fun cameraPreviewSizeDp(availableWidth: Float): CameraPreviewSizeDp {
    require(availableWidth.isFinite() && availableWidth >= 0f) { "availableWidth must be finite and non-negative" }
    return CameraPreviewSizeDp(width = availableWidth, height = availableWidth * 4f / 3f)
}

internal const val CAMERA_CONTROL_ROW_HEIGHT_DP = 96
internal const val CAMERA_SHUTTER_SIZE_DP = 80
internal const val CAMERA_SIDE_SLOT_SIZE_DP = 48
internal const val INPUT_MODE_SELECTOR_MIN_HEIGHT_DP = 48
internal const val INPUT_STATUS_SLOT_HEIGHT_DP = 88
internal const val INPUT_STATUS_OVERLAY_MAX_HEIGHT_DP = 112

internal fun captureActionsEnabled(screenEnabled: Boolean, captureInProgress: Boolean): Boolean =
    screenEnabled && !captureInProgress

internal data class CaptureShutterPresentation(
    val diameterDp: Int,
    val showProgress: Boolean,
    @StringRes val contentDescription: Int,
    @StringRes val stateDescription: Int,
)

/** Keep the button's measured bounds identical in ready, pending and failed states. */
internal fun captureShutterPresentation(captureInProgress: Boolean, captureFailed: Boolean) =
    when {
        captureInProgress -> CaptureShutterPresentation(
            diameterDp = CAMERA_SHUTTER_SIZE_DP,
            showProgress = true,
            contentDescription = R.string.legacy_capture_saving_photo,
            stateDescription = R.string.legacy_capture_capture_in_progress,
        )
        captureFailed -> CaptureShutterPresentation(
            diameterDp = CAMERA_SHUTTER_SIZE_DP,
            showProgress = false,
            contentDescription = R.string.legacy_capture_capture_photo,
            stateDescription = R.string.legacy_capture_capture_failed,
        )
        else -> CaptureShutterPresentation(
            diameterDp = CAMERA_SHUTTER_SIZE_DP,
            showProgress = false,
            contentDescription = R.string.legacy_capture_capture_photo,
            stateDescription = R.string.ready,
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
