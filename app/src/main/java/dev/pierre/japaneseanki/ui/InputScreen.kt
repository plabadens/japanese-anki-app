package dev.pierre.japaneseanki.ui

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pierre.japaneseanki.R
import java.io.File

/**
 * Input view of the portrait composition: an uncropped 4:3-sensor preview shown upright in a 3:4
 * viewport, with a Gallery/shutter row and a connected Camera/Type selector below it. Landscape
 * photographs remain inside this portrait composition; the app never becomes a landscape layout.
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

    if (captureMode == "Photo") {
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // A full-width upright 3:4 viewport is a design constraint, not something to shrink
            // when a short screen or large text leaves less room for chrome. The page scrolls.
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val previewSize = cameraPreviewSizeDp(maxWidth.value)
                Box(
                    modifier = Modifier
                        .width(previewSize.width.dp)
                        .height(previewSize.height.dp)
                        .align(Alignment.TopCenter)
                        .testTag("camera-preview-viewport"),
                ) {
                    PhotoPane(
                        modifier = Modifier.fillMaxSize(),
                        enabled = actionsEnabled,
                        controller = cameraController,
                        operationGate = captureOperationGate,
                    )
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
            }
            CaptureRow(
                enabled = actionsEnabled,
                operationGate = captureOperationGate,
                controller = cameraController,
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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CAMERA_CONTROL_ROW_HEIGHT_DP.dp),
            )
            CaptureModeSelector(
                captureMode = captureMode,
                onCaptureModeChange = onCaptureModeChange,
                enabled = actionsEnabled && !captureOperationGate.isActive,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Spacer(Modifier.height(8.dp))
        }
    } else {
        BoxWithConstraints(modifier.fillMaxSize()) {
            val writingHeight = (maxHeight - 280.dp).coerceAtLeast(220.dp)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                WritingPane(
                    modifier = Modifier.fillMaxWidth().height(writingHeight).padding(horizontal = 16.dp).padding(top = 8.dp),
                    editor = editor,
                    onEditorChange = onEditorChange,
                    enabled = actionsEnabled,
                )
                // Keep the attachment/OCR message slot stable; longer content scrolls within it.
                Box(Modifier.fillMaxWidth().height(INPUT_STATUS_SLOT_HEIGHT_DP.dp)) {
                    InputStatusPanel(
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = 16.dp),
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
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            enabled = actionsEnabled && !captureOperationGate.isActive,
                            onClick = { if (!captureOperationGate.isActive) onChoosePhoto() },
                        ) {
                            Text(if (imageAttached) stringResource(R.string.legacy_capture_change_photo) else stringResource(R.string.legacy_capture_add_photo_optional))
                        }
                        Spacer(Modifier.weight(1f))
                    }
                    FlowPrimaryButton(
                        text = stringResource(R.string.legacy_capture_continue_action),
                        onClick = { if (!captureOperationGate.isActive) onContinue() },
                        enabled = actionsEnabled && !captureOperationGate.isActive && editor.text.isNotBlank(),
                    )
                }
                CaptureModeSelector(
                    captureMode = captureMode,
                    onCaptureModeChange = onCaptureModeChange,
                    enabled = actionsEnabled && !captureOperationGate.isActive,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun CaptureModeSelector(
    captureMode: String,
    onCaptureModeChange: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .heightIn(min = INPUT_MODE_SELECTOR_MIN_HEIGHT_DP.dp)
            .testTag("input-mode-selector"),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.selectableGroup().heightIn(min = INPUT_MODE_SELECTOR_MIN_HEIGHT_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModeSelectorOption(
                label = stringResource(R.string.camera),
                tag = "input-mode-camera",
                selected = captureMode == "Photo",
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = { onCaptureModeChange("Photo") },
            )
            Box(
                Modifier
                    .width(1.dp)
                    .heightIn(min = INPUT_MODE_SELECTOR_MIN_HEIGHT_DP.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            ModeSelectorOption(
                label = stringResource(R.string.type),
                tag = "input-mode-type",
                selected = captureMode == "Type",
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = { onCaptureModeChange("Type") },
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
    val selectionState = stringResource(
        if (selected) R.string.legacy_capture_selected_state else R.string.legacy_capture_not_selected_state,
    )
    Box(
        modifier = modifier
            .heightIn(min = INPUT_MODE_SELECTOR_MIN_HEIGHT_DP.dp)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics { stateDescription = selectionState }
            .testTag(tag)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            maxLines = 1,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        )
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
                        ) { Text(stringResource(R.string.legacy_capture_retry_ocr)) }
                    }
                }
            }
            if (ocrLoading) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.legacy_capture_reading_the_photo_on_this_device), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (recoveryActions.reopenOcrChoices) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.legacy_capture_text_choices_are_ready), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(
                        enabled = enabled && !operationGate.isActive,
                        onClick = { if (!operationGate.isActive) onReopenOcrChooser() },
                    ) { Text(stringResource(R.string.legacy_capture_review_choices)) }
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
                        ) { Text(stringResource(R.string.legacy_capture_retry_photo)) }
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
                stringResource(R.string.legacy_capture_camera_access_is_needed_to_take_a_photo_ty),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }
    val hasFlash = rememberFlashAvailability(controller)
    var flashOn by remember(controller) { mutableStateOf(false) }
    val flashLabel = stringResource(R.string.legacy_capture_flash)
    val flashStateDescription = stringResource(
        if (flashOn) R.string.legacy_capture_flash_on_state else R.string.legacy_capture_flash_off_state,
    )
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
                        contentDescription = flashLabel
                        stateDescription = flashStateDescription
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
    onCaptured: (File) -> Unit,
    onCameraError: (String) -> Unit,
    onCaptureStateChanged: (Boolean) -> Unit,
    captureFailed: Boolean,
    outputDirectory: File,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.testTag("camera-controls-row").padding(horizontal = 16.dp, vertical = 8.dp),
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

        Box(Modifier.testTag("camera-shutter-slot"), contentAlignment = Alignment.Center) {
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
                FlowPrimaryButton(
                    text = stringResource(R.string.camera),
                    onClick = { if (enabled && !operationGate.isActive) onRequestCamera() },
                    modifier = Modifier.size(width = 120.dp, height = 48.dp).testTag("camera-permission-action"),
                    enabled = enabled && !operationGate.isActive,
                    showArrow = false,
                )
            }
        }

        Box(
            modifier = Modifier.weight(1f).fillMaxHeight().testTag("camera-type-slot"),
            contentAlignment = Alignment.CenterEnd,
        ) {}
    }
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
        val sentenceContentDescription = stringResource(R.string.sentence)
        OutlinedTextField(
            value = editor,
            onValueChange = onEditorChange,
            enabled = enabled,
            modifier = Modifier.fillMaxSize().padding(8.dp).semantics {
                contentDescription = sentenceContentDescription
            },
            placeholder = { Text(stringResource(R.string.legacy_capture_type_the_japanese_sentence), style = MaterialTheme.typography.bodyLarge) },
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
