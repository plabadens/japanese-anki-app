package dev.pierre.japaneseanki

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class PreferenceStorageInitializationTest {
    @Test
    fun repeatedLoadKeepsQueuedEditsWithoutReadingDiskAgain() = runTest {
        val context = CountingContext(ApplicationProvider.getApplicationContext())
        val storage = AndroidPreferenceStorage(context, StandardTestDispatcher(testScheduler))
        storage.load()
        storage.update(PreferenceFile.DRAFT, PreferenceChanges().putString("pending", "keep me"))
        assertNull(context.getSharedPreferences("draft", Context.MODE_PRIVATE).getString("pending", null))
        val readsAfterInitialization = context.reads

        storage.load()

        assertEquals(readsAfterInitialization, context.reads)
        assertEquals("keep me", storage.draft.value.getString("pending", null))
        assertTrue(storage.persist(PreferenceFile.DRAFT, PreferenceChanges().putBoolean("barrier", true)))
        assertEquals("keep me", context.getSharedPreferences("draft", Context.MODE_PRIVATE).getString("pending", null))
    }

    @Test
    fun concurrentFirstLoadsShareOneInitialization() = runTest {
        val context = CountingContext(ApplicationProvider.getApplicationContext())
        val storage = AndroidPreferenceStorage(context, StandardTestDispatcher(testScheduler))
        val loads = List(3) { launch { storage.load() } }
        runCurrent()
        loads.joinAll()

        assertEquals("Only draft and settings should be read once", 2, context.reads)
    }

    private class CountingContext(base: Context) : ContextWrapper(base) {
        var reads = 0
            private set

        override fun getApplicationContext(): Context = this

        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val preferences = baseContext.getSharedPreferences(name, mode)
            return object : SharedPreferences by preferences {
                override fun getAll(): MutableMap<String, *> {
                    reads++
                    return preferences.all
                }
            }
        }
    }
}
