package dev.pierre.japaneseanki

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Nhk2016PronunciationSourceTest {
    @Test fun downloadsPinnedIndexAndFirstExactSpellingRecordingOnDemand() {
        val root = Files.createTempDirectory("nhk-source-test").toFile()
        try {
            val urls = mutableListOf<String>()
            val index = """{"meta":{"version":2},"headwords":{"猫":["20170616125901.ogg","20170616125902.ogg"],"犬":["20170616125903.ogg"]}}""".toByteArray()
            val source = Nhk2016PronunciationSource(File(root, "index"), { "猫" }) { url ->
                urls += url.toString()
                FakeConnection(url, if (url.path.endsWith("index.json")) index else validOgg())
            }
            val output = File(root, "draft/word.ogg")
            val request = AudioSynthesisRequest(AudioItem.VOCABULARY, "cat", 0, "entry")
            assertEquals(output, source.synthesize(request, output))
            assertTrue(urls[0].contains(Nhk2016PronunciationSource.SOURCE_VERSION))
            assertTrue(urls[1].endsWith("/media/20170616125901.ogg"))
            assertTrue(AudioFileValidator.requireSupported(output).isFile)
            assertEquals("GPL-3.0 (index repository only; recording rights are not verified)", Nhk2016PronunciationSource.SOURCE_LICENSE)
        } finally { root.deleteRecursively() }
    }

    @Test fun unresolvedEntryAndMissingExactSpellingDoNotDownloadRecordings() {
        val root = Files.createTempDirectory("nhk-source-filter").toFile()
        try {
            val urls = mutableListOf<String>()
            val source = Nhk2016PronunciationSource(File(root, "index"), { "ねこ" }) { url ->
                urls += url.toString()
                FakeConnection(url, """{"headwords":{"猫":["20170616125901.ogg"]}}""".toByteArray())
            }
            assertTrue(runCatching { source.synthesize(AudioSynthesisRequest(AudioItem.VOCABULARY, "cat", 0), File(root, "x.ogg")) }.isFailure)
            assertEquals(0, urls.size)
            val missing = runCatching {
                source.synthesize(AudioSynthesisRequest(AudioItem.VOCABULARY, "cat", 0, "entry"), File(root, "x.ogg"))
            }.exceptionOrNull()
            assertTrue(missing is AudioSourceMissingException)
            assertEquals(1, urls.size) // index only; reading is not falsely treated as a headword match
            assertFalse(urls.any { it.contains("/media/") })
        } finally { root.deleteRecursively() }
    }

    @Test fun completedOggRecordingCanBeCachedAndCopiedToDraftWithMatchingExtension() {
        val root = Files.createTempDirectory("nhk-ogg-cache").toFile()
        try {
            val cache = ReusableAudioCache(File(root, "cache"))
            val key = AudioCacheKey("NHK-2016", "猫", "recording", 1f, entryIdentity = "entry")
            val media = File(root, "recording.ogg").also { it.writeBytes(validOgg()) }
            assertTrue(cache.put(key, media)?.extension == "ogg")
            val draft = cache.copyToActiveDraft(key, File(root, "draft"), "vocabulary.ogg")
            assertEquals("ogg", draft.extension)
            assertTrue(AudioFileValidator.requireSupported(draft).isFile)
            assertTrue(runCatching { cache.copyToActiveDraft(key, File(root, "other"), "vocabulary.wav") }.isFailure)
        } finally { root.deleteRecursively() }
    }

    @Test fun rejectsMalformedOggPagesAndMalformedIndex() {
        val root = Files.createTempDirectory("nhk-source-invalid").toFile()
        try {
            val indexFile = File(root, "index/nhk-2016-b0207d240f0f.json")
            indexFile.parentFile?.mkdirs()
            indexFile.writeText("{bad")
            val source = Nhk2016PronunciationSource(File(root, "index"), { "猫" }) { url ->
                FakeConnection(url, if (url.path.endsWith("index.json")) "{bad".toByteArray() else byteArrayOf())
            }
            assertTrue(runCatching { source.synthesize(AudioSynthesisRequest(AudioItem.VOCABULARY, "cat", 0, "entry"), File(root, "x.ogg")) }.isFailure)
            assertTrue(runCatching { AudioFileValidator.requireSupported(File(root, "bad.ogg").also { it.writeBytes("OggS".toByteArray()) }) }.isFailure)
        } finally { root.deleteRecursively() }
    }

    @Test fun indexDownloadReportsDeterminateProgressAndClearsWhenFinished() {
        val root = Files.createTempDirectory("nhk-source-progress").toFile()
        try {
            val index = indexJsonWithHeadwords(8_000).toByteArray()
            val progress = mutableListOf<NhkIndexProgress?>()
            val source = Nhk2016PronunciationSource(
                File(root, "index"),
                { "猫" },
                connectionFactory = { url -> FakeConnection(url, if (url.path.endsWith("index.json")) index else validOgg()) },
                onIndexProgress = { progress += it },
            )

            source.synthesize(AudioSynthesisRequest(AudioItem.VOCABULARY, "cat", 0, "entry"), File(root, "word.ogg"))

            val downloading = progress.filterNotNull()
            assertTrue(progress.last() == null) // nothing in flight once the index is committed
            assertEquals(0L, downloading.first().receivedBytes)
            assertEquals(index.size.toLong(), downloading.first().totalBytes)
            assertTrue(downloading.first().determinate)
            assertEquals(index.size.toLong(), downloading.last().receivedBytes)
            assertTrue(downloading.zipWithNext().all { (a, b) -> b.receivedBytes >= a.receivedBytes })
            assertTrue(downloading.last().describe().contains("MB"))
        } finally { root.deleteRecursively() }
    }

    @Test fun progressWithoutContentLengthIsIndeterminateAndStillReportsBytes() {
        val root = Files.createTempDirectory("nhk-source-indeterminate").toFile()
        try {
            val index = indexJsonWithHeadwords(200).toByteArray()
            val progress = mutableListOf<NhkIndexProgress?>()
            val source = Nhk2016PronunciationSource(
                File(root, "index"),
                { "猫" },
                connectionFactory = { url -> FakeConnection(url, if (url.path.endsWith("index.json")) index else validOgg(), contentLength = false) },
                onIndexProgress = { progress += it },
            )

            source.synthesize(AudioSynthesisRequest(AudioItem.VOCABULARY, "cat", 0, "entry"), File(root, "word.ogg"))

            val downloading = progress.filterNotNull()
            assertTrue(downloading.isNotEmpty())
            assertFalse(downloading.first().determinate)
            assertTrue(downloading.last().receivedBytes > 0)
            assertTrue(downloading.last().describe().contains("MB"))
        } finally { root.deleteRecursively() }
    }

    @Test fun cancellingTheIndexDownloadStopsItAndLeavesNoIndexBehind() {
        val root = Files.createTempDirectory("nhk-source-cancel").toFile()
        try {
            val index = indexJsonWithHeadwords(8_000).toByteArray()
            var received = 0L
            val source = Nhk2016PronunciationSource(
                File(root, "index"),
                { "猫" },
                connectionFactory = { url -> FakeConnection(url, if (url.path.endsWith("index.json")) index else validOgg()) },
                onIndexProgress = { progress -> progress?.let { received = it.receivedBytes } },
                isIndexFetchCancelled = { received > 0 },
            )

            val failure = runCatching {
                source.synthesize(AudioSynthesisRequest(AudioItem.VOCABULARY, "cat", 0, "entry"), File(root, "word.ogg"))
            }.exceptionOrNull()

            assertTrue(failure is java.io.IOException)
            assertTrue(failure!!.message.orEmpty().contains("cancelled", ignoreCase = true))
            assertFalse(File(root, "index/nhk-2016-b0207d240f0f.json").exists())
            assertFalse(File(root, "word.ogg").exists())
        } finally { root.deleteRecursively() }
    }

    @Test fun twoConcurrentRequestsShareOneIndexDownload() {
        val root = Files.createTempDirectory("nhk-source-dedupe").toFile()
        try {
            val index = indexJsonWithHeadwords(200).toByteArray()
            var indexRequests = 0
            val failures = java.util.Collections.synchronizedList(mutableListOf<Throwable>())
            val source = Nhk2016PronunciationSource(
                File(root, "index"),
                { "猫" },
                connectionFactory = { url ->
                    if (url.path.endsWith("index.json")) {
                        synchronized(failures) { indexRequests++ }
                        FakeConnection(url, index, delayMillis = 300)
                    } else {
                        FakeConnection(url, validOgg())
                    }
                },
            )

            val threads = (1..2).map { index ->
                Thread {
                    runCatching {
                        source.synthesize(
                            AudioSynthesisRequest(AudioItem.VOCABULARY, "cat", 0, "entry"),
                            File(root, "word-$index.ogg"),
                        )
                    }.exceptionOrNull()?.let(failures::add)
                }.also { it.start() }
            }
            threads.forEach { it.join(20_000) }

            assertTrue(failures.toString(), failures.isEmpty())
            assertEquals(1, indexRequests)
            assertTrue(File(root, "index/nhk-2016-b0207d240f0f.json").isFile)
        } finally { root.deleteRecursively() }
    }

    @Test fun freshDownloadReadsTheIndexOnceAndAnswersFromThatScan() {
        val root = Files.createTempDirectory("nhk-source-single-pass").toFile()
        try {
            val index = """{"headwords":{"犬":["20170616125903.ogg"],"猫":["20170616125901.ogg"]}}""".toByteArray()
            var indexRequests = 0
            val reads = mutableListOf<String>()
            val source = Nhk2016PronunciationSource(
                File(root, "index"),
                { it.text },
                readIndexText = { file -> reads += file.name; file.readText(Charsets.UTF_8) },
                connectionFactory = { url ->
                    if (url.path.endsWith("index.json")) {
                        indexRequests++
                        FakeConnection(url, index)
                    } else {
                        FakeConnection(url, validOgg())
                    }
                },
            )

            source.synthesize(AudioSynthesisRequest(AudioItem.VOCABULARY, "猫", 0, "entry"), File(root, "cat.ogg"))

            // The fresh download answers the lookup inside its own validation pass: one download,
            // one read (of the temporary file), and no re-read of the index just published.
            assertEquals(1, indexRequests)
            assertEquals(1, reads.size)
            assertTrue(reads.single().endsWith(".json"))
            assertTrue(File(root, "index/nhk-2016-b0207d240f0f.json").isFile)

            source.synthesize(AudioSynthesisRequest(AudioItem.VOCABULARY, "犬", 0, "entry"), File(root, "dog.ogg"))

            // Later lookups still scan the published index once, with no new download.
            assertEquals(1, indexRequests)
            assertEquals(listOf("nhk-2016-b0207d240f0f.json"), reads.drop(1))
        } finally { root.deleteRecursively() }
    }

    @Test fun freshDownloadWithMalformedLaterContentPublishesNothing() {
        val root = Files.createTempDirectory("nhk-source-late-malformed").toFile()
        try {
            val documents = listOf(
                """{"headwords":{"猫":["20170616125901.ogg"],"犬":["20170616125902.ogg"]""",
                """{"headwords":{"猫":["20170616125901.ogg"],"犬":["20170616125902.mp3"]}}""",
            )
            documents.forEach { document ->
                val source = Nhk2016PronunciationSource(File(root, "index"), { it.text }) { url ->
                    FakeConnection(url, if (url.path.endsWith("index.json")) document.toByteArray() else validOgg())
                }
                val failure = runCatching {
                    source.synthesize(
                        AudioSynthesisRequest(AudioItem.VOCABULARY, "猫", 0, "entry"),
                        File(root, "cat.ogg"),
                    )
                }
                assertTrue(failure.isFailure)
                assertFalse(File(root, "index/nhk-2016-b0207d240f0f.json").exists())
                assertFalse(File(root, "cat.ogg").exists())
            }
        } finally { root.deleteRecursively() }
    }

    @Test fun oversizedIndexAbortsBeforePublication() {
        val root = Files.createTempDirectory("nhk-source-oversized").toFile()
        try {
            // One byte over the 64 MB ceiling, streamed without allocating the document.
            val oversizedBytes = 64L * 1024 * 1024 + 1
            val source = Nhk2016PronunciationSource(File(root, "index"), { "猫" }) { url ->
                if (url.path.endsWith("index.json")) RepeatingConnection(url, oversizedBytes)
                else FakeConnection(url, validOgg())
            }
            val failure = runCatching {
                source.synthesize(AudioSynthesisRequest(AudioItem.VOCABULARY, "猫", 0, "entry"), File(root, "cat.ogg"))
            }
            assertTrue(failure.isFailure)
            assertFalse(File(root, "index/nhk-2016-b0207d240f0f.json").exists())
            assertFalse(File(root, "cat.ogg").exists())
            // The oversized download is not left behind as a partial index or scratch file.
            assertEquals(emptyList<String>(), leftovers(root))
        } finally { root.deleteRecursively() }
    }

    @Test fun failedPublicationLeavesNoIndexAndNoRecording() {
        val root = Files.createTempDirectory("nhk-source-publish-failure").toFile()
        try {
            // A directory at the destination path makes the atomic move fail after validation.
            val blocker = File(root, "index/nhk-2016-b0207d240f0f.json")
            blocker.mkdirs()
            File(blocker, "keep").writeText("not an index")
            val index = indexJsonWithHeadwords(2).toByteArray()
            val source = Nhk2016PronunciationSource(File(root, "index"), { "猫" }) { url ->
                FakeConnection(url, if (url.path.endsWith("index.json")) index else validOgg())
            }
            val failure = runCatching {
                source.synthesize(AudioSynthesisRequest(AudioItem.VOCABULARY, "猫", 0, "entry"), File(root, "cat.ogg"))
            }
            assertTrue(failure.isFailure)
            assertTrue(blocker.isDirectory)
            assertTrue(File(blocker, "keep").isFile)
            assertFalse(File(root, "cat.ogg").exists())
            assertEquals(emptyList<String>(), leftovers(root))
        } finally { root.deleteRecursively() }
    }

    /** Scratch files (`.{uuid}.json` downloads) that a failed attempt should never leave behind. */
    private fun leftovers(root: File): List<String> =
        File(root, "index").listFiles()?.filter { it.name.startsWith(".") }?.map { it.name }.orEmpty()

    private fun indexJsonWithHeadwords(count: Int): String = buildString {
        append("{\"meta\":{\"version\":2},\"headwords\":{")
        repeat(count) { append("\"dummy$it\":[\"20170616125901.ogg\"],") }
        append("\"猫\":[\"20170616125901.ogg\"]}}")
    }

    private fun validOgg(): ByteArray {
        val page = ByteArrayOutputStream()
        page.write("OggS".toByteArray())
        page.write(0) // stream structure version
        page.write(ByteArray(21)) // page header before segment count
        page.write(1) // one lacing value
        page.write(1) // one-byte page body
        page.write(0) // page body
        return page.toByteArray()
    }

    /** Emits [length] filler bytes without materialising the document in memory. */
    private class RepeatingConnection(url: URL, private val length: Long) : HttpURLConnection(url) {
        override fun getResponseCode() = HTTP_OK
        override fun getContentLengthLong() = length
        override fun getInputStream(): InputStream = object : InputStream() {
            private var remaining = length
            override fun read(): Int = if (remaining-- > 0) ' '.code else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (remaining <= 0) return -1
                val count = minOf(len.toLong(), remaining).toInt()
                b.fill(' '.code.toByte(), off, off + count)
                remaining -= count
                return count
            }
        }
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
    }

    private class FakeConnection(
        url: URL,
        private val body: ByteArray,
        private val contentLength: Boolean = true,
        private val delayMillis: Long = 0,
    ) : HttpURLConnection(url) {
        override fun getResponseCode(): Int {
            if (delayMillis > 0) Thread.sleep(delayMillis)
            return HTTP_OK
        }
        override fun getInputStream(): InputStream = ByteArrayInputStream(body)
        override fun getContentLengthLong() = if (contentLength) body.size.toLong() else -1L
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
    }
}
