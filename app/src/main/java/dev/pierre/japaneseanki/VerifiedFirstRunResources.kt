package dev.pierre.japaneseanki

import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.CRC32
import java.util.zip.ZipFile

private const val MAX_RESOURCE_BYTES = 2L * 1024 * 1024 * 1024 - 1

private fun isSafeArchivePath(path: String): Boolean {
    if (path.isBlank() || path.startsWith('/') || '\\' in path || ':' in path || '\u0000' in path) return false
    return path.split('/').all { part -> part.isNotEmpty() && part != "." && part != ".." }
}

/** One exact path and uncompressed size expected inside a ZIP resource. */
class ResourceArchiveEntry(val path: String, val sizeBytes: Long) {
    init {
        require(isSafeArchivePath(path)) { "Unsafe resource archive path" }
        require(sizeBytes > 0 && sizeBytes <= MAX_RESOURCE_BYTES) { "Invalid archive entry size" }
    }
}

/**
 * Immutable build-pinned metadata for one resource. The hash covers the bytes downloaded and
 * activated (the ZIP bytes for archive resources), not a URL response or an extracted directory.
 */
class FirstRunResource(
    val id: String,
    val version: String,
    val uri: URI,
    val sha256: String,
    val expectedBytes: Long,
    val targetFileName: String,
    archiveEntries: Collection<ResourceArchiveEntry> = emptyList(),
) {
    val archiveEntries: List<ResourceArchiveEntry> =
        Collections.unmodifiableList(ArrayList(archiveEntries))

    internal val archiveEntriesByPath: Map<String, ResourceArchiveEntry>

    init {
        require(id.matches(Regex("[a-z][a-z0-9-]{0,63}"))) { "Invalid resource id" }
        require(version.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}"))) { "Invalid resource version" }
        require(uri.isAbsolute && uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank() && uri.userInfo == null && uri.rawQuery == null && uri.fragment == null
        ) { "Resource URL must be HTTPS and must not contain credentials, query parameters, or a fragment" }
        require(sha256.matches(Regex("[0-9a-f]{64}"))) { "Resource SHA-256 must be 64 lowercase hexadecimal characters" }
        require(expectedBytes in 1..MAX_RESOURCE_BYTES) { "Invalid resource byte limit" }
        require(targetFileName.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) &&
            targetFileName != "." && targetFileName != ".."
        ) { "Invalid resource target file name" }

        val entries = LinkedHashMap<String, ResourceArchiveEntry>()
        this.archiveEntries.forEach { entry ->
            require(entries.put(entry.path, entry) == null) { "Duplicate resource archive path" }
        }
        var expandedBytes = 0L
        entries.values.forEach { entry ->
            require(expandedBytes <= MAX_RESOURCE_BYTES - entry.sizeBytes) {
                "Resource archive expands beyond the supported limit"
            }
            expandedBytes += entry.sizeBytes
        }
        archiveEntriesByPath = Collections.unmodifiableMap(entries)
    }
}

/** A validated immutable index; duplicate ids and target paths are rejected at construction. */
class FirstRunResourceManifest(resources: Collection<FirstRunResource>) {
    val resources: List<FirstRunResource> = Collections.unmodifiableList(ArrayList(resources))
    private val byId: Map<String, FirstRunResource>

    init {
        require(this.resources.isNotEmpty()) { "Resource manifest must not be empty" }
        val ids = LinkedHashMap<String, FirstRunResource>()
        val targets = HashSet<String>()
        this.resources.forEach { resource ->
            require(ids.put(resource.id, resource) == null) { "Duplicate resource id" }
            require(targets.add(resource.targetFileName)) { "Duplicate resource target" }
        }
        byId = Collections.unmodifiableMap(ids)
    }

    operator fun get(id: String): FirstRunResource? = byId[id]
}

fun interface ResourceTransport {
    @Throws(IOException::class)
    fun open(uri: URI): ResourceResponse
}

/** Optional byte-range support used only to resume an interrupted, already-pinned transfer. */
interface ResumableResourceTransport : ResourceTransport {
    @Throws(IOException::class)
    fun open(uri: URI, rangeStart: Long): ResourceResponse

    override fun open(uri: URI): ResourceResponse = open(uri, 0L)
}

interface ResourceResponse : Closeable {
    val statusCode: Int
    /** Null means the server did not send Content-Length. */
    val contentLength: Long?
    /** Null means the server did not send Content-Range. */
    val contentRange: String? get() = null
    val body: java.io.InputStream
}

/** HTTPS-only transport with bounded HTTPS redirects. Redirects never downgrade to cleartext. */
class HttpsResourceTransport(
    private val connectTimeoutMillis: Int = 15_000,
    private val readTimeoutMillis: Int = 30_000,
    private val maxRedirects: Int = 4,
) : ResumableResourceTransport {
    init {
        require(connectTimeoutMillis > 0 && readTimeoutMillis > 0 && maxRedirects in 0..10)
    }

    override fun open(uri: URI, rangeStart: Long): ResourceResponse {
        require(rangeStart >= 0) { "Resource byte range must not be negative" }
        require(uri.scheme.equals("https", ignoreCase = true)) { "Only HTTPS resources are allowed" }
        var current = uri
        repeat(maxRedirects + 1) { redirectCount ->
            val connection = current.toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.instanceFollowRedirects = false
            if (rangeStart > 0L) connection.setRequestProperty("Range", "bytes=$rangeStart-")
            try {
                val status = connection.responseCode
                if (status in REDIRECT_CODES) {
                    val location = connection.getHeaderField("Location")
                        ?: throw IOException("Resource server returned a redirect without a location")
                    if (redirectCount == maxRedirects) throw IOException("Resource server exceeded redirect limit")
                    val next = try {
                        current.resolve(location)
                    } catch (_: IllegalArgumentException) {
                        throw IOException("Resource server returned an invalid redirect")
                    }
                    if (!next.scheme.equals("https", ignoreCase = true) || next.host.isNullOrBlank() ||
                        next.userInfo != null || next.fragment != null
                    ) throw IOException("Resource redirect is not a stable HTTPS URL")
                    connection.disconnect()
                    current = next
                } else {
                    val stream = if (status == HttpURLConnection.HTTP_OK || status == HttpURLConnection.HTTP_PARTIAL) {
                        connection.inputStream
                    } else {
                        connection.errorStream ?: ByteArrayInputStream(ByteArray(0))
                    }
                    return HttpResponse(
                        connection,
                        status,
                        connection.contentLengthLong.takeIf { it >= 0 },
                        connection.getHeaderField("Content-Range"),
                        stream,
                    )
                }
            } catch (failure: Exception) {
                connection.disconnect()
                if (failure is IOException) throw failure
                throw IOException("Could not open resource response", failure)
            }
        }
        throw IOException("Resource redirect limit exceeded")
    }

    private class HttpResponse(
        private val connection: HttpURLConnection,
        override val statusCode: Int,
        override val contentLength: Long?,
        override val contentRange: String?,
        override val body: java.io.InputStream,
    ) : ResourceResponse {
        override fun close() {
            try {
                body.close()
            } finally {
                connection.disconnect()
            }
        }
    }

    private companion object {
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }
}

fun interface ResourceCancellation {
    fun isCancelled(): Boolean

    companion object {
        val NEVER = ResourceCancellation { false }
    }
}

enum class ResourceProgressPhase { DOWNLOADING, VERIFYING, VALIDATING_ARCHIVE, ACTIVATING }

data class ResourceProgress(
    val resourceId: String,
    val phase: ResourceProgressPhase,
    val completedBytes: Long,
    val totalBytes: Long,
)

enum class ResourceDisposition { DOWNLOADED, REUSED, ADOPTED_LEGACY }

data class ReadyResource(val resource: FirstRunResource, val location: File, val disposition: ResourceDisposition)

fun interface LegacyResourceValidator {
    /** Must verify the installed legacy object (schema and/or content), not just that it exists. */
    fun isValid(candidate: File, resource: FirstRunResource): Boolean
}

open class ResourceInstallException(message: String, cause: Throwable? = null) : IOException(message, cause)
class ResourceHttpException(val status: Int) : ResourceInstallException("Resource server returned HTTP $status")
class ResourceTransferException(message: String, cause: Throwable? = null) : ResourceInstallException(message, cause)
class ResourceIntegrityException(message: String) : ResourceInstallException(message)
class ResourceArchiveException(message: String, cause: Throwable? = null) : ResourceInstallException(message, cause)
class ResourceStorageException(message: String, cause: Throwable? = null) : ResourceInstallException(message, cause)
class ResourceActivationException(message: String, cause: Throwable? = null) : ResourceInstallException(message, cause)
class ResourceCancelledException : ResourceInstallException("Resource download cancelled")

/**
 * Pure JVM download/verification/activation core. It is deliberately not wired to runtime lookups:
 * onboarding calls it, while the existing dictionary and morphology code stay offline-only.
 * A failed transfer leaves the previously active resource untouched; retry starts from byte zero.
 */
class VerifiedFirstRunResourceInstaller(
    private val rootDirectory: File,
    private val transport: ResourceTransport = HttpsResourceTransport(),
    private val availableBytes: (File) -> Long = { it.usableSpace },
) {
    private val rootPath = rootDirectory.toPath().toAbsolutePath().normalize()

    fun install(
        resource: FirstRunResource,
        legacyCandidates: Collection<File> = emptyList(),
        legacyValidator: LegacyResourceValidator? = null,
        cancellation: ResourceCancellation = ResourceCancellation.NEVER,
        onProgress: (ResourceProgress) -> Unit = {},
    ): ReadyResource {
        val lockKey = "$rootPath/${resource.id}/${resource.version}"
        val lock = installLocks.computeIfAbsent(lockKey) { Any() }
        return synchronized(lock) {
            checkCancelled(cancellation)
            prepareRoot()
            val target = rootPath.resolve(resource.targetFileName).toFile()
            if (isValidDownloaded(target, resource, cancellation)) {
                return@synchronized ReadyResource(resource, target, ResourceDisposition.REUSED)
            }
            if (legacyValidator != null) {
                legacyCandidates.forEach { candidate ->
                    checkCancelled(cancellation)
                    if (candidate.exists() && !Files.isSymbolicLink(candidate.toPath()) &&
                        legacyValidator.isValid(candidate, resource)
                    ) return@synchronized ReadyResource(resource, candidate, ResourceDisposition.ADOPTED_LEGACY)
                }
            }

            val part = rootPath.resolve(".${resource.id}-${resource.version}.part").toFile()
            var preserveInterruptedPart = false
            try {
                if (availableBytes(rootPath.toFile()) < resource.expectedBytes) {
                    throw ResourceStorageException("Not enough free storage for ${resource.id}")
                }
                download(resource, part, cancellation, onProgress)
                if (resource.archiveEntries.isNotEmpty()) {
                    val archiveBytes = resource.archiveEntries.sumOf { it.sizeBytes }
                    onProgress(ResourceProgress(resource.id, ResourceProgressPhase.VALIDATING_ARCHIVE, 0, archiveBytes))
                    validateArchive(part, resource, cancellation, onProgress)
                }
                checkCancelled(cancellation)
                onProgress(
                    ResourceProgress(
                        resource.id,
                        ResourceProgressPhase.ACTIVATING,
                        resource.expectedBytes,
                        resource.expectedBytes,
                    ),
                )
                try {
                    Files.move(
                        part.toPath(),
                        target.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                } catch (failure: AtomicMoveNotSupportedException) {
                    throw ResourceActivationException("Atomic resource activation is not supported on this storage", failure)
                } catch (failure: IOException) {
                    throw ResourceActivationException("Could not activate verified resource ${resource.id}", failure)
                }
                ReadyResource(resource, target, ResourceDisposition.DOWNLOADED)
            } catch (failure: ResourceTransferException) {
                preserveInterruptedPart = transport is ResumableResourceTransport &&
                    part.isFile && part.length() in 1 until resource.expectedBytes
                throw failure
            } catch (failure: ResourceInstallException) {
                throw failure
            } catch (failure: IOException) {
                if (Thread.currentThread().isInterrupted) throw ResourceCancelledException()
                if (isStorageFailure(failure)) {
                    throw ResourceStorageException("Storage filled while installing ${resource.id}; existing resources were retained", failure)
                }
                throw ResourceTransferException("Could not download resource ${resource.id}; retry is safe", failure)
            } finally {
                // A killed process or transient transfer failure may leave a resumable prefix.
                // User cancellation and integrity/activation failures still remove it.
                if (!preserveInterruptedPart) {
                    try { Files.deleteIfExists(part.toPath()) } catch (_: IOException) { }
                }
            }
        }
    }

    private fun prepareRoot() {
        if (Files.isSymbolicLink(rootPath)) throw ResourceStorageException("Resource directory must not be a symbolic link")
        if (!Files.exists(rootPath) && !rootPath.toFile().mkdirs()) {
            throw ResourceStorageException("Could not create resource storage")
        }
        if (!Files.isDirectory(rootPath, LinkOption.NOFOLLOW_LINKS)) {
            throw ResourceStorageException("Resource storage is not a directory")
        }
    }

    private fun isValidDownloaded(file: File, resource: FirstRunResource, cancellation: ResourceCancellation): Boolean {
        if (!Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS) || file.length() != resource.expectedBytes) return false
        return try {
            checkCancelled(cancellation)
            validateDigest(file, resource, cancellation)
            if (resource.archiveEntries.isNotEmpty()) validateArchive(file, resource, cancellation) { }
            true
        } catch (cancelled: ResourceCancelledException) {
            throw cancelled
        } catch (_: ResourceInstallException) {
            false
        } catch (_: IOException) {
            false
        }
    }

    private fun download(
        resource: FirstRunResource,
        part: File,
        cancellation: ResourceCancellation,
        onProgress: (ResourceProgress) -> Unit,
    ) {
        checkCancelled(cancellation)
        val rangeTransport = transport as? ResumableResourceTransport
        var offset = if (
            Files.isRegularFile(part.toPath(), LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(part.toPath())
        ) {
            part.length()
        } else {
            0L
        }
        if (offset <= 0L || offset > resource.expectedBytes || rangeTransport == null) {
            if (Files.exists(part.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                try {
                    Files.deleteIfExists(part.toPath())
                } catch (failure: IOException) {
                    throw ResourceStorageException("Could not clear invalid partial resource state", failure)
                }
            }
            offset = 0L
        }

        val digest = MessageDigest.getInstance("SHA-256")
        if (offset > 0L) {
            var prefixBytes = 0L
            try {
                part.inputStream().buffered().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        checkCancelled(cancellation)
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                        prefixBytes += count
                    }
                }
            } catch (failure: IOException) {
                throw ResourceStorageException("Could not validate partial resource state", failure)
            }
            if (prefixBytes != offset) throw ResourceStorageException("Partial resource changed while resuming")
            onProgress(ResourceProgress(resource.id, ResourceProgressPhase.DOWNLOADING, offset, resource.expectedBytes))
        }
        if (offset == resource.expectedBytes) {
            onProgress(ResourceProgress(resource.id, ResourceProgressPhase.VERIFYING, offset, resource.expectedBytes))
            validateDigest(part, resource, cancellation)
            return
        }

        val response = try {
            if (rangeTransport != null) rangeTransport.open(resource.uri, offset)
            else transport.open(resource.uri)
        } catch (failure: IOException) {
            throw ResourceTransferException("Could not connect to the resource server", failure)
        }
        response.use { opened ->
            if (offset == 0L) {
                if (opened.statusCode != HttpURLConnection.HTTP_OK) throw ResourceHttpException(opened.statusCode)
            } else {
                if (opened.statusCode != HttpURLConnection.HTTP_PARTIAL) {
                    throw ResourceTransferException("Resource server did not honor the requested byte range")
                }
                val expectedRange = "bytes $offset-${resource.expectedBytes - 1}/${resource.expectedBytes}"
                if (opened.contentRange != expectedRange) {
                    throw ResourceIntegrityException("Resource byte range did not match the manifest")
                }
            }
            opened.contentLength?.let { length ->
                if (length != resource.expectedBytes - offset) {
                    throw ResourceIntegrityException("Resource length did not match the manifest")
                }
            }
            var received = offset
            onProgress(ResourceProgress(resource.id, ResourceProgressPhase.DOWNLOADING, received, resource.expectedBytes))
            try {
                FileOutputStream(part, offset > 0L).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        checkCancelled(cancellation)
                        val count = try {
                            opened.body.read(buffer)
                        } catch (failure: IOException) {
                            if (Thread.currentThread().isInterrupted) throw ResourceCancelledException()
                            throw ResourceTransferException("Could not read the resource response", failure)
                        }
                        if (count < 0) break
                        if (count == 0) continue
                        if (received > resource.expectedBytes - count) {
                            throw ResourceIntegrityException("Resource exceeded its manifest byte limit")
                        }
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        received += count
                        onProgress(
                            ResourceProgress(
                                resource.id,
                                ResourceProgressPhase.DOWNLOADING,
                                received,
                                resource.expectedBytes,
                            ),
                        )
                    }
                    output.fd.sync()
                }
            } catch (failure: ResourceInstallException) {
                throw failure
            } catch (failure: IOException) {
                throw ResourceStorageException("Could not persist resource data", failure)
            }
            if (received != resource.expectedBytes) {
                throw ResourceTransferException("Resource response ended before its manifest length")
            }
            onProgress(ResourceProgress(resource.id, ResourceProgressPhase.VERIFYING, received, resource.expectedBytes))
            // Keep the digest from the bounded streaming pass; no second network read is possible.
            val actual = digest.digest().toHex()
            if (actual != resource.sha256) throw ResourceIntegrityException("Resource SHA-256 did not match the build manifest")
        }
    }

    private fun validateDigest(
        file: File,
        resource: FirstRunResource,
        cancellation: ResourceCancellation,
    ) {
        val digest = MessageDigest.getInstance("SHA-256")
        var bytesRead = 0L
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                checkCancelled(cancellation)
                val count = input.read(buffer)
                if (count < 0) break
                if (bytesRead > resource.expectedBytes - count) {
                    throw ResourceIntegrityException("Installed resource exceeded its manifest byte limit")
                }
                digest.update(buffer, 0, count)
                bytesRead += count
            }
        }
        if (bytesRead != resource.expectedBytes) {
            throw ResourceIntegrityException("Installed resource length did not match the manifest")
        }
        if (digest.digest().toHex() != resource.sha256) {
            throw ResourceIntegrityException("Resource SHA-256 did not match the build manifest")
        }
    }

    private fun validateArchive(
        file: File,
        resource: FirstRunResource,
        cancellation: ResourceCancellation,
        onProgress: (ResourceProgress) -> Unit = {},
    ) {
        val expected = resource.archiveEntriesByPath
        val totalExpandedBytes = expected.values.sumOf { it.sizeBytes }
        val seen = HashSet<String>()
        var expanded = 0L
        try {
            ZipFile(file).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    checkCancelled(cancellation)
                    val entry = entries.nextElement()
                    if (!isSafeArchivePath(entry.name) || entry.isDirectory) {
                        throw ResourceArchiveException("Resource archive contains an unsafe entry")
                    }
                    val specification = expected[entry.name]
                        ?: throw ResourceArchiveException("Resource archive contains an unlisted entry")
                    if (!seen.add(entry.name) || entry.size != specification.sizeBytes) {
                        throw ResourceArchiveException("Resource archive entry did not match its manifest")
                    }
                    val crc = CRC32()
                    var entryBytes = 0L
                    zip.getInputStream(entry).use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            checkCancelled(cancellation)
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (count == 0) continue
                            if (entryBytes > specification.sizeBytes - count) {
                                throw ResourceArchiveException("Resource archive entry exceeded its size limit")
                            }
                            crc.update(buffer, 0, count)
                            entryBytes += count
                            expanded += count
                            onProgress(
                                ResourceProgress(
                                    resource.id,
                                    ResourceProgressPhase.VALIDATING_ARCHIVE,
                                    expanded,
                                    totalExpandedBytes,
                                ),
                            )
                        }
                    }
                    if (entryBytes != specification.sizeBytes) {
                        throw ResourceArchiveException("Resource archive entry ended early")
                    }
                    if (entry.crc < 0 || crc.value != entry.crc) {
                        throw ResourceArchiveException("Resource archive entry CRC did not match its directory")
                    }
                }
            }
        } catch (failure: ResourceInstallException) {
            throw failure
        } catch (failure: IOException) {
            throw ResourceArchiveException("Resource archive is corrupt", failure)
        }
        if (seen != expected.keys) throw ResourceArchiveException("Resource archive is missing a required entry")
    }

    private fun checkCancelled(cancellation: ResourceCancellation) {
        if (Thread.currentThread().isInterrupted || cancellation.isCancelled()) throw ResourceCancelledException()
    }

    private fun isStorageFailure(failure: IOException): Boolean {
        var cause: Throwable? = failure
        while (cause != null) {
            val message = when (cause) {
                is FileSystemException -> cause.reason ?: cause.message
                else -> cause.message
            }?.lowercase().orEmpty()
            if ("no space" in message || "disk full" in message || "quota exceeded" in message || "enospc" in message) {
                return true
            }
            cause = cause.cause
        }
        return false
    }

    private fun ByteArray.toHex(): String = buildString(size * 2) {
        val digits = "0123456789abcdef"
        for (byte in this@toHex) {
            val value = byte.toInt() and 0xff
            append(digits[value ushr 4])
            append(digits[value and 0x0f])
        }
    }

    private companion object {
        val installLocks = ConcurrentHashMap<String, Any>()
    }
}
