package dev.pierre.japaneseanki

import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
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

/**
 * File primitives the audio storage code depends on. Injectable so rename, fsync and delete
 * failures are deterministic in tests instead of depending on filesystem permissions.
 */
interface AudioFileOperations {
    @Throws(IOException::class)
    fun move(source: File, destination: File)

    /** Returns false when the file still exists after the attempt. */
    fun delete(file: File): Boolean

    @Throws(IOException::class)
    fun sync(file: File)

    companion object {
        fun system(): AudioFileOperations = SystemAudioFileOperations
    }
}

internal object SystemAudioFileOperations : AudioFileOperations {
    override fun move(source: File, destination: File) {
        try {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    override fun delete(file: File): Boolean = !file.exists() || file.delete()

    override fun sync(file: File) {
        java.io.RandomAccessFile(file, "rw").use { it.fd.sync() }
    }
}

/** Raised for a cache-only storage failure; callers keep the durable draft audio regardless. */
class AudioCacheException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** Best-effort cache write outcome. Reasons are sanitized: no paths or audio text. */
sealed interface AudioCacheStoreResult {
    data class Stored(val file: File) : AudioCacheStoreResult
    data class Skipped(val reason: String) : AudioCacheStoreResult
    data class Failed(val reason: String) : AudioCacheStoreResult
}

/** A pinned cache entry. Until [close] the file cannot be cleared or evicted. */
class AudioCacheLease internal constructor(
    val file: File,
    val format: AudioFormat,
    private val releaser: () -> Unit,
) : AutoCloseable {
    private var released = false

    override fun close() {
        if (released) return
        released = true
        releaser()
    }
}

/**
 * Immutable accepted audio. Draft copies are deliberately stored outside this cache.
 *
 * The cache owns one list of supported formats ([AudioFormat]) and validates content before
 * returning an entry, so an MP3 stored by [put] is also found by [get].
 */
class ReusableAudioCache(
    private val directory: File,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    private val operations: AudioFileOperations = AudioFileOperations.system(),
) : AudioCacheSource {
    init {
        require(maxBytes >= 0)
    }

    /** Cache entries pinned by an open lease, keyed by canonical path. */
    private val leases = HashMap<String, Int>()

    @Synchronized
    fun get(key: AudioCacheKey): File? {
        val cached = findValid(key) ?: return null
        cached.setLastModified(System.currentTimeMillis())
        return cached
    }

    /** Opens a validated entry and pins it until the returned lease is closed. */
    @Synchronized
    override fun open(key: AudioCacheKey): AudioCacheLease? {
        val cached = findValid(key) ?: return null
        cached.setLastModified(System.currentTimeMillis())
        val format = AudioFormat.fromFilename(cached.name) ?: return null
        val path = runCatching { cached.canonicalPath }.getOrElse { return null }
        leases[path] = (leases[path] ?: 0) + 1
        return AudioCacheLease(cached, format) {
            synchronized(this@ReusableAudioCache) {
                val remaining = (leases[path] ?: 1) - 1
                if (remaining <= 0) leases.remove(path) else leases[path] = remaining
            }
        }
    }

    /** Copies only complete audio into cache using an atomic rename; oversized files are not cached. */
    @Synchronized
    @Throws(IOException::class)
    fun put(key: AudioCacheKey, completedFile: File): File? = when (val result = storeOrThrow(key, completedFile)) {
        is AudioCacheStoreResult.Stored -> result.file
        else -> null
    }

    /**
     * Best-effort cache write used by the production audio route. Invalid source audio still
     * throws; only cache I/O failures are turned into a sanitized, non-fatal result.
     */
    @Synchronized
    @Throws(IOException::class)
    override fun store(key: AudioCacheKey, completedFile: File): AudioCacheStoreResult {
        val format = AudioFileValidator.requireContentFormat(completedFile)
        return try {
            storeValidated(key, completedFile, format)
        } catch (error: IOException) {
            AudioCacheStoreResult.Failed("Reusable audio cache is unavailable (${error.javaClass.simpleName})")
        } catch (error: SecurityException) {
            AudioCacheStoreResult.Failed("Reusable audio cache is unavailable (security)")
        }
    }

    /**
     * Keeps a durable copy in the draft area, independent of later cache eviction/clearing.
     * The cache entry stays pinned for the whole copy.
     */
    @Synchronized
    @Throws(IOException::class)
    fun copyToActiveDraft(key: AudioCacheKey, draftDirectory: File, name: String): File {
        require(name.matches(SAFE_DRAFT_NAME))
        require(!isInsideCache(draftDirectory)) { "Active-draft media must be outside the reusable cache" }
        val lease = open(key) ?: throw IOException("Audio is not cached")
        try {
            return AudioDraftMaterializer.materialize(lease.file, draftDirectory, name, operations)
        } finally {
            lease.close()
        }
    }

    @Synchronized
    fun clear() {
        directory.listFiles()?.filter { it.isFile && !isLeased(it) }?.forEach { operations.delete(it) }
    }

    @Synchronized
    fun sizeBytes(): Long = directory.listFiles()
        ?.filter { it.isFile && !it.name.endsWith(".tmp") }
        ?.sumOf(File::length) ?: 0L

    private fun storeOrThrow(key: AudioCacheKey, completedFile: File): AudioCacheStoreResult {
        val format = AudioFileValidator.requireContentFormat(completedFile)
        return storeValidated(key, completedFile, format)
    }

    @Throws(IOException::class)
    private fun storeValidated(key: AudioCacheKey, completedFile: File, format: AudioFormat): AudioCacheStoreResult {
        val length = completedFile.length()
        if (maxBytes <= 0L) return AudioCacheStoreResult.Skipped("cache budget is zero")
        if (length > maxBytes) return AudioCacheStoreResult.Skipped("audio is larger than the cache budget")
        if (!directory.exists() && !directory.mkdirs()) throw AudioCacheException("Could not create audio cache")
        val destination = File(directory, "${key.filename()}.${format.extension}")
        removeTemporaryFiles()
        evictForIncoming(length, destination)
        val temporary = File(directory, ".${key.filename()}.${System.nanoTime()}.tmp.${format.extension}")
        try {
            copyAndSync(completedFile, temporary)
            if (AudioFileValidator.requireContentFormat(temporary) != format) {
                throw AudioCacheException("Cached audio changed format during copy")
            }
            operations.move(temporary, destination)
        } finally {
            operations.delete(temporary)
        }
        evictExcept(destination)
        return if (destination.isFile) {
            AudioCacheStoreResult.Stored(destination)
        } else {
            throw AudioCacheException("Could not commit cached audio")
        }
    }

    @Throws(IOException::class)
    private fun copyAndSync(source: File, destination: File) {
        source.inputStream().use { input ->
            destination.outputStream().use { output ->
                input.copyTo(output, DEFAULT_BUFFER_SIZE)
                output.flush()
            }
        }
        operations.sync(destination)
    }

    private fun findValid(key: AudioCacheKey): File? = AudioFormat.entries.asSequence()
        .map { File(directory, "${key.filename()}.${it.extension}") }
        .firstOrNull { candidate ->
            candidate.isFile && runCatching { AudioFileValidator.requireFormat(candidate) }.isSuccess
        }

    private fun isLeased(file: File): Boolean {
        val path = runCatching { file.canonicalPath }.getOrNull() ?: return false
        return (leases[path] ?: 0) > 0
    }

    private fun removeTemporaryFiles() {
        directory.listFiles()?.filter { it.isFile && it.name.endsWith(".tmp") }?.forEach { operations.delete(it) }
    }

    private fun isInsideCache(target: File): Boolean {
        val root = directory.canonicalFile.toPath()
        return target.canonicalFile.toPath().startsWith(root)
    }

    private fun evictForIncoming(incoming: Long, destination: File) {
        val files = directory.listFiles()
            ?.filter { it.isFile && it != destination && !it.name.endsWith(".tmp") && !isLeased(it) }
            ?: return
        var total = files.sumOf(File::length) + (if (destination.isFile) destination.length() else 0L)
        for (file in files.sortedBy(File::lastModified)) {
            if (total + incoming <= maxBytes) break
            val length = file.length()
            if (operations.delete(file)) total -= length
        }
        if (total + incoming > maxBytes) throw AudioCacheException("Could not make room in audio cache")
    }

    private fun evictExcept(protected: File) {
        val files = directory.listFiles()?.filter { it.isFile && it != protected && !isLeased(it) } ?: return
        var total = protected.length() + files.sumOf(File::length)
        for (file in files.sortedBy(File::lastModified)) {
            if (total <= maxBytes) break
            val length = file.length()
            if (operations.delete(file)) total -= length
        }
    }

    companion object {
        const val DEFAULT_MAX_BYTES: Long = 250L * 1024 * 1024
        internal val SAFE_DRAFT_NAME = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
    }
}

/**
 * One validated, atomic source-to-draft materialization used by every audio route.
 *
 * The source is validated by content, copied into a unique temporary sibling, flushed and
 * fsynced, validated again, then published with an atomic rename. A failure never leaves a
 * partial file at [destinationDirectory]/[name].
 */
object AudioDraftMaterializer {
    @Throws(IOException::class)
    fun materialize(
        source: File,
        destinationDirectory: File,
        name: String,
        operations: AudioFileOperations = AudioFileOperations.system(),
    ): File {
        require(name.matches(ReusableAudioCache.SAFE_DRAFT_NAME)) { "Unsafe draft audio filename" }
        val format = AudioFileValidator.requireContentFormat(source)
        val named = AudioFormat.fromFilename(name)
            ?: throw IOException("Draft audio filename must use a supported extension")
        if (named != format) throw IOException("Draft audio filename extension must match its content")
        if (!destinationDirectory.isDirectory && !destinationDirectory.mkdirs()) {
            throw IOException("Could not create draft media directory")
        }
        val destination = File(destinationDirectory, name)
        val canonicalRoot = destinationDirectory.canonicalFile
        if (destination.canonicalFile.parentFile != canonicalRoot) {
            throw IOException("Draft audio destination is outside the draft media directory")
        }
        val temporary = File(destinationDirectory, ".$name.${System.nanoTime()}.tmp.${format.extension}")
        try {
            source.inputStream().use { input ->
                temporary.outputStream().use { output ->
                    input.copyTo(output, DEFAULT_BUFFER_SIZE)
                    output.flush()
                }
            }
            operations.sync(temporary)
            AudioFileValidator.requireFormat(temporary)
            operations.move(temporary, destination)
        } finally {
            operations.delete(temporary)
        }
        return destination
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

/**
 * Test-only revision machine kept so existing assertions stay comparable during migration.
 *
 * INTEGRATOR: once `MainActivity` is wired to [DraftAudioController], delete this class, its
 * state types above that are then unused, and the two revision/retry tests in
 * `AudioGenerationTest`. The controller carries the equivalent (stronger) revision, retry,
 * restart and token rules; this class is not the production audio lifecycle.
 */
@Deprecated("Superseded by DraftAudioController; delete after MainActivity wiring.")
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

/** Minimal cache boundary the production controller needs; [ReusableAudioCache] implements it. */
interface AudioCacheSource {
    fun open(key: AudioCacheKey): AudioCacheLease?
    fun store(key: AudioCacheKey, source: File): AudioCacheStoreResult

    companion object {
        /** Disabled cache: every attempt synthesizes once and keeps no reusable copy. */
        val disabled: AudioCacheSource = object : AudioCacheSource {
            override fun open(key: AudioCacheKey): AudioCacheLease? = null
            override fun store(key: AudioCacheKey, source: File): AudioCacheStoreResult =
                AudioCacheStoreResult.Skipped("reusable cache is disabled")
        }
    }
}
