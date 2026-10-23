package dev.pierre.japaneseanki

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files

/**
 * Robolectric/native-graphics contract for the WebP export path: encoder/decoder round trip,
 * descriptor accounting, and a deterministic encoder-write failure. This is not device acceptance.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [37], qualifiers = "w390dp-h844dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageExportInstrumentedTest {
    private lateinit var root: File

    @Before
    fun createWorkspace() {
        root = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "image-export-${System.nanoTime()}",
        )
        assertTrue("could not create export workspace", root.mkdirs())
    }

    @After
    fun removeWorkspace() {
        root.deleteRecursively()
    }

    @Test
    fun portraitExportDecodesAtTheFitGeometry() {
        assertEquals(720 to 1080, exportAndDecode("portrait", 1440, 2160))
    }

    @Test
    fun landscapeExportDecodesAtTheFitGeometry() {
        assertEquals(1080 to 810, exportAndDecode("landscape", 1920, 1440))
    }

    @Test
    fun smallExportIsNotUpscaled() {
        assertEquals(320 to 240, exportAndDecode("small", 320, 240))
    }

    @Test
    fun repeatedExportsDoNotLeakFileDescriptors() {
        repeat(2) { exportAndDecode("warmup-$it", 400, 300) }
        val baseline = descriptorsInto(root)
        // The counter must see this directory before a zero delta means anything.
        val probe = root.listFiles().orEmpty().first { it.isFile }
        FileInputStream(probe).use {
            assertEquals("descriptor probe did not register", baseline + 1, descriptorsInto(root))
        }
        assertEquals("descriptor probe leaked", baseline, descriptorsInto(root))
        repeat(20) { exportAndDecode("repeat-$it", 800, 600) }
        assertEquals("export leaked descriptors into the export directory", baseline, descriptorsInto(root))
    }

    @Test
    fun failingEncoderWriteKeepsTheDestinationAndCleansUp() {
        val source = sourceImage("failing.png", 640, 480)
        val destination = File(root, "failing.webp")
        val previousExport = "previous export".toByteArray()
        destination.writeBytes(previousExport)
        var streamClosed = false
        var failure: Throwable? = null

        try {
            ImageMediaProcessor.processBlocking(source, destination) { temporary ->
                object : OutputStream() {
                    private val delegate = FileOutputStream(temporary)
                    private var written = 0

                    override fun write(value: Int) {
                        if (written >= 32) throw IOException("disk full")
                        delegate.write(value)
                        written++
                    }

                    override fun write(bytes: ByteArray, offset: Int, length: Int) {
                        val room = 32 - written
                        if (room <= 0) throw IOException("disk full")
                        val chunk = minOf(room, length)
                        delegate.write(bytes, offset, chunk)
                        written += chunk
                        if (chunk < length) throw IOException("disk full")
                    }

                    override fun flush() = delegate.flush()

                    override fun close() {
                        streamClosed = true
                        delegate.close()
                    }
                }
            }
        } catch (thrown: Exception) {
            failure = thrown
        }

        assertNotNull("encoder write failure must not be swallowed", failure)
        assertTrue("encoder stream must be closed when encoding fails", streamClosed)
        assertArrayEquals(
            "a partial encode must never replace the destination",
            previousExport,
            destination.readBytes(),
        )
        assertTrue("temporary export file was left behind", tempFiles().isEmpty())
        assertEquals("descriptor leak after a failed encode", 0, descriptorsInto(root))
    }

    /** Exports one source frame and returns the dimensions of the decoded destination. */
    private fun exportAndDecode(name: String, width: Int, height: Int): Pair<Int, Int> {
        val source = sourceImage("$name.png", width, height)
        val sourceBytes = source.readBytes()
        val destination = File(root, "$name.webp")

        val processed = ImageMediaProcessor.processBlocking(source, destination)
        assertEquals(destination, processed.file)
        assertTrue("export is not a WebP container", isWebp(destination))
        assertArrayEquals("source image must survive export untouched", sourceBytes, source.readBytes())
        assertTrue("temporary export file was left behind", tempFiles().isEmpty())

        val decoded = decode(destination)
        try {
            assertEquals("processed geometry disagrees with the decoded file", processed.width, decoded.width)
            assertEquals("processed geometry disagrees with the decoded file", processed.height, decoded.height)
            return decoded.width to decoded.height
        } finally {
            decoded.recycle()
        }
    }

    private fun sourceImage(name: String, width: Int, height: Int): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            for (x in 0 until width step 16) {
                for (y in 0 until height step 16) {
                    bitmap.setPixel(x, y, 0xFF000000.toInt() or (x * 31 + y * 17))
                }
            }
            val file = File(root, name)
            FileOutputStream(file).use { output ->
                assertTrue("could not encode the source frame", bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
            return file
        } finally {
            bitmap.recycle()
        }
    }

    private fun decode(file: File): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }

    private fun isWebp(file: File): Boolean {
        val header = file.inputStream().use { it.readNBytes(12) }
        return header.size == 12 &&
            String(header, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(header, 8, 4, Charsets.US_ASCII) == "WEBP"
    }

    private fun tempFiles(): List<File> =
        root.listFiles { file -> file.name.startsWith("image-") && file.name.endsWith(".tmp.webp") }
            ?.toList()
            .orEmpty()

    /** Open descriptors pointing into the export directory; a leaked encoder stream shows up here. */
    private fun descriptorsInto(directory: File): Int {
        // /proc reports the canonical mount path, which differs from cacheDir on Android.
        val prefixes = listOf(directory.absolutePath, runCatching { directory.canonicalPath }.getOrDefault(directory.absolutePath))
        val descriptors = File("/proc/self/fd").listFiles() ?: return 0
        return descriptors.count { descriptor ->
            val target = runCatching { Files.readSymbolicLink(descriptor.toPath()).toString() }.getOrNull()
            target != null && prefixes.any { target.startsWith("$it/") }
        }
    }
}
