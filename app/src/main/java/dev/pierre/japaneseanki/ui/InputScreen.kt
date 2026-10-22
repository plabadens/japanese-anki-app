package dev.pierre.japaneseanki.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
 * Input view of the portrait composition: an uncropped 4:3-sensor preview shown upright in a 3:4
 * viewport, with Gallery / shutter / Type controls. Landscape photographs remain inside this
 * portrait composition; the app never becomes a landscape layout.
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
    onCaptureInProgressChanged: (Boolean) -> Unit = {},
    cameraPermissionGranted: Boolean = true,
    onRequestCamera: () -> Unit = {},
    cameraError: String? = null,
    ocrLoading: Boolean = false,
    ocrError: String? = null,
    onRetryOcr: () -> Unit = {},
    ocrChoicesAvailable: Boolean = false,
    onReopenOcrChooser: () -> Unit = {},
    imageAttached: Boolean = false,
    imageStatus: String? = null,
    imageFailed: Boolean = false,
    onRetryImage: () -> Unit = {},
) {
    // One controller drives both the preview and shutter. Recomposition for status/capture state
    // does not recreate it or change the measured preview bounds.
    val cameraController = if (captureMode == "Photo" && cameraPermissionGranted) {
        rememberCameraController()
    } else {
        null
    }
    var captureInProgress by remember { mutableStateOf(false) }
    val captureOperationGate = remember { CaptureOperationGate() }
    val actionsEnabled = captureActionsEnabled(enabled, captureInProgress || captureOperationGate.isActive)

    Column(modifier.fillMaxSize()) {
        if (captureMode == "Photo") {
            // Feedback must remain an overlay. Previously variable rows below the weighted camera
            // changed PreviewView's bounds and transform; CameraX's lifecycle binding itself stayed
            // bound, so the risk was a surface resize/reconfiguration rather than unbind/rebind.
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val previewSize = cameraPreviewSizeDp(maxWidth.value, maxHeight.value)
                Box(
                    modifier = Modifier
                        .size(width = previewSize.width.dp, height = previewSize.height.dp)
                        .align(Alignment.Center),
                ) {
                    PhotoPane(
                        modifier = Modifier.fillMaxSize(),
                        enabled = actionsEnabled,
                        controller = cameraController,
                        operationGate = captureOperationGate,
                    )
                }
                InputStatusPanel(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    enabled = actionsEnabled,
                    operationGate = captureOperationGate,
                    cameraError = cameraError,
                    ocrLoading = ocrLoading,
                    ocrError = ocrError,
                    onRetryOcr = onRetryOcr,
                    ocrChoicesAvailable = ocrChoicesAvailable,
                    onReopenOcrChooser = onReopenOcrChooser,
                    imageStatus = imageStatus,
                    imageFailed = imageFailed,
                    onRetryImage = onRetryImage,
                )
            }
            CaptureRow(
                enabled = actionsEnabled,
                operationGate = captureOperationGate,
                controller = cameraController,
                onRequestCamera = onRequestCamera,
                onOpenGallery = onOpenGallery,
                onType = { onCaptureModeChange("Type") },
                onCaptured = onCaptured,
                onCameraError = onCameraError,
                onCaptureStateChanged = { inProgress ->
                    captureInProgress = inProgress
                    onCaptureInProgressChanged(inProgress)
                },
                captureFailed = cameraError != null,
                outputDirectory = outputDirectory,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CAMERA_CONTROL_ROW_HEIGHT_DP.dp),
            )
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WritingPane(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp),
                    editor = editor,
                    onEditorChange = onEditorChange,
                    enabled = actionsEnabled,
                )
                // The slot stays measured even while empty, so attachment/OCR status never moves
                // the editor or hides its text. The Photo preview uses the same content as overlay.
                Box(Modifier.fillMaxWidth().height(INPUT_STATUS_SLOT_HEIGHT_DP.dp)) {
                    InputStatusPanel(
                        modifier = Modifier.align(Alignment.Center),
                        enabled = actionsEnabled,
                        operationGate = captureOperationGate,
                        cameraError = null,
                        ocrLoading = false,
                        ocrError = null,
                        onRetryOcr = onRetryOcr,
                        ocrChoicesAvailable = false,
                        onReopenOcrChooser = onReopenOcrChooser,
                        imageStatus = imageStatus,
                        imageFailed = imageFailed,
                        onRetryImage = when (photoRetryPath(captureMode)) {
                            PhotoRetryPath.REOPEN_TYPED_ATTACHMENT_PICKER -> onChoosePhoto
                            PhotoRetryPath.RETRY_SOURCE -> onRetryImage
                        },
                    )
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        enabled = actionsEnabled && !captureOperationGate.isActive,
                        onClick = { if (!captureOperationGate.isActive) onChoosePhoto() },
                    ) {
                        Text(if (imageAttached) "Change photo" else "Add photo (optional)")
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        enabled = actionsEnabled && !captureOperationGate.isActive,
                        onClick = { if (!captureOperationGate.isActive) onCaptureModeChange("Photo") },
                    ) {
                        Text("Photo")
                    }
                }
                FlowPrimaryButton(
                    text = "Continue",
                    onClick = { if (!captureOperationGate.isActive) onContinue() },
                    enabled = actionsEnabled && !captureOperationGate.isActive && editor.text.isNotBlank(),
                )
            }
        }
    }
}

@Composable
private fun InputStatusPanel(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    operationGate: CaptureOperationGate,
    cameraError: String?,
    ocrLoading: Boolean,
    ocrError: String?,
    onRetryOcr: () -> Unit,
    ocrChoicesAvailable: Boolean,
    onReopenOcrChooser: () -> Unit,
    imageStatus: String?,
    imageFailed: Boolean,
    onRetryImage: () -> Unit,
) {
    val recoveryActions = inputRecoveryActions(
        ocrLoading = ocrLoading,
        ocrFailed = ocrError != null,
        hasOcrChoices = ocrChoicesAvailable,
        imageFailed = imageFailed,
    )
    if (cameraError == null && ocrError == null && !ocrLoading &&
        !ocrChoicesAvailable && imageStatus == null
    ) return

    Surface(
        modifier = modifier.fillMaxWidth().heightIn(max = INPUT_STATUS_OVERLAY_MAX_HEIGHT_DP.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            cameraError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            ocrError?.let { message ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        message,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    if (recoveryActions.retryOcr) {
                        TextButton(
                            enabled = enabled && !operationGate.isActive,
                            onClick = { if (!operationGate.isActive) onRetryOcr() },
                        ) { Text("Retry OCR") }
                    }
                }
            }
            if (ocrLoading) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("Reading the photo on this device…", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (recoveryActions.reopenOcrChoices) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Text choices are ready.", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(
                        enabled = enabled && !operationGate.isActive,
                        onClick = { if (!operationGate.isActive) onReopenOcrChooser() },
                    ) { Text("Review choices") }
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
                    if (recoveryActions.retryImage) {
                        TextButton(
                            enabled = enabled && !operationGate.isActive,
                            onClick = { if (!operationGate.isActive) onRetryImage() },
                        ) { Text("Retry photo") }
                    }
                }
            }
        }
    }
}

/**
 * CameraX fills cameraInfo asynchronously after binding, so flash availability is checked away
 * from composition. Reading cameraInfo while CameraX is initialising can block the UI thread.
 */
@Composable
private fun rememberFlashAvailability(controller: androidx.camera.view.LifecycleCameraController): Boolean {
    var available by remember(controller) { mutableStateOf(false) }
    LaunchedEffect(controller) {
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
    operationGate: CaptureOperationGate,
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
    var flashOn by remember(controller) { mutableStateOf(false) }
    Box(modifier, contentAlignment = Alignment.Center) {
        CameraViewfinder(controller = controller, modifier = Modifier.fillMaxSize())
        if (hasFlash) {
            Surface(
                onClick = {
                    if (enabled && !operationGate.isActive) {
                        flashOn = !flashOn
                        controller.imageCaptureFlashMode = if (flashOn) {
                            androidx.camera.core.ImageCapture.FLASH_MODE_ON
                        } else {
                            androidx.camera.core.ImageCapture.FLASH_MODE_OFF
                        }
                    }
                },
                enabled = enabled && !operationGate.isActive,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(48.dp)
                    .semantics {
                        contentDescription = "Flash"
                        stateDescription = if (flashOn) "On" else "Off"
                    },
                shape = CircleShape,
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

/** Gallery / shutter / Type row: every sibling action is disabled during the camera write. */
@Composable
private fun CaptureRow(
    enabled: Boolean,
    operationGate: CaptureOperationGate,
    controller: androidx.camera.view.LifecycleCameraController?,
    onRequestCamera: () -> Unit,
    onOpenGallery: () -> Unit,
    onType: () -> Unit,
    onCaptured: (File) -> Unit,
    onCameraError: (String) -> Unit,
    onCaptureStateChanged: (Boolean) -> Unit,
    captureFailed: Boolean,
    outputDirectory: File,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CaptureActionButton(
            enabled = enabled,
            operationGate = operationGate,
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
                onCaptureStateChanged = onCaptureStateChanged,
                captureFailed = captureFailed,
                operationGate = operationGate,
            )
        } else {
            FlowPrimaryButton(
                text = "Camera",
                onClick = { if (enabled && !operationGate.isActive) onRequestCamera() },
                modifier = Modifier.size(width = 120.dp, height = 48.dp),
                enabled = enabled && !operationGate.isActive,
                showArrow = false,
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CaptureActionButton(
                enabled = enabled,
                operationGate = operationGate,
                onClick = onType,
                contentDescription = "Type a sentence",
            ) { FlowIcons.Keyboard(Modifier.size(24.dp)) }
            Text("Type", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CaptureActionButton(
    enabled: Boolean,
    operationGate: CaptureOperationGate,
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = { if (enabled && !operationGate.isActive) onClick() },
        enabled = enabled && !operationGate.isActive,
        modifier = Modifier.size(48.dp).semantics { this.contentDescription = contentDescription },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
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
