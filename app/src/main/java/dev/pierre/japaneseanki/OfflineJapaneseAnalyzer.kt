package dev.pierre.japaneseanki

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipInputStream

internal class OfflineJapaneseAnalyzer(context: Context) {
    private val appContext = context.applicationContext
    @Volatile private var tagger: NativeMeCab? = null

    fun prepare(onStatus: (String) -> Unit = {}) {
        if (tagger != null) return
        synchronized(installationLock) {
            if (tagger != null) return
            val destination = File(appContext.filesDir, DIRECTORY)
            if (!isInstalled(destination)) {
                onStatus("Checking UniDic runtime archive")
                val resource = UniDicResourceCatalog.manifest.resources.single()
                val archive = VerifiedFirstRunResourceInstaller(
                    File(appContext.filesDir, RESOURCE_DIRECTORY),
                ).install(resource) { progress ->
                    onStatus(progress.describeForUi())
                }.location
                onStatus("Unpacking UniDic: 0% (about 725 MB)")
                install(destination, archive) { extracted ->
                    onStatus("Unpacking UniDic: ${(extracted * 100).toInt()}% (about 725 MB)")
                }
            } else {
                onStatus("UniDic is ready")
            }
            tagger = NativeMeCab(destination)
        }
    }

    fun analyze(sentence: String): List<JapaneseToken> =
        checkNotNull(tagger) { "Offline Japanese analysis is not ready" }.analyze(sentence)

    private fun install(
        destination: File,
        archive: File,
        onExtractionProgress: (Float) -> Unit,
    ) {
        val temporary = File(appContext.filesDir, "$DIRECTORY.tmp")
        temporary.deleteRecursively()
        check(temporary.mkdirs()) { "Could not create UniDic storage" }
        try {
            val completedFiles = mutableSetOf<String>()
            var extractedBytes = 0L
            var lastProgressBytes = 0L
            archive.inputStream().buffered().use { source ->
                ZipInputStream(source).use { zip ->
                    while (true) {
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
            throw error
        }
    }

    private fun isInstalled(directory: File): Boolean =
        directory.isDirectory &&
            FILE_SIZES.values.sum() == TOTAL_BYTES &&
            FILE_SIZES.all { (name, size) -> File(directory, name).length() == size }

    private fun ResourceProgress.describeForUi(): String {
        val completedMb = completedBytes / MEBIBYTE
        val totalMb = totalBytes / MEBIBYTE
        return when (phase) {
            ResourceProgressPhase.DOWNLOADING -> String.format(
                Locale.US,
                "Downloading UniDic: %.1f of %.1f MiB",
                completedMb,
                totalMb,
            )
            ResourceProgressPhase.VERIFYING -> "Verifying UniDic SHA-256"
            ResourceProgressPhase.VALIDATING_ARCHIVE -> String.format(
                Locale.US,
                "Validating UniDic archive: %.1f of %.1f MiB",
                completedMb,
                totalMb,
            )
            ResourceProgressPhase.ACTIVATING -> "Activating verified UniDic archive"
        }
    }

    companion object {
        private const val DIRECTORY = "unidic-cwj-3.1.1"
        private const val RESOURCE_DIRECTORY = "first-run-resources"
        private const val TOTAL_BYTES = 724_593_605L
        private const val MEBIBYTE = 1024.0 * 1024.0
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
