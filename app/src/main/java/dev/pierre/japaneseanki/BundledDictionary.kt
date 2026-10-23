package dev.pierre.japaneseanki

import android.content.Context
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream

/** The bundled UniDic dictionary could not be unpacked. */
internal open class BundledDictionaryException(message: String, cause: Throwable? = null) :
    IOException(message, cause)

/** The device ran out of space while unpacking the bundled dictionary. */
internal class BundledDictionaryStorageException(message: String, cause: Throwable? = null) :
    BundledDictionaryException(message, cause)

/**
 * UniDic 2.1.2 (the "unidic-lite" data set), shipped inside the APK.
 *
 * The five MeCab runtime files are unpacked once from `assets/unidic-lite.zip` into app-private
 * storage, because MeCab memory-maps a directory and cannot read an APK asset. There is no network
 * access, no transfer state, and no user-facing dictionary task: a missing or outdated extraction
 * is simply redone on the next launch.
 *
 * [VERSION] is written into the extracted directory. Changing it (for example when the bundled data
 * changes) makes the next launch replace an older extraction, including the 3.1.1 files that earlier
 * releases downloaded.
 */
internal object BundledDictionary {
    const val VERSION = "unidic-lite-2.1.2"
    const val ASSET_NAME = "unidic-lite.zip"
    const val DIRECTORY = "unidic"

    /** Entry prefix inside [ASSET_NAME]. */
    const val ASSET_ROOT = "unidic-lite"

    /** Exact runtime files and their uncompressed sizes. */
    val FILE_SIZES: Map<String, Long> = linkedMapOf(
        "char.bin" to 262_496L,
        "dicrc" to 1_444L,
        "matrix.bin" to 71_544_726L,
        "sys.dic" to 187_680_870L,
        "unk.dic" to 5_475L,
    )

    val TOTAL_BYTES: Long = FILE_SIZES.values.sum()

    private const val MARKER_FILE = "version"

    fun directory(filesDir: File): File = File(filesDir, DIRECTORY)

    fun isInstalled(directory: File): Boolean {
        if (!directory.isDirectory) return false
        val marker = File(directory, MARKER_FILE)
        if (!marker.isFile) return false
        if (runCatching { marker.readText().trim() }.getOrNull() != VERSION) return false
        return FILE_SIZES.all { (name, size) -> File(directory, name).length() == size }
    }

    /**
     * Unpacks [ASSET_NAME] if [directory] is missing, incomplete, or from another dictionary
     * version. Blocking: call from a background dispatcher.
     */
    fun install(context: Context, filesDir: File = context.filesDir) {
        val destination = directory(filesDir)
        if (isInstalled(destination)) return
        if (filesDir.usableSpace < TOTAL_BYTES) {
            throw BundledDictionaryStorageException("Not enough free storage for the offline dictionary")
        }

        val staging = File(filesDir, "$DIRECTORY.staging")
        staging.deleteRecursively()
        if (!staging.mkdirs()) {
            throw BundledDictionaryException("Could not create dictionary staging storage")
        }
        try {
            extract(context, staging)
            File(staging, MARKER_FILE).writeText(VERSION)
            activate(staging, destination, filesDir)
        } catch (failure: BundledDictionaryException) {
            staging.deleteRecursively()
            throw failure
        } catch (failure: CancellationException) {
            staging.deleteRecursively()
            throw failure
        } catch (failure: IOException) {
            staging.deleteRecursively()
            if (failure.isOutOfSpace()) {
                throw BundledDictionaryStorageException("Not enough storage for the offline dictionary", failure)
            }
            throw BundledDictionaryException("Could not unpack the bundled dictionary", failure)
        }
    }

    private fun extract(context: Context, staging: File) {
        val seen = HashSet<String>()
        var written = 0L
        context.assets.open(ASSET_NAME).buffered().use { raw ->
            ZipInputStream(raw).use { zip ->
                while (true) {
                    if (Thread.currentThread().isInterrupted) {
                        throw CancellationException("Dictionary unpacking was cancelled")
                    }
                    val entry = zip.nextEntry ?: break
                    val name = entry.name.removePrefix("$ASSET_ROOT/")
                    val expected = FILE_SIZES[name]
                    if (entry.isDirectory || expected == null || entry.name != "$ASSET_ROOT/$name" ||
                        !seen.add(name)
                    ) {
                        throw BundledDictionaryException("Unexpected file in the bundled dictionary")
                    }
                    val target = File(staging, name)
                    FileOutputStream(target).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            written += count
                        }
                        output.fd.sync()
                    }
                    if (target.length() != expected) {
                        throw BundledDictionaryException("Bundled dictionary file size did not match the build")
                    }
                    zip.closeEntry()
                }
            }
        }
        if (seen != FILE_SIZES.keys || written != TOTAL_BYTES) {
            throw BundledDictionaryException("Bundled dictionary is incomplete")
        }
    }

    private fun activate(staging: File, destination: File, filesDir: File) {
        val previous = File(filesDir, "$DIRECTORY.previous")
        previous.deleteRecursively()
        if (destination.exists() && !destination.renameTo(previous)) {
            throw BundledDictionaryException("Could not replace the previous dictionary")
        }
        if (!staging.renameTo(destination)) {
            previous.renameTo(destination)
            throw BundledDictionaryException("Could not activate the bundled dictionary")
        }
        previous.deleteRecursively()
    }

    private fun IOException.isOutOfSpace(): Boolean {
        var cause: Throwable? = this
        while (cause != null) {
            if (cause is android.system.ErrnoException) return cause.errno == android.system.OsConstants.ENOSPC
            val message = cause.message.orEmpty()
            if (message.contains("ENOSPC", ignoreCase = true) ||
                message.contains("No space left on device", ignoreCase = true)
            ) {
                return true
            }
            cause = cause.cause
        }
        return false
    }
}
