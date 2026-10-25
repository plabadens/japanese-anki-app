package dev.pierre.japaneseanki

import android.os.Looper
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The app must reach RESUMED from a cold launch.
 *
 * Every other test composes a screen, a component or a pure function directly; none of them runs
 * [MainActivity.onCreate], so a crash in the real start-up path is invisible to the suite.
 */
@RunWith(RobolectricTestRunner::class)
class MainActivityLaunchTest {

    @Test
    fun coldLaunchReachesResumedWithoutThrowing() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()

        shadowOf(Looper.getMainLooper()).idle()

        val activity = controller.get()
        assertNotNull("MainActivity was not created", activity)
        assertTrue("MainActivity is not finishing right after launch", !activity.isFinishing)
        controller.pause().stop().destroy()
    }
}
