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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    fun flowThemeProvidesExpressiveDisplayScaleAndSecondaryRole() {
        val displaySize = AtomicReference<TextUnit?>()
        val secondaryContainer = AtomicReference<Color?>()
        val largeShape = AtomicReference<Shape?>()
        composeRule.setContent {
            FlowTheme(darkTheme = false) {
                val size = MaterialTheme.typography.displayMedium.fontSize
                val container = MaterialTheme.colorScheme.secondaryContainer
                val shape = MaterialTheme.shapes.large
                SideEffect {
                    displaySize.set(size)
                    secondaryContainer.set(container)
                    largeShape.set(shape)
                }
            }
        }

        composeRule.runOnIdle {
            assertEquals(44.sp, displaySize.get())
            assertEquals(FlowPalette.lightSecondaryContainer, secondaryContainer.get())
            assertEquals(RoundedCornerShape(26.dp), largeShape.get())
        }
    }

    @Test
    fun flowColorSchemesProvideWarmSurfaceContainerAndInverseRoles() {
        fun roles(colors: androidx.compose.material3.ColorScheme) = listOf(
            colors.surfaceDim,
            colors.surfaceBright,
            colors.surfaceContainerLowest,
            colors.surfaceContainerLow,
            colors.surfaceContainer,
            colors.surfaceContainerHigh,
            colors.surfaceContainerHighest,
            colors.inverseSurface,
            colors.inverseOnSurface,
            colors.inversePrimary,
            colors.surfaceTint,
        )

        assertEquals(
            listOf(
                Color(0xFFE8D9D1),
                Color(0xFFFFFDFC),
                Color(0xFFFFFFFF),
                Color(0xFFFFF3EE),
                Color(0xFFF8ECE6),
                Color(0xFFF2E5DE),
                Color(0xFFEDE0D9),
                Color(0xFF35241E),
                Color(0xFFFFF8F5),
                Color(0xFFFFB4A3),
                Color(0xFF9C3329),
            ),
            roles(FlowLightColors),
        )
        assertEquals(
            listOf(
                Color(0xFF1C1411),
                Color(0xFF3A2C26),
                Color(0xFF160F0D),
                Color(0xFF211815),
                Color(0xFF251B17),
                Color(0xFF2B201B),
                Color(0xFF31251F),
                Color(0xFFFFF8F5),
                Color(0xFF2B1712),
                Color(0xFF9C3329),
                Color(0xFFFFB4A3),
            ),
            roles(FlowDarkColors),
        )
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
