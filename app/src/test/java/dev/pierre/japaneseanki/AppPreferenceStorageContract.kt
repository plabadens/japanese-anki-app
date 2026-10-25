package dev.pierre.japaneseanki

import android.os.Looper
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Assertions every AppPreferenceStorage implementation must satisfy. */
internal abstract class AppPreferenceStorageContract {
    protected abstract fun createStorage(): AppPreferenceStorage
    protected abstract fun createFailingCommitStorage(): AppPreferenceStorage
    protected abstract fun resetPersistentStorage()

    private lateinit var storage: AppPreferenceStorage

    @Before
    fun setUpStorage() {
        resetPersistentStorage()
        storage = createStorage()
        runSuspendTest { storage.load() }
    }

    @Test
    fun updateIsImmediatelyVisibleThroughDraftAndSettings() {
        storage.update(PreferenceFile.DRAFT, PreferenceChanges().putString("sentence", "日本語"))
        storage.update(PreferenceFile.SETTINGS, PreferenceChanges().putBoolean("enabled", true))

        assertEquals("日本語", storage.draft.value.getString("sentence", null))
        assertTrue(storage.settings.value.getBoolean("enabled", false))
    }

    @Test
    fun persistBlockingRejectsMainThread() {
        assertEquals(Looper.getMainLooper(), Looper.myLooper())
        assertThrows(IllegalStateException::class.java) {
            storage.persistBlocking(PreferenceFile.DRAFT, PreferenceChanges().putString("sentence", "日本語"))
        }
    }

    @Test
    fun persistBlockingSucceedsOnBackgroundThread() {
        val changes = PreferenceChanges().putString("sentence", "日本語")

        assertTrue(onBackgroundThread { storage.persistBlocking(PreferenceFile.DRAFT, changes) })
        assertEquals("日本語", storage.draft.value.getString("sentence", null))
    }

    @Test
    fun persistBlockingReturnsFalseWhenCommitFails() {
        val failingStorage = createFailingCommitStorage()
        runSuspendTest { failingStorage.load() }

        assertFalse(
            onBackgroundThread {
                failingStorage.persistBlocking(
                    PreferenceFile.DRAFT,
                    PreferenceChanges().putString("sentence", "not durable"),
                )
            },
        )
    }

    @Test
    fun sequentialBlockingPersistsDoNotDeadlockAndPreserveOrder() {
        val executor = Executors.newSingleThreadExecutor()
        try {
            repeat(12) { index ->
                val value = "write-$index"
                val saved = executor.submit(Callable {
                    storage.persistBlocking(PreferenceFile.DRAFT, PreferenceChanges().putString("order", value))
                }).get(10, TimeUnit.SECONDS)
                assertTrue("persistBlocking failed at $index", saved)
                assertEquals(value, storage.draft.value.getString("order", null))
            }
        } finally {
            executor.shutdownNow()
        }

        val restarted = createStorage()
        runSuspendTest { restarted.load() }
        assertEquals("write-11", restarted.draft.value.getString("order", null))
    }

    @Test
    fun loadReadsValuesWrittenBeforeStorageWasRecreated() {
        assertTrue(
            onBackgroundThread {
                storage.persistBlocking(
                    PreferenceFile.DRAFT,
                    PreferenceChanges().putString("sentence", "saved before restart"),
                )
            },
        )
        assertTrue(
            onBackgroundThread {
                storage.persistBlocking(
                    PreferenceFile.SETTINGS,
                    PreferenceChanges().putString("last_note_tags", "review later"),
                )
            },
        )

        val restarted = createStorage()
        runSuspendTest { restarted.load() }
        assertEquals("saved before restart", restarted.draft.value.getString("sentence", null))
        assertEquals("review later", restarted.settings.value.getString("last_note_tags", null))
    }

    @Test
    fun clearDraftOnMainThreadIsDurable() {
        assertEquals(Looper.getMainLooper(), Looper.myLooper())
        storage.update(PreferenceFile.DRAFT, PreferenceChanges().putString("sentence", "clear me"))
        val store = PreferenceFrozenSubmissionStore(storage, File("unused-contract-draft-directory"))

        // Mirrors the composition/click-handler call site: clearDraft itself must not block here.
        store.clearDraft()
        assertNull(storage.draft.value.getString("sentence", null))

        // A later awaited write is a queue barrier for Android's asynchronous apply from clearDraft.
        assertTrue(
            runSuspendTest {
                storage.persist(PreferenceFile.DRAFT, PreferenceChanges().putBoolean("contract_barrier", true))
            },
        )
        val restarted = createStorage()
        runSuspendTest { restarted.load() }
        assertNull(restarted.draft.value.getString("sentence", null))
        assertTrue(restarted.draft.value.getBoolean("contract_barrier", false))
    }

    private fun <T> onBackgroundThread(block: () -> T): T {
        val executor = Executors.newSingleThreadExecutor()
        try {
            return executor.submit(Callable(block)).get(10, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
        }
    }
}
