package dev.pierre.japaneseanki.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pierre.japaneseanki.AppRoute
import dev.pierre.japaneseanki.R
import dev.pierre.japaneseanki.SettingsValues
import dev.pierre.japaneseanki.AppNavigationState
import dev.pierre.japaneseanki.AppDestination
import dev.pierre.japaneseanki.visibleRouteForAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [37], qualifiers = "w390dp-h844dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsScreenBehaviorTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun keyAndTagFieldsAcceptInputInsideFullscreenDialog() {
        val state = showSettings(SettingsPreviewState.KEY_SAVED)

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.google_key)).performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.google_key)).performTextInput("-typed-fixture")
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.default_tags)).performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.default_tags)).performTextInput("fresh-tag")

        composeRule.runOnIdle {
            assertEquals("synthetic-test-fixture-typed-fixture", state.value.googleKey)
            assertEquals("language studyfresh-tag", state.value.values.defaultTags)
        }
    }

    @Test
    fun noteTypeDeckAndAuthoritativeVoicePickersOpenAndPersistSelections() {
        val state = showSettings(SettingsPreviewState.VOICE_LIST_AUTHORITATIVE)

        composeRule.onNodeWithText("Japanese Sentence").performClick()
        composeRule.onNodeWithText("Japanese Sentence Alternate").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Japanese Sentence Alternate").assertIsDisplayed()

        composeRule.onNodeWithText("Japanese").performScrollTo().performClick()
        composeRule.onNodeWithText("Language study").assertIsDisplayed().performClick()

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.sentence_voice)).performScrollTo().performClick()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.voice_summary, "Kore", "ja-JP-Chirp3-HD-Kore"),
        ).assertIsDisplayed().performClick()

        composeRule.runOnIdle {
            assertEquals(2L, state.value.values.modelId)
            assertEquals(20L, state.value.values.deckId)
            assertEquals("ja-JP-Chirp3-HD-Kore", state.value.values.voiceName)
        }
    }

    @Test
    fun noKeySettingsStateDoesNotClaimCredentialsAreStored() {
        val noKeyState = showSettings(SettingsPreviewState.NO_KEY)
        assertEquals(false, noKeyState.value.credentialStored)
        assertEquals(null, noKeyState.value.voiceCatalog.error)
        composeRule.onAllNodesWithText("Use app while setup runs").assertCountEquals(0)
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.sentence_voice)).performScrollTo().performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.key_required)).assertIsDisplayed()
    }

    @Test
    fun voiceFailureStateExplainsWhatTheUserCanDo() {
        showSettings(SettingsPreviewState.VOICE_LIST_FAILED)
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.sentence_voice)).performScrollTo()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.voices_failed)).assertIsDisplayed()
    }

    @Test
    fun restoredCardAndIncompleteSetupStayBehindTheAnalyzerGate() {
        assertEquals(
            AppRoute.Capture,
            visibleRouteForAnalyzer(AppRoute.Card, analyzerReady = false, frozenCardActive = false),
        )
        assertEquals(
            AppRoute.Capture,
            visibleRouteForAnalyzer(AppRoute.Capture, analyzerReady = false, frozenCardActive = true),
        )
        assertEquals(
            AppRoute.Card,
            visibleRouteForAnalyzer(AppRoute.Capture, analyzerReady = true, frozenCardActive = true),
        )
        assertEquals(
            AppRoute.Card,
            visibleRouteForAnalyzer(AppRoute.Card, analyzerReady = true, frozenCardActive = false),
        )

        val navigation = AppNavigationState(AppRoute.Card)
        assertEquals(AppDestination.Setup, navigation.visibleDestination(false, true))
        assertEquals(AppDestination.Setup, navigation.visibleDestination(true, false))
        assertEquals(AppDestination.Card, navigation.visibleDestination(true, true))
        navigation.open(AppDestination.Settings)
        assertEquals(AppDestination.Settings, navigation.visibleDestination(false, false))
    }

    private fun showSettings(preview: SettingsPreviewState): MutableState<SettingsScreenState> {
        val state = mutableStateOf(settingsPreviewScreenState(preview))
        composeRule.setContent {
            FlowTheme(darkTheme = false) {
                SettingsScreen(
                    state = state.value,
                    noteTypes = settingsPreviewNoteTypes,
                    decks = settingsPreviewDecks,
                    onBack = {},
                    onFinishSetup = {},
                    onConnectAnki = {},
                    onModelChange = { id -> state.value = state.value.updated(values = state.value.values.copy(modelId = id)) },
                    onDeckChange = { id -> state.value = state.value.updated(values = state.value.values.copy(deckId = id)) },
                    onDefaultTagsChange = { value -> state.value = state.value.updated(values = state.value.values.copy(defaultTags = value)) },
                    onGoogleKeyChange = { value -> state.value = state.value.updated(googleKey = value, credentialStored = value.isNotBlank()) },
                    onVoiceChange = { value -> state.value = state.value.updated(values = state.value.values.copy(voiceName = value)) },
                    onReloadVoices = {},
                    onClearCache = {},
                    onOpenSources = {},
                )
            }
        }
        return state
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
