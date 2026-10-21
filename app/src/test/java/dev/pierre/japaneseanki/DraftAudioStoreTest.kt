package dev.pierre.japaneseanki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

/** Item 4: private staging, canonical finals, ownership-aware retirement and sweep. */
class DraftAudioStoreTest {
    private class Paths {
        val root: File = kotlin.io.path.createTempDirectory("draft-audio-store").toFile()
        val finals: File = File(root, "draft").apply { mkdirs() }
        val staging: File = File(root, "audio-staging").apply { mkdirs() }
        fun close() = root.deleteRecursively()
    }

    private fun store(paths: Paths, operations: AudioFileOperations = AudioFileOperations.system()) =
        DraftAudioFileStore(paths.finals, paths.staging, operations, AudioOwnershipRegistry())

    @Test
    fun publishKeepsFinalsDirectChildrenAndStagingOutsideExportedRoot() {
        val paths = Paths()
        try {
            val store = store(paths)
            val staging = store.beginAttempt(AudioItem.SENTENCE, 1, 4, AudioFormat.MP3)
            assertFalse("staging must not live in the exported draft directory", staging.file.parentFile == paths.finals)
            assertTrue(staging.file.parentFile == paths.staging.canonicalFile)
            AudioFixtures.mp3(staging.file)

            val published = store.publish(staging, 4, "identity-a")
            assertTrue(published.file().isFile)
            assertEquals(paths.finals.canonicalFile, published.file().parentFile)
            assertEquals(AudioFormat.MP3.extension, published.format)
            assertTrue(DraftAudioFileStore.CANONICAL_NAME.matches(published.file().name))
            assertFalse("published staging file is gone", staging.file.exists())
            assertFalse("worker lease released after publish", store.isOwned(published.file().absoluteFile))
            assertTrue(store.ownersOf(staging.file).isEmpty())
        } finally {
            paths.close()
        }
    }

    @Test
    fun publishRejectsInvalidOrEmptyAudioAndLeavesNoFinal() {
        val paths = Paths()
        try {
            val store = store(paths)
            val staging = store.beginAttempt(AudioItem.VOCABULARY, 1, 0, AudioFormat.OGG)
            try {
                store.publish(staging, 0, "identity-b")
                assertTrue("empty staging must be rejected", false)
            } catch (_: IOException) {
                // expected
            }
            assertEquals("no final published", 0, paths.finals.listFiles().orEmpty().size)
            staging.file.writeBytes(ByteArray(400) { 0x00 })
            var failed = false
            try {
                store.publish(staging, 0, "identity-b")
            } catch (_: IOException) {
                failed = true
            }
            assertTrue("garbage must be rejected", failed)
            assertEquals("no final published", 0, paths.finals.listFiles().orEmpty().size)
            staging.close()
            assertFalse("staging cleaned up on close", staging.file.exists())
        } finally {
            paths.close()
        }
    }

    @Test
    fun retireRefusesOutsideRootAndDelaysForOwners() {
        val paths = Paths()
        try {
            val ownership = AudioOwnershipRegistry()
            val store = DraftAudioFileStore(paths.finals, paths.staging, AudioFileOperations.system(), ownership)
            val outside = AudioFixtures.wav(File(paths.root, "outside.wav"))
            assertEquals(DraftAudioRetirement.OUTSIDE_ROOT, store.retire(outside))

            val accepted = store.publish(store.beginAttempt(AudioItem.SENTENCE, 1, 0).also {
                AudioFixtures.wav(it.file)
            }, 0, "identity-c")
            val playback = store.lease(accepted, AudioFileOwner.PLAYBACK)
            assertEquals(DraftAudioRetirement.DELAYED_BY_OWNER, store.retire(accepted))
            assertTrue("file must survive while a player reads it", accepted.file().isFile)
            playback.close()
            assertEquals(DraftAudioRetirement.RETIRED, store.retire(accepted))
            assertFalse(accepted.file().exists())
            assertEquals(DraftAudioRetirement.ALREADY_GONE, store.retire(accepted))
        } finally {
            paths.close()
        }
    }

    @Test
    fun sweepDeletesOnlyUnownedRecognizedAudioInsideOwnedRoots() {
        val paths = Paths()
        try {
            val ownership = AudioOwnershipRegistry()
            val store = DraftAudioFileStore(paths.finals, paths.staging, AudioFileOperations.system(), ownership)
            val canonical = AudioFixtures.mp3(File(paths.finals, "audio-sentence-abcdef0123456789abcdef01-1.mp3"))
            val legacy = AudioFixtures.wav(File(paths.finals, "sentence-1700000000000.wav"))
            val temporary = AudioFixtures.mp3(File(paths.finals, ".sentence-1.mp3.123.tmp.mp3"))
            val unknown = File(paths.finals, "notes.txt").also { it.writeText("keep me") }
            val image = File(paths.finals, "photo-1.webp").also { it.writeBytes(ByteArray(32)) }
            val protectedFile = AudioFixtures.ogg(File(paths.finals, "audio-word-deadbeefdeadbeefdeadbeef-2.ogg"))
            val leasedFile = AudioFixtures.ogg(File(paths.finals, "audio-word-feedfacefeedfacefeedface-3.ogg"))
            val lease = ownership.acquire(leasedFile, AudioFileOwner.IMPORT)
            val stagingOrphan = File(paths.staging, "word-0-1-abcd.ogg").also { AudioFixtures.ogg(it) }
            val stagingLeased = File(paths.staging, "sentence-0-2-efgh.mp3").also { AudioFixtures.mp3(it) }
            val workerLease = ownership.acquire(stagingLeased, AudioFileOwner.WORKER)

            val report = store.sweep(listOf(protectedFile.absolutePath, "", "   ", "/not/a/real/path"))
            assertTrue(report.deleted.contains(canonical.name))
            assertTrue(report.deleted.contains(legacy.name))
            assertTrue(report.deleted.contains(temporary.name))
            assertTrue(report.deleted.contains(stagingOrphan.name))
            assertTrue(report.protectedFiles.contains(protectedFile.name))
            assertTrue(report.protectedFiles.contains(leasedFile.name))
            assertTrue(report.protectedFiles.contains(stagingLeased.name))
            assertTrue(report.retainedUnknown.contains(unknown.name))
            assertTrue(report.retainedUnknown.contains(image.name))
            assertTrue("unknown file kept", unknown.isFile)
            assertTrue("image kept", image.isFile)
            assertTrue("protected audio kept", protectedFile.isFile)
            assertTrue("leased audio kept", leasedFile.isFile)
            assertTrue("leased staging kept", stagingLeased.isFile)
            lease.close()
            workerLease.close()
        } finally {
            paths.close()
        }
    }

    @Test
    fun sweepNeverFollowsSymlinksOrDeletesOutsideTargets() {
        val paths = Paths()
        try {
            val store = store(paths)
            val outside = AudioFixtures.mp3(File(paths.root, "outside-target.mp3"))
            val link = File(paths.finals, "audio-sentence-0123456789abcdef01234567-9.mp3")
            Files.createSymbolicLink(link.toPath(), outside.toPath())
            val report = store.sweep(emptyList())
            assertTrue("symlink is reported, not followed", report.outsideRoot.contains(link.name))
            assertTrue("symlink target must survive", outside.isFile)
            assertTrue("symlink itself must survive", Files.isSymbolicLink(link.toPath()))
        } finally {
            paths.close()
        }
    }

    @Test
    fun sweepReportsDeleteFailuresWithoutClaimingSuccess() {
        val paths = Paths()
        try {
            val failingDelete = object : AudioFileOperations {
                override fun move(source: File, destination: File) {
                    Files.move(source.toPath(), destination.toPath())
                }
                override fun delete(file: File): Boolean = file.delete()
                override fun sync(file: File) = Unit
            }
            val stubborn = AudioFixtures.mp3(File(paths.finals, "audio-word-0123456789abcdef01234567-4.mp3"))
            val guarded = object : AudioFileOperations by failingDelete {
                override fun delete(file: File): Boolean = if (file.name == stubborn.name) false else file.delete()
            }
            val store = DraftAudioFileStore(paths.finals, paths.staging, guarded, AudioOwnershipRegistry())
            val report = store.sweep(emptyList())
            assertTrue(report.failures.contains(stubborn.name))
            assertFalse(report.deleted.contains(stubborn.name))
            assertTrue("failed deletion leaves the file in place", stubborn.isFile)
        } finally {
            paths.close()
        }
    }

    @Test
    fun ownershipRegistryCountsNestedLeasesPerOwner() {
        val root = kotlin.io.path.createTempDirectory("audio-owned").toFile()
        try {
            val file = AudioFixtures.wav(File(root, "a.wav"))
            val registry = AudioOwnershipRegistry()
            assertFalse(registry.isLeased(file))
            val first = registry.acquire(file, AudioFileOwner.PLAYBACK)
            val second = registry.acquire(file, AudioFileOwner.PLAYBACK)
            val import = registry.acquire(file, AudioFileOwner.IMPORT)
            assertTrue(registry.isLeased(file))
            assertEquals(setOf(AudioFileOwner.PLAYBACK, AudioFileOwner.IMPORT), registry.ownersOf(file))
            first.close()
            assertTrue(registry.isLeased(file))
            second.close()
            import.close()
            assertFalse(registry.isLeased(file))
            first.close()
            assertFalse(registry.isLeased(file))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun stagingMustStayOutsideTheExportedDraftDirectory() {
        val paths = Paths()
        try {
            var rejected = false
            try {
                DraftAudioFileStore(paths.finals, File(paths.finals, "staging"))
            } catch (_: IllegalArgumentException) {
                rejected = true
            }
            assertTrue("staging inside filesDir/draft would be exported by FileProvider", rejected)
        } finally {
            paths.close()
        }
    }
}
