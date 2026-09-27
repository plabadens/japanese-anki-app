package dev.pierre.japaneseanki

import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Stable identity for reusable synthesis; source identifies the provider/engine and voice. */
data class AudioCacheKey(
    val source: String,
    val text: String,
    val voice: String,
    val rate: Float,
) {
    init {
        require(source.isNotBlank())
        require(text.isNotBlank())
        require(voice.isNotBlank())
        require(rate.isFinite() && rate > 0f)
    }

    internal fun filename(): String {
        val identity = listOf(source, text, voice, java.lang.Float.toString(rate)).joinToString("\u0000")
        val digest = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

/** A completed reusable audio file. Draft copies are deliberately stored outside this cache. */
class ReusableAudioCache(
    private val directory: File,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
) {
    init {
        require(maxBytes >= 0)
    }

    @Synchronized
    fun get(key: AudioCacheKey): File? {
        val cached = cacheFile(key)
        if (!cached.isFile) return null
        cached.setLastModified(System.currentTimeMillis())
        return cached
    }

    /** Copies only complete audio into cache using an atomic rename; oversized files are not cached. */
    @Synchronized
    @Throws(IOException::class)
    fun put(key: AudioCacheKey, completedFile: File): File? {
        require(completedFile.isFile)
        if (completedFile.length() > maxBytes) return null
        if (!directory.exists() && !directory.mkdirs()) throw IOException("Could not create audio cache")
        val destination = cacheFile(key)
        val temporary = File(directory, ".${key.filename()}.${System.nanoTime()}.tmp")
        try {
            completedFile.inputStream().use { input -> temporary.outputStream().use(input::copyTo) }
            if (!temporary.renameTo(destination)) throw IOException("Could not commit cached audio")
        } finally {
            temporary.delete()
        }
        evictExcept(destination)
        return destination.takeIf(File::isFile)
    }

    /** Keeps a durable copy in the draft area, independent of later cache eviction/clearing. */
    @Synchronized
    @Throws(IOException::class)
    fun copyToActiveDraft(key: AudioCacheKey, draftDirectory: File, name: String): File {
        require(name.matches(Regex("[A-Za-z0-9._-]+")))
        val source = get(key) ?: throw IOException("Audio is not cached")
        if (!draftDirectory.exists() && !draftDirectory.mkdirs()) throw IOException("Could not create draft media directory")
        val destination = File(draftDirectory, name)
        val temporary = File(draftDirectory, ".$name.${System.nanoTime()}.tmp")
        try {
            source.inputStream().use { input -> temporary.outputStream().use(input::copyTo) }
            if (!temporary.renameTo(destination)) throw IOException("Could not commit draft audio")
        } finally {
            temporary.delete()
        }
        return destination
    }

    @Synchronized
    fun clear() {
        directory.listFiles()?.filter { it.isFile }?.forEach(File::delete)
    }

    @Synchronized
    fun sizeBytes(): Long = directory.listFiles()?.filter { it.isFile }?.sumOf(File::length) ?: 0L

    private fun cacheFile(key: AudioCacheKey) = File(directory, "${key.filename()}.audio")

    private fun evictExcept(protected: File) {
        val files = directory.listFiles()?.filter { it.isFile && it != protected } ?: return
        var total = protected.length() + files.sumOf(File::length)
        for (file in files.sortedBy(File::lastModified)) {
            if (total <= maxBytes) break
            val length = file.length()
            if (file.delete()) total -= length
        }
    }

    companion object {
        const val DEFAULT_MAX_BYTES: Long = 250L * 1024 * 1024
    }
}

enum class AudioItem { SENTENCE, VOCABULARY }

data class AudioRequestIdentity(val item: AudioItem, val revision: Long, val text: String, val entryIdentity: String? = null) {
    init {
        require(revision >= 0)
        require(text.isNotBlank())
    }
}

sealed interface AudioGenerationState {
    data object Idle : AudioGenerationState
    data class Generating(val request: AudioRequestIdentity) : AudioGenerationState
    data class Ready(val request: AudioRequestIdentity, val file: File) : AudioGenerationState
    data class Failed(val request: AudioRequestIdentity, val retryable: Boolean) : AudioGenerationState
}

/** Accepts completion only for the exact current input revision; callers initiate retry explicitly. */
class RevisionedAudioResult {
    private val transitionLock = Any()

    @Volatile
    var state: AudioGenerationState = AudioGenerationState.Idle
        private set

    fun begin(request: AudioRequestIdentity) {
        synchronized(transitionLock) {
            state = AudioGenerationState.Generating(request)
        }
    }

    fun complete(request: AudioRequestIdentity, file: File): Boolean = synchronized(transitionLock) {
        if ((state as? AudioGenerationState.Generating)?.request != request) return false
        state = AudioGenerationState.Ready(request, file)
        true
    }

    fun fail(request: AudioRequestIdentity, retryable: Boolean): Boolean = synchronized(transitionLock) {
        if ((state as? AudioGenerationState.Generating)?.request != request) return false
        state = AudioGenerationState.Failed(request, retryable)
        true
    }
}
