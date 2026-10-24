package dev.pierre.japaneseanki

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.hardware.camera2.CameraManager
import androidx.camera.view.LifecycleCameraController
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.pierre.japaneseanki.ui.CameraShutterButton
import dev.pierre.japaneseanki.ui.CameraViewfinder
import dev.pierre.japaneseanki.ui.CaptureOperationGate
import dev.pierre.japaneseanki.ui.rememberCameraController
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Device-only proof for ML Kit and the real CameraX sensor/capture pipeline. */
@RunWith(AndroidJUnit4::class)
class CameraOcrInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun bundledJapaneseOcrRecognizesLocalFixtureWithoutNetwork() = runTest {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val image = File.createTempFile("offline-japanese-ocr-", ".jpg", context.cacheDir)
        try {
            instrumentation.context.assets.open("textbook-specimen.jpg").use { input ->
                image.outputStream().use { output -> input.copyTo(output) }
            }
            val result = JapaneseOcr().recognize(image)
            assertTrue("Bundled model failed on Japanese fixture: ${result.text}", result.text.contains("図書館で本を読みました"))
        } finally {
            image.delete()
        }
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
                    androidx.compose.runtime.SideEffect { controllerRef.set(controller) }
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

            composeRule.waitUntil(20_000) { controllerRef.get()?.initializationFuture?.isDone == true }
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
