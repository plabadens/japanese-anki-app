package dev.pierre.japaneseanki

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PreferenceStorageMainThreadTest {
    @Test fun repositoryClearCanBeRequestedOnMainWithoutDoingIoOnMain() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        DataStorePreferenceStorage(context).use { storage ->
            runSuspendTest {
                storage.load()
                storage.persist(PreferenceFile.DRAFT, PreferenceChanges().putString("sentence", "日本語"))
                val repository = DraftRepository(context, storage)
                repository.restore()
                repository.clear("")
                assertNull(withContext(Dispatchers.IO) { storage.readBlocking(PreferenceFile.DRAFT).getString("sentence", null) })
                repository.close()
            }
        }
    }

    @Test fun failedClearDoesNotDeleteAReferencedImage() {
        val root = kotlin.io.path.createTempDirectory("failed-clear").toFile()
        val image = File(root, "source.image").apply { writeText("image fixture") }
        val storage = MemoryAppPreferenceStorage(
            initialDraft = PreferenceValues(mapOf("note_image_path" to image.path)), failCommits = true,
        )
        runSuspendTest { storage.load() }
        assertThrows(IllegalStateException::class.java) {
            runSuspendTest { withContext(Dispatchers.IO) { clearPersistedDraftBlocking(storage, root) } }
        }
        assertTrue(image.exists())
        assertEquals(image.path, storage.durable(PreferenceFile.DRAFT).getString("note_image_path", null))
        root.deleteRecursively()
    }
}
