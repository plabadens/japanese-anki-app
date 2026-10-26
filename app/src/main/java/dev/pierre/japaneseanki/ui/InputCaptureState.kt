package dev.pierre.japaneseanki.ui

import java.util.concurrent.atomic.AtomicBoolean
import androidx.annotation.StringRes
import dev.pierre.japaneseanki.R

/** The upright 3:4 viewport for a 4:3 camera sensor. */
internal data class CameraPreviewSizeDp(val width: Float, val height: Float)

internal data class CameraCaptureLayoutDp(
    val preview: CameraPreviewSizeDp,
    val equalGap: Float,
)

/**
 * Sizes the full-width preview when there is room; otherwise it narrows symmetrically after both
 * equal control-row gaps reach their 12 dp floor.
 */
internal fun cameraCaptureLayoutDp(availableWidth: Float, availableHeight: Float): CameraCaptureLayoutDp {
    require(availableWidth.isFinite() && availableWidth >= 0f) { "availableWidth must be finite and non-negative" }
    require(availableHeight.isFinite() && availableHeight >= 0f) { "availableHeight must be finite and non-negative" }

    val maxPreviewHeight = (availableHeight - CAMERA_CONTROL_ROW_HEIGHT_DP - 2 * CAMERA_CAPTURE_GAP_FLOOR_DP)
        .coerceAtLeast(0f)
    val previewWidth = minOf(availableWidth, maxPreviewHeight * 3f / 4f)
    val preview = CameraPreviewSizeDp(previewWidth, previewWidth * 4f / 3f)
    val equalGap = maxOf(
        CAMERA_CAPTURE_GAP_FLOOR_DP.toFloat(),
        (availableHeight - preview.height - CAMERA_CONTROL_ROW_HEIGHT_DP) / 2f,
    )
    return CameraCaptureLayoutDp(preview, equalGap)
}

internal const val CAMERA_TOP_BAR_HEIGHT_DP = 64
internal const val CAMERA_CONTROL_ROW_HEIGHT_DP = 72
internal const val CAMERA_SHUTTER_SIZE_DP = 64
internal const val CAMERA_SIDE_SLOT_SIZE_DP = 48
internal const val CAMERA_CAPTURE_GAP_FLOOR_DP = 12
internal const val CAMERA_MODE_SWITCH_HEIGHT_DP = 48

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
