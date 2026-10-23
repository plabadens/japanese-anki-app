package dev.pierre.japaneseanki.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pierre.japaneseanki.SettingsValues
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.distinctUntilChanged
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Device check for the IME inside Settings' full-size, edge-to-edge dialog window. */
@RunWith(AndroidJUnit4::class)
class SettingsDialogInputInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun keyAndTagEditorsRaiseImeInsideSettingsDialog() {
        val state = mutableStateOf(settingsPreviewScreenState(SettingsPreviewState.KEY_SAVED))
        val imeVisible = AtomicBoolean(false)

        composeRule.setContent {
            val density = LocalDensity.current
            val imeInsets = WindowInsets.ime
            LaunchedEffect(density, imeInsets) {
                snapshotFlow { imeInsets.getBottom(density) > 0 }
                    .distinctUntilChanged()
                    .collect(imeVisible::set)
            }
            FlowTheme(darkTheme = false) {
                Dialog(
                    onDismissRequest = {},
                    properties = DialogProperties(
                        usePlatformDefaultWidth = false,
                        decorFitsSystemWindows = false,
                    ),
                ) {
                    Surface(Modifier.fillMaxSize()) {
                        SettingsScreen(
                            state = state.value,
                            noteTypes = settingsPreviewNoteTypes,
                            decks = settingsPreviewDecks,
                            onBack = {},
                            onFinishSetup = {},
                            onConnectAnki = {},
                            onModelChange = {},
                            onDeckChange = {},
                            onDefaultTagsChange = { value ->
                                state.value = state.value.updated(values = state.value.values.copy(defaultTags = value))
                            },
                            onGoogleKeyChange = { value ->
                                state.value = state.value.updated(googleKey = value, credentialStored = value.isNotBlank())
                            },
                            onVoiceChange = {},
                            onReloadVoices = {},
                                        onClearCache = {},
                            onOpenSources = {},
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("Google Cloud API key").performClick()
        composeRule.waitUntil(10_000) { imeVisible.get() }
        composeRule.onNodeWithText("Google Cloud API key").performTextInput("device-fixture")

        composeRule.onNodeWithText("Default tags").performScrollTo().performClick()
        composeRule.waitUntil(10_000) { imeVisible.get() }
        composeRule.onNodeWithText("Default tags").performTextInput("device-tag")
        composeRule.runOnIdle {
            assertEquals("language studydevice-tag", state.value.values.defaultTags)
        }
    }
}

private fun SettingsScreenState.updated(
    values: SettingsValues = this.values,
    googleKey: String = this.googleKey,
    credentialStored: Boolean = this.credentialStored,
) = SettingsScreenState(
    values = values,
    googleKey = googleKey,
    credentialStored = credentialStored,
    loading = loading,
    credentialUnreadable = credentialUnreadable,
    error = error,
    voiceCatalog = voiceCatalog,
    ankiConnected = ankiConnected,
    ankiInstalled = ankiInstalled,
    onboardingIncomplete = onboardingIncomplete,
    cacheBusy = cacheBusy,
    cacheMessage = cacheMessage,
)
