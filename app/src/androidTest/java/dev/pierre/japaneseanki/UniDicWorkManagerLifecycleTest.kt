package dev.pierre.japaneseanki

import android.content.Context
import android.graphics.Bitmap
import android.util.Log

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkerFactory
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UniDicWorkManagerLifecycleTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var task: UniDicDownloadTask

    @Before
    fun prepareTaskController() {
        task = UniDicDownloadTask(context)
    }

    @After
    fun clearTaskController() {
        task.cancel()
        currentGate.get()?.release?.countDown()
        task.close()
        WorkManager.getInstance(context).cancelUniqueWork(UniDicTaskStore.WORK_NAME).result.get(20, TimeUnit.SECONDS)
        UniDicTaskStore(context).clearTask()
    }

    @Test
    fun transferSurvivesWizardExitBackgroundingAndRepeatedStartWithoutDuplicateWork() {
        val gate = checkNotNull(currentGate.get())
        val consentTitle = context.getString(R.string.use_mobile_title)
        if (composeRule.onAllNodesWithText(consentTitle).fetchSemanticsNodes().isNotEmpty()) {
            // The app uses the emulator's actual active network; reflect that same handle in the test worker.
            networkState.set(currentNetworkState(context))
            Log.i(TAG, "metered consent prompt displayed; active network handle=${networkState.get().networkHandle}")
            composeRule.onNodeWithText(context.getString(R.string.use_mobile)).assertExists().performClick()
        }

        val workId = awaitTaskId(context)
        Log.i(TAG, "unique task id=$workId; releasing connected-network constraint for controlled fixture")
        val driver = WorkManagerTestInitHelper.getTestDriver(context)
        assertNotNull(driver)
        driver!!.setAllConstraintsMet(workId)
        assertTrue("controlled WorkManager Worker did not enter transfer", gate.entered.await(15, TimeUnit.SECONDS))
        val running = awaitState(workId, WorkManager.getInstance(context), WorkInfo.State.RUNNING)
        Log.i(
            TAG,
            "active id=${running.id}; state=${running.state}; phase=${running.progress.getString(UniDicTaskStore.KEY_PHASE)}; bytes=${running.progress.getLong(UniDicTaskStore.KEY_DOWNLOADED, 0L)}/${running.progress.getLong(UniDicTaskStore.KEY_TOTAL, 0L)}",
        )
        val processDump = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("ps -A")
        val processLines = android.os.ParcelFileDescriptor.AutoCloseInputStream(processDump)
            .bufferedReader().useLines { lines -> lines.filter { "japaneseanki" in it }.toList() }
        Log.i(
            TAG,
            "instrumentation pid=${android.os.Process.myPid()}; testPackage=${InstrumentationRegistry.getInstrumentation().context.packageName}; targetPackage=${context.packageName}; processes=${processLines.joinToString(" | ")}",
        )
        assertEquals(UniDicTaskStore.PHASE_DOWNLOADING, running.progress.getString(UniDicTaskStore.KEY_PHASE))
        assertTrue(running.progress.getLong(UniDicTaskStore.KEY_DOWNLOADED, 0L) > 0L)

        composeRule.onNodeWithText(context.getString(R.string.use_app)).assertExists().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Type").assertExists()
        composeRule.onNodeWithText("Dictionary").assertDoesNotExist()
        saveDeviceScreenshot("active-transfer-capture.png")
        Log.i(TAG, "wizard Use app route reached Capture while the same Worker remained RUNNING")

        val secondController = UniDicDownloadTask(context)
        try {
            assertEquals("re-entry must observe the same unique task", workId, secondController.start())
            val unique = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(UniDicTaskStore.WORK_NAME).get(15, TimeUnit.SECONDS)
            assertEquals(1, unique.count { it.state == WorkInfo.State.RUNNING })
        } finally {
            secondController.close()
        }

        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("input keyevent KEYCODE_HOME").close()
        Thread.sleep(500)
        Log.i(TAG, "Activity sent to HOME; work state=${WorkManager.getInstance(context).getWorkInfoById(workId).get(15, TimeUnit.SECONDS)?.state}")
        assertEquals(
            "backgrounding the Activity must not stop unique work",
            WorkInfo.State.RUNNING,
            WorkManager.getInstance(context).getWorkInfoById(workId).get(15, TimeUnit.SECONDS)?.state,
        )

        gate.release.countDown()
        val completed = awaitState(workId, WorkManager.getInstance(context), WorkInfo.State.SUCCEEDED)
        Log.i(TAG, "fixture completed id=${completed.id}; output phase=${completed.outputData.getString(UniDicTaskStore.KEY_PHASE)}")
        assertEquals(UniDicTaskStore.PHASE_READY, completed.outputData.getString(UniDicTaskStore.KEY_PHASE))
        assertEquals(UniDicResourceCatalog.ARCHIVE_BYTES, completed.outputData.getLong(UniDicTaskStore.KEY_TOTAL, 0L))
    }

    private fun saveDeviceScreenshot(name: String) {
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val directory = context.externalMediaDirs.firstOrNull()?.resolve("additional_test_output")
            ?: throw AssertionError("App-specific external evidence directory unavailable")
        check(directory.mkdirs() || directory.isDirectory)
        FileOutputStream(File(directory, name)).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        bitmap.recycle()
    }

    companion object {
        private const val TAG = "UniDicLifecycleTest"
        private val context: Context by lazy { InstrumentationRegistry.getInstrumentation().targetContext }
        private val workerExecutor = Executors.newFixedThreadPool(2)
        private val currentGate = AtomicReference<GateProvisioner?>()
        private val networkState = AtomicReference(UniDicNetworkState())

        @BeforeClass
        @JvmStatic
        fun initializeControlledWorkManager() {
            currentGate.set(GateProvisioner())
            networkState.set(currentNetworkState(context))
            val factory = object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker? {
                    if (workerClassName != UniDicDownloadWorker::class.java.name) return null
                    val gate = checkNotNull(currentGate.get())
                    return UniDicDownloadWorker(appContext, workerParameters, gate) { networkState.get() }
                }
            }
            val configuration = Configuration.Builder()
                .setWorkerFactory(factory)
                .setExecutor(workerExecutor)
                .setTaskExecutor(SynchronousExecutor())
                .build()
            WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration)
        }

        @AfterClass
        @JvmStatic
        fun closeControlledWorkManager() {
            runCatching { WorkManager.getInstance(context).cancelAllWork().result.get(20, TimeUnit.SECONDS) }
            runCatching { WorkManagerTestInitHelper.closeWorkDatabase() }
            workerExecutor.shutdownNow()
        }

        private fun awaitTaskId(context: Context): java.util.UUID {
            val store = UniDicTaskStore(context)
            repeat(100) {
                store.currentWorkId()?.let { return it }
                Thread.sleep(50)
            }
            throw AssertionError("Activity did not enqueue the durable UniDic work request")
        }

        private fun awaitState(
            workId: java.util.UUID,
            workManager: WorkManager,
            expected: WorkInfo.State,
        ): WorkInfo {
            repeat(300) {
                val info = workManager.getWorkInfoById(workId).get(10, TimeUnit.SECONDS)
                    ?: throw AssertionError("WorkManager lost task $workId")
                if (info.state == expected) return info
                Thread.sleep(50)
            }
            throw AssertionError("Worker $workId did not reach $expected")
        }

        private class GateProvisioner : UniDicDownloadProvisioner {
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)

            override fun prepare(
                context: Context,
                onStatus: (OfflineAnalyzerProgress) -> Unit,
                cancellation: ResourceCancellation,
            ) {
                if (cancellation.isCancelled()) throw ResourceCancelledException()
                onStatus(
                    OfflineAnalyzerProgress(
                        OfflineAnalyzerPhase.DOWNLOADING,
                        downloadedBytes = 1_048_576L,
                        totalBytes = UniDicResourceCatalog.ARCHIVE_BYTES,
                    ),
                )
                entered.countDown()
                if (!release.await(45, TimeUnit.SECONDS)) {
                    throw ResourceTransferException("controlled transfer gate timed out")
                }
                if (cancellation.isCancelled()) throw ResourceCancelledException()
                onStatus(
                    OfflineAnalyzerProgress(
                        OfflineAnalyzerPhase.VERIFYING,
                        downloadedBytes = UniDicResourceCatalog.ARCHIVE_BYTES,
                        totalBytes = UniDicResourceCatalog.ARCHIVE_BYTES,
                    ),
                )
                onStatus(
                    OfflineAnalyzerProgress(
                        OfflineAnalyzerPhase.INSTALLING,
                        downloadedBytes = UniDicResourceCatalog.ARCHIVE_BYTES,
                        totalBytes = UniDicResourceCatalog.ARCHIVE_BYTES,
                    ),
                )
                onStatus(
                    OfflineAnalyzerProgress(
                        OfflineAnalyzerPhase.READY,
                        downloadedBytes = UniDicResourceCatalog.ARCHIVE_BYTES,
                        totalBytes = UniDicResourceCatalog.ARCHIVE_BYTES,
                    ),
                )
            }
        }
    }
}
