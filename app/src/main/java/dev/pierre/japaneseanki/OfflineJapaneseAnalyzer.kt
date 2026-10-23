package dev.pierre.japaneseanki

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

internal enum class OfflineAnalyzerPhase { DOWNLOADING, VERIFYING, INSTALLING, READY }

internal data class OfflineAnalyzerProgress(
    val phase: OfflineAnalyzerPhase,
    val downloadedBytes: Long? = null,
    val totalBytes: Long? = null,
)

internal class OfflineJapaneseAnalyzer(context: Context) {
    private val appContext = context.applicationContext
    @Volatile private var tagger: NativeMeCab? = null

    fun loadInstalled(): Boolean {
        if (tagger != null) return true
        synchronized(installationLock) {
            if (tagger != null) return true
            val destination = File(appContext.filesDir, DIRECTORY)
            if (!isInstalled(destination)) return false
            tagger = NativeMeCab(destination)
            return true
        }
    }

    fun prepare(
        onStatus: (OfflineAnalyzerProgress) -> Unit = {},
        cancellation: ResourceCancellation = ResourceCancellation.NEVER,
    ) {
        if (tagger != null) return
        synchronized(installationLock) {
            if (tagger != null) return
            val destination = File(appContext.filesDir, DIRECTORY)
            if (!isInstalled(destination)) {
                onStatus(OfflineAnalyzerProgress(OfflineAnalyzerPhase.VERIFYING))
                val resource = UniDicResourceCatalog.manifest.resources.single()
                val archive = VerifiedFirstRunResourceInstaller(
                    File(appContext.filesDir, RESOURCE_DIRECTORY),
                ).install(
                    resource,
                    cancellation = cancellation,
                ) { progress ->
                    onStatus(
                        when (progress.phase) {
                            ResourceProgressPhase.DOWNLOADING -> OfflineAnalyzerProgress(
                                OfflineAnalyzerPhase.DOWNLOADING,
                                progress.completedBytes,
                                progress.totalBytes,
                            )
                            ResourceProgressPhase.VERIFYING -> OfflineAnalyzerProgress(
                                OfflineAnalyzerPhase.VERIFYING,
                                progress.completedBytes,
                                progress.totalBytes,
                            )
                            ResourceProgressPhase.VALIDATING_ARCHIVE -> OfflineAnalyzerProgress(OfflineAnalyzerPhase.VERIFYING)
                            ResourceProgressPhase.ACTIVATING -> OfflineAnalyzerProgress(OfflineAnalyzerPhase.INSTALLING)
                        },
                    )
                }.location
                onStatus(OfflineAnalyzerProgress(OfflineAnalyzerPhase.INSTALLING))
                install(destination, archive, cancellation) {}
            } else {
                onStatus(OfflineAnalyzerProgress(OfflineAnalyzerPhase.VERIFYING))
            }
            if (Thread.currentThread().isInterrupted || cancellation.isCancelled()) {
                throw ResourceCancelledException()
            }
            tagger = NativeMeCab(destination)
            onStatus(OfflineAnalyzerProgress(OfflineAnalyzerPhase.READY))
        }
    }

    fun analyze(sentence: String): List<JapaneseToken> =
        checkNotNull(tagger) { "Offline Japanese analysis is not ready" }.analyze(sentence)

    private fun install(
        destination: File,
        archive: File,
        cancellation: ResourceCancellation,
        onExtractionProgress: (Float) -> Unit,
    ) {
        if (appContext.filesDir.usableSpace < TOTAL_BYTES) {
            throw ResourceStorageException("Not enough free storage to extract UniDic")
        }
        val temporary = File(appContext.filesDir, "$DIRECTORY.tmp")
        temporary.deleteRecursively()
        if (!temporary.mkdirs()) throw ResourceActivationException("Could not create UniDic temporary storage")
        try {
            val completedFiles = mutableSetOf<String>()
            var extractedBytes = 0L
            var lastProgressBytes = 0L
            archive.inputStream().buffered().use { source ->
                ZipInputStream(source).use { zip ->
                    while (true) {
                        if (Thread.currentThread().isInterrupted || cancellation.isCancelled()) {
                            throw ResourceCancelledException()
                        }
                        val entry = zip.nextEntry ?: break
                        val relative = entry.name.removePrefix("$DIRECTORY/")
                        val expectedSize = FILE_SIZES[relative]
                        check(!entry.isDirectory && expectedSize != null &&
                            entry.name == "$DIRECTORY/$relative" && relative !in completedFiles
                        ) { "Unexpected file in the verified UniDic archive" }
                        val target = File(temporary, relative)
                        FileOutputStream(target).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                if (Thread.currentThread().isInterrupted || cancellation.isCancelled()) {
                                    throw ResourceCancelledException()
                                }
                                val count = zip.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                extractedBytes += count
                                if (extractedBytes - lastProgressBytes >= 8 * 1024 * 1024) {
                                    val progress = extractedBytes.toFloat() / TOTAL_BYTES
                                    onExtractionProgress(progress)
                                    lastProgressBytes = extractedBytes
                                }
                            }
                            output.fd.sync()
                        }
                        check(target.length() == expectedSize) { "UniDic file size did not match the pinned manifest" }
                        completedFiles += relative
                        zip.closeEntry()
                    }
                }
            }
            check(completedFiles == FILE_SIZES.keys && extractedBytes == TOTAL_BYTES) {
                "UniDic data is incomplete"
            }
            destination.deleteRecursively()
            check(temporary.renameTo(destination)) { "Could not install UniDic data" }
            onExtractionProgress(1f)
        } catch (error: Exception) {
            temporary.deleteRecursively()
            if (error is ResourceStorageException) throw error
            if (isStorageFailure(error)) {
                throw ResourceStorageException("Not enough storage to install UniDic", error)
            }
            throw error
        }
    }

    private fun isStorageFailure(error: Throwable): Boolean {
        var cause: Throwable? = error
        while (cause != null) {
            if (cause is android.system.ErrnoException && cause.errno == android.system.OsConstants.ENOSPC) return true
            val message = cause.message.orEmpty()
            if (message.contains("ENOSPC", ignoreCase = true) ||
                message.contains("No space left on device", ignoreCase = true)
            ) return true
            cause = cause.cause
        }
        return false
    }

    private fun isInstalled(directory: File): Boolean =
        directory.isDirectory &&
            FILE_SIZES.values.sum() == TOTAL_BYTES &&
            FILE_SIZES.all { (name, size) -> File(directory, name).length() == size }

    companion object {
        private const val DIRECTORY = "unidic-cwj-3.1.1"
        private const val RESOURCE_DIRECTORY = "first-run-resources"
        private const val TOTAL_BYTES = 724_593_605L

        private val FILE_SIZES = mapOf(
            "char.bin" to 262_496L,
            "dicrc" to 1_785L,
            "matrix.bin" to 480_905_780L,
            "sys.dic" to 243_418_052L,
            "unk.dic" to 5_492L,
        )
        private val installationLock = Any()
        @Volatile private var shared: OfflineJapaneseAnalyzer? = null

        fun shared(context: Context): OfflineJapaneseAnalyzer = shared ?: synchronized(installationLock) {
            shared ?: OfflineJapaneseAnalyzer(context).also { shared = it }
        }
    }
}
