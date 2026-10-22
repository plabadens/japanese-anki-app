package dev.pierre.japaneseanki

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.media.MediaPlayer
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.semantics.semantics
import dev.pierre.japaneseanki.ui.ReviewCard
import dev.pierre.japaneseanki.ui.AppContentMaxWidth
import dev.pierre.japaneseanki.ui.ReviewSendPresentation
import dev.pierre.japaneseanki.ui.ReviewStatusRole
import dev.pierre.japaneseanki.ui.ReviewAudioPresentation
import dev.pierre.japaneseanki.ui.reviewAudioGenerationFailureMessage
import dev.pierre.japaneseanki.ui.reviewAudioPlaybackFailureMessage
import dev.pierre.japaneseanki.ui.reviewAudioSendBlocker
import dev.pierre.japaneseanki.ui.FlowTheme
import dev.pierre.japaneseanki.ui.FlowTopBar
import dev.pierre.japaneseanki.ui.InputScreen
import dev.pierre.japaneseanki.ui.SettingsScreen
import dev.pierre.japaneseanki.ui.SettingsScreenState
import dev.pierre.japaneseanki.ui.TargetScreen
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation

private const val ANKIDROID_READ_WRITE_PERMISSION =
    "com.ichi2.anki.permission.READ_WRITE_DATABASE"
private const val IMAGE_RECEIPT_PATH = "image_import_receipt_path"
private const val IMAGE_RECEIPT_FILENAME = "image_import_receipt_filename"
private const val IMAGE_RECEIPT_SIZE = "image_import_receipt_size"
private const val IMAGE_RECEIPT_MODIFIED = "image_import_receipt_modified"
private const val VOICE_CACHE_PREF = "google_voice_cache"

/** Fixed, disclosed sample for the explicit translation check; one billable request. */
private const val TRANSLATION_TEST_SAMPLE = "今日はいい天気ですね。"
private const val SENTENCE_AUDIO_RECEIPT_PATH = "sentence_audio_receipt_path"
private const val SENTENCE_AUDIO_RECEIPT_FILENAME = "sentence_audio_receipt_filename"
private const val SENTENCE_AUDIO_RECEIPT_SIZE = "sentence_audio_receipt_size"
private const val SENTENCE_AUDIO_RECEIPT_MODIFIED = "sentence_audio_receipt_modified"
private const val WORD_AUDIO_RECEIPT_PATH = "word_audio_receipt_path"
private const val WORD_AUDIO_RECEIPT_FILENAME = "word_audio_receipt_filename"
private const val WORD_AUDIO_RECEIPT_SIZE = "word_audio_receipt_size"
private const val WORD_AUDIO_RECEIPT_MODIFIED = "word_audio_receipt_modified"

/** SharedPreferences adapter for [DraftAudioController]: commit, never apply. */
private class SharedPreferencesDraftAudioPersistence(
    private val preferences: SharedPreferences,
) : DraftAudioPersistence {
    override fun read(key: String): String? = preferences.getString(key, null)

    override fun write(changes: Map<String, String?>): Boolean {
        val editor = preferences.edit()
        for ((key, value) in changes) {
            if (value == null) editor.remove(key) else editor.putString(key, value)
        }
        return editor.commit()
    }
}

private val IMAGE_RECEIPT_KEYS = ImportedReceiptKeys(
    path = IMAGE_RECEIPT_PATH,
    filename = IMAGE_RECEIPT_FILENAME,
    size = IMAGE_RECEIPT_SIZE,
    modified = IMAGE_RECEIPT_MODIFIED,
)

private val SENTENCE_AUDIO_RECEIPT_KEYS = ImportedReceiptKeys(
    path = SENTENCE_AUDIO_RECEIPT_PATH,
    filename = SENTENCE_AUDIO_RECEIPT_FILENAME,
    size = SENTENCE_AUDIO_RECEIPT_SIZE,
    modified = SENTENCE_AUDIO_RECEIPT_MODIFIED,
)

private val WORD_AUDIO_RECEIPT_KEYS = ImportedReceiptKeys(
    path = WORD_AUDIO_RECEIPT_PATH,
    filename = WORD_AUDIO_RECEIPT_FILENAME,
    size = WORD_AUDIO_RECEIPT_SIZE,
    modified = WORD_AUDIO_RECEIPT_MODIFIED,
)

/** Persisted receipt identity, read without touching the file system. */
private fun receiptIdentity(preferences: SharedPreferences, keys: ImportedReceiptKeys): MediaIdentity? =
    preferences.getString(keys.path, null)?.let { path ->
        MediaIdentity(path, preferences.getLong(keys.size, -1L), preferences.getLong(keys.modified, -1L))
    }

private fun mediaImportRequest(
    preferences: SharedPreferences,
    keys: ImportedReceiptKeys,
    operation: MediaImportOperation,
    source: MediaIdentity,
    frozenFilename: String?,
): MediaImportRequest {
    val hasSavedReceipt = listOf(keys.path, keys.filename, keys.size, keys.modified).any(preferences::contains)
    val savedReceipt = when {
        hasSavedReceipt -> ImportedMediaReceipt(
            operation = operation,
            source = receiptIdentity(preferences, keys) ?: MediaIdentity("", -1L, -1L),
            filename = preferences.getString(keys.filename, null).orEmpty(),
        )
        frozenFilename != null -> ImportedMediaReceipt(operation, source, frozenFilename)
        else -> null
    }
    val receipt = if (frozenFilename != null && savedReceipt != null && savedReceipt.filename != frozenFilename) {
        savedReceipt.copy(source = MediaIdentity("", -1L, -1L))
    } else {
        savedReceipt
    }
    return MediaImportRequest(source, receipt)
}

internal fun applyImportedReceipt(snapshot: FrozenSubmission, receipt: ImportedMediaReceipt): FrozenSubmission {
    val (fieldName, serialized, updatedSnapshot) = when (receipt.operation) {
        MediaImportOperation.IMAGE -> Triple(
            "Image",
            AnkiFieldSerializer.imageReference(receipt.filename),
            snapshot.copy(importedFilename = receipt.filename),
        )
        MediaImportOperation.SENTENCE_AUDIO -> Triple(
            "SentAudio",
            AnkiFieldSerializer.audioReference(receipt.filename),
            snapshot.copy(sentenceAudio = snapshot.sentenceAudio.copy(importedFilename = receipt.filename)),
        )
        MediaImportOperation.WORD_AUDIO -> Triple(
            "VocabAudio",
            AnkiFieldSerializer.audioReference(receipt.filename),
            snapshot.copy(wordAudio = snapshot.wordAudio.copy(importedFilename = receipt.filename)),
        )
        MediaImportOperation.AUDIO -> throw IllegalArgumentException("Generic audio is not a Send operation.")
    }
    val valuesByName = snapshot.modelFields.zip(snapshot.orderedFields).toMap().toMutableMap()
    require(fieldName in valuesByName && serialized != null) { "The imported media reference cannot be serialized." }
    valuesByName[fieldName] = serialized
    val mapping = AnkiFieldContract.mapToModelOrder(snapshot.modelFields, valuesByName)
    require(mapping.isValid) { mapping.errors.joinToString() }
    return updatedSnapshot.copy(orderedFields = mapping.values)
}

private fun putMediaReceipt(editor: SharedPreferences.Editor, receipt: ImportedMediaReceipt): SharedPreferences.Editor {
    val keys = when (receipt.operation) {
        MediaImportOperation.IMAGE -> IMAGE_RECEIPT_KEYS
        MediaImportOperation.SENTENCE_AUDIO -> SENTENCE_AUDIO_RECEIPT_KEYS
        MediaImportOperation.WORD_AUDIO -> WORD_AUDIO_RECEIPT_KEYS
        MediaImportOperation.AUDIO -> throw IllegalArgumentException("Generic audio is not a Send operation.")
    }
    return editor.putString(keys.path, receipt.source.path)
        .putLong(keys.size, receipt.source.size)
        .putLong(keys.modified, receipt.source.modified)
        .putString(keys.filename, receipt.filename)
}

/** Every preference key the draft-audio controller owns, for a full draft reset. */
private fun draftAudioPreferenceKeys(): List<String> = buildList {
    add(DraftAudioPreferences.VERSION)
    add(DraftAudioPreferences.EPOCH)
    for (item in AudioItem.entries) {
        add(DraftAudioPreferences.identity(item))
        add(DraftAudioPreferences.status(item))
        add(DraftAudioPreferences.path(item))
        add(DraftAudioPreferences.format(item))
        add(DraftAudioPreferences.size(item))
        add(DraftAudioPreferences.modified(item))
        add(DraftAudioPreferences.frozenPath(item))
    }
}

private fun pitchUnavailableMessage(failure: AjtPitchFailure): String = when (failure) {
    AjtPitchFailure.MISSING_INDEX ->
        "The bundled AJT pitch index is missing; pitch-dependent notes cannot be mined."
    AjtPitchFailure.EMPTY_INDEX ->
        "The AJT pitch index contains no records; pitch-dependent notes cannot be mined."
    AjtPitchFailure.INVALID_INDEX ->
        "The AJT pitch index is unusable and was discarded; tap Retry to reinstall it."
    AjtPitchFailure.OPEN_FAILED ->
        "The AJT pitch index could not be opened; tap Retry to reinstall it."
}

/** Per-item audio state for the current draft revision. */
internal data class DraftAudio(
    val path: String? = null,
    val generating: Boolean = false,
    val error: String? = null,
    val advisoryHeadline: String? = null,
    val advisoryMessage: String? = null,
    val identity: String = "",
    val needsContinue: Boolean = false,
) {
    fun presentation(actionLabel: String = "Retry"): ReviewAudioPresentation = when {
        generating -> ReviewAudioPresentation.Generating
        path != null -> ReviewAudioPresentation.Ready
        advisoryHeadline != null && advisoryMessage != null ->
            ReviewAudioPresentation.Advisory(advisoryHeadline, advisoryMessage, actionLabel)
        error != null -> ReviewAudioPresentation.Failed(error)
        needsContinue -> ReviewAudioPresentation.NeedsPreparation
        else -> ReviewAudioPresentation.Unavailable
    }
}

/** UI view of the controller's per-item state; the controller stays the only source of truth. */
private fun DraftAudioState.toDraftAudio(item: AudioItem): DraftAudio = when (this) {
    DraftAudioState.Idle -> DraftAudio()
    is DraftAudioState.NeedsContinue -> DraftAudio(identity = identity, needsContinue = true)
    is DraftAudioState.Generating -> DraftAudio(generating = true, identity = identity)
    is DraftAudioState.Ready -> DraftAudio(path = file.path, identity = identity)
    is DraftAudioState.Failed -> DraftAudio(
        error = reviewAudioGenerationFailureMessage(
            if (item == AudioItem.SENTENCE) "Sentence" else "Word",
            message,
        ),
        identity = identity,
    )
}

enum class AppRoute { Capture, Target, Review }

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
    submissionLocked: Boolean,
): Boolean = !submissionLocked && callbackGeneration == currentGeneration

internal fun ocrResultIsCurrent(
    callbackGeneration: Long,
    currentGeneration: Long,
    callbackEditorRevision: Long,
    currentEditorRevision: Long,
    submissionLocked: Boolean,
): Boolean = draftCallbackIsCurrent(callbackGeneration, currentGeneration, submissionLocked) &&
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

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedText.value = textFromIntent(intent)
        sharedImage.value = imageFromIntent(intent)
        offlineDictionary = sharedDictionary(this)
        offlineAnalyzer = OfflineJapaneseAnalyzer.shared(this)
        japaneseOcr = JapaneseOcr()
        ajtPitchRepository = AjtPitchRepository.shared(this)
        val preferences = getSharedPreferences("draft", MODE_PRIVATE)
        val settings = getSharedPreferences("settings", MODE_PRIVATE)
        if (preferences.getString(SubmissionRecoveryKeys.STATUS, null) == "done") {
            clearPersistedDraft(preferences, File(filesDir, "draft"))
        }
        setContent {
            val coroutineScope = rememberCoroutineScope()
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
            var target by remember {
                mutableStateOf(
                    TargetSpan(
                        preferences.getInt("target_start", -1),
                        preferences.getInt("target_end", -1),
                    ).takeIf { it.textIn(editor.text) != null },
                )
            }
            var vocabulary by remember { mutableStateOf(preferences.getString("vocabulary", "").orEmpty()) }
            var vocabularyReading by remember { mutableStateOf(preferences.getString("vocabulary_reading", "").orEmpty()) }
            var vocabularyReadingError by remember { mutableStateOf<String?>(null) }
            var pitchAccentText by remember { mutableStateOf(preferences.getString("pitch_accent", "").orEmpty()) }
            var pitchHtmlText by remember { mutableStateOf(preferences.getString("pitch_html", null)) }
            var pitchIndexError by remember { mutableStateOf<String?>(null) }
            val pitch = pitchAccentText.toIntOrNull()?.let {
                PitchAccent.create(vocabularyReading, it, pitchHtmlText)
            }
            var definition by remember { mutableStateOf(preferences.getString("definition", "").orEmpty()) }
            var definitionManuallyEdited by remember { mutableStateOf(preferences.getBoolean("definition_manual", false)) }
            var definitionProvenanceKnown by remember { mutableStateOf(preferences.contains("definition_manual")) }
            var sentenceReading by remember { mutableStateOf(preferences.getString("sentence_reading", "").orEmpty()) }
            var readingCorrections by remember {
                mutableStateOf(
                    SentenceReadings.decode(
                        preferences.getString("sentence_reading_corrections", null),
                        preferences.getString("sentence", "").orEmpty(),
                    ),
                )
            }
            var readingCorrectionError by remember { mutableStateOf<String?>(null) }
            var dependentFieldsStale by remember {
                mutableStateOf(preferences.getBoolean("definition_manual_stale", false) &&
                    preferences.getBoolean("definition_manual", false))
            }
            var showDictionarySheet by remember { mutableStateOf(false) }
            val restoredTranslationText = preferences.getString("translation", "").orEmpty()
            val restoredTranslationSource = preferences.getString("translation_source", null)
                ?.let { saved -> TranslationSource.entries.firstOrNull { it.name == saved } }
            val restoredTranslationWasPending = preferences.getBoolean("translation_pending", false)
            val restoredTranslationError = preferences.getString("translation_error", null)
            val restoredSuggestionError = preferences.getString("translation_suggestion_error", null)
            val restoredTranslationManual = preferences.getBoolean(
                "translation_manual",
                restoredTranslationSource == TranslationSource.MANUAL ||
                    (restoredTranslationText.isNotBlank() && restoredTranslationSource == null),
            )
            val restoredTranslationStale = preferences.getBoolean("translation_stale", false)
            val restoredSuggestionText = preferences.getString("translation_suggestion", null)
            val restoredSuggestionSource = preferences.getString("translation_suggestion_source", null)
                ?.let { saved -> TranslationSource.entries.firstOrNull { it.name == saved } }
            val interruptedTranslation = TranslationResult.Failed("Translation was interrupted. Tap Retry to continue.")
            var translationDraft by remember {
                mutableStateOf(
                    TranslationDraft(
                        sentence = preferences.getString(
                            "translation_sentence",
                            preferences.getString("sentence", "").orEmpty(),
                        ).orEmpty(),
                        result = when {
                            restoredTranslationManual -> TranslationResult.Ready(restoredTranslationText, TranslationSource.MANUAL)
                            restoredTranslationSource != null && restoredTranslationText.isNotBlank() ->
                                TranslationResult.Ready(restoredTranslationText, restoredTranslationSource)
                            restoredTranslationText.isNotBlank() -> TranslationResult.Ready(restoredTranslationText, TranslationSource.MANUAL)
                            restoredTranslationWasPending -> interruptedTranslation
                            restoredTranslationError != null -> TranslationResult.Failed(restoredTranslationError)
                            else -> TranslationResult.Pending
                        },
                        manuallyEdited = restoredTranslationManual,
                        stale = restoredTranslationStale,
                        suggestion = when {
                            restoredSuggestionText != null && restoredSuggestionSource != null ->
                                TranslationResult.Ready(restoredSuggestionText, restoredSuggestionSource)
                            restoredTranslationWasPending && restoredTranslationManual && restoredTranslationStale ->
                                interruptedTranslation
                            restoredSuggestionError != null -> TranslationResult.Failed(restoredSuggestionError)
                            else -> null
                        },
                    ),
                )
            }
            val translation = (translationDraft.result as? TranslationResult.Ready)?.text.orEmpty()
            var translationAttemptedSentence by remember {
                mutableStateOf(preferences.getString("translation_attempted_sentence", null))
            }
            var translationAutoAllowed by remember {
                mutableStateOf(preferences.getString("sentence", "").isNullOrBlank())
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
                translationAutoAllowed = true
                translationDraft = translationDraft.sentenceChanged(sentence)
            }
            val activeTranslationResult: () -> TranslationResult = {
                if (translationDraft.manuallyEdited && translationDraft.stale) {
                    translationDraft.suggestion ?: TranslationResult.Pending
                } else translationDraft.result
            }
            var notes by remember { mutableStateOf(preferences.getString("notes", "").orEmpty()) }
            var tags by remember {
                mutableStateOf(
                    seedFreshNoteTags(
                        preferences.getString("tags", null),
                        settings.getString(SettingsPreferenceKeys.DEFAULT_TAGS, "").orEmpty(),
                    ),
                )
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
            var showSettings by remember { mutableStateOf(!onboardingComplete) }
            var showSentenceEditor by remember { mutableStateOf(false) }
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
            var analyzerStatus by remember { mutableStateOf("Checking UniDic runtime archive") }
            var analyzerRetry by remember { mutableStateOf(0) }
            var analyzerError by remember { mutableStateOf<String?>(null) }
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
            var reviewAuthorizationSequence by remember {
                mutableStateOf(preferences.getLong("review_authorization_sequence", 0L).coerceAtLeast(0L))
            }
            var pendingReviewAuthorization by remember { mutableStateOf<Long?>(null) }
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
            val audioPersistence = remember { SharedPreferencesDraftAudioPersistence(preferences) }
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
            val reviewAudioCoordinator = remember(audioController) {
                ReviewAudioGenerationCoordinator(audioController, audioPersistence)
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
                DraftAudioIdentity.sentence(
                    editor.text,
                    target,
                    sentenceReading,
                    sentenceFurigana,
                    selectedVoiceName,
                ),
            )

            fun audioView(item: AudioItem): DraftAudio {
                audioConfigErrors[item]?.let {
                    val field = if (item == AudioItem.SENTENCE) "Sentence" else "Word"
                    return DraftAudio(
                        advisoryHeadline = "$field audio setup needed",
                        advisoryMessage = it.substringBefore(". ").trimEnd('.') + ".",
                    )
                }
                audioPlaybackErrors[item]?.let { return DraftAudio(error = it) }
                return (audioSnapshot.items[item] ?: DraftAudioState.Idle).toDraftAudio(item)
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
                    AppRoute.entries.firstOrNull { it.name == preferences.getString("route", null) }
                        ?: when {
                            editor.text.isBlank() -> AppRoute.Capture
                            target != null && preferences.getLong("dictionary_entry_id", -1) > 0 -> AppRoute.Review
                            else -> AppRoute.Target
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
            var cameraError by remember { mutableStateOf<String?>(null) }
            var captureInProgress by remember { mutableStateOf(false) }
            var showModelMenu by remember { mutableStateOf(false) }
            var showDeckMenu by remember { mutableStateOf(false) }
            var isSubmitting by remember { mutableStateOf(false) }
            var duplicateBlocked by remember { mutableStateOf(false) }
            var submitMessage by remember { mutableStateOf<String?>(null) }
            val restoredPhase = SubmissionPhase.fromPersisted(
                preferences.getString(SubmissionRecoveryKeys.STATUS, null),
            )
            var recoveryRequired by remember { mutableStateOf(restoredPhase != null) }
            var recoveryPhase by remember { mutableStateOf(restoredPhase) }
            var frozenRetryReady by remember { mutableStateOf(false) }
            var recoveryMessage by remember {
                mutableStateOf(
                    when (restoredPhase) {
                        SubmissionPhase.MEDIA_IMPORTING -> {
                            val operation = preferences.getString(SubmissionRecoveryKeys.MEDIA_OPERATION, null)
                                ?.let { name -> runCatching { MediaImportOperation.valueOf(name) }.getOrNull() }
                            "${operation?.label?.replaceFirstChar { it.uppercase() } ?: "Media preparation"} was interrupted before its receipt was saved. Inspect AnkiDroid before retrying."
                        }
                        SubmissionPhase.MEDIA_IMPORTED -> "Media receipts were recorded, but note insertion did not start. Review before resuming."
                        SubmissionPhase.NOTE_INSERTING -> "A previous Send was interrupted. Verify its frozen outcome before retrying."
                        SubmissionPhase.NOTE_INSERTED -> "The note ID was saved before deck assignment. Verifying its cards before retrying anything."
                        SubmissionPhase.DESTINATION_VERIFIED -> "The inserted note's destination was verified. Finalizing recovery."
                        null -> null
                    },
                )
            }
            var recoveryDuplicate by remember { mutableStateOf(false) }
            var knownNoteId by remember { mutableStateOf<Long?>(null) }
            var publishedFrozenSnapshot by remember { mutableStateOf<FrozenSubmission?>(null) }
            var completedSubmission by remember { mutableStateOf<FrozenSubmission?>(null) }
            var pitchInfoExpanded by remember { mutableStateOf(false) }
            var frozenSnapshotAppliedRevision by remember { mutableStateOf(-1L) }
            var draftGeneration by remember { mutableStateOf(0L) }
            var editorInputRevision by remember { mutableStateOf(0L) }
            var setupGeneration by remember { mutableStateOf(0L) }
            val selectedModel = noteTypes.firstOrNull { it.id == selectedModelId }
            val selectedDeck = decks.firstOrNull { it.id == selectedDeckId }
            val incoming = sharedText.value
            val submissionLocked = isSubmitting || recoveryRequired || frozenRetryReady || duplicateBlocked
            val navigationLocked = submissionLocked || captureInProgress
            val currentSubmissionLocked = rememberUpdatedState(submissionLocked)
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
                        "Could not list voices. Check the saved key and network, then reload."
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

            fun closeSettings() {
                if (!showSettings || settingsSaving) return
                val coordinator = settingsCoordinator
                if (coordinator == null || settingsLoading) {
                    settingsError = "Settings are still loading. Back out again when loading has finished."
                    return
                }
                settingsSaving = true
                coroutineScope.launch {
                    val result = withContext(Dispatchers.IO) { coordinator.flush() }
                    settingsSaving = false
                    if (result.isSuccess) {
                        syncSettingsScreen(coordinator)
                        if (!onboardingComplete && !coordinator.canFinishOnboarding()) {
                            settingsError = "To finish setup, save a Google Cloud key and choose a voice verified by Google's current list."
                            syncSettingsScreen(coordinator)
                            return@launch
                        }
                        if (!onboardingComplete && !settings.edit().putBoolean("onboarding_complete", true).commit()) {
                            settingsError = "Setup could not be marked complete. Back remains here so you can retry."
                            syncSettingsScreen(coordinator)
                            return@launch
                        }
                        onboardingComplete = true
                        googleKey = coordinator.currentGoogleKey()
                        settingsCoordinator = null
                        showSettings = false
                    } else {
                        settingsError = result.exceptionOrNull()?.message ?: "Settings could not be saved."
                        syncSettingsScreen(coordinator)
                    }
                }
            }

            fun continueToReview() {
                if (!onboardingComplete) {
                    preparationAuthorizationError = "Finish setup in Settings before reviewing audio."
                    showSettings = true
                    return
                }
                if (navigationLocked) return
                val nextRevision = runCatching { Math.addExact(reviewAuthorizationSequence, 1L) }.getOrNull()
                if (nextRevision == null || !preferences.edit()
                        .putLong("review_authorization_sequence", nextRevision).commit()
                ) {
                    preparationAuthorizationError = "Could not record this Review authorization. Try Continue again."
                    return
                }
                reviewAuthorizationSequence = nextRevision
                preparationAuthorizationError = null
                generationStarted = true
                translationAutoAllowed = true
                pendingReviewAuthorization = nextRevision
                route = AppRoute.Review
            }

            val hasManualDefinition = definitionManuallyEdited && definition.isNotBlank()
            val persistLastSentTags: (FrozenSubmission) -> Unit = { snapshot ->
                check(settings.edit().putString("last_note_tags", snapshot.tags.joinToString(" ")).commit()) {
                    "Could not save the tags from the confirmed note."
                }
            }
            val clearDraft = {
                listOfNotNull(photoSourcePath, noteImagePath, processedImagePath).distinct().forEach { File(it).delete() }
                editorInputRevision++
                editor = TextFieldValue("")
                route = AppRoute.Capture
                photoSourcePath = null
                noteImagePath = null
                processedImagePath = null
                clearPersistedDraft(preferences, File(filesDir, "draft"))
                publishedFrozenSnapshot = null
                completedSubmission = null
                duplicateBlocked = false
                submitMessage = null
                frozenSnapshotAppliedRevision = -1L
                draftGeneration++
                frozenRetryReady = false
                recoveryRequired = false
                recoveryPhase = null
                recoveryDuplicate = false
                knownNoteId = null
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
                target = null
                vocabulary = ""
                vocabularyReading = ""
                pitchAccentText = ""
                definition = ""
                sentenceReading = ""
                definitionManuallyEdited = false
                definitionProvenanceKnown = true
                dependentFieldsStale = false
                translationDraft = TranslationDraft("", TranslationResult.Pending)
                translationAttemptedSentence = null
                translationAutoAllowed = false
                translationRetry = 0
                lastHandledTranslationRetry = 0
                translationRequestRevision++
                notes = ""
                tags = normalizeTagInput(settings.getString(SettingsPreferenceKeys.DEFAULT_TAGS, "").orEmpty())
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
                preferences.edit()
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
                    .apply()
            }
            val replaceEditorText: (String, Boolean) -> Unit = { text, invalidateDraftGeneration ->
                editorInputRevision++
                if (invalidateDraftGeneration) draftGeneration++
                if (text != editor.text) {
                    if (submissionLocked) translationRequestRevision++ else updateTranslationSentence(text)
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
                if (completedSubmission != null) {
                    kotlinx.coroutines.delay(900)
                    if (completedSubmission != null) clearDraft()
                }
            }

            val selectTarget: (TargetSpan?) -> Unit = selectTarget@{ selected ->
                if (currentSubmissionLocked.value) return@selectTarget
                val previousTarget = target
                if (selected != previousTarget) {
                    if (hasManualDefinition) dependentFieldsStale = true
                    selectedDictionaryEntry = null
                    selectedDictionaryEntryId = null
                    dictionaryEntries = emptyList()
                    pitchAccentText = ""
                }
                if (selected != previousTarget) {
                    // The reading belongs to the selected occurrence; a new target must never
                    // inherit the previous target's reading.
                    sentenceReading = selected?.let { span ->
                        resolveSurfaceReading(editor.text, japaneseTokens, span)
                    }.orEmpty()
                }
                target = selected
                if (selected != null && vocabulary.isBlank()) {
                    val surface = selected.textIn(editor.text).orEmpty()
                    val lemma = resolveAnalyzedLemma(japaneseTokens, selected)
                    vocabulary = lemma?.expression ?: surface
                    if (lemma != null && vocabularyReading.isBlank() && containsKanji(lemma.expression)) {
                        vocabularyReading = lemma.reading
                    }
                }
            }
            val applyOcrText: (String, Float) -> Unit = applyOcrText@{ text, focusY ->
                if (currentSubmissionLocked.value) return@applyOcrText
                selectTarget(null)
                replaceEditorText(text.trim(), false)
                photoFocusY = focusY.coerceIn(0f, 1f)
                japaneseTokens = emptyList()
                tokenizationError = null
                tokenizationLoading = analyzerReady
                ocrError = null
                route = AppRoute.Target
            }
            val setDraftOrigin: (String) -> Unit = { origin ->
                if (draftOrigin != origin) {
                    draftOrigin = origin
                    preferences.edit().putString("draft_origin", origin).apply()
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
                    preferences.edit()
                        .remove(IMAGE_RECEIPT_PATH)
                        .remove(IMAGE_RECEIPT_FILENAME)
                        .remove(IMAGE_RECEIPT_SIZE)
                        .remove(IMAGE_RECEIPT_MODIFIED)
                        .putString("photo_source_path", photoSourcePath)
                        .putString("note_image_path", noteImagePath)
                        .putString("draft_origin", draftOrigin)
                        .remove("processed_image_path")
                        .commit(),
                ) { "Could not save the draft image source." }
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
                            imageError = "This photo could not be prepared for Anki. Retry or remove it."
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
                                    ocrError = "No text was found. Type or edit the sentence to continue."
                                    route = AppRoute.Target
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
                                ocrError = "Could not read this photo. Type or edit the sentence to continue."
                                route = AppRoute.Target
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
                    imageError = "This draft came from a photo, so it needs its image. Replace the image instead."
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
                preferences.edit()
                    .remove("photo_source_path")
                    .remove("note_image_path")
                    .remove("processed_image_path")
                    .remove(IMAGE_RECEIPT_PATH)
                    .remove(IMAGE_RECEIPT_FILENAME)
                    .remove(IMAGE_RECEIPT_SIZE)
                    .remove(IMAGE_RECEIPT_MODIFIED)
                    .commit()
                }
                Unit
            }
            val stagePhotoReplacement: (File) -> Unit = stagePhotoReplacement@{ file ->
                if (submissionLocked) return@stagePhotoReplacement
                check(
                    preferences.edit().putString("pending_replacement_image_path", file.absolutePath).commit(),
                ) { "Could not save the pending photo replacement." }
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
            ) { granted -> cameraPermissionGranted = granted }
            androidx.compose.runtime.LaunchedEffect(route, captureMode) {
                if (route == AppRoute.Capture && captureMode == "Photo" && !cameraPermissionGranted) {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
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
                            ocrError = "Could not open that photo. Choose another image or type the sentence."
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
                            imageError = "Could not open that photo. Choose another image."
                        }
                    }
                }
            }

            androidx.compose.runtime.LaunchedEffect(Unit, draftGeneration, submissionLocked) {
                if (submissionLocked) return@LaunchedEffect
                val source = noteImagePath?.let(::File)?.takeIf(File::isFile)
                if (source != null && processedImagePath?.let(::File)?.isFile != true) {
                    processImage(source, photoSourcePath == source.absolutePath && editor.text.isBlank())
                } else if (photoSourcePath?.let(::File)?.isFile == true && editor.text.isBlank()) {
                    processImage(File(photoSourcePath!!), true)
                }
            }

            androidx.compose.runtime.LaunchedEffect(dictionaryRetry, setupGeneration, submissionLocked) {
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
                        ?.let { pitchUnavailableMessage(it.failure) }
                    dictionaryReady = true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.e("OfflineDictionary", "Could not prepare the bundled dictionary", error)
                    if (asyncSetupIsCurrent(callbackGeneration)) {
                        dictionaryError = "Offline dictionary could not be prepared. A dictionary entry is required before Send."
                    }
                } finally {
                    dictionaryLoading = false
                }
            }

            androidx.compose.runtime.LaunchedEffect(dictionaryRetry, analyzerRetry, setupGeneration, submissionLocked) {
                if (currentSubmissionLocked.value) return@LaunchedEffect
                val callbackGeneration = setupGeneration
                analyzerLoading = true
                analyzerError = null
                analyzerStatus = "Checking UniDic runtime archive"
                try {
                    withContext(Dispatchers.IO) {
                        offlineAnalyzer.prepare(
                            onStatus = { status ->
                                if (asyncSetupIsCurrent(callbackGeneration)) analyzerStatus = status
                            },
                        )
                    }
                    if (!asyncSetupIsCurrent(callbackGeneration)) return@LaunchedEffect
                    analyzerReady = true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: LinkageError) {
                    Log.e("OfflineAnalyzer", "Could not load MeCab native analysis", error)
                    if (asyncSetupIsCurrent(callbackGeneration)) analyzerError = "MeCab native analysis could not load. You can still select a sentence span and search the dictionary."
                } catch (error: Exception) {
                    Log.e("OfflineAnalyzer", "Could not prepare UniDic analysis", error)
                    if (asyncSetupIsCurrent(callbackGeneration)) {
                        analyzerError = "UniDic could not be downloaded, verified or installed. Check your connection and free storage, then retry."
                    }
                } finally {
                    analyzerLoading = false
                }
            }

            // Nothing from a previous word may survive an unresolved dictionary choice.
            fun clearUnresolvedVocabulary() {
                val unresolved = VocabularyResolution.unresolved()
                vocabulary = unresolved.vocabulary
                vocabularyReading = unresolved.reading
                vocabularyReadingError = null
                definition = unresolved.definition
                definitionManuallyEdited = false
                definitionProvenanceKnown = true
                dependentFieldsStale = false
                selectedDictionaryEntry = null
                selectedDictionaryEntryId = null
                selectedSenseIndices = unresolved.senseIndices
                pitchAccentText = unresolved.pitchAccent
                pitchHtmlText = null
                pitchCandidates = emptyList()
                pitchIndexError = "Choose a dictionary entry to resolve this word."
                audioController.clear(AudioItem.VOCABULARY)
                audioSnapshot = audioController.snapshot()
            }

            fun dismissDictionarySheet() {
                showDictionarySheet = false
                if (VocabularyResolution.isUnresolved(dictionaryEntries.size, selectedDictionaryEntry?.id)) {
                    clearUnresolvedVocabulary()
                }
            }

            /** A corrected vocabulary reading re-resolves entry, pitch and recording identity. */
            fun applyVocabularyReading(raw: String): Boolean {
                if (submissionLocked) return false
                val corrected = VocabularyReadings.normalize(raw)
                val match = VocabularyReadings.selectEntry(
                    dictionaryEntries.map { ReadingCandidate(it.id, it.expression, it.reading) },
                    vocabulary,
                    corrected,
                )
                if (match == null) {
                    audioController.clear(AudioItem.VOCABULARY)
                    audioSnapshot = audioController.snapshot()
                    pitchAccentText = ""
                    pitchHtmlText = null
                    pitchCandidates = emptyList()
                    // Never display a pronunciation that no dictionary entry supports.
                    vocabularyReading = ""
                    vocabularyReadingError = if (corrected.isEmpty()) {
                        "Enter the reading in kana."
                    } else if (dictionaryEntries.isEmpty()) {
                        "No dictionary entry is loaded for this word yet."
                    } else {
                        "No dictionary entry for ${vocabulary.ifBlank { "this word" }} is read ${corrected}. Pick a listed reading."
                    }
                    selectedDictionaryEntry = null
                    selectedDictionaryEntryId = null
                    selectedSenseIndices = emptySet()
                    pitchIndexError = "No pitch pattern can be resolved for this reading."
                    return false
                }
                val entry = dictionaryEntries.first { it.id == match.id }
                vocabularyReadingError = null
                if (vocabularyReading == entry.reading && selectedDictionaryEntry?.id == entry.id) {
                    // Already resolved to this entry and reading: nothing to invalidate.
                    return true
                }
                // The recording belongs to the previous reading/entry; re-resolve it.
                audioController.clear(AudioItem.VOCABULARY)
                audioSnapshot = audioController.snapshot()
                pitchAccentText = ""
                pitchHtmlText = null
                pitchCandidates = emptyList()
                pitchIndexError = null
                vocabularyReading = entry.reading
                if (selectedDictionaryEntry?.id != entry.id) {
                    selectedDictionaryEntry = entry
                    selectedDictionaryEntryId = entry.id
                    val senses = if (entry.senses.isEmpty()) emptySet() else setOf(0)
                    selectedSenseIndices = senses
                    definition = senses.sorted().mapNotNull { index ->
                        entry.senses.getOrNull(index)?.glosses?.joinToString("; ")
                    }.joinToString("\n")
                    definitionManuallyEdited = false
                    definitionProvenanceKnown = true
                }
                return true
            }

            /** Correct one kanji span's reading without editing the sentence (SPEC §4.5). */
            fun applyReadingCorrection(span: TargetSpan, reading: String): Boolean {
                if (submissionLocked) return false
                val rejection = SentenceReadings.rejection(editor.text, span, reading)
                if (rejection != null) {
                    readingCorrectionError = rejection
                    return false
                }
                readingCorrectionError = null
                val corrected = SentenceReadings.normalizeReading(reading)
                if (span == target) {
                    if (sentenceReading == corrected) return true
                    sentenceReading = corrected
                } else {
                    readingCorrections = SentenceReadings.correct(readingCorrections, editor.text, span, corrected)
                }
                // Any sentence-reading change invalidates the sentence recording and its hints.
                audioController.clear(AudioItem.SENTENCE)
                audioSnapshot = audioController.snapshot()
                return true
            }

            // Typing in the sentence can shift the spans a correction was stored against.
            androidx.compose.runtime.LaunchedEffect(editor.text) {
                val stale = readingCorrections.filter { it.span.textIn(editor.text) != it.surface }
                if (stale.isNotEmpty()) {
                    readingCorrections = readingCorrections.filter { it.span.textIn(editor.text) == it.surface }
                }
            }

            androidx.compose.runtime.LaunchedEffect(
                editor.text, translationRequestRevision, translationRetry, translationAutoAllowed, submissionLocked,
                generationStarted,
            ) {
                val sentence = editor.text
                // Network work starts only after the deliberate Continue, never on typing or token taps.
                if (sentence.isBlank() || submissionLocked || !generationStarted) return@LaunchedEffect

                var requestDraft = translationDraft
                if (requestDraft.sentence != sentence) {
                    requestDraft = requestDraft.sentenceChanged(sentence)
                    translationDraft = requestDraft
                }
                if (requestDraft.manuallyEdited && !requestDraft.stale) return@LaunchedEffect
                if (!requestDraft.manuallyEdited && !requestDraft.stale && requestDraft.result is TranslationResult.Ready) {
                    return@LaunchedEffect
                }

                val explicitRetry = translationRetry != lastHandledTranslationRetry
                if (translationAttemptedSentence == sentence && !explicitRetry) {
                    val active = if (requestDraft.manuallyEdited && requestDraft.stale) {
                        requestDraft.suggestion ?: TranslationResult.Pending
                    } else requestDraft.result
                    if (active is TranslationResult.Pending) {
                        translationDraft = requestDraft.requestFinished(
                            TranslationResult.Failed("Translation needs retry. Tap Retry to continue."),
                        )
                    }
                    return@LaunchedEffect
                }
                if (!translationAutoAllowed && !explicitRetry) {
                    translationDraft = requestDraft.requestFinished(
                        TranslationResult.Failed("Translation was not started. Tap Retry to continue."),
                    )
                    return@LaunchedEffect
                }

                lastHandledTranslationRetry = translationRetry
                val requestRevision = translationRequestRevision
                kotlinx.coroutines.delay(350)
                if (!translationResponseIsCurrent(
                        sentence, requestRevision, editor.text,
                        currentTranslationRequestRevision.value, currentSubmissionLocked.value,
                    )
                ) return@LaunchedEffect

                translationDraft = translationDraft.requestStarted()
                translationAttemptedSentence = sentence
                val requestRecorded = preferences.edit()
                    .putString("translation_attempted_sentence", sentence)
                    .putBoolean("translation_pending", true)
                    .commit()
                if (!requestRecorded) {
                    translationDraft = translationDraft.requestFinished(
                        TranslationResult.Failed("Translation request could not be recorded. Tap Retry to continue."),
                    )
                    return@LaunchedEffect
                }

                val result = try {
                    withContext(Dispatchers.IO) {
                        translationService.translate(sentence)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    TranslationResult.Failed("Translation is unavailable. Tap Retry to continue.")
                }
                if (translationResponseIsCurrent(
                        sentence, requestRevision, editor.text,
                        currentTranslationRequestRevision.value, currentSubmissionLocked.value,
                    )
                ) {
                    translationDraft = translationDraft.requestFinished(result)
                }
            }

            androidx.compose.runtime.LaunchedEffect(
                analyzerReady, editor.text, analysisRetry, draftGeneration, submissionLocked, dictionaryReady,
            ) {
                if (currentSubmissionLocked.value) return@LaunchedEffect
                val callbackGeneration = draftGeneration
                if (!analyzerReady || editor.text.isBlank()) {
                    japaneseTokens = emptyList()
                    tokenizationLoading = false
                    return@LaunchedEffect
                }
                tokenizationLoading = true
                tokenizationError = null
                try {
                    kotlinx.coroutines.delay(150)
                    val analyzed = withContext(Dispatchers.IO) {
                        val raw = offlineAnalyzer.analyze(editor.text)
                        groupTokens(raw) { candidate ->
                            dictionaryReady && runCatching {
                                // One bound existence probe; the readiness gate and the
                                // exception-to-false policy are unchanged.
                                offlineDictionary.hasEntry(candidate)
                            }.getOrDefault(false)
                        }
                    }
                    if (!asyncDraftIsCurrent(callbackGeneration)) return@LaunchedEffect
                    japaneseTokens = analyzed
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (asyncDraftIsCurrent(callbackGeneration)) {
                        japaneseTokens = emptyList()
                        tokenizationError = "Could not analyze this sentence. Select a target span to search the dictionary."
                    }
                } finally {
                    tokenizationLoading = false
                }
            }

            androidx.compose.runtime.LaunchedEffect(
                target, editor.text, japaneseTokens, vocabulary, vocabularyReading,
                vocabularyReadingError, dependentFieldsStale,
            ) {
                if (submissionLocked) return@LaunchedEffect
                val targetSpan = target ?: return@LaunchedEffect
                val targetText = targetSpan.textIn(editor.text) ?: return@LaunchedEffect
                val resolved = resolveAnalyzedLemma(japaneseTokens, targetSpan) ?: return@LaunchedEffect
                val shouldResolveSurface = vocabulary.isBlank() ||
                    (!dependentFieldsStale && vocabulary == targetText)
                if (shouldResolveSurface) vocabulary = resolved.expression
                // A rejected reading correction stays unresolved; do not silently restore a
                // different pronunciation over the user's attempt.
                if (vocabularyReadingError == null &&
                    (shouldResolveSurface || vocabulary == resolved.expression) && vocabularyReading.isBlank() &&
                    containsKanji(resolved.expression)
                ) vocabularyReading = resolved.reading
            }

            androidx.compose.runtime.LaunchedEffect(
                target, editor.text, japaneseTokens, sentenceReading,
            ) {
                if (submissionLocked) return@LaunchedEffect
                val targetSpan = target ?: return@LaunchedEffect
                val targetText = targetSpan.textIn(editor.text) ?: return@LaunchedEffect
                if (sentenceReading.isNotBlank() || !containsKanji(targetText)) {
                    return@LaunchedEffect
                }
                sentenceReading = resolveSurfaceReading(
                    editor.text, japaneseTokens, targetSpan,
                )?.takeIf(String::isNotBlank) ?: return@LaunchedEffect
            }

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
                            if (item == AudioItem.SENTENCE) "Sentence" else "Word",
                        )
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

            fun audioActionLabel(item: AudioItem): String = when {
                audioConfigErrors.containsKey(item) && audioSettingsRequired(item) -> "Open Settings"
                audioConfigErrors.containsKey(item) -> "Edit preparation"
                (audioSnapshot.items[item] as? DraftAudioState.Failed)?.retryable == false -> "Edit preparation"
                audioSnapshot.items[item] is DraftAudioState.NeedsContinue -> "Generate"
                else -> "Retry"
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
                        generationStarted = false
                        route = AppRoute.Target
                    }
                    (audioSnapshot.items[item] as? DraftAudioState.Failed)?.retryable == false -> {
                        generationStarted = false
                        route = AppRoute.Target
                    }
                    else -> {
                        val attempt = audioController.confirm(item) ?: audioController.retry(item)
                        if (attempt != null) {
                            runAudioAttempt(attempt)
                        } else {
                            val message = if (item == AudioItem.SENTENCE) {
                                "Sentence audio is not configured. Add a Google Cloud key and verified voice in Settings."
                            } else {
                                "Word audio is unavailable. Select a dictionary entry in preparation."
                            }
                            audioConfigErrors = audioConfigErrors + (item to message)
                            Log.w("DraftAudio", "${item.name} audio unavailable: no current input")
                        }
                    }
                }
            }

            // Inputs are declared when preparation or Review is visible. Automatic work is
            // separately authorized by the explicit Continue transition below.
            androidx.compose.runtime.LaunchedEffect(
                route, submissionLocked, audioInputRevision,
                currentSentenceIdentity.value, currentWordIdentity.value,
            ) {
                // Declare both frozen-revision inputs on preparation and Review. This does no provider
                // work; restore/revisit cannot authorize generation by itself.
                if (submissionLocked) return@LaunchedEffect

                val declaredAudioRevision = declaredReviewAudioRevision(
                    pendingReviewAuthorization,
                    reviewAuthorizationSequence,
                )
                val sentenceText = editor.text.trim()
                val selectedTarget = target
                val authoritativeCatalog = settingsScreenState.voiceCatalog.takeIf {
                    settingsScreenState.credentialStored && it.provenance == VoiceCatalogProvenance.AUTHORITATIVE
                }
                val voice = authoritativeCatalog?.voices?.firstOrNull { it.name == selectedVoiceName }
                val hintResult = if (selectedTarget != null && selectedTarget.textIn(editor.text) != null) {
                    ReadingHintBuilder.build(
                        sentenceText,
                        sentenceHintSpans(sentenceText, selectedTarget, sentenceReading, sentenceFurigana),
                    )
                } else null

                if (selectedTarget != null && sentenceText.isNotEmpty() &&
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
                        sentenceText.isEmpty() || selectedTarget == null -> null
                        voice == null -> when {
                            settingsScreenState.credentialUnreadable ->
                                "The saved Google Cloud API key could not be opened. Re-enter it and choose a verified sentence voice in Settings."
                            !settingsScreenState.credentialStored ->
                                "No Google Cloud API key is saved. Add one and choose a verified sentence voice in Settings."
                            selectedVoiceName.isBlank() ->
                                "No verified sentence voice is selected. Choose one in Settings before generating sentence audio."
                            else ->
                                "The selected sentence voice is not verified. Reload voices and choose a verified voice in Settings."
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

                val entryId = selectedDictionaryEntryId
                val entry = selectedDictionaryEntry?.takeIf {
                    it.id == entryId && it.expression == vocabulary && it.reading == vocabularyReading
                }
                if (entry == null || vocabulary.isBlank()) {
                    // An unresolved word can never be sent, and its old recording must not survive
                    // the change that invalidated it.
                    audioConfigErrors = if (entry == null && vocabulary.isNotBlank()) {
                        audioConfigErrors + (
                            AudioItem.VOCABULARY to "Select a dictionary entry before generating word audio."
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
                audioSnapshot = audioController.snapshot()
            }

            androidx.compose.runtime.LaunchedEffect(route, pendingReviewAuthorization, submissionLocked) {
                val revision = pendingReviewAuthorization ?: return@LaunchedEffect
                if (route != AppRoute.Review || submissionLocked) return@LaunchedEffect
                androidx.compose.runtime.withFrameNanos { }
                if (route != AppRoute.Review || submissionLocked || pendingReviewAuthorization != revision) {
                    return@LaunchedEffect
                }
                pendingReviewAuthorization = null
                val attempts = reviewAudioCoordinator.onReviewEntry(
                    revision,
                    ReviewAudioEntryCause.EXPLICIT_CONTINUE,
                )
                Log.i(
                    "DraftAudio",
                    "Review entry revision=$revision audioAttempts=${attempts.joinToString(",") { it.input.item.name }}",
                )
                attempts.forEach(::runAudioAttempt)
                audioSnapshot = audioController.snapshot()
            }


            androidx.compose.runtime.LaunchedEffect(dictionaryReady, target, editor.text, dictionaryRetry, japaneseTokens, draftGeneration, submissionLocked) {
                if (currentSubmissionLocked.value) return@LaunchedEffect
                val callbackGeneration = draftGeneration
                val targetText = target?.textIn(editor.text)
                if (!dictionaryReady || targetText == null) {
                    dictionaryEntries = emptyList()
                    dictionaryHasMore = false
                    selectedDictionaryEntry = null
                } else {
                    dictionarySearchLoading = true
                    try {
                        val entries = withContext(Dispatchers.IO) {
                            offlineDictionary.search(targetText, analyzedTarget?.expression)
                        }
                        if (!asyncDraftIsCurrent(callbackGeneration)) return@LaunchedEffect
                        dictionaryEntries = entries
                        dictionaryError = null
                        dictionaryHasMore = entries.size == 50
                        val restoredEntry = entries.firstOrNull {
                            it.id == selectedDictionaryEntryId &&
                                it.expression == vocabulary && it.reading == vocabularyReading
                        }
                        val entry = restoredEntry ?: entries.firstOrNull()
                        selectedDictionaryEntry = entry
                        selectedSenseIndices = if (entry != null) {
                            if (restoredEntry != null) {
                                preferences.getStringSet("dictionary_sense_indices", null)
                                    ?.mapNotNull(String::toIntOrNull)?.filter { it in entry.senses.indices }
                                    ?.toSet()?.takeIf { it.isNotEmpty() } ?: setOf(0)
                            } else if (entry.senses.isEmpty()) emptySet() else setOf(0)
                        } else emptySet()
                        if (entry != null) {
                            val entryChanged = selectedDictionaryEntryId != entry.id || vocabulary != entry.expression ||
                                vocabularyReading != entry.reading
                            val savedSenses = selectedSenseIndices.sorted().mapNotNull { entry.senses.getOrNull(it) }
                                .joinToString("\n") { it.glosses.joinToString("; ") }
                            if (!definitionProvenanceKnown) {
                                definitionManuallyEdited = definition.isNotBlank() && definition != savedSenses
                                definitionProvenanceKnown = true
                            }
                            if (entryChanged) {
                                if (definitionManuallyEdited && definition.isNotBlank()) dependentFieldsStale = true
                                if (selectedDictionaryEntryId != entry.id) pitchAccentText = ""
                                vocabulary = entry.expression
                                vocabularyReading = entry.reading
                                selectedDictionaryEntryId = entry.id
                            }
                            if (!definitionManuallyEdited) {
                                val resolvedSenses = if (entryChanged || selectedSenseIndices.isEmpty()) setOf(0)
                                    else selectedSenseIndices
                                selectedSenseIndices = resolvedSenses
                                definition = resolvedSenses.sorted().mapNotNull { index ->
                                    entry.senses.getOrNull(index)?.glosses?.joinToString("; ")
                                }.joinToString("\n")
                            }
                        }
                        if (VocabularyResolution.isUnresolved(entries.size, entry?.id)) {
                            // Several entries compete and none is chosen: clear the previous word
                            // before the chooser is shown, so dismissing it cannot show stale data.
                            clearUnresolvedVocabulary()
                            showDictionarySheet = true
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        if (asyncDraftIsCurrent(callbackGeneration)) {
                            dictionaryError = "Dictionary lookup failed. Retry before selecting a dictionary entry."
                        }
                    } finally {
                        dictionarySearchLoading = false
                    }
                }
            }

            androidx.compose.runtime.LaunchedEffect(
                dictionaryReady, selectedDictionaryEntryId, selectedDictionaryEntry, vocabulary, vocabularyReading,
                target, editor.text, dictionaryRetry, draftGeneration, submissionLocked, ajtAvailability,
            ) {
                if (currentSubmissionLocked.value) return@LaunchedEffect
                val callbackGeneration = draftGeneration
                val entry = selectedDictionaryEntry?.takeIf {
                    it.id == selectedDictionaryEntryId && it.expression == vocabulary && it.reading == vocabularyReading
                }
                if (!dictionaryReady || entry == null || vocabulary.isBlank() || vocabularyReading.isBlank() ||
                    ajtAvailability !is AjtPitchAvailability.Ready
                ) {
                    pitchCandidates = emptyList()
                    pitchAccentText = ""
                    pitchHtmlText = null
                    pitchLookupLoading = false
                    pitchIndexError = (ajtAvailability as? AjtPitchAvailability.Unavailable)
                        ?.let { pitchUnavailableMessage(it.failure) }
                    return@LaunchedEffect
                }
                pitchAccentText = ""
                pitchHtmlText = null
                pitchCandidates = emptyList()
                pitchLookupLoading = true
                try {
                    val candidates = withContext(Dispatchers.IO) {
                        ajtPitchRepository.pitch(entry.expression, entry.reading)
                    }.filter { PitchAccent.create(entry.reading, it.accent, it.html) != null }
                    if (!asyncDraftIsCurrent(callbackGeneration)) return@LaunchedEffect
                    pitchCandidates = candidates
                    pitchIndexError = if (candidates.isEmpty()) {
                        "No AJT pitch record matches this entry/reading, so the note cannot be mined."
                    } else null
                    if (candidates.isNotEmpty()) {
                        pitchAccentText = candidates.first().accent.toString()
                        pitchHtmlText = candidates.first().html
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (asyncDraftIsCurrent(callbackGeneration)) pitchCandidates = emptyList()
                } finally {
                    pitchLookupLoading = false
                }
            }

            androidx.compose.runtime.LaunchedEffect(incoming, submissionLocked) {
                if (submissionLocked) {
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
                        route = AppRoute.Target
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
            androidx.compose.runtime.LaunchedEffect(sharedImage.value, submissionLocked) {
                if (submissionLocked) {
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
                            ocrError = "Could not open that photo. Choose another image or type the sentence."
                        }
                    }
                }
            }
            val saveSettings: () -> Unit = {
                val google = googleKey.trim()
                if (google.isNotEmpty() && google.any { it.isWhitespace() }) {
                    settingsError = "The Google Cloud key contains whitespace."
                } else {
                    settingsSaving = true
                    settingsError = null
                    coroutineScope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                settings.edit()
                                    .remove("default_tags")
                                    .putString("google_tts_voice", selectedVoiceName)
                                    .commit()
                                check(GoogleCloudCredential.write(settings, google)) {
                                    "Could not persist settings."
                                }
                                // The obsolete OpenRouter ciphertext is deleted, never read back as
                                // a Google key. Drafts and media are untouched.
                                GoogleCloudCredential.removeObsoleteOpenRouterStorage(settings)
                            }
                            // Saving writes only local state; it sends nothing. The previous listing
                            // belongs to the old key, so speech status returns to "not checked"
                            // until Refresh voices is tapped.
                            googleKey = google
                            translationTestResult = null
                            cloudVoicesError = null
                            cloudVoices = emptyList()
                            voiceListAttempted = false
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            settingsError = "Settings could not be saved. The key was not stored in plain text."
                        } finally {
                            settingsSaving = false
                        }
                    }
                }
            }
            if (showSettings) {
                Dialog(
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
                            ),
                            noteTypes = noteTypes,
                            decks = decks,
                            onBack = { closeSettings() },
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
                                    coordinator.updateGoogleKey(value)
                                    syncSettingsScreen(coordinator)
                                }
                            },
                            onVoiceChange = { voice ->
                                settingsCoordinator?.let { coordinator ->
                                    coordinator.updateValues(coordinator.currentValues().copy(voiceName = voice))
                                    audioInputRevision++
                                    syncSettingsScreen(coordinator)
                                }
                            },
                            onReloadVoices = {
                                settingsCoordinator?.let { coordinator ->
                                    val request = coordinator.reloadVoices()
                                    if (request == null) {
                                        settingsError = "Save the current key successfully before reloading voices."
                                        syncSettingsScreen(coordinator)
                                    } else {
                                        settingsError = null
                                        startVoiceList(coordinator, request)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                            additionalSections = {
                                androidx.compose.material3.HorizontalDivider()
                                Text("AnkiDroid setup", style = MaterialTheme.typography.titleMedium)
                                when {
                                    !ankiDroidInstalled -> Text("Install AnkiDroid to set up export.")
                                    !apiPermissionGranted -> {
                                        Text("Grant the AnkiDroid API permission before choosing the note type or deck.")
                                        Button(
                                            enabled = !navigationLocked,
                                            onClick = { apiPermissionLauncher.launch(ANKIDROID_READ_WRITE_PERMISSION) },
                                        ) { Text("Grant AnkiDroid API permission") }
                                    }
                                    setupLoading -> Text("Loading note types and decks…")
                                    setupError != null -> {
                                        Text(setupError!!, color = MaterialTheme.colorScheme.error)
                                        TextButton(onClick = { setupReload++ }) { Text("Retry AnkiDroid setup") }
                                    }
                                    else -> Text("Note types and decks are loaded from AnkiDroid.")
                                }

                                androidx.compose.material3.HorizontalDivider()
                                Text("Audio cache", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Reusable sentence and word audio: ${formatCacheSize(audioCacheBytes)}. " +
                                        "Current draft audio is kept separately.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                TextButton(
                                    enabled = !audioCacheBusy && !settingsSaving,
                                    onClick = {
                                        audioCacheBusy = true
                                        audioCacheMessage = null
                                        coroutineScope.launch {
                                            try {
                                                val remaining = withContext(Dispatchers.IO) {
                                                    reusableAudioCache.clear()
                                                    reusableAudioCache.sizeBytes()
                                                }
                                                audioCacheBytes = remaining
                                                audioCacheMessage = "Audio cache cleared. Draft audio is unaffected."
                                            } catch (_: Exception) {
                                                audioCacheMessage = "The audio cache could not be cleared."
                                            } finally {
                                                audioCacheBusy = false
                                            }
                                        }
                                    },
                                ) { Text(if (audioCacheBusy) "Clearing…" else "Clear audio cache") }
                                audioCacheMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

                                androidx.compose.material3.HorizontalDivider()
                                Text("Translation test", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Google Cloud Translation Basic — default neural (NMT), Japanese to English. " +
                                        "The disclosed sample is sent only when you tap Test; that request may be billable.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                when {
                                    translationTestRunning -> Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        Text("  Testing translation…")
                                    }
                                    translationTestResult is TranslationResult.Ready -> Text(
                                        "Test passed. Sample 「$TRANSLATION_TEST_SAMPLE」 → " +
                                            (translationTestResult as TranslationResult.Ready).text,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    translationTestResult is TranslationResult.Failed -> Text(
                                        (translationTestResult as TranslationResult.Failed).message,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    else -> Text("Translation has not been tested in this session.", style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(
                                    enabled = settingsScreenState.credentialStored && !translationTestRunning && !settingsSaving,
                                    onClick = {
                                        translationTestRunning = true
                                        translationTestResult = null
                                        coroutineScope.launch {
                                            try {
                                                translationTestResult = withContext(Dispatchers.IO) {
                                                    translationService.translate(TRANSLATION_TEST_SAMPLE)
                                                }
                                            } catch (cancelled: CancellationException) {
                                                throw cancelled
                                            } catch (_: Exception) {
                                                translationTestResult = TranslationResult.Failed("Translation test failed.")
                                            } finally {
                                                translationTestRunning = false
                                            }
                                        }
                                    },
                                ) { Text(if (translationTestRunning) "Testing…" else "Test translation") }

                                androidx.compose.material3.HorizontalDivider()
                                Text("Sources and licenses", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "JMdict and Kanjium attribution is available in Sources & licenses. " +
                                        "The bundled mixed-source AJT pitch data still needs a separate distribution-rights review.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                TextButton(onClick = { showSources = true }) { Text("Sources & licenses") }
                            },
                        )
                    }
                }
            }
            if (false && showSettings) {
                Dialog(
                    onDismissRequest = { if (!settingsSaving) showSettings = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().statusBarsPadding()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(
                                    enabled = !settingsSaving,
                                    onClick = { showSettings = false },
                                ) { Text("Back") }
                                Text(
                                    "Settings",
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(
                                    enabled = !settingsLoading && !settingsSaving,
                                    onClick = saveSettings,
                                ) { Text(if (settingsSaving) "Saving…" else "Save") }
                            }
                            Column(
                                modifier = Modifier.fillMaxWidth().weight(1f)
                                    .navigationBarsPadding()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 20.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text("AnkiDroid", style = MaterialTheme.typography.titleMedium)
                                when {
                                    !ankiDroidInstalled -> Text(
                                        "AnkiDroid not detected. Install it before setting up export.",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    !apiPermissionGranted -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("AnkiDroid detected. Grant API permission to continue setup.")
                                        Button(enabled = !isSubmitting, onClick = {
                                            apiPermissionLauncher.launch(ANKIDROID_READ_WRITE_PERMISSION)
                                        }) { Text("Grant AnkiDroid API permission") }
                                    }
                                    else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (setupLoading) Text("Loading note types and decks…")
                                        setupError?.let {
                                            Text(it)
                                            TextButton(onClick = { setupReload++ }) { Text("Retry") }
                                        }
                                        Text("Note type", style = MaterialTheme.typography.titleSmall)
                                        Box {
                                            OutlinedButton(
                                                onClick = { showModelMenu = true },
                                                enabled = !settingsLoading && !settingsSaving && noteTypes.isNotEmpty(),
                                            ) {
                                                Text(selectedModel?.name ?: if (selectedModelId == null) "Choose note type" else "Previously selected note type unavailable")
                                            }
                                            DropdownMenu(
                                                expanded = showModelMenu,
                                                onDismissRequest = { showModelMenu = false },
                                            ) {
                                                noteTypes.forEach { model ->
                                                    DropdownMenuItem(
                                                        text = { Text(model.name) },
                                                        onClick = { selectedModelId = model.id; showModelMenu = false },
                                                    )
                                                }
                                            }
                                        }
                                        selectedModel?.let { model ->
                                            AnkiFieldContract.mapToModelOrder(model.fields, emptyMap()).errors
                                                .forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                                        }
                                        Text("Destination deck", style = MaterialTheme.typography.titleSmall)
                                        Box {
                                            OutlinedButton(
                                                onClick = { showDeckMenu = true },
                                                enabled = !settingsLoading && !settingsSaving && decks.isNotEmpty(),
                                            ) {
                                                Text(selectedDeck?.name ?: if (selectedDeckId == null) "Choose destination deck" else "Previously selected deck unavailable")
                                            }
                                            DropdownMenu(
                                                expanded = showDeckMenu,
                                                onDismissRequest = { showDeckMenu = false },
                                            ) {
                                                decks.forEach { deck ->
                                                    DropdownMenuItem(
                                                        text = { Text(deck.name) },
                                                        onClick = { selectedDeckId = deck.id; showDeckMenu = false },
                                                    )
                                                }
                                            }
                                        }
                                        if (!setupLoading && setupError == null && noteTypes.isEmpty()) {
                                            Text("No note types available in AnkiDroid.")
                                        }
                                        if (!setupLoading && setupError == null && decks.isEmpty()) {
                                            Text("No existing decks available in AnkiDroid.")
                                        }
                                    }
                                }
                                Text("Google Cloud", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "One API key for sentence speech and translation. It is encrypted with " +
                                        "Android Keystore, stays on this device, and is sent only to Google Cloud " +
                                        "in an authentication header, never in a URL.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    "Project setup: enable billing, enable the Cloud Text-to-Speech API and the " +
                                        "Cloud Translation API, and restrict this key to those two APIs. Entering or " +
                                        "saving the key sends no request.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                if (settingsLoading) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        Text("Loading saved settings…")
                                    }
                                }
                                OutlinedTextField(
                                    value = googleKey,
                                    onValueChange = { googleKey = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Google Cloud API key") },
                                    visualTransformation = if (googleKeyVisible) VisualTransformation.None
                                        else PasswordVisualTransformation(),
                                    singleLine = true,
                                    enabled = !settingsLoading && !settingsSaving,
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = googleKeyVisible,
                                        onCheckedChange = { googleKeyVisible = it },
                                        enabled = !settingsLoading && !settingsSaving,
                                    )
                                    Text("Show key")
                                }
                                Text(
                                    if (settings.contains(GoogleCloudCredential.PREF)) {
                                        "Key saved for speech and translation."
                                    } else {
                                        "No key saved. Local preparation works; speech and translation need a key."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text("Speech · Cloud Text-to-Speech", style = MaterialTheme.typography.titleSmall)
                                Text("Sentence voice", style = MaterialTheme.typography.bodyMedium)
                                when {
                                    cloudVoicesLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        Text("  Listing Japanese Chirp 3 HD voices…")
                                    }
                                    cloudVoicesError != null -> Text(
                                        buildString {
                                            append(cloudVoicesError)
                                            if (cloudVoicesCached && cloudVoices.isNotEmpty()) {
                                                append(" Showing the cached list, which is not fresh verification.")
                                            }
                                        },
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    cloudVoices.isEmpty() && settings.contains(GoogleCloudCredential.PREF) && voiceListAttempted ->
                                        Text("No Japanese Chirp 3 HD voices were returned.", style = MaterialTheme.typography.bodySmall)
                                    cloudVoices.isEmpty() && settings.contains(GoogleCloudCredential.PREF) ->
                                        Text(
                                            "Voice listing not checked yet. Refresh voices to validate speech access.",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                }
                                Box {
                                    OutlinedButton(
                                        onClick = { showVoiceMenu = true },
                                        enabled = !settingsLoading && !settingsSaving && cloudVoices.isNotEmpty(),
                                    ) {
                                        val voice = cloudVoices.firstOrNull { it.name == selectedVoiceName }
                                        Text(voice?.let { "${it.displayName} · ${it.name}" }
                                            ?: if (selectedVoiceName.isBlank()) "Choose sentence voice"
                                            else "Saved voice not in this list")
                                    }
                                    DropdownMenu(
                                        expanded = showVoiceMenu,
                                        onDismissRequest = { showVoiceMenu = false },
                                        modifier = Modifier.heightIn(max = 320.dp),
                                    ) {
                                        cloudVoices.forEach { voice ->
                                            DropdownMenuItem(
                                                text = { Text("${voice.displayName} · ${voice.name}") },
                                                onClick = {
                                                    selectedVoiceName = voice.name
                                                    showVoiceMenu = false
                                                },
                                            )
                                        }
                                    }
                                }
                                if (selectedVoiceName.isNotBlank() && cloudVoices.isNotEmpty() &&
                                    cloudVoices.none { it.name == selectedVoiceName }
                                ) {
                                    Text(
                                        "The saved voice is no longer listed. Choose another voice before generating sentence audio.",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                TextButton(
                                    enabled = !cloudVoicesLoading && !settingsSaving && settings.contains(GoogleCloudCredential.PREF),
                                    onClick = { voiceRefresh++ },
                                ) { Text("Refresh voices") }
                                if (!settings.contains(GoogleCloudCredential.PREF) && googleKey.isNotBlank()) {
                                    Text("Save the key, then refresh voices.", style = MaterialTheme.typography.bodySmall)
                                }
                                Text(
                                    "Changing the voice invalidates sentence audio only. Voice listing does not " +
                                        "synthesize or bill any speech.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text("Audio cache", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Reusable sentence and word audio: ${formatCacheSize(audioCacheBytes)}. " +
                                        "The current draft's audio is kept outside this cache.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                TextButton(
                                    enabled = !audioCacheBusy && !settingsSaving,
                                    onClick = {
                                        audioCacheBusy = true
                                        audioCacheMessage = null
                                        coroutineScope.launch {
                                            try {
                                                val remaining = withContext(Dispatchers.IO) {
                                                    reusableAudioCache.clear()
                                                    reusableAudioCache.sizeBytes()
                                                }
                                                audioCacheBytes = remaining
                                                audioCacheMessage = "Audio cache cleared. Draft audio is unaffected."
                                            } catch (_: Exception) {
                                                audioCacheMessage = "The audio cache could not be cleared."
                                            } finally {
                                                audioCacheBusy = false
                                            }
                                        }
                                    },
                                ) { Text(if (audioCacheBusy) "Clearing…" else "Clear audio cache") }
                                audioCacheMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                Text("Translation · Cloud Translation Basic", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Google Cloud Translation Basic — default neural (NMT), Japanese → English. " +
                                        "Fixed path: no model or provider choice.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                val testedTranslation = translationTestResult
                                when {
                                    translationTestRunning -> Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        Text("  Testing translation…")
                                    }
                                    testedTranslation is TranslationResult.Ready -> Text(
                                        "Test passed. Sample 「$TRANSLATION_TEST_SAMPLE」 → ${testedTranslation.text}",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    testedTranslation is TranslationResult.Failed -> Text(
                                        testedTranslation.message,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    else -> Text(
                                        "Translation not tested in this session.",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                TextButton(
                                    enabled = !translationTestRunning && !settingsSaving &&
                                        settings.contains(GoogleCloudCredential.PREF),
                                    onClick = {
                                        translationTestRunning = true
                                        translationTestResult = null
                                        coroutineScope.launch {
                                            try {
                                                translationTestResult = withContext(Dispatchers.IO) {
                                                    translationService.translate(TRANSLATION_TEST_SAMPLE)
                                                }
                                            } finally {
                                                translationTestRunning = false
                                            }
                                        }
                                    },
                                ) { Text(if (translationTestRunning) "Testing…" else "Test translation") }
                                Text(
                                    "The test sends one disclosed sample sentence and may be billable. A failed or " +
                                        "empty translation blocks Send; Retry keeps manual corrections. Voice listing " +
                                        "validates speech access only, not translation access or speech quality.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                settingsError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                                Text("Sources", style = MaterialTheme.typography.titleMedium)
                                TextButton(onClick = { showSettings = false; showSources = true }) {
                                    Text("Sources & licenses")
                                }
                                }
                            }
                        }
                    }
                }
            if (showSources) {
                AlertDialog(
                    onDismissRequest = { showSources = false },
                    title = { Text("Data sources and licenses") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("JMdict © James William Breen and EDRDG; CC BY-SA 4.0. ${dictionaryInfo["jmdict_source"].orEmpty()}")
                            Text("The pitch accent notation, verb particle data, phonetics, homonyms, and other additions or modifications to EDICT, KANJIDIC, or KRADFILE were provided by Uros O. through his free database. Kanjium CC BY-SA 4.0. ${dictionaryInfo["kanjium_commit"].orEmpty()}")
                            Text("MeCab 0.996 © Taku Kudo and Nippon Telegraph and Telephone Corporation; BSD license.")
                            Text("UniDic CWJ 3.1.1 © the UniDic Consortium and Teruaki Oka; distributed under the included BSD license.")
                            Text("Local license copies are bundled with the app. JMdict data is updated through app releases.")
                            TextButton(onClick = {
                                runCatching {
                                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.edrdg.org/edrdg/licence.html")))
                                }
                            }) { Text("Open EDRDG license") }
                            TextButton(onClick = {
                                runCatching {
                                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://creativecommons.org/licenses/by-sa/4.0/")))
                                }
                            }) { Text("Open CC BY-SA 4.0") }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showSources = false }) { Text("Done") }
                    },
                )
            }
            if (!submissionLocked && showReplace && (incoming != null || pendingPhotoFile != null || pendingSharedImageUri != null)) {
                val replacingSentence = incoming != null
                AlertDialog(
                    onDismissRequest = {
                        discardSharePayload()
                        showReplace = false
                        sharedText.value = null
                        pendingPhotoFile?.delete()
                        pendingPhotoFile = null
                        preferences.edit().remove("pending_replacement_image_path").commit()
                        pendingSharedImageUri = null
                    },
                    title = { Text("Replace current draft?") },
                    text = {
                        Text(if (replacingSentence) "The current draft will be replaced by the shared text."
                            else "The current draft will be replaced by text from this photo.")
                    },
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
                                    preferences.edit().remove("pending_replacement_image_path").commit()
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
                                                ocrError = "Could not open that photo. Choose another image or type the sentence."
                                            }
                                        }
                                    }
                                }
                            }
                            showReplace = false
                        }) { Text("Replace") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            discardSharePayload()
                            showReplace = false
                            sharedText.value = null
                            pendingPhotoFile?.delete()
                            pendingPhotoFile = null
                            preferences.edit().remove("pending_replacement_image_path").commit()
                            pendingSharedImageUri = null
                        }) { Text("Keep draft") }
                    },
                )
            }

            if (showOcrSheet && ocrChoices.size > 1) {
                ModalBottomSheet(onDismissRequest = { showOcrSheet = false }) {
                    Column(
                        Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Choose the text region", style = MaterialTheme.typography.titleLarge)
                        ocrChoices.forEachIndexed { index, block ->
                            OutlinedButton(
                                enabled = !submissionLocked,
                                onClick = {
                                    showOcrSheet = false
                                    ocrChoices = emptyList()
                                    applyOcrText(block.text, ocrFocusFraction(block.bounds, ocrImageHeight))
                                },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            ) { Text("${index + 1}. ${block.text.trim()}") }
                        }
                    }
                }
            }

            if (showDiscardConfirm) {
                val hasWork = editor.text.isNotBlank() || noteImagePath != null || target != null
                AlertDialog(
                    onDismissRequest = { showDiscardConfirm = false },
                    title = { Text("Discard this draft?") },
                    text = {
                        Text(
                            if (hasWork) {
                                "The sentence, target, photo and any generated results will be deleted. This cannot be undone."
                            } else {
                                "This draft is already empty."
                            },
                        )
                    },
                    confirmButton = {
                        TextButton(
                            enabled = !submissionLocked,
                            onClick = {
                                showDiscardConfirm = false
                                clearDraft()
                            },
                        ) { Text("Discard draft") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDiscardConfirm = false }) { Text("Keep working") }
                    },
                )
            }

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
            val frozenForDisplay = completedSubmission ?: if (submissionLocked) {
                publishedFrozenSnapshot ?: readFrozenSubmission(preferences)
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
            val visibleRoute = if (frozenForDisplay != null) AppRoute.Review else route
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
            val canSend = apiPermissionGranted && selectedModel != null && selectedDeck != null &&
                (if (frozenRetryReady) effectiveFrozenFields != null else fieldsByName != null && validationErrors.isEmpty()) &&
                effectiveMapping?.isValid == true && sendImageReady && frozenDestinationReady &&
                (frozenRetryReady || pitch != null) &&
                (frozenRetryReady || (sentenceAudioReady && wordAudioReady)) &&
                (frozenRetryReady || (!dictionaryLoading && !dictionarySearchLoading)) &&
                setupLoadedSuccessfully && !setupLoading && setupError == null && !recoveryRequired &&
                !isSubmitting && !duplicateBlocked

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
                route = AppRoute.Review
                frozenSnapshotAppliedRevision = snapshot.revision
            }

            androidx.compose.runtime.LaunchedEffect(submissionLocked, publishedFrozenSnapshot) {
                if (!submissionLocked) return@LaunchedEffect
                val snapshot = publishedFrozenSnapshot ?: readFrozenSubmission(preferences) ?: return@LaunchedEffect
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
                    recoveryMessage = "The frozen Send record is incomplete. Keep this draft and resolve the outcome manually before retrying."
                    return@LaunchedEffect
                }
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
                    recoveryMessage = "The frozen note type is unavailable. Confirm the previous outcome before choosing a new destination."
                    return@LaunchedEffect
                }
                if (model.fields != snapshot.modelFields) {
                    recoveryMessage = "The frozen note type fields changed. Confirm the previous outcome before retrying."
                    return@LaunchedEffect
                }
                val values = snapshot.modelFields.zip(snapshot.orderedFields).toMap()
                if (phase == SubmissionPhase.NOTE_INSERTED || phase == SubmissionPhase.DESTINATION_VERIFIED) {
                    val noteId = snapshot.noteId
                    if (noteId == null) {
                        recoveryMessage = "The inserted note ID was not saved. The outcome is uncertain; resolve it explicitly before retrying."
                        return@LaunchedEffect
                    }
                    knownNoteId = noteId
                    recoveryMessage = "Checking the saved note ID and destination cards in AnkiDroid…"
                    try {
                        val decision = withContext(Dispatchers.IO) {
                            val gateway = AnkiDroidGateway(contentResolver)
                            noteInsertedRecoveryDecision(noteId, gateway.noteCards(noteId), snapshot.deckId)
                        }
                        when (decision) {
                            NoteInsertedRecoveryDecision.COMPLETE -> {
                                check(preferences.edit().putString(SubmissionRecoveryKeys.STATUS, SubmissionPhase.DESTINATION_VERIFIED.persisted).commit())
                                persistLastSentTags(snapshot)
                                check(preferences.edit().putString(SubmissionRecoveryKeys.STATUS, "done").commit())
                                recoveryRequired = false
                                recoveryPhase = null
                                frozenRetryReady = false
                                completedSubmission = snapshot
                                knownNoteId = null
                                submitMessage = "Note ${noteId} recovered in the selected deck."
                            }
                            NoteInsertedRecoveryDecision.NEEDS_REVIEW -> recoveryMessage =
                                "The saved note has cards in another or mixed destination. Nothing was moved or reinserted; inspect AnkiDroid and resolve this draft explicitly."
                            NoteInsertedRecoveryDecision.INDETERMINATE -> recoveryMessage =
                                "The saved note ID has no verifiable cards. Nothing was moved or reinserted; inspect AnkiDroid and resolve this draft explicitly."
                            NoteInsertedRecoveryDecision.FAIL -> error("The frozen note ID or destination deck ID is invalid.")
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        recoveryMessage = "Could not verify the saved note ID. Retry this read-only recovery check; no new note will be inserted."
                    }
                    return@LaunchedEffect
                }
                if (phase != SubmissionPhase.NOTE_INSERTING) return@LaunchedEffect
                recoveryMessage = "Checking the frozen Send revision in AnkiDroid…"
                try {
                    val duplicateId = withContext(Dispatchers.IO) {
                        AnkiDroidGateway(contentResolver).hasMatchingNoteInAnyDeck(model, values)
                    }
                    if (duplicateId != null) {
                        recoveryDuplicate = true
                        knownNoteId = duplicateId
                        recoveryMessage = "A matching note exists for the frozen revision. Treat it as complete only after checking AnkiDroid."
                    } else if (decks.none { it.id == snapshot.deckId }) {
                        recoveryDuplicate = false
                        recoveryMessage = "No matching note was found, but the frozen destination deck is unavailable. Confirm the outcome before choosing a new destination."
                    } else {
                        recoveryRequired = false
                        frozenRetryReady = true
                        recoveryDuplicate = false
                        knownNoteId = null
                        recoveryMessage = "No matching note was found for the frozen revision; retry will reuse its saved media receipt."
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    recoveryMessage = "Could not verify the frozen Send. Retry the check or inspect AnkiDroid before resolving it."
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
                            setupError = "Could not read note types and decks. Check AnkiDroid API access and retry."
                        }
                    } finally {
                        if (currentSetupReload.value == loadRevision) setupLoading = false
                    }
                }
            }

            androidx.compose.runtime.LaunchedEffect(showSettings) {
                if (!showSettings) {
                    settingsCoordinator = null
                    return@LaunchedEffect
                }
                settingsLoading = true
                settingsSaving = false
                settingsError = null
                googleKeyVisible = false
                translationTestResult = null
                translationTestRunning = false
                val coordinator = SettingsCoordinator(
                    persistence = SharedPreferencesSettingsPersistence(settings),
                    onSaveResult = { result ->
                        coroutineScope.launch(Dispatchers.Main.immediate) {
                            settingsError = result.exceptionOrNull()?.message
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
                    settingsError = "Could not load saved settings."
                }
            }

            // Obsolete pre-shared-key OpenRouter ciphertext is deleted once per launch. It is never
            // read as a Google key; the shared key, drafts, and media are untouched.
            androidx.compose.runtime.LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) { GoogleCloudCredential.removeObsoleteOpenRouterStorage(settings) }
            }

            androidx.compose.runtime.LaunchedEffect(photoSourcePath, noteImagePath, processedImagePath) {
                preferences.edit()
                    .putString("photo_source_path", photoSourcePath)
                    .putString("note_image_path", noteImagePath)
                    .putString("processed_image_path", processedImagePath)
                    .apply()
            }

            androidx.compose.runtime.LaunchedEffect(
                editor.text, editor.selection, target, vocabulary, vocabularyReading, pitchAccentText, pitchHtmlText, definition,
                sentenceReading, readingCorrections, definitionManuallyEdited, dependentFieldsStale, translationDraft,
                translationAttemptedSentence,
                notes, tags, selectedModelId, selectedDeckId, selectedDictionaryEntryId, selectedSenseIndices, completedSubmission,
            ) {
                if (completedSubmission != null) return@LaunchedEffect
                preferences.edit()
                    .putString("sentence", editor.text)
                    .putInt("selection_start", editor.selection.min)
                    .putInt("selection_end", editor.selection.max)
                    .putInt("target_start", target?.start ?: -1)
                    .putInt("target_end", target?.end ?: -1)
                    .putString("vocabulary", vocabulary)
                    .putString("vocabulary_reading", vocabularyReading)
                    .putString("pitch_accent", pitchAccentText)
                    .putString("pitch_html", pitchHtmlText)
                    .putString("definition", definition)
                    .putBoolean("definition_manual", definitionManuallyEdited)
                    .putBoolean("definition_manual_stale", dependentFieldsStale && definitionManuallyEdited)
                    .remove("dependent_fields_stale")
                    .putString("sentence_reading", sentenceReading)
                    .putString("sentence_reading_corrections", SentenceReadings.encode(readingCorrections))
                    .remove("leave_sentence_target_unannotated")
                    .remove("leave_vocabulary_unannotated")
                    .putString("translation", translation)
                    .putString("translation_sentence", translationDraft.sentence)
                    .putString("translation_source", (translationDraft.result as? TranslationResult.Ready)?.source?.name)
                    .putBoolean("translation_manual", translationDraft.manuallyEdited)
                    .putBoolean("translation_stale", translationDraft.stale)
                    .putBoolean("translation_pending", activeTranslationResult() is TranslationResult.Pending)
                    .putString("translation_error", (activeTranslationResult() as? TranslationResult.Failed)?.message)
                    .putString(
                        "translation_suggestion",
                        (translationDraft.suggestion as? TranslationResult.Ready)?.text,
                    )
                    .putString(
                        "translation_suggestion_source",
                        (translationDraft.suggestion as? TranslationResult.Ready)?.source?.name,
                    )
                    .putString(
                        "translation_suggestion_error",
                        (translationDraft.suggestion as? TranslationResult.Failed)?.message,
                    )
                    .putString("translation_attempted_sentence", translationAttemptedSentence)
                    .putString("notes", notes)
                    .putString("tags", tags)
                    .putLong("model_id", selectedModelId ?: -1)
                    .putLong("deck_id", selectedDeckId ?: -1)
                    .putLong("dictionary_entry_id", selectedDictionaryEntryId ?: -1)
                    .putStringSet("dictionary_sense_indices", selectedSenseIndices.mapTo(mutableSetOf()) { it.toString() })
                    .apply()
            }

            val openKnownNote: (Long) -> Unit = { noteId ->
                val browserIntent = ankiBrowserIntent(noteId)
                if (browserIntent.resolveActivity(packageManager) != null) {
                    startActivity(browserIntent)
                } else {
                    submitMessage = "AnkiDroid browser is unavailable. Note $noteId matches '${displayVocabulary}' in the submitted sentence '${displaySentence}'. Keep this draft for manual resolution."
                }
            }

            val sendRecoveryContent: @androidx.compose.runtime.Composable () -> Unit = {
                knownNoteId?.let { noteId ->
                    TextButton(onClick = { openKnownNote(noteId) }) {
                        Text("Open matching note in AnkiDroid")
                    }
                }
                if (duplicateBlocked) TextButton(onClick = {
                    val blockedSnapshot = publishedFrozenSnapshot ?: readFrozenSubmission(preferences)
                    if (blockedSnapshot != null) applyFrozenSubmissionToDraft(blockedSnapshot)
                    clearPendingSubmission(preferences)
                    duplicateBlocked = false
                    recoveryRequired = false
                    recoveryPhase = null
                    frozenRetryReady = false
                    publishedFrozenSnapshot = null
                    frozenSnapshotAppliedRevision = -1L
                    knownNoteId = null
                    submitMessage = null
                    recoveryMessage = null
                    route = AppRoute.Target
                    draftGeneration++
                }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Edit and recheck")
                }
                if (recoveryRequired && recoveryPhase == SubmissionPhase.NOTE_INSERTING &&
                    (!apiPermissionGranted || setupLoading || setupError != null || !setupLoadedSuccessfully)) {
                    Text("Recovery is held until AnkiDroid setup succeeds; the frozen draft has not been cleared.", style = MaterialTheme.typography.bodyMedium)
                } else if (recoveryRequired && recoveryDuplicate) {
                    TextButton(onClick = {
                        val confirmedSnapshot = publishedFrozenSnapshot ?: readFrozenSubmission(preferences)
                        if (confirmedSnapshot == null) {
                            recoveryMessage = "The frozen Send is unavailable; keep the draft and resolve the outcome manually."
                            return@TextButton
                        }
                        persistLastSentTags(confirmedSnapshot)
                        check(preferences.edit().putString(SubmissionRecoveryKeys.STATUS, "done").commit()) {
                            "Could not save the resolved Send state."
                        }
                        completedSubmission = confirmedSnapshot
                        recoveryRequired = false
                        recoveryPhase = null
                        recoveryDuplicate = false
                        knownNoteId = null
                        submitMessage = "Pending Send marked complete."
                    }) { Text("Treat pending Send as complete") }
                } else if (recoveryRequired && recoveryPhase == SubmissionPhase.MEDIA_IMPORTING) {
                    val pendingOperation = preferences.getString(SubmissionRecoveryKeys.MEDIA_OPERATION, null)
                        ?.let { name -> runCatching { MediaImportOperation.valueOf(name) }.getOrNull() }
                    TextButton(onClick = {
                        recoveryRequired = false
                        frozenRetryReady = true
                        recoveryMessage = "Retrying the frozen media sequence may duplicate an import that reached AnkiDroid without a saved receipt."
                    }) { Text("Retry media import (may duplicate)") }
                    if (pendingOperation == MediaImportOperation.IMAGE) {
                        TextButton(onClick = {
                            clearPendingSubmission(preferences)
                            removeDraftImage()
                            recoveryRequired = false
                            recoveryPhase = null
                            recoveryMessage = "The interrupted image was discarded; the rest of the draft was kept."
                        }) { Text("Remove interrupted image") }
                    } else {
                        TextButton(onClick = {
                            clearPendingSubmission(preferences)
                            audioController.releaseFreeze(retainFiles = true)
                            audioSnapshot = audioController.snapshot()
                            publishedFrozenSnapshot = null
                            recoveryRequired = false
                            recoveryPhase = null
                            frozenRetryReady = false
                            recoveryMessage = "Frozen recovery cleared after review; the draft media was retained."
                        }) { Text("I checked; clear frozen recovery") }
                    }
                } else if (recoveryRequired && recoveryPhase == SubmissionPhase.MEDIA_IMPORTED) {
                    TextButton(onClick = {
                        recoveryRequired = false
                        frozenRetryReady = true
                        recoveryMessage = "Resuming the frozen Send will reuse the saved media receipt."
                    }) { Text("Resume frozen Send") }
                    TextButton(onClick = {
                        clearPendingSubmission(preferences)
                        removeDraftImage()
                        recoveryRequired = false
                        recoveryPhase = null
                        recoveryMessage = "Pending Send cleared after review; configure the destination before retrying."
                    }) { Text("I checked; configure destination") }
                } else if (recoveryRequired && recoveryMessage?.contains("No matching note") == true) {
                    TextButton(onClick = {
                        recoveryRequired = false
                        frozenRetryReady = true
                        recoveryMessage = "Retrying the frozen Send; the draft remains locked until it finishes."
                    }) { Text("Retry frozen Send") }
                } else if (recoveryRequired && knownNoteId != null && recoveryMessage?.contains("no longer") == true) {
                    Text("The saved note ID cannot be found. No automatic re-insert will be attempted; inspect AnkiDroid before resolving this draft.", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = {
                        clearPendingSubmission(preferences)
                        recoveryRequired = false
                        recoveryPhase = null
                        frozenRetryReady = false
                        knownNoteId = null
                        recoveryMessage = "The missing saved note outcome was explicitly acknowledged; review the draft before a new Send."
                    }) { Text("I checked; resolve outcome") }
                } else if (recoveryRequired && recoveryMessage?.contains("unavailable") == true) {
                    TextButton(onClick = {
                        clearPendingSubmission(preferences)
                        recoveryRequired = false
                        recoveryPhase = null
                        recoveryMessage = "Outcome acknowledged; choose a valid destination before retrying."
                    }) { Text("I checked; configure destination") }
                } else if (recoveryRequired && recoveryMessage?.startsWith("Could not verify") == true) {
                    TextButton(onClick = { setupReload++ }) { Text("Retry pending-send check") }
                } else if (recoveryRequired) {
                    TextButton(onClick = {
                        clearPendingSubmission(preferences)
                        recoveryRequired = false
                        recoveryPhase = null
                        recoveryDuplicate = false
                        recoveryMessage = "Pending Send held for manual resolution; configure the destination before retrying."
                    }) { Text("I checked; configure destination") }
                }
            }

            androidx.activity.compose.BackHandler(
                enabled = !navigationLocked && (
                    showSettings || showSources || showDictionarySheet || showOcrSheet ||
                        route != AppRoute.Capture
                    ),
            ) {
                when {
                    showSettings -> closeSettings()
                    showSources -> showSources = false
                    showDictionarySheet -> dismissDictionarySheet()
                    showOcrSheet -> showOcrSheet = false
                    route == AppRoute.Review -> {
                        generationStarted = false
                        route = AppRoute.Target
                    }
                    route == AppRoute.Target -> route = AppRoute.Capture
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
                        var submitAction: () -> Unit = {}
                        val reviewTarget = displayTarget?.takeIf { it.textIn(displaySentence) != null }
                        if (visibleRoute != AppRoute.Capture) {
                            FlowTopBar(
                                title = when (visibleRoute) {
                                    AppRoute.Capture -> ""
                                    AppRoute.Target -> "Target"
                                    AppRoute.Review -> "Review"
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                onBack = if (navigationLocked) null else when (visibleRoute) {
                                    AppRoute.Review -> ({ generationStarted = false; route = AppRoute.Target })
                                    AppRoute.Target -> ({ route = AppRoute.Capture })
                                    AppRoute.Capture -> null
                                },
                                onSettings = if (!navigationLocked) ({ openSettings() }) else null,
                            )
                        }
                        if (visibleRoute != AppRoute.Capture &&
                            (analyzerLoading || analyzerError != null || dictionaryLoading || dictionaryError != null)
                        ) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                                if (analyzerLoading) {
                                    Text(analyzerStatus, style = MaterialTheme.typography.bodySmall)
                                } else if (analyzerError != null) {
                                    Text(analyzerError!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                    TextButton(onClick = { analyzerRetry++ }) { Text("Retry UniDic setup") }
                                } else if (dictionaryLoading) {
                                    Text("Preparing the offline dictionary for first use…", style = MaterialTheme.typography.bodySmall)
                                } else if (dictionaryError != null) {
                                    Text(dictionaryError!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        val updateEditor: (TextFieldValue) -> Unit = updateEditor@{ updated ->
                            if (submissionLocked) return@updateEditor
                            if (updated.text != editor.text) {
                                if (photoSourcePath == null) setDraftOrigin("Type")
                                draftGeneration++
                                updateTranslationSentence(updated.text)
                                if (hasManualDefinition) dependentFieldsStale = true
                                selectTarget(null)
                                japaneseTokens = emptyList()
                                tokenizationError = null
                                tokenizationLoading = analyzerReady
                            }
                            editor = updated
                        }
                        val sendBlockers = buildList {
                            if (imagePending) {
                                add("The image is still being prepared. Send becomes available when it is ready.")
                            } else if (imageAttached && !imageProcessing && imageError == null && !imageReady) {
                                add("The selected image is not ready to send.")
                            } else if (!sendImageReady) {
                                add("The selected image is missing or not ready to send.")
                            }
                            if (!frozenRetryReady && !sentenceAudioReady) {
                                add(reviewAudioSendBlocker("Sentence", sentenceAudio.presentation(audioActionLabel(AudioItem.SENTENCE)))
                                    ?: "Sentence audio is not ready to send.")
                            }
                            if (!frozenRetryReady && !wordAudioReady) {
                                add(reviewAudioSendBlocker("Word", wordAudio.presentation(audioActionLabel(AudioItem.VOCABULARY)))
                                    ?: "Word audio is not ready to send.")
                            }
                            if (!apiPermissionGranted) {
                                add("Allow AnkiDroid API access in Settings before sending.")
                            }
                            if (selectedModel == null) add("Choose an AnkiDroid note type in Settings before sending.")
                            if (selectedDeck == null) add("Choose an AnkiDroid destination deck in Settings before sending.")
                            if (fieldsByName == null && !frozenRetryReady) {
                                add("The note fields are incomplete. Return to preparation and complete the note.")
                            }
                            validationErrors.firstOrNull()?.let { add("Still needed: $it") }
                            if (effectiveMapping?.isValid != true) {
                                add(
                                    effectiveMapping?.errors?.firstOrNull()?.let { "AnkiDroid field mapping: $it" }
                                        ?: "The note fields do not match the selected AnkiDroid note type.",
                                )
                            }
                            if (!frozenDestinationReady) {
                                add("Restore the frozen note's AnkiDroid note type and deck in Settings before retrying.")
                            }
                            if (!frozenRetryReady && pitch == null) {
                                add("Choose a source-listed pitch pattern before sending.")
                            }
                            if (!frozenRetryReady && (dictionaryLoading || dictionarySearchLoading)) {
                                add("The offline dictionary is still loading. Wait for it to finish before sending.")
                            }
                            if (setupError != null) {
                                add(setupError)
                            } else if (!setupLoadedSuccessfully) {
                                add("AnkiDroid setup could not be loaded. Open Settings and retry setup.")
                            }
                            if (setupLoading) add("AnkiDroid setup is still loading. Wait for it to finish.")
                        }
                        val submitHint = when {
                            completedSubmission != null || duplicateBlocked || recoveryRequired -> null
                            isSubmitting -> null
                            submitMessage != null -> null
                            else -> sendBlockers.firstOrNull()
                        }
                        val reviewTranslationError =
                            (activeTranslationResult() as? TranslationResult.Failed)?.message
                        val translationNeedsSettings = reviewTranslationError?.let {
                            it.contains("Google Cloud API key", ignoreCase = true) ||
                                it.contains("credential", ignoreCase = true)
                        } == true
                        val submitHintActionLabel = when {
                            translationNeedsSettings -> "Open Settings"
                            reviewTranslationError != null -> "Retry"
                            !sentenceAudioReady -> audioActionLabel(AudioItem.SENTENCE)
                            !wordAudioReady -> audioActionLabel(AudioItem.VOCABULARY)
                            !apiPermissionGranted || selectedModel == null || selectedDeck == null || setupError != null -> "Open Settings"
                            else -> "Edit preparation"
                        }
                        val submitHintRole = if (
                            translationNeedsSettings || !apiPermissionGranted || selectedModel == null ||
                            selectedDeck == null || setupError != null
                        ) ReviewStatusRole.Information else ReviewStatusRole.Warning
                        val submitHintAction: (() -> Unit) = when {
                            translationNeedsSettings -> ({ openSettings() })
                            reviewTranslationError != null -> ({
                                translationAutoAllowed = true
                                translationRetry++
                            })
                            !sentenceAudioReady -> ({ onAudioAction(AudioItem.SENTENCE) })
                            !wordAudioReady -> ({ onAudioAction(AudioItem.VOCABULARY) })
                            !apiPermissionGranted || selectedModel == null || selectedDeck == null || setupError != null -> ({ openSettings() })
                            else -> ({
                                generationStarted = false
                                route = AppRoute.Target
                            })
                        }
                        val loadMoreMatches: () -> Unit = {
                            val query = target?.textIn(editor.text)
                            if (query != null) {
                                val callbackGeneration = draftGeneration
                                coroutineScope.launch {
                                    if (!asyncDraftIsCurrent(callbackGeneration)) return@launch
                                    dictionarySearchLoading = true
                                    try {
                                        val more = withContext(Dispatchers.IO) {
                                            offlineDictionary.search(
                                                query,
                                                analyzedTarget?.expression,
                                                offset = dictionaryEntries.size,
                                            )
                                        }
                                        if (!asyncDraftIsCurrent(callbackGeneration)) return@launch
                                        dictionaryEntries = dictionaryEntries + more
                                        dictionaryHasMore = more.size == 50
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        if (asyncDraftIsCurrent(callbackGeneration)) {
                                            dictionaryError = "Could not load more dictionary matches."
                                        }
                                    } finally {
                                        dictionarySearchLoading = false
                                    }
                                }
                            }
                        }
                        when (visibleRoute) {
                            AppRoute.Capture -> InputScreen(
                                captureMode = captureMode,
                                onCaptureModeChange = { mode ->
                                    captureMode = mode
                                    preferences.edit().putString("capture_mode", mode).apply()
                                },
                                editor = editor,
                                onEditorChange = updateEditor,
                                onContinue = { route = AppRoute.Target },
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
                                enabled = !submissionLocked,
                                cameraPermissionGranted = cameraPermissionGranted,
                                onRequestCamera = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                                cameraError = cameraError,
                                ocrLoading = ocrLoading,
                                ocrError = ocrError,
                                onRetryOcr = {
                                    val source = photoSourcePath?.let(::File)?.takeIf(File::isFile)
                                    if (source != null) {
                                        processImage(source, true)
                                    } else {
                                        photoPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                        )
                                    }
                                },
                                imageStatus = when {
                                    imageProcessing -> "Preparing the photo for Anki…"
                                    imageError != null -> imageError
                                    imageAttached -> "Photo attached"
                                    else -> null
                                },
                                imageFailed = !imageProcessing && imageError != null,
                                onRetryImage = {
                                    val source = photoSourcePath?.let(::File)?.takeIf(File::isFile)
                                    if (source != null) {
                                        processImage(source, true)
                                    } else {
                                        photoPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                        )
                                    }
                                },
                                onSettings = { openSettings() },
                            )
                            AppRoute.Target -> TargetScreen(
                                sentence = editor.text,
                                tokens = japaneseTokens,
                                selectedSpan = target,
                                onSelectSpan = { span ->
                                    selectTarget(span)
                                    editor = editor.copy(selection = TextRange(span.start, span.end))
                                },
                                onEditSentence = { showSentenceEditor = true },
                                entryLabel = selectedDictionaryEntry?.expression ?: vocabulary.takeIf { it.isNotBlank() },
                                entryDetail = selectedDictionaryEntry?.reading,
                                entryCount = dictionaryEntries.size,
                                onChooseEntry = { showDictionarySheet = true },
                                senses = selectedDictionaryEntry?.senses
                                    ?.map { sense -> sense.glosses.joinToString("; ") }
                                    ?: emptyList(),
                                selectedSenses = selectedSenseIndices,
                                onToggleSense = { index, checked ->
                                    if (!submissionLocked) {
                                        val newSelection = if (checked) {
                                            selectedSenseIndices + index
                                        } else {
                                            selectedSenseIndices - index
                                        }
                                        selectedSenseIndices = newSelection
                                        selectedDictionaryEntry?.let { entry ->
                                            definition = newSelection.sorted().mapNotNull { selected ->
                                                entry.senses.getOrNull(selected)?.glosses?.joinToString("; ")
                                            }.joinToString("\n")
                                        }
                                        definitionManuallyEdited = false
                                        definitionProvenanceKnown = true
                                    }
                                },
                                meaningStatus = when {
                                    ocrError != null -> ocrError
                                    preparationAuthorizationError != null -> preparationAuthorizationError
                                    target == null -> "Tap a word to choose the target."
                                    dictionarySearchLoading -> "Searching JMdict…"
                                    selectedDictionaryEntry == null && dictionaryLoading -> "Preparing the offline dictionary…"
                                    selectedDictionaryEntry == null && dictionaryEntries.size > 1 -> "Choose the intended dictionary entry."
                                    selectedDictionaryEntry == null && dictionaryReady -> "No dictionary match for this selection."
                                    vocabularyReadingError != null -> vocabularyReadingError
                                    pitchIndexError != null -> pitchIndexError
                                    else -> null
                                },
                                canContinue = !submissionLocked && target != null && selectedDictionaryEntry != null &&
                                    definition.isNotBlank() && pitch != null,
                                continueHint = when {
                                    target == null -> "Tap a word to continue."
                                    selectedDictionaryEntry == null -> "Choose a dictionary entry to continue."
                                    definition.isBlank() -> "Choose a meaning to continue."
                                    pitch == null -> "A source-listed pitch pattern is required to continue."
                                    else -> null
                                },
                                onContinue = { continueToReview() },
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                locked = submissionLocked,
                                tokensLoading = tokenizationLoading,
                                hasMoreEntries = dictionaryHasMore && !dictionarySearchLoading,
                                onLoadMoreEntries = loadMoreMatches,
                                ocrChoicesAvailable = ocrChoices.size > 1,
                                onReopenOcrChooser = { showOcrSheet = ocrChoices.size > 1 },
                                vocabularyReading = vocabularyReading,
                                onVocabularyReadingChange = { applyVocabularyReading(it) },
                                vocabularyReadingError = vocabularyReadingError,
                                correctableReadings = correctableReadings,
                                onReadingCorrection = { span, reading -> applyReadingCorrection(span, reading) },
                                readingCorrectionError = readingCorrectionError,
                                definition = definition,
                                definitionStale = dependentFieldsStale && definitionManuallyEdited,
                                onDefinitionChange = { text ->
                                    if (!submissionLocked) {
                                        definition = text
                                        definitionManuallyEdited = true
                                        definitionProvenanceKnown = true
                                        selectedSenseIndices = emptySet()
                                        dependentFieldsStale = false
                                    }
                                },
                                onAcknowledgeDefinition = { dependentFieldsStale = false },
                                translation = translation,
                                translationPending = activeTranslationResult() is TranslationResult.Pending,
                                translationError = (activeTranslationResult() as? TranslationResult.Failed)?.message,
                                translationStale = translationDraft.manuallyEdited && translationDraft.stale,
                                onTranslationChange = { text ->
                                    if (!submissionLocked) {
                                        translationRequestRevision++
                                        translationDraft = translationDraft.saveManualEdit(text)
                                    }
                                },
                                onRetryTranslation = {
                                    translationAutoAllowed = true
                                    translationRetry++
                                },
                                pitchAlternatives = pitchCandidates.map(AjtPitchRecord::accent),
                                selectedPitchAccent = pitchAccentText.toIntOrNull(),
                                onSelectPitch = { accent ->
                                    pitchCandidates.firstOrNull { it.accent == accent }?.let { record ->
                                        pitchAccentText = record.accent.toString()
                                        pitchHtmlText = record.html
                                    }
                                },
                                imageAttached = imageAttached,
                                imageProcessing = imageProcessing,
                                imageError = imageError,
                                onChoosePhoto = {
                                    if (!submissionLocked) noteImagePicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                    )
                                },
                                onRemovePhoto = { if (!submissionLocked) removeDraftImage() },
                            )
                            AppRoute.Review -> Box(Modifier.weight(1f).fillMaxWidth()) {
                                if (reviewTarget != null) {
                                    ReviewCard(
                                        sentence = displaySentence,
                                        target = reviewTarget,
                                        sentenceReading = frozenForDisplay?.sentenceReading ?: sentenceReading,
                                        furigana = if (frozenForDisplay == null) sentenceFurigana else emptyList(),
                                        vocabulary = displayVocabulary,
                                        vocabularyReading = displayVocabularyReading,
                                        pitch = displayPitch,
                                        definition = displayDefinition,
                                        translation = displayTranslation,
                                        translationPending = activeTranslationResult() is TranslationResult.Pending,
                                        translationError = reviewTranslationError,
                                        translationStale = translationDraft.manuallyEdited && translationDraft.stale,
                                        editingEnabled = !submissionLocked && completedSubmission == null,
                                        onRetryTranslation = {
                                            translationAutoAllowed = true
                                            translationRetry++
                                        },
                                        onOpenSettings = { openSettings() },
                                        sentenceAudio = sentenceAudio.presentation(audioActionLabel(AudioItem.SENTENCE)),
                                        vocabularyAudio = wordAudio.presentation(audioActionLabel(AudioItem.VOCABULARY)),
                                        sentenceAudioAction = audioActionLabel(AudioItem.SENTENCE),
                                        vocabularyAudioAction = audioActionLabel(AudioItem.VOCABULARY),
                                        onToggleSentenceAudio = {
                                            if (playingAudio == AudioItem.SENTENCE) {
                                                stopAudioPlayback()
                                                playingAudio = null
                                            } else {
                                                audioReadyFile(AudioItem.SENTENCE)?.let { playAudio(AudioItem.SENTENCE, it) }
                                            }
                                        },
                                        onRetrySentenceAudio = { onAudioAction(AudioItem.SENTENCE) },
                                        onToggleVocabularyAudio = {
                                            if (playingAudio == AudioItem.VOCABULARY) {
                                                stopAudioPlayback()
                                                playingAudio = null
                                            } else {
                                                audioReadyFile(AudioItem.VOCABULARY)?.let { playAudio(AudioItem.VOCABULARY, it) }
                                            }
                                        },
                                        onRetryVocabularyAudio = { onAudioAction(AudioItem.VOCABULARY) },
                                        notes = displayNotes,
                                        tags = displayTags.split(Regex("\\s+")).filter(String::isNotBlank),
                                        imagePath = frozenForDisplay?.media?.path ?: noteImagePath,
                                        imageStatus = when {
                                            imageProcessing -> "Preparing image…"
                                            imageError != null -> imageError
                                            imageAttached -> "Image is not available"
                                            else -> null
                                        },
                                        ocrChoicesAvailable = ocrChoices.size > 1,
                                        onReopenOcrChooser = { showOcrSheet = ocrChoices.size > 1 },
                                        pitchHasAlternatives = pitchCandidates.size > 1,
                                        pitchInfoExpanded = pitchInfoExpanded,
                                        onTogglePitchInfo = { pitchInfoExpanded = !pitchInfoExpanded },
                                        onEdit = {
                                            if (!submissionLocked) {
                                                generationStarted = false
                                                route = AppRoute.Target
                                            }
                                        },
                                        onTagsChange = { text -> if (!submissionLocked) tags = text },
                                        onNotesChange = { text -> if (!submissionLocked) notes = text },
                                        sendPresentation = when {
                                            isSubmitting -> ReviewSendPresentation.Sending
                                            completedSubmission != null -> ReviewSendPresentation.Status(
                                                submitMessage ?: "Note sent to AnkiDroid.",
                                            )
                                            duplicateBlocked -> ReviewSendPresentation.Status(
                                                submitMessage ?: "A matching note already exists in AnkiDroid.",
                                            )
                                            recoveryRequired -> ReviewSendPresentation.Status(
                                                recoveryMessage ?: "Pending Send needs review.",
                                            )
                                            submitMessage?.startsWith("Blocked:") == true ->
                                                ReviewSendPresentation.Status(submitMessage.orEmpty())
                                            submitMessage != null -> ReviewSendPresentation.Status(
                                                submitMessage!!,
                                                "Retry Send",
                                            )
                                            else -> ReviewSendPresentation.Ready
                                        },
                                        onSend = if (canSend && completedSubmission == null && !duplicateBlocked &&
                                            submitMessage?.startsWith("Blocked:") != true
                                        ) {
                                            ({ submitAction() })
                                        } else {
                                            null
                                        },
                                        onSendStatusAction = if (canSend && completedSubmission == null && !duplicateBlocked) {
                                            ({ submitAction() })
                                        } else {
                                            null
                                        },
                                        sendStatusContent = sendRecoveryContent,
                                        imageFocalY = photoFocusY,
                                        sendHint = submitHint,
                                        sendHintActionLabel = submitHintActionLabel,
                                        sendHintRole = submitHintRole,
                                        onSendHintAction = submitHintAction,
                                    )
                                }
                            }
                        }
                        if (showSentenceEditor) {
                            ModalBottomSheet(onDismissRequest = { showSentenceEditor = false }) {
                                Column(
                                    Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState())
                                        .padding(horizontal = 24.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Text("Edit sentence", style = MaterialTheme.typography.titleLarge)
                                    OutlinedTextField(
                                        value = editor,
                                        onValueChange = updateEditor,
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text("Japanese sentence") },
                                        minLines = 3,
                                    )
                                    Text(
                                        "Select part of the sentence, then use it as the target for a multi-word phrase.",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            enabled = editor.selection.start != editor.selection.end,
                                            onClick = {
                                                selectTarget(
                                                    TargetSpan.fromSelection(
                                                        editor.text,
                                                        editor.selection.start,
                                                        editor.selection.end,
                                                    ),
                                                )
                                                showSentenceEditor = false
                                            },
                                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                        ) { Text("Use selection as target") }
                                        Button(
                                            onClick = { showSentenceEditor = false },
                                            modifier = Modifier.heightIn(min = 48.dp),
                                        ) { Text("Done") }
                                    }
                                }
                            }
                        }
                        if (showDeckSheet) {
                            ModalBottomSheet(onDismissRequest = { showDeckSheet = false }) {
                                Column(
                                    Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState())
                                        .padding(horizontal = 24.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text("Destination deck", style = MaterialTheme.typography.titleLarge)
                                    if (decks.isEmpty()) {
                                        Text(
                                            "No decks are available. Check AnkiDroid setup in Settings.",
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                    decks.forEach { deck ->
                                        TextButton(
                                            onClick = {
                                                selectedDeckId = deck.id
                                                preferences.edit().putLong("deck_id", deck.id).apply()
                                                showDeckSheet = false
                                            },
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                        ) { Text(deck.name) }
                                    }
                                }
                            }
                        }
                        if (visibleRoute == AppRoute.Review && reviewTarget == null) {
                            submitMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                            recoveryMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                            sendRecoveryContent()
                        }
                        submitAction = submitAction@{
                            if (!canSend || isSubmitting || recoveryRequired || duplicateBlocked || completedSubmission != null ||
                                submitMessage?.startsWith("Blocked:") == true
                            ) {
                                return@submitAction
                            }
                            val model = selectedModel ?: return@submitAction
                            val deck = selectedDeck ?: return@submitAction
                            val restoredFrozen = if (frozenRetryReady) readFrozenSubmission(preferences) else null
                            if (frozenRetryReady && (restoredFrozen == null || restoredFrozen.modelId != model.id ||
                                    restoredFrozen.deckId != deck.id || restoredFrozen.modelFields != model.fields)
                            ) {
                                submitMessage = "The frozen Send destination is unavailable; resolve it before retrying."
                                return@submitAction
                            }
                            val fields = restoredFrozen?.modelFields?.zip(restoredFrozen.orderedFields)?.toMap()
                                ?: fieldsByName ?: return@submitAction
                            val tagSet = restoredFrozen?.tags ?: AnkiFieldContract.parseTags(tags)
                            // Freeze both audio items before any import: the files are pinned by
                            // leases and the identities are immutable from here on. A restored
                            // frozen Send already owns its audio, so it is not frozen twice.
                            val frozenAudio = if (restoredFrozen != null) null else {
                                when (val freeze = audioController.freezeForSend()) {
                                    is DraftAudioFreeze.Frozen -> freeze.snapshot
                                    is DraftAudioFreeze.Incomplete -> {
                                        submitMessage =
                                            "Wait for or retry the missing audio before sending."
                                        return@submitAction
                                    }
                                    DraftAudioFreeze.Disposed -> return@submitAction
                                }
                            }
                            audioSnapshot = audioController.snapshot()
                            publishedFrozenSnapshot = restoredFrozen ?: modelMapping?.takeIf { it.isValid }?.let { mapping ->
                                FrozenSubmission(
                                    revision = nextSubmissionRevision(
                                        preferences.getLong(SubmissionRecoveryKeys.REVISION_SEQUENCE, 0L),
                                        preferences.getLong(SubmissionRecoveryKeys.REVISION, 0L),
                                    ),
                                    modelId = model.id,
                                    deckId = deck.id,
                                    modelFields = model.fields,
                                    orderedFields = mapping.values,
                                    sentence = editor.text,
                                    lemma = vocabulary,
                                    vocabularyReading = vocabularyReading,
                                    sentenceReading = sentenceReading,
                                    definition = definition,
                                    translation = translation,
                                    notes = notes,
                                    tags = tagSet,
                                    pitchAccentText = pitchAccentText,
                                    targetStart = target?.start ?: -1,
                                    targetEnd = target?.end ?: -1,
                                    media = processedImageIdentity,
                                    importedFilename = imageReceiptFilename,
                                    noteId = null,
                                )
                            }
                            draftGeneration++
                            translationRequestRevision++
                            translationAutoAllowed = false
                            if (activeTranslationResult() is TranslationResult.Pending) {
                                translationDraft = translationDraft.requestFinished(
                                    TranslationResult.Failed("Translation paused while sending. Retry after resolving this Send."),
                                )
                            }
                            dictionaryLoading = false
                            dictionarySearchLoading = false
                            analyzerLoading = false
                            tokenizationLoading = false
                            pitchLookupLoading = false
                            ocrLoading = false
                            isSubmitting = true
                            knownNoteId = null
                            submitMessage = null
                            coroutineScope.launch {
                                suspend fun publishFrozen(snapshot: FrozenSubmission) {
                                    withContext(Dispatchers.Main.immediate) {
                                        publishedFrozenSnapshot = snapshot
                                    }
                                }
                                try {
                                    val outcome = withContext(Dispatchers.IO) {
                                        val gateway = AnkiDroidGateway(contentResolver, applicationContext)
                                        val duplicateId = gateway.hasDuplicate(model, deck, fields)
                                        if (duplicateId != null) {
                                            preferences.edit().putString(SubmissionRecoveryKeys.STATUS, "blocked").commit()
                                            SubmissionOutcome("duplicate", duplicateId)
                                        } else {
                                            var frozen = if (restoredFrozen != null) {
                                                val localFile = restoredFrozen.media?.let { File(it.path) }
                                                val localExists = localFile?.exists() == true
                                                check(
                                                    frozenImagePermitsImport(
                                                        importedFilename = restoredFrozen.importedFilename,
                                                        frozenIdentity = restoredFrozen.media,
                                                        receiptIdentity = receiptIdentity(preferences, IMAGE_RECEIPT_KEYS),
                                                        receiptFilename = preferences.getString(IMAGE_RECEIPT_FILENAME, null),
                                                        localFileExists = localExists,
                                                        localIdentity = if (localExists) liveMediaIdentity(localFile) else null,
                                                    ),
                                                ) { "The frozen image receipt is unavailable; resolve the pending Send manually." }
                                                restoredFrozen
                                            } else {
                                                val includeImage = imageAttached && !imageProcessing
                                                val processed = if (includeImage) {
                                                    processedImagePath?.let(::File)?.takeIf(File::isFile)
                                                } else null
                                                if (includeImage) check(processed != null) {
                                                    "The selected image is not ready for import."
                                                }
                                                val imageIdentity = liveMediaIdentity(processed)
                                                val importedImage = if (includeImage) {
                                                    importedReceiptFromPreferences(
                                                        preferences,
                                                        IMAGE_RECEIPT_KEYS,
                                                        imageIdentity,
                                                        AnkiMediaImporter::sanitizeFilename,
                                                    )
                                                } else null
                                                val frozenFiles = frozenAudio
                                                    ?: error("Draft audio was not frozen for this Send.")
                                                val sentenceEntry = frozenFiles.files.getValue(AudioItem.SENTENCE)
                                                val wordEntry = frozenFiles.files.getValue(AudioItem.VOCABULARY)
                                                val sentenceIdentity = MediaIdentity(
                                                    sentenceEntry.path, sentenceEntry.size, sentenceEntry.modified,
                                                )
                                                val wordIdentity = MediaIdentity(
                                                    wordEntry.path, wordEntry.size, wordEntry.modified,
                                                )
                                                val sentenceImported = importedReceiptFromPreferences(
                                                    preferences, SENTENCE_AUDIO_RECEIPT_KEYS, sentenceIdentity,
                                                    AnkiMediaImporter::sanitizeAudioFilename,
                                                )
                                                val wordImported = importedReceiptFromPreferences(
                                                    preferences, WORD_AUDIO_RECEIPT_KEYS, wordIdentity,
                                                    AnkiMediaImporter::sanitizeAudioFilename,
                                                )
                                                val baseDraft = noteDraft.copy(
                                                    imageFilename = importedImage,
                                                    sentenceAudioFilename = sentenceImported,
                                                    vocabularyAudioFilename = wordImported,
                                                )
                                                val baseFields = baseDraft.fieldsByName()
                                                    ?: error("The note changed before Send could be frozen.")
                                                val baseMapping = AnkiFieldContract.mapToModelOrder(model.fields, baseFields)
                                                check(baseMapping.isValid) { baseMapping.errors.joinToString() }
                                                FrozenSubmission(
                                                    revision = nextSubmissionRevision(
                                                        preferences.getLong(SubmissionRecoveryKeys.REVISION_SEQUENCE, 0L),
                                                        preferences.getLong(SubmissionRecoveryKeys.REVISION, 0L),
                                                    ),
                                                    modelId = model.id,
                                                    deckId = deck.id,
                                                    modelFields = model.fields,
                                                    orderedFields = baseMapping.values,
                                                    sentence = editor.text,
                                                    lemma = vocabulary,
                                                    vocabularyReading = vocabularyReading,
                                                    sentenceReading = sentenceReading,
                                                    definition = definition,
                                                    translation = translation,
                                                    notes = notes,
                                                    tags = tagSet,
                                                    pitchAccentText = pitchAccentText,
                                                    targetStart = target?.start ?: -1,
                                                    targetEnd = target?.end ?: -1,
                                                    media = imageIdentity,
                                                    importedFilename = importedImage,
                                                    noteId = null,
                                                    sentenceAudio = FrozenAudioItem(sentenceIdentity, sentenceImported),
                                                    wordAudio = FrozenAudioItem(wordIdentity, wordImported),
                                                ).also { snapshot ->
                                                    check(preferences.edit()
                                                        .putLong(SubmissionRecoveryKeys.REVISION_SEQUENCE, snapshot.revision)
                                                        .putFrozenSubmission(snapshot, SubmissionPhase.MEDIA_IMPORTING)
                                                        .commit()) { "Could not save frozen submission recovery data." }
                                                }
                                            }
                                            publishFrozen(frozen)

                                            var durableSnapshot = frozen
                                            fun persistReceipt(receipt: ImportedMediaReceipt): Boolean {
                                                val updated = try {
                                                    applyImportedReceipt(durableSnapshot, receipt)
                                                } catch (_: Exception) {
                                                    return false
                                                }
                                                val saved = runCatching {
                                                    preferences.edit()
                                                        .putFrozenSubmission(updated, SubmissionPhase.MEDIA_IMPORTED)
                                                        .apply { putMediaReceipt(this, receipt) }
                                                        .remove(SubmissionRecoveryKeys.MEDIA_OPERATION)
                                                        .remove(SubmissionRecoveryKeys.MEDIA_SOURCE_PATH)
                                                        .remove(SubmissionRecoveryKeys.MEDIA_SOURCE_SIZE)
                                                        .remove(SubmissionRecoveryKeys.MEDIA_SOURCE_MODIFIED)
                                                        .commit()
                                                }.getOrDefault(false)
                                                if (saved) durableSnapshot = updated
                                                return saved
                                            }
                                            val importedReceipts = try {
                                                importSendMedia(
                                                    sentenceAudio = frozen.sentenceAudio?.takeUnless { it.omitted }?.let { item ->
                                                        val identity = item.identity
                                                            ?: error("The frozen sentence audio has no media identity.")
                                                        mediaImportRequest(
                                                            preferences,
                                                            SENTENCE_AUDIO_RECEIPT_KEYS,
                                                            MediaImportOperation.SENTENCE_AUDIO,
                                                            identity,
                                                            item.importedFilename,
                                                        )
                                                    },
                                                    wordAudio = frozen.wordAudio?.takeUnless { it.omitted }?.let { item ->
                                                        val identity = item.identity
                                                            ?: error("The frozen vocabulary audio has no media identity.")
                                                        mediaImportRequest(
                                                            preferences,
                                                            WORD_AUDIO_RECEIPT_KEYS,
                                                            MediaImportOperation.WORD_AUDIO,
                                                            identity,
                                                            item.importedFilename,
                                                        )
                                                    },
                                                    image = frozen.media?.let { identity ->
                                                        mediaImportRequest(
                                                            preferences,
                                                            IMAGE_RECEIPT_KEYS,
                                                            MediaImportOperation.IMAGE,
                                                            identity,
                                                            frozen.importedFilename,
                                                        )
                                                    },
                                                    markImporting = { operation, source ->
                                                        preferences.edit()
                                                            .putFrozenSubmission(durableSnapshot, SubmissionPhase.MEDIA_IMPORTING)
                                                            .putString(SubmissionRecoveryKeys.MEDIA_OPERATION, operation.name)
                                                            .putString(SubmissionRecoveryKeys.MEDIA_SOURCE_PATH, source.path)
                                                            .putLong(SubmissionRecoveryKeys.MEDIA_SOURCE_SIZE, source.size)
                                                            .putLong(SubmissionRecoveryKeys.MEDIA_SOURCE_MODIFIED, source.modified)
                                                            .commit()
                                                    },
                                                    import = { operation, identity ->
                                                        val file = File(identity.path)
                                                        check(liveMediaIdentity(file) == identity) {
                                                            "Frozen media changed before provider import."
                                                        }
                                                        when (operation) {
                                                            MediaImportOperation.SENTENCE_AUDIO,
                                                            MediaImportOperation.WORD_AUDIO -> gateway.importAudio(file, operation)
                                                            MediaImportOperation.IMAGE -> gateway.importProcessedImage(file)
                                                            MediaImportOperation.AUDIO -> error("Generic audio is not a Send operation.")
                                                        }
                                                    },
                                                    persistReceipt = ::persistReceipt,
                                                )
                                            } catch (cancelled: CancellationException) {
                                                throw cancelled
                                            } catch (error: Exception) {
                                                withContext(Dispatchers.Main.immediate) {
                                                    publishedFrozenSnapshot = durableSnapshot
                                                }
                                                throw error
                                            }
                                            // The importer calls the callback for new receipts. Persist
                                            // reused frozen receipts too, so recovery has one canonical record.
                                            importedReceipts.forEach { receipt ->
                                                check(persistReceipt(receipt)) {
                                                    "Could not save the ${receipt.operation.label} receipt."
                                                }
                                            }
                                            frozen = durableSnapshot
                                            check(preferences.edit()
                                                .putFrozenSubmission(frozen, SubmissionPhase.MEDIA_IMPORTED)
                                                .remove(SubmissionRecoveryKeys.MEDIA_OPERATION)
                                                .remove(SubmissionRecoveryKeys.MEDIA_SOURCE_PATH)
                                                .remove(SubmissionRecoveryKeys.MEDIA_SOURCE_SIZE)
                                                .remove(SubmissionRecoveryKeys.MEDIA_SOURCE_MODIFIED)
                                                .commit()) { "Could not save completed media recovery state." }
                                            publishFrozen(frozen)
                                            val frozenFields = frozen.modelFields.zip(frozen.orderedFields).toMap()
                                            val frozenMapping = AnkiFieldContract.mapToModelOrder(model.fields, frozenFields)
                                            check(frozenMapping.isValid) { frozenMapping.errors.joinToString() }
                                            check(preferences.edit().putFrozenSubmission(frozen, SubmissionPhase.NOTE_INSERTING).commit()) {
                                                "Could not save frozen submission data."
                                            }
                                            publishFrozen(frozen)
                                            // A process death between provider insert and this commit leaves NOTE_INSERTING without an ID;
                                            // recovery must reconcile explicitly rather than blindly inserting again.
                                            val insertedNoteId = gateway.insertNote(model, frozenMapping.values, tagSet)
                                            val insertedSnapshot = frozen.copy(noteId = insertedNoteId)
                                            check(preferences.edit().putFrozenSubmission(
                                                insertedSnapshot,
                                                SubmissionPhase.NOTE_INSERTED,
                                            ).commit()) { "Could not persist the inserted note ID before deck assignment." }
                                            publishFrozen(insertedSnapshot)
                                            check(gateway.ensureNoteInDeck(insertedNoteId, deck.id)) {
                                                "The inserted note no longer has cards; resolve the saved note ID before retrying."
                                            }
                                            check(preferences.edit().putFrozenSubmission(
                                                insertedSnapshot,
                                                SubmissionPhase.DESTINATION_VERIFIED,
                                            ).commit()) { "Could not persist verified destination membership." }
                                            persistLastSentTags(insertedSnapshot)
                                            check(preferences.edit().putString(SubmissionRecoveryKeys.STATUS, "done").commit())
                                            clearPersistedDraft(preferences, File(filesDir, "draft"))
                                            SubmissionOutcome("created", insertedNoteId)
                                        }
                                    }
                                    when (outcome.status) {
                                        "duplicate" -> {
                                            audioController.releaseFreeze(retainFiles = false)
                                            audioSnapshot = audioController.snapshot()
                                            frozenRetryReady = false
                                            recoveryPhase = null
                                            duplicateBlocked = true
                                            knownNoteId = outcome.noteId
                                            if (restoredFrozen != null) clearPendingSubmission(preferences)
                                            submitMessage = "Blocked: a note with this sentence and vocabulary lemma already exists."
                                        }
                                        "created" -> {
                                            // The note is verified: nothing needs the frozen files
                                            // any more, so release them (clearDraft then discards).
                                            audioController.releaseFreeze(retainFiles = false)
                                            audioSnapshot = audioController.snapshot()
                                            completedSubmission = publishedFrozenSnapshot ?: readFrozenSubmission(preferences)
                                            frozenRetryReady = false
                                            recoveryRequired = false
                                            recoveryPhase = null
                                            recoveryDuplicate = false
                                            knownNoteId = null
                                            submitMessage = "Note sent to AnkiDroid."
                                        }
                                        else -> {
                                            audioController.releaseFreeze(retainFiles = false)
                                            audioSnapshot = audioController.snapshot()
                                            frozenRetryReady = false
                                            recoveryPhase = null
                                            submitMessage = "AnkiDroid did not add the note. You can safely review and retry."
                                        }
                                    }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: AnkiProviderDefiniteInsertException) {
                                    audioController.releaseFreeze(retainFiles = false)
                                    audioSnapshot = audioController.snapshot()
                                    clearPendingSubmission(preferences)
                                    publishedFrozenSnapshot = null
                                    recoveryRequired = false
                                    recoveryPhase = null
                                    frozenRetryReady = false
                                    knownNoteId = null
                                    submitMessage = error.message.orEmpty() + " Retry Send to rerun duplicate checking before any new insert."
                                } catch (error: Exception) {
                                    val phase = SubmissionPhase.fromPersisted(
                                        preferences.getString(SubmissionRecoveryKeys.STATUS, null),
                                    )
                                    if (phase != null) {
                                        val mediaFailure = error as? MediaImportException
                                        val mediaOperation = mediaFailure?.operation ?: preferences
                                            .getString(SubmissionRecoveryKeys.MEDIA_OPERATION, null)
                                            ?.let { name -> runCatching { MediaImportOperation.valueOf(name) }.getOrNull() }
                                        // Uncertain Send: keep the frozen files and their leases so
                                        // recovery can still see the audio this draft promised.
                                        audioController.releaseFreeze(retainFiles = true)
                                        audioSnapshot = audioController.snapshot()
                                        recoveryPhase = phase
                                        recoveryRequired = true
                                        frozenRetryReady = false
                                        recoveryMessage = when (phase) {
                                            SubmissionPhase.MEDIA_IMPORTING -> {
                                                val label = mediaOperation?.label ?: "media"
                                                val stage = mediaFailure?.stage?.name?.lowercase()?.replace('_', ' ')
                                                "The $label import${stage?.let { " failed during $it" }.orEmpty()} may have reached AnkiDroid. Inspect the collection before resuming; do not blindly retry."
                                            }
                                            SubmissionPhase.MEDIA_IMPORTED -> "Media receipts were saved, but note insertion did not start. Review the frozen note before resuming."
                                            SubmissionPhase.NOTE_INSERTING -> "Could not confirm note insertion. Checking the frozen Send revision…"
                                            SubmissionPhase.NOTE_INSERTED -> "Note ID was saved, but deck assignment was interrupted. Recovery will move only that note's cards."
                                            SubmissionPhase.DESTINATION_VERIFIED -> "The note's destination was verified; recovery will finalize it without inserting again."
                                        }
                                    } else if (imageAttached) {
                                        audioController.releaseFreeze(retainFiles = false)
                                        audioSnapshot = audioController.snapshot()
                                        submitMessage = "Could not import the selected image. The draft was kept; retry or remove the image."
                                    } else {
                                        audioController.releaseFreeze(retainFiles = false)
                                        audioSnapshot = audioController.snapshot()
                                        submitMessage = "Could not send the note. Check AnkiDroid API access and retry."
                                    }
                                } finally {
                                    isSubmitting = false
                                }
                            }
                        }
                        if (visibleRoute == AppRoute.Review && reviewTarget == null) Button(
                            enabled = completedSubmission == null && !duplicateBlocked &&
                                submitMessage?.startsWith("Blocked:") != true && canSend,
                            onClick = { submitAction() },
                        ) {
                            if (isSubmitting) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Text("Sending…")
                                }
                            } else {
                                Text(
                                    when {
                                        completedSubmission != null -> "Sent to AnkiDroid"
                                        submitMessage?.startsWith("Blocked:") == true -> "Already in AnkiDroid"
                                        recoveryRequired -> "Pending Send needs review"
                                        submitMessage != null -> "Retry Send"
                                        !frozenRetryReady && imagePending -> "Preparing image…"
                                        else -> "Send to AnkiDroid"
                                    },
                                )
                            }
                        }
                    }
                }
                if (showDictionarySheet && target?.textIn(editor.text) != null && dictionaryEntries.size > 1 &&
                    selectedDictionaryEntry == null
                ) {
                    ModalBottomSheet(onDismissRequest = { dismissDictionarySheet() }) {
                        Column(
                            Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("Choose dictionary entry", style = MaterialTheme.typography.titleLarge)
                            dictionaryEntries.forEach { entry ->
                                OutlinedButton(
                                    enabled = !submissionLocked,
                                    onClick = {
                                        if (!definitionProvenanceKnown) {
                                            val savedSenseIndices = preferences.getStringSet("dictionary_sense_indices", null)
                                                ?.mapNotNull(String::toIntOrNull)?.filter { it in entry.senses.indices }
                                                ?.takeIf { it.isNotEmpty() } ?: listOf(0)
                                            val savedGlosses = savedSenseIndices.sorted().mapNotNull { index ->
                                                entry.senses.getOrNull(index)?.glosses?.joinToString("; ")
                                            }.joinToString("\n")
                                            definitionManuallyEdited = definition.isNotBlank() && definition != savedGlosses
                                            definitionProvenanceKnown = true
                                        }
                                        pitchAccentText = ""
                                        vocabularyReadingError = null
                                        pitchIndexError = null
                                        if (definitionManuallyEdited && definition.isNotBlank()) dependentFieldsStale = true
                                        vocabulary = entry.expression
                                        vocabularyReading = entry.reading
                                        selectedDictionaryEntry = entry
                                        selectedDictionaryEntryId = entry.id
                                        selectedSenseIndices = if (entry.senses.isEmpty()) emptySet() else setOf(0)
                                        if (!definitionManuallyEdited) {
                                            definition = entry.senses.firstOrNull()?.glosses?.joinToString("; ").orEmpty()
                                        }
                                        showDictionarySheet = false
                                    },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                ) {
                                    Column(horizontalAlignment = Alignment.Start) {
                                        Text("${entry.expression} · ${entry.reading}")
                                        Text(entry.senses.firstOrNull()?.glosses?.joinToString("; ").orEmpty())
                                    }
                                }
                            }
                        }
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


private fun clearPersistedDraft(preferences: SharedPreferences, draftDirectory: File? = null) {
    val ownedDirectory = draftDirectory?.canonicalFile
    val ownedPaths = listOfNotNull(
        preferences.getString("photo_source_path", null),
        preferences.getString("note_image_path", null),
        preferences.getString("processed_image_path", null),
        preferences.getString("pending_replacement_image_path", null),
    )
    ownedPaths.distinct().forEach { path ->
        val file = File(path)
        if (ownedDirectory != null && file.exists() && file.canonicalFile.parentFile == ownedDirectory) file.delete()
    }
    val draftKeys = listOf(
        "sentence", "selection_start", "selection_end", "target_start", "target_end",
        "vocabulary", "vocabulary_reading", "pitch_accent", "definition", "definition_manual_stale", "sentence_reading",
        "sentence_reading_corrections",
        "leave_sentence_target_unannotated", "leave_vocabulary_unannotated", "dependent_fields_stale",
        "definition_manual", "definition_manual_stale", "translation", "translation_sentence", "translation_source",
        "translation_manual", "translation_stale", "translation_pending", "translation_error",
        "translation_suggestion", "translation_suggestion_source", "translation_suggestion_error",
        "translation_attempted_sentence", "notes", "tags", "submission_status", "pending_model_id", "pending_deck_id",
        "pending_sentence", "pending_lemma", "dictionary_entry_id", "dictionary_sense_indices",
        "sentence_furigana_corrections", "photo_source_path", "note_image_path", "processed_image_path",
        IMAGE_RECEIPT_PATH, IMAGE_RECEIPT_FILENAME, IMAGE_RECEIPT_SIZE, IMAGE_RECEIPT_MODIFIED,
        SENTENCE_AUDIO_RECEIPT_PATH, SENTENCE_AUDIO_RECEIPT_FILENAME,
        SENTENCE_AUDIO_RECEIPT_SIZE, SENTENCE_AUDIO_RECEIPT_MODIFIED,
        WORD_AUDIO_RECEIPT_PATH, WORD_AUDIO_RECEIPT_FILENAME,
        WORD_AUDIO_RECEIPT_SIZE, WORD_AUDIO_RECEIPT_MODIFIED,
        SubmissionRecoveryKeys.MEDIA_OPERATION, SubmissionRecoveryKeys.MEDIA_SOURCE_PATH,
        SubmissionRecoveryKeys.MEDIA_SOURCE_SIZE, SubmissionRecoveryKeys.MEDIA_SOURCE_MODIFIED,
        "pending_replacement_image_path",
        "draft_origin",
        SubmissionRecoveryKeys.REVISION, SubmissionRecoveryKeys.MODEL_FIELDS,
        SubmissionRecoveryKeys.ORDERED_FIELDS, SubmissionRecoveryKeys.VOCABULARY_READING,
        SubmissionRecoveryKeys.SENTENCE_READING, SubmissionRecoveryKeys.DEFINITION,
        SubmissionRecoveryKeys.TRANSLATION, SubmissionRecoveryKeys.NOTES, SubmissionRecoveryKeys.TAGS,
        SubmissionRecoveryKeys.PITCH, SubmissionRecoveryKeys.TARGET_START, SubmissionRecoveryKeys.TARGET_END,
        SubmissionRecoveryKeys.MEDIA_PATH, SubmissionRecoveryKeys.MEDIA_SIZE,
        SubmissionRecoveryKeys.MEDIA_MODIFIED, SubmissionRecoveryKeys.IMPORTED_FILENAME,
        SubmissionRecoveryKeys.NOTE_ID,
    ) + draftAudioPreferenceKeys()
    preferences.edit().also { editor -> draftKeys.forEach(editor::remove) }.commit()
}

private fun clearPendingSubmission(preferences: SharedPreferences) {
    clearFrozenSubmission(preferences)
}

private fun TextRange.coerceIn(min: Int, max: Int): TextRange =
    TextRange(start.coerceIn(min, max), end.coerceIn(min, max))
