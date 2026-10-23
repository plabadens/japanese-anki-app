package dev.pierre.japaneseanki

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [37], qualifiers = "w390dp-h844dp-xxhdpi")
class UniDicDownloadWorkerInjectionTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var taskStore: UniDicTaskStore

    @Before
    fun clearOnlyDictionaryTaskState() {
        taskStore = UniDicTaskStore(context)
        check(taskStore.preferences.edit().clear().commit())
    }

    @Test
    fun unapprovedMeteredNetworkStopsBeforeFixtureTransfersBytes() = runBlocking {
        val starts = AtomicReference(false)
        val network = UniDicNetworkState(networkHandle = 41L, metered = true)
        val provisioner = UniDicDownloadProvisioner { _, onStatus, cancellation ->
            if (cancellation.isCancelled()) throw ResourceCancelledException()
            starts.set(true)
            onStatus(OfflineAnalyzerProgress(OfflineAnalyzerPhase.DOWNLOADING, 1L, 8L))
        }
        val result = worker(provisioner, Data.EMPTY) { network }.doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        assertFalse("an unapproved metered transfer must be stopped before the provisioner runs", starts.get())
        assertEquals(UniDicTaskStore.PHASE_WAITING_MOBILE, taskStore.preferences.getString(UniDicTaskStore.KEY_PHASE, null))
        assertEquals(0L, taskStore.preferences.getLong(UniDicTaskStore.KEY_DOWNLOADED, 0L))
        assertFalse(FileProbe.activeArchiveExists(context))
    }

    @Test
    fun approvedMeteredTransferStopsOnNetworkChangeAndRetainsHonestPartialCount() = runBlocking {
        val network = AtomicReference(UniDicNetworkState(networkHandle = 41L, metered = true))
        val provisioner = UniDicDownloadProvisioner { _, onStatus, cancellation ->
            onStatus(OfflineAnalyzerProgress(OfflineAnalyzerPhase.DOWNLOADING, 4L, 8L))
            network.set(UniDicNetworkState(networkHandle = 42L, metered = true))
            if (cancellation.isCancelled()) throw ResourceCancelledException()
            onStatus(OfflineAnalyzerProgress(OfflineAnalyzerPhase.DOWNLOADING, 8L, 8L))
        }
        val input = Data.Builder()
            .putLong(UniDicTaskStore.KEY_APPROVED_NETWORK, 41L)
            .build()

        val result = worker(provisioner, input) { network.get() }.doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        assertEquals(UniDicTaskStore.PHASE_WAITING_MOBILE, taskStore.preferences.getString(UniDicTaskStore.KEY_PHASE, null))
        assertEquals(4L, taskStore.preferences.getLong(UniDicTaskStore.KEY_DOWNLOADED, 0L))
        assertEquals(UniDicResourceCatalog.ARCHIVE_BYTES, taskStore.preferences.getLong(UniDicTaskStore.KEY_TOTAL, 0L))
        assertTrue(taskStore.hasPartialTransfer())
    }

    @Test
    fun onlySuccessfulProvisioningPublishesReadyAndRetryCanSucceedAfterFailure() = runBlocking {
        val first = worker(
            UniDicDownloadProvisioner { _, _, _ -> throw ResourceTransferException("controlled transfer failure") },
            Data.EMPTY,
        ) { UniDicNetworkState(networkHandle = 7L, metered = false) }
        val failed = first.doWork()

        assertTrue(failed is ListenableWorker.Result.Failure)
        assertEquals(
            UniDicTaskStore.FAILURE_DICTIONARY_FAILED,
            (failed as ListenableWorker.Result.Failure).outputData.getString(UniDicTaskStore.KEY_FAILURE),
        )
        assertEquals(
            UniDicTaskStore.PHASE_FAILED,
            (failed as ListenableWorker.Result.Failure).outputData.getString(UniDicTaskStore.KEY_PHASE),
        )

        val retry = worker(
            UniDicDownloadProvisioner { _, onStatus, _ ->
                onStatus(OfflineAnalyzerProgress(OfflineAnalyzerPhase.VERIFYING, 8L, 8L))
                onStatus(OfflineAnalyzerProgress(OfflineAnalyzerPhase.INSTALLING, 8L, 8L))
                onStatus(OfflineAnalyzerProgress(OfflineAnalyzerPhase.READY, 8L, 8L))
            },
            Data.EMPTY,
        ) { UniDicNetworkState(networkHandle = 7L, metered = false) }
        val succeeded = retry.doWork()

        assertTrue(succeeded is ListenableWorker.Result.Success)
        assertEquals(UniDicTaskStore.PHASE_READY, taskStore.preferences.getString(UniDicTaskStore.KEY_PHASE, null))
        assertEquals(
            UniDicResourceCatalog.ARCHIVE_BYTES,
            taskStore.preferences.getLong(UniDicTaskStore.KEY_DOWNLOADED, 0L),
        )
        assertFalse(taskStore.hasPartialTransfer())
    }

    @Test
    fun integrityFailureCannotPublishReadyOrActivateAnArchive() = runBlocking {
        val provisioner = UniDicDownloadProvisioner { _, onStatus, _ ->
            onStatus(OfflineAnalyzerProgress(OfflineAnalyzerPhase.VERIFYING, 8L, 8L))
            throw ResourceIntegrityException("controlled wrong SHA-256")
        }

        val result = worker(provisioner, Data.EMPTY) { UniDicNetworkState(networkHandle = 7L, metered = false) }.doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        val output = (result as ListenableWorker.Result.Failure).outputData
        assertEquals(UniDicTaskStore.FAILURE_DICTIONARY_INVALID, output.getString(UniDicTaskStore.KEY_FAILURE))
        assertEquals(UniDicTaskStore.PHASE_FAILED, output.getString(UniDicTaskStore.KEY_PHASE))
        assertFalse(
            "integrity failure must not mark the task ready",
            taskStore.preferences.getString(UniDicTaskStore.KEY_PHASE, null) == UniDicTaskStore.PHASE_READY,
        )
        assertFalse(taskStore.hasPartialTransfer())
        assertFalse(FileProbe.activeArchiveExists(context))
    }

    private fun worker(
        provisioner: UniDicDownloadProvisioner,
        input: Data,
        networkState: (Context) -> UniDicNetworkState,
    ): UniDicDownloadWorker = TestListenableWorkerBuilder
        .from(context, UniDicDownloadWorker::class.java)
        .setInputData(input)
        .setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker? {
                if (workerClassName != UniDicDownloadWorker::class.java.name) return null
                return UniDicDownloadWorker(appContext, workerParameters, provisioner, networkState)
            }
        })
        .build()

    private object FileProbe {
        fun activeArchiveExists(context: Context): Boolean =
            context.filesDir.resolve("first-run-resources/${UniDicResourceCatalog.ARCHIVE_FILE_NAME}").exists()
    }
}
