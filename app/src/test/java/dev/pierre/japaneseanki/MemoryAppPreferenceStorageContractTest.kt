package dev.pierre.japaneseanki

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class MemoryAppPreferenceStorageContractTest : AppPreferenceStorageContract() {
    private val backingStore = MemoryPreferenceBackingStore()

    override fun createStorage(): AppPreferenceStorage =
        MemoryAppPreferenceStorage(backingStore = backingStore)

    override fun createFailingCommitStorage(): AppPreferenceStorage =
        MemoryAppPreferenceStorage(backingStore = backingStore, failCommits = true)

    override fun resetPersistentStorage() = backingStore.clear()
}
