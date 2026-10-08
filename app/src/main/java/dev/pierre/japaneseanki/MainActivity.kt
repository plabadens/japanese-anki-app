package dev.pierre.japaneseanki

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import dev.pierre.japaneseanki.ui.ReviewCard
import dev.pierre.japaneseanki.ui.ReviewSendPresentation
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
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
    private var textToSpeech: TextToSpeech? = null
    private val offlineJapaneseVoices = mutableStateOf<List<Voice>>(emptyList())
    private val textToSpeechEngine = mutableStateOf<String?>(null)
    private val textToSpeechReady = mutableStateOf(false)
    private val textToSpeechError = mutableStateOf(false)

    private fun initializeTextToSpeech() {
        textToSpeech?.shutdown()
        textToSpeech = null
        textToSpeechReady.value = false
        textToSpeechError.value = false
        offlineJapaneseVoices.value = emptyList()
        textToSpeechEngine.value = null
        val handler = Handler(Looper.getMainLooper())
        var created: TextToSpeech? = null
        try {
            created = TextToSpeech(applicationContext) { status ->
                handler.post {
                    val service = created ?: return@post
                    if (textToSpeech !== service) return@post
                    if (status == TextToSpeech.SUCCESS) {
                        textToSpeechEngine.value = service.defaultEngine
                        offlineJapaneseVoices.value = service.voices.orEmpty()
                            .filter {
                                it.locale.language == "ja" && !it.isNetworkConnectionRequired &&
                                    "notInstalled" !in it.features
                            }
                            .sortedBy { it.name }
                    } else {
                        textToSpeechError.value = true
                    }
                    textToSpeechReady.value = true
                }
            }
            textToSpeech = created
        } catch (_: Exception) {
            textToSpeechError.value = true
            textToSpeechReady.value = true
        }
    }

    override fun onDestroy() {
        textToSpeech?.shutdown()
        textToSpeech = null
        super.onDestroy()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedText.value = textFromIntent(intent)
        sharedImage.value = imageFromIntent(intent)
        offlineDictionary = sharedDictionary(this)
        offlineAnalyzer = OfflineJapaneseAnalyzer.shared(this)
        japaneseOcr = JapaneseOcr(this)
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
            val ttsVoiceDataLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult(),
            ) { initializeTextToSpeech() }
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
            var vocabularyReading by remember { mutableStateOf("") }
            var pitchAccentText by remember { mutableStateOf("") }
            val pitch = pitchAccentText.toIntOrNull()?.let { PitchAccent.create(vocabularyReading, it) }
            var definition by remember { mutableStateOf(preferences.getString("definition", "").orEmpty()) }
            var definitionManuallyEdited by remember { mutableStateOf(preferences.getBoolean("definition_manual", false)) }
            var definitionProvenanceKnown by remember { mutableStateOf(preferences.contains("definition_manual")) }
            var sentenceReading by remember { mutableStateOf("") }
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
            val translationSource = (translationDraft.result as? TranslationResult.Ready)?.source
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
            val localTranslationFallback = remember { MlKitLocalTranslationFallback(applicationContext) }
            DisposableEffect(localTranslationFallback) {
                onDispose { localTranslationFallback.close() }
            }
            var translationInfoExpanded by remember { mutableStateOf(false) }
            var notes by remember { mutableStateOf(preferences.getString("notes", "").orEmpty()) }
            var defaultTags by remember { mutableStateOf(settings.getString("default_tags", "").orEmpty()) }
            var tags by remember {
                mutableStateOf(
                    initialDraftTags(
                        preferences.getString("tags", null),
                        settings.getString("last_note_tags", null),
                        defaultTags,
                    ),
                )
            }
            val restoredPendingPhoto = preferences.getString("pending_replacement_image_path", null)
                ?.let(::File)?.takeIf(File::isFile)
            var showReplace by remember { mutableStateOf(restoredPendingPhoto != null) }
            var showSources by remember { mutableStateOf(false) }
            var showSettings by remember { mutableStateOf(false) }
            var settingsDefaultTags by remember { mutableStateOf("") }
            var azureKey by remember { mutableStateOf("") }
            var azureKeyVisible by remember { mutableStateOf(false) }
            var azureRegion by remember { mutableStateOf("") }
            var azureVoice by remember { mutableStateOf("") }
            var azureRate by remember { mutableStateOf(1f) }
            var androidTtsVoiceName by remember { mutableStateOf("") }
            var androidTtsRate by remember { mutableStateOf(1f) }
            var settingsLoading by remember { mutableStateOf(false) }
            var settingsSaving by remember { mutableStateOf(false) }
            var settingsError by remember { mutableStateOf<String?>(null) }
            var showAndroidVoiceMenu by remember { mutableStateOf(false) }
            var dictionaryRetry by remember { mutableStateOf(0) }
            var dictionaryReady by remember { mutableStateOf(false) }
            var dictionaryLoading by remember { mutableStateOf(false) }
            var dictionaryError by remember { mutableStateOf<String?>(null) }
            var dictionaryInfo by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
            var analyzerReady by remember { mutableStateOf(false) }
            var analyzerLoading by remember { mutableStateOf(false) }
            var analyzerProgress by remember { mutableStateOf(0f) }
            var analyzerError by remember { mutableStateOf<String?>(null) }
            var tokenizationLoading by remember { mutableStateOf(false) }
            var tokenizationError by remember { mutableStateOf<String?>(null) }
            var analysisRetry by remember { mutableStateOf(0) }
            var japaneseTokens by remember { mutableStateOf<List<JapaneseToken>>(emptyList()) }
            val sentenceFurigana = sentenceFuriganaSpans(editor.text, japaneseTokens, target)
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
            var pitchCandidates by remember { mutableStateOf<List<DictionaryPitch>>(emptyList()) }
            var pitchLookupLoading by remember { mutableStateOf(false) }
            var noteTypes by remember { mutableStateOf<List<AnkiNoteType>>(emptyList()) }
            var decks by remember { mutableStateOf<List<AnkiDeck>>(emptyList()) }
            var setupLoading by remember { mutableStateOf(false) }
            var setupLoadedSuccessfully by remember { mutableStateOf(false) }
            var setupError by remember { mutableStateOf<String?>(null) }
            var setupReload by remember { mutableStateOf(0) }
            var selectedModelId by remember {
                mutableStateOf(preferences.getLong("model_id", -1).takeIf { it > 0 })
            }
            var selectedDeckId by remember {
                mutableStateOf(preferences.getLong("deck_id", -1).takeIf { it > 0 })
            }
            var captureMode by remember { mutableStateOf(preferences.getString("capture_mode", "Photo") ?: "Photo") }
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
            var autoOpenReview by remember { mutableStateOf(false) }
            var photoSourcePath by remember {
                mutableStateOf(preferences.getString("photo_source_path", null)?.takeIf { File(it).isFile })
            }
            var noteImagePath by remember {
                mutableStateOf(preferences.getString("note_image_path", null)?.takeIf { File(it).isFile })
            }
            var processedImagePath by remember {
                mutableStateOf(preferences.getString("processed_image_path", null)?.takeIf { File(it).isFile })
            }
            var imageProcessing by remember { mutableStateOf(false) }
            var imageProcessingRevision by remember { mutableStateOf(0L) }
            var imageError by remember { mutableStateOf<String?>(null) }
            var ocrLoading by remember { mutableStateOf(false) }
            var ocrError by remember { mutableStateOf<String?>(null) }
            var ocrChoices by remember { mutableStateOf<List<OcrBlock>>(emptyList()) }
            var showOcrSheet by remember { mutableStateOf(false) }
            var pendingPhotoFile by remember { mutableStateOf(restoredPendingPhoto) }
            var pendingSharedImageUri by remember { mutableStateOf<Uri?>(null) }
            var cameraPermissionGranted by remember {
                mutableStateOf(checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
            }
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
                        SubmissionPhase.MEDIA_IMPORTING -> "Image import was interrupted before its receipt was saved. Do not retry automatically."
                        SubmissionPhase.MEDIA_IMPORTED -> "Image import was recorded, but note insertion did not start. Review before resuming."
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
            var omitImageForSend by remember { mutableStateOf(false) }
            val selectedModel = noteTypes.firstOrNull { it.id == selectedModelId }
            val selectedDeck = decks.firstOrNull { it.id == selectedDeckId }
            val incoming = sharedText.value
            val submissionLocked = isSubmitting || recoveryRequired || frozenRetryReady || duplicateBlocked
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
                omitImageForSend = false
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
                tags = settings.getString("last_note_tags", defaultTags).orEmpty()
                selectedDictionaryEntry = null
                selectedDictionaryEntryId = null
                selectedSenseIndices = emptySet()
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
                autoOpenReview = selected != null
                if (selected != previousTarget) {
                    if (hasManualDefinition) dependentFieldsStale = true
                    selectedDictionaryEntry = null
                    selectedDictionaryEntryId = null
                    dictionaryEntries = emptyList()
                    pitchAccentText = ""
                }
                target = selected
                if (selected != null && sentenceReading.isBlank()) {
                    sentenceReading = resolveSurfaceReading(
                        editor.text, japaneseTokens, selected,
                    ).orEmpty()
                }
                if (selected != null && vocabulary.isBlank()) {
                    val surface = selected.textIn(editor.text).orEmpty()
                    val lemma = resolveAnalyzedLemma(japaneseTokens, selected)
                    vocabulary = lemma?.expression ?: surface
                    if (lemma != null && vocabularyReading.isBlank() && containsKanji(lemma.expression)) {
                        vocabularyReading = lemma.reading
                    }
                }
            }
            val applyOcrText: (String) -> Unit = applyOcrText@{ text ->
                if (currentSubmissionLocked.value) return@applyOcrText
                selectTarget(null)
                replaceEditorText(text.trim(), false)
                japaneseTokens = emptyList()
                tokenizationError = null
                tokenizationLoading = analyzerReady
                ocrError = null
                route = AppRoute.Target
            }
            val processImage: (File, Boolean) -> Unit = processImage@{ file, runOcr ->
                if (currentSubmissionLocked.value) return@processImage
                val callbackGeneration = draftGeneration
                val callbackEditorRevision = editorInputRevision
                val previousFiles = listOfNotNull(noteImagePath, processedImagePath)
                    .distinct().filter { it != file.absolutePath }
                if (!runOcr && photoSourcePath == noteImagePath) photoSourcePath = null
                if (runOcr) photoSourcePath = file.absolutePath
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
                        .remove("processed_image_path")
                        .commit(),
                ) { "Could not save the draft image source." }
                imageError = null
                omitImageForSend = false
                imageProcessing = true
                val processingRevision = ++imageProcessingRevision
                val destination = File(filesDir, "draft/${java.util.UUID.randomUUID()}.avif")
                coroutineScope.launch {
                    try {
                        val processed = ImageMediaProcessor.process(file, destination)
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
                            when {
                                blocks.size > 1 -> {
                                    ocrChoices = blocks
                                    showOcrSheet = true
                                }
                                blocks.size == 1 -> applyOcrText(blocks.single().text)
                                result.text.isNotBlank() -> applyOcrText(result.text)
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
                omitImageForSend = false
                preferences.edit()
                    .remove("photo_source_path")
                    .remove("note_image_path")
                    .remove("processed_image_path")
                    .remove(IMAGE_RECEIPT_PATH)
                    .remove(IMAGE_RECEIPT_FILENAME)
                    .remove(IMAGE_RECEIPT_SIZE)
                    .remove(IMAGE_RECEIPT_MODIFIED)
                    .commit()
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
            val photoPicker = rememberLauncherForActivityResult(
                ActivityResultContracts.PickVisualMedia(),
            ) { uri ->
                if (uri != null) coroutineScope.launch {
                    if (currentSubmissionLocked.value) return@launch
                    val callbackGeneration = draftGeneration
                    val callbackRevision = imageProcessingRevision
                    try {
                        val copied = copyImageToDraft(uri)
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
                        val copied = copyImageToDraft(uri)
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

            androidx.compose.runtime.LaunchedEffect(dictionaryRetry, setupGeneration, submissionLocked) {
                if (currentSubmissionLocked.value) return@LaunchedEffect
                val callbackGeneration = setupGeneration
                analyzerLoading = true
                analyzerError = null
                analyzerProgress = 0f
                try {
                    withContext(Dispatchers.IO) {
                        offlineAnalyzer.prepare { progress ->
                            if (asyncSetupIsCurrent(callbackGeneration)) analyzerProgress = progress
                        }
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
                    if (asyncSetupIsCurrent(callbackGeneration)) analyzerError = "UniDic could not be installed. Check available storage and retry."
                } finally {
                    analyzerLoading = false
                }
            }

            androidx.compose.runtime.LaunchedEffect(
                editor.text, translationRequestRevision, translationRetry, translationAutoAllowed, submissionLocked,
            ) {
                val sentence = editor.text
                if (sentence.isBlank() || submissionLocked) return@LaunchedEffect

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
                if (sentence != editor.text || requestRevision != currentTranslationRequestRevision.value ||
                    currentSubmissionLocked.value
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
                        TranslationService.fromSettings(settings, local = localTranslationFallback).translate(sentence)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    TranslationResult.Failed("Translation is unavailable. Tap Retry to continue.")
                }
                if (sentence == editor.text && requestRevision == currentTranslationRequestRevision.value &&
                    !currentSubmissionLocked.value
                ) {
                    translationDraft = translationDraft.requestFinished(result)
                }
            }

            androidx.compose.runtime.LaunchedEffect(analyzerReady, editor.text, analysisRetry, draftGeneration, submissionLocked) {
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
                    val analyzed = withContext(Dispatchers.IO) { offlineAnalyzer.analyze(editor.text) }
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
                dependentFieldsStale,
            ) {
                if (submissionLocked) return@LaunchedEffect
                val targetSpan = target ?: return@LaunchedEffect
                val targetText = targetSpan.textIn(editor.text) ?: return@LaunchedEffect
                val resolved = resolveAnalyzedLemma(japaneseTokens, targetSpan) ?: return@LaunchedEffect
                val shouldResolveSurface = vocabulary.isBlank() ||
                    (!dependentFieldsStale && vocabulary == targetText)
                if (shouldResolveSurface) vocabulary = resolved.expression
                if ((shouldResolveSurface || vocabulary == resolved.expression) && vocabularyReading.isBlank() &&
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

            androidx.compose.runtime.LaunchedEffect(
                route, autoOpenReview, target, editor.text, selectedDictionaryEntry, selectedDictionaryEntryId,
                vocabulary, vocabularyReading, dictionarySearchLoading, submissionLocked,
            ) {
                if (submissionLocked) return@LaunchedEffect
                val entry = selectedDictionaryEntry
                if (route == AppRoute.Target && autoOpenReview && target?.textIn(editor.text) != null &&
                    entry != null && entry.id == selectedDictionaryEntryId && entry.expression == vocabulary &&
                    entry.reading == vocabularyReading && !dictionarySearchLoading
                ) {
                    route = AppRoute.Review
                    autoOpenReview = false
                }
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
                        val entry = entries.singleOrNull() ?: restoredEntry
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
                        if (entries.size > 1 && entry == null) showDictionarySheet = true
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
                target, editor.text, dictionaryRetry, draftGeneration, submissionLocked,
            ) {
                if (currentSubmissionLocked.value) return@LaunchedEffect
                val callbackGeneration = draftGeneration
                val entry = selectedDictionaryEntry?.takeIf {
                    it.id == selectedDictionaryEntryId && it.expression == vocabulary && it.reading == vocabularyReading
                }
                if (!dictionaryReady || entry == null || vocabulary.isBlank() || vocabularyReading.isBlank()) {
                    pitchCandidates = emptyList()
                    pitchAccentText = ""
                    pitchLookupLoading = false
                    return@LaunchedEffect
                }
                pitchAccentText = ""
                pitchCandidates = emptyList()
                pitchLookupLoading = true
                try {
                    val candidates = withContext(Dispatchers.IO) {
                        offlineDictionary.pitch(entry.expression, entry.reading)
                    }.filter { PitchAccent.create(entry.reading, it.accent) != null }
                    if (!asyncDraftIsCurrent(callbackGeneration)) return@LaunchedEffect
                    pitchCandidates = candidates
                    if (candidates.isNotEmpty()) {
                        pitchAccentText = candidates.first().accent.toString()
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
                        route = AppRoute.Capture
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
            if (showSettings) {
                AlertDialog(
                    onDismissRequest = { if (!settingsSaving) showSettings = false },
                    title = { Text("Settings") },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("New-note defaults", style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = { showSettings = false; showSources = true }) {
                                Text("Sources & licenses")
                            }
                            OutlinedTextField(
                                value = settingsDefaultTags,
                                onValueChange = { settingsDefaultTags = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Default tags") },
                                supportingText = { Text("Hierarchical tags such as JLPT::N4 are supported.") },
                                singleLine = true,
                                enabled = !settingsLoading && !settingsSaving,
                            )
                            Text("Cloud speech", style = MaterialTheme.typography.titleMedium)
                            Text("Optional Azure settings. The key is encrypted with Android Keystore and stays on this device.")
                            if (settingsLoading) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Text("Loading saved settings…")
                                }
                            }
                            OutlinedTextField(
                                value = azureKey,
                                onValueChange = { azureKey = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Azure subscription key") },
                                visualTransformation = if (azureKeyVisible) VisualTransformation.None
                                    else PasswordVisualTransformation(),
                                singleLine = true,
                                enabled = !settingsLoading && !settingsSaving,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = azureKeyVisible,
                                    onCheckedChange = { azureKeyVisible = it },
                                    enabled = !settingsLoading && !settingsSaving,
                                )
                                Text("Show key")
                            }
                            OutlinedTextField(
                                value = azureRegion,
                                onValueChange = { azureRegion = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Azure region") },
                                singleLine = true,
                                enabled = !settingsLoading && !settingsSaving,
                            )
                            OutlinedTextField(
                                value = azureVoice,
                                onValueChange = { azureVoice = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Japanese Azure voice name") },
                                singleLine = true,
                                enabled = !settingsLoading && !settingsSaving,
                            )
                            Text("Azure speech rate: ${"%.2f".format(azureRate)}×")
                            Slider(
                                value = azureRate,
                                onValueChange = { azureRate = it },
                                valueRange = 0.5f..2f,
                                steps = 29,
                                enabled = !settingsLoading && !settingsSaving,
                            )
                            Text("On-device speech", style = MaterialTheme.typography.titleMedium)
                            Text(
                                when {
                                    !textToSpeechReady.value -> "Checking installed text-to-speech voices…"
                                    textToSpeechError.value -> "Android could not initialize a text-to-speech engine."
                                    textToSpeechEngine.value != null -> "System engine: ${textToSpeechEngine.value}"
                                    else -> "No system text-to-speech engine was reported."
                                },
                            )
                            TextButton(onClick = {
                                runCatching {
                                    ttsVoiceDataLauncher.launch(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
                                }.onFailure { settingsError = "Could not open Android text-to-speech settings." }
                            }) { Text("Manage installed voice data") }
                            if (textToSpeechError.value) {
                                TextButton(onClick = { initializeTextToSpeech() }) { Text("Retry voice discovery") }
                            }
                            if (offlineJapaneseVoices.value.isNotEmpty()) {
                                Box {
                                    OutlinedButton(
                                        onClick = { showAndroidVoiceMenu = true },
                                        enabled = !settingsLoading && !settingsSaving,
                                    ) {
                                        val selectedVoice = offlineJapaneseVoices.value.firstOrNull {
                                            it.name == androidTtsVoiceName
                                        }
                                        Text(selectedVoice?.let { "${it.locale.displayName} · ${it.name}" }
                                            ?: "Choose installed offline Japanese voice")
                                    }
                                    DropdownMenu(
                                        expanded = showAndroidVoiceMenu,
                                        onDismissRequest = { showAndroidVoiceMenu = false },
                                    ) {
                                        offlineJapaneseVoices.value.forEach { voice ->
                                            DropdownMenuItem(
                                                text = { Text("${voice.locale.displayName} · ${voice.name}") },
                                                onClick = {
                                                    androidTtsVoiceName = voice.name
                                                    showAndroidVoiceMenu = false
                                                },
                                            )
                                        }
                                    }
                                }
                                if (androidTtsVoiceName.isNotBlank() &&
                                    offlineJapaneseVoices.value.none { it.name == androidTtsVoiceName }
                                ) Text("Saved voice is no longer installed or offline-capable.")
                            } else if (textToSpeechReady.value) {
                                Text("No installed offline Japanese voice was found. Add one in Android settings.")
                            }
                            Text("Android speech rate: ${"%.2f".format(androidTtsRate)}×")
                            Slider(
                                value = androidTtsRate,
                                onValueChange = { androidTtsRate = it },
                                valueRange = 0.5f..2f,
                                steps = 29,
                                enabled = !settingsLoading && !settingsSaving,
                            )
                            Text("Speech generation is not active yet; these preferences are saved for the audio layer.")
                            settingsError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            enabled = !settingsLoading && !settingsSaving,
                            onClick = {
                                val key = azureKey.trim()
                                val region = azureRegion.trim()
                                val voice = azureVoice.trim()
                                when {
                                    key.isNotEmpty() && (region.isEmpty() || voice.isEmpty()) ->
                                        settingsError = "Enter an Azure region and voice when a key is configured."
                                    region.isNotEmpty() && !region.matches(Regex("[A-Za-z0-9-]+")) ->
                                        settingsError = "Enter a valid Azure region name."
                                    else -> {
                                        settingsSaving = true
                                        settingsError = null
                                        coroutineScope.launch {
                                            try {
                                                withContext(Dispatchers.IO) {
                                                    val edit = settings.edit()
                                                        .putString("default_tags", settingsDefaultTags)
                                                        .putString("azure_region", region)
                                                        .putString("azure_voice", voice)
                                                        .putFloat("azure_rate", azureRate)
                                                        .putString("android_tts_voice", androidTtsVoiceName)
                                                        .putFloat("android_tts_rate", androidTtsRate)
                                                    if (key.isBlank()) {
                                                        edit.remove("azure_key_encrypted")
                                                    } else {
                                                        edit.putString("azure_key_encrypted", EncryptedSecretStore.encrypt(key))
                                                    }
                                                    check(edit.commit()) { "Could not persist settings." }
                                                    if (key.isBlank()) runCatching { EncryptedSecretStore.removeKey() }
                                                }
                                                defaultTags = settingsDefaultTags
                                                showSettings = false
                                            } catch (cancelled: CancellationException) {
                                                throw cancelled
                                            } catch (_: Exception) {
                                                settingsError = "Settings could not be saved. The Azure key was not stored in plain text."
                                            } finally {
                                                settingsSaving = false
                                            }
                                        }
                                    }
                                }
                            },
                        ) { Text(if (settingsSaving) "Saving…" else "Save settings") }
                    },
                    dismissButton = {
                        TextButton(
                            enabled = !settingsSaving,
                            onClick = { showSettings = false },
                        ) { Text("Cancel") }
                    },
                )
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
                                            val copied = copyImageToDraft(uri)
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
                ModalBottomSheet(onDismissRequest = { }) {
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
                                    applyOcrText(block.text)
                                },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            ) { Text("${index + 1}. ${block.text.trim()}") }
                        }
                    }
                }
            }

            val processedImageIdentity = processedImagePath?.let { path ->
                runCatching { File(path).takeIf(File::isFile)?.let(MediaIdentity::fromFile) }.getOrNull()
            }
            val imageReceiptFilename = if (
                processedImageIdentity != null &&
                preferences.getString(IMAGE_RECEIPT_PATH, null) == processedImageIdentity.path &&
                preferences.getLong(IMAGE_RECEIPT_SIZE, -1L) == processedImageIdentity.size &&
                preferences.getLong(IMAGE_RECEIPT_MODIFIED, -1L) == processedImageIdentity.modified
            ) {
                preferences.getString(IMAGE_RECEIPT_FILENAME, null)
                    ?.let(AnkiMediaImporter::sanitizeFilename)
            } else null
            val imageAttached = noteImagePath != null
            val imagePending = imageAttached && imageProcessing
            val imageReady = !imageAttached || omitImageForSend || imagePending || (
                imageError == null && processedImagePath?.let(::File)?.isFile == true
            )
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
                dependentFieldsStale = dependentFieldsStale,
                translation = translation,
                imageFilename = imageReceiptFilename,
                notes = notes,
                sentenceFurigana = sentenceFurigana,
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
                ?.let { PitchAccent.create(displayVocabularyReading, it) } ?: pitch
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
                snapshot.importedFilename == null || (
                    snapshot.media != null &&
                        preferences.getString(IMAGE_RECEIPT_PATH, null) == snapshot.media.path &&
                        preferences.getLong(IMAGE_RECEIPT_SIZE, -1L) == snapshot.media.size &&
                        preferences.getLong(IMAGE_RECEIPT_MODIFIED, -1L) == snapshot.media.modified &&
                        preferences.getString(IMAGE_RECEIPT_FILENAME, null) == snapshot.importedFilename &&
                        (!File(snapshot.media.path).exists() ||
                            runCatching { MediaIdentity.fromFile(File(snapshot.media.path)) == snapshot.media }.getOrDefault(false))
                )
            } ?: true
            val sendImageReady = if (frozenRetryReady) frozenReceiptReady else imageReady
            val canSend = apiPermissionGranted && selectedModel != null && selectedDeck != null &&
                (if (frozenRetryReady) effectiveFrozenFields != null else fieldsByName != null && validationErrors.isEmpty()) &&
                effectiveMapping?.isValid == true && sendImageReady && frozenDestinationReady &&
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
                        val completed = withContext(Dispatchers.IO) {
                            val gateway = AnkiDroidGateway(contentResolver)
                            val cards = gateway.noteCards(noteId)
                            when (noteInsertedRecoveryDecision(noteId, cards, snapshot.deckId)) {
                                NoteInsertedRecoveryDecision.COMPLETE -> true
                                NoteInsertedRecoveryDecision.MOVE -> gateway.ensureNoteInDeck(noteId, snapshot.deckId)
                                NoteInsertedRecoveryDecision.RESOLVE -> false
                                NoteInsertedRecoveryDecision.FAIL -> error("The frozen destination deck ID is invalid.")
                            }
                        }
                        if (!completed) {
                            recoveryMessage = "The saved note ID no longer resolves to cards. Do not insert again; explicitly resolve this outcome."
                        } else {
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
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        recoveryMessage = "Could not verify or move the saved note ID. Retry this recovery check; no new note will be inserted."
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

            androidx.compose.runtime.LaunchedEffect(defaultTags) {
                settings.edit().putString("default_tags", defaultTags).apply()
            }

            androidx.compose.runtime.LaunchedEffect(showSettings) {
                if (!showSettings) return@LaunchedEffect
                settingsLoading = true
                settingsSaving = false
                settingsError = null
                azureKeyVisible = false
                try {
                    val key = withContext(Dispatchers.IO) {
                        runCatching {
                            EncryptedSecretStore.decrypt(settings.getString("azure_key_encrypted", null))
                        }
                    }
                    azureKey = key.getOrElse { "" }
                    if (key.isFailure) settingsError = "The saved Azure key could not be opened. Enter it again."
                    azureRegion = settings.getString("azure_region", "").orEmpty()
                    azureVoice = settings.getString("azure_voice", "").orEmpty()
                    azureRate = settings.getFloat("azure_rate", 1f).coerceIn(0.5f, 2f)
                    androidTtsVoiceName = settings.getString("android_tts_voice", "").orEmpty()
                    androidTtsRate = settings.getFloat("android_tts_rate", 1f).coerceIn(0.5f, 2f)
                    settingsDefaultTags = defaultTags
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    settingsError = "Could not load saved speech settings."
                } finally {
                    settingsLoading = false
                }
            }

            androidx.compose.runtime.LaunchedEffect(photoSourcePath, noteImagePath, processedImagePath) {
                preferences.edit()
                    .putString("photo_source_path", photoSourcePath)
                    .putString("note_image_path", noteImagePath)
                    .putString("processed_image_path", processedImagePath)
                    .apply()
            }

            androidx.compose.runtime.LaunchedEffect(
                editor.text, editor.selection, target, vocabulary, vocabularyReading, pitchAccentText, definition,
                sentenceReading, definitionManuallyEdited, dependentFieldsStale, translationDraft, translationAttemptedSentence,
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
                    .remove("vocabulary_reading")
                    .remove("pitch_accent")
                    .putString("definition", definition)
                    .putBoolean("definition_manual", definitionManuallyEdited)
                    .putBoolean("definition_manual_stale", dependentFieldsStale && definitionManuallyEdited)
                    .remove("dependent_fields_stale")
                    .remove("sentence_reading")
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
                    .remove("sentence_furigana_corrections")
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
                    autoOpenReview = false
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
                    TextButton(onClick = {
                        clearPendingSubmission(preferences)
                        recoveryRequired = false
                        recoveryPhase = null
                        recoveryMessage = "Image import retry requires a new explicit Send and may create duplicate media."
                    }) { Text("Retry image import (may duplicate media)") }
                    TextButton(onClick = {
                        clearPendingSubmission(preferences)
                        removeDraftImage()
                        recoveryRequired = false
                        recoveryPhase = null
                        recoveryMessage = "The interrupted image was discarded; the rest of the draft was kept."
                    }) { Text("Remove interrupted image") }
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

            androidx.activity.compose.BackHandler(enabled = submissionLocked) {
                when {
                    showSettings -> showSettings = false
                    showSources -> showSources = false
                    showDictionarySheet -> showDictionarySheet = false
                    showOcrSheet -> showOcrSheet = false
                    else -> Unit
                }
            }
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) {
                    darkColorScheme(
                        primary = Color(0xFFE4DAC5), onPrimary = Color(0xFF16181B),
                        secondary = Color(0xFFE4DAC5), onSecondary = Color(0xFF16181B),
                        tertiary = Color(0xFFE4DAC5), onTertiary = Color(0xFF16181B),
                        primaryContainer = Color(0xFF35383C), onPrimaryContainer = Color(0xFFECE8DF),
                        secondaryContainer = Color(0xFF35383C), onSecondaryContainer = Color(0xFFECE8DF),
                        tertiaryContainer = Color(0xFF35383C), onTertiaryContainer = Color(0xFFECE8DF),
                        background = Color(0xFF16181B), onBackground = Color(0xFFECE8DF),
                        surface = Color(0xFF1D2024), onSurface = Color(0xFFECE8DF),
                        surfaceVariant = Color(0xFF22262B), onSurfaceVariant = Color(0xFFB8B2A6),
                        outline = Color(0xFF6C7176),
                    )
                } else {
                    lightColorScheme(
                        primary = Color(0xFF201F1D), onPrimary = Color(0xFFF6F3EC),
                        secondary = Color(0xFF201F1D), onSecondary = Color(0xFFF6F3EC),
                        tertiary = Color(0xFF201F1D), onTertiary = Color(0xFFF6F3EC),
                        primaryContainer = Color(0xFFE5E1D8), onPrimaryContainer = Color(0xFF201F1D),
                        secondaryContainer = Color(0xFFE5E1D8), onSecondaryContainer = Color(0xFF201F1D),
                        tertiaryContainer = Color(0xFFE5E1D8), onTertiaryContainer = Color(0xFF201F1D),
                        background = Color(0xFFF6F3EC), onBackground = Color(0xFF201F1D),
                        surface = Color(0xFFFFFFFF), onSurface = Color(0xFF201F1D),
                        surfaceVariant = Color(0xFFFBFAF6), onSurfaceVariant = Color(0xFF6B6558),
                        outline = Color(0xFFDAD4C6),
                    )
                },
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        var submitAction: () -> Unit = {}
                        val reviewTarget = displayTarget?.takeIf { it.textIn(displaySentence) != null }
                        if (visibleRoute == AppRoute.Review && reviewTarget != null) {
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
                                translationUsesFallback = translationSource == TranslationSource.LOCAL && !translationDraft.manuallyEdited,
                                translationInfoExpanded = translationInfoExpanded,
                                generatingFields = if (activeTranslationResult() is TranslationResult.Pending) setOf("Translation") else emptySet(),
                                translationError = (activeTranslationResult() as? TranslationResult.Failed)?.message,
                                translationStale = translationDraft.manuallyEdited && translationDraft.stale,
                                translationSuggestion = (translationDraft.suggestion as? TranslationResult.Ready)?.text,
                                translationEditable = !submissionLocked && completedSubmission == null,
                                editingEnabled = !submissionLocked && completedSubmission == null,
                                onTranslationChange = { text ->
                                    translationRequestRevision++
                                    translationInfoExpanded = false
                                    translationDraft = translationDraft.saveManualEdit(text)
                                },
                                onRetryTranslation = {
                                    translationAutoAllowed = true
                                    translationRetry++
                                },
                                onKeepMyTranslation = {
                                    translationRequestRevision++
                                    translationDraft = translationDraft.keepMyText()
                                },
                                onUseTranslationSuggestion = {
                                    translationRequestRevision++
                                    translationDraft = translationDraft.useSuggestion()
                                },
                                onToggleTranslationInfo = { translationInfoExpanded = !translationInfoExpanded },
                                notes = displayNotes,
                                tags = displayTags.split(Regex("\\s+")).filter(String::isNotBlank),
                                imagePath = frozenForDisplay?.media?.path ?: noteImagePath,
                                imageStatus = when {
                                    imageProcessing -> "Preparing image…"
                                    imageError != null -> imageError
                                    imageAttached -> "Image is not available"
                                    else -> null
                                },
                                pitchHasAlternatives = pitchCandidates.size > 1,
                                pitchInfoExpanded = pitchInfoExpanded,
                                onTogglePitchInfo = { pitchInfoExpanded = !pitchInfoExpanded },
                                onEdit = { if (!submissionLocked) { autoOpenReview = false; route = AppRoute.Target } },
                                onAddTag = { if (!submissionLocked) { autoOpenReview = false; route = AppRoute.Target } },
                                onChooseImage = { if (!submissionLocked) noteImagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                onRemoveImage = { if (!submissionLocked) removeDraftImage() },
                                onDefinitionChange = { text -> if (!submissionLocked) { definition = text; definitionManuallyEdited = true; definitionProvenanceKnown = true; selectedSenseIndices = emptySet() } },
                                onNotesChange = { text -> if (!submissionLocked) notes = text },
                                tokens = japaneseTokens,
                                onSelectToken = { span -> if (!submissionLocked) selectTarget(span) },
                                definitionSenses = selectedDictionaryEntry?.senses ?: emptyList(),
                                selectedSenseIndices = selectedSenseIndices,
                                onToggleSense = { index, checked ->
                                    if (!submissionLocked) {
                                        val newSelection = if (checked) selectedSenseIndices + index else selectedSenseIndices - index
                                        selectedSenseIndices = newSelection
                                        selectedDictionaryEntry?.let { entry ->
                                            definition = newSelection.sorted().mapNotNull { index2 ->
                                                entry.senses.getOrNull(index2)?.glosses?.joinToString("; ")
                                            }.joinToString("\n")
                                        }
                                        definitionManuallyEdited = false
                                        definitionProvenanceKnown = true
                                    }
                                },
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
                                    submitMessage?.startsWith("Blocked:") == true -> ReviewSendPresentation.Status(submitMessage.orEmpty())
                                    submitMessage != null -> ReviewSendPresentation.Status(submitMessage!!, "Retry Send")
                                    else -> ReviewSendPresentation.Ready
                                },
                                onSend = if (canSend && completedSubmission == null && !duplicateBlocked &&
                                    submitMessage?.startsWith("Blocked:") != true
                                ) ({ submitAction() }) else null,
                                onSendStatusAction = if (canSend && completedSubmission == null && !duplicateBlocked) ({ submitAction() }) else null,
                                sendStatusContent = sendRecoveryContent,
                            )
                        } else {
                        Text("Japanese Anki", style = MaterialTheme.typography.headlineMedium)
                        if (!frozenRetryReady && imagePending && !omitImageForSend) {
                            Text("Image is still processing; Send will omit it if you continue.", style = MaterialTheme.typography.bodyMedium)
                        }
                        if (imageAttached && !imageProcessing && imageError == null && !imageReady) {
                            Text("The selected image is not ready to send.", style = MaterialTheme.typography.bodyMedium)
                        }
                        imageError?.let { message ->
                            Text(message, style = MaterialTheme.typography.bodyMedium)
                            TextButton(enabled = !submissionLocked, onClick = {
                                val source = noteImagePath?.let(::File)
                                if (source?.isFile == true) attachImage(source)
                                else noteImagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }) { Text("Retry image") }
                            TextButton(enabled = !submissionLocked, onClick = removeDraftImage) { Text("Remove image") }
                        }
                        if (visibleRoute == AppRoute.Capture) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (captureMode == "Photo") {
                                    Button(enabled = !submissionLocked, onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                                        Text("Photo")
                                    }
                                    OutlinedButton(enabled = !submissionLocked, onClick = {
                                        captureMode = "Type"
                                        preferences.edit().putString("capture_mode", captureMode).apply()
                                    }) { Text("Type") }
                                } else {
                                    OutlinedButton(enabled = !submissionLocked, onClick = {
                                        captureMode = "Photo"
                                        preferences.edit().putString("capture_mode", captureMode).apply()
                                    }) { Text("Photo") }
                                    Button(enabled = !submissionLocked, onClick = {
                                        captureMode = "Type"
                                        preferences.edit().putString("capture_mode", captureMode).apply()
                                    }) { Text("Type") }
                                    OutlinedButton(enabled = !submissionLocked, onClick = {
                                        noteImagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    }) { Text(if (noteImagePath == null) "Add image" else "Change image") }
                                }
                                TextButton(
                                    enabled = !submissionLocked,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        settingsLoading = true
                                        showSettings = true
                                        if (!textToSpeechReady.value && textToSpeech == null) initializeTextToSpeech()
                                    },
                                ) { Text("Settings") }
                            }
                        } else if (visibleRoute == AppRoute.Target) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(enabled = !submissionLocked, onClick = { route = AppRoute.Capture }) { Text("Back to capture") }
                                if (displayTarget?.textIn(displaySentence) != null) {
                                    TextButton(enabled = !submissionLocked, onClick = { route = AppRoute.Review }) { Text("Back to review") }
                                }
                            }
                        }
                        if (visibleRoute == AppRoute.Review) {
                            Text("Review card", style = MaterialTheme.typography.titleLarge)
                        }
                        if (dictionaryLoading) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text("Preparing offline dictionary for first use…")
                            }
                        }
                        if (dictionaryError != null) {
                            Text(dictionaryError!!, style = MaterialTheme.typography.bodyMedium)
                            TextButton(enabled = !submissionLocked, onClick = { dictionaryRetry++ }) { Text("Retry offline data setup") }
                        }
                        if (analyzerLoading) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text("Installing UniDic: ${(analyzerProgress * 100).toInt()}% (about 725 MB)…")
                            }
                        }
                        if (analyzerError != null) {
                            Text(analyzerError!!, style = MaterialTheme.typography.bodyMedium)
                            TextButton(enabled = !submissionLocked, onClick = { dictionaryRetry++ }) { Text("Retry analyzer setup") }
                        }
                        OutlinedTextField(
                            value = if (frozenForDisplay != null) {
                                TextFieldValue(displaySentence, selection = TextRange(displaySentence.length))
                            } else editor,
                            readOnly = submissionLocked,
                            onValueChange = { updated ->
                                if (updated.text != editor.text) {
                                    draftGeneration++
                                    updateTranslationSentence(updated.text)
                                    if (hasManualDefinition) dependentFieldsStale = true
                                    selectTarget(null)
                                    japaneseTokens = emptyList()
                                    tokenizationError = null
                                    tokenizationLoading = analyzerReady
                                }
                                editor = updated
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Japanese sentence") },
                            minLines = 3,
                        )
                        ocrError?.let { message ->
                            Text(message, style = MaterialTheme.typography.bodyMedium)
                            TextButton(enabled = !submissionLocked, onClick = {
                                val source = photoSourcePath?.let(::File)?.takeIf(File::isFile)
                                if (source != null) processImage(source, true)
                                else photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }) { Text("Retry photo text reading") }
                            TextButton(enabled = !submissionLocked, onClick = {
                                ocrError = null
                                route = AppRoute.Target
                            }) { Text("Edit sentence") }
                        }
                        if (analyzerReady) {
                            Text("Analyzed tokens — tap one to select it as the target")
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                japaneseTokens.forEach { token ->
                                    val selected = target == token.span
                                    val onSelect = {
                                        selectTarget(token.span)
                                        editor = editor.copy(selection = TextRange(token.span.start, token.span.end))
                                    }
                                    if (selected) {
                                        Button(enabled = !submissionLocked, onClick = onSelect) { Text(token.surface) }
                                    } else {
                                        OutlinedButton(enabled = !submissionLocked, onClick = onSelect) { Text(token.surface) }
                                    }
                                }
                            }
                            if (tokenizationLoading) Text("Analyzing sentence…")
                            if (tokenizationError != null) {
                                Text(tokenizationError!!, style = MaterialTheme.typography.bodyMedium)
                                TextButton(enabled = !submissionLocked, onClick = { analysisRetry++ }) { Text("Retry sentence analysis") }
                            }
                        }
                        OutlinedButton(
                            enabled = !submissionLocked && editor.selection.start != editor.selection.end,
                            onClick = {
                                selectTarget(
                                    TargetSpan.fromSelection(
                                        editor.text,
                                        editor.selection.start,
                                        editor.selection.end,
                                    ),
                                )
                            },
                        ) { Text("Use selected text as target") }
                        val targetText = displayTarget?.textIn(displaySentence)
                        val lemmaLabel = displayVocabulary.takeIf(String::isNotBlank)?.let { " → $it" }.orEmpty()
                        Text(
                            targetText?.let { "Target: $it$lemmaLabel" }
                                ?: "Select text in the sentence to choose a target.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        if (dependentFieldsStale && target?.textIn(editor.text) == null) {
                            Text(
                                "Previous vocabulary and reading work was kept. Select a new target to review it.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        if (displayTarget?.textIn(displaySentence) != null) {
                            Text("Offline dictionary match", style = MaterialTheme.typography.titleMedium)
                            if (frozenForDisplay != null) {
                                Text("$displayVocabulary · $displayVocabularyReading", style = MaterialTheme.typography.titleSmall)
                            }
                            if (frozenForDisplay == null && dictionaryEntries.size > 1 && selectedDictionaryEntry == null) {
                                OutlinedButton(enabled = !submissionLocked, onClick = { showDictionarySheet = true }) {
                                    Text("Choose dictionary entry")
                                }
                            }
                            if (frozenForDisplay == null) {
                                analyzedTarget?.let { Text("Also searching analyzed lemma: ${it.expression}") }
                                when {
                                    !dictionaryReady && dictionaryLoading -> Text("Dictionary is being prepared…")
                                    !dictionaryReady -> Text("Dictionary unavailable; an entry is required before Send.")
                                    dictionarySearchLoading -> Text("Searching JMdict…")
                                    dictionaryEntries.isEmpty() -> Text("No dictionary match for this selection. Edit the sentence or reselect a target.")
                                }
                                selectedDictionaryEntry?.takeIf { dictionaryEntries.size == 1 }?.let { entry ->
                                    Text("${entry.expression} · ${entry.reading}", style = MaterialTheme.typography.titleSmall)
                                    Text(entry.senses.firstOrNull()?.glosses?.joinToString("; ").orEmpty())
                                }
                            }
                            if (frozenForDisplay == null && dictionaryHasMore && !dictionarySearchLoading) {
                                TextButton(enabled = !submissionLocked, onClick = {
                                    val query = target?.textIn(editor.text) ?: return@TextButton
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
                                }) { Text("Load more matches") }
                            }
                            selectedDictionaryEntry?.let { entry ->
                                Text("Select the applicable JMdict senses:", style = MaterialTheme.typography.titleSmall)
                                entry.senses.forEachIndexed { index, sense ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            enabled = !submissionLocked,
                                            checked = index in selectedSenseIndices,
                                            onCheckedChange = { checked ->
                                                val newSelection = if (checked) {
                                                    selectedSenseIndices + index
                                                } else {
                                                    selectedSenseIndices - index
                                                }
                                                selectedSenseIndices = newSelection
                                                definition = newSelection.sorted().mapNotNull { selected ->
                                                    entry.senses.getOrNull(selected)?.glosses?.joinToString("; ")
                                                }.joinToString("\n")
                                                definitionManuallyEdited = false
                                                definitionProvenanceKnown = true
                                            },
                                        )
                                        Column {
                                            Text(sense.glosses.joinToString("; "))
                                            if (sense.partsOfSpeech.isNotEmpty()) {
                                                Text(sense.partsOfSpeech.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }
                                }
                            }
                            if (displayPitch != null) PitchPatternDiagram(displayPitch, PitchDiagramScale.Compact)
                            OutlinedTextField(
                                value = displayDefinition,
                                readOnly = submissionLocked,
                                onValueChange = {
                                    definition = it
                                    definitionManuallyEdited = true
                                    definitionProvenanceKnown = true
                                    dependentFieldsStale = false
                                    selectedSenseIndices = emptySet()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("English definition (required)") },
                                minLines = 2,
                            )
                            OutlinedTextField(
                                value = displayTranslation,
                                readOnly = submissionLocked,
                                onValueChange = {
                                    translationRequestRevision++
                                    translationInfoExpanded = false
                                    translationDraft = translationDraft.saveManualEdit(it)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Sentence translation (optional)") },
                                minLines = 2,
                            )
                            OutlinedTextField(
                                value = displayNotes,
                                readOnly = submissionLocked,
                                onValueChange = { notes = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Notes (optional)") },
                                minLines = 2,
                            )
                            OutlinedTextField(
                                value = displayTags,
                                readOnly = submissionLocked,
                                onValueChange = { tags = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Tags (optional)") },
                                singleLine = true,
                            )
                        }
                        if (target != null) {
                            if (dependentFieldsStale) {
                                Text(
                                    "Sentence, target, or entry changed. Your manual definition was kept; review it before using this note.",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                OutlinedButton(enabled = !submissionLocked, onClick = { dependentFieldsStale = false }) {
                                    Text("I've reviewed these fields")
                                }
                            }
                            if (validationErrors.isEmpty()) {
                                Text("Required note text is complete.", style = MaterialTheme.typography.bodyMedium)
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Still needed", style = MaterialTheme.typography.titleSmall)
                                    validationErrors.forEach { issue ->
                                        Text("• $issue", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                        }
                        if (visibleRoute != AppRoute.Review) when {
                            !ankiDroidInstalled -> Text(
                                "AnkiDroid not detected. Install it before setting up export.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            apiPermissionGranted -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("AnkiDroid API permission granted.", style = MaterialTheme.typography.bodyMedium)
                                if (setupLoading) Text("Loading note types and decks…")
                                setupError?.let {
                                    Text(it)
                                    TextButton(onClick = { setupReload++ }) { Text("Retry") }
                                }
                                Text("Note type", style = MaterialTheme.typography.titleMedium)
                                Button(
                                    onClick = { showModelMenu = true },
                                    enabled = !submissionLocked && noteTypes.isNotEmpty(),
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
                                            onClick = { if (!submissionLocked) { selectedModelId = model.id; showModelMenu = false } },
                                        )
                                    }
                                }
                                selectedModel?.let { model ->
                                    AnkiFieldContract.mapToModelOrder(model.fields, emptyMap()).errors
                                        .forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                                }
                                Text("Destination deck", style = MaterialTheme.typography.titleMedium)
                                Button(
                                    onClick = { showDeckMenu = true },
                                    enabled = !submissionLocked && decks.isNotEmpty(),
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
                                            onClick = { if (!submissionLocked) { selectedDeckId = deck.id; showDeckMenu = false } },
                                        )
                                    }
                                }
                                if (!setupLoading && setupError == null && noteTypes.isEmpty()) {
                                    Text("No note types available in AnkiDroid.")
                                }
                                if (!setupLoading && setupError == null && decks.isEmpty()) {
                                    Text("No existing decks available in AnkiDroid.")
                                }
                            }
                            else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("AnkiDroid detected. Grant API permission to continue setup.")
                                Button(enabled = !isSubmitting, onClick = {
                                    apiPermissionLauncher.launch(ANKIDROID_READ_WRITE_PERMISSION)
                                }) { Text("Grant AnkiDroid API permission") }
                            }
                        }
                        if (visibleRoute != AppRoute.Review || reviewTarget == null) {
                            submitMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                            recoveryMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                            sendRecoveryContent()
                        }
                        if (visibleRoute != AppRoute.Review) Text(
                            "Draft saved on this device.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
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
                            analyzerProgress = 0f
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
                                            val frozen = if (restoredFrozen != null) {
                                                check(restoredFrozen.importedFilename == null ||
                                                    (restoredFrozen.media != null &&
                                                        preferences.getString(IMAGE_RECEIPT_PATH, null) == restoredFrozen.media.path &&
                                                        preferences.getLong(IMAGE_RECEIPT_SIZE, -1L) == restoredFrozen.media.size &&
                                                        preferences.getLong(IMAGE_RECEIPT_MODIFIED, -1L) == restoredFrozen.media.modified &&
                                                        preferences.getString(IMAGE_RECEIPT_FILENAME, null) == restoredFrozen.importedFilename &&
                                                        (!File(restoredFrozen.media.path).exists() ||
                                                            MediaIdentity.fromFile(File(restoredFrozen.media.path)) == restoredFrozen.media))
                                                ) { "The frozen media receipt is unavailable; resolve the pending Send manually." }
                                                restoredFrozen
                                            } else {
                                                val includeImage = imageAttached && !omitImageForSend && !imageProcessing
                                                val receiptPath = preferences.getString(IMAGE_RECEIPT_PATH, null)
                                                val receiptFilename = preferences.getString(IMAGE_RECEIPT_FILENAME, null)
                                                    ?.let(AnkiMediaImporter::sanitizeFilename)
                                                val processed = if (includeImage) {
                                                    processedImagePath?.let(::File)?.takeIf(File::isFile)
                                                } else null
                                                if (includeImage) check(processed != null) {
                                                    "The selected image is not ready for import."
                                                }
                                                val media = processed?.let(MediaIdentity::fromFile)
                                                val imported = if (includeImage && media != null &&
                                                    receiptPath == media.path &&
                                                    preferences.getLong(IMAGE_RECEIPT_SIZE, -1L) == media.size &&
                                                    preferences.getLong(IMAGE_RECEIPT_MODIFIED, -1L) == media.modified &&
                                                    receiptFilename != null
                                                ) receiptFilename else null
                                                val baseDraft = noteDraft.copy(imageFilename = imported)
                                                val baseFields = baseDraft.fieldsByName()
                                                    ?: error("The note changed before Send could be frozen.")
                                                val baseMapping = AnkiFieldContract.mapToModelOrder(model.fields, baseFields)
                                                check(baseMapping.isValid) { baseMapping.errors.joinToString() }
                                                var snapshot = FrozenSubmission(
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
                                                    media = media,
                                                    importedFilename = imported,
                                                    noteId = null,
                                                )
                                                check(preferences.edit().putLong(
                                                    SubmissionRecoveryKeys.REVISION_SEQUENCE,
                                                    snapshot.revision,
                                                ).commit()) { "Could not save submission revision." }
                                                if (includeImage && media != null && imported == null) {
                                                    check(preferences.edit().putFrozenSubmission(snapshot, SubmissionPhase.MEDIA_IMPORTING)
                                                        .remove(IMAGE_RECEIPT_PATH)
                                                        .remove(IMAGE_RECEIPT_FILENAME)
                                                        .remove(IMAGE_RECEIPT_SIZE)
                                                        .remove(IMAGE_RECEIPT_MODIFIED)
                                                        .commit()) { "Could not save media import recovery state." }
                                                    publishFrozen(snapshot)
                                                    val actualFilename = gateway.importProcessedImage(
                                                        processed ?: error("The selected image is not ready for import."),
                                                    )
                                                    check(AnkiMediaImporter.sanitizeFilename(actualFilename) != null) {
                                                        "AnkiDroid returned an unsafe image filename."
                                                    }
                                                    val importedFields = noteDraft.copy(imageFilename = actualFilename).fieldsByName()
                                                        ?: error("The frozen note fields are invalid after image import.")
                                                    val importedMapping = AnkiFieldContract.mapToModelOrder(model.fields, importedFields)
                                                    check(importedMapping.isValid) { importedMapping.errors.joinToString() }
                                                    snapshot = snapshot.copy(
                                                        orderedFields = importedMapping.values,
                                                        importedFilename = actualFilename,
                                                    )
                                                    check(preferences.edit().putFrozenSubmission(snapshot, SubmissionPhase.MEDIA_IMPORTED)
                                                        .putString(IMAGE_RECEIPT_PATH, media.path)
                                                        .putString(IMAGE_RECEIPT_FILENAME, actualFilename)
                                                        .putLong(IMAGE_RECEIPT_SIZE, media.size)
                                                        .putLong(IMAGE_RECEIPT_MODIFIED, media.modified)
                                                        .commit()) { "Could not save the imported media receipt." }
                                                    publishFrozen(snapshot)
                                                } else {
                                                    check(preferences.edit().putFrozenSubmission(
                                                        snapshot,
                                                        if (imported == null) SubmissionPhase.NOTE_INSERTING else SubmissionPhase.MEDIA_IMPORTED,
                                                    ).apply {
                                                        if (imported != null && media != null) {
                                                            putString(IMAGE_RECEIPT_PATH, media.path)
                                                                .putString(IMAGE_RECEIPT_FILENAME, imported)
                                                                .putLong(IMAGE_RECEIPT_SIZE, media.size)
                                                                .putLong(IMAGE_RECEIPT_MODIFIED, media.modified)
                                                        }
                                                    }.commit()) { "Could not save submission recovery data." }
                                                    publishFrozen(snapshot)
                                                }
                                                snapshot
                                            }
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
                                            frozenRetryReady = false
                                            recoveryPhase = null
                                            duplicateBlocked = true
                                            knownNoteId = outcome.noteId
                                            if (restoredFrozen != null) clearPendingSubmission(preferences)
                                            submitMessage = "Blocked: a note with this sentence and vocabulary lemma already exists."
                                        }
                                        "created" -> {
                                            completedSubmission = publishedFrozenSnapshot ?: readFrozenSubmission(preferences)
                                            frozenRetryReady = false
                                            recoveryRequired = false
                                            recoveryPhase = null
                                            recoveryDuplicate = false
                                            knownNoteId = null
                                            submitMessage = "Note sent to AnkiDroid."
                                        }
                                        else -> {
                                            frozenRetryReady = false
                                            recoveryPhase = null
                                            submitMessage = "AnkiDroid did not add the note. You can safely review and retry."
                                        }
                                    }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: AnkiProviderDefiniteInsertException) {
                                    clearPendingSubmission(preferences)
                                    publishedFrozenSnapshot = null
                                    recoveryRequired = false
                                    recoveryPhase = null
                                    frozenRetryReady = false
                                    knownNoteId = null
                                    submitMessage = error.message.orEmpty() + " Retry Send to rerun duplicate checking before any new insert."
                                } catch (_: Exception) {
                                    val phase = SubmissionPhase.fromPersisted(
                                        preferences.getString(SubmissionRecoveryKeys.STATUS, null),
                                    )
                                    if (phase != null) {
                                        recoveryPhase = phase
                                        recoveryRequired = true
                                        frozenRetryReady = false
                                        recoveryMessage = when (phase) {
                                            SubmissionPhase.MEDIA_IMPORTING -> "Image import may have completed, but its receipt was not saved. Do not retry without explicit acknowledgement."
                                            SubmissionPhase.MEDIA_IMPORTED -> "Image import was recorded, but note insertion did not start. Resume only after reviewing the frozen draft."
                                            SubmissionPhase.NOTE_INSERTING -> "Could not confirm note insertion. Checking the frozen Send revision…"
                                            SubmissionPhase.NOTE_INSERTED -> "Note ID was saved, but deck assignment was interrupted. Recovery will move only that note's cards."
                                            SubmissionPhase.DESTINATION_VERIFIED -> "The note's destination was verified; recovery will finalize it without inserting again."
                                        }
                                    } else if (imageAttached) {
                                        submitMessage = "Could not import the selected image. The draft was kept; retry or remove the image."
                                    } else {
                                        submitMessage = "Could not send the note. Check AnkiDroid API access and retry."
                                    }
                                } finally {
                                    isSubmitting = false
                                }
                            }
                        }
                        if (visibleRoute != AppRoute.Review || reviewTarget == null) Button(
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
                                        !frozenRetryReady && imagePending && !omitImageForSend -> "Send without image"
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
                    ModalBottomSheet(onDismissRequest = { showDictionarySheet = false }) {
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

internal fun initialDraftTags(draftTags: String?, lastNoteTags: String?, defaultTags: String?): String =
    draftTags ?: lastNoteTags ?: defaultTags.orEmpty()

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
        "leave_sentence_target_unannotated", "leave_vocabulary_unannotated", "dependent_fields_stale",
        "definition_manual", "definition_manual_stale", "translation", "translation_sentence", "translation_source",
        "translation_manual", "translation_stale", "translation_pending", "translation_error",
        "translation_suggestion", "translation_suggestion_source", "translation_suggestion_error",
        "translation_attempted_sentence", "notes", "tags", "submission_status", "pending_model_id", "pending_deck_id",
        "pending_sentence", "pending_lemma", "dictionary_entry_id", "dictionary_sense_indices",
        "sentence_furigana_corrections", "photo_source_path", "note_image_path", "processed_image_path",
        IMAGE_RECEIPT_PATH, IMAGE_RECEIPT_FILENAME, IMAGE_RECEIPT_SIZE, IMAGE_RECEIPT_MODIFIED,
        "pending_replacement_image_path",
        SubmissionRecoveryKeys.REVISION, SubmissionRecoveryKeys.MODEL_FIELDS,
        SubmissionRecoveryKeys.ORDERED_FIELDS, SubmissionRecoveryKeys.VOCABULARY_READING,
        SubmissionRecoveryKeys.SENTENCE_READING, SubmissionRecoveryKeys.DEFINITION,
        SubmissionRecoveryKeys.TRANSLATION, SubmissionRecoveryKeys.NOTES, SubmissionRecoveryKeys.TAGS,
        SubmissionRecoveryKeys.PITCH, SubmissionRecoveryKeys.TARGET_START, SubmissionRecoveryKeys.TARGET_END,
        SubmissionRecoveryKeys.MEDIA_PATH, SubmissionRecoveryKeys.MEDIA_SIZE,
        SubmissionRecoveryKeys.MEDIA_MODIFIED, SubmissionRecoveryKeys.IMPORTED_FILENAME,
        SubmissionRecoveryKeys.NOTE_ID,
    )
    preferences.edit().also { editor -> draftKeys.forEach(editor::remove) }.commit()
}

private fun clearPendingSubmission(preferences: SharedPreferences) {
    clearFrozenSubmission(preferences)
}

private fun TextRange.coerceIn(min: Int, max: Int): TextRange =
    TextRange(start.coerceIn(min, max), end.coerceIn(min, max))
