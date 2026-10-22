package dev.pierre.japaneseanki.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.hardware.camera2.CameraManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.TextFieldValue
import androidx.camera.view.LifecycleCameraController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class InputScreenLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun cameraViewportAndControlBoundsStayFixedAndBothModesShareTheSelector() {
        val mode = mutableStateOf("Photo")
        val cameraError = mutableStateOf<String?>(null)
        val ocrLoading = mutableStateOf(false)
        val ocrError = mutableStateOf<String?>(null)
        val imageStatus = mutableStateOf<String?>(null)

        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, fontScale = 2f)) {
                MaterialTheme {
                    // Keep the full fixed preview and control row visible while the
                    // mode selector still has to scroll on this compact, 200%-text pane.
                    Box(Modifier.width(320.dp).height(540.dp)) {
                        InputScreen(
                            captureMode = mode.value,
                            onCaptureModeChange = { mode.value = it },
                            editor = TextFieldValue(""),
                            onEditorChange = {},
                            onContinue = {},
                            onOpenGallery = {},
                            onChoosePhoto = {},
                            onCaptured = {},
                            onCameraError = {},
                            outputDirectory = File("/unused-camera-layout-test"),
                            modifier = Modifier.fillMaxSize(),
                            cameraPermissionGranted = false,
                            cameraError = cameraError.value,
                            ocrLoading = ocrLoading.value,
                            ocrError = ocrError.value,
                            imageStatus = imageStatus.value,
                            imageFailed = imageStatus.value != null,
                        )
                    }
                }
            }
        }

        val previewBefore = composeRule.onNodeWithTag("camera-preview-viewport")
            .fetchSemanticsNode().boundsInRoot
        val controlsBefore = composeRule.onNodeWithTag("camera-controls-row")
            .fetchSemanticsNode().boundsInRoot
        val density = composeRule.density.density
        assertEquals(320f * density, previewBefore.width, 1f)
        assertEquals(320f * 4f / 3f * density, previewBefore.height, 2f)
        assertEquals(CAMERA_CONTROL_ROW_HEIGHT_DP * density, controlsBefore.height, 1f)

        val leftSlot = composeRule.onNodeWithTag("camera-gallery-slot").fetchSemanticsNode().boundsInRoot
        val rightSlot = composeRule.onNodeWithTag("camera-type-slot").fetchSemanticsNode().boundsInRoot
        val shutterSlot = composeRule.onNodeWithTag("camera-shutter-slot").fetchSemanticsNode().boundsInRoot
        assertEquals("Capture action slots must have equal width", leftSlot.width, rightSlot.width, 1f)
        assertEquals("The center action stays centered", controlsBefore.center.x, shutterSlot.center.x, 1f)
        composeRule.onNodeWithTag("camera-gallery-button").assertExists()
        composeRule.onNodeWithTag("input-mode-camera").assertIsSelected()
        composeRule.onNodeWithTag("input-mode-type").assertIsNotSelected()

        cameraError.value = "Camera failed. Try again."
        composeRule.waitForIdle()
        assertEquals(previewBefore, composeRule.onNodeWithTag("camera-preview-viewport").fetchSemanticsNode().boundsInRoot)
        assertEquals(controlsBefore, composeRule.onNodeWithTag("camera-controls-row").fetchSemanticsNode().boundsInRoot)

        ocrLoading.value = true
        ocrError.value = "OCR is still retryable."
        imageStatus.value = "Preparing photo for Anki…"
        composeRule.waitForIdle()
        assertEquals(previewBefore, composeRule.onNodeWithTag("camera-preview-viewport").fetchSemanticsNode().boundsInRoot)
        assertEquals(controlsBefore, composeRule.onNodeWithTag("camera-controls-row").fetchSemanticsNode().boundsInRoot)

        composeRule.onNodeWithTag("input-mode-type").performScrollTo().performClick()
        assertEquals("Type", mode.value)
        composeRule.onNodeWithTag("input-mode-selector").performScrollTo()
        composeRule.onNodeWithTag("input-mode-camera").assertIsDisplayed()
        composeRule.onNodeWithTag("input-mode-type").assertIsDisplayed().assertIsSelected()
        composeRule.onNodeWithTag("input-mode-camera").assertIsNotSelected()
        val selectorBounds = composeRule.onNodeWithTag("input-mode-selector").fetchSemanticsNode().boundsInRoot
        assertTrue("The 200%-text selector stays inside its 320dp viewport", selectorBounds.left >= -1f)
        assertTrue("The 200%-text selector stays inside its 320dp viewport", selectorBounds.right <= 320f * density + 1f)

        composeRule.onNodeWithTag("input-mode-camera").performScrollTo().performClick()
        assertEquals("Photo", mode.value)
        composeRule.onNodeWithTag("input-mode-camera").assertIsSelected()
    }

    @Test
    fun shutterComposableKeepsItsRealBoundsAcrossReadyAndErrorStates() {
        val failed = mutableStateOf(false)
        composeRule.setContent {
            val context = LocalContext.current
            val controller = remember { LifecycleCameraController(context) }
            MaterialTheme {
                CameraShutterButton(
                    controller = controller,
                    enabled = true,
                    outputDirectory = File("/unused-camera-layout-test"),
                    onCaptured = {},
                    onError = {},
                    onCaptureStateChanged = {},
                    captureFailed = failed.value,
                    operationGate = remember { CaptureOperationGate() },
                    modifier = Modifier.testTag("camera-shutter-test"),
                )
            }
        }

        val readyBounds = composeRule.onNodeWithTag("camera-shutter-test").fetchSemanticsNode().boundsInRoot
        composeRule.onNodeWithTag("camera-shutter-test")
            .assertWidthIsEqualTo(CAMERA_SHUTTER_SIZE_DP.dp)
            .assertHeightIsEqualTo(CAMERA_SHUTTER_SIZE_DP.dp)
        failed.value = true
        composeRule.waitForIdle()
        val errorBounds = composeRule.onNodeWithTag("camera-shutter-test").fetchSemanticsNode().boundsInRoot
        assertEquals(readyBounds, errorBounds)
    }

    @Test
    fun liveCameraPreviewMatchesItsViewportAndShutterSavesAnImage() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.CAMERA)
        assertEquals(
            "Camera permission must be granted to the target app",
            PackageManager.PERMISSION_GRANTED,
            context.checkSelfPermission(Manifest.permission.CAMERA),
        )
        assertTrue(
            "The API-37 camera provider exposes no cameras",
            context.getSystemService(CameraManager::class.java).cameraIdList.isNotEmpty(),
        )

        val outputDirectory = File(context.cacheDir, "camera-capture-${System.nanoTime()}")
        assertTrue("Could not create camera output directory", outputDirectory.mkdirs())
        val capturedFile = AtomicReference<File?>(null)
        val cameraError = AtomicReference<String?>(null)

        val controllerRef = AtomicReference<LifecycleCameraController?>(null)
        try {
            composeRule.setContent {
                val lifecycleOwner = remember { ResumedTestLifecycleOwner() }
                CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                    val controller = rememberCameraController()
                    SideEffect { controllerRef.set(controller) }
                    MaterialTheme {
                        Column {
                            Box(
                                Modifier.width(320.dp).height(427.dp)
                                    .testTag("camera-preview-viewport"),
                            ) {
                                CameraViewfinder(controller, Modifier.fillMaxSize())
                            }
                            CameraShutterButton(
                                controller = controller,
                                enabled = true,
                                outputDirectory = outputDirectory,
                                onCaptured = { capturedFile.set(it) },
                                onError = { cameraError.set(it) },
                                onCaptureStateChanged = {},
                                captureFailed = false,
                                operationGate = remember { CaptureOperationGate() },
                                modifier = Modifier.testTag("camera-shutter"),
                            )
                        }
                    }
                }
            }

            composeRule.waitUntil(20_000) {
                controllerRef.get()?.initializationFuture?.isDone == true
            }
            val controller = controllerRef.get()
            assertNotNull("Camera controller was not created", controller)
            val initializationFailure = runCatching { controller!!.initializationFuture.get() }.exceptionOrNull()
            assertNull("CameraX initialization failed: $initializationFailure", initializationFailure)
            val hasCameraInfo = AtomicReference<Boolean?>(null)
            composeRule.runOnIdle { hasCameraInfo.set(controller!!.cameraInfo != null) }
            assertEquals("CameraX did not expose camera information", true, hasCameraInfo.get())

            val viewportBounds = composeRule.onNodeWithTag("camera-preview-viewport")
                .fetchSemanticsNode().boundsInRoot
            val livePreviewBounds = composeRule.onNodeWithTag("camera-live-preview")
                .fetchSemanticsNode().boundsInRoot
            assertEquals("PreviewView must fill the fixed camera viewport", viewportBounds, livePreviewBounds)

            composeRule.onNodeWithTag("camera-shutter").performClick()
            composeRule.waitUntil(20_000) { capturedFile.get() != null || cameraError.get() != null }
            assertNull("Camera capture failed: ${cameraError.get()}", cameraError.get())

            val imageFile = capturedFile.get()
            assertNotNull("Camera callback did not produce a file", imageFile)
            assertTrue("Camera image is empty", imageFile!!.isFile && imageFile.length() > 0L)
            val bitmap = BitmapFactory.decodeFile(imageFile.path)
            assertNotNull("Captured camera image did not decode", bitmap)
            try {
                assertTrue("Captured image width is invalid", bitmap!!.width > 0)
                assertTrue("Captured image height is invalid", bitmap.height > 0)
            } finally {
                bitmap?.recycle()
            }
        } finally {
            outputDirectory.deleteRecursively()
        }
    }

    private class ResumedTestLifecycleOwner : LifecycleOwner {
        override val lifecycle: Lifecycle = LifecycleRegistry(this).apply {
            currentState = Lifecycle.State.RESUMED
        }
    }
}
