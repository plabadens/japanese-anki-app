package dev.pierre.japaneseanki.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pierre.japaneseanki.AnkiDeck
import dev.pierre.japaneseanki.AnkiFieldContract
import dev.pierre.japaneseanki.AnkiNoteType
import dev.pierre.japaneseanki.speech.CloudVoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Behaviour of the one-page setup surface: the voice list must actually open, AnkiDroid and audio
 * must gate Finish, and the bundled dictionary must not create any download affordance.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [37], qualifiers = "w390dp-h844dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SetupScreenBehaviorTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val voices = listOf(
        CloudVoice(
            name = "ja-JP-Chirp3-HD-Aoede",
            displayName = "Aoede",
            languageCodes = listOf("ja-JP"),
            naturalSampleRateHz = 24_000,
        ),
        CloudVoice(
            name = "ja-JP-Chirp3-HD-Kore",
            displayName = "Kore",
            languageCodes = listOf("ja-JP"),
            naturalSampleRateHz = 24_000,
        ),
    )

    private fun setupUi(
        audioReady: Boolean = true,
        ankiReady: Boolean = true,
        ankiConnected: Boolean = true,
        selectedVoice: String = "ja-JP-Chirp3-HD-Aoede",
    ) = SetupUi(
        audio = AudioSetupUi(
            key = if (audioReady) "synthetic-test-fixture" else "",
            credentialStored = audioReady,
            selectedVoice = if (audioReady) selectedVoice else "",
            voices = if (audioReady) voices else emptyList(),
            loading = false,
            loadingVoices = false,
        ),
        anki = AnkiSetupUi(
            installed = true,
            connected = ankiConnected,
            loading = false,
            noteTypes = listOf(AnkiNoteType(1, "Japanese Sentence", AnkiFieldContract.requiredFields)),
            decks = listOf(AnkiDeck(10, "Japanese")),
            selectedNoteTypeId = 1,
            selectedDeckId = 10,
        ),
        cameraPermissionGranted = false,
        cameraDenied = false,
        dictionaryPreparing = false,
        dictionaryErrorResource = null,
        audioReady = audioReady,
        ankiReady = ankiReady,
    )

    @Test
    fun firstRunSetupRendersAndCompletesInsideItsFullScreenFlowDialog() {
        val initial = SetupUi(
            audio = AudioSetupUi(
                key = "",
                credentialStored = false,
                selectedVoice = "",
                voices = emptyList(),
                loading = false,
                loadingVoices = false,
            ),
            anki = AnkiSetupUi(
                installed = false,
                connected = false,
                loading = false,
                noteTypes = emptyList(),
                decks = emptyList(),
                selectedNoteTypeId = null,
                selectedDeckId = null,
            ),
            cameraPermissionGranted = false,
            cameraDenied = false,
            dictionaryPreparing = true,
            dictionaryErrorResource = null,
            audioReady = false,
            ankiReady = false,
        )
        val state = mutableStateOf(initial)
        var finished = false
        composeRule.setContent {
            FlowDialog(
                onDismissRequest = {},
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false,
                    dismissOnClickOutside = false,
                ),
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SetupScreen(
                        state = state.value,
                        onBack = null,
                        onKeyChange = {},
                        onVoiceChange = {},
                        onLoadVoices = {},
                        onRequestCamera = {},
                        onConnectAnki = {},
                        onSelectNoteType = {},
                        onSelectDeck = {},
                        onRetryDictionary = {},
                        onFinish = { finished = true },
                    )
                }
            }
        }

        composeRule.onNodeWithText("Preparing the offline dictionary").assertIsDisplayed()
        composeRule.onNodeWithText("Install AnkiDroid").assertIsDisplayed()
        composeRule.onNodeWithText("Finish setup").assertIsNotEnabled()

        composeRule.runOnIdle { state.value = setupUi() }
        composeRule.onNodeWithText("Everything is ready").assertIsDisplayed()
        composeRule.onNodeWithText("Finish setup").assertIsEnabled().performClick()
        composeRule.runOnIdle { assertTrue(finished) }
    }

    @Test
    fun voiceRowOpensTheListAndReportsTheChosenVoice() {
        val state = mutableStateOf(setupUi())
        var chosen: String? = null
        composeRule.setContent {
            FlowTheme(darkTheme = false) {
                SetupScreen(
                    state = state.value,
                    onBack = null,
                    onKeyChange = {},
                    onVoiceChange = { name ->
                        chosen = name
                        state.value = state.value.copy(
                            audio = state.value.audio.copy(selectedVoice = name),
                        )
                    },
                    onLoadVoices = {},
                    onRequestCamera = {},
                    onConnectAnki = {},
                    onSelectNoteType = {},
                    onSelectDeck = {},
                    onRetryDictionary = {},
                    onFinish = {},
                )
            }
        }

        composeRule.onNodeWithText("Sentence voice").performScrollTo().performClick()
        composeRule.onNodeWithText("Kore").performScrollTo().performClick()

        composeRule.runOnIdle {
            assertEquals("ja-JP-Chirp3-HD-Kore", chosen)
            assertEquals("ja-JP-Chirp3-HD-Kore", state.value.audio.selectedVoice)
        }
        composeRule.onNodeWithText("Kore · ja-JP-Chirp3-HD-Kore").assertIsDisplayed()
    }

    @Test
    fun setupPickersUseFortyEightDpRadioRowsAndSelectOptions() {
        val initial = setupUi()
        val state = mutableStateOf(
            initial.copy(
                anki = initial.anki.copy(
                    noteTypes = initial.anki.noteTypes + AnkiNoteType(
                        2,
                        "Alternate note type",
                        AnkiFieldContract.requiredFields,
                    ),
                    decks = initial.anki.decks + AnkiDeck(11, "Second deck"),
                ),
            ),
        )
        composeRule.setContent {
            FlowTheme(darkTheme = false) {
                SetupScreen(
                    state = state.value,
                    onBack = null,
                    onKeyChange = {},
                    onVoiceChange = {},
                    onLoadVoices = {},
                    onRequestCamera = {},
                    onConnectAnki = {},
                    onSelectNoteType = { id ->
                        state.value = state.value.copy(anki = state.value.anki.copy(selectedNoteTypeId = id))
                    },
                    onSelectDeck = { id ->
                        state.value = state.value.copy(anki = state.value.anki.copy(selectedDeckId = id))
                    },
                    onRetryDictionary = {},
                    onFinish = {},
                )
            }
        }

        composeRule.onNodeWithText("Note type").performScrollTo().performClick()
        val noteTypeOption = composeRule.onNodeWithText("Alternate note type").fetchSemanticsNode()
        assertEquals(Role.RadioButton, noteTypeOption.config[SemanticsProperties.Role])
        assertTrue(noteTypeOption.boundsInRoot.height >= with(composeRule.density) { 48.dp.toPx() })
        composeRule.onNodeWithText("Alternate note type").performClick()
        composeRule.runOnIdle { assertEquals(2L, state.value.anki.selectedNoteTypeId) }

        composeRule.onNodeWithText("Deck").performScrollTo().performClick()
        val deckOption = composeRule.onNodeWithText("Second deck").fetchSemanticsNode()
        assertEquals(Role.RadioButton, deckOption.config[SemanticsProperties.Role])
        assertTrue(deckOption.boundsInRoot.height >= with(composeRule.density) { 48.dp.toPx() })
        composeRule.onNodeWithText("Second deck").performClick()
        composeRule.runOnIdle { assertEquals(11L, state.value.anki.selectedDeckId) }
    }

    @Test
    fun backAffordanceOnlyExistsWhenSetupCanBeLeft() {
        var backs = 0
        composeRule.setContent {
            FlowTheme(darkTheme = false) {
                SetupScreen(
                    state = setupUi(),
                    onBack = { backs++ },
                    onKeyChange = {},
                    onVoiceChange = {},
                    onLoadVoices = {},
                    onRequestCamera = {},
                    onConnectAnki = {},
                    onSelectNoteType = {},
                    onSelectDeck = {},
                    onRetryDictionary = {},
                    onFinish = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.runOnIdle { assertEquals(1, backs) }
    }

    @Test
    fun finishStaysDisabledUntilAudioAndAnkiDroidAreBothReady() {
        val state = mutableStateOf(setupUi(audioReady = false, ankiReady = false, ankiConnected = false))
        composeRule.setContent {
            FlowTheme(darkTheme = false) {
                SetupScreen(
                    state = state.value,
                    onBack = null,
                    onKeyChange = {},
                    onVoiceChange = {},
                    onLoadVoices = {},
                    onRequestCamera = {},
                    onConnectAnki = {},
                    onSelectNoteType = {},
                    onSelectDeck = {},
                    onRetryDictionary = {},
                    onFinish = {},
                )
            }
        }

        composeRule.onNodeWithText("Two steps left").assertIsDisplayed()
        composeRule.onNodeWithText("Finish setup").assertIsNotEnabled()
        composeRule.onNodeWithText("Connect AnkiDroid").assertIsDisplayed()

        composeRule.runOnIdle { state.value = setupUi() }

        composeRule.onNodeWithText("Everything is ready").assertIsDisplayed()
        composeRule.onNodeWithText("Finish setup").assertIsEnabled()
    }

    @Test
    fun ankiDroidIsRequiredWhileTheCameraIsOptional() {
        val state = mutableStateOf(setupUi(ankiReady = false, ankiConnected = false))
        composeRule.setContent {
            FlowTheme(darkTheme = false) {
                SetupScreen(
                    state = state.value,
                    onBack = null,
                    onKeyChange = {},
                    onVoiceChange = {},
                    onLoadVoices = {},
                    onRequestCamera = {},
                    onConnectAnki = {},
                    onSelectNoteType = {},
                    onSelectDeck = {},
                    onRetryDictionary = {},
                    onFinish = {},
                )
            }
        }

        composeRule.onNodeWithText("AnkiDroid is left").assertIsDisplayed()
        composeRule.onNodeWithText("Finish setup").assertIsNotEnabled()
        composeRule.onNodeWithText("Connect AnkiDroid").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Allow camera").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("Camera access is off.").assertCountEquals(0)
    }

    @Test
    fun bundledDictionaryHasNoDownloadSurface() {
        composeRule.setContent {
            FlowTheme(darkTheme = false) {
                SetupScreen(
                    state = setupUi(),
                    onBack = null,
                    onKeyChange = {},
                    onVoiceChange = {},
                    onLoadVoices = {},
                    onRequestCamera = {},
                    onConnectAnki = {},
                    onSelectNoteType = {},
                    onSelectDeck = {},
                    onRetryDictionary = {},
                    onFinish = {},
                )
            }
        }

        listOf("Dictionary", "Download", "Downloads", "UniDic", "Waiting for Wi-Fi", "Pause", "Resume")
            .forEach { label ->
                composeRule.onAllNodesWithText(label, substring = true).assertCountEquals(0)
            }
    }

    @Test
    fun preparingTheDictionaryIsAStatusLineNotATask() {
        val state = mutableStateOf(setupUi())
        composeRule.setContent {
            FlowTheme(darkTheme = false) {
                SetupScreen(
                    state = state.value,
                    onBack = null,
                    onKeyChange = {},
                    onVoiceChange = {},
                    onLoadVoices = {},
                    onRequestCamera = {},
                    onConnectAnki = {},
                    onSelectNoteType = {},
                    onSelectDeck = {},
                    onRetryDictionary = {},
                    onFinish = {},
                )
            }
        }

        composeRule.runOnIdle {
            state.value = state.value.copy(dictionaryPreparing = true)
        }

        composeRule.onNodeWithText("Preparing the offline dictionary").assertIsDisplayed()
        composeRule.onNodeWithText("Finish setup").assertIsNotEnabled()
        composeRule.onAllNodesWithText("Download", substring = true).assertCountEquals(0)
    }
}
