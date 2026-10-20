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
    val engine: String = "",
    val entryIdentity: String = "",
    /** Canonical phonetic-hint fragment for sentence speech; empty for other audio. */
    val hintIdentity: String = "",
) {
    init {
        require(source.isNotBlank())
        require(text.isNotBlank())
        require(voice.isNotBlank())
        require(rate.isFinite() && rate > 0f)
    }

    internal fun filename(): String {
        val identity = listOf(source, text, entryIdentity, engine, voice, java.lang.Float.toString(rate), hintIdentity)
            .joinToString("\u0000")
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
        val cached = listOf(cacheFile(key, "wav"), cacheFile(key, "ogg")).firstOrNull(File::isFile) ?: return null
        cached.setLastModified(System.currentTimeMillis())
        return cached
    }

    /** Copies only complete audio into cache using an atomic rename; oversized files are not cached. */
    @Synchronized
    @Throws(IOException::class)
    fun put(key: AudioCacheKey, completedFile: File): File? {
        require(completedFile.isFile)
        AudioFileValidator.requireSupported(completedFile)
        val length = completedFile.length()
        if (length > maxBytes) return null
        if (!directory.exists() && !directory.mkdirs()) throw IOException("Could not create audio cache")
        val extension = AudioFileValidator.requireSupported(completedFile).extension.lowercase().ifBlank { "wav" }
        val destination = cacheFile(key, extension)
        removeTemporaryFiles()
        evictForIncoming(length, destination)
        val temporary = File(directory, ".${key.filename()}.${System.nanoTime()}.tmp")
        try {
            completedFile.inputStream().use { input -> temporary.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var copied = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    copied += count
                    if (copied > maxBytes) throw IOException("Audio exceeds cache limit")
                    output.write(buffer, 0, count)
                }
            } }
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
        require(!isInsideCache(draftDirectory)) { "Active-draft media must be outside the reusable cache" }
        val source = get(key) ?: throw IOException("Audio is not cached")
        AudioFileValidator.requireSupported(source)
        require(name.substringAfterLast('.', "").equals(source.extension, ignoreCase = true)) {
            "Draft audio filename extension must match its content"
        }
        if (!draftDirectory.exists() && !draftDirectory.mkdirs()) throw IOException("Could not create draft media directory")
        val destination = File(draftDirectory, name)
        val temporary = File(draftDirectory, ".$name.${System.nanoTime()}.tmp.${source.extension.lowercase()}")
        try {
            source.inputStream().use { input -> temporary.outputStream().use(input::copyTo) }
            AudioFileValidator.requireSupported(temporary)
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
    fun sizeBytes(): Long = directory.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") }?.sumOf(File::length) ?: 0L

    private fun removeTemporaryFiles() {
        directory.listFiles()?.filter { it.isFile && it.name.endsWith(".tmp") }?.forEach(File::delete)
    }

    private fun cacheFile(key: AudioCacheKey, extension: String) = File(directory, "${key.filename()}.$extension")

    private fun isInsideCache(target: File): Boolean {
        val root = directory.canonicalFile.toPath()
        return target.canonicalFile.toPath().startsWith(root)
    }

    private fun evictForIncoming(incoming: Long, destination: File) {
        val files = directory.listFiles()?.filter { it.isFile && it != destination && !it.name.endsWith(".tmp") } ?: return
        var total = files.sumOf(File::length) + (if (destination.isFile) destination.length() else 0L)
        for (file in files.sortedBy(File::lastModified)) {
            if (total + incoming <= maxBytes) break
            val length = file.length()
            if (file.delete()) total -= length
        }
        if (total + incoming > maxBytes) throw IOException("Could not make room in audio cache")
    }

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
            if (request.revision >= currentRevision()) state = AudioGenerationState.Generating(request)
        }
    }

    /**
     * Starts automatic work only for a newer revision. Pass [afterProcessRestart] for a restored,
     * interrupted request: it is retained as retryable failure but never restarted automatically.
     * This prevents silently repeating potentially billable work after process recreation.
     */
    fun beginIfNeeded(request: AudioRequestIdentity, afterProcessRestart: Boolean = false): Boolean = synchronized(transitionLock) {
        if (afterProcessRestart) {
            if (request.revision >= currentRevision()) state = AudioGenerationState.Failed(request, retryable = true)
            return@synchronized false
        }
        when (val current = state) {
            is AudioGenerationState.Generating -> if (current.request == request || request.revision < current.request.revision) false else {
                state = AudioGenerationState.Generating(request)
                true
            }
            is AudioGenerationState.Ready -> if ((current.request == request && current.file.isFile) || request.revision < current.request.revision) false else {
                state = AudioGenerationState.Generating(request)
                true
            }
            is AudioGenerationState.Failed -> if (current.request == request || request.revision < current.request.revision) false else {
                state = AudioGenerationState.Generating(request)
                true
            }
            AudioGenerationState.Idle -> {
                state = AudioGenerationState.Generating(request)
                true
            }
        }
    }

    /** Retry is explicit and permitted only for the currently failed request. */
    fun retry(request: AudioRequestIdentity): Boolean = synchronized(transitionLock) {
        val failed = state as? AudioGenerationState.Failed
        if (failed?.request != request || !failed.retryable) return false
        state = AudioGenerationState.Generating(request)
        true
    }

    private fun currentRevision(): Long = when (val current = state) {
        is AudioGenerationState.Generating -> current.request.revision
        is AudioGenerationState.Ready -> current.request.revision
        is AudioGenerationState.Failed -> current.request.revision
        AudioGenerationState.Idle -> -1L
    }

    fun complete(request: AudioRequestIdentity, file: File): Boolean = synchronized(transitionLock) {
        if ((state as? AudioGenerationState.Generating)?.request != request) return false
        if (runCatching { AudioFileValidator.requireSupported(file) }.isFailure) {
            state = AudioGenerationState.Failed(request, retryable = true)
            return false
        }
        state = AudioGenerationState.Ready(request, file)
        true
    }

    fun fail(request: AudioRequestIdentity, retryable: Boolean): Boolean = synchronized(transitionLock) {
        if ((state as? AudioGenerationState.Generating)?.request != request) return false
        state = AudioGenerationState.Failed(request, retryable)
        true
    }
}
