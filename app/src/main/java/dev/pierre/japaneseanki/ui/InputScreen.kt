package dev.pierre.japaneseanki.ui

import androidx.camera.view.LifecycleCameraController
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SingleChoiceSegmentedButtonRowScope
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import dev.pierre.japaneseanki.R
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File

/** Static camera capability for real-screen previews; production binds the live CameraX controller. */
internal data class CaptureCameraPreviewState(
    val hasTorch: Boolean,
    val torchOn: Boolean = false,
)

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
    confirmationMessage: String? = null,
    onSettings: () -> Unit = {},
    cameraPreviewState: CaptureCameraPreviewState? = null,
) {
    val cameraController = if (
        captureMode == "Photo" && cameraPermissionGranted && cameraPreviewState == null && !LocalInspectionMode.current
    ) {
        rememberCameraController()
    } else {
        null
    }
    var captureInProgress by remember { mutableStateOf(false) }
    val captureOperationGate = remember { CaptureOperationGate() }
    val actionsEnabled = captureActionsEnabled(enabled, captureInProgress || captureOperationGate.isActive)
    val context = LocalContext.current
    val hasTorch = cameraPreviewState?.hasTorch
        ?: if (cameraController != null) rememberTorchAvailability(cameraController) else false
    var liveTorchOn by remember(cameraController) { mutableStateOf(false) }
    val torchOn = cameraPreviewState?.torchOn ?: liveTorchOn
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(cameraController, lifecycleOwner) {
        val observer = if (cameraController == null) null else LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                cameraController.cameraControl?.enableTorch(false)
                liveTorchOn = false
            }
        }
        observer?.let(lifecycleOwner.lifecycle::addObserver)
        onDispose {
            cameraController?.cameraControl?.enableTorch(false)
            observer?.let(lifecycleOwner.lifecycle::removeObserver)
            liveTorchOn = false
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
                            confirmationMessage = confirmationMessage,
                        )
                    }
                    CaptureRow(
                        enabled = actionsEnabled,
                        operationGate = captureOperationGate,
                        controller = cameraController,
                        cameraPermissionGranted = cameraPermissionGranted,
                        hasTorch = hasTorch,
                        torchOn = torchOn,
                        onToggleTorch = {
                            val next = !torchOn
                            if (cameraPreviewState == null) {
                                val future = cameraController?.cameraControl?.enableTorch(next)
                                if (future != null) {
                                    liveTorchOn = next
                                    future.addListener(
                                        {
                                            if (runCatching { future.get() }.isFailure && liveTorchOn == next) {
                                                liveTorchOn = !next
                                            }
                                        },
                                        ContextCompat.getMainExecutor(context),
                                    )
                                }
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
                Text(stringResource(R.string.attach_photo))
            }
            FlowPrimaryButton(
                text = stringResource(R.string.choose_word),
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
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.align(Alignment.Center)
                .width(176.dp)
                .height(CAMERA_MODE_SWITCH_HEIGHT_DP.dp)
                .testTag("capture-mode-switch"),
        ) {
            ModeSelectorOption(
                label = stringResource(R.string.camera),
                tag = "capture-mode-camera",
                selected = captureMode == "Photo",
                enabled = enabled,
                index = 0,
                count = 2,
                modifier = Modifier.weight(1f),
                onClick = { onCaptureModeChange("Photo") },
            )
            ModeSelectorOption(
                label = stringResource(R.string.type),
                tag = "capture-mode-type",
                selected = captureMode == "Type",
                enabled = enabled,
                index = 1,
                count = 2,
                modifier = Modifier.weight(1f),
                onClick = { onCaptureModeChange("Type") },
            )
        }
        androidx.compose.material3.IconButton(
            onClick = onSettings,
            enabled = enabled,
            modifier = Modifier.align(Alignment.CenterEnd).size(48.dp).testTag("capture-settings"),
        ) {
            androidx.compose.material3.Icon(
                MaterialSymbols.Settings,
                contentDescription = stringResource(R.string.settings),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun SingleChoiceSegmentedButtonRowScope.ModeSelectorOption(
    label: String,
    tag: String,
    selected: Boolean,
    enabled: Boolean,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val feedback = LocalTouchFeedbackDispatcher.current
    SegmentedButton(
        selected = selected,
        onClick = {
            if (!selected) {
                feedback.dispatch(TouchFeedbackEvent.MODE_SELECTION)
                onClick()
            }
        },
        shape = SegmentedButtonDefaults.itemShape(index, count),
        modifier = modifier.fillMaxHeight().testTag(tag),
        enabled = enabled,
        colors = SegmentedButtonDefaults.colors(
            activeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            activeContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            activeBorderColor = MaterialTheme.colorScheme.outline,
            inactiveContainerColor = MaterialTheme.colorScheme.surface,
            inactiveContentColor = MaterialTheme.colorScheme.onSurface,
            inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
        icon = {},
        label = {
            Text(label, maxLines = 1, style = MaterialTheme.typography.labelLarge)
        },
    )
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
    confirmationMessage: String?,
) {
    val recoveryActions = inputRecoveryActions(
        ocrLoading = ocrLoading,
        ocrFailed = ocrError != null,
        hasOcrChoices = ocrChoicesAvailable,
        imageFailed = imageFailed,
    )
    val noTextError = stringResource(R.string.legacy_capture_no_text_was_found_type_or_edit_the_sentenc)
    val message = when {
        ocrLoading -> stringResource(R.string.reading_photo)
        ocrError == noTextError -> stringResource(R.string.ocr_empty)
        ocrError != null -> ocrError
        cameraError != null -> cameraError
        confirmationMessage != null -> confirmationMessage
        imageStatus != null -> imageStatus
        recoveryActions.reopenOcrChoices -> stringResource(R.string.legacy_capture_text_choices_are_ready)
        else -> null
    } ?: return
    val motionEnabled = LocalMotionEnabled.current

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(max = 48.dp)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                if (ocrLoading) {
                    progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                    stateDescription = message
                }
            }
            .testTag("capture-status-line"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(
            visible = confirmationMessage != null && message == confirmationMessage,
            enter = if (motionEnabled) {
                fadeIn(tween(140)) + scaleIn(initialScale = 0.88f, animationSpec = tween(140))
            } else {
                EnterTransition.None
            },
            exit = ExitTransition.None,
        ) {
            FlowIcons.Check(Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        }
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
            ) { Text(stringResource(R.string.retry)) }
            recoveryActions.reopenOcrChoices -> TextButton(
                enabled = enabled && !operationGate.isActive,
                onClick = { if (!operationGate.isActive) onReopenOcrChooser() },
            ) { Text(stringResource(R.string.legacy_capture_review_choices)) }
            recoveryActions.retryImage -> TextButton(
                enabled = enabled && !operationGate.isActive,
                onClick = { if (!operationGate.isActive) onRetryImage() },
            ) { Text(stringResource(R.string.retry)) }
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
    cameraPermissionGranted: Boolean,
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
    val torchLabel = stringResource(R.string.torch)
    val torchState = stringResource(
        if (torchOn) R.string.legacy_capture_flash_on_state
        else R.string.legacy_capture_flash_off_state,
    )
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
                contentDescription = stringResource(R.string.legacy_capture_choose_photo_from_device),
                modifier = Modifier.testTag("camera-gallery-button"),
            ) { FlowIcons.Gallery(Modifier.size(24.dp)) }
        }

        Box(Modifier.width(CAMERA_SHUTTER_SIZE_DP.dp).fillMaxHeight().testTag("camera-shutter-slot"), contentAlignment = Alignment.Center) {
            if (cameraPermissionGranted) {
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
                            contentDescription = torchLabel
                            stateDescription = torchState
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
    val cameraLabel = stringResource(R.string.allow_camera)
    Box(
        modifier = Modifier.size(CAMERA_SHUTTER_SIZE_DP.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onRequestCamera)
            .border(BorderStroke(3.dp, MaterialTheme.colorScheme.outline), CircleShape)
            .padding(6.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
            .semantics {
                contentDescription = cameraLabel
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
    val sentenceFieldLabel = stringResource(R.string.sentence)
    BasicTextField(
        value = editor,
        onValueChange = onEditorChange,
        enabled = enabled,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(16.dp)
            .semantics { contentDescription = sentenceFieldLabel }
            .testTag("capture-sentence-field"),
        textStyle = TextStyle(
            fontSize = 24.sp,
            lineHeight = 36.sp,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
    )
}
