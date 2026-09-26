package dev.pierre.japaneseanki

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

internal class OfflineJapaneseAnalyzer(context: Context) {
    private val appContext = context.applicationContext
    @Volatile private var tagger: NativeMeCab? = null

    fun prepare(onProgress: (Float) -> Unit = {}) {
        if (tagger != null) return
        synchronized(installationLock) {
            if (tagger != null) return
            val destination = File(appContext.filesDir, DIRECTORY)
            if (!isInstalled(destination)) install(destination, onProgress)
            tagger = NativeMeCab(destination)
        }
    }

    fun analyze(sentence: String): List<JapaneseToken> =
        checkNotNull(tagger) { "Offline Japanese analysis is not ready" }.analyze(sentence)

    private fun install(destination: File, onProgress: (Float) -> Unit) {
        val temporary = File(appContext.filesDir, "$DIRECTORY.tmp")
        temporary.deleteRecursively()
        check(temporary.mkdirs()) { "Could not create UniDic storage" }
        try {
            val completedFiles = mutableSetOf<String>()
            var extractedBytes = 0L
            var lastProgressBytes = 0L
            appContext.assets.open(ASSET).use { asset ->
                ZipInputStream(asset.buffered()).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val relative = entry.name.removePrefix("$DIRECTORY/")
                        val expectedSize = FILE_SIZES[relative]
                        check(!entry.isDirectory && expectedSize != null &&
                            entry.name == "$DIRECTORY/$relative" && relative !in completedFiles
                        ) { "Unexpected file in the bundled UniDic archive" }
                        val target = File(temporary, relative)
                        FileOutputStream(target).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val count = zip.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                extractedBytes += count
                                if (extractedBytes - lastProgressBytes >= 8 * 1024 * 1024) {
                                    onProgress(extractedBytes.toFloat() / TOTAL_BYTES)
                                    lastProgressBytes = extractedBytes
                                }
                            }
                            output.fd.sync()
                        }
                        check(target.length() == expectedSize) { "Bundled UniDic file size did not match" }
                        completedFiles += relative
                        zip.closeEntry()
                    }
                }
            }
            check(completedFiles == FILE_SIZES.keys && extractedBytes == TOTAL_BYTES) {
                "Bundled UniDic data is incomplete"
            }
            destination.deleteRecursively()
            check(temporary.renameTo(destination)) { "Could not install UniDic data" }
            onProgress(1f)
        } catch (error: Exception) {
            temporary.deleteRecursively()
            throw error
        }
    }

    private fun isInstalled(directory: File): Boolean =
        FILE_SIZES.all { (name, size) -> File(directory, name).length() == size }

    companion object {
        private const val DIRECTORY = "unidic-cwj-3.1.1"
        private const val ASSET = "$DIRECTORY.zip"
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
