package dev.pierre.japaneseanki.ui

import androidx.camera.view.LifecycleCameraController
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File

/** The camera and type modes share one fixed top bar and keep their primary action pinned. */
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
    imageStatus: String? = null,
    imageFailed: Boolean = false,
    onRetryImage: () -> Unit = {},
    onSettings: () -> Unit = {},
) {
    val cameraController = if (captureMode == "Photo" && cameraPermissionGranted) {
        rememberCameraController()
    } else {
        null
    }
    var captureInProgress by remember { mutableStateOf(false) }
    val captureOperationGate = remember { CaptureOperationGate() }
    val actionsEnabled = captureActionsEnabled(enabled, captureInProgress || captureOperationGate.isActive)
    val context = LocalContext.current
    val hasTorch = if (cameraController != null) rememberTorchAvailability(cameraController) else false
    var torchOn by remember(cameraController) { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(cameraController, lifecycleOwner) {
        val observer = if (cameraController == null) null else LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                cameraController.cameraControl?.enableTorch(false)
                torchOn = false
            }
        }
        observer?.let(lifecycleOwner.lifecycle::addObserver)
        onDispose {
            cameraController?.cameraControl?.enableTorch(false)
            observer?.let(lifecycleOwner.lifecycle::removeObserver)
            torchOn = false
        }
    }

    if (captureMode == "Photo") {
        Column(modifier.fillMaxSize()) {
            CaptureTopBar(
                captureMode = captureMode,
                enabled = actionsEnabled && !captureOperationGate.isActive,
                onCaptureModeChange = onCaptureModeChange,
                onSettings = onSettings,
            )
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val layout = cameraCaptureLayoutDp(maxWidth.value, maxHeight.value)
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .width(layout.preview.width.dp)
                            .height(layout.preview.height.dp)
                            .testTag("camera-preview-viewport"),
                    ) {
                        PhotoPane(
                            modifier = Modifier.fillMaxSize(),
                            controller = cameraController,
                        )
                    }
                    Box(
                        modifier = Modifier.fillMaxWidth().height(layout.equalGap.dp)
                            .testTag("camera-gap-above-controls"),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        InputStatusPanel(
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
                        hasTorch = hasTorch,
                        torchOn = torchOn,
                        onToggleTorch = {
                            val next = !torchOn
                            val future = cameraController?.cameraControl?.enableTorch(next)
                            if (future != null) {
                                torchOn = next
                                future.addListener(
                                    {
                                        if (runCatching { future.get() }.isFailure && torchOn == next) {
                                            torchOn = !next
                                        }
                                    },
                                    ContextCompat.getMainExecutor(context),
                                )
                            }
                        },
                        onRequestCamera = onRequestCamera,
                        onOpenGallery = onOpenGallery,
                        onCaptured = onCaptured,
                        onCameraError = onCameraError,
                        onCaptureStateChanged = { inProgress ->
                            captureInProgress = inProgress
                            onCaptureInProgressChanged(inProgress)
                        },
                        captureFailed = cameraError != null,
                        outputDirectory = outputDirectory,
                        modifier = Modifier.fillMaxWidth().height(CAMERA_CONTROL_ROW_HEIGHT_DP.dp),
                    )
                    Spacer(
                        Modifier.fillMaxWidth().height(layout.equalGap.dp)
                            .testTag("camera-gap-below-controls"),
                    )
                }
            }
        }
    } else {
        Column(modifier.fillMaxSize()) {
            CaptureTopBar(
                captureMode = captureMode,
                enabled = actionsEnabled && !captureOperationGate.isActive,
                onCaptureModeChange = onCaptureModeChange,
                onSettings = onSettings,
            )
            WritingPane(
                modifier = Modifier.fillMaxWidth().weight(1f),
                editor = editor,
                onEditorChange = onEditorChange,
                enabled = actionsEnabled,
            )
            TextButton(
                enabled = actionsEnabled && !captureOperationGate.isActive,
                onClick = {
                    if (!captureOperationGate.isActive) {
                        when (photoRetryPath(captureMode)) {
                            PhotoRetryPath.REOPEN_TYPED_ATTACHMENT_PICKER -> onChoosePhoto()
                            PhotoRetryPath.RETRY_SOURCE -> onRetryImage()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp)
                    .testTag("capture-attach-photo"),
            ) {
                Text("Attach photo")
            }
            FlowPrimaryButton(
                text = "Choose word",
                onClick = { if (!captureOperationGate.isActive) onContinue() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp)
                    .testTag("capture-choose-word"),
                enabled = actionsEnabled && !captureOperationGate.isActive && editor.text.isNotBlank(),
                showArrow = false,
            )
        }
    }
}

@Composable
private fun CaptureTopBar(
    captureMode: String,
    enabled: Boolean,
    onCaptureModeChange: (String) -> Unit,
    onSettings: () -> Unit,
) {
    Box(
        Modifier.fillMaxWidth().height(CAMERA_TOP_BAR_HEIGHT_DP.dp).testTag("capture-top-bar"),
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .width(176.dp)
                .height(CAMERA_MODE_SWITCH_HEIGHT_DP.dp)
                .testTag("capture-mode-switch"),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(Modifier.fillMaxSize().selectableGroup()) {
                ModeSelectorOption(
                    label = "Camera",
                    tag = "capture-mode-camera",
                    selected = captureMode == "Photo",
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    onClick = { onCaptureModeChange("Photo") },
                )
                ModeSelectorOption(
                    label = "Type",
                    tag = "capture-mode-type",
                    selected = captureMode == "Type",
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    onClick = { onCaptureModeChange("Type") },
                )
            }
        }
        androidx.compose.material3.IconButton(
            onClick = onSettings,
            enabled = enabled,
            modifier = Modifier.align(Alignment.CenterEnd).size(48.dp).testTag("capture-settings"),
        ) {
            androidx.compose.material3.Icon(
                MaterialSymbols.Settings,
                contentDescription = "Settings",
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun ModeSelectorOption(
    label: String,
    tag: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(1.dp)
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surface,
            )
            .then(
                if (selected) Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                else Modifier,
            )
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics { stateDescription = if (selected) "Selected" else "Not selected" }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            maxLines = 1,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun InputStatusPanel(
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
    val message = when {
        ocrLoading -> "Reading text…"
        ocrError?.startsWith("No text was found") == true -> "No text found."
        ocrError != null -> ocrError
        cameraError != null -> cameraError
        imageStatus != null -> imageStatus
        recoveryActions.reopenOcrChoices -> "Text choices are ready."
        else -> null
    } ?: return

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(max = 48.dp)
            .testTag("capture-status-line"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            message,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = if (ocrError != null || cameraError != null || imageFailed) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        when {
            recoveryActions.retryOcr -> TextButton(
                enabled = enabled && !operationGate.isActive,
                onClick = { if (!operationGate.isActive) onRetryOcr() },
            ) { Text("Retry") }
            recoveryActions.reopenOcrChoices -> TextButton(
                enabled = enabled && !operationGate.isActive,
                onClick = { if (!operationGate.isActive) onReopenOcrChooser() },
            ) { Text("Review") }
            recoveryActions.retryImage -> TextButton(
                enabled = enabled && !operationGate.isActive,
                onClick = { if (!operationGate.isActive) onRetryImage() },
            ) { Text("Retry") }
        }
    }
}

/** CameraX fills cameraInfo asynchronously after binding; do not block the composition thread. */
@Composable
private fun rememberTorchAvailability(controller: androidx.camera.view.LifecycleCameraController): Boolean {
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
    controller: androidx.camera.view.LifecycleCameraController?,
) {
    if (controller == null) {
        Box(modifier.background(Color.Black).testTag("camera-live-preview"))
    } else {
        CameraViewfinder(controller = controller, modifier = modifier)
    }
}

/** Gallery / shutter / torch controls share one fixed 48 dp row. */
@Composable
private fun CaptureRow(
    enabled: Boolean,
    operationGate: CaptureOperationGate,
    controller: androidx.camera.view.LifecycleCameraController?,
    hasTorch: Boolean,
    torchOn: Boolean,
    onToggleTorch: () -> Unit,
    onRequestCamera: () -> Unit,
    onOpenGallery: () -> Unit,
    onCaptured: (File) -> Unit,
    onCameraError: (String) -> Unit,
    onCaptureStateChanged: (Boolean) -> Unit,
    captureFailed: Boolean,
    outputDirectory: File,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.testTag("camera-controls-row").padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight().testTag("camera-gallery-slot"),
            contentAlignment = Alignment.CenterStart,
        ) {
            CaptureActionButton(
                enabled = enabled,
                operationGate = operationGate,
                onClick = onOpenGallery,
                contentDescription = "Choose a photo from this device",
                modifier = Modifier.testTag("camera-gallery-button"),
            ) { FlowIcons.Gallery(Modifier.size(24.dp)) }
        }

        Box(Modifier.width(CAMERA_SHUTTER_SIZE_DP.dp).fillMaxHeight().testTag("camera-shutter-slot"), contentAlignment = Alignment.Center) {
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
                    modifier = Modifier.testTag("camera-shutter"),
                )
            } else {
                CameraPermissionShutter(
                    enabled = enabled && !operationGate.isActive,
                    onRequestCamera = onRequestCamera,
                )
            }
        }

        Box(
            modifier = Modifier.weight(1f).fillMaxHeight().testTag("camera-torch-slot"),
            contentAlignment = Alignment.CenterEnd,
        ) {
            if (hasTorch) {
                Surface(
                    onClick = { if (enabled && !operationGate.isActive) onToggleTorch() },
                    enabled = enabled && !operationGate.isActive,
                    modifier = Modifier.size(CAMERA_SIDE_SLOT_SIZE_DP.dp)
                        .semantics {
                            contentDescription = "Torch"
                            stateDescription = if (torchOn) "On" else "Off"
                        }
                        .testTag("camera-torch-toggle"),
                    shape = CircleShape,
                    color = if (torchOn) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (torchOn) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    border = if (torchOn) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
                ) {
                    Box(contentAlignment = Alignment.Center) { FlowIcons.Torch(Modifier.size(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun CameraPermissionShutter(
    enabled: Boolean,
    onRequestCamera: () -> Unit,
) {
    Box(
        modifier = Modifier.size(CAMERA_SHUTTER_SIZE_DP.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onRequestCamera)
            .border(BorderStroke(3.dp, MaterialTheme.colorScheme.outline), CircleShape)
            .padding(6.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
            .semantics {
                contentDescription = "Enable camera"
                role = Role.Button
            },
    )
}

@Composable
private fun CaptureActionButton(
    enabled: Boolean,
    operationGate: CaptureOperationGate,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = { if (enabled && !operationGate.isActive) onClick() },
        enabled = enabled && !operationGate.isActive,
        modifier = modifier.size(CAMERA_SIDE_SLOT_SIZE_DP.dp).semantics { this.contentDescription = contentDescription },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
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
    BasicTextField(
        value = editor,
        onValueChange = onEditorChange,
        enabled = enabled,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(16.dp)
            .semantics { contentDescription = "Japanese sentence" }
            .testTag("capture-sentence-field"),
        textStyle = TextStyle(
            fontSize = 24.sp,
            lineHeight = 36.sp,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
    )
}
