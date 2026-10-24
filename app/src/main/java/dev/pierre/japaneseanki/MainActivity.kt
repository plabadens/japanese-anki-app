package dev.pierre.japaneseanki

import androidx.compose.ui.res.stringResource

import android.Manifest
import android.content.Context
import android.content.Intent

import android.net.Uri
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Html
import android.util.Log
import android.media.MediaPlayer
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.InputChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.semantics.semantics
import dev.pierre.japaneseanki.ui.FlowDialog
import dev.pierre.japaneseanki.ui.FlowStatusText
import dev.pierre.japaneseanki.ui.flow
import dev.pierre.japaneseanki.ui.AppContentMaxWidth

import dev.pierre.japaneseanki.ui.ReviewAudioPresentation
import dev.pierre.japaneseanki.ui.CardAction
import dev.pierre.japaneseanki.card.CardDeckSheet
import dev.pierre.japaneseanki.card.CardDiscardConfirmationDialog
import dev.pierre.japaneseanki.card.CardHintActionEffects
import dev.pierre.japaneseanki.card.CardHintActionSignals
import dev.pierre.japaneseanki.card.CardScreenContent
import dev.pierre.japaneseanki.card.CardNoTargetRecoveryContent
import dev.pierre.japaneseanki.card.CardScreenContentModel
import dev.pierre.japaneseanki.card.CardScreenDetailInput
import dev.pierre.japaneseanki.card.CardScreenStateHolder
import dev.pierre.japaneseanki.card.rememberCardScreenStateHolder
import dev.pierre.japaneseanki.card.CardTargetSelectionCoordinator
import dev.pierre.japaneseanki.card.SentenceOcrChoicesSheet
import dev.pierre.japaneseanki.card.CardTargetSelectionSnapshot
import dev.pierre.japaneseanki.card.CardVocabularyEffects
import dev.pierre.japaneseanki.card.CardVocabularyErrorCopy
import dev.pierre.japaneseanki.card.CardVocabularyReadingCoordinator
import dev.pierre.japaneseanki.card.CardVocabularySnapshot
import dev.pierre.japaneseanki.card.DictionaryMatchPageRequest
import dev.pierre.japaneseanki.card.cardHintAction
import dev.pierre.japaneseanki.card.cardSendBlockers
import dev.pierre.japaneseanki.card.cardScreenPresentationContext
import dev.pierre.japaneseanki.card.loadMoreDictionaryMatches
import dev.pierre.japaneseanki.card.CardSendBlockerSignals
import dev.pierre.japaneseanki.card.CardSentenceEditorSheet
import dev.pierre.japaneseanki.card.commitCardSentenceEdit
import dev.pierre.japaneseanki.card.useCardSentenceSelectionAsTarget
import dev.pierre.japaneseanki.card.CardMeaningPicker
import dev.pierre.japaneseanki.ui.reviewTranslationFailureStatus
import dev.pierre.japaneseanki.ui.reviewAudioGenerationFailureMessage
import dev.pierre.japaneseanki.ui.reviewAudioPlaybackFailureMessage
import dev.pierre.japaneseanki.ui.reviewAudioSendBlocker
import dev.pierre.japaneseanki.ui.RecoveryReason
import dev.pierre.japaneseanki.ui.SendPhase
import dev.pierre.japaneseanki.ui.SendState
import dev.pierre.japaneseanki.ui.FlowTheme
import dev.pierre.japaneseanki.ui.FlowTopBar
import dev.pierre.japaneseanki.ui.LocalTouchFeedbackDispatcher
import dev.pierre.japaneseanki.ui.MotionAwareProgressIndicator
import dev.pierre.japaneseanki.ui.TouchFeedbackEvent
import dev.pierre.japaneseanki.send.IndeterminateReason
import dev.pierre.japaneseanki.send.SendCoordinator
import dev.pierre.japaneseanki.send.SendOutcome
import dev.pierre.japaneseanki.send.SendRequest
import dev.pierre.japaneseanki.send.cardActionClickHandler
import dev.pierre.japaneseanki.send.toUiUpdate
import dev.pierre.japaneseanki.ui.InputScreen
import dev.pierre.japaneseanki.ui.SettingsScreen
import dev.pierre.japaneseanki.ui.SettingsScreenState
import dev.pierre.japaneseanki.ui.AudioSetupUi
import dev.pierre.japaneseanki.ui.AnkiSetupUi
import dev.pierre.japaneseanki.ui.SetupScreen
import dev.pierre.japaneseanki.ui.SetupUi
import dev.pierre.japaneseanki.ui.ocrFocusFraction
import dev.pierre.japaneseanki.speech.CloudVoice
import dev.pierre.japaneseanki.speech.GoogleChirpSpeechProvider
import dev.pierre.japaneseanki.speech.HintResult
import dev.pierre.japaneseanki.speech.ReadingHintBuilder
import dev.pierre.japaneseanki.speech.SentenceSpeechProvider
import dev.pierre.japaneseanki.speech.SentenceSpeechRequest
import dev.pierre.japaneseanki.speech.SpeechException
import dev.pierre.japaneseanki.speech.decodeVoices
import dev.pierre.japaneseanki.speech.encodeVoices
import dev.pierre.japaneseanki.speech.sentenceHintSpans
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import java.io.File
import java.nio.charset.Charset
import java.text.NumberFormat
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow

private fun formatIecByteCount(context: Context, bytes: Long): String {
    val units = intArrayOf(
        R.string.bytes_unit,
        R.string.kibibytes_unit,
        R.string.mebibytes_unit,
        R.string.gibibytes_unit,
        R.string.tebibytes_unit,
    )
    var value = bytes.coerceAtLeast(0L).toDouble()
    var unit = 0
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    val formatter = NumberFormat.getNumberInstance(context.resources.configuration.locales[0]).apply {
        minimumFractionDigits = if (unit == 0) 0 else 1
        maximumFractionDigits = if (unit == 0) 0 else 1
    }
    return context.getString(R.string.formatted_byte_count, formatter.format(value), context.getString(units[unit]))
}

private const val ANKIDROID_READ_WRITE_PERMISSION =
    "com.ichi2.anki.permission.READ_WRITE_DATABASE"
internal const val IMAGE_RECEIPT_PATH = "image_import_receipt_path"
internal const val IMAGE_RECEIPT_FILENAME = "image_import_receipt_filename"
internal const val IMAGE_RECEIPT_SIZE = "image_import_receipt_size"
internal const val IMAGE_RECEIPT_MODIFIED = "image_import_receipt_modified"
private const val VOICE_CACHE_PREF = "google_voice_cache"

/** Fixed, disclosed sample for the explicit translation check; one billable request. */
private const val TRANSLATION_TEST_SAMPLE = "今日はいい天気ですね。"
internal const val SENTENCE_AUDIO_RECEIPT_PATH = "sentence_audio_receipt_path"
internal const val SENTENCE_AUDIO_RECEIPT_FILENAME = "sentence_audio_receipt_filename"
internal const val SENTENCE_AUDIO_RECEIPT_SIZE = "sentence_audio_receipt_size"
internal const val SENTENCE_AUDIO_RECEIPT_MODIFIED = "sentence_audio_receipt_modified"
internal const val WORD_AUDIO_RECEIPT_PATH = "word_audio_receipt_path"
internal const val WORD_AUDIO_RECEIPT_FILENAME = "word_audio_receipt_filename"
internal const val WORD_AUDIO_RECEIPT_SIZE = "word_audio_receipt_size"
internal const val WORD_AUDIO_RECEIPT_MODIFIED = "word_audio_receipt_modified"


internal val IMAGE_RECEIPT_KEYS = ImportedReceiptKeys(
    path = IMAGE_RECEIPT_PATH,
    filename = IMAGE_RECEIPT_FILENAME,
    size = IMAGE_RECEIPT_SIZE,
    modified = IMAGE_RECEIPT_MODIFIED,
)

internal val SENTENCE_AUDIO_RECEIPT_KEYS = ImportedReceiptKeys(
    path = SENTENCE_AUDIO_RECEIPT_PATH,
    filename = SENTENCE_AUDIO_RECEIPT_FILENAME,
    size = SENTENCE_AUDIO_RECEIPT_SIZE,
    modified = SENTENCE_AUDIO_RECEIPT_MODIFIED,
)

internal val WORD_AUDIO_RECEIPT_KEYS = ImportedReceiptKeys(
    path = WORD_AUDIO_RECEIPT_PATH,
    filename = WORD_AUDIO_RECEIPT_FILENAME,
    size = WORD_AUDIO_RECEIPT_SIZE,
    modified = WORD_AUDIO_RECEIPT_MODIFIED,
)


/** Every preference key the draft-audio controller owns, for a full draft reset. */
private fun pitchUnavailableMessage(failure: AjtPitchFailure): Int = when (failure) {
    AjtPitchFailure.MISSING_INDEX -> R.string.card_ajt_pitch_index_missing
    AjtPitchFailure.EMPTY_INDEX -> R.string.card_ajt_pitch_index_empty
    AjtPitchFailure.INVALID_INDEX -> R.string.card_ajt_pitch_index_invalid
    AjtPitchFailure.OPEN_FAILED -> R.string.card_ajt_pitch_index_open_failed
}

/** Per-item audio state for the current draft revision. */
internal data class DraftAudio(
    val path: String? = null,
    val generating: Boolean = false,
    val error: String? = null,
    val sourceMissing: Boolean = false,
    val advisoryHeadline: String? = null,
    val advisoryMessage: String? = null,
    val identity: String = "",
    val needsContinue: Boolean = false,
) {
    fun presentation(actionLabel: String): ReviewAudioPresentation = when {
        generating -> ReviewAudioPresentation.Generating
        path != null -> ReviewAudioPresentation.Ready
        sourceMissing -> ReviewAudioPresentation.SourceMissing
        advisoryHeadline != null && advisoryMessage != null ->
            ReviewAudioPresentation.Advisory(advisoryHeadline, advisoryMessage, actionLabel)
        error != null -> ReviewAudioPresentation.Failed(error)
        needsContinue -> ReviewAudioPresentation.NeedsPreparation
        else -> ReviewAudioPresentation.Unavailable
    }
}

/** UI view of the controller's per-item state; the controller stays the only source of truth. */
private fun DraftAudioState.toDraftAudio(item: AudioItem, context: Context): DraftAudio = when (this) {
    DraftAudioState.Idle -> DraftAudio()
    is DraftAudioState.NeedsContinue -> DraftAudio(identity = identity, needsContinue = true)
    is DraftAudioState.Generating -> DraftAudio(generating = true, identity = identity)
    is DraftAudioState.Ready -> DraftAudio(path = file.path, identity = identity)
    is DraftAudioState.Failed -> if (
        item == AudioItem.VOCABULARY && diagnostic?.category == "SOURCE_MISSING"
    ) {
        DraftAudio(sourceMissing = true, identity = identity)
    } else {
        DraftAudio(
            error = reviewAudioGenerationFailureMessage(
                context.getString(if (item == AudioItem.SENTENCE) R.string.card_sentence_audio_field else R.string.card_word_audio_field),
                message,
                context.getString(R.string.card_audio_no_diagnostic_details),
            ).resolve(context),
            identity = identity,
        )
    }
}

private data class SentenceAudioAuthorization(
    val revision: Long,
    val sentence: String,
    val voiceName: String,
)

private data class WordAudioAuthorization(
    val revision: Long,
    val target: TargetSpan,
)

data class SubmissionOutcome(val status: String, val noteId: Long? = null)

internal fun ankiBrowserUriText(noteId: Long): String {
    require(noteId > 0) { "Note ID must be positive." }
    return "anki://x-callback-url/browser?search=nid%3A$noteId"
}

internal fun ankiBrowserUri(noteId: Long): Uri = Uri.parse(ankiBrowserUriText(noteId))

internal fun ankiBrowserIntent(noteId: Long): Intent =
    Intent(Intent.ACTION_VIEW, ankiBrowserUri(noteId)).setPackage("com.ichi2.anki")

internal fun draftCallbackIsCurrent(
    callbackGeneration: Long,
    currentGeneration: Long,
    locked: Boolean,
): Boolean = !locked && callbackGeneration == currentGeneration

internal fun ocrResultIsCurrent(
    callbackGeneration: Long,
    currentGeneration: Long,
    callbackEditorRevision: Long,
    currentEditorRevision: Long,
    locked: Boolean,
): Boolean = draftCallbackIsCurrent(callbackGeneration, currentGeneration, locked) &&
    callbackEditorRevision == currentEditorRevision

internal fun intentPayloadMatches(
    currentText: String?,
    currentImage: String?,
    expectedText: String?,
    expectedImage: String?,
): Boolean = currentText == expectedText && currentImage == expectedImage

class MainActivity : ComponentActivity() {
    private val sharedText = mutableStateOf<String?>(null)
    private val sharedImage = mutableStateOf<Uri?>(null)
    private var ankiDroidInstalled by mutableStateOf(false)
    private var apiPermissionGranted by mutableStateOf(false)
    private lateinit var offlineDictionary: OfflineDictionary
    private lateinit var offlineAnalyzer: OfflineJapaneseAnalyzer
    private lateinit var japaneseOcr: JapaneseOcr
    private lateinit var ajtPitchRepository: AjtPitchRepository
    private var audioPlayer: MediaPlayer? = null
    private var audioLease: AudioFileLease? = null

    private fun stopAudioPlayback() {
        audioPlayer?.runCatching {
            if (isPlaying) stop()
            release()
        }
        audioPlayer = null
        audioLease?.close()
        audioLease = null
    }

    override fun onDestroy() {
        stopAudioPlayback()
        super.onDestroy()
    }

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedText.value = textFromIntent(intent)
        sharedImage.value = imageFromIntent(intent)
        offlineDictionary = sharedDictionary(this)
        offlineAnalyzer = OfflineJapaneseAnalyzer.shared(this)
        japaneseOcr = JapaneseOcr()
        ajtPitchRepository = AjtPitchRepository.shared(this)
        val preferenceStorage = AndroidPreferenceStorage(this)
        lifecycleScope.launch {
            preferenceStorage.load()
            if (preferenceStorage.draft.value.getString(SubmissionRecoveryKeys.STATUS, null) == "done") {
                withContext(Dispatchers.IO) {
                    clearPersistedDraftBlocking(preferenceStorage, File(filesDir, "draft"))
                }
            }
            setContent {
            val preferences by preferenceStorage.draft.collectAsState()
            val settings by preferenceStorage.settings.collectAsState()
            fun updateDraft(changes: PreferenceChanges): Boolean {
                preferenceStorage.update(PreferenceFile.DRAFT, changes)
                return true
            }
            fun updateSettings(changes: PreferenceChanges): Boolean {
                preferenceStorage.update(PreferenceFile.SETTINGS, changes)
                return true
            }
            val coroutineScope = rememberCoroutineScope()
            val cardScreenHolderRef = remember { arrayOfNulls<CardScreenStateHolder>(1) }
            val touchFeedbackDispatcher = LocalTouchFeedbackDispatcher.current
            var captureConfirmationMessage by remember { mutableStateOf<String?>(null) }
            var verifiedDelivery by remember { mutableStateOf(false) }
            val apiPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted -> apiPermissionGranted = granted }
            var editor by remember {
                mutableStateOf(
                    TextFieldValue(
                        text = preferences.getString("sentence", "").orEmpty(),
                        selection = TextRange(
                            preferences.getInt("selection_start", 0),
                            preferences.getInt("selection_end", 0),
                        ).coerceIn(0, preferences.getString("sentence", "").orEmpty().length),
                    ),
                )
            }
            val draftState = remember {
                mutableStateOf(
                    restoreDraftModel(
                        preferences = preferences,
                        sentence = editor.text,
                        defaultTags = settings.getString(SettingsPreferenceKeys.DEFAULT_TAGS, "").orEmpty(),
                    ),
                )
            }
            var target by draftState.field(DraftModel::target, DraftModel::withTarget)
            var vocabulary by draftState.field(DraftModel::vocabulary, DraftModel::withVocabulary)
            var vocabularyReading by draftState.field(DraftModel::vocabularyReading, DraftModel::withVocabularyReading)
            var pitchAccentText by draftState.field(DraftModel::pitchAccent, DraftModel::withPitchAccent)
            var definition by draftState.field(DraftModel::definition, DraftModel::withDefinition)
            var sentenceReading by draftState.field(DraftModel::sentenceReading, DraftModel::withSentenceReading)
            var readingCorrections by draftState.field(DraftModel::readingCorrections, DraftModel::withReadingCorrections)
            var notes by draftState.field(DraftModel::notes, DraftModel::withNotes)
            var tags by draftState.field(DraftModel::tags, DraftModel::withTags)
            val draft = draftState.value
            var vocabularyReadingError by remember { mutableStateOf<String?>(null) }
            var pitchHtmlText by remember { mutableStateOf(preferences.getString("pitch_html", null)) }
            var pitchIndexError by remember { mutableStateOf<String?>(null) }
            val pitch = pitchAccentText.toIntOrNull()?.let {
                PitchAccent.create(vocabularyReading, it, pitchHtmlText)
            }
            var definitionManuallyEdited by remember { mutableStateOf(preferences.getBoolean("definition_manual", false)) }
            var definitionProvenanceKnown by remember { mutableStateOf(preferences.contains("definition_manual")) }
            var dependentFieldsStale by remember {
                mutableStateOf(preferences.getBoolean("definition_manual_stale", false) &&
                    preferences.getBoolean("definition_manual", false))
            }
            var readingCorrectionError by remember { mutableStateOf<String?>(null) }
            var showDictionarySheet by remember { mutableStateOf(false) }
            val restoredTranslation = remember { restoreTranslationDraft(preferences) }
            var translationDraft by remember { mutableStateOf(restoredTranslation.draft) }
            val translation = (translationDraft.result as? TranslationResult.Ready)?.text.orEmpty()
            var translationAttemptedSentence by remember {
                mutableStateOf(restoredTranslation.attemptedSentence)
            }
            var translationAutoAllowed by remember {
                mutableStateOf(false)
            }
            var translationRetry by remember { mutableStateOf(0) }
            var lastHandledTranslationRetry by remember { mutableStateOf(0) }
            var translationRequestRevision by remember { mutableStateOf(0) }
            val currentTranslationRequestRevision = rememberUpdatedState(translationRequestRevision)
            // The shared Google Cloud key feeds translation too; Compose only sees results.
            val translationService = remember {
                TranslationService(credential = { GoogleCloudCredential.read(settings) })
            }
            val updateTranslationSentence: (String) -> Unit = { sentence ->
                translationRequestRevision++
                translationAutoAllowed = false
                translationDraft = translationDraft.sentenceChanged(sentence)
            }
            val activeTranslationResult: () -> TranslationResult = {
                if (translationDraft.manuallyEdited && translationDraft.stale) {
                    translationDraft.suggestion ?: TranslationResult.Pending
                } else translationDraft.result
            }
            val restoredPendingPhoto = preferences.getString("pending_replacement_image_path", null)
                ?.let(::File)?.takeIf(File::isFile)
            var showReplace by remember { mutableStateOf(restoredPendingPhoto != null) }
            var showSources by remember { mutableStateOf(false) }
            val previouslyConfigured = settings.contains(GoogleCloudCredential.PREF) &&
                settings.getString(SettingsPreferenceKeys.GOOGLE_VOICE, "").orEmpty().isNotBlank()
            var onboardingComplete by remember {
                mutableStateOf(settings.getBoolean("onboarding_complete", previouslyConfigured))
            }
            var showSetup by remember { mutableStateOf(!onboardingComplete) }
            var cameraDenied by remember { mutableStateOf(settings.getBoolean("onboarding_camera_denied", false)) }
            var showSettings by remember { mutableStateOf(false) }
            var showSentenceEditor by remember { mutableStateOf(false) }
            var sentenceEditorDraft by remember { mutableStateOf<TextFieldValue?>(null) }
            var showDeckSheet by remember { mutableStateOf(false) }
            var showDiscardConfirm by remember { mutableStateOf(false) }
            var audioCacheBytes by remember { mutableStateOf(0L) }
            var audioCacheBusy by remember { mutableStateOf(false) }
            var audioCacheMessage by remember { mutableStateOf<String?>(null) }
            var googleKey by remember { mutableStateOf("") }
            var googleKeyVisible by remember { mutableStateOf(false) }
            var translationTestRunning by remember { mutableStateOf(false) }
            var translationTestResult by remember { mutableStateOf<TranslationResult?>(null) }
            var selectedVoiceName by remember {
                mutableStateOf(settings.getString(SettingsPreferenceKeys.GOOGLE_VOICE, "").orEmpty())
            }
            var cloudVoices by remember { mutableStateOf(emptyList<CloudVoice>()) }
            var cloudVoicesCached by remember { mutableStateOf(false) }
            var cloudVoicesLoading by remember { mutableStateOf(false) }
            var cloudVoicesError by remember { mutableStateOf<String?>(null) }
            var voiceListAttempted by remember { mutableStateOf(false) }
            var voiceRefresh by remember { mutableStateOf(0) }
            var showVoiceMenu by remember { mutableStateOf(false) }
            var settingsLoading by remember { mutableStateOf(false) }
            var settingsSaving by remember { mutableStateOf(false) }
            var settingsError by remember { mutableStateOf<String?>(null) }
            var setupAudioErrorResource by remember { mutableStateOf<Int?>(null) }
            var audioSetupInvalidated by remember { mutableStateOf(false) }
            var settingsCoordinator by remember { mutableStateOf<SettingsCoordinator?>(null) }
            var settingsScreenState by remember {
                val cached = decodeVoices(settings.getString(SettingsPreferenceKeys.VOICE_CACHE, null))
                mutableStateOf(
                    SettingsScreenState(
                        values = SettingsValues(
                            modelId = settings.getLong(SettingsPreferenceKeys.MODEL_ID, -1L).takeIf { it > 0 },
                            deckId = settings.getLong(SettingsPreferenceKeys.DECK_ID, -1L).takeIf { it > 0 },
                            defaultTags = settings.getString(SettingsPreferenceKeys.DEFAULT_TAGS, "").orEmpty(),
                            voiceName = settings.getString(SettingsPreferenceKeys.GOOGLE_VOICE, "").orEmpty(),
                        ),
                        googleKey = "",
                        credentialStored = settings.contains(GoogleCloudCredential.PREF),
                        loading = true,
                        voiceCatalog = VoiceCatalogState(
                            voices = cached,
                            provenance = if (cached.isEmpty()) VoiceCatalogProvenance.NONE else VoiceCatalogProvenance.CACHED,
                        ),
                    ),
                )
            }
            var dictionaryRetry by remember { mutableStateOf(0) }
            var dictionaryReady by remember { mutableStateOf(false) }
            var dictionaryLoading by remember { mutableStateOf(false) }
            var dictionaryError by remember { mutableStateOf<String?>(null) }
            var dictionaryInfo by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
            var analyzerReady by remember { mutableStateOf(false) }
            var analyzerLoading by remember { mutableStateOf(false) }
            var analyzerFailure by remember { mutableStateOf<Int?>(null) }
            var analyzerRetry by remember { mutableStateOf(0) }
            val setupVisible = shouldShowSetup(
                showSetup = showSetup,
                onboardingComplete = onboardingComplete,
                analyzerReady = analyzerReady,
                showSettings = showSettings,
            )
            var tokenizationLoading by remember { mutableStateOf(false) }
            var tokenizationError by remember { mutableStateOf<String?>(null) }
            var analysisRetry by remember { mutableStateOf(0) }
            var japaneseTokens by remember { mutableStateOf<List<JapaneseToken>>(emptyList()) }
            val sentenceFurigana = SentenceReadings.effective(
                editor.text,
                japaneseTokens,
                target,
                sentenceReading,
                readingCorrections,
            )
            val correctableReadings = SentenceReadings.correctable(
                editor.text,
                japaneseTokens,
                target,
                sentenceReading,
                readingCorrections,
            )
            val analyzedTarget = target?.let { resolveAnalyzedLemma(japaneseTokens, it) }
            var dictionaryEntries by remember { mutableStateOf<List<DictionaryEntry>>(emptyList()) }
            var dictionarySearchLoading by remember { mutableStateOf(false) }
            var dictionaryHasMore by remember { mutableStateOf(false) }
            var selectedDictionaryEntry by remember { mutableStateOf<DictionaryEntry?>(null) }
            var selectedDictionaryEntryId by remember {
                mutableStateOf(preferences.getLong("dictionary_entry_id", -1).takeIf { it > 0 })
            }
            var selectedSenseIndices by remember {
                mutableStateOf(
                    preferences.getStringSet("dictionary_sense_indices", null)
                        ?.mapNotNull(String::toIntOrNull)?.toSet() ?: setOf(0),
                )
            }
            var pitchCandidates by remember { mutableStateOf<List<AjtPitchRecord>>(emptyList()) }
            var pitchLookupLoading by remember { mutableStateOf(false) }
            // Observable, process-wide AJT availability: a late Ready or a retry re-keys the query.
            val ajtAvailability by ajtPitchRepository.availability.collectAsState()
            var noteTypes by remember { mutableStateOf<List<AnkiNoteType>>(emptyList()) }
            var decks by remember { mutableStateOf<List<AnkiDeck>>(emptyList()) }
            var setupLoading by remember { mutableStateOf(false) }
            var setupLoadedSuccessfully by remember { mutableStateOf(false) }
            var setupError by remember { mutableStateOf<String?>(null) }
            var setupReload by remember { mutableStateOf(0) }
            var selectedModelId by remember {
                mutableStateOf(
                    settings.getLong(SettingsPreferenceKeys.MODEL_ID, -1).takeIf { it > 0 }
                        ?: preferences.getLong("model_id", -1).takeIf { it > 0 },
                )
            }
            var selectedDeckId by remember {
                mutableStateOf(
                    settings.getLong(SettingsPreferenceKeys.DECK_ID, -1).takeIf { it > 0 }
                        ?: preferences.getLong("deck_id", -1).takeIf { it > 0 },
                )
            }
            var captureMode by remember { mutableStateOf(preferences.getString("capture_mode", "Photo") ?: "Photo") }
            var generationStarted by remember { mutableStateOf(false) }
            var cardInvalidated by remember {
                mutableStateOf(preferences.getBoolean("card_invalidated", false))
            }
            var committedSentence by remember {
                mutableStateOf(preferences.getString("committed_sentence", null))
            }
            var committedSentenceVoice by remember {
                mutableStateOf(preferences.getString("committed_sentence_voice", selectedVoiceName))
            }
            var reviewAuthorizationSequence by remember {
                mutableStateOf(preferences.getLong("review_authorization_sequence", 0L).coerceAtLeast(0L))
            }
            var pendingSentenceAudioAuthorization by remember { mutableStateOf<SentenceAudioAuthorization?>(null) }
            var pendingWordAudioAuthorization by remember { mutableStateOf<WordAudioAuthorization?>(null) }
            var handledSentenceAudioAuthorization by remember { mutableStateOf<Long?>(null) }
            var handledWordAudioAuthorization by remember { mutableStateOf<Long?>(null) }
            var preparationAuthorizationError by remember { mutableStateOf<String?>(null) }
            var playingAudio by remember { mutableStateOf<AudioItem?>(null) }
            var audioPlaybackErrors by remember { mutableStateOf<Map<AudioItem, String>>(emptyMap()) }
            var audioConfigErrors by remember { mutableStateOf<Map<AudioItem, String>>(emptyMap()) }
            var audioInputRevision by remember { mutableStateOf(0L) }
            var audioControllerGeneration by remember { mutableStateOf(0L) }

            val speechProvider: SentenceSpeechProvider = remember {
                GoogleChirpSpeechProvider(
                    credential = { GoogleCloudCredential.read(settings) },
                )
            }
            val recordedWordSource = remember {
                Nhk2016PronunciationSource(
                    readIndexText = {
                        applicationContext.assets.open(Nhk2016PronunciationSource.ASSET_NAME)
                            .bufferedReader(Charsets.UTF_8).use { it.readText() }
                    },
                )
            }
            val reusableAudioCache = remember { ReusableAudioCache(File(cacheDir, "audio")) }
            val draftAudioDirectory = remember { File(filesDir, "draft").apply { mkdirs() } }
            // Finals stay direct children of filesDir/draft (FileProvider + Anki import root);
            // staging is private and outside the exported directory.
            val draftAudioStore = remember {
                DraftAudioFileStore(
                    finalsDirectory = draftAudioDirectory,
                    stagingDirectory = File(filesDir, "audio-staging"),
                )
            }
            val audioPersistence = remember { PreferenceDraftAudioPersistence(preferenceStorage) }
            val audioController = remember(audioControllerGeneration) {
                DraftAudioController(
                    store = draftAudioStore,
                    persistence = audioPersistence,
                    cache = reusableAudioCache,
                    synthesize = { input, destination ->
                        when (val payload = input.providerPayload) {
                            is SentenceSpeechRequest -> speechProvider.synthesize(payload, destination)
                            is AudioSynthesisRequest -> recordedWordSource.synthesize(payload, destination)
                            else -> throw IllegalStateException(
                                "No audio provider is configured for this draft item.",
                            )
                        }
                        Unit
                    },
                    legacyPaths = mapOf(
                        AudioItem.SENTENCE to preferences.getString("sentence_audio_path", null),
                        AudioItem.VOCABULARY to preferences.getString("word_audio_path", null),
                    ),
                )
            }
            val sendCoordinator = remember(audioController) {
                SendCoordinator(
                    gateway = AnkiDroidGateway(contentResolver, applicationContext),
                    storage = preferenceStorage,
                    audio = audioController,
                    repository = PreferenceFrozenSubmissionStore(
                        storage = preferenceStorage,
                        draftDirectory = File(filesDir, "draft"),
                    ),
                )
            }
            val preparationAuthorization = remember {
                PreparationAuthorization(
                    initialCommittedSentence = committedSentence,
                    persistSentenceCommit = { sentence ->
                        val nextRevision = runCatching { Math.addExact(reviewAuthorizationSequence, 1L) }
                            .getOrNull() ?: return@PreparationAuthorization false
                        val saved = updateDraft(PreferenceChanges()
                            .putString("committed_sentence", sentence)
                            .putString("sentence", sentence)
                            .putString("committed_sentence_voice", selectedVoiceName)
                            .putLong("review_authorization_sequence", nextRevision)
                            )
                        if (saved) {
                            committedSentence = sentence
                            committedSentenceVoice = selectedVoiceName
                            cardInvalidated = true
                            reviewAuthorizationSequence = nextRevision
                        }
                        saved
                    },
                    authorizeTranslation = {
                        translationRequestRevision++
                        translationAutoAllowed = true
                        generationStarted = true
                    },
                    authorizeSentenceSpeech = { sentence ->
                        pendingSentenceAudioAuthorization = SentenceAudioAuthorization(
                            revision = reviewAuthorizationSequence,
                            sentence = sentence,
                            voiceName = selectedVoiceName,
                        )
                    },
                    authorizeWordRecording = { identity, selected ->
                        if (identity.isBlank()) return@PreparationAuthorization false
                        val nextRevision = runCatching { Math.addExact(reviewAuthorizationSequence, 1L) }
                            .getOrNull() ?: return@PreparationAuthorization false
                        if (!updateDraft(PreferenceChanges().putLong("review_authorization_sequence", nextRevision))) {
                            false
                        } else {
                            reviewAuthorizationSequence = nextRevision
                            pendingWordAudioAuthorization = WordAudioAuthorization(nextRevision, selected)
                            true
                        }
                    },
                )
            }
            androidx.compose.runtime.DisposableEffect(audioController) {
                onDispose { audioController.dispose() }
            }
            var audioSnapshot by remember(audioController) { mutableStateOf(audioController.snapshot()) }
            val currentWordIdentity = rememberUpdatedState(
                DraftAudioIdentity.word(
                    selectedDictionaryEntry?.takeIf {
                        it.id == selectedDictionaryEntryId && it.expression == vocabulary && it.reading == vocabularyReading
                    }?.id,
                    vocabulary,
                    vocabularyReading,
                ),
            )
            // A reading, sentence or voice change invalidates the sentence recording; a vocabulary
            // reading change also re-resolves the word recording through its own identity.
            val currentSentenceIdentity = rememberUpdatedState(
                DraftAudioIdentity.sentenceRequest(
                    editor.text,
                    sentenceHintSpans(editor.text, japaneseTokens, readingCorrections),
                    selectedVoiceName,
                ),
            )
            androidx.compose.runtime.LaunchedEffect(selectedVoiceName, committedSentence, committedSentenceVoice) {
                if (!committedSentence.isNullOrBlank() && committedSentenceVoice != selectedVoiceName) {
                    cardInvalidated = true
                }
            }

            fun audioView(item: AudioItem): DraftAudio {
                audioConfigErrors[item]?.let {
                    val field = this@MainActivity.getString(
                        if (item == AudioItem.SENTENCE) R.string.card_sentence_audio_field else R.string.card_word_audio_field,
                    )
                    return DraftAudio(
                        advisoryHeadline = this@MainActivity.getString(R.string.card_audio_setup_needed_headline, field),
                        advisoryMessage = it.substringBefore(". ").trimEnd('.') + ".",
                    )
                }
                audioPlaybackErrors[item]?.let { return DraftAudio(error = it) }
                return (audioSnapshot.items[item] ?: DraftAudioState.Idle).toDraftAudio(item, this@MainActivity)
            }

            fun audioReady(item: AudioItem, identity: String): Boolean {
                if (audioConfigErrors[item] != null || audioPlaybackErrors[item] != null) return false
                val state = audioSnapshot.items[item] as? DraftAudioState.Ready ?: return false
                return state.identity == identity && state.file.isIntact()
            }

            fun runAudioAttempt(attempt: DraftAudioAttempt?) {
                if (attempt == null) return
                audioSnapshot = audioController.snapshot()
                coroutineScope.launch {
                    when (val result = withContext(Dispatchers.IO) { audioController.runAttempt(attempt) }) {
                        is DraftAudioAttemptResult.Accepted -> Log.i(
                            "DraftAudio",
                            "${attempt.input.item.name} audio accepted fromCache=${result.fromCache}",
                        )
                        is DraftAudioAttemptResult.Failed -> Log.e(
                            "DraftAudio",
                            "${attempt.input.item.name} audio generation failed " +
                                "category=${result.diagnostic?.category ?: "UNKNOWN"} retryable=${result.retryable}",
                        )
                        DraftAudioAttemptResult.Stale -> Log.i(
                            "DraftAudio",
                            "${attempt.input.item.name} audio attempt became stale",
                        )
                    }
                    audioSnapshot = audioController.snapshot()
                }
            }

            // One pass over both owned roots, ownership-based, before any new generation. Only
            // recognised draft audio nobody references (and no live preference names) is deleted.
            androidx.compose.runtime.LaunchedEffect(audioController) {
                withContext(Dispatchers.IO) {
                    val report = draftAudioStore.sweep(
                        audioController.protectedPaths(
                            listOfNotNull(
                                preferences.getString("sentence_audio_path", null),
                                preferences.getString("word_audio_path", null),
                                preferences.getString(SubmissionRecoveryKeys.SENTENCE_AUDIO_PATH, null),
                                preferences.getString(SubmissionRecoveryKeys.WORD_AUDIO_PATH, null),
                                preferences.getString(DraftAudioPreferences.frozenPath(AudioItem.SENTENCE), null),
                                preferences.getString(DraftAudioPreferences.frozenPath(AudioItem.VOCABULARY), null),
                            ),
                        ),
                    )
                    Log.i(
                        "DraftAudio",
                        "retired=${report.deleted.size} kept=${report.protectedFiles.size} " +
                            "unknown=${report.retainedUnknown.size} failed=${report.failures.size}",
                    )
                }
            }

            val sentenceAudio = audioView(AudioItem.SENTENCE)
            val wordAudio = audioView(AudioItem.VOCABULARY)

            var route by remember {
                mutableStateOf(
                    when (preferences.getString("route", null)) {
                        "Capture" -> AppRoute.Capture
                        "Target", "Review", "Card" -> AppRoute.Card
                        else -> if (editor.text.isBlank()) AppRoute.Capture else AppRoute.Card
                    },
                )
            }
            var photoSourcePath by remember {
                mutableStateOf(preferences.getString("photo_source_path", null)?.takeIf { File(it).isFile })
            }
            var noteImagePath by remember {
                mutableStateOf(preferences.getString("note_image_path", null)?.takeIf { File(it).isFile })
            }
            var processedImagePath by remember {
                mutableStateOf(preferences.getString("processed_image_path", null)?.takeIf { File(it).isFile })
            }
            var draftOrigin by remember {
                mutableStateOf(preferences.getString("draft_origin", "Type") ?: "Type")
            }
            var imageProcessing by remember { mutableStateOf(false) }
            var imageProcessingRevision by remember { mutableStateOf(0L) }
            var imageError by remember { mutableStateOf<String?>(null) }
            var ocrLoading by remember { mutableStateOf(false) }
            var ocrError by remember { mutableStateOf<String?>(null) }
            var ocrChoices by remember { mutableStateOf<List<OcrBlock>>(emptyList()) }
            var showOcrSheet by remember { mutableStateOf(false) }
            var ocrImageHeight by remember { mutableStateOf(0) }
            var photoFocusY by remember { mutableStateOf(0.5f) }
            var pendingPhotoFile by remember { mutableStateOf(restoredPendingPhoto) }
            var pendingSharedImageUri by remember { mutableStateOf<Uri?>(null) }
            var cameraPermissionGranted by remember {
                mutableStateOf(checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
            }
            fun compatibleSetupNoteTypes(): List<AnkiNoteType> = noteTypes.filter { model ->
                AnkiFieldContract.mapToModelOrder(model.fields, emptyMap()).isValid
            }
            val setupReadiness = SettingsCoordinator.setupReadiness(
                audioCredentialAvailable = settingsScreenState.googleKey.isNotBlank() ||
                    settingsScreenState.credentialStored,
                selectedVoiceName = settingsScreenState.values.voiceName,
                ankiDroidInstalled = ankiDroidInstalled,
                ankiPermissionGranted = apiPermissionGranted,
                ankiSetupLoaded = setupLoadedSuccessfully,
                selectedCompatibleNoteType = compatibleSetupNoteTypes().any { it.id == selectedModelId },
                selectedDeckAvailable = decks.any { it.id == selectedDeckId },
                dictionaryReady = analyzerReady,
            )
            fun markCameraStepDone() {
                updateSettings(PreferenceChanges()
                    .putBoolean("onboarding_camera_done", true)
                    .putBoolean("onboarding_camera_denied", false)
                    )
                cameraDenied = false
            }
            fun enterSetup() {
                showSetup = true
            }
            var cameraError by remember { mutableStateOf<String?>(null) }
            var captureInProgress by remember { mutableStateOf(false) }
            var showModelMenu by remember { mutableStateOf(false) }
            var showDeckMenu by remember { mutableStateOf(false) }
            var submitMessage by remember { mutableStateOf<String?>(null) }
            val restoredPhase = SubmissionPhase.fromPersisted(
                preferences.getString(SubmissionRecoveryKeys.STATUS, null),
            )
            var sendState by remember {
                mutableStateOf(
                    SendState(
                        phase = when (restoredPhase) {
                            null -> SendPhase.Idle
                            SubmissionPhase.DESTINATION_VERIFIED -> SendPhase.Verified
                            else -> SendPhase.Inserted
                        },
                        frozen = if (restoredPhase != null) readFrozenSubmission(preferences) else null,
                        recovery = when (restoredPhase) {
                            SubmissionPhase.MEDIA_IMPORTING -> RecoveryReason.MediaInterrupted
                            SubmissionPhase.MEDIA_IMPORTED -> RecoveryReason.MediaImported
                            else -> RecoveryReason.None
                        },
                        message =
                    when (restoredPhase) {
                        SubmissionPhase.MEDIA_IMPORTING -> {
                            val operation = preferences.getString(SubmissionRecoveryKeys.MEDIA_OPERATION, null)
                                ?.let { name -> runCatching { MediaImportOperation.valueOf(name) }.getOrNull() }
                            this@MainActivity.getString(
                                R.string.card_send_recovery_media_preparation_interrupted,
                                operation?.label?.replaceFirstChar { it.uppercase() }
                                    ?: this@MainActivity.getString(R.string.card_media_preparation),
                            )
                        }
                        SubmissionPhase.MEDIA_IMPORTED -> this@MainActivity.getString(R.string.card_send_recovery_media_receipts_recorded)
                        SubmissionPhase.NOTE_INSERTING -> this@MainActivity.getString(R.string.card_send_recovery_previous_send_interrupted)
                        SubmissionPhase.NOTE_INSERTED -> this@MainActivity.getString(R.string.card_send_recovery_note_saved_verifying_cards)
                        SubmissionPhase.DESTINATION_VERIFIED -> this@MainActivity.getString(R.string.card_send_recovery_destination_verified_initial)
                        null -> null
                    },
                    ),
                )
            }
            fun updateRecoveryMessage(
                message: String?,
                reason: RecoveryReason = sendState.recovery,
                phase: SendPhase = sendState.phase,
            ) {
                sendState = sendState.copy(message = message, recovery = reason, phase = phase)
            }
            val isSubmitting = sendState.submitting
            val duplicateBlocked = sendState.duplicateBlocked
            val recoveryRequired = sendState.recoveryRequired
            val frozenRetryReady = sendState.retryReady
            val recoveryDuplicate = sendState.recovery == RecoveryReason.MatchingNoteExists && sendState.phase != SendPhase.Idle
            val recoveryMessage = sendState.message
            val completedSubmission = sendState.completedSubmission
            val knownNoteId = sendState.duplicateNoteId ?: sendState.frozen?.noteId.takeIf { sendState.recoveryRequired }
            val recoveryPhase = when (sendState.recovery) {
                RecoveryReason.MediaInterrupted -> SubmissionPhase.MEDIA_IMPORTING
                RecoveryReason.MediaImported -> SubmissionPhase.MEDIA_IMPORTED
                RecoveryReason.MatchingNoteExists -> SubmissionPhase.NOTE_INSERTING
                RecoveryReason.SavedNoteMissing,
                RecoveryReason.DestinationUnavailable,
                RecoveryReason.Unverifiable -> when {
                    sendState.phase == SendPhase.Verified -> SubmissionPhase.DESTINATION_VERIFIED
                    sendState.frozen?.noteId != null -> SubmissionPhase.NOTE_INSERTED
                    else -> restoredPhase ?: SubmissionPhase.NOTE_INSERTING
                }
                RecoveryReason.None -> when {
                    sendState.phase == SendPhase.Verified && sendState.completedNoteId == null ->
                        SubmissionPhase.DESTINATION_VERIFIED
                    sendState.phase == SendPhase.Inserted && sendState.frozen?.noteId != null ->
                        SubmissionPhase.NOTE_INSERTED
                    sendState.phase == SendPhase.Inserted -> restoredPhase ?: SubmissionPhase.NOTE_INSERTING
                    else -> null
                }
            }
            var pitchInfoExpanded by remember { mutableStateOf(false) }
            var frozenSnapshotAppliedRevision by remember { mutableStateOf(-1L) }
            var draftGeneration by remember { mutableStateOf(0L) }
            var editorInputRevision by remember { mutableStateOf(0L) }
            var setupGeneration by remember { mutableStateOf(0L) }
            val selectedModel = noteTypes.firstOrNull { it.id == selectedModelId }
            val selectedDeck = decks.firstOrNull { it.id == selectedDeckId }
            val incoming = sharedText.value
            val blockedStatusPrefix = stringResource(R.string.card_blocked)
            val navigationLocked = sendState.locked || captureInProgress
            val currentSubmissionLocked = rememberUpdatedState(sendState.locked)
            val currentDraftGeneration = rememberUpdatedState(draftGeneration)
            val currentEditorInputRevision = rememberUpdatedState(editorInputRevision)
            val currentSetupGeneration = rememberUpdatedState(setupGeneration)
            val currentSetupReload = rememberUpdatedState(setupReload)
            fun asyncDraftIsCurrent(generation: Long): Boolean = draftCallbackIsCurrent(
                generation,
                currentDraftGeneration.value,
                currentSubmissionLocked.value,
            )
            fun asyncSetupIsCurrent(generation: Long): Boolean =
                !currentSubmissionLocked.value && currentSetupGeneration.value == generation

            fun syncSettingsScreen(coordinator: SettingsCoordinator, loading: Boolean = false) {
                val values = coordinator.currentValues()
                val catalog = coordinator.currentVoiceCatalog()
                settingsScreenState = SettingsScreenState(
                    values = values,
                    googleKey = coordinator.currentGoogleKey(),
                    credentialStored = coordinator.isCredentialStored(),
                    loading = loading,
                    error = coordinator.currentSaveError() ?: settingsError,
                    voiceCatalog = catalog,
                    ankiConnected = ankiDroidInstalled && apiPermissionGranted,
                    ankiInstalled = ankiDroidInstalled,
                    onboardingIncomplete = !onboardingComplete,
                        cacheBusy = audioCacheBusy,
                    cacheMessage = audioCacheMessage,
                )
                selectedModelId = values.modelId
                selectedDeckId = values.deckId
                selectedVoiceName = values.voiceName
                cloudVoices = catalog.voices
                cloudVoicesCached = catalog.hasCachedList
                cloudVoicesLoading = catalog.loading
                cloudVoicesError = catalog.error
                voiceListAttempted = catalog.attempted
            }

            fun startVoiceList(coordinator: SettingsCoordinator, request: VoiceLoadRequest) {
                syncSettingsScreen(coordinator, loading = settingsLoading)
                coroutineScope.launch {
                    val voices = try {
                        withContext(Dispatchers.IO) { speechProvider.listVoices() }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        null
                    }
                    val safeError = if (voices == null) {
                        "Could not load voices."
                    } else null
                    withContext(Dispatchers.IO) {
                        coordinator.completeVoiceLoad(request, voices = voices, safeError = safeError)
                    }
                    syncSettingsScreen(coordinator, loading = settingsLoading)
                }
            }

            fun openSettings() {
                if (navigationLocked || showSettings) return
                settingsLoading = true
                settingsSaving = false
                settingsError = null
                googleKeyVisible = false
                translationTestResult = null
                translationTestRunning = false
                showSettings = true
            }

            suspend fun flushSettings(coordinator: SettingsCoordinator): Result<Unit> = try {
                withContext(Dispatchers.IO) { coordinator.flush() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Result.failure(error)
            } finally {
                // A failed or cancelled flush must not leave every Settings control disabled.
                settingsSaving = false
            }

            fun closeSettings(resumeSetup: Boolean = false) {
                if (!showSettings || settingsSaving) return
                val coordinator = settingsCoordinator
                if (coordinator == null || settingsLoading) {
                    settingsError = this@MainActivity.getString(R.string.legacy_settings_settings_are_still_loading_back_out_again)
                    return
                }
                settingsSaving = true
                coroutineScope.launch {
                    val result = flushSettings(coordinator)
                    if (result.isSuccess) {
                        syncSettingsScreen(coordinator)
                        googleKey = coordinator.currentGoogleKey()
                        settingsCoordinator = null
                        showSettings = false
                        if (resumeSetup || audioSetupInvalidated) {
                            audioSetupInvalidated = false
                            enterSetup()
                        }
                    } else {
                        settingsError = this@MainActivity.getString(R.string.save_failed)
                        syncSettingsScreen(coordinator)
                    }
                }
            }

            fun finishSetup() {
                if (settingsLoading || settingsSaving) return
                if (!setupReadiness.dictionaryReady || !setupReadiness.audioReady || !setupReadiness.ankiReady) return
                val coordinator = settingsCoordinator
                if (coordinator == null) {
                    setupAudioErrorResource = R.string.save_failed
                    return
                }
                setupAudioErrorResource = null
                settingsSaving = true
                coroutineScope.launch {
                    val result = flushSettings(coordinator)
                    syncSettingsScreen(coordinator)
                    if (result.isFailure) {
                        setupAudioErrorResource = R.string.save_failed
                        return@launch
                    }
                    if (!coordinator.isCredentialStored()) {
                        setupAudioErrorResource = R.string.key_required
                        return@launch
                    }
                    if (!coordinator.canFinishOnboarding()) {
                        val catalog = coordinator.currentVoiceCatalog()
                        setupAudioErrorResource = if (catalog.voices.isEmpty()) {
                            R.string.voices_failed
                        } else {
                            R.string.voice_unavailable
                        }
                        return@launch
                    }
                    val persisted = updateSettings(PreferenceChanges()
                        .putBoolean("onboarding_anki_done", true)
                        .putBoolean("onboarding_anki_deferred", false)
                        .putBoolean("onboarding_complete", true)
                        )
                    if (!persisted) {
                        setupAudioErrorResource = R.string.save_failed
                        return@launch
                    }
                    onboardingComplete = true
                    showSetup = false
                }
            }

            fun continueToCard() {
                if (!analyzerReady) return
                if (navigationLocked) return
                preparationAuthorizationError = null
                route = AppRoute.Card
            }

            fun commitSentence(sentence: String = editor.text): Boolean {
                if (navigationLocked || sentence.isBlank()) return false
                preparationAuthorizationError = null
                if (committedSentence == sentence) return true
                val committed = preparationAuthorization.commitSentence(sentence)
                if (!committed && committedSentence != sentence) {
                    preparationAuthorizationError = "Could not record the sentence commit. Try committing it again."
                }
                return committed
            }

            val hasManualDefinition = definitionManuallyEdited && definition.isNotBlank()
            val persistLastSentTags: (FrozenSubmission) -> Unit = { snapshot ->
                check(updateSettings(PreferenceChanges().putString("last_note_tags", snapshot.tags.joinToString(" ")))) {
                    this@MainActivity.getString(R.string.card_tags_from_confirmed_note_save_failed)
                }
            }
            val clearDraft = {
                val showDeliveryConfirmation = verifiedDelivery
                captureConfirmationMessage = if (showDeliveryConfirmation) {
                    this@MainActivity.getString(R.string.card_note_sent_to_ankidroid)
                } else {
                    null
                }
                if (showDeliveryConfirmation) {
                    captureMode = "Photo"
                    updateDraft(PreferenceChanges().putString("capture_mode", "Photo"))
                }
                verifiedDelivery = false
                listOfNotNull(photoSourcePath, noteImagePath, processedImagePath).distinct().forEach { File(it).delete() }
                editorInputRevision++
                editor = TextFieldValue("")
                draftState.value = draftState.value.cleared(
                    normalizeTagInput(settings.getString(SettingsPreferenceKeys.DEFAULT_TAGS, "").orEmpty()),
                )
                route = AppRoute.Capture
                photoSourcePath = null
                noteImagePath = null
                processedImagePath = null
                preferenceStorage.update(PreferenceFile.DRAFT, draftClearChanges())
                sendState = SendState(message = sendState.message)
                submitMessage = null
                frozenSnapshotAppliedRevision = -1L
                draftGeneration++
                imageProcessingRevision++
                imageProcessing = false
                imageError = null
                ocrLoading = false
                ocrError = null
                ocrChoices = emptyList()
                showOcrSheet = false
                dictionaryRetry++
                analysisRetry++
                setupGeneration++
                dictionaryLoading = false
                dictionarySearchLoading = false
                analyzerLoading = false
                tokenizationLoading = false
                pitchLookupLoading = false
                japaneseTokens = emptyList()
                tokenizationError = null
                definitionManuallyEdited = false
                definitionProvenanceKnown = true
                dependentFieldsStale = false
                translationDraft = TranslationDraft("", TranslationResult.Pending)
                translationAttemptedSentence = null
                translationAutoAllowed = false
                translationRetry = 0
                lastHandledTranslationRetry = 0
                translationRequestRevision++
                committedSentence = null
                committedSentenceVoice = selectedVoiceName
                cardInvalidated = false
                preparationAuthorization.reset()
                reviewAuthorizationSequence = 0L
                pendingSentenceAudioAuthorization = null
                pendingWordAudioAuthorization = null
                handledSentenceAudioAuthorization = null
                handledWordAudioAuthorization = null
                selectedDictionaryEntry = null
                selectedDictionaryEntryId = null
                selectedSenseIndices = emptySet()
                generationStarted = false
                playingAudio = null
                audioPlaybackErrors = emptyMap()
                audioConfigErrors = emptyMap()
                stopAudioPlayback()
                // Release any Send freeze first: a retained freeze blocks discard by design.
                audioController.releaseFreeze(retainFiles = false)
                audioController.discard()
                audioControllerGeneration++
                audioSnapshot = audioController.snapshot()
                updateDraft(PreferenceChanges()
                    .remove(SENTENCE_AUDIO_RECEIPT_PATH)
                    .remove(SENTENCE_AUDIO_RECEIPT_FILENAME)
                    .remove(SENTENCE_AUDIO_RECEIPT_SIZE)
                    .remove(SENTENCE_AUDIO_RECEIPT_MODIFIED)
                    .remove(WORD_AUDIO_RECEIPT_PATH)
                    .remove(WORD_AUDIO_RECEIPT_FILENAME)
                    .remove(WORD_AUDIO_RECEIPT_SIZE)
                    .remove(WORD_AUDIO_RECEIPT_MODIFIED)
                    .remove("sentence_audio_path")
                    .remove("word_audio_path")
                    .remove("committed_sentence")
                    .remove("committed_sentence_voice")
                    .remove("card_invalidated")
                    .remove("review_authorization_sequence")
                    )
            }
            val replaceEditorText: (String, Boolean) -> Unit = { text, invalidateDraftGeneration ->
                editorInputRevision++
                if (invalidateDraftGeneration) draftGeneration++
                if (text != editor.text) {
                    cardInvalidated = true
                    generationStarted = false
                    if (sendState.locked) translationRequestRevision++ else updateTranslationSentence(text)
                    draftState.value = draftState.value.withSentence(text)
                }
                editor = TextFieldValue(text, selection = TextRange(text.length))
            }

            val discardSharePayload = {
                val rawText = textFromIntent(intent)
                val rawImage = imageFromIntent(intent)
                val expectedText = rawText ?: sharedText.value
                val expectedImage = rawImage ?: sharedImage.value ?: pendingSharedImageUri
                if (clearConsumedShareIntent(expectedText, expectedImage) || (rawText == null && rawImage == null)) {
                    sharedText.value = null
                    sharedImage.value = null
                    pendingSharedImageUri = null
                    draftGeneration++
                    imageProcessingRevision++
                    ocrLoading = false
                }
            }

            androidx.compose.runtime.LaunchedEffect(completedSubmission) {
                if (completedSubmission != null) clearDraft()
            }
            androidx.compose.runtime.LaunchedEffect(captureConfirmationMessage) {
                val message = captureConfirmationMessage ?: return@LaunchedEffect
                kotlinx.coroutines.delay(1_400)
                if (captureConfirmationMessage == message) captureConfirmationMessage = null
            }

            val targetSelectionCoordinator = CardTargetSelectionCoordinator(
                snapshot = {
                    CardTargetSelectionSnapshot(
                        target = target,
                        sentence = editor.text,
                        tokens = japaneseTokens,
                        vocabulary = vocabulary,
                        vocabularyReading = vocabularyReading,
                        locked = currentSubmissionLocked.value,
                        hasManualDefinition = hasManualDefinition,
                    )
                },
                invalidateCard = { cardInvalidated = true },
                markDefinitionStale = { dependentFieldsStale = true },
                resetDictionarySelection = {
                    selectedDictionaryEntry = null
                    selectedDictionaryEntryId = null
                    dictionaryEntries = emptyList()
                    dictionarySearchLoading = false
                    dictionaryError = null
                    dictionaryHasMore = false
                    selectedSenseIndices = emptySet()
                    vocabularyReadingError = null
                    definitionManuallyEdited = false
                    definitionProvenanceKnown = true
                    audioController.clear(AudioItem.VOCABULARY)
                    audioSnapshot = audioController.snapshot()
                },
                setTarget = { target = it },
                setSentenceReading = { sentenceReading = it },
                authorizeWord = { identity, span -> preparationAuthorization.selectWord(identity, span) },
                setVocabulary = { vocabulary = it },
                setVocabularyReading = { vocabularyReading = it },
                resolveSurfaceReading = ::resolveSurfaceReading,
                resolveAnalyzedLemma = ::resolveAnalyzedLemma,
                containsKanji = ::containsKanji,
            )
            val selectTarget: (TargetSpan?) -> Unit = targetSelectionCoordinator::select
            val applyOcrText: (String, Float) -> Unit = applyOcrText@{ text, focusY ->
                if (currentSubmissionLocked.value) return@applyOcrText
                selectTarget(null)
                replaceEditorText(text.trim(), false)
                photoFocusY = focusY.coerceIn(0f, 1f)
                japaneseTokens = emptyList()
                tokenizationError = null
                tokenizationLoading = analyzerReady
                ocrError = null
                route = AppRoute.Card
                commitSentence(text.trim())
            }
            val setDraftOrigin: (String) -> Unit = { origin ->
                if (draftOrigin != origin) {
                    draftOrigin = origin
                    updateDraft(PreferenceChanges().putString("draft_origin", origin))
                }
            }
            val processImage: (File, Boolean) -> Unit = processImage@{ file, runOcr ->
                if (currentSubmissionLocked.value) return@processImage
                val callbackGeneration = draftGeneration
                val callbackEditorRevision = editorInputRevision
                val previousFiles = listOfNotNull(noteImagePath, processedImagePath)
                    .distinct().filter { it != file.absolutePath }
                ocrChoices = emptyList()
                showOcrSheet = false
                if (!runOcr && photoSourcePath == noteImagePath) photoSourcePath = null
                if (runOcr) photoSourcePath = file.absolutePath
                if (runOcr) {
                    draftOrigin = "Photo"
                }
                noteImagePath = file.absolutePath
                processedImagePath = null
                check(
                    updateDraft(PreferenceChanges()
                        .remove(IMAGE_RECEIPT_PATH)
                        .remove(IMAGE_RECEIPT_FILENAME)
                        .remove(IMAGE_RECEIPT_SIZE)
                        .remove(IMAGE_RECEIPT_MODIFIED)
                        .putString("photo_source_path", photoSourcePath)
                        .putString("note_image_path", noteImagePath)
                        .putString("draft_origin", draftOrigin)
                        .remove("processed_image_path")
                        ),
                ) { this@MainActivity.getString(R.string.legacy_capture_draft_image_source_save_failed) }
                imageError = null
                imageProcessing = true
                val processingRevision = ++imageProcessingRevision
                val destination = File(filesDir, "draft/${java.util.UUID.randomUUID()}.webp")
                coroutineScope.launch {
                    try {
                        val processed = withContext(Dispatchers.IO) {
                            ImageMediaProcessor.process(file, destination)
                        }
                        if (asyncDraftIsCurrent(callbackGeneration) && noteImagePath == file.absolutePath && imageProcessingRevision == processingRevision) {
                            processedImagePath = processed.file.absolutePath
                            previousFiles.forEach { File(it).delete() }
                        } else {
                            processed.file.delete()
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        if (asyncDraftIsCurrent(callbackGeneration) && noteImagePath == file.absolutePath && imageProcessingRevision == processingRevision) {
                            imageError = this@MainActivity.getString(R.string.legacy_capture_this_photo_could_not_be_prepared_for_anki)
                        }
                    } finally {
                        if (noteImagePath == file.absolutePath && imageProcessingRevision == processingRevision) {
                            imageProcessing = false
                        }
                    }
                }
                if (runOcr) {
                    ocrLoading = true
                    ocrError = null
                    coroutineScope.launch {
                        try {
                            val result = japaneseOcr.recognize(file)
                            if (!ocrResultIsCurrent(
                                    callbackGeneration,
                                    currentDraftGeneration.value,
                                    callbackEditorRevision,
                                    currentEditorInputRevision.value,
                                    currentSubmissionLocked.value,
                                ) || imageProcessingRevision != processingRevision || noteImagePath != file.absolutePath
                            ) return@launch
                            val blocks = result.blocks.filter { it.text.isNotBlank() }
                            ocrImageHeight = result.sourceHeight
                            when {
                                blocks.size > 1 -> {
                                    ocrChoices = blocks
                                    showOcrSheet = true
                                }
                                blocks.size == 1 -> applyOcrText(
                                    blocks.single().text,
                                    ocrFocusFraction(blocks.single().bounds, result.sourceHeight),
                                )
                                result.text.isNotBlank() -> applyOcrText(result.text, 0.5f)
                                else -> {
                                    ocrError = this@MainActivity.getString(R.string.legacy_capture_no_text_was_found_type_or_edit_the_sentenc)
                                    route = AppRoute.Capture
                                }
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            if (ocrResultIsCurrent(
                                    callbackGeneration,
                                    currentDraftGeneration.value,
                                    callbackEditorRevision,
                                    currentEditorInputRevision.value,
                                    currentSubmissionLocked.value,
                                ) && imageProcessingRevision == processingRevision && noteImagePath == file.absolutePath
                            ) {
                                ocrError = this@MainActivity.getString(R.string.legacy_capture_could_not_read_this_photo_type_or_edit_the)
                                route = AppRoute.Capture
                            }
                        } finally {
                            if (imageProcessingRevision == processingRevision && noteImagePath == file.absolutePath) {
                                ocrLoading = false
                            }
                        }
                    }
                }
            }
            val removeDraftImage = {
                if (draftOrigin == "Photo") {
                    imageError = this@MainActivity.getString(R.string.legacy_capture_this_draft_came_from_a_photo_so_it_needs_i)
                } else {
                listOfNotNull(photoSourcePath, noteImagePath, processedImagePath).distinct().forEach { path ->
                    File(path).delete()
                }
                if (photoSourcePath == noteImagePath) photoSourcePath = null
                noteImagePath = null
                processedImagePath = null
                imageProcessingRevision++
                draftGeneration++
                imageProcessing = false
                ocrLoading = false
                imageError = null
                updateDraft(PreferenceChanges()
                    .remove("photo_source_path")
                    .remove("note_image_path")
                    .remove("processed_image_path")
                    .remove(IMAGE_RECEIPT_PATH)
                    .remove(IMAGE_RECEIPT_FILENAME)
                    .remove(IMAGE_RECEIPT_SIZE)
                    .remove(IMAGE_RECEIPT_MODIFIED)
                    )
                }
                Unit
            }
            val stagePhotoReplacement: (File) -> Unit = stagePhotoReplacement@{ file ->
                if (sendState.locked) return@stagePhotoReplacement
                check(
                    updateDraft(PreferenceChanges().putString("pending_replacement_image_path", file.absolutePath)),
                ) { this@MainActivity.getString(R.string.legacy_capture_pending_photo_replacement_save_failed) }
                pendingPhotoFile = file
                pendingSharedImageUri = null
                showReplace = true
            }
            val requestPhoto: (File) -> Unit = { file ->
                if (editor.text.isNotBlank()) {
                    stagePhotoReplacement(file)
                } else {
                    processImage(file, true)
                }
            }
            val attachImage: (File) -> Unit = { file -> processImage(file, false) }
            val cameraPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                cameraPermissionGranted = granted
                if (granted) {
                    markCameraStepDone()
                } else {
                    cameraDenied = true
                    updateSettings(PreferenceChanges().putBoolean("onboarding_camera_denied", true))
                }
            }
            val photoPicker = rememberLauncherForActivityResult(
                ActivityResultContracts.PickVisualMedia(),
            ) { uri ->
                if (uri != null) coroutineScope.launch {
                    if (currentSubmissionLocked.value) return@launch
                    val callbackGeneration = draftGeneration
                    val callbackRevision = imageProcessingRevision
                    try {
                        val copied = withContext(Dispatchers.IO) { copyImageToDraft(uri) }
                        if (!asyncDraftIsCurrent(callbackGeneration) || callbackRevision != imageProcessingRevision) {
                            copied.delete()
                            return@launch
                        }
                        requestPhoto(copied)
                    } catch (_: Exception) {
                        if (asyncDraftIsCurrent(callbackGeneration)) {
                            ocrError = this@MainActivity.getString(R.string.legacy_capture_could_not_open_that_photo_choose_another_i)
                        }
                    }
                }
            }
            val noteImagePicker = rememberLauncherForActivityResult(
                ActivityResultContracts.PickVisualMedia(),
            ) { uri ->
                if (uri != null) coroutineScope.launch {
                    if (currentSubmissionLocked.value) return@launch
                    val callbackGeneration = draftGeneration
                    val callbackRevision = imageProcessingRevision
                    try {
                        val copied = withContext(Dispatchers.IO) { copyImageToDraft(uri) }
                        if (!asyncDraftIsCurrent(callbackGeneration) || callbackRevision != imageProcessingRevision) {
                            copied.delete()
                            return@launch
                        }
                        attachImage(copied)
                    } catch (_: Exception) {
                        if (asyncDraftIsCurrent(callbackGeneration)) {
                            imageError = this@MainActivity.getString(R.string.legacy_capture_could_not_open_that_photo_choose_another_i_2)
                        }
                    }
                }
            }

            androidx.compose.runtime.LaunchedEffect(Unit, draftGeneration, sendState.locked) {
                if (sendState.locked) return@LaunchedEffect
                val source = noteImagePath?.let(::File)?.takeIf(File::isFile)
                if (source != null && processedImagePath?.let(::File)?.isFile != true) {
                    processImage(source, photoSourcePath == source.absolutePath && editor.text.isBlank())
                } else if (photoSourcePath?.let(::File)?.isFile == true && editor.text.isBlank()) {
                    processImage(File(photoSourcePath!!), true)
                }
            }

            androidx.compose.runtime.LaunchedEffect(dictionaryRetry, setupGeneration, sendState.locked) {
                if (currentSubmissionLocked.value) return@LaunchedEffect
                val callbackGeneration = setupGeneration
                dictionaryLoading = true
                dictionaryError = null
                try {
                    val preparedDictionary = withContext(Dispatchers.IO) { offlineDictionary.prepare() }
                    if (!asyncSetupIsCurrent(callbackGeneration)) return@LaunchedEffect
                    dictionaryInfo = preparedDictionary
                    val availability = withContext(Dispatchers.IO) { ajtPitchRepository.prepare() }
                    if (!asyncSetupIsCurrent(callbackGeneration)) return@LaunchedEffect
                    pitchIndexError = (availability as? AjtPitchAvailability.Unavailable)
                        ?.let { this@MainActivity.getString(pitchUnavailableMessage(it.failure)) }
                    dictionaryReady = true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.e("OfflineDictionary", "Could not prepare the bundled dictionary", error)
                    if (asyncSetupIsCurrent(callbackGeneration)) {
                        dictionaryError = this@MainActivity.getString(R.string.legacy_onboarding_offline_dictionary_could_not_be_prepared_a)
                    }
                } finally {
                    dictionaryLoading = false
                }
            }

            // The dictionary ships in the APK. Opening it is local; the only cost is a one-time
            // unpack into app storage, retried here whenever the user asks.
            androidx.compose.runtime.LaunchedEffect(analyzerRetry) {
                analyzerLoading = true
                analyzerFailure = null
                val installed = withContext(Dispatchers.IO) {
                    runCatching { offlineAnalyzer.loadInstalled() }.getOrDefault(false)
                }
                if (!installed) {
                    val prepared = withContext(Dispatchers.IO) {
                        runCatching {
                            offlineAnalyzer.prepare()
                            offlineAnalyzer.loadInstalled()
                        }
                    }
                    if (prepared.isFailure) {
                        Log.e("OfflineDictionary", "Could not unpack the bundled dictionary", prepared.exceptionOrNull())
                        analyzerFailure = if (prepared.exceptionOrNull() is BundledDictionaryStorageException) {
                            R.string.storage_full
                        } else {
                            R.string.dictionary_install_failed
                        }
                    }
                }
                analyzerReady = runCatching { offlineAnalyzer.loadInstalled() }.getOrDefault(false)
                if (analyzerReady) {
                    analyzerFailure = null
                } else if (analyzerFailure == null) {
                    analyzerFailure = R.string.dictionary_install_failed
                }
                analyzerLoading = false
                if (!analyzerReady && onboardingComplete) {
                    onboardingComplete = false
                    updateSettings(PreferenceChanges().putBoolean("onboarding_complete", false))
                    showSetup = true
                }
            }
            // Nothing from a previous word may survive an unresolved dictionary choice.
            fun clearUnresolvedVocabulary() {
                val currentTarget = target
                val surface = currentTarget?.textIn(editor.text).orEmpty()
                val candidate = currentTarget?.let { resolveAnalyzedLemma(japaneseTokens, it) }
                vocabulary = candidate?.expression ?: surface
                vocabularyReading = candidate?.reading.orEmpty()
                vocabularyReadingError = null
                definition = ""
                definitionManuallyEdited = false
                definitionProvenanceKnown = true
                selectedDictionaryEntry = null
                selectedDictionaryEntryId = null
                selectedSenseIndices = emptySet()
                pitchAccentText = ""
                pitchHtmlText = null
                pitchCandidates = emptyList()
                pitchIndexError = null
                audioController.clear(AudioItem.VOCABULARY)
                audioSnapshot = audioController.snapshot()
            }

            fun dismissDictionarySheet() {
                showDictionarySheet = false
                if (selectedDictionaryEntry == null && DictionaryMeaningChoices.from(dictionaryEntries).size > 1) {
                    clearUnresolvedVocabulary()
                }
            }

            fun commitDictionaryChoice(choice: DictionaryMeaningChoice) {
                if (sendState.locked) return
                val previousIsResolved = selectedDictionaryEntry?.let {
                    it.id == selectedDictionaryEntryId && it.expression == vocabulary && it.reading == vocabularyReading
                } == true
                val samePronunciation = previousIsResolved && selectedDictionaryEntryId == choice.entry.id &&
                    vocabulary == choice.entry.expression && vocabularyReading == choice.entry.reading
                val sameChoice = samePronunciation && selectedSenseIndices == setOf(choice.senseIndex)
                if (previousIsResolved && !sameChoice) dependentFieldsStale = true
                if (!samePronunciation) {
                    audioController.clear(AudioItem.VOCABULARY)
                    audioSnapshot = audioController.snapshot()
                    pitchAccentText = ""
                    pitchHtmlText = null
                    pitchCandidates = emptyList()
                    pitchIndexError = null
                    vocabularyReadingError = null
                }
                selectedDictionaryEntry = choice.entry
                selectedDictionaryEntryId = choice.entry.id
                draftState.value = draftState.value.withEntry(choice.entry, setOf(choice.senseIndex))
                selectedSenseIndices = setOf(choice.senseIndex)
                definitionManuallyEdited = false
                definitionProvenanceKnown = true
                dictionaryError = null
                audioConfigErrors = audioConfigErrors - AudioItem.VOCABULARY
                showDictionarySheet = false
            }

            val vocabularyReadingCoordinator = CardVocabularyReadingCoordinator(
                snapshot = {
                    CardVocabularySnapshot(
                        locked = sendState.locked,
                        entries = dictionaryEntries,
                        vocabulary = vocabulary,
                        vocabularyReading = vocabularyReading,
                        selectedEntry = selectedDictionaryEntry,
                    )
                },
                effects = CardVocabularyEffects(
                    clearAudio = {
                        audioController.clear(AudioItem.VOCABULARY)
                        audioSnapshot = audioController.snapshot()
                    },
                    clearPitch = {
                        pitchAccentText = ""
                        pitchHtmlText = null
                        pitchCandidates = emptyList()
                    },
                    invalidateCard = { cardInvalidated = true },
                    setVocabularyReading = { vocabularyReading = it },
                    setVocabularyReadingError = { vocabularyReadingError = it },
                    setSelectedEntry = { selectedDictionaryEntry = it },
                    setSelectedEntryId = { selectedDictionaryEntryId = it },
                    setSelectedSenses = { selectedSenseIndices = it },
                    setPitchError = { pitchIndexError = it },
                    setDefinition = { definition = it },
                    setDefinitionManuallyEdited = { definitionManuallyEdited = it },
                    setDefinitionProvenanceKnown = { definitionProvenanceKnown = it },
                ),
                errorCopy = CardVocabularyErrorCopy(
                    kanaRequired = this@MainActivity.getString(R.string.card_vocabulary_reading_kana_required),
                    noDictionaryEntry = this@MainActivity.getString(R.string.card_no_dictionary_entry_loaded),
                    noPitchPattern = this@MainActivity.getString(R.string.card_no_pitch_pattern_can_be_resolved_for_this),
                    readingNotListed = { word, reading ->
                        this@MainActivity.getString(R.string.card_reading_not_listed_for_word, word, reading)
                    },
                    thisWord = this@MainActivity.getString(R.string.card_this_word),
                ),
            )
            val applyVocabularyReading: (String) -> Boolean = vocabularyReadingCoordinator::apply


            /** Correct one kanji span's reading without editing the sentence (SPEC §4.5). */
            fun applyReadingCorrection(span: TargetSpan, reading: String): Boolean {
                if (sendState.locked) return false
                val rejection = SentenceReadings.rejection(editor.text, span, reading)
                if (rejection != null) {
                    readingCorrectionError = rejection
                    return false
                }
                readingCorrectionError = null
                val corrected = SentenceReadings.normalizeReading(reading)
                cardInvalidated = true
                readingCorrections = SentenceReadings.correct(readingCorrections, editor.text, span, corrected)
                if (span == target) {
                    if (sentenceReading == corrected) return true
                    sentenceReading = corrected
                }
                // A sentence-reading change invalidates sentence speech and its hints.
                audioController.clear(AudioItem.SENTENCE)
                audioSnapshot = audioController.snapshot()
                return true
            }

            DraftReadingCorrectionCleanupEffect(
                sentence = draft.sentence,
                corrections = draft.readingCorrections,
                onCorrectionsChanged = { readingCorrections = it },
            )

            DraftTranslationEffect(
                sentence = draft.sentence,
                requestRevision = translationRequestRevision,
                retry = translationRetry,
                autoAllowed = translationAutoAllowed,
                sendLocked = sendState.locked,
                generationStarted = generationStarted,
                lastHandledRetry = lastHandledTranslationRetry,
                attemptedSentence = translationAttemptedSentence,
                currentDraft = { translationDraft },
                currentRequestRevision = currentTranslationRequestRevision,
                currentSubmissionLocked = currentSubmissionLocked,
                translationService = translationService,
                storage = preferenceStorage,
                onDraftChanged = { translationDraft = it },
                onLastHandledRetryChanged = { lastHandledTranslationRetry = it },
                onAttemptedSentenceChanged = { translationAttemptedSentence = it },
                retryNeededMessage = this@MainActivity.getString(
                    R.string.card_translation_needs_retry_tap_retry_to_conti,
                ),
                notStartedMessage = this@MainActivity.getString(
                    R.string.card_translation_was_not_started_tap_retry_to_c,
                ),
                requestRecordFailedMessage = this@MainActivity.getString(
                    R.string.card_translation_request_could_not_be_recorded,
                ),
                unavailableMessage = this@MainActivity.getString(
                    R.string.card_translation_is_unavailable_tap_retry_to_co,
                ),
            )

            DraftTokenizationEffect(
                analyzerReady = analyzerReady,
                sentence = draft.sentence,
                analysisRetry = analysisRetry,
                draftGeneration = draftGeneration,
                sendLocked = sendState.locked,
                dictionaryReady = dictionaryReady,
                offlineAnalyzer = offlineAnalyzer,
                offlineDictionary = offlineDictionary,
                isDraftCurrent = ::asyncDraftIsCurrent,
                onTokensChanged = { japaneseTokens = it },
                onLoadingChanged = { tokenizationLoading = it },
                onError = { tokenizationError = it.takeIf(String::isNotEmpty) },
                errorMessage = this@MainActivity.getString(R.string.card_could_not_analyze_this_sentence_select_a_t),
            )

            DraftTargetVocabularyEffect(
                target = draft.target,
                sentence = draft.sentence,
                tokens = japaneseTokens,
                vocabulary = draft.vocabulary,
                vocabularyReading = draft.vocabularyReading,
                vocabularyReadingError = vocabularyReadingError,
                dependentFieldsStale = dependentFieldsStale,
                sendLocked = sendState.locked,
                onVocabularyChanged = { vocabulary = it },
                onVocabularyReadingChanged = { vocabularyReading = it },
            )

            DraftSentenceReadingEffect(
                target = draft.target,
                sentence = draft.sentence,
                tokens = japaneseTokens,
                sentenceReading = draft.sentenceReading,
                sendLocked = sendState.locked,
                onSentenceReadingChanged = { sentenceReading = it },
            )

            fun playAudio(item: AudioItem, file: DraftAudioFile) {
                stopAudioPlayback()
                audioPlaybackErrors = audioPlaybackErrors - item
                // A playback lease keeps retirement from deleting the file under the player.
                val lease = draftAudioStore.lease(file, AudioFileOwner.PLAYBACK)
                val player = MediaPlayer()
                try {
                    player.setDataSource(file.path)
                    player.setOnCompletionListener {
                        playingAudio = null
                        stopAudioPlayback()
                    }
                    player.prepare()
                    player.start()
                    audioPlayer = player
                    audioLease = lease
                    playingAudio = item
                } catch (error: Exception) {
                    Log.e("DraftAudio", "${item.name} playback failed ${error.javaClass.simpleName}")
                    lease.close()
                    player.release()
                    playingAudio = null
                    audioPlaybackErrors = audioPlaybackErrors + (
                        item to reviewAudioPlaybackFailureMessage(
                            this@MainActivity.getString(
                                if (item == AudioItem.SENTENCE) R.string.card_sentence_audio_field else R.string.card_word_audio_field,
                            ),
                        ).resolve(this@MainActivity)
                    )
                }
            }

            fun audioReadyFile(item: AudioItem): DraftAudioFile? =
                (audioSnapshot.items[item] as? DraftAudioState.Ready)?.file

            fun audioSettingsRequired(item: AudioItem): Boolean = item == AudioItem.SENTENCE && (
                settingsScreenState.credentialUnreadable || !settingsScreenState.credentialStored ||
                    selectedVoiceName.isBlank() ||
                    settingsScreenState.voiceCatalog.provenance != VoiceCatalogProvenance.AUTHORITATIVE ||
                    settingsScreenState.voiceCatalog.voices.none { it.name == selectedVoiceName }
                )

            fun audioActionLabel(item: AudioItem): Int = when {
                audioConfigErrors.containsKey(item) && audioSettingsRequired(item) -> R.string.card_open_settings_action
                audioConfigErrors.containsKey(item) -> R.string.card_edit_preparation
                (audioSnapshot.items[item] as? DraftAudioState.Failed)?.retryable == false -> R.string.card_edit_preparation
                audioSnapshot.items[item] is DraftAudioState.NeedsContinue -> R.string.card_generate_action
                else -> R.string.retry
            }

            /** The single user-authorized audio action: Generate, Retry, or repair a playback failure. */
            fun onAudioAction(item: AudioItem) {
                when {
                    audioPlaybackErrors.containsKey(item) -> {
                        audioPlaybackErrors = audioPlaybackErrors - item
                        audioReadyFile(item)?.let { playAudio(item, it) }
                    }
                    audioConfigErrors.containsKey(item) && audioSettingsRequired(item) -> openSettings()
                    audioConfigErrors.containsKey(item) -> {
                        audioConfigErrors = audioConfigErrors - item
                        route = AppRoute.Card
                    }
                    (audioSnapshot.items[item] as? DraftAudioState.Failed)?.retryable == false -> {
                        route = AppRoute.Card
                    }
                    else -> {
                        val attempt = audioController.confirm(item) ?: audioController.retry(item)
                        if (attempt != null) {
                            runAudioAttempt(attempt)
                        } else {
                            val message = this@MainActivity.getString(
                                if (item == AudioItem.SENTENCE) R.string.card_sentence_audio_not_configured
                                else R.string.card_word_audio_unavailable,
                            )
                            audioConfigErrors = audioConfigErrors + (item to message)
                            Log.w("DraftAudio", "${item.name} audio unavailable: no current input")
                        }
                    }
                }
            }

            // Inputs are cheap to declare. Provider work is started only by the matching
            // sentence-commit or word-selection authorization token.
            androidx.compose.runtime.LaunchedEffect(
                route, sendState.locked, audioInputRevision, reviewAuthorizationSequence,
                pendingSentenceAudioAuthorization, pendingWordAudioAuthorization,
                currentSentenceIdentity.value, currentWordIdentity.value,
            ) {
                if (sendState.locked) return@LaunchedEffect

                val declaredAudioRevision = reviewAuthorizationSequence
                val sentenceText = editor.text
                val requestHintSpans = sentenceHintSpans(sentenceText, japaneseTokens, readingCorrections)
                val hintResult = ReadingHintBuilder.build(sentenceText, requestHintSpans)
                val authoritativeCatalog = settingsScreenState.voiceCatalog.takeIf {
                    settingsScreenState.credentialStored && it.provenance == VoiceCatalogProvenance.AUTHORITATIVE
                }
                val voice = authoritativeCatalog?.voices?.firstOrNull { it.name == selectedVoiceName }

                if (sentenceText.isNotEmpty() &&
                    voice != null && hintResult is HintResult.Ready
                ) {
                    audioConfigErrors = audioConfigErrors - AudioItem.SENTENCE
                    val request = SentenceSpeechRequest(
                        text = sentenceText,
                        voice = voice,
                        hints = hintResult.hints,
                    )
                    audioController.setInput(
                        DraftAudioInput(
                            item = AudioItem.SENTENCE,
                            identity = currentSentenceIdentity.value,
                            text = sentenceText,
                            revision = declaredAudioRevision,
                            cacheKey = AudioCacheKey(
                                source = "google-chirp3-hd",
                                text = sentenceText,
                                voice = voice.name,
                                rate = request.speakingRate.toFloat(),
                                hintIdentity = ReadingHintBuilder.cacheFragment(hintResult.hints),
                            ),
                            expectedFormat = AudioFormat.MP3,
                            providerPayload = request,
                        ),
                    )
                } else {
                    // Configuration errors stay on the screen: they are not provider failures
                    // and must not be presented as retryable generation attempts.
                    val sentenceConfigError = when {
                        sentenceText.isEmpty() -> null
                        voice == null -> when {
                            settingsScreenState.credentialUnreadable ->
                                this@MainActivity.getString(R.string.card_saved_cloud_key_unreadable)
                            !settingsScreenState.credentialStored ->
                                this@MainActivity.getString(R.string.card_no_cloud_key_saved)
                            selectedVoiceName.isBlank() ->
                                this@MainActivity.getString(R.string.card_no_verified_sentence_voice)
                            else ->
                                this@MainActivity.getString(R.string.card_sentence_voice_unverified)
                        }
                        hintResult is HintResult.Impossible -> hintResult.reason
                        else -> null
                    }
                    if (sentenceConfigError != null) {
                        val cause = when {
                            settingsScreenState.credentialUnreadable -> "saved Google Cloud credential is unreadable"
                            !settingsScreenState.credentialStored -> "no Google Cloud credential is saved"
                            voice == null -> "no verified sentence voice is selected"
                            else -> "sentence pronunciation hints are unavailable"
                        }
                        Log.i("DraftAudio", "Sentence audio unavailable: $cause")
                    }
                    audioConfigErrors = if (sentenceConfigError == null) {
                        audioConfigErrors - AudioItem.SENTENCE
                    } else {
                        audioConfigErrors + (AudioItem.SENTENCE to sentenceConfigError)
                    }
                    audioController.clear(AudioItem.SENTENCE)
                }

                val sentenceAuthorization = pendingSentenceAudioAuthorization
                if (sentenceAuthorization != null &&
                    handledSentenceAudioAuthorization != sentenceAuthorization.revision
                ) {
                    val authorizationStillMatches = sentenceAuthorization.sentence == sentenceText &&
                        sentenceAuthorization.voiceName == selectedVoiceName
                    val sentenceHasKanji = sentenceText.any { containsKanji(it.toString()) }
                    val waitingForAnalysis = sentenceHasKanji &&
                        (!analyzerReady || analyzerLoading || tokenizationLoading)
                    val waitingForVoiceCatalog = voice == null && settingsScreenState.loading
                    if (!authorizationStillMatches) {
                        handledSentenceAudioAuthorization = sentenceAuthorization.revision
                        pendingSentenceAudioAuthorization = null
                    } else if (!waitingForAnalysis && !waitingForVoiceCatalog) {
                        handledSentenceAudioAuthorization = sentenceAuthorization.revision
                        pendingSentenceAudioAuthorization = null
                        if (voice != null && hintResult is HintResult.Ready) {
                            runAudioAttempt(audioController.confirm(AudioItem.SENTENCE))
                            audioSnapshot = audioController.snapshot()
                        }
                    }
                }

                val entryId = selectedDictionaryEntryId
                val entry = selectedDictionaryEntry?.takeIf {
                    it.id == entryId && it.expression == vocabulary && it.reading == vocabularyReading
                }
                if (entry == null || vocabulary.isBlank()) {
                    // An unresolved word can never be sent, and its old recording must not survive
                    // the change that invalidated it.
                    audioConfigErrors = if (entry == null && vocabulary.isNotBlank()) {
                        audioConfigErrors + (
                            AudioItem.VOCABULARY to this@MainActivity.getString(R.string.card_select_dictionary_entry_for_audio)
                            )
                    } else {
                        audioConfigErrors - AudioItem.VOCABULARY
                    }
                    // Only a blank word has no input at all; an unresolved entry keeps whatever is
                    // on disk until a new input replaces it (the identity gate blocks Send).
                    if (vocabulary.isBlank()) audioController.clear(AudioItem.VOCABULARY)
                } else {
                    audioConfigErrors = audioConfigErrors - AudioItem.VOCABULARY
                    val entryIdentity = "$entryId|${entry.expression}|${entry.reading}"
                    audioController.setInput(
                        DraftAudioInput(
                            item = AudioItem.VOCABULARY,
                            identity = currentWordIdentity.value,
                            text = vocabulary,
                            revision = declaredAudioRevision,
                            entryIdentity = entryIdentity,
                            cacheKey = AudioCacheKey(
                                source = "nhk-2016",
                                text = vocabulary,
                                voice = "NHK-2016",
                                rate = 1f,
                                entryIdentity = entryIdentity,
                            ),
                            expectedFormat = AudioFormat.OGG,
                            providerPayload = AudioSynthesisRequest(
                                item = AudioItem.VOCABULARY,
                                text = vocabulary,
                                revision = declaredAudioRevision,
                                entryIdentity = entryIdentity,
                            ),
                        ),
                    )
                }

                val wordAuthorization = pendingWordAudioAuthorization
                if (wordAuthorization != null && handledWordAudioAuthorization != wordAuthorization.revision) {
                    if (target != wordAuthorization.target) {
                        handledWordAudioAuthorization = wordAuthorization.revision
                        pendingWordAudioAuthorization = null
                    } else if (entry != null && vocabulary.isNotBlank()) {
                        handledWordAudioAuthorization = wordAuthorization.revision
                        pendingWordAudioAuthorization = null
                        runAudioAttempt(audioController.confirm(AudioItem.VOCABULARY))
                        audioSnapshot = audioController.snapshot()
                    }
                }
                audioSnapshot = audioController.snapshot()
            }


            DraftDictionarySearchEffect(
                dictionaryReady = dictionaryReady,
                target = draft.target,
                sentence = draft.sentence,
                dictionaryRetry = dictionaryRetry,
                tokens = japaneseTokens,
                draftGeneration = draftGeneration,
                sendLocked = sendState.locked,
                analyzedExpression = analyzedTarget?.expression,
                selectedEntryId = selectedDictionaryEntryId,
                vocabulary = draft.vocabulary,
                vocabularyReading = draft.vocabularyReading,
                storage = preferenceStorage,
                offlineDictionary = offlineDictionary,
                isDraftCurrent = ::asyncDraftIsCurrent,
                onEntriesChanged = { dictionaryEntries = it },
                onHasMoreChanged = { dictionaryHasMore = it },
                onSelectedEntryChanged = { selectedDictionaryEntry = it },
                onLoadingChanged = { dictionarySearchLoading = it },
                onError = { dictionaryError = it.takeIf(String::isNotEmpty) },
                clearUnresolvedVocabulary = ::clearUnresolvedVocabulary,
                commitChoice = ::commitDictionaryChoice,
                onSheetChanged = { showDictionarySheet = it },
                lookupFailedMessage = this@MainActivity.getString(
                    R.string.card_dictionary_lookup_failed_retry_before_sele,
                ),
            )

            DraftPitchLookupEffect(
                dictionaryReady = dictionaryReady,
                selectedEntryId = selectedDictionaryEntryId,
                selectedEntry = selectedDictionaryEntry,
                vocabulary = draft.vocabulary,
                vocabularyReading = draft.vocabularyReading,
                target = draft.target,
                sentence = draft.sentence,
                dictionaryRetry = dictionaryRetry,
                draftGeneration = draftGeneration,
                sendLocked = sendState.locked,
                availability = ajtAvailability,
                repository = ajtPitchRepository,
                isDraftCurrent = ::asyncDraftIsCurrent,
                onCandidatesChanged = { pitchCandidates = it },
                onPitchAccentChanged = { pitchAccentText = it },
                onPitchHtmlChanged = { pitchHtmlText = it },
                onLoadingChanged = { pitchLookupLoading = it },
                onError = { pitchIndexError = it },
                unavailableMessage = { failure ->
                    this@MainActivity.getString(pitchUnavailableMessage(failure))
                },
                missingMessage = this@MainActivity.getString(R.string.card_ajt_pitch_record_missing),
            )

            androidx.compose.runtime.LaunchedEffect(incoming, sendState.locked) {
                if (sendState.locked) {
                    incoming?.let {
                        val rawImage = imageFromIntent(intent)
                        if (clearConsumedShareIntent(expectedText = it, expectedImage = rawImage)) {
                            sharedText.value = null
                            if (sharedImage.value == rawImage) sharedImage.value = null
                        }
                    }
                    return@LaunchedEffect
                }
                if (incoming != null) {
                    if (editor.text.isBlank()) {
                        captureMode = "Type"
                        route = AppRoute.Capture
                        if (photoSourcePath == null) setDraftOrigin("Type")
                        replaceEditorText(incoming, true)
                        selectTarget(null)
                        japaneseTokens = emptyList()
                        clearConsumedShareIntent(expectedText = incoming, expectedImage = imageFromIntent(intent))
                        sharedText.value = null
                    } else {
                        showReplace = true
                    }
                }
            }
            androidx.compose.runtime.LaunchedEffect(sharedImage.value, sendState.locked) {
                if (sendState.locked) {
                    sharedImage.value?.let {
                        val rawText = textFromIntent(intent)
                        val rawImage = imageFromIntent(intent)
                        if (clearConsumedShareIntent(expectedText = rawText, expectedImage = it) ||
                            (rawText == null && rawImage == null)
                        ) {
                            sharedImage.value = null
                        }
                    }
                    return@LaunchedEffect
                }
                val uri = sharedImage.value ?: return@LaunchedEffect
                val callbackGeneration = draftGeneration
                captureMode = "Photo"
                val callbackRevision = imageProcessingRevision
                try {
                    val source = withContext(Dispatchers.IO) { copyImageToDraft(uri) }
                    if (!asyncDraftIsCurrent(callbackGeneration) || callbackRevision != imageProcessingRevision || sharedImage.value != uri) {
                        source.delete()
                        return@LaunchedEffect
                    }
                    if (sharedImage.value != uri) {
                        source.delete()
                        return@LaunchedEffect
                    }
                    if (editor.text.isNotBlank()) stagePhotoReplacement(source)
                    clearConsumedShareIntent(expectedText = textFromIntent(intent), expectedImage = uri)
                    sharedImage.value = null
                    if (editor.text.isBlank()) requestPhoto(source)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (sharedImage.value == uri) {
                        clearConsumedShareIntent(expectedText = textFromIntent(intent), expectedImage = uri)
                        sharedImage.value = null
                        if (asyncDraftIsCurrent(callbackGeneration)) {
                            ocrError = this@MainActivity.getString(R.string.legacy_capture_could_not_open_that_photo_choose_another_i)
                        }
                    }
                }
            }
            if (showSettings) {
                FlowDialog(
                    onDismissRequest = { closeSettings() },
                    properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
                ) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        SettingsScreen(
                            state = SettingsScreenState(
                                values = settingsScreenState.values,
                                googleKey = settingsScreenState.googleKey,
                                credentialStored = settingsScreenState.credentialStored,
                                loading = settingsScreenState.loading || settingsLoading || settingsSaving,
                                credentialUnreadable = settingsScreenState.credentialUnreadable,
                                error = settingsError ?: settingsScreenState.error,
                                voiceCatalog = settingsScreenState.voiceCatalog,
                                ankiConnected = ankiDroidInstalled && apiPermissionGranted,
                                ankiInstalled = ankiDroidInstalled,
                                onboardingIncomplete = !onboardingComplete,
                                                cacheBusy = audioCacheBusy,
                                cacheMessage = audioCacheMessage,
                            ),
                            noteTypes = noteTypes,
                            decks = decks,
                            onBack = { closeSettings() },
                            onFinishSetup = { closeSettings(resumeSetup = true) },
                            onConnectAnki = {
                                if (ankiDroidInstalled && !apiPermissionGranted) {
                                    apiPermissionLauncher.launch(ANKIDROID_READ_WRITE_PERMISSION)
                                } else if (!ankiDroidInstalled) {
                                    val openedStore = runCatching {
                                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.ichi2.anki")))
                                    }.isSuccess
                                    if (!openedStore) settingsError = "Install AnkiDroid to send this card."
                                }
                            },
                            onModelChange = { modelId ->
                                settingsCoordinator?.let { coordinator ->
                                    coordinator.updateValues(coordinator.currentValues().copy(modelId = modelId))
                                    syncSettingsScreen(coordinator)
                                }
                            },
                            onDeckChange = { deckId ->
                                settingsCoordinator?.let { coordinator ->
                                    coordinator.updateValues(coordinator.currentValues().copy(deckId = deckId))
                                    syncSettingsScreen(coordinator)
                                }
                            },
                            onDefaultTagsChange = { value ->
                                settingsCoordinator?.let { coordinator ->
                                    coordinator.updateValues(coordinator.currentValues().copy(defaultTags = value))
                                    syncSettingsScreen(coordinator)
                                }
                            },
                            onGoogleKeyChange = { value ->
                                settingsCoordinator?.let { coordinator ->
                                    val changed = value != coordinator.currentGoogleKey()
                                    coordinator.updateGoogleKey(value)
                                    if (changed) {
                                        updateSettings(PreferenceChanges()
                                            .putBoolean("onboarding_audio_validated", false)
                                            .putBoolean("onboarding_complete", false)
                                            )
                                        onboardingComplete = false
                                        audioSetupInvalidated = true
                                        setupAudioErrorResource = null
                                    }
                                    syncSettingsScreen(coordinator)
                                }
                            },
                            onVoiceChange = { voice ->
                                settingsCoordinator?.let { coordinator ->
                                    val changed = voice != coordinator.currentValues().voiceName
                                    coordinator.updateValues(coordinator.currentValues().copy(voiceName = voice))
                                    audioInputRevision++
                                    if (changed) {
                                        updateSettings(PreferenceChanges()
                                            .putBoolean("onboarding_audio_validated", false)
                                            .putBoolean("onboarding_complete", false)
                                            )
                                        onboardingComplete = false
                                        audioSetupInvalidated = true
                                        setupAudioErrorResource = null
                                    }
                                    syncSettingsScreen(coordinator)
                                }
                            },
                            onReloadVoices = {
                                settingsCoordinator?.let { coordinator ->
                                    val request = coordinator.reloadVoices()
                                    if (request == null) {
                                        settingsError = if (coordinator.currentGoogleKey().isBlank()) {
                                            "Enter a Google Cloud API key."
                                        } else {
                                            "Could not save your changes."
                                        }
                                        syncSettingsScreen(coordinator)
                                    } else {
                                        settingsError = null
                                        startVoiceList(coordinator, request)
                                    }
                                }
                            },
                            onClearCache = {
                                audioCacheBusy = true
                                audioCacheMessage = null
                                coroutineScope.launch {
                                    try {
                                        withContext(Dispatchers.IO) { reusableAudioCache.clear() }
                                        audioCacheMessage = "Cache cleared"
                                    } catch (_: Exception) {
                                        audioCacheMessage = "Could not clear the cache."
                                    } finally {
                                        audioCacheBusy = false
                                    }
                                }
                            },
                            onOpenSources = { showSources = true },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
            if (showSources) {
                FlowDialog(
                    onDismissRequest = { showSources = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
                ) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Column(Modifier.fillMaxSize()) {
                            FlowTopBar(
                                title = stringResource(R.string.sources),
                                onBack = { showSources = false },
                                modifier = Modifier.statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                            Column(
                                Modifier.fillMaxWidth().weight(1f).navigationBarsPadding()
                                    .verticalScroll(rememberScrollState()).padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(stringResource(R.string.legacy_settings_jmdict_source_notice, dictionaryInfo["jmdict_source"].orEmpty()))
                                Text(stringResource(R.string.legacy_settings_kanjium_source_notice, dictionaryInfo["kanjium_commit"].orEmpty()))
                                Text(stringResource(R.string.legacy_settings_mecab_0_996_taku_kudo_and_nippon_telegraph))
                                Text(stringResource(R.string.legacy_settings_unidic_2_1_2_the_unidic_consortium_and))
                                val licenseDocuments = remember {
                                    fun readAsset(path: String, charset: Charset = Charsets.UTF_8): String = runCatching {
                                        assets.open(path).bufferedReader(charset).use { it.readText() }
                                    }.getOrDefault("")
                                    listOf(
                                        runCatching {
                                            Html.fromHtml(
                                                readAsset("licenses/EDRDG-licence.html", Charsets.ISO_8859_1),
                                                Html.FROM_HTML_MODE_LEGACY,
                                            ).toString()
                                        }.getOrDefault(""),
                                        readAsset("licenses/CC-BY-SA-4.0.txt"),
                                        readAsset("licenses/mecab/BSD"),
                                        readAsset("licenses/unidic/BSD"),
                                    ).filter(String::isNotBlank).joinToString("\n\n")
                                }
                                Text(licenseDocuments)
                            }
                        }
                }
            }
            if (setupVisible) {
                FlowDialog(
                    onDismissRequest = { if (onboardingComplete && analyzerReady) showSetup = false },
                        properties = DialogProperties(
                            usePlatformDefaultWidth = false,
                            decorFitsSystemWindows = false,
                            dismissOnClickOutside = false,
                        ),
                    ) {
                        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                            SetupScreen(
                                state = SetupUi(
                                    audio = AudioSetupUi(
                                        key = settingsScreenState.googleKey,
                                        credentialStored = settingsScreenState.credentialStored,
                                        selectedVoice = settingsScreenState.values.voiceName,
                                        voices = settingsScreenState.voiceCatalog.voices,
                                        loading = settingsLoading || settingsSaving,
                                        loadingVoices = settingsScreenState.voiceCatalog.loading,
                                        errorResource = setupAudioErrorResource,
                                    ),
                                    anki = AnkiSetupUi(
                                        installed = ankiDroidInstalled,
                                        connected = ankiDroidInstalled && apiPermissionGranted &&
                                            setupLoadedSuccessfully && setupError == null,
                                        loading = setupLoading,
                                        noteTypes = compatibleSetupNoteTypes(),
                                        decks = decks,
                                        selectedNoteTypeId = selectedModelId?.takeIf { id ->
                                            compatibleSetupNoteTypes().any { model -> model.id == id }
                                        },
                                        selectedDeckId = selectedDeckId,
                                    ),
                                    cameraPermissionGranted = cameraPermissionGranted,
                                    cameraDenied = cameraDenied,
                                    dictionaryPreparing = !setupReadiness.dictionaryReady && analyzerFailure == null,
                                    dictionaryErrorResource = analyzerFailure,
                                    audioReady = setupReadiness.audioReady,
                                    ankiReady = setupReadiness.ankiReady,
                                ),
                                onBack = if (onboardingComplete && setupReadiness.dictionaryReady) ({ showSetup = false }) else null,
                                onKeyChange = { value ->
                                    settingsCoordinator?.let { coordinator ->
                                        val changed = value != coordinator.currentGoogleKey()
                                        coordinator.updateGoogleKey(value)
                                        if (changed) {
                                            updateSettings(PreferenceChanges().putBoolean("onboarding_complete", false))
                                            onboardingComplete = false
                                            setupAudioErrorResource = null
                                        }
                                        syncSettingsScreen(coordinator)
                                    }
                                },
                                onVoiceChange = { voice ->
                                    settingsCoordinator?.let { coordinator ->
                                        val changed = voice != coordinator.currentValues().voiceName
                                        coordinator.updateValues(coordinator.currentValues().copy(voiceName = voice))
                                        if (changed) {
                                            updateSettings(PreferenceChanges().putBoolean("onboarding_complete", false))
                                            onboardingComplete = false
                                            setupAudioErrorResource = null
                                        }
                                        syncSettingsScreen(coordinator)
                                    }
                                },
                                onLoadVoices = {
                                    settingsCoordinator?.let { coordinator ->
                                        val request = coordinator.reloadVoices()
                                        if (request == null) setupAudioErrorResource = R.string.voices_failed
                                        else {
                                            setupAudioErrorResource = null
                                            startVoiceList(coordinator, request)
                                        }
                                    }
                                },
                                onRequestCamera = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                                onConnectAnki = {
                                    if (ankiDroidInstalled && !apiPermissionGranted) {
                                        apiPermissionLauncher.launch(ANKIDROID_READ_WRITE_PERMISSION)
                                    } else if (!ankiDroidInstalled) {
                                        val openedStore = runCatching {
                                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.ichi2.anki")))
                                        }.isSuccess
                                        if (!openedStore) settingsError = this@MainActivity.getString(R.string.anki_missing)
                                    }
                                },
                                onSelectNoteType = { modelId ->
                                    if (updateSettings(PreferenceChanges().putLong(SettingsPreferenceKeys.MODEL_ID, modelId))) {
                                        selectedModelId = modelId
                                    } else {
                                        settingsError = this@MainActivity.getString(R.string.save_failed)
                                    }
                                },
                                onSelectDeck = { deckId ->
                                    if (updateSettings(PreferenceChanges().putLong(SettingsPreferenceKeys.DECK_ID, deckId))) {
                                        selectedDeckId = deckId
                                    } else {
                                        settingsError = this@MainActivity.getString(R.string.save_failed)
                                    }
                                },
                                onRetryDictionary = { analyzerRetry++ },
                                onFinish = { finishSetup() },
                            )
                        }
                }
            }
            if (!sendState.locked && showReplace && (incoming != null || pendingPhotoFile != null || pendingSharedImageUri != null)) {
                FlowDialog(
                    onDismissRequest = {
                        discardSharePayload()
                        showReplace = false
                        sharedText.value = null
                        pendingPhotoFile?.delete()
                        pendingPhotoFile = null
                        updateDraft(PreferenceChanges().remove("pending_replacement_image_path"))
                        pendingSharedImageUri = null
                    },
                    title = { Text(stringResource(R.string.replace_title)) },
                    text = { Text(stringResource(R.string.replace_body)) },
                    confirmButton = {
                        TextButton(onClick = {
                            when {
                                incoming != null -> {
                                    clearConsumedShareIntent(expectedText = incoming, expectedImage = imageFromIntent(intent))
                                    clearDraft()
                                    captureMode = "Type"
                                    replaceEditorText(incoming, true)
                                    route = AppRoute.Capture
                                    sharedText.value = null
                                }
                                pendingPhotoFile != null -> {
                                    val file = pendingPhotoFile!!
                                    pendingPhotoFile = null
                                    updateDraft(PreferenceChanges().remove("pending_replacement_image_path"))
                                    clearDraft()
                                    captureMode = "Photo"
                                    processImage(file, true)
                                }
                                pendingSharedImageUri != null -> {
                                    val uri = pendingSharedImageUri!!
                                    pendingSharedImageUri = null
                                    clearDraft()
                                    captureMode = "Photo"
                                    val callbackGeneration = draftGeneration
                                    val callbackRevision = imageProcessingRevision
                                    coroutineScope.launch {
                                        try {
                                            val copied = withContext(Dispatchers.IO) { copyImageToDraft(uri) }
                                            if (!asyncDraftIsCurrent(callbackGeneration) || callbackRevision != imageProcessingRevision) {
                                                copied.delete()
                                                return@launch
                                            }
                                            requestPhoto(copied)
                                        } catch (_: Exception) {
                                            if (asyncDraftIsCurrent(callbackGeneration)) {
                                                ocrError = "Could not open this photo."
                                            }
                                        }
                                    }
                                }
                            }
                            showReplace = false
                        }) { Text(stringResource(R.string.replace)) }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            discardSharePayload()
                            showReplace = false
                            sharedText.value = null
                            pendingPhotoFile?.delete()
                            pendingPhotoFile = null
                            updateDraft(PreferenceChanges().remove("pending_replacement_image_path"))
                            pendingSharedImageUri = null
                        }) { Text(stringResource(R.string.keep_draft)) }
                    },
                    )
            }

            SentenceOcrChoicesSheet(
                visible = showOcrSheet,
                choices = ocrChoices,
                enabled = !sendState.locked,
                onDismiss = { showOcrSheet = false },
                onChoose = { block ->
                    showOcrSheet = false
                    ocrChoices = emptyList()
                    applyOcrText(block.text, ocrFocusFraction(block.bounds, ocrImageHeight))
                },
            )

            CardDiscardConfirmationDialog(
                visible = showDiscardConfirm,
                hasWork = editor.text.isNotBlank() || noteImagePath != null || target != null,
                enabled = !sendState.locked,
                title = stringResource(R.string.legacy_capture_discard_this_draft),
                workMessage = stringResource(R.string.legacy_capture_the_sentence_target_photo_and_any_generate),
                emptyMessage = stringResource(R.string.legacy_capture_this_draft_is_already_empty),
                discardLabel = stringResource(R.string.legacy_capture_discard_draft),
                keepWorkingLabel = stringResource(R.string.legacy_capture_keep_working),
                onDismiss = { showDiscardConfirm = false },
                onConfirm = {
                    showDiscardConfirm = false
                    clearDraft()
                },
            )

            val processedImageIdentity = liveMediaIdentity(processedImagePath?.let(::File))
            val imageReceiptFilename = importedReceiptFromPreferences(
                preferences,
                IMAGE_RECEIPT_KEYS,
                processedImageIdentity,
                AnkiMediaImporter::sanitizeFilename,
            )
            val imageAttached = noteImagePath != null
            // The controller's Ready metadata is exactly what was published, so these receipts
            // need no extra stat: one identity per item, computed once.
            fun audioMediaIdentity(item: AudioItem): MediaIdentity? =
                (audioSnapshot.items[item] as? DraftAudioState.Ready)
                    ?.let { state -> MediaIdentity(state.file.path, state.file.size, state.file.modified) }

            val sentenceAudioReceipt = importedReceiptFromPreferences(
                preferences,
                SENTENCE_AUDIO_RECEIPT_KEYS,
                audioMediaIdentity(AudioItem.SENTENCE),
                AnkiMediaImporter::sanitizeAudioFilename,
            )
            val wordAudioReceipt = importedReceiptFromPreferences(
                preferences,
                WORD_AUDIO_RECEIPT_KEYS,
                audioMediaIdentity(AudioItem.VOCABULARY),
                AnkiMediaImporter::sanitizeAudioFilename,
            )
            val sentenceAudioReady = audioReady(AudioItem.SENTENCE, currentSentenceIdentity.value)
            val wordAudioReady = audioReady(AudioItem.VOCABULARY, currentWordIdentity.value)
            val imagePending = imageAttached && imageProcessing
            val imageReady = if (imageAttached) {
                !imagePending && imageError == null && processedImagePath?.let(::File)?.isFile == true
            } else {
                draftOrigin != "Photo"
            }
            val noteDraft = ManualNoteDraft(
                sentence = editor.text,
                target = target,
                sentenceReading = sentenceReading,
                vocabulary = vocabulary,
                vocabularyReading = vocabularyReading,
                definition = definition,
                dictionaryEntryResolved = selectedDictionaryEntry?.let {
                    it.id == selectedDictionaryEntryId && it.expression == vocabulary && it.reading == vocabularyReading
                } == true,
                pitch = pitch,
                vocabularyPartOfSpeech = VocabularyPartOfSpeech.forSelectedSenses(
                    selectedDictionaryEntry?.takeIf {
                        it.id == selectedDictionaryEntryId && it.expression == vocabulary && it.reading == vocabularyReading
                    },
                    selectedSenseIndices,
                ),
                dependentFieldsStale = dependentFieldsStale,
                translation = translation,
                imageFilename = imageReceiptFilename,
                notes = notes,
                sentenceFurigana = sentenceFurigana,
                sentenceAudioFilename = sentenceAudioReceipt,
                vocabularyAudioFilename = wordAudioReceipt,
            )
            val validationErrors = noteDraft.validationErrors()
            val fieldsByName = noteDraft.fieldsByName()
            val modelMapping = selectedModel?.let {
                AnkiFieldContract.mapToModelOrder(it.fields, fieldsByName.orEmpty())
            }
            val frozenForDisplay = completedSubmission ?: if (sendState.locked) {
                sendState.frozen ?: readFrozenSubmission(preferences)
            } else null
            val restoredFrozenForUi = if (frozenRetryReady) frozenForDisplay else null
            val displaySentence = frozenForDisplay?.sentence ?: editor.text
            val displayTarget = frozenForDisplay?.let { snapshot ->
                TargetSpan(snapshot.targetStart, snapshot.targetEnd)
                    .takeIf { it.textIn(snapshot.sentence) != null }
            } ?: target
            val displayVocabulary = frozenForDisplay?.lemma ?: vocabulary
            val displayVocabularyReading = frozenForDisplay?.vocabularyReading ?: vocabularyReading
            val displayDefinition = frozenForDisplay?.definition ?: definition
            val displayTranslation = frozenForDisplay?.translation ?: translation
            val displayNotes = frozenForDisplay?.notes ?: notes
            val displayTags = frozenForDisplay?.tags?.joinToString(" ") ?: tags
            val displayPitch = frozenForDisplay?.pitchAccentText?.toIntOrNull()
                ?.let { PitchAccent.create(displayVocabularyReading, it, pitchHtmlText) } ?: pitch
            val visibleRoute = visibleRouteForAnalyzer(
                requestedRoute = route,
                analyzerReady = analyzerReady,
                frozenCardActive = frozenForDisplay != null,
            )
            val frozenDestinationReady = !frozenRetryReady || (
                restoredFrozenForUi != null && selectedModel?.id == restoredFrozenForUi.modelId &&
                    selectedDeck?.id == restoredFrozenForUi.deckId && selectedModel.fields == restoredFrozenForUi.modelFields
            )
            val effectiveFrozenFields = restoredFrozenForUi?.modelFields?.zip(restoredFrozenForUi.orderedFields)?.toMap()
            val effectiveMapping = selectedModel?.let {
                AnkiFieldContract.mapToModelOrder(it.fields, effectiveFrozenFields ?: fieldsByName.orEmpty())
            }
            val frozenReceiptReady = restoredFrozenForUi?.let { snapshot ->
                val localFile = snapshot.media?.let { File(it.path) }
                val localExists = localFile?.exists() == true
                frozenImagePermitsImport(
                    importedFilename = snapshot.importedFilename,
                    frozenIdentity = snapshot.media,
                    receiptIdentity = receiptIdentity(preferences, IMAGE_RECEIPT_KEYS),
                    receiptFilename = preferences.getString(IMAGE_RECEIPT_FILENAME, null),
                    localFileExists = localExists,
                    localIdentity = if (localExists) liveMediaIdentity(localFile) else null,
                )
            } ?: true
            val sendImageReady = if (frozenRetryReady) frozenReceiptReady else imageReady
            val translationReady = activeTranslationResult() is TranslationResult.Ready &&
                !translationDraft.stale && translationDraft.sentence == editor.text
            val cardPreparationReady = committedSentence == editor.text && translationReady &&
                !dependentFieldsStale && sentenceAudioReady && wordAudioReady && pitch != null &&
                imageReady && !imagePending && validationErrors.isEmpty() &&
                !dictionaryLoading && !dictionarySearchLoading
            val cardNeedsUpdate = cardInvalidated ||
                (target != null && (committedSentence != editor.text || !cardPreparationReady))
            val canSend = apiPermissionGranted && selectedModel != null && selectedDeck != null &&
                (if (frozenRetryReady) effectiveFrozenFields != null else fieldsByName != null && validationErrors.isEmpty()) &&
                effectiveMapping?.isValid == true && sendImageReady && frozenDestinationReady &&
                (frozenRetryReady || (!cardNeedsUpdate && cardPreparationReady)) &&
                (frozenRetryReady || pitch != null) &&
                (frozenRetryReady || (sentenceAudioReady && wordAudioReady)) &&
                (frozenRetryReady || (!dictionaryLoading && !dictionarySearchLoading)) &&
                setupLoadedSuccessfully && !setupLoading && setupError == null &&
                (sendState.editingEnabled || frozenRetryReady)

            // The card has one action: Send. Preparation is automatic, so an invalidated card only
            // greys Send out until every prepared artifact matches the committed sentence again.
            androidx.compose.runtime.LaunchedEffect(
                cardInvalidated, cardPreparationReady, committedSentence, editor.text,
            ) {
                if (!cardInvalidated || !cardPreparationReady || committedSentence != editor.text) return@LaunchedEffect
                cardInvalidated = false
                updateDraft(PreferenceChanges().putBoolean("card_invalidated", false))
            }

            val applyFrozenSubmissionToDraft: (FrozenSubmission) -> Unit = { snapshot ->
                replaceEditorText(snapshot.sentence, true)
                target = TargetSpan(snapshot.targetStart, snapshot.targetEnd)
                    .takeIf { it.textIn(snapshot.sentence) != null }
                vocabulary = snapshot.lemma
                vocabularyReading = snapshot.vocabularyReading
                sentenceReading = snapshot.sentenceReading
                definition = snapshot.definition
                translationDraft = if (translation == snapshot.translation) {
                    translationDraft.copy(sentence = snapshot.sentence)
                } else {
                    TranslationDraft(
                        sentence = snapshot.sentence,
                        result = TranslationResult.Ready(snapshot.translation, TranslationSource.MANUAL),
                        manuallyEdited = true,
                    )
                }
                notes = snapshot.notes
                tags = snapshot.tags.joinToString(" ")
                pitchAccentText = snapshot.pitchAccentText
                if (snapshot.media != null && File(snapshot.media.path).isFile) {
                    noteImagePath = snapshot.media.path
                    processedImagePath = snapshot.media.path
                }
                selectedModelId = snapshot.modelId
                selectedDeckId = snapshot.deckId
                selectedDictionaryEntry = null
                selectedDictionaryEntryId = null
                route = AppRoute.Card
                frozenSnapshotAppliedRevision = snapshot.revision
            }

            androidx.compose.runtime.LaunchedEffect(sendState.locked, sendState.frozen) {
                if (!sendState.locked) return@LaunchedEffect
                val snapshot = sendState.frozen ?: readFrozenSubmission(preferences) ?: return@LaunchedEffect
                if (frozenSnapshotAppliedRevision == snapshot.revision) return@LaunchedEffect
                applyFrozenSubmissionToDraft(snapshot)
            }

            androidx.compose.runtime.LaunchedEffect(
                recoveryRequired, apiPermissionGranted, noteTypes, decks, setupReload,
                selectedModelId, selectedDeckId, setupLoading, setupLoadedSuccessfully, setupError,
            ) {
                if (!recoveryRequired || !apiPermissionGranted || setupLoading || !setupLoadedSuccessfully || setupError != null) return@LaunchedEffect
                val phase = recoveryPhase ?: SubmissionPhase.fromPersisted(
                    preferences.getString(SubmissionRecoveryKeys.STATUS, null),
                ) ?: return@LaunchedEffect
                val snapshot = readFrozenSubmission(preferences)
                if (snapshot == null) {
                    updateRecoveryMessage(
                        this@MainActivity.getString(R.string.card_the_frozen_send_record_is_incomplete_keep),
                    )
                    return@LaunchedEffect
                }
                sendState = sendState.copy(frozen = snapshot)
                if (selectedModelId != snapshot.modelId) {
                    selectedModelId = snapshot.modelId
                    return@LaunchedEffect
                }
                if (selectedDeckId != snapshot.deckId) {
                    selectedDeckId = snapshot.deckId
                    return@LaunchedEffect
                }
                val model = noteTypes.firstOrNull { it.id == snapshot.modelId }
                if (model == null) {
                    updateRecoveryMessage(
                        this@MainActivity.getString(R.string.card_the_frozen_note_type_is_unavailable_confir),
                        RecoveryReason.DestinationUnavailable,
                    )
                    return@LaunchedEffect
                }
                if (model.fields != snapshot.modelFields) {
                    updateRecoveryMessage(
                        this@MainActivity.getString(R.string.card_the_frozen_note_type_fields_changed_confir),
                    )
                    return@LaunchedEffect
                }
                if (phase != SubmissionPhase.NOTE_INSERTED &&
                    phase != SubmissionPhase.DESTINATION_VERIFIED &&
                    phase != SubmissionPhase.NOTE_INSERTING
                ) return@LaunchedEffect
                val checkingInserted = phase == SubmissionPhase.NOTE_INSERTED ||
                    phase == SubmissionPhase.DESTINATION_VERIFIED
                updateRecoveryMessage(
                    if (checkingInserted) {
                        this@MainActivity.getString(R.string.card_checking_the_saved_note_id_and_destination)
                    } else {
                        this@MainActivity.getString(R.string.card_checking_the_frozen_send_revision_in_ankid)
                    },
                )
                when (val outcome = sendCoordinator.recover(sendState.copy(frozen = snapshot))) {
                    is SendOutcome.Created -> {
                        sendState = sendState.copy(
                            phase = SendPhase.Verified,
                            frozen = snapshot,
                            completedNoteId = outcome.noteId,
                            duplicateNoteId = null,
                            recovery = RecoveryReason.None,
                        )
                        verifiedDelivery = true
                        touchFeedbackDispatcher.dispatch(TouchFeedbackEvent.DELIVERY_VERIFIED)
                        submitMessage = this@MainActivity.getString(
                            R.string.card_note_1_d_recovered_in_the_selected_deck,
                            outcome.noteId.toString(),
                        )
                    }
                    is SendOutcome.DestinationUnverified -> updateRecoveryMessage(
                        this@MainActivity.getString(R.string.card_the_saved_note_has_cards_in_another_or_mix),
                    )
                    is SendOutcome.Duplicate -> {
                        updateRecoveryMessage(
                            this@MainActivity.getString(R.string.card_a_matching_note_exists_for_the_frozen_revi),
                            RecoveryReason.MatchingNoteExists,
                            SendPhase.Inserted,
                        )
                        sendState = sendState.copy(duplicateNoteId = outcome.noteId)
                    }
                    SendOutcome.RetryAvailable -> {
                        if (decks.none { it.id == snapshot.deckId }) {
                            updateRecoveryMessage(
                                this@MainActivity.getString(R.string.card_no_matching_note_was_found_but_the_frozen),
                                RecoveryReason.DestinationUnavailable,
                                SendPhase.Preparing,
                            )
                        } else {
                            updateRecoveryMessage(
                                this@MainActivity.getString(R.string.card_no_matching_note_was_found_for_the_frozen),
                                RecoveryReason.None,
                                SendPhase.Preparing,
                            )
                        }
                    }
                    is SendOutcome.Indeterminate -> when (outcome.reason) {
                        IndeterminateReason.MISSING_NOTE_ID -> updateRecoveryMessage(
                            this@MainActivity.getString(R.string.card_the_inserted_note_id_was_not_saved_the_out),
                            RecoveryReason.SavedNoteMissing,
                        )
                        IndeterminateReason.NO_VERIFIABLE_CARDS -> updateRecoveryMessage(
                            this@MainActivity.getString(R.string.card_the_saved_note_id_has_no_verifiable_cards),
                        )
                        IndeterminateReason.LOOKUP_FAILED,
                        IndeterminateReason.OPERATION_FAILED -> updateRecoveryMessage(
                            if (checkingInserted) {
                                this@MainActivity.getString(R.string.card_could_not_verify_the_saved_note_id_retry_t)
                            } else {
                                this@MainActivity.getString(R.string.card_could_not_verify_the_frozen_send_retry_the)
                            },
                            RecoveryReason.Unverifiable,
                            if (checkingInserted) sendState.phase else SendPhase.Inserted,
                        )
                    }
                    SendOutcome.PendingRecovery -> Unit
                    is SendOutcome.InsertFailed,
                    is SendOutcome.MediaFailed,
                    is SendOutcome.NotReady -> Unit
                }
            }

            androidx.compose.runtime.LaunchedEffect(ankiDroidInstalled, apiPermissionGranted, setupReload) {
                if (!ankiDroidInstalled || !apiPermissionGranted) {
                    noteTypes = emptyList()
                    decks = emptyList()
                    setupError = null
                    setupLoadedSuccessfully = false
                    setupLoading = false
                } else {
                    val loadRevision = setupReload
                    noteTypes = emptyList()
                    decks = emptyList()
                    setupLoading = true
                    setupLoadedSuccessfully = false
                    setupError = null
                    try {
                        val data = withContext(Dispatchers.IO) {
                            val gateway = AnkiDroidGateway(contentResolver)
                            gateway.noteTypes() to gateway.decks()
                        }
                        if (currentSetupReload.value != loadRevision) return@LaunchedEffect
                        noteTypes = data.first
                        decks = data.second
                        setupLoadedSuccessfully = true
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        if (currentSetupReload.value == loadRevision) {
                            setupLoadedSuccessfully = false
                            setupError = this@MainActivity.getString(R.string.card_could_not_read_note_types_and_decks_check)
                        }
                    } finally {
                        if (currentSetupReload.value == loadRevision) setupLoading = false
                    }
                }
            }

            androidx.compose.runtime.LaunchedEffect(
                setupLoadedSuccessfully,
                selectedModelId,
                noteTypes,
                ankiDroidInstalled,
                apiPermissionGranted,
            ) {
                if (!setupLoadedSuccessfully || !ankiDroidInstalled || !apiPermissionGranted) return@LaunchedEffect
                val onlyCompatibleModel = compatibleSetupNoteTypes().singleOrNull() ?: return@LaunchedEffect
                if (selectedModelId == onlyCompatibleModel.id) return@LaunchedEffect
                if (updateSettings(PreferenceChanges().putLong(SettingsPreferenceKeys.MODEL_ID, onlyCompatibleModel.id))) {
                    selectedModelId = onlyCompatibleModel.id
                    settingsCoordinator?.let { coordinator ->
                        coordinator.updateValues(coordinator.currentValues().copy(modelId = onlyCompatibleModel.id))
                        syncSettingsScreen(coordinator)
                    }
                }
            }

            androidx.compose.runtime.LaunchedEffect(showSettings, setupVisible) {
                val settingsActive = showSettings || setupVisible
                if (!settingsActive) {
                    settingsCoordinator = null
                    settingsLoading = false
                    return@LaunchedEffect
                }
                if (settingsCoordinator != null) return@LaunchedEffect
                settingsLoading = true
                settingsSaving = false
                settingsError = null
                setupAudioErrorResource = null
                googleKeyVisible = false
                translationTestResult = null
                translationTestRunning = false
                val coordinator = SettingsCoordinator(
                    persistence = PreferenceBackedSettingsPersistence(preferenceStorage),
                    onSaveResult = { result ->
                        coroutineScope.launch(Dispatchers.Main.immediate) {
                            settingsError = if (result.isSuccess) null else this@MainActivity.getString(R.string.save_failed)
                            settingsSaving = false
                            settingsCoordinator?.let { syncSettingsScreen(it, loading = settingsLoading) }
                        }
                    },
                )
                settingsCoordinator = coordinator
                try {
                    val opened = withContext(Dispatchers.IO) { coordinator.open() }
                    googleKey = opened.googleKey
                    audioCacheBytes = withContext(Dispatchers.IO) { reusableAudioCache.sizeBytes() }
                    settingsLoading = false
                    syncSettingsScreen(coordinator)
                    opened.initialVoiceRequest?.let { startVoiceList(coordinator, it) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    settingsLoading = false
                    settingsError = this@MainActivity.getString(R.string.legacy_settings_could_not_load_saved_settings)
                }
            }

            // Obsolete pre-shared-key OpenRouter ciphertext is deleted once per launch. It is never
            // read as a Google key; the shared key, drafts, and media are untouched.
            androidx.compose.runtime.LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) { GoogleCloudCredential.removeObsoleteOpenRouterStorage(preferenceStorage) }
            }

            androidx.compose.runtime.LaunchedEffect(photoSourcePath, noteImagePath, processedImagePath) {
                updateDraft(PreferenceChanges()
                    .putString("photo_source_path", photoSourcePath)
                    .putString("note_image_path", noteImagePath)
                    .putString("processed_image_path", processedImagePath)
                    )
            }

            androidx.compose.runtime.LaunchedEffect(
                draft, editor.selection, pitchHtmlText, definitionManuallyEdited, dependentFieldsStale,
                translationDraft, translationAttemptedSentence, cardInvalidated,
                selectedModelId, selectedDeckId, selectedDictionaryEntryId, selectedSenseIndices, completedSubmission,
            ) {
                if (completedSubmission != null) return@LaunchedEffect
                persistDraftPreferences(
                    storage = preferenceStorage,
                    selectionStart = editor.selection.min,
                    selectionEnd = editor.selection.max,
                    draft = draft,
                    pitchHtmlText = pitchHtmlText,
                    definitionManuallyEdited = definitionManuallyEdited,
                    dependentFieldsStale = dependentFieldsStale,
                    translation = translation,
                    translationDraft = translationDraft,
                    activeTranslationResult = activeTranslationResult(),
                    translationAttemptedSentence = translationAttemptedSentence,
                    cardInvalidated = cardInvalidated,
                    selectedModelId = selectedModelId,
                    selectedDeckId = selectedDeckId,
                    selectedDictionaryEntryId = selectedDictionaryEntryId,
                    selectedSenseIndices = selectedSenseIndices,
                )
            }

            val openKnownNote: (Long) -> Unit = { noteId ->
                val browserIntent = ankiBrowserIntent(noteId)
                if (browserIntent.resolveActivity(packageManager) != null) {
                    startActivity(browserIntent)
                } else {
                    submitMessage = this@MainActivity.getString(R.string.card_ankidroid_browser_is_unavailable_note_1_d, noteId.toString(), displayVocabulary, displaySentence)
                }
            }

            val sendRecoveryContent: @androidx.compose.runtime.Composable () -> Unit = {
                knownNoteId?.let { noteId ->
                    TextButton(onClick = { openKnownNote(noteId) }) {
                        Text(stringResource(R.string.card_open_matching_note_in_ankidroid))
                    }
                }
                if (duplicateBlocked) TextButton(onClick = {
                    val blockedSnapshot = sendState.frozen ?: readFrozenSubmission(preferences)
                    if (blockedSnapshot != null) applyFrozenSubmissionToDraft(blockedSnapshot)
                    clearPendingSubmission(preferenceStorage)
                    sendState = SendState()
                    frozenSnapshotAppliedRevision = -1L
                    submitMessage = null
                    route = AppRoute.Card
                    draftGeneration++
                }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.card_edit_and_recheck))
                }
                if (recoveryRequired && recoveryPhase == SubmissionPhase.NOTE_INSERTING &&
                    (!apiPermissionGranted || setupLoading || setupError != null || !setupLoadedSuccessfully)) {
                    Text(stringResource(R.string.card_recovery_is_held_until_ankidroid_setup_suc), style = MaterialTheme.typography.bodyMedium)
                } else if (recoveryRequired && recoveryDuplicate) {
                    TextButton(onClick = {
                        val confirmedSnapshot = sendState.frozen ?: readFrozenSubmission(preferences)
                        if (confirmedSnapshot == null) {
                            updateRecoveryMessage(
                                this@MainActivity.getString(R.string.card_the_frozen_send_is_unavailable_keep_the_dr),
                                RecoveryReason.MatchingNoteExists,
                            )
                            return@TextButton
                        }
                        persistLastSentTags(confirmedSnapshot)
                        check(updateDraft(PreferenceChanges().putString(SubmissionRecoveryKeys.STATUS, "done"))) {
                            this@MainActivity.getString(R.string.card_resolved_send_state_save_failed)
                        }
                        sendState = sendState.copy(
                            phase = SendPhase.Verified,
                            frozen = confirmedSnapshot,
                            completedNoteId = sendState.duplicateNoteId ?: confirmedSnapshot.noteId,
                            duplicateNoteId = null,
                            recovery = RecoveryReason.None,
                        )
                        submitMessage = this@MainActivity.getString(R.string.card_pending_send_marked_complete)
                    }) { Text(stringResource(R.string.card_treat_pending_send_as_complete)) }
                } else if (recoveryRequired && recoveryPhase == SubmissionPhase.MEDIA_IMPORTING) {
                    val pendingOperation = preferences.getString(SubmissionRecoveryKeys.MEDIA_OPERATION, null)
                        ?.let { name -> runCatching { MediaImportOperation.valueOf(name) }.getOrNull() }
                    TextButton(onClick = {
                        updateRecoveryMessage(
                            this@MainActivity.getString(R.string.card_retrying_the_frozen_media_sequence_may_dup),
                            RecoveryReason.None,
                            SendPhase.Preparing,
                        )
                    }) { Text(stringResource(R.string.card_retry_media_import_may_duplicate)) }
                    if (pendingOperation == MediaImportOperation.IMAGE) {
                        TextButton(onClick = {
                            clearPendingSubmission(preferenceStorage)
                            removeDraftImage()
                            updateRecoveryMessage(
                                this@MainActivity.getString(R.string.card_the_interrupted_image_was_discarded_the_re),
                                RecoveryReason.None,
                                SendPhase.Idle,
                            )
                        }) { Text(stringResource(R.string.card_remove_interrupted_image)) }
                    } else {
                        TextButton(onClick = {
                            clearPendingSubmission(preferenceStorage)
                            audioController.releaseFreeze(retainFiles = true)
                            audioSnapshot = audioController.snapshot()
                            updateRecoveryMessage(
                                this@MainActivity.getString(R.string.card_frozen_recovery_cleared_after_review_the_d),
                                RecoveryReason.None,
                                SendPhase.Idle,
                            )
                            sendState = sendState.copy(frozen = null, duplicateNoteId = null)
                        }) { Text(stringResource(R.string.card_i_checked_clear_frozen_recovery)) }
                    }
                } else if (recoveryRequired && recoveryPhase == SubmissionPhase.MEDIA_IMPORTED) {
                    TextButton(onClick = {
                        updateRecoveryMessage(
                            this@MainActivity.getString(R.string.card_resuming_the_frozen_send_will_reuse_the_sa),
                            RecoveryReason.None,
                            SendPhase.Preparing,
                        )
                    }) { Text(stringResource(R.string.card_resume_frozen_send)) }
                    TextButton(onClick = {
                        clearPendingSubmission(preferenceStorage)
                        removeDraftImage()
                        updateRecoveryMessage(
                            this@MainActivity.getString(R.string.card_pending_send_cleared_after_review_configur),
                            RecoveryReason.None,
                            SendPhase.Idle,
                        )
                    }) { Text(stringResource(R.string.card_i_checked_configure_destination)) }
                } else if (recoveryRequired) {
                    when (sendState.recovery) {
                        RecoveryReason.DestinationUnavailable -> {
                            if (sendState.phase == SendPhase.Preparing) {
                                TextButton(onClick = {
                                    updateRecoveryMessage(
                                        this@MainActivity.getString(R.string.card_retrying_the_frozen_send_the_draft_remains),
                                        RecoveryReason.None,
                                        SendPhase.Preparing,
                                    )
                                }) { Text(stringResource(R.string.card_retry_frozen_send)) }
                            } else {
                                TextButton(onClick = {
                                    clearPendingSubmission(preferenceStorage)
                                    updateRecoveryMessage(
                                        this@MainActivity.getString(R.string.card_outcome_acknowledged_choose_a_valid_destin),
                                        RecoveryReason.None,
                                        SendPhase.Idle,
                                    )
                                }) { Text(stringResource(R.string.card_i_checked_configure_destination)) }
                            }
                        }
                        RecoveryReason.SavedNoteMissing -> {
                            if (knownNoteId != null) {
                                FlowStatusText(
                                    stringResource(R.string.card_the_saved_note_id_cannot_be_found_no_autom),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                TextButton(onClick = {
                                    clearPendingSubmission(preferenceStorage)
                                    updateRecoveryMessage(
                                        this@MainActivity.getString(R.string.card_the_missing_saved_note_outcome_was_explici),
                                        RecoveryReason.None,
                                        SendPhase.Idle,
                                    )
                                }) { Text(stringResource(R.string.card_i_checked_resolve_outcome)) }
                            } else {
                                TextButton(onClick = {
                                    clearPendingSubmission(preferenceStorage)
                                    updateRecoveryMessage(
                                        this@MainActivity.getString(R.string.card_pending_send_held_for_manual_resolution_co),
                                        RecoveryReason.None,
                                        SendPhase.Idle,
                                    )
                                }) { Text(stringResource(R.string.card_i_checked_configure_destination)) }
                            }
                        }
                        RecoveryReason.Unverifiable ->
                            TextButton(onClick = { setupReload++ }) { Text(stringResource(R.string.card_retry_pending_send_check)) }
                        RecoveryReason.MatchingNoteExists,
                        RecoveryReason.MediaInterrupted,
                        RecoveryReason.MediaImported,
                        RecoveryReason.None -> TextButton(onClick = {
                            clearPendingSubmission(preferenceStorage)
                            updateRecoveryMessage(
                                this@MainActivity.getString(R.string.card_pending_send_held_for_manual_resolution_co),
                                RecoveryReason.None,
                                SendPhase.Idle,
                            )
                        }) { Text(stringResource(R.string.card_i_checked_configure_destination)) }
                    }
                }
            }

            androidx.activity.compose.BackHandler(
                enabled = !navigationLocked && (
                    showSettings || showSources || showDictionarySheet || showOcrSheet ||
                        (setupVisible && onboardingComplete && analyzerReady) ||
                        route != AppRoute.Capture
                    ),
            ) {
                when {
                    showSources -> showSources = false
                    showSettings -> closeSettings()
                    setupVisible && onboardingComplete && analyzerReady -> showSetup = false
                    showDictionarySheet -> dismissDictionarySheet()
                    showOcrSheet -> showOcrSheet = false
                    route == AppRoute.Card -> route = AppRoute.Capture
                }
            }
            val darkTheme = isSystemInDarkTheme()
            val view = LocalView.current
            androidx.compose.runtime.LaunchedEffect(darkTheme) {
                val window = (view.context as? android.app.Activity)?.window ?: return@LaunchedEffect
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
            FlowTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    // Portrait composition only: on a rotated or wide window the study's column
                    // stays centred instead of stretching the photo slot and sentence across it.
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentWidth(Alignment.CenterHorizontally)
                            .widthIn(max = AppContentMaxWidth)
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .imePadding(),
                    ) {
                        val reviewTarget = displayTarget?.takeIf { it.textIn(displaySentence) != null }
                        if (visibleRoute == AppRoute.Card) {
                            FlowTopBar(
                                title = stringResource(R.string.card),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                onBack = if (!navigationLocked) ({ route = AppRoute.Capture }) else null,
                                onSettings = if (!navigationLocked) ({ openSettings() }) else null,
                            )
                        }
                        val updateEditor: (TextFieldValue) -> Unit = updateEditor@{ updated ->
                            if (sendState.locked) return@updateEditor
                            if (updated.text != editor.text) {
                                if (photoSourcePath == null) setDraftOrigin("Type")
                                draftGeneration++
                                cardInvalidated = true
                                generationStarted = false
                                updateTranslationSentence(updated.text)
                                if (hasManualDefinition) dependentFieldsStale = true
                                selectTarget(null)
                                draftState.value = draftState.value.withSentence(updated.text)
                                japaneseTokens = emptyList()
                                tokenizationError = null
                                tokenizationLoading = analyzerReady
                            }
                            editor = updated
                            cardScreenHolderRef[0]?.onSentenceChanged(updated.text)
                        }
                        val sentenceAudioField = this@MainActivity.getString(R.string.card_sentence_audio_field)
                        val wordAudioField = this@MainActivity.getString(R.string.card_word_audio_field)
                        val sendBlockers = cardSendBlockers(
                            CardSendBlockerSignals(
                                audioSetupReady = setupReadiness.audioReady,
                                imagePending = imagePending,
                                imageAttached = imageAttached,
                                imageProcessing = imageProcessing,
                                imageError = imageError,
                                imageReady = imageReady,
                                sendImageReady = sendImageReady,
                                frozenRetryReady = frozenRetryReady,
                                sentenceAudioReady = sentenceAudioReady,
                                sentenceAudioField = sentenceAudioField,
                                sentenceAudioBlocker = reviewAudioSendBlocker(
                                    sentenceAudioField,
                                    sentenceAudio.presentation(this@MainActivity.getString(audioActionLabel(AudioItem.SENTENCE))),
                                )?.resolve(this@MainActivity),
                                wordAudioReady = wordAudioReady,
                                wordAudioField = wordAudioField,
                                wordAudioBlocker = reviewAudioSendBlocker(
                                    wordAudioField,
                                    wordAudio.presentation(this@MainActivity.getString(audioActionLabel(AudioItem.VOCABULARY))),
                                )?.resolve(this@MainActivity),
                                apiPermissionGranted = apiPermissionGranted,
                                modelSelected = selectedModel != null,
                                deckSelected = selectedDeck != null,
                                fieldsAvailable = fieldsByName != null,
                                validationError = validationErrors.firstOrNull(),
                                mappingValid = effectiveMapping?.isValid == true,
                                mappingError = effectiveMapping?.errors?.firstOrNull(),
                                frozenDestinationReady = frozenDestinationReady,
                                pitchAvailable = pitch != null,
                                dictionaryLoading = dictionaryLoading,
                                dictionarySearchLoading = dictionarySearchLoading,
                                setupError = setupError,
                                setupLoaded = setupLoadedSuccessfully,
                                setupLoading = setupLoading,
                            ),
                        )
                        val submitHint = when {
                            completedSubmission != null || duplicateBlocked || recoveryRequired -> null
                            isSubmitting -> null
                            submitMessage != null -> null
                            else -> sendBlockers.firstOrNull()
                        }
                        val reviewTranslationError =
                            (activeTranslationResult() as? TranslationResult.Failed)?.message
                        val hintAction = cardHintAction(
                            signals = CardHintActionSignals(
                                translationError = reviewTranslationError,
                                audioSetupReady = setupReadiness.audioReady,
                                sentenceAudioReady = sentenceAudioReady,
                                wordAudioReady = wordAudioReady,
                                apiPermissionGranted = apiPermissionGranted,
                                modelSelected = selectedModel != null,
                                deckSelected = selectedDeck != null,
                                setupError = setupError != null,
                            ),
                            sentenceAudioLabel = this@MainActivity.getString(audioActionLabel(AudioItem.SENTENCE)),
                            wordAudioLabel = this@MainActivity.getString(audioActionLabel(AudioItem.VOCABULARY)),
                            effects = CardHintActionEffects(
                                openSettings = { openSettings() },
                                enterSetup = { enterSetup() },
                                retryTranslation = {
                                    if (committedSentence == editor.text) {
                                        generationStarted = true
                                        translationAutoAllowed = true
                                        translationRetry++
                                    }
                                },
                                retrySentenceAudio = { onAudioAction(AudioItem.SENTENCE) },
                                retryWordAudio = { onAudioAction(AudioItem.VOCABULARY) },
                                editPreparation = { route = AppRoute.Card },
                            ),
                        )
                        val submitHintActionLabel = hintAction.label
                        val submitHintAction = hintAction.invoke
                        val loadMoreMatches: () -> Unit = {
                            loadMoreDictionaryMatches(
                                DictionaryMatchPageRequest(
                                    query = target?.textIn(editor.text),
                                    generation = draftGeneration,
                                    expression = analyzedTarget?.expression,
                                    offset = dictionaryEntries.size,
                                    scope = coroutineScope,
                                    isCurrent = ::asyncDraftIsCurrent,
                                    search = { query, expression, offset ->
                                        offlineDictionary.search(query, expression, offset = offset)
                                    },
                                    setLoading = { dictionarySearchLoading = it },
                                    append = { dictionaryEntries = dictionaryEntries + it },
                                    setHasMore = { dictionaryHasMore = it },
                                    setError = { dictionaryError = it },
                                    errorMessage = this@MainActivity.getString(
                                        R.string.card_could_not_load_more_dictionary_matches,
                                    ),
                                ),
                            )
                        }
                        val submitAtClick: () -> Unit = submitAtClick@{
                            if (!canSend || (sendState.locked && !frozenRetryReady) || sendState.completedNoteId != null ||
                                submitMessage?.startsWith(blockedStatusPrefix) == true
                            ) return@submitAtClick
                            val model = selectedModel ?: return@submitAtClick
                            val deck = selectedDeck ?: return@submitAtClick
                            val restoredFrozen = if (frozenRetryReady) readFrozenSubmission(preferences) else null
                            if (frozenRetryReady && (restoredFrozen == null || restoredFrozen.modelId != model.id ||
                                    restoredFrozen.deckId != deck.id || restoredFrozen.modelFields != model.fields)
                            ) {
                                submitMessage = this@MainActivity.getString(R.string.card_the_frozen_send_destination_is_unavailable)
                                return@submitAtClick
                            }
                            val fields = restoredFrozen?.modelFields?.zip(restoredFrozen.orderedFields)?.toMap()
                                ?: fieldsByName ?: return@submitAtClick
                            val tagSet = restoredFrozen?.tags ?: AnkiFieldContract.parseTags(tags)
                            val request = SendRequest(
                                draft = noteDraft,
                                fieldsByName = fields,
                                model = model,
                                deck = deck,
                                tags = tagSet,
                                pitchAccentText = pitchAccentText,
                                imageAttached = imageAttached,
                                imageProcessing = imageProcessing,
                                processedImagePath = processedImagePath,
                                processedImageIdentity = processedImageIdentity,
                                imageReceiptFilename = imageReceiptFilename,
                                restoredFrozen = restoredFrozen,
                            )
                            draftGeneration++
                            translationRequestRevision++
                            translationAutoAllowed = false
                            if (activeTranslationResult() is TranslationResult.Pending) {
                                translationDraft = translationDraft.requestFinished(
                                    TranslationResult.Failed(this@MainActivity.getString(
                                        R.string.card_translation_paused_while_sending_retry_aft,
                                    )),
                                )
                            }
                            dictionaryLoading = false
                            dictionarySearchLoading = false
                            analyzerLoading = false
                            tokenizationLoading = false
                            pitchLookupLoading = false
                            ocrLoading = false
                            sendState = sendState.copy(
                                phase = SendPhase.Submitting,
                                frozen = restoredFrozen,
                                completedNoteId = null,
                                duplicateNoteId = null,
                                recovery = RecoveryReason.None,
                            )
                            submitMessage = null
                            coroutineScope.launch {
                                try {
                                    val outcome = sendCoordinator.submit(request) { updated ->
                                        sendState = sendState.copy(frozen = updated.frozen)
                                    }
                                    val update = outcome.toUiUpdate(
                                        current = sendState,
                                        context = this@MainActivity,
                                        preferences = preferences,
                                        imageAttached = imageAttached,
                                    )
                                    sendState = update.state
                                    if (update.updateSubmitMessage) submitMessage = update.submitMessage
                                    audioSnapshot = audioController.snapshot()
                                    if (update.deliveryVerified) {
                                        verifiedDelivery = true
                                        touchFeedbackDispatcher.dispatch(TouchFeedbackEvent.DELIVERY_VERIFIED)
                                    }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } finally {
                                    if (sendState.phase == SendPhase.Submitting) {
                                        sendState = sendState.copy(phase = SendPhase.Idle)
                                    }
                                }
                            }
                        }
                        val cardMeaningChoices = DictionaryMeaningChoices.from(dictionaryEntries)
                        val selectedMeaningChoice = DictionaryMeaningChoices.find(
                            choices = cardMeaningChoices,
                            entryId = selectedDictionaryEntryId,
                            expression = vocabulary,
                            reading = vocabularyReading,
                            senseIndex = selectedSenseIndices.singleOrNull(),
                        )
                        val wordAudioPresentation = wordAudio.presentation(
                            this@MainActivity.getString(audioActionLabel(AudioItem.VOCABULARY)),
                        )
                        val cardScreenPresentationContext = cardScreenPresentationContext(
                            analyzerError = tokenizationError,
                            analyzing = tokenizationLoading,
                            completed = completedSubmission != null,
                            duplicateBlocked = duplicateBlocked,
                            recoveryRequired = recoveryRequired,
                            submitting = isSubmitting,
                            sendBlocked = submitMessage?.startsWith(blockedStatusPrefix) == true,
                            submitMessage = submitMessage,
                            submitHint = submitHint,
                            submitHintActionLabel = submitHintActionLabel,
                            editPreparationLabel = this@MainActivity.getString(R.string.card_edit_preparation),
                            translationFailure = reviewTranslationError?.let {
                                this@MainActivity.getString(reviewTranslationFailureStatus(it).headlineResource)
                            },
                            wordAudioGenerating = wordAudioPresentation == ReviewAudioPresentation.Generating,
                            hasTarget = target != null,
                            actionHasTarget = reviewTarget != null,
                            canSend = canSend,
                            submissionLocked = sendState.locked,
                            editingEnabled = sendState.editingEnabled,
                            frozenRetryReady = frozenRetryReady,
                            recoveryMessage = recoveryMessage,
                        )
                        val cardScreenHolder = rememberCardScreenStateHolder(
                            sentence = displaySentence,
                            selectedSpan = reviewTarget,
                            meaningChoice = selectedMeaningChoice,
                            committedSentence = committedSentence,
                            presentationContext = cardScreenPresentationContext,
                            canSelectWords = { sendState.editingEnabled },
                            isSubmissionLocked = { sendState.locked },
                            onWordTapped = { span -> selectTarget(span) },
                            onMeaningChosen = { choice -> commitDictionaryChoice(choice) },
                            dispatchPrimaryAction = { action ->
                                cardActionClickHandler(
                                    action = action,
                                    sendState = sendState,
                                    frozenRetryReady = frozenRetryReady,
                                    onSubmit = submitAtClick,
                                    onRetryAnalysis = { analysisRetry++ },
                                    onRecovery = submitHintAction,
                                ).invoke()
                            },
                        )
                        cardScreenHolderRef[0] = cardScreenHolder
                        when (visibleRoute) {
                            AppRoute.Capture -> InputScreen(
                                captureMode = captureMode,
                                onCaptureModeChange = { mode ->
                                    captureMode = mode
                                    updateDraft(PreferenceChanges().putString("capture_mode", mode))
                                },
                                editor = editor,
                                onEditorChange = updateEditor,
                                onContinue = {
                                    if (analyzerReady && commitSentence()) {
                                        continueToCard()
                                    }
                                },
                                onOpenGallery = {
                                    photoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                    )
                                },
                                onChoosePhoto = {
                                    noteImagePicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                    )
                                },
                                onCaptured = { file -> cameraError = null; requestPhoto(file) },
                                onCameraError = { message -> cameraError = message },
                                onCaptureInProgressChanged = { captureInProgress = it },
                                ocrChoicesAvailable = ocrChoices.size > 1,
                                onReopenOcrChooser = { showOcrSheet = ocrChoices.size > 1 },
                                outputDirectory = File(filesDir, "draft").apply { mkdirs() },
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                enabled = !sendState.locked,
                                cameraPermissionGranted = cameraPermissionGranted,
                                onRequestCamera = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                                cameraError = cameraError,
                                ocrLoading = ocrLoading,
                                ocrError = ocrError,
                                onRetryOcr = {
                                    val source = photoSourcePath?.let(::File)?.takeIf(File::isFile)
                                    if (source != null) processImage(source, true)
                                    else photoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                    )
                                },
                                imageStatus = when {
                                    imageProcessing -> stringResource(R.string.legacy_capture_preparing_photo_for_anki)
                                    imageError != null -> imageError
                                    imageAttached -> stringResource(R.string.legacy_capture_photo_attached)
                                    else -> null
                                },
                                imageFailed = !imageProcessing && imageError != null,
                                onRetryImage = {
                                    val source = photoSourcePath?.let(::File)?.takeIf(File::isFile)
                                    if (source != null) processImage(source, true)
                                    else photoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                    )
                                },
                                confirmationMessage = captureConfirmationMessage,
                                onSettings = { openSettings() },
                            )
                            AppRoute.Card -> {
                                val sentenceAudioPresentation = sentenceAudio.presentation(
                                    this@MainActivity.getString(audioActionLabel(AudioItem.SENTENCE)),
                                )
                                val cardDetails = CardScreenDetailInput(
                                    selectedSpan = reviewTarget,
                                    dictionaryLoading = dictionaryLoading,
                                    dictionarySearchLoading = dictionarySearchLoading,
                                    dictionaryError = dictionaryError,
                                    selectedEntry = selectedDictionaryEntry,
                                    selectedEntryId = selectedDictionaryEntryId,
                                    vocabulary = vocabulary,
                                    vocabularyReading = vocabularyReading,
                                    meaningChoices = cardMeaningChoices,
                                    selectedMeaning = selectedMeaningChoice,
                                    dictionaryReady = dictionaryReady,
                                    vocabularyReadingError = vocabularyReadingError,
                                    wordAudio = wordAudioPresentation,
                                    pitch = displayPitch,
                                    pitchLookupLoading = pitchLookupLoading,
                                    pitchIndexError = pitchIndexError,
                                    selectedSenseIndices = selectedSenseIndices,
                                    sendLocked = sendState.locked,
                                )
                                CardScreenContent(
                                    holder = cardScreenHolder,
                                    model = CardScreenContentModel(
                                        sentence = displaySentence,
                                        tokens = japaneseTokens,
                                        selectionEnabled = sendState.editingEnabled,
                                        onSelectSpan = { span ->
                                            if (sendState.editingEnabled) {
                                                cardScreenHolder.onWordTapped(span)
                                                editor = editor.copy(selection = TextRange(span.start, span.end))
                                            }
                                        },
                                        furigana = if (frozenForDisplay == null) sentenceFurigana else emptyList(),
                                        onEditSentence = { if (!sendState.locked) showSentenceEditor = true },
                                        translation = displayTranslation,
                                        vocabulary = displayVocabulary,
                                        vocabularyReading = displayVocabularyReading,
                                        details = cardDetails,
                                        definition = displayDefinition,
                                        pitch = displayPitch,
                                        onChangeMeaning = {
                                            if (!sendState.locked && cardMeaningChoices.size > 1) showDictionarySheet = true
                                        },
                                        imagePath = frozenForDisplay?.media?.path ?: processedImagePath.takeIf { imageReady },
                                        imageFocusY = photoFocusY,
                                        notes = displayNotes,
                                        tags = displayTags,
                                        onNotesChange = { text -> if (sendState.editingEnabled) notes = text },
                                        onTagsChange = { text -> if (sendState.editingEnabled) tags = text },
                                        sentenceAudio = sentenceAudioPresentation,
                                        wordAudio = wordAudioPresentation,
                                        onToggleSentenceAudio = {
                                            if (playingAudio == AudioItem.SENTENCE) {
                                                stopAudioPlayback()
                                                playingAudio = null
                                            } else {
                                                audioReadyFile(AudioItem.SENTENCE)?.let { playAudio(AudioItem.SENTENCE, it) }
                                            }
                                        },
                                        onRetrySentenceAudio = { onAudioAction(AudioItem.SENTENCE) },
                                        onToggleWordAudio = {
                                            if (playingAudio == AudioItem.VOCABULARY) {
                                                stopAudioPlayback()
                                                playingAudio = null
                                            } else {
                                                audioReadyFile(AudioItem.VOCABULARY)?.let { playAudio(AudioItem.VOCABULARY, it) }
                                            }
                                        },
                                        onRetryWordAudio = { onAudioAction(AudioItem.VOCABULARY) },
                                        actionLoading = isSubmitting,
                                        statusContent = {
                                            if (duplicateBlocked || recoveryRequired) sendRecoveryContent()
                                        },
                                        modifier = Modifier.weight(1f).fillMaxWidth(),
                                    ),
                                )
                            }
                        }
                        if (showSentenceEditor) {
                            CardSentenceEditorSheet(
                                draft = sentenceEditorDraft,
                                editor = editor,
                                onDraftChanged = { sentenceEditorDraft = it },
                                onDismiss = {
                                    sentenceEditorDraft = null
                                    showSentenceEditor = false
                                },
                                onUseSelection = { edited ->
                                    useCardSentenceSelectionAsTarget(
                                        edited,
                                        updateEditor,
                                        { sentence -> commitSentence(sentence) },
                                        selectTarget,
                                    ) {
                                        sentenceEditorDraft = null
                                        showSentenceEditor = false
                                    }
                                },
                                onDone = { edited ->
                                    commitCardSentenceEdit(
                                        edited,
                                        updateEditor,
                                        { sentence -> commitSentence(sentence) },
                                    ) {
                                        sentenceEditorDraft = null
                                        showSentenceEditor = false
                                    }
                                },
                                selectionHint = stringResource(
                                    R.string.legacy_settings_select_part_of_the_sentence_then_use_it_as,
                                ),
                                useSelectionLabel = stringResource(
                                    R.string.legacy_settings_use_selection_as_target,
                                ),
                            )
                        }
                        CardDeckSheet(
                            visible = showDeckSheet,
                            decks = decks,
                            title = stringResource(R.string.legacy_settings_destination_deck),
                            emptyMessage = stringResource(
                                R.string.legacy_settings_no_decks_are_available_check_ankidroid_set,
                            ),
                            onDismiss = { showDeckSheet = false },
                            onSelect = { deck ->
                                selectedDeckId = deck.id
                                updateDraft(PreferenceChanges().putLong("deck_id", deck.id))
                                showDeckSheet = false
                            },
                        )
                        CardNoTargetRecoveryContent(
                            visible = visibleRoute == AppRoute.Card && reviewTarget == null,
                            submitMessage = submitMessage,
                            recoveryMessage = recoveryMessage,
                            recoveryContent = { sendRecoveryContent() },
                        )


                    }
                }
                val meaningChoices = DictionaryMeaningChoices.from(dictionaryEntries)
                CardMeaningPicker(
                    visible = showDictionarySheet,
                    targetAvailable = target?.textIn(editor.text) != null,
                    choices = meaningChoices,
                    enabled = !sendState.locked,
                    selectedEntryId = selectedDictionaryEntryId,
                    vocabulary = vocabulary,
                    vocabularyReading = vocabularyReading,
                    selectedSenseIndices = selectedSenseIndices,
                    onDismiss = { dismissDictionarySheet() },
                    onChoose = { choice -> checkNotNull(cardScreenHolderRef[0]).onMeaningChosen(choice) },
                )
            }
        }
        }
    }
    }

    override fun onResume() {
        super.onResume()
        ankiDroidInstalled = isAnkiDroidInstalled()
        apiPermissionGranted = ankiDroidInstalled &&
            checkSelfPermission(ANKIDROID_READ_WRITE_PERMISSION) == PackageManager.PERMISSION_GRANTED
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedText.value = textFromIntent(intent)
        sharedImage.value = imageFromIntent(intent)
    }

    private fun isAnkiDroidInstalled(): Boolean = try {
        packageManager.getPackageInfo("com.ichi2.anki", PackageManager.PackageInfoFlags.of(0))
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun textFromIntent(intent: Intent?): String? {
        if (intent == null) return null
        val value = when (intent.action) {
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
            Intent.ACTION_SEND -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
            else -> null
        }
        return value?.toString()?.takeIf(String::isNotBlank)
    }

    private fun imageFromIntent(intent: Intent?): Uri? = when (intent?.action) {
        Intent.ACTION_SEND -> intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            ?.takeIf { intent.type?.startsWith("image/") == true }
        else -> null
    }

    private fun clearConsumedShareIntent(expectedText: String?, expectedImage: Uri?): Boolean {
        val currentIntent = intent ?: return false
        if (!intentPayloadMatches(
                textFromIntent(currentIntent), imageFromIntent(currentIntent)?.toString(),
                expectedText, expectedImage?.toString(),
            )
        ) return false
        setIntent(Intent(currentIntent).apply {
            action = null
            type = null
            data = null
            replaceExtras(Bundle())
        })
        return true
    }

    private fun copyImageToDraft(uri: Uri): File {
        val directory = File(filesDir, "draft").apply { check(isDirectory || mkdirs()) }
        val destination = File(directory, "source-${java.util.UUID.randomUUID()}.image")
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                destination.outputStream().use(input::copyTo)
            } ?: error("Could not open shared image")
            check(destination.isFile && destination.length() > 0) { "Shared image is empty" }
            return destination
        } catch (error: Exception) {
            destination.delete()
            throw error
        }
    }

    companion object {
        @Volatile private var dictionaryInstance: OfflineDictionary? = null

        private fun sharedDictionary(context: Context): OfflineDictionary =
            dictionaryInstance ?: synchronized(this) {
                dictionaryInstance ?: OfflineDictionary(context).also { dictionaryInstance = it }
            }
    }
}

internal fun initialDraftTags(draftTags: String?, lastNoteTags: String?): String =
    draftTags ?: lastNoteTags ?: ""

internal fun formatCacheSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
}


private fun clearPendingSubmission(storage: AppPreferenceStorage) {
    storage.update(PreferenceFile.DRAFT, clearFrozenSubmission())
}

private fun TextRange.coerceIn(min: Int, max: Int): TextRange =
    TextRange(start.coerceIn(min, max), end.coerceIn(min, max))
