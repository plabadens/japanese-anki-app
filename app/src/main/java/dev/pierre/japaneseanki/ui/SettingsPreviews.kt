package dev.pierre.japaneseanki.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.tooling.preview.Preview
import dev.pierre.japaneseanki.AnkiDeck
import dev.pierre.japaneseanki.AnkiFieldContract
import dev.pierre.japaneseanki.AnkiNoteType
import dev.pierre.japaneseanki.SettingsValues
import dev.pierre.japaneseanki.VoiceCatalogProvenance
import dev.pierre.japaneseanki.VoiceCatalogState
import dev.pierre.japaneseanki.speech.CloudVoice

internal enum class SettingsPreviewState(val fileName: String) {
    NO_KEY("settings-no-key"),
    KEY_SAVED("settings-key-saved"),
    VOICE_LIST_LOADING("settings-voice-list-loading"),
    VOICE_LIST_AUTHORITATIVE("settings-voice-list-authoritative"),
    VOICE_LIST_FAILED("settings-voice-list-failed"),
}

private val aoedePreviewVoice = CloudVoice(
    name = "ja-JP-Chirp3-HD-Aoede",
    displayName = "Aoede",
    languageCodes = listOf("ja-JP"),
    naturalSampleRateHz = 24_000,
)
private val korePreviewVoice = CloudVoice(
    name = "ja-JP-Chirp3-HD-Kore",
    displayName = "Kore",
    languageCodes = listOf("ja-JP"),
    naturalSampleRateHz = 24_000,
)

internal val settingsPreviewNoteTypes = listOf(
    AnkiNoteType(1, "Japanese Sentence", AnkiFieldContract.requiredFields),
    AnkiNoteType(2, "Japanese Sentence Alternate", AnkiFieldContract.requiredFields),
)
internal val settingsPreviewDecks = listOf(
    AnkiDeck(10, "Japanese"),
    AnkiDeck(20, "Language study"),
)

internal fun settingsPreviewScreenState(preview: SettingsPreviewState): SettingsScreenState {
    val selectedVoice = aoedePreviewVoice.name
    val values = SettingsValues(
        modelId = 1,
        deckId = 10,
        defaultTags = "language study",
        voiceName = if (preview == SettingsPreviewState.NO_KEY) "" else selectedVoice,
    )
    val catalog = when (preview) {
        SettingsPreviewState.NO_KEY,
        SettingsPreviewState.KEY_SAVED,
        -> VoiceCatalogState()
        SettingsPreviewState.VOICE_LIST_LOADING -> VoiceCatalogState(
            voices = listOf(aoedePreviewVoice),
            provenance = VoiceCatalogProvenance.CACHED,
            loading = true,
            attempted = true,
        )
        SettingsPreviewState.VOICE_LIST_AUTHORITATIVE -> VoiceCatalogState(
            voices = listOf(aoedePreviewVoice, korePreviewVoice),
            provenance = VoiceCatalogProvenance.AUTHORITATIVE,
            attempted = true,
        )
        SettingsPreviewState.VOICE_LIST_FAILED -> VoiceCatalogState(
            attempted = true,
            error = "Could not load voices.",
        )
    }
    val keySaved = preview != SettingsPreviewState.NO_KEY
    return SettingsScreenState(
        values = values,
        googleKey = if (keySaved) "synthetic-test-fixture" else "",
        credentialStored = keySaved,
        voiceCatalog = catalog,
        ankiConnected = true,
        ankiInstalled = true,
        onboardingIncomplete = false,
    )
}

@Composable
internal fun SettingsPreviewContent(
    state: SettingsPreviewState,
    darkTheme: Boolean = false,
    fontScale: Float = 1f,
) {
    val configuration = android.content.res.Configuration(LocalConfiguration.current).apply {
        this.fontScale = fontScale
    }
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalConfiguration provides configuration,
        LocalDensity provides Density(density.density, fontScale),
    ) {
        FlowTheme(darkTheme = darkTheme) {
            SettingsScreen(
                state = settingsPreviewScreenState(state),
                noteTypes = settingsPreviewNoteTypes,
                decks = settingsPreviewDecks,
                onBack = {},
                onFinishSetup = {},
                onConnectAnki = {},
                onModelChange = {},
                onDeckChange = {},
                onDefaultTagsChange = {},
                onGoogleKeyChange = {},
                onVoiceChange = {},
                onReloadVoices = {},
                onClearCache = {},
                onOpenSources = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Preview(name = "Settings / no key", widthDp = 390, heightDp = 844)
@Composable
internal fun SettingsNoKeyPreview() = SettingsPreviewContent(SettingsPreviewState.NO_KEY)

@Preview(name = "Settings / key saved", widthDp = 390, heightDp = 844)
@Composable
internal fun SettingsKeySavedPreview() = SettingsPreviewContent(SettingsPreviewState.KEY_SAVED)

@Preview(name = "Settings / voice list loading", widthDp = 390, heightDp = 844)
@Composable
internal fun SettingsVoiceListLoadingPreview() = SettingsPreviewContent(SettingsPreviewState.VOICE_LIST_LOADING)

@Preview(name = "Settings / voice list authoritative", widthDp = 390, heightDp = 844)
@Composable
internal fun SettingsVoiceListAuthoritativePreview() = SettingsPreviewContent(SettingsPreviewState.VOICE_LIST_AUTHORITATIVE)

@Preview(name = "Settings / voice list failed", widthDp = 390, heightDp = 844)
@Composable
internal fun SettingsVoiceListFailedPreview() = SettingsPreviewContent(SettingsPreviewState.VOICE_LIST_FAILED)
