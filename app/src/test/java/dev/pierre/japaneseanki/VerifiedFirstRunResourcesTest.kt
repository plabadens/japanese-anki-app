package dev.pierre.japaneseanki

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VerifiedFirstRunResourcesTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun manifestCopiesInputsAndRejectsUntrustedMetadata() {
        val bytes = byteArrayOf(0, 1, 2, 0xff.toByte())
        val entries = mutableListOf(ResourceArchiveEntry("dictionary.sqlite", 4))
        val resource = resource(bytes, archiveEntries = entries)
        entries.clear()
        val resources = mutableListOf(resource)
        val manifest = FirstRunResourceManifest(resources)
        resources.clear()

        assertEquals(1, manifest.resources.size)
        assertEquals("dictionary.sqlite", manifest.resources.single().archiveEntries.single().path)
        assertThrows(UnsupportedOperationException::class.java) {
            (manifest.resources as MutableList).clear()
        }
        assertThrows(IllegalArgumentException::class.java) { resource(bytes, uri = "http://example.invalid/file") }
        assertThrows(IllegalArgumentException::class.java) { resource(bytes, uri = "https://example.invalid/file?token=x") }
        assertThrows(IllegalArgumentException::class.java) { resource(bytes, hash = "not-a-hash") }
        assertThrows(IllegalArgumentException::class.java) { ResourceArchiveEntry("../escape", 1) }
        assertThrows(IllegalArgumentException::class.java) {
            FirstRunResourceManifest(listOf(resource(bytes), resource(bytes, id = "other", target = "dictionary-v1.bin")))
        }
    }

    @Test fun downloadsHashesAndAtomicallyPublishesOnlyVerifiedBytesWithProgress() {
        val bytes = byteArrayOf(0, 1, 2, 0x7f, 0x80.toByte(), 0xff.toByte())
        val spec = resource(bytes)
        val phases = mutableListOf<ResourceProgressPhase>()
        val (installer, root) = installerWithRoot(QueueTransport(response(bytes)))

        val ready = installer.install(spec, onProgress = { phases += it.phase })

        assertEquals(ResourceDisposition.DOWNLOADED, ready.disposition)
        assertEquals(bytes.toList(), ready.location.readBytes().toList())
        assertEquals(bytes.size.toLong(), ready.location.length())
        assertTrue(phases.contains(ResourceProgressPhase.DOWNLOADING))
        assertTrue(phases.contains(ResourceProgressPhase.VERIFYING))
        assertTrue(phases.contains(ResourceProgressPhase.ACTIVATING))
        assertFalse(File(root, ".${spec.id}-${spec.version}.part").exists())
    }

    @Test fun validInstalledResourceIsReusedOfflineWithoutOpeningTransport() {
        val bytes = "verified-once".toByteArray()
        val root = temporaryFolder.newFolder("offline")
        val spec = resource(bytes)
        File(root, spec.targetFileName).writeBytes(bytes)
        val transport = noNetworkTransport("valid local resource must not use network")

        val ready = VerifiedFirstRunResourceInstaller(root, transport).install(spec)

        assertEquals(ResourceDisposition.REUSED, ready.disposition)
        assertEquals(bytes.toList(), ready.location.readBytes().toList())
        assertEquals(0, transport.opens)
    }

    @Test fun validatedLegacyResourceIsAdoptedWithoutDownloadingOrDeletingIt() {
        val bytes = "existing sqlite contents".toByteArray()
        val legacy = File(temporaryFolder.newFolder("legacy"), "old-dictionary.sqlite").apply { writeBytes(bytes) }
        val transport = noNetworkTransport("validated legacy resource must not download")
        val spec = resource(bytes)

        val ready = installer(transport).install(
            spec,
            legacyCandidates = listOf(legacy),
            legacyValidator = LegacyResourceValidator { candidate, pinned ->
                candidate.isFile && candidate.length() == pinned.expectedBytes && sha256(candidate.readBytes()) == pinned.sha256
            },
        )

        assertEquals(ResourceDisposition.ADOPTED_LEGACY, ready.disposition)
        assertEquals(legacy.canonicalFile, ready.location.canonicalFile)
        assertEquals(bytes.toList(), legacy.readBytes().toList())
        assertEquals(0, transport.opens)
    }

    @Test fun interruptedTransferLeavesOldVersionAloneAndRetryStartsCleanly() {
        val oldBytes = "old version".toByteArray()
        val root = temporaryFolder.newFolder("versions")
        val oldFile = File(root, "dictionary-v1.sqlite").apply { writeBytes(oldBytes) }
        val bytes = "new resource payload".toByteArray()
        val spec = resource(bytes, version = "2", target = "dictionary-v2.sqlite")
        val partialThenFail = object : InputStream() {
            private var first = true
            override fun read(): Int = throw UnsupportedOperationException()
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (!first) throw IOException("connection reset")
                first = false
                val count = minOf(length, 3)
                bytes.copyInto(buffer, offset, 0, count)
                return count
            }
        }
        val transport = QueueTransport(response(bytes, body = partialThenFail), response(bytes))
        val installer = VerifiedFirstRunResourceInstaller(root, transport)

        assertThrows(ResourceTransferException::class.java) { installer.install(spec) }
        assertEquals(oldBytes.toList(), oldFile.readBytes().toList())
        assertFalse(File(root, ".${spec.id}-${spec.version}.part").exists())
        val retried = installer.install(spec)

        assertEquals(ResourceDisposition.DOWNLOADED, retried.disposition)
        assertEquals(bytes.toList(), retried.location.readBytes().toList())
        assertEquals(oldBytes.toList(), oldFile.readBytes().toList())
        assertEquals(2, transport.opens)
    }

    @Test fun cancellationDiscardsPartialAndNeverActivatesIt() {
        val bytes = "complete payload".toByteArray()
        val cancelled = AtomicBoolean(false)
        val body = object : InputStream() {
            private var sent = false
            override fun read(): Int = throw UnsupportedOperationException()
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (sent) return -1
                sent = true
                buffer[offset] = bytes[0]
                cancelled.set(true)
                return 1
            }
        }
        val spec = resource(bytes)
        val root = temporaryFolder.newFolder("cancelled")
        val installer = VerifiedFirstRunResourceInstaller(root, QueueTransport(response(bytes, body = body)))

        assertThrows(ResourceCancelledException::class.java) {
            installer.install(spec, cancellation = ResourceCancellation { cancelled.get() })
        }

        assertFalse(File(root, spec.targetFileName).exists())
        assertEquals(emptyList<File>(), root.listFiles()?.toList())
    }

    @Test fun hashLengthAndHttpFailuresAreRejectedBeforeActivation() {
        val expected = "manifest bytes".toByteArray()
        val wrong = "different bytes".toByteArray()
        val spec = resource(expected)
        val wrongRoot = temporaryFolder.newFolder("wrong-hash")
        val wrongHashInstaller = VerifiedFirstRunResourceInstaller(wrongRoot, QueueTransport(response(wrong)))
        assertThrows(ResourceIntegrityException::class.java) { wrongHashInstaller.install(spec) }
        assertFalse(File(wrongRoot, spec.targetFileName).exists())

        val short = installer(QueueTransport(response(expected.copyOf(expected.size - 1), contentLength = null)))
        assertThrows(ResourceIntegrityException::class.java) { short.install(spec) }

        val wrongLength = installer(QueueTransport(response(expected, contentLength = expected.size.toLong() + 1)))
        assertThrows(ResourceIntegrityException::class.java) { wrongLength.install(spec) }

        val unavailable = installer(QueueTransport(response(expected, status = 503)))
        assertThrows(ResourceHttpException::class.java) { unavailable.install(spec) }
    }

    @Test fun insufficientStorageFailsBeforeNetworkAccess() {
        val bytes = ByteArray(32) { it.toByte() }
        val transport = noNetworkTransport("storage preflight must run first")
        val root = temporaryFolder.newFolder("storage")
        val installer = VerifiedFirstRunResourceInstaller(root, transport) { 0L }

        assertThrows(ResourceStorageException::class.java) { installer.install(resource(bytes)) }
        assertEquals(0, transport.opens)
    }

    @Test fun storageFailureDuringStreamingKeepsExistingVersionAndCleansPartial() {
        val root = temporaryFolder.newFolder("mid-transfer-storage")
        val oldBytes = "old version".toByteArray()
        val old = File(root, "dictionary-v1.bin").apply { writeBytes(oldBytes) }
        val newBytes = "new version that cannot fit".toByteArray()
        val body = object : InputStream() {
            private var first = true
            override fun read(): Int = throw UnsupportedOperationException()
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (!first) throw IOException("No space left on device")
                first = false
                buffer[offset] = newBytes[0]
                return 1
            }
        }
        val spec = resource(newBytes, version = "2", target = "dictionary-v2.bin")
        val installer = VerifiedFirstRunResourceInstaller(root, QueueTransport(response(newBytes, body = body)))

        assertThrows(ResourceStorageException::class.java) { installer.install(spec) }

        assertEquals(oldBytes.toList(), old.readBytes().toList())
        assertFalse(File(root, spec.targetFileName).exists())
        assertFalse(File(root, ".${spec.id}-${spec.version}.part").exists())
    }

    @Test fun zipRequiresExactSafeEntriesAndChecksCorruptionBeforeActivation() {
        val content = "runtime dictionary".toByteArray()
        val validZip = zipOf("unidic/sys.dic" to content)
        val validSpec = resource(
            validZip,
            id = "unidic",
            target = "unidic-runtime.zip",
            archiveEntries = listOf(ResourceArchiveEntry("unidic/sys.dic", content.size.toLong())),
        )
        val progress = mutableListOf<ResourceProgress>()
        val valid = installer(QueueTransport(response(validZip))).install(validSpec, onProgress = { progress += it })
        assertEquals(ResourceDisposition.DOWNLOADED, valid.disposition)
        assertTrue(progress.any { it.phase == ResourceProgressPhase.VALIDATING_ARCHIVE })

        val traversalZip = zipOf("../escape" to content)
        val traversalSpec = resource(
            traversalZip,
            id = "unidic",
            version = "2",
            target = "unidic-runtime-v2.zip",
            archiveEntries = listOf(ResourceArchiveEntry("unidic/sys.dic", content.size.toLong())),
        )
        val traversalRoot = temporaryFolder.newFolder("traversal")
        val traversalInstaller = VerifiedFirstRunResourceInstaller(traversalRoot, QueueTransport(response(traversalZip)))
        assertThrows(ResourceArchiveException::class.java) { traversalInstaller.install(traversalSpec) }
        assertFalse(File(traversalRoot, traversalSpec.targetFileName).exists())

        val corrupt = "not a zip".toByteArray()
        val corruptSpec = resource(
            corrupt,
            id = "unidic",
            version = "3",
            target = "unidic-runtime-v3.zip",
            archiveEntries = listOf(ResourceArchiveEntry("unidic/sys.dic", content.size.toLong())),
        )
        assertThrows(ResourceArchiveException::class.java) { installer(QueueTransport(response(corrupt))).install(corruptSpec) }

        val unexpectedZip = zipOf("unidic/sys.dic" to content, "unlisted.txt" to byteArrayOf(1))
        val unexpectedSpec = resource(
            unexpectedZip,
            id = "unidic",
            version = "4",
            target = "unidic-runtime-v4.zip",
            archiveEntries = listOf(ResourceArchiveEntry("unidic/sys.dic", content.size.toLong())),
        )
        assertThrows(ResourceArchiveException::class.java) { installer(QueueTransport(response(unexpectedZip))).install(unexpectedSpec) }
    }

    @Test fun manifestLimitRejectsOversizedPayloadBeforeAllocationOrTransport() {
        assertThrows(IllegalArgumentException::class.java) {
            FirstRunResource(
                id = "dictionary",
                version = "1",
                uri = URI("https://example.invalid/dictionary.bin"),
                sha256 = "0".repeat(64),
                expectedBytes = 2L * 1024 * 1024 * 1024 + 1,
                targetFileName = "dictionary.bin",
            )
        }
    }

    private fun resource(
        bytes: ByteArray,
        id: String = "dictionary",
        version: String = "1",
        target: String = "dictionary-v1.bin",
        hash: String = sha256(bytes),
        uri: String = "https://resources.example.invalid/$target",
        archiveEntries: Collection<ResourceArchiveEntry> = emptyList(),
    ) = FirstRunResource(id, version, URI(uri), hash, bytes.size.toLong(), target, archiveEntries)

    private fun installer(transport: ResourceTransport) =
        VerifiedFirstRunResourceInstaller(temporaryFolder.newFolder("root-${System.nanoTime()}"), transport)

    private fun noNetworkTransport(message: String) =
        QueueTransport(*arrayOf<() -> ResourceResponse>({ throw AssertionError(message) }))

    private fun installerWithRoot(transport: ResourceTransport): Pair<VerifiedFirstRunResourceInstaller, File> {
        val root = temporaryFolder.newFolder("root-${System.nanoTime()}")
        return VerifiedFirstRunResourceInstaller(root, transport) to root
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun response(
        expectedBytes: ByteArray,
        status: Int = 200,
        contentLength: Long? = expectedBytes.size.toLong(),
        body: InputStream = ByteArrayInputStream(expectedBytes),
    ) = object : ResourceResponse {
        override val statusCode: Int = status
        override val contentLength: Long? = contentLength
        override val body: InputStream = body
        override fun close() = body.close()
    }

    private class QueueTransport(vararg replies: () -> ResourceResponse) : ResourceTransport {
        private val queue = ArrayDeque(replies.toList())
        var opens = 0
            private set

        constructor(vararg responses: ResourceResponse) : this(*responses.map { response -> { response } }.toTypedArray())

        override fun open(uri: URI): ResourceResponse {
            opens++
            val next = queue.pollFirst() ?: throw IOException("No test response remains")
            return next()
        }
    }

    private fun response(bytes: ByteArray, body: InputStream) = response(bytes, contentLength = null, body = body)

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, contents) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(contents)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
