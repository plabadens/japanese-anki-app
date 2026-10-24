package dev.pierre.japaneseanki.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
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
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowValueAnimator

@RunWith(AndroidJUnit4::class)
@Config(sdk = [37], qualifiers = "w390dp-h844dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InputScreenLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun disablePlatformAnimations() {
        setAnimatorDurationScale(0f)
    }

    @After
    fun restorePlatformAnimations() {
        setAnimatorDurationScale(1f)
    }

    @Test
    fun ocrProgressAndFailureStateStayAccessibleAndSilent() {
        val loading = mutableStateOf(true)
        val error = mutableStateOf<String?>(null)
        val events = mutableListOf<TouchFeedbackEvent>()
        val dispatcher = TouchFeedbackDispatcher { event -> events += event }
        composeRule.setContent {
            CompositionLocalProvider(
                LocalMotionEnabled provides false,
                LocalTouchFeedbackDispatcher provides dispatcher,
            ) {
                MaterialTheme {
                    Box(Modifier.width(390.dp).height(796.dp)) {
                        InputScreen(
                            captureMode = "Photo",
                            onCaptureModeChange = {},
                            editor = TextFieldValue(""),
                            onEditorChange = {},
                            onContinue = {},
                            onOpenGallery = {},
                            onChoosePhoto = {},
                            onCaptured = {},
                            onCameraError = {},
                            outputDirectory = File("/unused-ocr-feedback-test"),
                            modifier = Modifier.fillMaxSize(),
                            cameraPermissionGranted = false,
                            ocrLoading = loading.value,
                            ocrError = error.value,
                        )
                    }
                }
            }
        }

        val busyStatus = composeRule.onNodeWithTag("capture-status-line").fetchSemanticsNode().config
        assertEquals(ProgressBarRangeInfo.Indeterminate, busyStatus[SemanticsProperties.ProgressBarRangeInfo])
        assertEquals("Reading text…", busyStatus[SemanticsProperties.StateDescription])
        assertTrue("Background OCR must not dispatch haptics", events.isEmpty())
        captureMotionEvidence("reduced-motion-ocr-progress.png")

        loading.value = false
        error.value = "OCR failed"
        composeRule.waitForIdle()
        composeRule.onNodeWithText("OCR failed").assertIsDisplayed()
        assertTrue("OCR errors must not dispatch haptics", events.isEmpty())
        captureMotionEvidence("reduced-motion-ocr-error.png")
    }

    @Test
    fun systemReducedMotionKeepsCaptureProgressStaticAndOperable() {
        val mode = mutableStateOf("Photo")
        val resolvedMotionEnabled = AtomicReference<Boolean?>(null)
        composeRule.setContent {
            FlowTheme(darkTheme = false) {
                val motionEnabled = LocalMotionEnabled.current
                SideEffect { resolvedMotionEnabled.set(motionEnabled) }
                Box(Modifier.width(390.dp).height(796.dp)) {
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
                        outputDirectory = File("/unused-system-motion-test"),
                        modifier = Modifier.fillMaxSize(),
                        cameraPermissionGranted = false,
                        ocrLoading = true,
                    )
                }
            }
        }

        composeRule.waitForIdle()
        assertEquals("Android's disabled animator setting must select reduced motion", false, resolvedMotionEnabled.get())
        val busyStatus = composeRule.onNodeWithTag("capture-status-line").fetchSemanticsNode().config
        assertEquals(ProgressBarRangeInfo.Indeterminate, busyStatus[SemanticsProperties.ProgressBarRangeInfo])
        assertEquals("Reading text…", busyStatus[SemanticsProperties.StateDescription])
        captureMotionEvidence("system-reduced-motion-capture-progress.png")

        composeRule.onNodeWithTag("capture-mode-type").performClick()
        assertEquals("Reduced motion must not block mode changes", "Type", mode.value)
        composeRule.onNodeWithTag("capture-sentence-field").assertIsDisplayed()
    }

    @Test
    fun systemTouchFeedbackDisabledKeepsCaptureModeOperable() {
        val mode = mutableStateOf("Photo")
        composeRule.setContent {
            FlowTheme(darkTheme = false) {
                Box(Modifier.width(390.dp).height(796.dp)) {
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
                        outputDirectory = File("/unused-system-haptic-test"),
                        modifier = Modifier.fillMaxSize(),
                        cameraPermissionGranted = false,
                    )
                }
            }
        }

        composeRule.onNodeWithTag("capture-mode-type").performClick()
        assertEquals("Disabling system touch feedback must not block mode changes", "Type", mode.value)
        composeRule.onNodeWithTag("capture-sentence-field").assertIsDisplayed()
        captureMotionEvidence("system-touch-feedback-disabled-type-mode.png")
    }

    @Test
    fun cameraTypeSelectionDispatchesOneTickOnlyWhenTheModeChanges() {
        val mode = mutableStateOf("Photo")
        val events = mutableListOf<TouchFeedbackEvent>()
        val dispatcher = TouchFeedbackDispatcher { event -> events += event }
        composeRule.setContent {
            CompositionLocalProvider(
                LocalMotionEnabled provides false,
                LocalTouchFeedbackDispatcher provides dispatcher,
            ) {
                MaterialTheme {
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
                        outputDirectory = File("/unused-mode-haptic-test"),
                        modifier = Modifier.fillMaxSize(),
                        cameraPermissionGranted = false,
                    )
                }
            }
        }

        composeRule.onNodeWithTag("capture-mode-type").performClick()
        assertEquals("Type", mode.value)
        assertEquals(listOf(TouchFeedbackEvent.MODE_SELECTION), events)
        composeRule.onNodeWithTag("capture-mode-type").performClick()
        assertEquals("Re-tapping the selected mode must be silent", listOf(TouchFeedbackEvent.MODE_SELECTION), events)
        composeRule.onNodeWithTag("capture-mode-camera").performClick()
        assertEquals("Photo", mode.value)
        assertEquals(
            listOf(TouchFeedbackEvent.MODE_SELECTION, TouchFeedbackEvent.MODE_SELECTION),
            events,
        )
        captureMotionEvidence("touch-feedback-disabled-type-mode.png")
    }

    @Test
    fun cameraGeometryAndModeSwitchStayFixedAcrossStatusAndModeChanges() {
        val mode = mutableStateOf("Photo")
        val cameraError = mutableStateOf<String?>(null)
        val ocrLoading = mutableStateOf(false)
        val ocrError = mutableStateOf<String?>(null)

        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.width(390.dp).height(796.dp)) {
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
                    )
                }
            }
        }

        val density = composeRule.density.density
        val topBar = composeRule.onNodeWithTag("capture-top-bar").fetchSemanticsNode().boundsInRoot
        val modeSwitchBefore = composeRule.onNodeWithTag("capture-mode-switch").fetchSemanticsNode().boundsInRoot
        val settingsBefore = composeRule.onNodeWithTag("capture-settings").fetchSemanticsNode().boundsInRoot
        val previewBefore = composeRule.onNodeWithTag("camera-preview-viewport").fetchSemanticsNode().boundsInRoot
        val controlsBefore = composeRule.onNodeWithTag("camera-controls-row").fetchSemanticsNode().boundsInRoot
        val gapAbove = composeRule.onNodeWithTag("camera-gap-above-controls").fetchSemanticsNode().boundsInRoot
        val gapBelow = composeRule.onNodeWithTag("camera-gap-below-controls").fetchSemanticsNode().boundsInRoot

        assertEquals(56f * density, topBar.height, 1f)
        assertEquals(48f * density, modeSwitchBefore.height, 1f)
        assertEquals(48f * density, settingsBefore.width, 1f)
        assertEquals(390f * density, previewBefore.width, 1f)
        assertEquals(520f * density, previewBefore.height, 2f)
        assertEquals(72f * density, controlsBefore.height, 1f)
        assertEquals(gapAbove.height, gapBelow.height, 1f)
        assertEquals(74f * density, gapAbove.height, 1f)
        val galleryCenterX = composeRule.onNodeWithTag("camera-gallery-slot").fetchSemanticsNode().boundsInRoot.center.x
        val shutterCenterX = composeRule.onNodeWithTag("camera-shutter-slot").fetchSemanticsNode().boundsInRoot.center.x
        val torchCenterX = composeRule.onNodeWithTag("camera-torch-slot").fetchSemanticsNode().boundsInRoot.center.x
        val x1 = shutterCenterX - galleryCenterX
        val x2 = torchCenterX - shutterCenterX
        assertEquals("Camera side controls must be symmetric around the shutter", x1, x2, 1f)
        assertEquals(controlsBefore.center.x, shutterCenterX, 1f)
        composeRule.onNodeWithTag("capture-mode-camera").assertIsSelected()
        composeRule.onNodeWithTag("capture-mode-type").assertIsNotSelected()
        assertTrue(composeRule.onAllNodesWithTag("input-mode-selector").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithTag("camera-type-slot").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Input").fetchSemanticsNodes().isEmpty())

        cameraError.value = "Could not capture a photo. Try again."
        composeRule.waitForIdle()
        assertEquals(previewBefore, composeRule.onNodeWithTag("camera-preview-viewport").fetchSemanticsNode().boundsInRoot)
        assertEquals(controlsBefore, composeRule.onNodeWithTag("camera-controls-row").fetchSemanticsNode().boundsInRoot)
        ocrLoading.value = true
        ocrError.value = "Could not read this photo."
        composeRule.waitForIdle()
        assertEquals(previewBefore, composeRule.onNodeWithTag("camera-preview-viewport").fetchSemanticsNode().boundsInRoot)
        assertEquals(controlsBefore, composeRule.onNodeWithTag("camera-controls-row").fetchSemanticsNode().boundsInRoot)

        composeRule.onNodeWithTag("capture-mode-type").performClick()
        assertEquals("Type", mode.value)
        composeRule.onNodeWithTag("capture-mode-switch").assertIsDisplayed()
        assertEquals(modeSwitchBefore, composeRule.onNodeWithTag("capture-mode-switch").fetchSemanticsNode().boundsInRoot)
        assertEquals(settingsBefore, composeRule.onNodeWithTag("capture-settings").fetchSemanticsNode().boundsInRoot)
        composeRule.onNodeWithTag("capture-mode-type").assertIsSelected()
        composeRule.onNodeWithTag("capture-sentence-field").assertIsDisplayed()
        composeRule.onNodeWithTag("capture-attach-photo").assertIsDisplayed()
        composeRule.onNodeWithTag("capture-choose-word").assertIsNotEnabled()
    }

    @OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
    @Test
    fun captureModeGroupIsOneConnectedAccessibleChoiceWithIcons() {
        val mode = mutableStateOf("Photo")
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.width(390.dp).height(796.dp)) {
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
                        outputDirectory = File("/unused-connected-mode-group-test"),
                        modifier = Modifier.fillMaxSize(),
                        cameraPermissionGranted = false,
                    )
                }
            }
        }

        val density = composeRule.density.density
        val camera = composeRule.onNodeWithTag("capture-mode-camera").fetchSemanticsNode()
        val type = composeRule.onNodeWithTag("capture-mode-type").fetchSemanticsNode()
        assertEquals("Each mode target must meet the 48 dp floor", 48f * density, camera.boundsInRoot.height, 1f)
        assertEquals(
            "The two modes must read as one connected group",
            with(composeRule.density) {
                androidx.compose.material3.ButtonGroupDefaults.ConnectedSpaceBetween.toPx()
            },
            type.boundsInRoot.left - camera.boundsInRoot.right,
            1f,
        )
        assertEquals(
            "A single-choice mode switch must expose radio semantics",
            androidx.compose.ui.semantics.Role.RadioButton,
            camera.config[SemanticsProperties.Role],
        )
        composeRule.onNodeWithTag("capture-mode-camera-icon", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("capture-mode-type-icon", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Camera").assertIsDisplayed()
        composeRule.onNodeWithText("Type").assertIsDisplayed()
        composeRule.onNodeWithTag("capture-mode-type").performClick()
        assertEquals("Type", mode.value)
        composeRule.onNodeWithTag("capture-mode-type").assertIsSelected()
        composeRule.onNodeWithTag("capture-mode-camera").assertIsNotSelected()
    }

    @Test
    fun captureModeGroupLabelsSurviveTwoHundredPercentFontScale() {
        val screenWidth = mutableStateOf(360)
        composeRule.setContent {
            val density = composeRule.density
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                MaterialTheme {
                    Box(Modifier.width(screenWidth.value.dp).height(828.dp)) {
                        InputScreen(
                            captureMode = "Photo",
                            onCaptureModeChange = {},
                            editor = TextFieldValue(""),
                            onEditorChange = {},
                            onContinue = {},
                            onOpenGallery = {},
                            onChoosePhoto = {},
                            onCaptured = {},
                            onCameraError = {},
                            outputDirectory = File("/unused-connected-mode-group-test"),
                            modifier = Modifier.fillMaxSize(),
                            cameraPermissionGranted = false,
                        )
                    }
                }
            }
        }

        listOf(360, 390).forEach { width ->
            screenWidth.value = width
            composeRule.waitForIdle()
            listOf("Camera", "Type").forEach { label ->
                val layouts = mutableListOf<TextLayoutResult>()
                val node = composeRule.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode()
                val action = node.config[SemanticsActions.GetTextLayoutResult]
                assertTrue("Could not inspect the $label mode label", action.action?.invoke(layouts) == true)
                val layout = layouts.single()
                assertEquals("The $label label must stay on one line at ${width}dp", 1, layout.lineCount)
                assertTrue(
                    "The $label label must not be squeezed at 200 % font scale on a ${width}dp screen",
                    layout.multiParagraph.width + 1f >= layout.multiParagraph.maxIntrinsicWidth,
                )
            }
        }
        assertTrue(
            "Decorative mode icons step aside at 200 % font scale so neither label is squeezed",
            composeRule.onAllNodesWithTag("capture-mode-camera-icon", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun shortScreenKeepsTheBarAndControlRowWhileThePreviewNarrows() {
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.width(390.dp).height(572.dp)) {
                    InputScreen(
                        captureMode = "Photo",
                        onCaptureModeChange = {},
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
                    )
                }
            }
        }

        val density = composeRule.density.density
        composeRule.onNodeWithTag("capture-top-bar").assertHeightIsEqualTo(56.dp)
        composeRule.onNodeWithTag("camera-controls-row").assertHeightIsEqualTo(72.dp)
        val preview = composeRule.onNodeWithTag("camera-preview-viewport").fetchSemanticsNode().boundsInRoot
        assertEquals(315f * density, preview.width, 1f)
        assertEquals(420f * density, preview.height, 1f)
        assertEquals(195f * density, preview.center.x, 1f)
        assertEquals(12f * density, composeRule.onNodeWithTag("camera-gap-above-controls").fetchSemanticsNode().boundsInRoot.height, 1f)
        assertEquals(12f * density, composeRule.onNodeWithTag("camera-gap-below-controls").fetchSemanticsNode().boundsInRoot.height, 1f)
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

    private fun setAnimatorDurationScale(scale: Float) {
        // Robolectric exposes the system animator scale through this protected shadow hook.
        ShadowValueAnimator::class.java
            .getDeclaredMethod("setDurationScale", Float::class.javaPrimitiveType)
            .apply { isAccessible = true }
            .invoke(null, scale)
    }

    private fun captureMotionEvidence(name: String) {
        composeRule.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val directory = File("build/robolectric-evidence/motion-feedback")
        if (!directory.isDirectory) directory.mkdirs()
        if (!directory.isDirectory) {
            throw AssertionError(
                "Could not create motion-feedback evidence directory: " +
                    "${directory.absolutePath}; canWrite=${directory.canWrite()}"
            )
        }

        val screenshot = File(directory, name)
        var bitmapDimensions = "unavailable"
        var lastFailure: Throwable? = null
        repeat(2) { attempt ->
            if (attempt > 0) {
                composeRule.waitForIdle()
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            }

            var bitmap: Bitmap? = null
            try {
                val capturedBitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
                bitmap = capturedBitmap
                bitmapDimensions = "${capturedBitmap.width}x${capturedBitmap.height}"
                val encoded = screenshot.outputStream().use { output ->
                    capturedBitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                }
                if (encoded && screenshot.isFile && screenshot.length() > 0L) return
                lastFailure = AssertionError("PNG encoding or file verification failed on attempt ${attempt + 1}")
            } catch (failure: Exception) {
                lastFailure = failure
            } catch (failure: AssertionError) {
                lastFailure = failure
            } finally {
                bitmap?.recycle()
            }
        }

        val failure = AssertionError(
            "Motion evidence was not written: ${screenshot.absolutePath}; " +
                "exists=${screenshot.exists()}; length=${screenshot.length()}; " +
                "bitmap=$bitmapDimensions"
        )
        lastFailure?.let { failure.initCause(it) }
        throw failure
    }

}
