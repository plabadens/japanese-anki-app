package dev.pierre.japaneseanki.ui

import androidx.compose.animation.core.snap
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class FlowMotionTest {
    @Test
    fun motionRolesUseTheMatchingMaterialSchemeSpec() {
        val scheme = MotionScheme.expressive()
        val motion = FlowMotion(scheme, animated = true)

        assertEquals(scheme.fastEffectsSpec<Float>(), motion.colour<Float>())
        assertEquals(scheme.fastSpatialSpec<Float>(), motion.smallSpatial<Float>())
        assertEquals(scheme.defaultSpatialSpec<Float>(), motion.containerSpatial<Float>())
    }

    @Test
    fun zeroDurationScaleSnapsEveryRoleAndHalfScaleStillAnimates() {
        val scheme = MotionScheme.expressive()
        val motion = FlowMotion(scheme, animated = false)

        assertEquals(snap<Float>(), motion.colour<Float>())
        assertEquals(snap<Float>(), motion.smallSpatial<Float>())
        assertEquals(snap<Float>(), motion.containerSpatial<Float>())
        assertTrue(MotionPolicy(0.5f, scheme).animated)
        assertEquals(false, MotionPolicy(0f, scheme).animated)
    }
}
