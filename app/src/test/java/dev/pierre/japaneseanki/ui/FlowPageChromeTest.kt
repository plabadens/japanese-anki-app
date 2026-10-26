package dev.pierre.japaneseanki.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pierre.japaneseanki.R
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
class FlowPageChromeTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun standardAppBarKeepsItsBoundsAndTargetsAcrossBusyRecoveryThemesAndLargeText() {
        val dark = mutableStateOf(false)
        val busy = mutableStateOf(false)
        val width = mutableStateOf(360)
        val scale = mutableStateOf(1f)
        composeRule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, scale.value)) {
                FlowTheme(darkTheme = dark.value) {
                    Box(Modifier.width(width.value.dp).height((if (width.value == 360) 828 else 844).dp)) {
                        FlowPage(topBar = {
                            FlowTopBar(
                                title = "Card",
                                onBack = {},
                                onSettings = {},
                                navigationEnabled = !busy.value,
                                modifier = Modifier.testTag("page-app-bar"),
                            )
                        }) { Box(Modifier.fillMaxSize().testTag("page-body")) }
                    }
                }
            }
        }
        val back = composeRule.activity.getString(R.string.back)
        val settings = composeRule.activity.getString(R.string.settings)
        for (screenWidth in listOf(360, 390)) for (fontScale in listOf(1f, 2f)) for (night in listOf(false, true)) {
            composeRule.runOnIdle { width.value = screenWidth; scale.value = fontScale; dark.value = night; busy.value = false }
            val bar = composeRule.onNodeWithTag("page-app-bar").fetchSemanticsNode().boundsInRoot
            val body = composeRule.onNodeWithTag("page-body").fetchSemanticsNode().boundsInRoot
            val before = composeRule.onNodeWithContentDescription(back).fetchSemanticsNode().boundsInRoot
            assertTrue(bar.height >= with(composeRule.density) { 64.dp.toPx() })
            assertEquals("The body starts immediately below the bar, not after a second top inset", bar.bottom, body.top, 1f)
            assertTrue(before.height >= with(composeRule.density) { 48.dp.toPx() })
            assertTrue(before.width >= with(composeRule.density) { 48.dp.toPx() })
            val layouts = mutableListOf<TextLayoutResult>()
            composeRule.onNodeWithText("Card").fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(layouts)
            assertTrue(layouts.isNotEmpty())
            // Compose may retain a wider paragraph constraint than the measured intrinsic Text.
            // Check the actual glyph bounds, not paragraph allocation, for clipping.
            layouts.forEach { layout ->
                layout.layoutInput.text.indices.forEach { index ->
                    val glyph = layout.getBoundingBox(index)
                    assertTrue("Title clips horizontally at $screenWidth/$fontScale", glyph.left >= 0 && glyph.right <= layout.size.width + 1)
                    assertTrue("Title clips vertically at $screenWidth/$fontScale", glyph.top >= 0 && glyph.bottom <= layout.size.height + 1)
                }
            }
            composeRule.runOnIdle { busy.value = true }
            composeRule.onNodeWithContentDescription(back).assertIsNotEnabled()
            composeRule.onNodeWithContentDescription(settings).assertIsNotEnabled()
            assertEquals(bar, composeRule.onNodeWithTag("page-app-bar").fetchSemanticsNode().boundsInRoot)
            assertEquals(before, composeRule.onNodeWithContentDescription(back).fetchSemanticsNode().boundsInRoot)
            composeRule.runOnIdle { busy.value = false }
            composeRule.onNodeWithContentDescription(back).assertIsEnabled()
            assertEquals(bar, composeRule.onNodeWithTag("page-app-bar").fetchSemanticsNode().boundsInRoot)
        }
    }
}
