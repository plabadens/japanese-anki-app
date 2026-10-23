package dev.pierre.japaneseanki.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders each contract state at both themes and both text scales for this viewport. */
abstract class CardFlowPreviewCatalogueTestBase(
    private val widthDp: Int,
    private val heightDp: Int,
) {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun catalogueContainsOnlyTheFourteenContractStates() {
        assertEquals(14, CardFlowPreviewState.values().size)
    }

    @Test
    fun renderFontScale100AtLightAndDark() = renderAtFontScale(1f)

    @Test
    fun renderFontScale200AtLightAndDark() = renderAtFontScale(2f)

    private fun renderAtFontScale(fontScale: Float) {
        val scaleQualifier = if (fontScale == 1f) "100" else "200"
        val preview = mutableStateOf(
            CardFlowPreviewConfiguration(
                state = CardFlowPreviewState.values().first(),
                darkTheme = false,
                fontScale = fontScale,
            ),
        )
        composeRule.setContent {
            val current = preview.value
            CardFlowPreviewContent(current.state, current.darkTheme, current.fontScale)
        }

        CardFlowPreviewState.values().forEach { state ->
            listOf(false, true).forEach { darkTheme ->
                val themeQualifier = if (darkTheme) "dark" else "light"
                val filePath = "../docs/design/states/" +
                    "${state.fileName}__${themeQualifier}__font-${scaleQualifier}__${widthDp}x${heightDp}.png"
                composeRule.runOnIdle {
                    preview.value = CardFlowPreviewConfiguration(state, darkTheme, fontScale)
                }
                composeRule.waitForIdle()
                val output = File(filePath)
                composeRule.onRoot().captureRoboImage(output)
                assertTrue("Missing rendered golden: $filePath", output.isFile)
            }
        }
    }
}

private data class CardFlowPreviewConfiguration(
    val state: CardFlowPreviewState,
    val darkTheme: Boolean,
    val fontScale: Float,
)

@RunWith(AndroidJUnit4::class)
@Config(sdk = [37], qualifiers = "w390dp-h844dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardFlowPreviewCatalogue390x844Test : CardFlowPreviewCatalogueTestBase(390, 844)

@RunWith(AndroidJUnit4::class)
@Config(sdk = [37], qualifiers = "w360dp-h828dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardFlowPreviewCatalogue360x828Test : CardFlowPreviewCatalogueTestBase(360, 828)
