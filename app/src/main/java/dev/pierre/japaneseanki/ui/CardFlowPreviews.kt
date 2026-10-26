package dev.pierre.japaneseanki.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.fillMaxSize
import dev.pierre.japaneseanki.AnkiDeck
import dev.pierre.japaneseanki.AnkiFieldContract
import dev.pierre.japaneseanki.AnkiNoteType
import dev.pierre.japaneseanki.JapaneseToken
import dev.pierre.japaneseanki.PitchAccent
import dev.pierre.japaneseanki.R
import dev.pierre.japaneseanki.TargetSpan
import dev.pierre.japaneseanki.speech.CloudVoice
import java.io.File

/**
 * Contract states rendered by the production Capture, setup and Card composables.
 *
 * The two setup states replace the retired dictionary-download states: the dictionary is bundled
 * and has no UI, so setup now only shows the required audio and AnkiDroid work plus the optional
 * camera.
 */
internal enum class CardFlowPreviewState(val fileName: String) {
    CAPTURE_CAMERA_IDLE("capture-camera-idle"),
    CAPTURE_PERMISSION_PROMPT("capture-camera-permission-prompt"),
    SETUP_READY("setup-ready"),
    SETUP_AUDIO_REQUIRED("setup-audio-required"),
    CAPTURE_TORCH_ON("capture-torch-on"),
    TYPE_EMPTY_FIELD("type-empty-field"),
    TYPE_FILLED_SENTENCE("type-filled-sentence"),
    CARD_NO_WORD_CHOSEN("card-no-word-chosen"),
    CARD_WORD_SELECTED("card-word-selected"),
    CARD_PRONUNCIATION_PREPARING("card-pronunciation-preparing"),
    CARD_BLOCKED_BY_SETUP("card-blocked-by-setup"),
    CARD_REQUIRED_ITEM_FAILED("card-required-item-failed"),
    CARD_READY_TO_SEND("card-ready-to-send"),
    CARD_SENT("card-sent"),
}

private const val PREVIEW_SENTENCE = "古い本を読みました。"

private val previewTokens = listOf(
    JapaneseToken(TargetSpan(0, 2), "古い", "古い", "ふるい", "ふるい", "形容詞"),
    JapaneseToken(TargetSpan(2, 3), "本", "本", "ほん", "ほん", "名詞-普通名詞"),
    JapaneseToken(TargetSpan(3, 4), "を", "を", "を", "を", "助詞-格助詞"),
    JapaneseToken(TargetSpan(4, 9), "読みました", "読む", "よみました", "よむ", "動詞-一般"),
    JapaneseToken(TargetSpan(9, 10), "。", "。", "", "", "補助記号-句点"),
)

/** Shared local wrapper used by Android Studio previews and the Roborazzi catalogue. */
@Composable
internal fun CardFlowPreviewContent(
    state: CardFlowPreviewState,
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
            when (state) {
                CardFlowPreviewState.CAPTURE_CAMERA_IDLE -> CapturePreview(
                    cameraPermissionGranted = true,
                    cameraPreviewState = CaptureCameraPreviewState(hasTorch = false),
                )
                CardFlowPreviewState.CAPTURE_PERMISSION_PROMPT -> CapturePreview(
                    cameraPermissionGranted = false,
                )
                CardFlowPreviewState.SETUP_READY -> SetupPreview(ready = true)
                CardFlowPreviewState.SETUP_AUDIO_REQUIRED -> SetupPreview(ready = false)
                CardFlowPreviewState.CAPTURE_TORCH_ON -> CapturePreview(
                    cameraPermissionGranted = true,
                    cameraPreviewState = CaptureCameraPreviewState(hasTorch = true, torchOn = true),
                )
                CardFlowPreviewState.TYPE_EMPTY_FIELD -> CapturePreview(mode = "Type")
                CardFlowPreviewState.TYPE_FILLED_SENTENCE -> CapturePreview(
                    mode = "Type",
                    sentence = PREVIEW_SENTENCE,
                )
                else -> CardPreview(state)
            }
        }
    }
}

@Composable
private fun CapturePreview(
    mode: String = "Photo",
    sentence: String = "",
    cameraPermissionGranted: Boolean = true,
    cameraPreviewState: CaptureCameraPreviewState? = null,
) {
    FlowPage {
        InputScreen(
            captureMode = mode,
            onCaptureModeChange = {},
            editor = TextFieldValue(sentence),
            onEditorChange = {},
            onContinue = {},
            onOpenGallery = {},
            onChoosePhoto = {},
            onCaptured = {},
            onCameraError = {},
            outputDirectory = File("/unused-design-preview"),
            modifier = Modifier.fillMaxSize(),
            cameraPermissionGranted = cameraPermissionGranted,
            onRequestCamera = {},
            cameraPreviewState = cameraPreviewState,
        )
    }
}

private val setupPreviewVoices = listOf(
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

@Composable
private fun SetupPreview(ready: Boolean) {
    val state = SetupUi(
        audio = AudioSetupUi(
            key = if (ready) "synthetic-design-fixture" else "",
            credentialStored = ready,
            selectedVoice = if (ready) setupPreviewVoices.first().name else "",
            voices = if (ready) setupPreviewVoices else emptyList(),
            loading = false,
            loadingVoices = false,
        ),
        anki = AnkiSetupUi(
            installed = true,
            connected = true,
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
        audioReady = ready,
        ankiReady = true,
    )
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        SetupScreen(
            state = state,
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

@Composable
private fun CardPreview(state: CardFlowPreviewState) {
    val target = TargetSpan(2, 3)
    val selected = state != CardFlowPreviewState.CARD_NO_WORD_CHOSEN
    val wordAudio = when (state) {
        CardFlowPreviewState.CARD_PRONUNCIATION_PREPARING -> ReviewAudioPresentation.Generating
        CardFlowPreviewState.CARD_BLOCKED_BY_SETUP -> ReviewAudioPresentation.NeedsPreparation
        CardFlowPreviewState.CARD_REQUIRED_ITEM_FAILED -> ReviewAudioPresentation.Failed(
            stringResource(
                R.string.card_audio_generation_failed,
                stringResource(R.string.card_word_audio_field),
                stringResource(R.string.card_audio_no_diagnostic_details),
            ),
        )
        else -> ReviewAudioPresentation.Ready
    }
    val sentenceAudio = if (state == CardFlowPreviewState.CARD_WORD_SELECTED) {
        ReviewAudioPresentation.Generating
    } else {
        ReviewAudioPresentation.Ready
    }
    val failureMessage = stringResource(
        R.string.card_audio_generation_failed,
        stringResource(R.string.card_word_audio_field),
        stringResource(R.string.card_audio_no_diagnostic_details),
    )
    val hint = when (state) {
        CardFlowPreviewState.CARD_NO_WORD_CHOSEN -> stringResource(R.string.card_hint_word)
        CardFlowPreviewState.CARD_PRONUNCIATION_PREPARING -> stringResource(R.string.card_hint_preparing)
        CardFlowPreviewState.CARD_BLOCKED_BY_SETUP -> stringResource(R.string.audio_setup_needed)
        CardFlowPreviewState.CARD_REQUIRED_ITEM_FAILED -> failureMessage
        CardFlowPreviewState.CARD_SENT -> stringResource(R.string.card_note_sent_to_ankidroid)
        else -> null
    }
    val failed = state == CardFlowPreviewState.CARD_REQUIRED_ITEM_FAILED
    val ready = state == CardFlowPreviewState.CARD_READY_TO_SEND
    FlowPage(topBar = {
        FlowTopBar(title = stringResource(R.string.card), onBack = {}, onSettings = {})
    }) {
        CardScreen(
            sentence = PREVIEW_SENTENCE,
            tokens = previewTokens,
            selectedSpan = if (selected) target else null,
            selectionEnabled = state != CardFlowPreviewState.CARD_SENT,
            onSelectSpan = {},
            furigana = emptyList(),
            onEditSentence = {},
            translation = "I read an old book.",
            vocabulary = if (selected) "本" else "",
            vocabularyReading = if (selected) "ほん" else "",
            partOfSpeech = if (selected) "noun" else null,
            definition = if (selected) "book" else "",
            pitch = if (selected) PitchAccent.create("ほん", 1) else null,
            pitchStatus = null,
            pitchPending = false,
            wordStatus = null,
            wordAudioEnabled = selected && state != CardFlowPreviewState.CARD_BLOCKED_BY_SETUP,
            meaningAvailable = false,
            onChangeMeaning = {},
            imagePath = null,
            imageFocusY = 0.5f,
            notes = "",
            tags = "",
            onNotesChange = {},
            onTagsChange = {},
            sentenceAudio = sentenceAudio,
            wordAudio = wordAudio,
            onToggleSentenceAudio = {},
            onRetrySentenceAudio = {},
            onToggleWordAudio = {},
            onRetryWordAudio = {},
            hint = hint,
            hintPreparing = state == CardFlowPreviewState.CARD_PRONUNCIATION_PREPARING,
            actionLabel = if (failed) stringResource(R.string.retry) else stringResource(R.string.send),
            actionEnabled = ready || failed,
            actionLoading = false,
            onAction = {},
            statusMessage = null,
            statusContent = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Preview(name = "Capture / camera idle", widthDp = 390, heightDp = 844)
@Composable
internal fun CaptureCameraIdlePreview() = CardFlowPreviewContent(CardFlowPreviewState.CAPTURE_CAMERA_IDLE)

@Preview(name = "Capture / permission prompt", widthDp = 390, heightDp = 844)
@Composable
internal fun CapturePermissionPromptPreview() = CardFlowPreviewContent(CardFlowPreviewState.CAPTURE_PERMISSION_PROMPT)

@Preview(name = "Setup / audio required", widthDp = 390, heightDp = 844)
@Composable
internal fun SetupAudioRequiredPreview() = CardFlowPreviewContent(CardFlowPreviewState.SETUP_AUDIO_REQUIRED)

@Preview(name = "Setup / ready", widthDp = 390, heightDp = 844)
@Composable
internal fun SetupReadyPreview() = CardFlowPreviewContent(CardFlowPreviewState.SETUP_READY)

@Preview(name = "Capture / torch on", widthDp = 390, heightDp = 844)
@Composable
internal fun CaptureTorchOnPreview() = CardFlowPreviewContent(CardFlowPreviewState.CAPTURE_TORCH_ON)

@Preview(name = "Type / empty field", widthDp = 390, heightDp = 844)
@Composable
internal fun TypeEmptyFieldPreview() = CardFlowPreviewContent(CardFlowPreviewState.TYPE_EMPTY_FIELD)

@Preview(name = "Type / filled sentence", widthDp = 390, heightDp = 844)
@Composable
internal fun TypeFilledSentencePreview() = CardFlowPreviewContent(CardFlowPreviewState.TYPE_FILLED_SENTENCE)

@Preview(name = "Card / no word chosen", widthDp = 390, heightDp = 844)
@Composable
internal fun CardNoWordChosenPreview() = CardFlowPreviewContent(CardFlowPreviewState.CARD_NO_WORD_CHOSEN)

@Preview(name = "Card / word selected", widthDp = 390, heightDp = 844)
@Composable
internal fun CardWordSelectedPreview() = CardFlowPreviewContent(CardFlowPreviewState.CARD_WORD_SELECTED)

@Preview(name = "Card / pronunciation preparing", widthDp = 390, heightDp = 844)
@Composable
internal fun CardPronunciationPreparingPreview() = CardFlowPreviewContent(CardFlowPreviewState.CARD_PRONUNCIATION_PREPARING)

@Preview(name = "Card / blocked by setup", widthDp = 390, heightDp = 844)
@Composable
internal fun CardBlockedBySetupPreview() = CardFlowPreviewContent(CardFlowPreviewState.CARD_BLOCKED_BY_SETUP)

@Preview(name = "Card / required item failed", widthDp = 390, heightDp = 844)
@Composable
internal fun CardRequiredItemFailedPreview() = CardFlowPreviewContent(CardFlowPreviewState.CARD_REQUIRED_ITEM_FAILED)

@Preview(name = "Card / ready to send", widthDp = 390, heightDp = 844)
@Composable
internal fun CardReadyToSendPreview() = CardFlowPreviewContent(CardFlowPreviewState.CARD_READY_TO_SEND)

@Preview(name = "Card / sent", widthDp = 390, heightDp = 844)
@Composable
internal fun CardSentPreview() = CardFlowPreviewContent(CardFlowPreviewState.CARD_SENT)
