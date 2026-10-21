package dev.pierre.japaneseanki.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/**
 * Input view of the portrait composition (mockup/expressive-flow): a centred portrait camera
 * with gallery on the left and Type on the right, or a writing canvas for Type. Landscape
 * photographs are source images inside this composition; the app never becomes a landscape layout.
 */
@Composable
internal fun InputScreen(
    captureMode: String,
    onCaptureModeChange: (String) -> Unit,
    editor: TextFieldValue,
    onEditorChange: (TextFieldValue) -> Unit,
    onContinue: () -> Unit,
    onOpenGallery: () -> Unit,
    onChoosePhoto: () -> Unit,
    onCaptured: (File) -> Unit,
    onCameraError: (String) -> Unit,
    outputDirectory: File,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cameraPermissionGranted: Boolean = true,
    onRequestCamera: () -> Unit = {},
    cameraError: String? = null,
    ocrLoading: Boolean = false,
    ocrError: String? = null,
    onRetryOcr: () -> Unit = {},
    imageAttached: Boolean = false,
    imageStatus: String? = null,
    imageFailed: Boolean = false,
    onRetryImage: () -> Unit = {},
) {
    // One camera controller for the preview and the shutter: binding two to the same lifecycle
    // would make the second steal the first's session.
    val cameraController = if (captureMode == "Photo" && cameraPermissionGranted) {
        rememberCameraController()
    } else {
        null
    }
    Column(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (captureMode == "Photo") {
                PhotoPane(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp),
                    enabled = enabled,
                    controller = cameraController,
                )
            } else {
                WritingPane(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp),
                    editor = editor,
                    onEditorChange = onEditorChange,
                    enabled = enabled,
                )
            }
            cameraError?.let { message ->
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            ocrError?.let { message ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        message,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(enabled = enabled, onClick = onRetryOcr) { Text("Retry") }
                }
            }
            if (ocrLoading) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("Reading the photo on this device…", style = MaterialTheme.typography.bodyMedium)
                }
            }
            imageStatus?.let { status ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        status,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (imageFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // v0.3 offered an explicit retry for a failed photo; re-picking is not a
                    // recovery path the user should have to discover.
                    if (imageFailed) {
                        TextButton(enabled = enabled, onClick = onRetryImage) { Text("Retry photo") }
                    }
                }
            }
        }
        if (captureMode == "Photo") {
            CaptureRow(
                enabled = enabled,
                controller = cameraController,
                onRequestCamera = onRequestCamera,
                onOpenGallery = onOpenGallery,
                onType = { onCaptureModeChange("Type") },
                onCaptured = onCaptured,
                onCameraError = onCameraError,
                outputDirectory = outputDirectory,
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(enabled = enabled, onClick = onChoosePhoto) {
                        Text(if (imageAttached) "Change photo" else "Add photo (optional)")
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(enabled = enabled, onClick = { onCaptureModeChange("Photo") }) {
                        Text("Photo")
                    }
                }
                FlowPrimaryButton(
                    text = "Continue",
                    onClick = onContinue,
                    enabled = enabled && editor.text.isNotBlank(),
                )
            }
        }
    }
}

/**
 * CameraX fills `cameraInfo` asynchronously after the controller binds, so the flash control has
 * to wait for it instead of reading a single nullable snapshot during composition. The wait runs
 * off the main thread: asking the controller for camera info can block on CameraX initialisation,
 * which ANRs the UI if it happens during composition.
 */
@Composable
private fun rememberFlashAvailability(controller: androidx.camera.view.LifecycleCameraController): Boolean {
    var available by remember(controller) { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(controller) {
        available = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            runCatching {
                controller.initializationFuture.get(10, java.util.concurrent.TimeUnit.SECONDS)
                controller.cameraInfo?.hasFlashUnit() == true
            }.getOrDefault(false)
        }
    }
    return available
}

@Composable
private fun PhotoPane(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    controller: androidx.camera.view.LifecycleCameraController?,
) {
    if (controller == null) {
        Column(
            modifier = modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Camera access is needed to take a photo. Typing and the gallery still work.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }
    val hasFlash = rememberFlashAvailability(controller)
    var flashOn by remember { mutableStateOf(false) }
    Box(modifier, contentAlignment = Alignment.Center) {
        CameraViewfinder(
            controller = controller,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(28.dp)),
        )
        if (hasFlash) {
            Surface(
                onClick = {
                    flashOn = !flashOn
                    controller.imageCaptureFlashMode = if (flashOn) {
                        androidx.camera.core.ImageCapture.FLASH_MODE_ON
                    } else {
                        androidx.camera.core.ImageCapture.FLASH_MODE_OFF
                    }
                },
                enabled = enabled,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(48.dp)
                    .semantics {
                        contentDescription = "Flash"
                        stateDescription = if (flashOn) "On" else "Off"
                    },
                shape = androidx.compose.foundation.shape.CircleShape,
                color = Color.Black.copy(alpha = 0.55f),
                contentColor = Color.White,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    FlowIcons.Flash(Modifier.size(24.dp))
                }
            }
        }
    }
}

/** Gallery / shutter / Type row: the shutter is the only capture control in the composition. */
@Composable
private fun CaptureRow(
    enabled: Boolean,
    controller: androidx.camera.view.LifecycleCameraController?,
    onRequestCamera: () -> Unit,
    onOpenGallery: () -> Unit,
    onType: () -> Unit,
    onCaptured: (File) -> Unit,
    onCameraError: (String) -> Unit,
    outputDirectory: File,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlowRoundIconButton(
            onClick = onOpenGallery,
            contentDescription = "Choose a photo from this device",
        ) { FlowIcons.Gallery(Modifier.size(24.dp)) }

        if (controller != null) {
            CameraShutterButton(
                controller = controller,
                enabled = enabled,
                outputDirectory = outputDirectory,
                onCaptured = onCaptured,
                onError = onCameraError,
            )
        } else {
            FlowPrimaryButton(
                text = "Camera",
                onClick = onRequestCamera,
                modifier = Modifier.size(width = 120.dp, height = 48.dp),
                showArrow = false,
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FlowRoundIconButton(
                onClick = onType,
                contentDescription = "Type a sentence",
            ) { FlowIcons.Keyboard(Modifier.size(24.dp)) }
            Text("Type", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun WritingPane(
    modifier: Modifier = Modifier,
    editor: TextFieldValue,
    onEditorChange: (TextFieldValue) -> Unit,
    enabled: Boolean,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        OutlinedTextField(
            value = editor,
            onValueChange = onEditorChange,
            enabled = enabled,
            modifier = Modifier.fillMaxSize().padding(8.dp).semantics { contentDescription = "Japanese sentence" },
            placeholder = { Text("Type the Japanese sentence", style = MaterialTheme.typography.bodyLarge) },
            textStyle = TextStyle(fontSize = 24.sp, lineHeight = 36.sp, color = MaterialTheme.colorScheme.onSurface),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                disabledBorderColor = Color.Transparent,
            ),
        )
    }
}
