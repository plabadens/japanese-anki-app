package dev.pierre.japaneseanki

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PreferenceStorageInitializationTest {
    @Test fun sharedPreferencesMigrationPreservesTypedValuesAndCiphertext() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val legacy = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        legacy.edit().putLong("deck_id", 72).putBoolean("onboarding_complete", true)
            .putString(GoogleCloudCredential.PREF, "synthetic-ciphertext-not-plaintext").commit()
        DataStorePreferenceStorage(context).use { storage ->
            runSuspendTest { storage.load() }
            assertEquals(72L, storage.settings.value.getLong("deck_id", -1))
            assertTrue(storage.settings.value.getBoolean("onboarding_complete", false))
            assertEquals("synthetic-ciphertext-not-plaintext", storage.settings.value.getString(GoogleCloudCredential.PREF, null))
            assertFalse("Migration retires the old preferences", legacy.contains("deck_id"))
        }
    }

    @Test fun repeatedAndConcurrentLoadsDoNotReplaceCommittedEdits() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        DataStorePreferenceStorage(context).use { storage ->
            runSuspendTest {
                storage.load()
                assertTrue(storage.persist(PreferenceFile.DRAFT, PreferenceChanges().putString("sentence", "keep me")))
                coroutineScope { repeat(3) { launch { storage.load() } } }
                assertEquals("keep me", storage.draft.first { it.getString("sentence", null) == "keep me" }.getString("sentence", null))
            }
        }
    }
}
