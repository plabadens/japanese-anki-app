package dev.pierre.japaneseanki.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [37], qualifiers = "w390dp-h844dp-night-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FlowDialogThemeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun alertDialogOpenedFromLightActivityContentUsesSystemDarkFlowTheme() {
        val dialogSurface = AtomicReference<Color?>()
        val dialogContent = AtomicReference<Color?>()

        composeRule.setContent {
            FlowTheme(darkTheme = false) {
                var open by remember { mutableStateOf(false) }
                androidx.compose.material3.TextButton(onClick = { open = true }) {
                    Text("open-flow-dialog")
                }
                if (open) {
                    FlowDialog(
                        onDismissRequest = { open = false },
                        title = { Text("dialog-title") },
                        text = {
                            val surface = MaterialTheme.colorScheme.surface
                            val content = MaterialTheme.colorScheme.onSurface
                            SideEffect {
                                dialogSurface.set(surface)
                                dialogContent.set(content)
                            }
                            Text("dialog-body")
                        },
                        confirmButton = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("open-flow-dialog").performClick()
        composeRule.onNodeWithText("dialog-body").assertExists()
        composeRule.runOnIdle {
            assertNotNull("dialog content must compose", dialogSurface.get())
            assertEquals(FlowDarkColors.surface, dialogSurface.get())
            assertEquals(FlowDarkColors.onSurface, dialogContent.get())
        }
    }
}
