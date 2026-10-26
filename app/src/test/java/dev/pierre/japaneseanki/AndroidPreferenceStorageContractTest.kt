package dev.pierre.japaneseanki

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class AndroidPreferenceStorageContractTest : AppPreferenceStorageContract() {
    private val root = kotlin.io.path.createTempDirectory("datastore-contract").toFile()
    private val readOnlyDirectories = mutableListOf<File>()
    private fun context(directory: File): Context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = directory.apply { mkdirs() }
    }
    override fun createStorage(): AppPreferenceStorage = DataStorePreferenceStorage(context(root))
    override fun createFailingCommitStorage(): AppPreferenceStorage {
        val directory = File(root, "write-failure")
        val native = DataStorePreferenceStorage(context(directory))
        return object : AppPreferenceStorage by native, AutoCloseable {
            override suspend fun load() {
                native.load()
                val files = File(directory, "datastore").apply { mkdirs() }
                check(files.setWritable(false, false))
                readOnlyDirectories += files
            }
            override fun close() { native.close() }
        }
    }
    override fun resetPersistentStorage() { root.mkdirs() }
    @After fun cleanupFiles() { readOnlyDirectories.forEach { it.setWritable(true, true) }; root.deleteRecursively() }
}
