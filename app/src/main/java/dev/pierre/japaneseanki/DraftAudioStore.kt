package dev.pierre.japaneseanki

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Owners that may keep a draft audio file alive. Every lease is process-visible. */
enum class AudioFileOwner {
    /** The accepted audio currently referenced by the draft controller. */
    ACTIVE_DRAFT,

    /** Audio pinned by a frozen Send/recovery snapshot. */
    FROZEN_SUBMISSION,

    /** MediaPlayer (or another player) is reading the file. */
    PLAYBACK,

    /** An AnkiDroid media import is in progress. */
    IMPORT,

    /** A blocking worker still owns a staging file. */
    WORKER,

    /** A completed candidate that has not been accepted yet. */
    CANDIDATE,
}

/**
 * Process-wide ownership registry. Lease counts live here rather than in an Activity so a
 * recreated Activity, a background worker and retirement checks all see the same owners.
 */
class AudioOwnershipRegistry {
    private val owners = ConcurrentHashMap<String, MutableMap<AudioFileOwner, Int>>()

    fun acquire(file: File, owner: AudioFileOwner): AudioFileLease {
        val path = canonicalPath(file)
        owners.compute(path) { _, current ->
            val next = current ?: mutableMapOf()
            next[owner] = (next[owner] ?: 0) + 1
            next
        }
        return AudioFileLease(path) { release(it, owner) }
    }

    fun isLeased(file: File): Boolean = isLeased(canonicalPath(file))

    fun isLeased(path: String): Boolean = owners[path]?.values?.any { it > 0 } == true

    fun ownersOf(file: File): Set<AudioFileOwner> =
        owners[canonicalPath(file)]?.filterValues { it > 0 }?.keys?.toSet().orEmpty()

    private fun release(path: String, owner: AudioFileOwner) {
        owners.computeIfPresent(path) { _, current ->
            val remaining = (current[owner] ?: 1) - 1
            if (remaining <= 0) current.remove(owner) else current[owner] = remaining
            current.takeIf { it.isNotEmpty() }
        }
    }

    private fun canonicalPath(file: File): String =
        runCatching { file.canonicalPath }.getOrElse { file.absolutePath }

    companion object {
        /** Shared by every Activity/worker in this process. */
        val process = AudioOwnershipRegistry()
    }
}

/** A single process-visible ownership claim; closing it twice is harmless. */
class AudioFileLease internal constructor(
    val path: String,
    private val releaser: (String) -> Unit,
) : AutoCloseable {
    private var released = false

    override fun close() {
        if (released) return
        released = true
        releaser(path)
    }
}

/** Immutable metadata for one durable draft audio file; this is what gets persisted. */
data class DraftAudioFile(
    val path: String,
    val format: String,
    val size: Long,
    val modified: Long,
) {
    fun file(): File = File(path)

    /** Re-validates the file on disk against this metadata and its container. */
    fun isIntact(): Boolean {
        val candidate = file()
        if (!candidate.isFile) return false
        if (candidate.length() != size || candidate.lastModified() != modified) return false
        return runCatching { AudioFileValidator.requireFormat(candidate) }.getOrNull()?.extension == format
    }

    companion object {
        @Throws(IOException::class)
        fun fromFile(file: File): DraftAudioFile {
            val format = AudioFileValidator.requireFormat(file)
            return DraftAudioFile(file.canonicalPath, format.extension, file.length(), file.lastModified())
        }

        /** Decodes persisted metadata without touching the filesystem. */
        fun decode(path: String?, format: String?, size: Long?, modified: Long?): DraftAudioFile? {
            if (path.isNullOrBlank() || format == null || size == null || modified == null) return null
            val known = AudioFormat.fromExtension(format) ?: return null
            if (size < 0 || modified < 0) return null
            return DraftAudioFile(path, known.extension, size, modified)
        }

        fun encode(file: DraftAudioFile): List<String?> = listOf(file.path, file.format, file.size.toString(), file.modified.toString())
    }
}

/** A private per-attempt staging file. The worker that created it owns it until it closes. */
class DraftAudioStaging internal constructor(
    val item: AudioItem,
    val attemptId: Long,
    val file: File,
    private val workerLease: AudioFileLease,
    private val operations: AudioFileOperations,
) : AutoCloseable {
    private var closed = false

    fun sync() {
        operations.sync(file)
    }

    /** Deletes a staging file that was not published. */
    override fun close() {
        if (closed) return
        closed = true
        if (file.exists()) file.delete()
        workerLease.close()
    }
}

enum class DraftAudioRetirement {
    RETIRED,
    ALREADY_GONE,
    DELAYED_BY_OWNER,
    OUTSIDE_ROOT,
    FAILED,
}

data class DraftAudioSweepReport(
    val deleted: List<String> = emptyList(),
    val protectedFiles: List<String> = emptyList(),
    val retainedUnknown: List<String> = emptyList(),
    val failures: List<String> = emptyList(),
    val outsideRoot: List<String> = emptyList(),
) {
    val changed: Boolean get() = deleted.isNotEmpty()
}

/**
 * Owns durable draft audio files and private per-attempt staging.
 *
 * Finals stay direct children of the existing `filesDir/draft` directory so the FileProvider
 * `<files-path path="draft/">` contract and AnkiDroid media import keep working. Staging lives
 * outside that directory (for example `filesDir/audio-staging`) so a partial provider file is
 * never reachable through the media provider.
 */
class DraftAudioFileStore(
    finalsDirectory: File,
    stagingDirectory: File,
    private val operations: AudioFileOperations = AudioFileOperations.system(),
    private val ownership: AudioOwnershipRegistry = AudioOwnershipRegistry.process,
) {
    private val finalsRoot: File = finalsDirectory.canonicalFile
    private val stagingRoot: File = stagingDirectory.canonicalFile

    init {
        require(stagingRoot != finalsRoot) { "Audio staging must not be the draft media directory" }
        require(!isInside(stagingRoot, finalsRoot)) { "Audio staging must stay outside the exported draft directory" }
        require(!isInside(finalsRoot, stagingRoot)) { "The draft media directory must not live inside audio staging" }
    }

    fun finalsDirectory(): File = finalsRoot

    fun stagingDirectory(): File = stagingRoot

    /** Creates the private staging file for one attempt and takes the worker lease on it. */
    @Throws(IOException::class)
    fun beginAttempt(
        item: AudioItem,
        attemptId: Long,
        draftEpoch: Long,
        expectedFormat: AudioFormat = AudioFormat.MP3,
    ): DraftAudioStaging {
        require(attemptId > 0) { "Attempt id must be positive" }
        if (!stagingRoot.isDirectory && !stagingRoot.mkdirs()) throw IOException("Could not create audio staging")
        val name = "${itemKey(item)}-$draftEpoch-$attemptId-${UUID.randomUUID()}.${expectedFormat.extension}"
        val file = File(stagingRoot, name)
        if (!file.createNewFile()) throw IOException("Could not create audio staging file")
        val lease = ownership.acquire(file, AudioFileOwner.WORKER)
        return DraftAudioStaging(item, attemptId, file, lease, operations)
    }

    /**
     * Validates a completed staging file and publishes it under its canonical final name.
     * The previous file is never touched here; the controller commits the replacement first.
     */
    @Throws(IOException::class)
    fun publish(
        staging: DraftAudioStaging,
        draftEpoch: Long,
        inputIdentity: String,
    ): DraftAudioFile {
        if (!staging.file.isFile || staging.file.length() == 0L) {
            throw IOException("Audio provider returned no usable audio")
        }
        val format = AudioFileValidator.requireContentFormat(staging.file)
        staging.sync()
        if (!finalsRoot.isDirectory && !finalsRoot.mkdirs()) throw IOException("Could not create draft media directory")
        val name = canonicalName(staging.item, draftEpoch, inputIdentity, staging.attemptId, format)
        val destination = File(finalsRoot, name)
        if (destination.canonicalFile.parentFile != finalsRoot) {
            throw IOException("Draft audio destination is outside the draft media directory")
        }
        operations.move(staging.file, destination)
        if (!destination.isFile) throw IOException("Could not publish draft audio")
        val published = DraftAudioFile.fromFile(destination)
        staging.close()
        return published
    }

    fun discard(staging: DraftAudioStaging) = staging.close()

    /** Deletes [file] only when it is a canonical child of the finals root and unowned. */
    fun retire(file: DraftAudioFile): DraftAudioRetirement = retire(file.file())

    fun retire(file: File): DraftAudioRetirement {
        val canonical = runCatching { file.canonicalFile }.getOrNull() ?: return DraftAudioRetirement.FAILED
        if (canonical.parentFile != finalsRoot) return DraftAudioRetirement.OUTSIDE_ROOT
        if (ownership.isLeased(canonical)) return DraftAudioRetirement.DELAYED_BY_OWNER
        if (!canonical.exists()) return DraftAudioRetirement.ALREADY_GONE
        return if (operations.delete(canonical)) DraftAudioRetirement.RETIRED else DraftAudioRetirement.FAILED
    }

    fun lease(file: DraftAudioFile, owner: AudioFileOwner): AudioFileLease = ownership.acquire(file.file(), owner)

    fun lease(file: File, owner: AudioFileOwner): AudioFileLease = ownership.acquire(file, owner)

    fun isOwned(file: File): Boolean = ownership.isLeased(file)

    fun ownersOf(file: File): Set<AudioFileOwner> = ownership.ownersOf(file)

    /**
     * Copies a validated source (cache entry or provider output) into the attempt's staging file
     * and fsyncs it. The final atomic publication happens in [publish].
     */
    @Throws(IOException::class)
    fun stageCopy(source: File, staging: DraftAudioStaging): AudioFormat {
        val format = AudioFileValidator.requireContentFormat(source)
        source.inputStream().use { input ->
            staging.file.outputStream().use { output ->
                input.copyTo(output, DEFAULT_BUFFER_SIZE)
                output.flush()
            }
        }
        staging.sync()
        if (AudioFileValidator.requireContentFormat(staging.file) != format) {
            throw IOException("Staged audio changed during copy")
        }
        return format
    }

    /**
     * Deletes recognized audio/staging files that nobody owns and that no live reference
     * protects. Only direct children of the two owned roots are considered; images, unknown
     * names, symlinks, NHK data and anything outside the roots are never touched. This is
     * ownership-based, never age-based.
     */
    fun sweep(protectedPaths: Collection<String>): DraftAudioSweepReport {
        val protected = protectedPaths.mapNotNullTo(HashSet()) { raw ->
            if (raw.isBlank()) null else runCatching { File(raw).canonicalPath }.getOrNull()
        }
        val deleted = mutableListOf<String>()
        val protectedFiles = mutableListOf<String>()
        val retainedUnknown = mutableListOf<String>()
        val failures = mutableListOf<String>()
        val outsideRoot = mutableListOf<String>()

        val finals = finalsRoot.listFiles().orEmpty()
        for (candidate in finals) {
            if (isSymlink(candidate)) {
                outsideRoot += candidate.name
                continue
            }
            val canonical = runCatching { candidate.canonicalFile }.getOrNull()
            if (canonical == null || canonical.parentFile != finalsRoot) {
                outsideRoot += candidate.name
                continue
            }
            if (!candidate.isFile) continue
            if (!isRecognizedDraftAudio(candidate.name)) {
                retainedUnknown += candidate.name
                continue
            }
            if (canonical.path in protected || ownership.isLeased(canonical)) {
                protectedFiles += candidate.name
                continue
            }
            if (operations.delete(candidate)) deleted += candidate.name else failures += candidate.name
        }

        for (candidate in stagingRoot.listFiles().orEmpty()) {
            if (isSymlink(candidate)) {
                outsideRoot += candidate.name
                continue
            }
            val canonical = runCatching { candidate.canonicalFile }.getOrNull()
            if (canonical == null || canonical.parentFile != stagingRoot) {
                outsideRoot += candidate.name
                continue
            }
            if (!candidate.isFile) continue
            if (canonical.path in protected || ownership.isLeased(canonical)) {
                protectedFiles += candidate.name
                continue
            }
            if (operations.delete(candidate)) deleted += candidate.name else failures += candidate.name
        }

        return DraftAudioSweepReport(
            deleted = deleted,
            protectedFiles = protectedFiles,
            retainedUnknown = retainedUnknown,
            failures = failures,
            outsideRoot = outsideRoot,
        )
    }

    private fun canonicalName(
        item: AudioItem,
        draftEpoch: Long,
        inputIdentity: String,
        attemptId: Long,
        format: AudioFormat,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$draftEpoch\u0000$inputIdentity".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(24)
        return "audio-${itemKey(item)}-$digest-$attemptId.${format.extension}"
    }

    private fun itemKey(item: AudioItem): String = when (item) {
        AudioItem.SENTENCE -> "sentence"
        AudioItem.VOCABULARY -> "word"
    }

    private fun isRecognizedDraftAudio(name: String): Boolean =
        CANONICAL_NAME.matches(name) || LEGACY_NAME.matches(name) || TEMPORARY_NAME.containsMatchIn(name)

    private fun isSymlink(file: File): Boolean = runCatching { java.nio.file.Files.isSymbolicLink(file.toPath()) }.getOrDefault(true)

    private fun isInside(child: File, parent: File): Boolean =
        runCatching { child.canonicalPath.startsWith(parent.canonicalPath + File.separator) }.getOrDefault(true)

    companion object {
        /** `audio-sentence-<digest>-<attempt>.<ext>`: the only name this store publishes. */
        val CANONICAL_NAME = Regex("audio-(sentence|word)-[0-9a-f]{8,64}-[0-9]+\\.(wav|ogg|mp3)")

        /** Flat files written by the pre-controller audio route. */
        val LEGACY_NAME = Regex("(sentence|word)-[A-Za-z0-9-]+\\.(wav|ogg|mp3)")

        /** Partial files left by earlier copy/rename implementations. */
        val TEMPORARY_NAME = Regex("^\\..*\\.tmp(\\.[A-Za-z0-9]+)?$")
    }
}
