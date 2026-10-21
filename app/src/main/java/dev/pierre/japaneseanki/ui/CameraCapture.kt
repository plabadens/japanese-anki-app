package dev.pierre.japaneseanki.ui

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.util.UUID

/**
 * Binds a CameraX controller to the current lifecycle (SPEC.md §4.2). Shared by
 * [CameraViewfinder] and [CameraShutterRow] so the viewfinder and shutter can be laid out as
 * independent rows (frame as a tight 4:3 box, shutter below with ordinary padding) while both
 * drive the same camera session.
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
        onDispose { controller.unbind() }
    }
    return controller
}

/**
 * Height the landscape camera frame may use: the screen height minus the capture header, screen
 * padding and system bars. The 4:3 frame is height-limited in landscape, so deriving it from the
 * screen *width* (as portrait does) pushed the shutter row off-screen and cropped the preview.
 */
internal fun landscapeViewfinderHeightDp(screenHeightDp: Int): Int =
    (screenHeightDp - LANDSCAPE_CHROME_DP).coerceAtLeast(MIN_LANDSCAPE_FRAME_DP)

internal const val LANDSCAPE_CHROME_DP = 150
internal const val MIN_LANDSCAPE_FRAME_DP = 120

/**
 * The live preview itself: an upright 3:4, corner-bracketed frame. The caller gives it an
 * edge-to-edge box with height = width * 4/3; any extra space is letterboxed, matching mockup 06.
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
            modifier = Modifier.fillMaxSize(),
        )
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // A 4:3 sensor shown upright is a 3:4 (width:height) box — taller than wide.
            val frameWidth = (maxHeight * 3f / 4f).coerceAtMost(maxWidth)
            val frameHeight = (frameWidth * 4f / 3f).coerceAtMost(maxHeight)
            val insetX = ((maxWidth - frameWidth) / 2).coerceAtLeast(0.dp)
            val insetY = ((maxHeight - frameHeight) / 2).coerceAtLeast(0.dp)
            if (insetX > 0.dp) {
                Box(
                    Modifier.fillMaxHeight().width(insetX).align(Alignment.TopStart)
                        .background(Color.Black.copy(alpha = 0.6f)),
                )
                Box(
                    Modifier.fillMaxHeight().width(insetX).align(Alignment.TopEnd)
                        .background(Color.Black.copy(alpha = 0.6f)),
                )
            }
            if (insetY > 0.dp) {
                Box(
                    Modifier.fillMaxWidth().height(insetY).align(Alignment.TopStart)
                        .background(Color.Black.copy(alpha = 0.6f)),
                )
                Box(
                    Modifier.fillMaxWidth().height(insetY).align(Alignment.BottomStart)
                        .background(Color.Black.copy(alpha = 0.6f)),
                )
            }
            Canvas(Modifier.fillMaxSize()) {
                val top = insetY.toPx()
                val bottom = size.height - insetY.toPx()
                val left = insetX.toPx()
                val right = size.width - insetX.toPx()
                val margin = 10.dp.toPx()
                val arm = 24.dp.toPx()
                val stroke = 2.5.dp.toPx()
                val color = Color.White.copy(alpha = 0.85f)
                // Top-left
                drawLine(color, Offset(left + margin, top + margin), Offset(left + margin + arm, top + margin), strokeWidth = stroke)
                drawLine(color, Offset(left + margin, top + margin), Offset(left + margin, top + margin + arm), strokeWidth = stroke)
                // Top-right
                drawLine(color, Offset(right - margin, top + margin), Offset(right - margin - arm, top + margin), strokeWidth = stroke)
                drawLine(color, Offset(right - margin, top + margin), Offset(right - margin, top + margin + arm), strokeWidth = stroke)
                // Bottom-left
                drawLine(color, Offset(left + margin, bottom - margin), Offset(left + margin + arm, bottom - margin), strokeWidth = stroke)
                drawLine(color, Offset(left + margin, bottom - margin), Offset(left + margin, bottom - margin - arm), strokeWidth = stroke)
                // Bottom-right
                drawLine(color, Offset(right - margin, bottom - margin), Offset(right - margin - arm, bottom - margin), strokeWidth = stroke)
                drawLine(color, Offset(right - margin, bottom - margin), Offset(right - margin, bottom - margin - arm), strokeWidth = stroke)
            }
        }
    }
}

/** The shutter row below the viewfinder — ordinary UI, not part of the 4:3 frame. */
@Composable
internal fun CameraShutterRow(
    controller: LifecycleCameraController,
    enabled: Boolean,
    outputDirectory: File,
    onCaptured: (File) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CameraShutterButton(controller, enabled, outputDirectory, onCaptured, onError)
    }
}

/** The circular shutter itself; the portrait capture triad places it beside Gallery and Type. */
@Composable
internal fun CameraShutterButton(
    controller: LifecycleCameraController,
    enabled: Boolean,
    outputDirectory: File,
    onCaptured: (File) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var capturing by remember { mutableStateOf(false) }
    if (capturing) {
        CircularProgressIndicator(modifier.size(74.dp), strokeWidth = 3.dp)
    } else {
            val takePhoto = {
                capturing = true
                val destination = File(outputDirectory, "${UUID.randomUUID()}.jpg")
                val options = ImageCapture.OutputFileOptions.Builder(destination).build()
                controller.takePicture(
                    options,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            capturing = false
                            onCaptured(destination)
                        }
                        override fun onError(exception: ImageCaptureException) {
                            capturing = false
                            onError("Could not capture a photo. Try again.")
                        }
                    },
                )
            }
            Box(
                modifier.size(80.dp)
                    .clip(CircleShape)
                    .clickable(enabled = enabled, onClick = takePhoto)
                    .border(BorderStroke(4.dp, MaterialTheme.colorScheme.primary), CircleShape)
                    .padding(7.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .semantics { contentDescription = "Capture photo" },
            )
    }
}
