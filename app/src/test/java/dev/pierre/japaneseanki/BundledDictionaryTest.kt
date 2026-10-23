package dev.pierre.japaneseanki

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [37])
class BundledDictionaryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun assetContainsExactlyTheRuntimeFilesAtTheirPinnedSizes() {
        val entries = mutableMapOf<String, Long>()
        context.assets.open(BundledDictionary.ASSET_NAME).use { raw ->
            ZipInputStream(raw).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    assertFalse("Directories are not part of the asset", entry.isDirectory)
                    entries[entry.name] = entry.size
                }
            }
        }

        val expected = BundledDictionary.FILE_SIZES.mapKeys { "${BundledDictionary.ASSET_ROOT}/${it.key}" }
        assertEquals(expected, entries)
        assertEquals(259_495_011L, BundledDictionary.TOTAL_BYTES)
    }

    @Test
    fun installUnpacksOnceAndReplacesAnOlderDictionary() {
        val filesDir = temporaryFolder.newFolder("files")
        val destination = BundledDictionary.directory(filesDir)

        // Simulate an install from the retired downloaded 3.1.1 data.
        destination.mkdirs()
        File(destination, "sys.dic").writeBytes(byteArrayOf(1, 2, 3))
        File(destination, "version").writeText("unidic-cwj-3.1.1")
        assertFalse(BundledDictionary.isInstalled(destination))

        BundledDictionary.install(context, filesDir)

        assertTrue(BundledDictionary.isInstalled(destination))
        assertEquals(BundledDictionary.VERSION, File(destination, "version").readText().trim())
        BundledDictionary.FILE_SIZES.forEach { (name, size) ->
            assertEquals("$name size", size, File(destination, name).length())
        }
        assertFalse(File(filesDir, "${BundledDictionary.DIRECTORY}.staging").exists())
        assertFalse(File(filesDir, "${BundledDictionary.DIRECTORY}.previous").exists())

        val markerLastModified = File(destination, "version").lastModified()
        BundledDictionary.install(context, filesDir)
        assertEquals("A second install must be a no-op", markerLastModified, File(destination, "version").lastModified())
    }

    @Test
    fun aDirectoryWithTheWrongMarkerIsNotInstalled() {
        val destination = temporaryFolder.newFolder("wrong-marker")
        BundledDictionary.FILE_SIZES.forEach { (name, size) ->
            java.io.RandomAccessFile(File(destination, name), "rw").use { it.setLength(size) }
        }
        File(destination, "version").writeText("unidic-cwj-3.1.1")

        assertFalse(BundledDictionary.isInstalled(destination))
    }
}
