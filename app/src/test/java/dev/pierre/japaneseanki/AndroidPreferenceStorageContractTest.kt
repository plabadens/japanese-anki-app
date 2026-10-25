package dev.pierre.japaneseanki

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class AndroidPreferenceStorageContractTest : AppPreferenceStorageContract() {
    private fun appContext(): Context = ApplicationProvider.getApplicationContext()

    override fun createStorage(): AppPreferenceStorage = AndroidPreferenceStorage(appContext())

    override fun createFailingCommitStorage(): AppPreferenceStorage =
        AndroidPreferenceStorage(CommitFailingContext(appContext()))

    override fun resetPersistentStorage() {
        listOf("draft", "settings").forEach { name ->
            check(appContext().getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit())
        }
    }

    /** Keeps Robolectric's real preferences, but models an Editor.commit() failure. */
    private class CommitFailingContext(base: Context) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this

        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val delegate = baseContext.getSharedPreferences(name, mode)
            return object : SharedPreferences by delegate {
                override fun edit(): SharedPreferences.Editor {
                    val editor = delegate.edit()
                    return object : SharedPreferences.Editor by editor {
                        override fun commit(): Boolean = false
                    }
                }
            }
        }
    }
}
