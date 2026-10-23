package dev.pierre.japaneseanki.ui

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.pierre.japaneseanki.R
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Binds a CameraX controller to the current lifecycle (SPEC.md §4.2). Shared by
 * [CameraViewfinder] and [CameraShutterButton] so the preview and capture controls drive the
 * same camera session.
 */
@Composable
internal fun rememberCameraController(): LifecycleCameraController {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        }
    }
    DisposableEffect(lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose {
            controller.cameraControl?.enableTorch(false)
            controller.unbind()
        }
    }
    return controller
}

/**
 * The live preview itself. The caller gives it an exact upright 3:4 viewport for the 4:3 sensor;
 * the containing pane, not state-dependent feedback, controls its dimensions.
 */
@Composable
internal fun CameraViewfinder(controller: LifecycleCameraController, modifier: Modifier = Modifier) {
    Box(modifier.background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    this.controller = controller
                    // FIT_CENTER keeps the whole captured frame visible; FILL_CENTER cropped the
                    // preview whenever the preview aspect differed from the 4:3 capture aspect.
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                }
            },
            modifier = Modifier.fillMaxSize().testTag("camera-live-preview"),
        )
    }
}

/** The fixed-size circular shutter; the capture row centers it between equal gallery-side slots. */
@Composable
internal fun CameraShutterButton(
    controller: LifecycleCameraController,
    enabled: Boolean,
    outputDirectory: File,
    onCaptured: (File) -> Unit,
    onError: (String) -> Unit,
    onCaptureStateChanged: (Boolean) -> Unit,
    captureFailed: Boolean,
    operationGate: CaptureOperationGate,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var capturing by remember(controller) { mutableStateOf(false) }
    val presentation = captureShutterPresentation(capturing, captureFailed)
    val shutterDescription = stringResource(presentation.contentDescription)
    val shutterStateDescription = stringResource(presentation.stateDescription)
    val captureFailureMessage = stringResource(R.string.legacy_capture_camera_capture_failed)
    val currentCaptureFailureMessage by rememberUpdatedState(captureFailureMessage)
    val currentOnCaptured by rememberUpdatedState(onCaptured)
    val currentOnError by rememberUpdatedState(onError)
    val currentOnCaptureStateChanged by rememberUpdatedState(onCaptureStateChanged)
    val feedback = LocalTouchFeedbackDispatcher.current

    val finish = {
        if (operationGate.finish()) {
            capturing = false
            currentOnCaptureStateChanged(false)
        }
    }
    val fail = { destination: File, stage: String, errorCode: Int?, cause: Throwable? ->
        val partialRemoved = runCatching { !destination.exists() || destination.delete() }.getOrDefault(false)
        val causeName = cause?.javaClass?.simpleName ?: "none"
        Log.w(
            CAMERA_CAPTURE_TAG,
            "Photo save failed: stage=$stage code=${errorCode ?: "none"} cause=$causeName partialRemoved=$partialRemoved",
        )
        finish()
        currentOnError(currentCaptureFailureMessage)
    }
    val takePhoto = takePhoto@{
        if (!enabled || !operationGate.tryStart()) return@takePhoto
        capturing = true
        currentOnCaptureStateChanged(true)
        val destination = File(outputDirectory, "${UUID.randomUUID()}.jpg")
        try {
            if (!outputDirectory.isDirectory) {
                val created = outputDirectory.mkdirs()
                if (!created && !outputDirectory.isDirectory) {
                    throw IOException("Capture directory is unavailable")
                }
            }
            val options = ImageCapture.OutputFileOptions.Builder(destination).build()
            controller.takePicture(
                options,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        if (!destination.isFile || destination.length() == 0L) {
                            fail(destination, "empty_output", null, null)
                            return
                        }
                        finish()
                        currentOnCaptured(destination)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        fail(destination, "camera_write", exception.imageCaptureError, exception.cause)
                    }
                },
            )
            feedback.dispatch(TouchFeedbackEvent.SHUTTER_ACCEPTED)
        } catch (exception: Exception) {
            fail(destination, "capture_setup", null, exception)
        }
    }

    Box(
        modifier.size(presentation.diameterDp.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled && !capturing, onClick = takePhoto)
            .border(BorderStroke(4.dp, MaterialTheme.colorScheme.primary), CircleShape)
            .padding(7.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape)
            .semantics {
                contentDescription = shutterDescription
                stateDescription = shutterStateDescription
            },
        contentAlignment = Alignment.Center,
    ) {
        if (presentation.showProgress) {
            MotionAwareProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                diameter = 32.dp,
                strokeWidth = 2.dp,
                stateDescription = shutterStateDescription,
            )
        }
    }
}

private const val CAMERA_CAPTURE_TAG = "CameraCapture"
