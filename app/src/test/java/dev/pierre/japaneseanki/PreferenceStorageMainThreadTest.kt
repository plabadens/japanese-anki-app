package dev.pierre.japaneseanki

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * `clearDraft()` is invoked from composition — a `LaunchedEffect` when a submission completes, and
 * from discard click handlers — all of which run on the main thread. It used to bottom out in
 * `persistBlocking`, whose main-thread guard throws, so the app died at launch whenever a completed
 * submission was recorded. The in-memory storage used elsewhere has no such guard, which is why the
 * suite stayed green.
 */
@RunWith(RobolectricTestRunner::class)
class PreferenceStorageMainThreadTest {

    @Test
    fun clearDraftFromTheMainThreadDoesNotThrowAndStillClears() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = AndroidPreferenceStorage(context)
        storage.update(PreferenceFile.DRAFT, PreferenceChanges().putString("sentence", "日本語"))
        assertEquals("日本語", storage.draft.value.getString("sentence", null))

        val store = PreferenceFrozenSubmissionStore(storage, File(context.filesDir, "draft"))

        // Runs on the JVM main thread exactly as the LaunchedEffect / click handler does.
        store.clearDraft()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            "A draft clear requested from the main thread must not leave the draft behind",
            null,
            storage.draft.value.getString("sentence", null),
        )
    }
}
