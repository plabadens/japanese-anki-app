package dev.pierre.japaneseanki

import android.os.Looper
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Durable data is the contract; immediate optimistic visibility and private queue internals are not. */
internal abstract class AppPreferenceStorageContract {
    protected abstract fun createStorage(): AppPreferenceStorage
    protected abstract fun createFailingCommitStorage(): AppPreferenceStorage
    protected abstract fun resetPersistentStorage()
    private lateinit var storage: AppPreferenceStorage
    private val opened = mutableListOf<AppPreferenceStorage>()
    @Before fun setup() { resetPersistentStorage(); storage = open() }
    @After fun close() { opened.forEach { (it as? AutoCloseable)?.close() } }
    private fun open(): AppPreferenceStorage = createStorage().also { opened += it; runSuspendTest { it.load() } }
    private fun restart(): AppPreferenceStorage {
        (storage as? AutoCloseable)?.close()
        return open()
    }

    @Test fun updatesEventuallyPublishCommittedDraftAndSettings() {
        storage.update(PreferenceFile.DRAFT, PreferenceChanges().putString("sentence", "日本語"))
        storage.update(PreferenceFile.SETTINGS, PreferenceChanges().putBoolean("enabled", true))
        runSuspendTest {
            storage.draft.first { it.getString("sentence", null) == "日本語" }
            storage.settings.first { it.getBoolean("enabled", false) }
        }
        assertEquals("日本語", restart().draft.value.getString("sentence", null))
    }
    @Test fun blockingWritesRejectMainThread() {
        assertEquals(Looper.getMainLooper(), Looper.myLooper())
        assertThrows(IllegalStateException::class.java) { storage.persistBlocking(PreferenceFile.DRAFT, PreferenceChanges().putString("sentence", "日本語")) }
    }
    @Test fun awaitedWriteIsDurableBeforeItReturns() {
        assertTrue(background { storage.persistBlocking(PreferenceFile.DRAFT, PreferenceChanges().putString("sentence", "日本語")) })
        assertEquals("日本語", background { storage.readBlocking(PreferenceFile.DRAFT).getString("sentence", null) })
        assertEquals("日本語", restart().draft.value.getString("sentence", null))
    }
    @Test fun failedWriteIsReportedAsFailure() {
        val failing = createFailingCommitStorage().also { opened += it }
        runSuspendTest { failing.load() }
        assertFalse(background { failing.persistBlocking(PreferenceFile.DRAFT, PreferenceChanges().putString("sentence", "not durable")) })
    }
    @Test fun sequentialWritesPreserveTheLastCommittedValue() {
        repeat(12) { index -> assertTrue(background { storage.persistBlocking(PreferenceFile.DRAFT, PreferenceChanges().putString("order", "write-$index")) }) }
        assertEquals("write-11", restart().draft.value.getString("order", null))
    }
    @Test fun restartRestoresBothStores() {
        runSuspendTest {
            assertTrue(storage.persist(PreferenceFile.DRAFT, PreferenceChanges().putString("sentence", "saved before restart")))
            assertTrue(storage.persist(PreferenceFile.SETTINGS, PreferenceChanges().putString("last_note_tags", "review later")))
        }
        val restored = restart()
        assertEquals("saved before restart", restored.draft.value.getString("sentence", null))
        assertEquals("review later", restored.settings.value.getString("last_note_tags", null))
    }
    @Test fun clearRemovesAllDraftKeysWithoutTouchingSettings() {
        runSuspendTest {
            storage.persist(PreferenceFile.DRAFT, PreferenceChanges().putString("sentence", "clear me").putString("obsolete_key", "also clear me"))
            storage.persist(PreferenceFile.SETTINGS, PreferenceChanges().putString("setting", "keep me"))
            assertTrue(storage.persist(PreferenceFile.DRAFT, draftClearChanges()))
        }
        val restored = restart()
        assertNull(restored.draft.value.getString("sentence", null))
        assertNull(restored.draft.value.getString("obsolete_key", null))
        assertEquals("keep me", restored.settings.value.getString("setting", null))
    }
    private fun <T> background(block: () -> T): T {
        val executor = Executors.newSingleThreadExecutor()
        return try { executor.submit(Callable(block)).get(10, TimeUnit.SECONDS) } finally { executor.shutdownNow() }
    }
}
