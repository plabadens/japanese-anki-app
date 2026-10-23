package dev.pierre.japaneseanki

import android.content.Context
import android.content.ContextWrapper
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkerFactory
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.impl.Processor
import androidx.work.impl.WorkDatabase
import androidx.work.impl.WorkLauncherImpl
import androidx.work.impl.WorkManagerImpl
import androidx.work.impl.close
import androidx.work.impl.constraints.trackers.Trackers
import androidx.work.impl.utils.taskexecutor.WorkManagerTaskExecutor
import androidx.work.testing.TestScheduler
import androidx.work.testing.WorkManagerTestInitHelper
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Exercises the real WorkManager scheduler/database with a local transfer fake. No network and no
 * UniDic payload are involved; the fake persists its offset outside the worker, like the production
 * installer, so a replacement worker must resume from that exact offset before verify/install.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [37], qualifiers = "w390dp-h844dp-xxhdpi")
class UniDicWorkManagerLifecycleTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val workDatabaseDir = File(context.cacheDir, "unidic-work-database-${UUID.randomUUID()}")
    private val workContext = object : ContextWrapper(context) {
        override fun getApplicationContext(): Context = this
        override fun getNoBackupFilesDir(): File = workDatabaseDir.apply { mkdirs() }
    }
    private val workerExecutor = Executors.newFixedThreadPool(2)
    private val workExecutor = Executors.newSingleThreadExecutor()
    private val testExecutor = Executors.newSingleThreadExecutor()
    private val network = AtomicReference(UniDicNetworkState(networkHandle = 7L, metered = false))
    private val provisioner = PersistedOffsetProvisioner()
    private lateinit var configuration: Configuration
    private lateinit var workManagerImpl: WorkManagerImpl
    private lateinit var testScheduler: TestScheduler
    private lateinit var task: UniDicDownloadTask

    @Before
    fun initializeWorkManager() {
        context.getSharedPreferences(UniDicTaskStore.PREFERENCES, Context.MODE_PRIVATE)
            .edit().clear().commit()
        provisioner.transferState.delete()
        provisioner.installedMarker.delete()

        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker? {
                if (workerClassName != UniDicDownloadWorker::class.java.name) return null
                return UniDicDownloadWorker(appContext, workerParameters, provisioner) { network.get() }
            }
        }
        configuration = Configuration.Builder()
            .setWorkerFactory(factory)
            .setExecutor(workerExecutor)
            .setTaskExecutor(workExecutor)
            .build()
        initializePersistentWorkManager()
        task = UniDicDownloadTask(context)
    }

    @After
    fun closeWorkManager() {
        if (::task.isInitialized) {
            runCatching {
                testExecutor.submit {
                    task.cancel()
                    WorkManager.getInstance(context).cancelAllWork().result.get(20, TimeUnit.SECONDS)
                }.get(30, TimeUnit.SECONDS)
            }
            task.close()
        }
        if (::workManagerImpl.isInitialized) {
            runCatching { drainWorkExecutor() }
            runCatching { workManagerImpl.close() }
            runCatching { WorkManagerImpl.setDelegate(null) }
        }
        workerExecutor.shutdownNow()
        workExecutor.shutdownNow()
        testExecutor.shutdownNow()
        workDatabaseDir.deleteRecursively()
        provisioner.transferState.delete()
        provisioner.installedMarker.delete()
        context.getSharedPreferences(UniDicTaskStore.PREFERENCES, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun interruptedTransferPersistsProgressAndReplacementResumesThroughVerifyAndInstall() {
        val workManager = WorkManager.getInstance(context)
        val firstWorkId = testExecutor.submit<UUID> { task.start() }.get(10, TimeUnit.SECONDS)
        awaitState(workManager, firstWorkId, WorkInfo.State.ENQUEUED)
        assertTrue(
            "WorkManager test database must be on disk for the restart simulation",
            workDatabaseDir.listFiles().orEmpty().any { it.name.startsWith("androidx.work.workdb") },
        )
        setAllConstraintsMet(firstWorkId)

        val firstProgressArrived = awaitLatch(provisioner.firstProgress, 15)
        val firstAttemptInfo = awaitState(workManager, firstWorkId, WorkInfo.State.RUNNING)
        assertTrue(
            "controlled transfer did not publish its first persisted offset (work=$firstAttemptInfo)",
            firstProgressArrived,
        )
        val running = awaitState(workManager, firstWorkId, WorkInfo.State.RUNNING)
        assertEquals(UniDicTaskStore.PHASE_DOWNLOADING, running.progress.getString(UniDicTaskStore.KEY_PHASE))
        assertEquals(PERSISTED_OFFSET, running.progress.getLong(UniDicTaskStore.KEY_DOWNLOADED, 0L))
        assertEquals(PERSISTED_OFFSET, UniDicTaskStore(context).preferences.getLong(UniDicTaskStore.KEY_DOWNLOADED, 0L))
        assertTrue(UniDicTaskStore(context).hasPartialTransfer())

        // Result.retry() leaves the unique request in WorkManager's database; the task store and
        // partial-transfer file retain the byte offset independently of WorkInfo progress.
        provisioner.releaseFirstAttempt.countDown()
        network.set(UniDicNetworkState())
        val interrupted = awaitState(workManager, firstWorkId, WorkInfo.State.ENQUEUED)
        assertEquals(1, interrupted.runAttemptCount)
        assertEquals(UniDicTaskStore.PHASE_WAITING_WIFI, UniDicTaskStore(context).preferences.getString(UniDicTaskStore.KEY_PHASE, null))
        assertEquals(PERSISTED_OFFSET, UniDicTaskStore(context).preferences.getLong(UniDicTaskStore.KEY_DOWNLOADED, 0L))

        // Close the WorkManager engine and its on-disk Room database, then create a new engine on
        // that same database. This is the Robolectric process-restart simulation; no OS process is
        // killed, and the real host transfer remains an explicit NOT RUN boundary.
        restartWorkManager()
        val restartedWorkManager = WorkManager.getInstance(context)
        val restored = awaitState(restartedWorkManager, firstWorkId, WorkInfo.State.ENQUEUED)
        assertEquals(1, restored.runAttemptCount)
        assertEquals(PERSISTED_OFFSET, UniDicTaskStore(context).preferences.getLong(UniDicTaskStore.KEY_DOWNLOADED, 0L))

        network.set(UniDicNetworkState(networkHandle = 7L, metered = false))
        val resumedWorkId = testExecutor.submit<UUID> { task.resume() }.get(10, TimeUnit.SECONDS)
        assertNotEquals(firstWorkId, resumedWorkId)
        awaitState(restartedWorkManager, resumedWorkId, WorkInfo.State.ENQUEUED)
        setAllConstraintsMet(resumedWorkId)

        val completed = awaitState(restartedWorkManager, resumedWorkId, WorkInfo.State.SUCCEEDED)
        assertEquals(UniDicTaskStore.PHASE_READY, completed.outputData.getString(UniDicTaskStore.KEY_PHASE))
        assertEquals(UniDicResourceCatalog.ARCHIVE_BYTES, completed.outputData.getLong(UniDicTaskStore.KEY_TOTAL, 0L))
        assertEquals(PERSISTED_OFFSET, provisioner.resumedFrom.get())
        assertTrue(provisioner.installedMarker.isFile)
        assertEquals("verified-and-installed", provisioner.installedMarker.readText())
        assertEquals(UniDicTaskStore.PHASE_READY, UniDicTaskStore(context).preferences.getString(UniDicTaskStore.KEY_PHASE, null))
        assertFalsePartialTransfer()
    }

    private fun assertFalsePartialTransfer() {
        assertTrue("successful installation must clear the partial-transfer marker", !UniDicTaskStore(context).hasPartialTransfer())
        assertEquals(UniDicResourceCatalog.ARCHIVE_BYTES, UniDicTaskStore(context).preferences.getLong(UniDicTaskStore.KEY_DOWNLOADED, 0L))
    }

    private fun setAllConstraintsMet(id: UUID) {
        testExecutor.submit { testScheduler.setAllConstraintsMet(id) }.get(10, TimeUnit.SECONDS)
    }

    private fun drainWorkExecutor() {
        repeat(2) {
            shadowOf(Looper.getMainLooper()).idle()
            workExecutor.submit {}.get(10, TimeUnit.SECONDS)
        }
    }

    private fun initializePersistentWorkManager() {
        val managerAndScheduler = testExecutor.submit<Pair<WorkManagerImpl, TestScheduler>> {
            // The standard test initializer uses an in-memory Room database. Keep its test
            // scheduler, but use WorkDatabase.create(..., false) to prove persistence across close/reopen.
            val workTaskExecutor = WorkManagerTaskExecutor(configuration.taskExecutor)
            val workDatabase = WorkDatabase.create(
                workContext,
                workTaskExecutor.serialTaskExecutor,
                configuration.clock,
                false,
            )
            val trackers = Trackers(workContext, workTaskExecutor)
            val processor = Processor(workContext, configuration, workTaskExecutor, workDatabase)
            val scheduler = TestScheduler(
                workDatabase,
                WorkLauncherImpl(processor, workTaskExecutor),
                configuration.clock,
                configuration.runnableScheduler,
                WorkManagerTestInitHelper.ExecutorsMode.PRESERVE_EXECUTORS,
            )
            val manager = WorkManagerImpl(
                workContext,
                configuration,
                workTaskExecutor,
                workDatabase,
                listOf(scheduler),
                processor,
                trackers,
            )
            manager to scheduler
        }.get(20, TimeUnit.SECONDS)
        workManagerImpl = managerAndScheduler.first
        testScheduler = managerAndScheduler.second
        WorkManagerImpl.setDelegate(workManagerImpl)
    }

    private fun restartWorkManager() {
        task.close()
        drainWorkExecutor()
        workManagerImpl.close()
        WorkManagerImpl.setDelegate(null)
        initializePersistentWorkManager()
        task = UniDicDownloadTask(context)
    }

    private fun awaitLatch(latch: CountDownLatch, timeoutSeconds: Long): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
        while (System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (latch.await(25, TimeUnit.MILLISECONDS)) return true
        }
        shadowOf(Looper.getMainLooper()).idle()
        return latch.count == 0L
    }

    private fun awaitState(workManager: WorkManager, id: UUID, expected: WorkInfo.State): WorkInfo {
        repeat(400) {
            shadowOf(Looper.getMainLooper()).idle()
            val info = testExecutor.submit<WorkInfo?> {
                workManager.getWorkInfoById(id).get(10, TimeUnit.SECONDS)
            }.get(10, TimeUnit.SECONDS)
            if (info == null) {
                Thread.sleep(25)
                return@repeat
            }
            if (info.state == expected) return info
            Thread.sleep(25)
        }
        throw AssertionError("Work $id did not reach $expected")
    }

    private inner class PersistedOffsetProvisioner : UniDicDownloadProvisioner {
        val transferState = File(context.cacheDir, "unidic-resume-offset.txt")
        val installedMarker = File(context.filesDir, "unidic-test-installed.marker")
        val firstProgress = CountDownLatch(1)
        val releaseFirstAttempt = CountDownLatch(1)
        val attempts = AtomicInteger(0)
        val resumedFrom = AtomicLong(-1L)

        override fun prepare(
            context: Context,
            onStatus: (OfflineAnalyzerProgress) -> Unit,
            cancellation: ResourceCancellation,
        ) {
            if (attempts.getAndIncrement() == 0) {
                transferState.writeText(PERSISTED_OFFSET.toString())
                onStatus(
                    OfflineAnalyzerProgress(
                        OfflineAnalyzerPhase.DOWNLOADING,
                        PERSISTED_OFFSET,
                        UniDicResourceCatalog.ARCHIVE_BYTES,
                    ),
                )
                firstProgress.countDown()
                if (!releaseFirstAttempt.await(15, TimeUnit.SECONDS)) {
                    throw ResourceTransferException("controlled transfer gate timed out")
                }
                network.set(UniDicNetworkState())
                throw ResourceTransferException("simulated process interruption after partial transfer")
            }

            val offset = transferState.readText().toLong()
            resumedFrom.set(offset)
            onStatus(
                OfflineAnalyzerProgress(
                    OfflineAnalyzerPhase.DOWNLOADING,
                    offset,
                    UniDicResourceCatalog.ARCHIVE_BYTES,
                ),
            )
            onStatus(
                OfflineAnalyzerProgress(
                    OfflineAnalyzerPhase.VERIFYING,
                    UniDicResourceCatalog.ARCHIVE_BYTES,
                    UniDicResourceCatalog.ARCHIVE_BYTES,
                ),
            )
            onStatus(
                OfflineAnalyzerProgress(
                    OfflineAnalyzerPhase.INSTALLING,
                    UniDicResourceCatalog.ARCHIVE_BYTES,
                    UniDicResourceCatalog.ARCHIVE_BYTES,
                ),
            )
            installedMarker.writeText("verified-and-installed")
            transferState.delete()
            onStatus(
                OfflineAnalyzerProgress(
                    OfflineAnalyzerPhase.READY,
                    UniDicResourceCatalog.ARCHIVE_BYTES,
                    UniDicResourceCatalog.ARCHIVE_BYTES,
                ),
            )
        }
    }

    private companion object {
        const val PERSISTED_OFFSET = 1_048_576L
    }
}
