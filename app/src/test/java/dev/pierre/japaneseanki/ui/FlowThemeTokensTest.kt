package dev.pierre.japaneseanki.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [37], qualifiers = "w390dp-h844dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FlowThemeTokensTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun flowThemeProvidesExpressiveSmallShapeAndTouchTargetToken() {
        val smallShape = AtomicReference<Shape?>()
        val touchTarget = AtomicReference<Dp?>()
        val quietOutline = AtomicReference<Color?>()
        composeRule.setContent {
            FlowTheme(darkTheme = false) {
                val shape = MaterialTheme.shapes.small
                val target = MaterialTheme.flow.touchTarget
                val outline = MaterialTheme.flow.quietOutline
                SideEffect {
                    smallShape.set(shape)
                    touchTarget.set(target)
                    quietOutline.set(outline)
                }
            }
        }

        composeRule.runOnIdle {
            assertEquals(RoundedCornerShape(14.dp), smallShape.get())
            assertEquals(48.dp, touchTarget.get())
            assertEquals(FlowLightColors.outline.copy(alpha = 0.5f), quietOutline.get())
        }
    }

    @Test
    fun settingsPickerRowExposesRadioRoleAndFortyEightDpTarget() {
        composeRule.setContent {
            SettingsPreviewContent(state = SettingsPreviewState.KEY_SAVED)
        }

        composeRule.onNodeWithText("Japanese Sentence").performClick()
        val row = composeRule.onNodeWithText("Japanese Sentence Alternate").fetchSemanticsNode()
        assertEquals(Role.RadioButton, row.config[SemanticsProperties.Role])
        assertTrue(
            "Settings picker rows must be at least 48 dp high",
            row.boundsInRoot.height >= with(composeRule.density) { 48.dp.toPx() },
        )
    }
}
