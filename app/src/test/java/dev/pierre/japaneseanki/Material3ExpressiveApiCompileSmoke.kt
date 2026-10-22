package dev.pierre.japaneseanki

import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable

/**
 * Compile-only tripwire for the required Material 3 Expressive APIs.
 * It is deliberately not invoked at runtime; test compilation must fail if an API disappears.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun material3ExpressiveApiCompileSmoke() {
    val standardMotion = MotionScheme.standard()
    val expressiveMotion = MotionScheme.expressive()

    MaterialExpressiveTheme(motionScheme = standardMotion) {}
    MaterialExpressiveTheme(motionScheme = expressiveMotion) {
        ButtonGroup(overflowIndicator = {}) {}
    }
}
