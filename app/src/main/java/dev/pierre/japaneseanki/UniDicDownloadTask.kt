package dev.pierre.japaneseanki

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.Constraints
import androidx.work.workDataOf
import androidx.core.content.getSystemService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

internal data class UniDicNetworkState(
    val networkHandle: Long? = null,
    val metered: Boolean = true,
) {
    val available: Boolean get() = networkHandle != null
    val unmetered: Boolean get() = available && !metered
}

internal data class UniDicTaskSnapshot(
    val workInfo: WorkInfo?,
    val userState: String,
    val phase: String?,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val failureCode: String?,
    val hadPartialTransfer: Boolean,
    val hasTask: Boolean,
) {
    val isRunningOrQueued: Boolean
        get() = workInfo?.state == WorkInfo.State.RUNNING || workInfo?.state == WorkInfo.State.ENQUEUED ||
            workInfo?.state == WorkInfo.State.BLOCKED

    val isReady: Boolean get() = workInfo?.state == WorkInfo.State.SUCCEEDED
    val isFailed: Boolean get() = workInfo?.state == WorkInfo.State.FAILED
    val isPaused: Boolean get() = userState == UniDicTaskStore.USER_PAUSED
    val isCancelled: Boolean get() = userState == UniDicTaskStore.USER_CANCELLED
}

/** One unique, durable WorkManager task. Progress lives in WorkInfo; user pause/cancel intent is durable too. */
internal class UniDicDownloadTask(context: Context) {
    private val appContext = context.applicationContext
    private val store = UniDicTaskStore(appContext)
    private val workManager = WorkManager.getInstance(appContext)
    private val workId = MutableStateFlow(store.currentWorkId())
    private val userState = MutableStateFlow(store.userState())
    private val preferenceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == UniDicTaskStore.KEY_WORK_ID) workId.value = store.currentWorkId()
        if (key == UniDicTaskStore.KEY_USER_STATE) userState.value = store.userState()
    }

    init {
        store.preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observe(): Flow<UniDicTaskSnapshot> = combine(
        workId.flatMapLatest { id ->
            if (id == null) {
                flowOf<WorkInfo?>(null)
            } else {
                flow {
                    while (true) {
                        val info = withContext(Dispatchers.IO) {
                            runCatching { workManager.getWorkInfoById(id).get() }.getOrNull()
                        }
                        emit(info)
                        if (info?.state == WorkInfo.State.SUCCEEDED || info?.state == WorkInfo.State.FAILED ||
                            info?.state == WorkInfo.State.CANCELLED
                        ) break
                        delay(1_000)
                    }
                }
            }
        },
        userState,
    ) { info, state -> store.snapshot(info, state) }

    fun close() {
        store.preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
    }

    fun networkState(): UniDicNetworkState = currentNetworkState(appContext)

    fun approvedNetworkHandle(): Long? = store.mobileApproval()

    fun deferredMobileNetworkHandle(): Long? = store.deferredMobileNetwork()

    fun deferMobileNetwork(handle: Long?) = store.deferMobileNetwork(handle)

    suspend fun ensureStartedIfNever() {
        val id = store.currentWorkId()
        if (id == null) {
            start(forceReplace = false)
            return
        }
        val lookup = withContext(Dispatchers.IO) {
            runCatching { workManager.getWorkInfoById(id).get() }
        }
        if (lookup.isSuccess && lookup.getOrNull() == null) {
            store.clearTask()
            start(forceReplace = true)
        }
    }

    fun start(approvedMobileHandle: Long? = null, forceReplace: Boolean = false): UUID = synchronized(COMMAND_LOCK) {
        if (!forceReplace) store.currentWorkId()?.let { return@synchronized it }
        val request = OneTimeWorkRequestBuilder<UniDicDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(
                        if (approvedMobileHandle == null) NetworkType.UNMETERED else NetworkType.CONNECTED,
                    )
                    .build(),
            )
            .setInputData(
                workDataOf(UniDicTaskStore.KEY_APPROVED_NETWORK to (approvedMobileHandle ?: NO_APPROVED_NETWORK)),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        store.begin(request.id, approvedMobileHandle)
        workManager.enqueueUniqueWork(
            UniDicTaskStore.WORK_NAME,
            if (forceReplace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request,
        )
        request.id
    }

    fun pause() = synchronized(COMMAND_LOCK) {
        store.setUserState(UniDicTaskStore.USER_PAUSED)
        workManager.cancelUniqueWork(UniDicTaskStore.WORK_NAME)
    }

    fun cancel() = synchronized(COMMAND_LOCK) {
        store.setUserState(UniDicTaskStore.USER_CANCELLED)
        store.clearMobileApproval()
        store.clearDeferredMobileNetwork()
        workManager.cancelUniqueWork(UniDicTaskStore.WORK_NAME)
    }

    fun resume() = start(forceReplace = true)

    fun useMobile(handle: Long) = start(approvedMobileHandle = handle, forceReplace = true)

    fun retry() = start(forceReplace = true)

    fun snapshot(info: WorkInfo?, state: String = store.userState()): UniDicTaskSnapshot =
        store.snapshot(info, state)

    private companion object {
        const val NO_APPROVED_NETWORK = -1L
        val COMMAND_LOCK = Any()
    }
}

internal class UniDicTaskStore(context: Context) {
    val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun currentWorkId(): UUID? = preferences.getString(KEY_WORK_ID, null)?.let { value ->
        runCatching { UUID.fromString(value) }.getOrNull()
    }
    fun userState(): String = preferences.getString(KEY_USER_STATE, USER_NONE) ?: USER_NONE

    fun begin(id: UUID, approvedMobileHandle: Long?) {
        val editor = preferences.edit()
            .putString(KEY_WORK_ID, id.toString())
            .putString(KEY_USER_STATE, USER_NONE)
            .putLong(KEY_APPROVED_NETWORK, approvedMobileHandle ?: NO_APPROVED_NETWORK)
            .putString(KEY_PHASE, PHASE_VERIFYING)
            .putLong(KEY_DOWNLOADED, 0L)
            .putLong(KEY_TOTAL, UniDicResourceCatalog.ARCHIVE_BYTES)
        if (approvedMobileHandle != null) editor.remove(KEY_DEFERRED_NETWORK)
        editor.commit()
    }

    fun setUserState(state: String) {
        preferences.edit().putString(KEY_USER_STATE, state).commit()
    }

    fun clearTask() {
        preferences.edit()
            .remove(KEY_WORK_ID)
            .putString(KEY_USER_STATE, USER_NONE)
            .remove(KEY_PHASE)
            .remove(KEY_DOWNLOADED)
            .remove(KEY_TOTAL)
            .remove(KEY_DEFERRED_NETWORK)
            .commit()
    }

    fun clearMobileApproval() {
        preferences.edit().putLong(KEY_APPROVED_NETWORK, NO_APPROVED_NETWORK).commit()
    }

    fun mobileApproval(): Long? = preferences.getLong(KEY_APPROVED_NETWORK, NO_APPROVED_NETWORK)
        .takeIf { it != NO_APPROVED_NETWORK }

    fun deferredMobileNetwork(): Long? = preferences.getLong(KEY_DEFERRED_NETWORK, NO_APPROVED_NETWORK)
        .takeIf { it != NO_APPROVED_NETWORK }

    fun deferMobileNetwork(handle: Long?) {
        val editor = preferences.edit()
        if (handle == null) editor.remove(KEY_DEFERRED_NETWORK)
        else editor.putLong(KEY_DEFERRED_NETWORK, handle)
        editor.commit()
    }

    fun clearDeferredMobileNetwork() {
        preferences.edit().remove(KEY_DEFERRED_NETWORK).commit()
    }

    fun hasPartialTransfer(): Boolean = preferences.getBoolean(KEY_HAD_PARTIAL, false)

    fun updateProgress(phase: String, downloaded: Long, total: Long) {
        val editor = preferences.edit()
            .putString(KEY_PHASE, phase)
            .putLong(KEY_DOWNLOADED, downloaded.coerceAtLeast(0L))
            .putLong(KEY_TOTAL, total.coerceAtLeast(0L))
        if (downloaded > 0) editor.putBoolean(KEY_HAD_PARTIAL, true)
        editor.commit()
    }

    fun markReady() {
        preferences.edit()
            .putString(KEY_USER_STATE, USER_NONE)
            .putString(KEY_PHASE, PHASE_READY)
            .putLong(KEY_DOWNLOADED, UniDicResourceCatalog.ARCHIVE_BYTES)
            .putLong(KEY_TOTAL, UniDicResourceCatalog.ARCHIVE_BYTES)
            .putBoolean(KEY_HAD_PARTIAL, false)
            .commit()
    }

    fun snapshot(info: WorkInfo?, userState: String): UniDicTaskSnapshot {
        val data = when {
            info?.state == WorkInfo.State.FAILED -> info.outputData
            else -> info?.progress
        }
        val storedPhase = preferences.getString(KEY_PHASE, null)
        val phase = data?.getString(KEY_PHASE) ?: storedPhase
        val downloaded = data?.getLong(KEY_DOWNLOADED, -1L)?.takeIf { it >= 0 }
            ?: preferences.getLong(KEY_DOWNLOADED, 0L)
        val total = data?.getLong(KEY_TOTAL, -1L)?.takeIf { it >= 0 }
            ?: preferences.getLong(KEY_TOTAL, UniDicResourceCatalog.ARCHIVE_BYTES)
        val failure = info?.outputData?.getString(KEY_FAILURE)
        val hasTask = currentWorkId() != null
        return UniDicTaskSnapshot(
            workInfo = info,
            userState = userState,
            phase = phase,
            downloadedBytes = downloaded.coerceAtMost(total.coerceAtLeast(0L)),
            totalBytes = total,
            failureCode = failure,
            hadPartialTransfer = hasPartialTransfer(),
            hasTask = hasTask,
        )
    }

    companion object {
        const val WORK_NAME = "first-run-unidic"
        const val PREFERENCES = "first_run_dictionary_task"
        const val KEY_WORK_ID = "work_id"
        const val KEY_USER_STATE = "user_state"
        const val KEY_APPROVED_NETWORK = "approved_network"
        const val KEY_DEFERRED_NETWORK = "deferred_network"
        const val KEY_PHASE = "phase"
        const val KEY_DOWNLOADED = "downloaded"
        const val KEY_TOTAL = "total"
        const val KEY_FAILURE = "failure"
        const val KEY_HAD_PARTIAL = "had_partial"

        const val FAILURE_DICTIONARY_FAILED = "dictionary_failed"
        const val FAILURE_DICTIONARY_INVALID = "dictionary_invalid"
        const val FAILURE_DICTIONARY_INSTALL_FAILED = "dictionary_install_failed"
        const val FAILURE_STORAGE_FULL = "storage_full"

        const val USER_NONE = "none"
        const val USER_PAUSED = "paused"
        const val USER_CANCELLED = "cancelled"

        const val PHASE_DOWNLOADING = "downloading"
        const val PHASE_VERIFYING = "verifying"
        const val PHASE_INSTALLING = "installing"
        const val PHASE_WAITING_WIFI = "waiting_wifi"
        const val PHASE_WAITING_MOBILE = "waiting_mobile"
        const val PHASE_READY = "ready"
        const val PHASE_FAILED = "failed"

        const val CHANNEL_ID = "downloads"
        const val NOTIFICATION_ID = 44031
        const val ACTION_PAUSE = "dev.pierre.japaneseanki.action.PAUSE_UNIDIC"
        const val ACTION_CANCEL = "dev.pierre.japaneseanki.action.CANCEL_UNIDIC"
        const val EXTRA_OPEN_DOWNLOADS = "open_downloads"

        private const val NO_APPROVED_NETWORK = -1L
    }
}

internal fun currentNetworkState(context: Context): UniDicNetworkState {
    val manager = context.applicationContext.getSystemService<ConnectivityManager>() ?: return UniDicNetworkState()
    val network = manager.activeNetwork ?: return UniDicNetworkState()
    val capabilities = manager.getNetworkCapabilities(network) ?: return UniDicNetworkState()
    val connected = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    if (!connected) return UniDicNetworkState()
    return UniDicNetworkState(
        networkHandle = network.networkHandle,
        metered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
    )
}

internal class UniDicDownloadWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    private val taskStore = UniDicTaskStore(applicationContext)
    private val approvedNetwork = inputData.getLong(
        UniDicTaskStore.KEY_APPROVED_NETWORK,
        -1L,
    ).takeIf { it >= 0L }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        createNotificationChannel(applicationContext)
        updateForeground(UniDicTaskStore.PHASE_VERIFYING, 0L, UniDicResourceCatalog.ARCHIVE_BYTES)
        publishProgress(UniDicTaskStore.PHASE_VERIFYING, 0L, UniDicResourceCatalog.ARCHIVE_BYTES)

        fun mobileConsentMissing(): Boolean {
            val state = currentNetworkState(applicationContext)
            return state.available && state.metered && state.networkHandle != approvedNetwork
        }

        fun noNetwork(): Boolean = !currentNetworkState(applicationContext).available
        var downloadedBytes = 0L

        try {
            OfflineJapaneseAnalyzer.shared(applicationContext).prepare(
                onStatus = { progress ->
                    val phase = when (progress.phase) {
                        OfflineAnalyzerPhase.DOWNLOADING -> UniDicTaskStore.PHASE_DOWNLOADING
                        OfflineAnalyzerPhase.VERIFYING -> UniDicTaskStore.PHASE_VERIFYING
                        OfflineAnalyzerPhase.INSTALLING -> UniDicTaskStore.PHASE_INSTALLING
                        OfflineAnalyzerPhase.READY -> UniDicTaskStore.PHASE_READY
                    }
                    val bytes = progress.downloadedBytes?.also { downloadedBytes = it } ?: downloadedBytes
                    val total = progress.totalBytes ?: UniDicResourceCatalog.ARCHIVE_BYTES
                    publishIfNeeded(phase, bytes, total)
                },
                cancellation = ResourceCancellation {
                    isStopped || mobileConsentMissing()
                },
            )
            taskStore.markReady()
            publishProgress(UniDicTaskStore.PHASE_READY, UniDicResourceCatalog.ARCHIVE_BYTES, UniDicResourceCatalog.ARCHIVE_BYTES)
            Result.success(
                workDataOf(
                    UniDicTaskStore.KEY_PHASE to UniDicTaskStore.PHASE_READY,
                    UniDicTaskStore.KEY_DOWNLOADED to UniDicResourceCatalog.ARCHIVE_BYTES,
                    UniDicTaskStore.KEY_TOTAL to UniDicResourceCatalog.ARCHIVE_BYTES,
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (cancelled: ResourceCancelledException) {
            if (isStopped) throw CancellationException("UniDic work was stopped")
            when {
                mobileConsentMissing() -> waitForMobileConsent()
                noNetwork() -> waitForNetwork()
                else -> fail(UniDicTaskStore.FAILURE_DICTIONARY_FAILED)
            }
        } catch (failure: ResourceStorageException) {
            fail(UniDicTaskStore.FAILURE_STORAGE_FULL)
        } catch (failure: ResourceIntegrityException) {
            fail(UniDicTaskStore.FAILURE_DICTIONARY_INVALID)
        } catch (failure: ResourceArchiveException) {
            fail(UniDicTaskStore.FAILURE_DICTIONARY_INVALID)
        } catch (failure: ResourceActivationException) {
            fail(UniDicTaskStore.FAILURE_DICTIONARY_INSTALL_FAILED)
        } catch (failure: ResourceTransferException) {
            when {
                mobileConsentMissing() -> waitForMobileConsent()
                noNetwork() -> waitForNetwork()
                else -> fail(UniDicTaskStore.FAILURE_DICTIONARY_FAILED)
            }
        } catch (failure: IOException) {
            when {
                mobileConsentMissing() -> waitForMobileConsent()
                noNetwork() -> waitForNetwork()
                else -> fail(UniDicTaskStore.FAILURE_DICTIONARY_FAILED)
            }
        } catch (failure: Exception) {
            Log.e(TAG, "UniDic installation failed", failure)
            fail(UniDicTaskStore.FAILURE_DICTIONARY_INSTALL_FAILED)
        } catch (failure: LinkageError) {
            Log.e(TAG, "UniDic native analyzer could not be loaded", failure)
            fail(UniDicTaskStore.FAILURE_DICTIONARY_INSTALL_FAILED)
        }
    }

    private var lastPublishedBytes = -1L
    private var lastPublishedAt = 0L
    private var lastPublishedPhase: String? = null

    private fun publishIfNeeded(phase: String, bytes: Long, total: Long) {
        val now = android.os.SystemClock.elapsedRealtime()
        val phaseChanged = phase != lastPublishedPhase
        val transferUpdate = phase == UniDicTaskStore.PHASE_DOWNLOADING &&
            (bytes == 0L || bytes >= lastPublishedBytes + 1024 * 1024 || now - lastPublishedAt >= 1_000 || bytes == total)
        if (!phaseChanged && !transferUpdate) return
        lastPublishedAt = now
        lastPublishedBytes = bytes
        lastPublishedPhase = phase
        publishProgress(phase, bytes, total)
        updateForeground(phase, bytes, total)
    }

    private fun publishProgress(phase: String, bytes: Long, total: Long) {
        val safeBytes = bytes.coerceAtLeast(0L).coerceAtMost(total.coerceAtLeast(0L))
        taskStore.updateProgress(phase, safeBytes, total)
        val data = workDataOf(
            UniDicTaskStore.KEY_PHASE to phase,
            UniDicTaskStore.KEY_DOWNLOADED to safeBytes,
            UniDicTaskStore.KEY_TOTAL to total,
        )
        runBlocking { setProgress(data) }
    }

    private fun updateForeground(phase: String, bytes: Long, total: Long) {
        val notification = buildNotification(applicationContext, phase, bytes, total)
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                UniDicTaskStore.NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(UniDicTaskStore.NOTIFICATION_ID, notification)
        }
        runBlocking { setForeground(info) }
    }

    private fun waitForMobileConsent(): Result {
        val snapshot = taskStore.preferences.getLong(UniDicTaskStore.KEY_DOWNLOADED, 0L)
        publishProgress(
            UniDicTaskStore.PHASE_WAITING_MOBILE,
            snapshot,
            UniDicResourceCatalog.ARCHIVE_BYTES,
        )
        return Result.failure(
            workDataOf(
                UniDicTaskStore.KEY_PHASE to UniDicTaskStore.PHASE_WAITING_MOBILE,
                UniDicTaskStore.KEY_DOWNLOADED to snapshot,
                UniDicTaskStore.KEY_TOTAL to UniDicResourceCatalog.ARCHIVE_BYTES,
            ),
        )
    }

    private fun waitForNetwork(): Result {
        publishProgress(
            UniDicTaskStore.PHASE_WAITING_WIFI,
            taskStore.preferences.getLong(UniDicTaskStore.KEY_DOWNLOADED, 0L),
            UniDicResourceCatalog.ARCHIVE_BYTES,
        )
        return Result.retry()
    }

    private fun fail(failureCode: String): Result {
        taskStore.setUserState(UniDicTaskStore.USER_NONE)
        return Result.failure(
            workDataOf(
                UniDicTaskStore.KEY_PHASE to UniDicTaskStore.PHASE_FAILED,
                UniDicTaskStore.KEY_FAILURE to failureCode,
                UniDicTaskStore.KEY_DOWNLOADED to taskStore.preferences.getLong(UniDicTaskStore.KEY_DOWNLOADED, 0L),
                UniDicTaskStore.KEY_TOTAL to UniDicResourceCatalog.ARCHIVE_BYTES,
            ),
        )
    }

    private companion object {
        const val TAG = "UniDicWork"
    }
}

private fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                UniDicTaskStore.CHANNEL_ID,
                context.getString(R.string.downloads),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }
}

private fun buildNotification(context: Context, phase: String, bytes: Long, total: Long): Notification {
    createNotificationChannel(context)
    val openIntent = Intent(context, MainActivity::class.java)
        .putExtra(UniDicTaskStore.EXTRA_OPEN_DOWNLOADS, true)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    val contentIntent = PendingIntent.getActivity(
        context,
        44032,
        openIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val pauseIntent = PendingIntent.getBroadcast(
        context,
        44033,
        Intent(context, UniDicTaskActionReceiver::class.java).setAction(UniDicTaskStore.ACTION_PAUSE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val cancelIntent = PendingIntent.getBroadcast(
        context,
        44034,
        Intent(context, UniDicTaskActionReceiver::class.java).setAction(UniDicTaskStore.ACTION_CANCEL),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Notification.Builder(context, UniDicTaskStore.CHANNEL_ID)
    } else {
        Notification.Builder(context)
    }
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle(context.getString(R.string.download_notification))
        .setContentIntent(contentIntent)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .addAction(android.R.drawable.ic_media_pause, context.getString(R.string.pause), pauseIntent)
        .addAction(android.R.drawable.ic_menu_close_clear_cancel, context.getString(R.string.cancel), cancelIntent)

    val phaseId = when (phase) {
        UniDicTaskStore.PHASE_DOWNLOADING -> R.string.downloading
        UniDicTaskStore.PHASE_INSTALLING -> R.string.installing
        UniDicTaskStore.PHASE_READY -> R.string.dictionary_ready
        UniDicTaskStore.PHASE_WAITING_WIFI -> R.string.waiting_wifi
        UniDicTaskStore.PHASE_FAILED -> R.string.dictionary_failed
        UniDicTaskStore.PHASE_VERIFYING, UniDicTaskStore.PHASE_WAITING_MOBILE -> R.string.verifying
        else -> R.string.verifying
    }
    builder.setContentText(context.getString(phaseId))
    if (phase == UniDicTaskStore.PHASE_DOWNLOADING && total in 1..Int.MAX_VALUE.toLong()) {
        builder.setProgress(total.toInt(), bytes.coerceIn(0, total).toInt(), false)
    }
    return builder.build()
}

internal class UniDicTaskActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val task = UniDicDownloadTask(context)
        try {
            when (intent.action) {
                UniDicTaskStore.ACTION_PAUSE -> task.pause()
                UniDicTaskStore.ACTION_CANCEL -> task.cancel()
            }
        } finally {
            task.close()
        }
    }
}

private const val NO_APPROVED_NETWORK = -1L
