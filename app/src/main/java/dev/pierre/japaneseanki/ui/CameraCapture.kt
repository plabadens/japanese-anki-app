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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
 * Live CameraX viewfinder for Photo capture (SPEC.md §4.2). The preview fills the full
 * available area edge-to-edge horizontally; a 4:3 letterboxed, corner-bracketed frame
 * overlay marks the region that is actually captured, matching mockup 06/07. The shutter
 * row (thumbnail + shutter button) is part of this composable since it owns the
 * CameraX controller that both the preview and the capture action need.
 */
@Composable
internal fun CameraCaptureScreen(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    outputDirectory: File,
    onCaptured: (File) -> Unit,
    onError: (String) -> Unit,
) {
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
    var capturing by remember { mutableStateOf(false) }

    Column(modifier) {
        Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black)) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        this.controller = controller
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val frameHeight = (maxWidth * 3f / 4f).coerceAtMost(maxHeight)
                val inset = ((maxHeight - frameHeight) / 2).coerceAtLeast(0.dp)
                if (inset > 0.dp) {
                    Box(
                        Modifier.fillMaxWidth().height(inset).align(Alignment.TopStart)
                            .background(Color.Black.copy(alpha = 0.6f)),
                    )
                    Box(
                        Modifier.fillMaxWidth().height(inset).align(Alignment.BottomStart)
                            .background(Color.Black.copy(alpha = 0.6f)),
                    )
                }
                Canvas(Modifier.fillMaxSize()) {
                    val top = inset.toPx()
                    val bottom = size.height - inset.toPx()
                    val left = 0f
                    val right = size.width
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
        Row(
            Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (capturing) {
                CircularProgressIndicator(Modifier.size(56.dp), strokeWidth = 3.dp)
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
                    Modifier.size(74.dp)
                        .clip(CircleShape)
                        .clickable(enabled = enabled, onClick = takePhoto)
                        .border(BorderStroke(4.dp, MaterialTheme.colorScheme.primary), CircleShape)
                        .semantics { contentDescription = "Capture photo" },
                )
            }
        }
    }
}
