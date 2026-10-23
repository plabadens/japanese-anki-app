package dev.pierre.japaneseanki

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies the shared Robolectric resource defaults without class-specific @Config overrides. */
@RunWith(AndroidJUnit4::class)
class RobolectricSetupTest {
    @Test
    fun sharedSdkQualifiersAndApplicationResourcesAreAvailable() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertEquals(37, Build.VERSION.SDK_INT)
        assertEquals(390, context.resources.configuration.screenWidthDp)
        assertEquals(844, context.resources.configuration.screenHeightDp)
        assertEquals(480, context.resources.displayMetrics.densityDpi)
        assertNotNull(context.getString(R.string.app_name))
    }
}
