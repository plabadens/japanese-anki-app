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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
        if (preferences.getString("submission_status", null) == "done") {
            clearPersistedDraft(preferences)
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
            var translation by remember { mutableStateOf(preferences.getString("translation", "").orEmpty()) }
            var notes by remember { mutableStateOf(preferences.getString("notes", "").orEmpty()) }
            var defaultTags by remember { mutableStateOf(settings.getString("default_tags", "").orEmpty()) }
            var tags by remember { mutableStateOf(preferences.getString("tags", defaultTags).orEmpty()) }
            var showReplace by remember { mutableStateOf(false) }
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
            var imageError by remember { mutableStateOf<String?>(null) }
            var ocrLoading by remember { mutableStateOf(false) }
            var ocrError by remember { mutableStateOf<String?>(null) }
            var ocrChoices by remember { mutableStateOf<List<OcrBlock>>(emptyList()) }
            var showOcrSheet by remember { mutableStateOf(false) }
            var pendingPhotoFile by remember { mutableStateOf<File?>(null) }
            var pendingSharedImageUri by remember { mutableStateOf<Uri?>(null) }
            var cameraPermissionGranted by remember {
                mutableStateOf(checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
            }
            var showModelMenu by remember { mutableStateOf(false) }
            var showDeckMenu by remember { mutableStateOf(false) }
            var isSubmitting by remember { mutableStateOf(false) }
            var submitMessage by remember { mutableStateOf<String?>(null) }
            var recoveryRequired by remember {
                mutableStateOf(preferences.getString("submission_status", null) == "started")
            }
            var recoveryMessage by remember {
                mutableStateOf(
                    if (preferences.getString("submission_status", null) == "started") {
                        "A previous Send was interrupted. Verify its outcome before retrying."
                    } else null,
                )
            }
            var recoveryDuplicate by remember { mutableStateOf(false) }
            val selectedModel = noteTypes.firstOrNull { it.id == selectedModelId }
            val selectedDeck = decks.firstOrNull { it.id == selectedDeckId }
            val incoming = sharedText.value
            val hasManualDefinition = definitionManuallyEdited && definition.isNotBlank()
            val clearDraft = {
                listOfNotNull(photoSourcePath, noteImagePath, processedImagePath).distinct().forEach { File(it).delete() }
                editor = TextFieldValue("")
                route = AppRoute.Capture
                photoSourcePath = null
                noteImagePath = null
                processedImagePath = null
                imageProcessing = false
                imageError = null
                ocrLoading = false
                ocrError = null
                ocrChoices = emptyList()
                showOcrSheet = false
                japaneseTokens = emptyList()
                tokenizationLoading = false
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
                translation = ""
                notes = ""
                tags = settings.getString("last_note_tags", defaultTags).orEmpty()
                selectedDictionaryEntry = null
                selectedDictionaryEntryId = null
                selectedSenseIndices = emptySet()
            }
            val selectTarget: (TargetSpan?) -> Unit = { selected ->
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
            val applyOcrText: (String) -> Unit = { text ->
                selectTarget(null)
                editor = TextFieldValue(text.trim(), selection = TextRange(text.trim().length))
                japaneseTokens = emptyList()
                tokenizationError = null
                tokenizationLoading = analyzerReady
                ocrError = null
                route = AppRoute.Target
            }
            val processImage: (File, Boolean) -> Unit = { file, runOcr ->
                val previousFiles = listOfNotNull(noteImagePath, processedImagePath)
                    .distinct().filter { it != file.absolutePath }
                if (!runOcr && photoSourcePath == noteImagePath) photoSourcePath = null
                if (runOcr) photoSourcePath = file.absolutePath
                noteImagePath = file.absolutePath
                processedImagePath = null
                check(
                    preferences.edit()
                        .putString("photo_source_path", photoSourcePath)
                        .putString("note_image_path", noteImagePath)
                        .remove("processed_image_path")
                        .commit(),
                ) { "Could not save the draft image source." }
                imageError = null
                imageProcessing = true
                val destination = File(filesDir, "draft/${java.util.UUID.randomUUID()}.avif")
                coroutineScope.launch {
                    try {
                        val processed = ImageMediaProcessor.process(file, destination)
                        if (noteImagePath == file.absolutePath) {
                            processedImagePath = processed.file.absolutePath
                            previousFiles.forEach { File(it).delete() }
                        } else {
                            processed.file.delete()
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        if (noteImagePath == file.absolutePath) {
                            imageError = "This photo could not be prepared for Anki. Retry or remove it."
                        }
                    } finally {
                        if (noteImagePath == file.absolutePath) imageProcessing = false
                    }
                }
                if (runOcr) {
                    ocrLoading = true
                    ocrError = null
                    coroutineScope.launch {
                        try {
                            val result = japaneseOcr.recognize(file)
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
                            ocrError = "Could not read this photo. Type or edit the sentence to continue."
                            route = AppRoute.Target
                        } finally {
                            ocrLoading = false
                        }
                    }
                }
            }
            val requestPhoto: (File) -> Unit = { file ->
                if (editor.text.isNotBlank()) {
                    pendingPhotoFile = file
                    pendingSharedImageUri = null
                    showReplace = true
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
                    try {
                        requestPhoto(copyImageToDraft(uri))
                    } catch (_: Exception) {
                        ocrError = "Could not open that photo. Choose another image or type the sentence."
                    }
                }
            }
            val noteImagePicker = rememberLauncherForActivityResult(
                ActivityResultContracts.PickVisualMedia(),
            ) { uri ->
                if (uri != null) coroutineScope.launch {
                    try {
                        attachImage(copyImageToDraft(uri))
                    } catch (_: Exception) {
                        imageError = "Could not open that photo. Choose another image."
                    }
                }
            }

            androidx.compose.runtime.LaunchedEffect(Unit) {
                val source = noteImagePath?.let(::File)?.takeIf(File::isFile)
                if (source != null && processedImagePath?.let(::File)?.isFile != true) {
                    processImage(source, runOcr = photoSourcePath == source.absolutePath && editor.text.isBlank())
                } else if (photoSourcePath?.let(::File)?.isFile == true && editor.text.isBlank()) {
                    processImage(File(photoSourcePath!!), runOcr = true)
                }
            }

            androidx.compose.runtime.LaunchedEffect(dictionaryRetry) {
                dictionaryLoading = true
                dictionaryError = null
                try {
                    dictionaryInfo = withContext(Dispatchers.IO) { offlineDictionary.prepare() }
                    dictionaryReady = true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.e("OfflineDictionary", "Could not prepare the bundled dictionary", error)
                    dictionaryError = "Offline dictionary could not be prepared. A dictionary entry is required before Send."
                } finally {
                    dictionaryLoading = false
                }
            }

            androidx.compose.runtime.LaunchedEffect(dictionaryRetry) {
                analyzerLoading = true
                analyzerError = null
                analyzerProgress = 0f
                try {
                    withContext(Dispatchers.IO) { offlineAnalyzer.prepare { analyzerProgress = it } }
                    analyzerReady = true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: LinkageError) {
                    Log.e("OfflineAnalyzer", "Could not load MeCab native analysis", error)
                    analyzerError = "MeCab native analysis could not load. You can still select a sentence span and search the dictionary."
                } catch (error: Exception) {
                    Log.e("OfflineAnalyzer", "Could not prepare UniDic analysis", error)
                    analyzerError = "UniDic could not be installed. Check available storage and retry."
                } finally {
                    analyzerLoading = false
                }
            }

            androidx.compose.runtime.LaunchedEffect(analyzerReady, editor.text, analysisRetry) {
                if (!analyzerReady || editor.text.isBlank()) {
                    japaneseTokens = emptyList()
                    tokenizationLoading = false
                    return@LaunchedEffect
                }
                tokenizationLoading = true
                tokenizationError = null
                try {
                    kotlinx.coroutines.delay(150)
                    japaneseTokens = withContext(Dispatchers.IO) { offlineAnalyzer.analyze(editor.text) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    japaneseTokens = emptyList()
                    tokenizationError = "Could not analyze this sentence. Select a target span to search the dictionary."
                } finally {
                    tokenizationLoading = false
                }
            }

            androidx.compose.runtime.LaunchedEffect(
                target, editor.text, japaneseTokens, vocabulary, vocabularyReading,
                dependentFieldsStale,
            ) {
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
                vocabulary, vocabularyReading, dictionarySearchLoading,
            ) {
                val entry = selectedDictionaryEntry
                if (route == AppRoute.Target && autoOpenReview && target?.textIn(editor.text) != null &&
                    entry != null && entry.id == selectedDictionaryEntryId && entry.expression == vocabulary &&
                    entry.reading == vocabularyReading && !dictionarySearchLoading
                ) {
                    route = AppRoute.Review
                    autoOpenReview = false
                }
            }

            androidx.compose.runtime.LaunchedEffect(dictionaryReady, target, editor.text, dictionaryRetry, japaneseTokens) {
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
                        dictionaryError = "Dictionary lookup failed. Retry before selecting a dictionary entry."
                    } finally {
                        dictionarySearchLoading = false
                    }
                }
            }

            androidx.compose.runtime.LaunchedEffect(
                dictionaryReady, selectedDictionaryEntryId, selectedDictionaryEntry, vocabulary, vocabularyReading,
                target, editor.text, dictionaryRetry,
            ) {
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
                    pitchCandidates = candidates
                    if (candidates.isNotEmpty()) {
                        pitchAccentText = candidates.first().accent.toString()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    pitchCandidates = emptyList()
                } finally {
                    pitchLookupLoading = false
                }
            }

            androidx.compose.runtime.LaunchedEffect(incoming) {
                if (incoming != null) {
                    if (editor.text.isBlank()) {
                        captureMode = "Type"
                        route = AppRoute.Capture
                        editor = TextFieldValue(incoming, selection = TextRange(incoming.length))
                        selectTarget(null)
                        japaneseTokens = emptyList()
                        sharedText.value = null
                    } else {
                        showReplace = true
                    }
                }
            }
            androidx.compose.runtime.LaunchedEffect(sharedImage.value) {
                val uri = sharedImage.value ?: return@LaunchedEffect
                captureMode = "Photo"
                try {
                    val source = withContext(Dispatchers.IO) { copyImageToDraft(uri) }
                    if (sharedImage.value != uri) {
                        source.delete()
                        return@LaunchedEffect
                    }
                    clearConsumedImageIntent(uri)
                    sharedImage.value = null
                    if (editor.text.isNotBlank()) {
                        pendingPhotoFile = source
                        pendingSharedImageUri = null
                        showReplace = true
                    } else {
                        requestPhoto(source)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (sharedImage.value == uri) sharedImage.value = null
                    ocrError = "Could not open that photo. Choose another image or type the sentence."
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
            if (isSubmitting) {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text("Sending to AnkiDroid") },
                    text = { Text("The note snapshot is frozen until AnkiDroid confirms the result.") },
                    confirmButton = {},
                )
            }
            if (showReplace && (incoming != null || pendingPhotoFile != null || pendingSharedImageUri != null)) {
                val replacingSentence = incoming != null
                AlertDialog(
                    onDismissRequest = {
                        showReplace = false
                        sharedText.value = null
                        pendingPhotoFile?.delete()
                        pendingPhotoFile = null
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
                                    clearDraft()
                                    captureMode = "Type"
                                    editor = TextFieldValue(incoming, selection = TextRange(incoming.length))
                                    route = AppRoute.Capture
                                    sharedText.value = null
                                }
                                pendingPhotoFile != null -> {
                                    val file = pendingPhotoFile!!
                                    pendingPhotoFile = null
                                    clearDraft()
                                    captureMode = "Photo"
                                    processImage(file, true)
                                }
                                pendingSharedImageUri != null -> {
                                    val uri = pendingSharedImageUri!!
                                    pendingSharedImageUri = null
                                    clearDraft()
                                    captureMode = "Photo"
                                    coroutineScope.launch {
                                        try {
                                            requestPhoto(copyImageToDraft(uri))
                                        } catch (_: Exception) {
                                            ocrError = "Could not open that photo. Choose another image or type the sentence."
                                        }
                                    }
                                }
                            }
                            showReplace = false
                        }) { Text("Replace") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showReplace = false
                            sharedText.value = null
                            pendingPhotoFile?.delete()
                            pendingPhotoFile = null
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
                notes = notes,
                sentenceFurigana = sentenceFurigana,
            )
            val validationErrors = noteDraft.validationErrors()
            val fieldsByName = noteDraft.fieldsByName()
            val modelMapping = selectedModel?.let {
                AnkiFieldContract.mapToModelOrder(it.fields, fieldsByName.orEmpty())
            }
            val canSend = apiPermissionGranted && selectedModel != null && selectedDeck != null &&
                fieldsByName != null && modelMapping?.isValid == true && validationErrors.isEmpty() &&
                !setupLoading && setupError == null && !recoveryRequired && !isSubmitting

            androidx.compose.runtime.LaunchedEffect(
                recoveryRequired, apiPermissionGranted, noteTypes, decks, setupReload,
            ) {
                if (!recoveryRequired || !apiPermissionGranted) return@LaunchedEffect
                val pendingModelId = preferences.getLong("pending_model_id", -1)
                val model = noteTypes.firstOrNull { it.id == pendingModelId }
                val sentence = preferences.getString("pending_sentence", null)
                val lemma = preferences.getString("pending_lemma", null)
                val values = if (sentence != null && lemma != null) {
                    mapOf("SentKanji" to sentence, "VocabKanji" to lemma)
                } else null
                if (model == null || values == null) {
                    recoveryMessage = "Previous Send was interrupted. Restore API access and the selected note type/deck to check before retrying."
                    return@LaunchedEffect
                }
                recoveryMessage = "Checking AnkiDroid for the pending note…"
                try {
                    val duplicate = withContext(Dispatchers.IO) {
                        AnkiDroidGateway(contentResolver).hasMatchingNoteInAnyDeck(model, values)
                    }
                    if (duplicate) {
                        recoveryDuplicate = true
                        recoveryMessage = "A matching note exists in AnkiDroid. The interrupted Send may have succeeded, possibly in another deck; do not retry without checking."
                    } else {
                        clearPendingSubmission(preferences)
                        recoveryRequired = false
                        recoveryDuplicate = false
                        recoveryMessage = "No matching note was found; it is safe to retry Send."
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    recoveryMessage = "Could not verify the interrupted Send. Retry the check or inspect AnkiDroid before resolving it."
                }
            }

            androidx.compose.runtime.LaunchedEffect(ankiDroidInstalled, apiPermissionGranted, setupReload) {
                if (!ankiDroidInstalled || !apiPermissionGranted) {
                    noteTypes = emptyList()
                    decks = emptyList()
                    setupError = null
                    setupLoading = false
                } else {
                    setupLoading = true
                    setupError = null
                    try {
                        val data = withContext(Dispatchers.IO) {
                            val gateway = AnkiDroidGateway(contentResolver)
                            gateway.noteTypes() to gateway.decks()
                        }
                        noteTypes = data.first
                        decks = data.second
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        setupError = "Could not read note types and decks. Check AnkiDroid API access and retry."
                    } finally {
                        setupLoading = false
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
                sentenceReading, definitionManuallyEdited, dependentFieldsStale, translation, notes, tags, selectedModelId, selectedDeckId,
                selectedDictionaryEntryId, selectedSenseIndices,
            ) {
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
                    .putString("notes", notes)
                    .putString("tags", tags)
                    .putLong("model_id", selectedModelId ?: -1)
                    .putLong("deck_id", selectedDeckId ?: -1)
                    .putLong("dictionary_entry_id", selectedDictionaryEntryId ?: -1)
                    .putStringSet("dictionary_sense_indices", selectedSenseIndices.mapTo(mutableSetOf()) { it.toString() })
                    .remove("sentence_furigana_corrections")
                    .apply()
            }

            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) {
                    darkColorScheme(
                        primary = Color(0xFFD0C9B8), onPrimary = Color(0xFF25231F),
                        secondary = Color(0xFFB8B1A3), onSecondary = Color(0xFF25231F),
                        tertiary = Color(0xFFB8B1A3), onTertiary = Color(0xFF25231F),
                        primaryContainer = Color(0xFF49463F), onPrimaryContainer = Color(0xFFF4F1E8),
                        secondaryContainer = Color(0xFF393731), onSecondaryContainer = Color(0xFFE9E5DC),
                        tertiaryContainer = Color(0xFF393731), onTertiaryContainer = Color(0xFFE9E5DC),
                        background = Color(0xFF1D1C19), onBackground = Color(0xFFE9E5DC),
                        surface = Color(0xFF25231F), onSurface = Color(0xFFE9E5DC),
                        surfaceVariant = Color(0xFF393731), onSurfaceVariant = Color(0xFFD0CABE),
                        outline = Color(0xFF888276),
                    )
                } else {
                    lightColorScheme(
                        primary = Color(0xFF49463F), onPrimary = Color(0xFFFFFFFF),
                        secondary = Color(0xFF625E55), onSecondary = Color(0xFFFFFFFF),
                        tertiary = Color(0xFF625E55), onTertiary = Color(0xFFFFFFFF),
                        primaryContainer = Color(0xFFE1DDD2), onPrimaryContainer = Color(0xFF25231F),
                        secondaryContainer = Color(0xFFE9E5DC), onSecondaryContainer = Color(0xFF25231F),
                        tertiaryContainer = Color(0xFFE9E5DC), onTertiaryContainer = Color(0xFF25231F),
                        background = Color(0xFFF4F1E8), onBackground = Color(0xFF25231F),
                        surface = Color(0xFFFAF8F2), onSurface = Color(0xFF25231F),
                        surfaceVariant = Color(0xFFE9E5DC), onSurfaceVariant = Color(0xFF49463F),
                        outline = Color(0xFF777166),
                    )
                },
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("Japanese Anki", style = MaterialTheme.typography.headlineMedium)
                        imageError?.let { message ->
                            Text(message, style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = {
                                val source = noteImagePath?.let(::File)
                                if (source?.isFile == true) attachImage(source)
                                else noteImagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }) { Text("Retry image") }
                            TextButton(onClick = {
                                listOfNotNull(noteImagePath, processedImagePath)
                                    .distinct().forEach { File(it).delete() }
                                if (photoSourcePath == noteImagePath) photoSourcePath = null
                                noteImagePath = null
                                processedImagePath = null
                                imageProcessing = false
                                imageError = null
                            }) { Text("Remove image") }
                        }
                        if (route == AppRoute.Capture) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (captureMode == "Photo") {
                                    Button(onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                                        Text("Photo")
                                    }
                                    OutlinedButton(onClick = {
                                        captureMode = "Type"
                                        preferences.edit().putString("capture_mode", captureMode).apply()
                                    }) { Text("Type") }
                                } else {
                                    OutlinedButton(onClick = {
                                        captureMode = "Photo"
                                        preferences.edit().putString("capture_mode", captureMode).apply()
                                    }) { Text("Photo") }
                                    Button(onClick = {
                                        captureMode = "Type"
                                        preferences.edit().putString("capture_mode", captureMode).apply()
                                    }) { Text("Type") }
                                    OutlinedButton(onClick = {
                                        noteImagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    }) { Text(if (noteImagePath == null) "Add image" else "Change image") }
                                }
                                TextButton(
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        settingsLoading = true
                                        showSettings = true
                                        if (!textToSpeechReady.value && textToSpeech == null) initializeTextToSpeech()
                                    },
                                ) { Text("Settings") }
                            }
                        } else if (route == AppRoute.Target) {
                            TextButton(onClick = { route = AppRoute.Capture }) { Text("Back to capture") }
                        }
                        if (route == AppRoute.Review) {
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
                            TextButton(onClick = { dictionaryRetry++ }) { Text("Retry offline data setup") }
                        }
                        if (analyzerLoading) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text("Installing UniDic: ${(analyzerProgress * 100).toInt()}% (about 725 MB)…")
                            }
                        }
                        if (analyzerError != null) {
                            Text(analyzerError!!, style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { dictionaryRetry++ }) { Text("Retry analyzer setup") }
                        }
                        OutlinedTextField(
                            value = editor,
                            onValueChange = { updated ->
                                if (updated.text != editor.text) {
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
                            TextButton(onClick = {
                                val source = photoSourcePath?.let(::File)?.takeIf(File::isFile)
                                if (source != null) processImage(source, runOcr = true)
                                else photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }) { Text("Retry photo text reading") }
                            TextButton(onClick = {
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
                                        Button(onClick = onSelect) { Text(token.surface) }
                                    } else {
                                        OutlinedButton(onClick = onSelect) { Text(token.surface) }
                                    }
                                }
                            }
                            if (tokenizationLoading) Text("Analyzing sentence…")
                            if (tokenizationError != null) {
                                Text(tokenizationError!!, style = MaterialTheme.typography.bodyMedium)
                                TextButton(onClick = { analysisRetry++ }) { Text("Retry sentence analysis") }
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                selectTarget(
                                    TargetSpan.fromSelection(
                                        editor.text,
                                        editor.selection.start,
                                        editor.selection.end,
                                    ),
                                )
                            },
                            enabled = editor.selection.start != editor.selection.end,
                        ) { Text("Use selected text as target") }
                        val targetText = target?.textIn(editor.text)
                        val lemmaLabel = analyzedTarget?.expression?.let { " → $it" }.orEmpty()
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
                        if (target?.textIn(editor.text) != null) {
                            Text("Offline dictionary match", style = MaterialTheme.typography.titleMedium)
                            if (dictionaryEntries.size > 1 && selectedDictionaryEntry == null) {
                                OutlinedButton(onClick = { showDictionarySheet = true }) {
                                    Text("Choose dictionary entry")
                                }
                            }
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
                            if (dictionaryHasMore && !dictionarySearchLoading) {
                                TextButton(onClick = {
                                    val query = target?.textIn(editor.text) ?: return@TextButton
                                    coroutineScope.launch {
                                        dictionarySearchLoading = true
                                        try {
                                            val more = withContext(Dispatchers.IO) {
                                                offlineDictionary.search(
                                                    query,
                                                    analyzedTarget?.expression,
                                                    offset = dictionaryEntries.size,
                                                )
                                            }
                                            dictionaryEntries = dictionaryEntries + more
                                            dictionaryHasMore = more.size == 50
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (_: Exception) {
                                            dictionaryError = "Could not load more dictionary matches."
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
                            if (pitch != null) PitchPatternDiagram(pitch, PitchDiagramScale.Compact)
                            OutlinedTextField(
                                value = definition,
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
                                value = translation,
                                onValueChange = { translation = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Sentence translation (optional)") },
                                minLines = 2,
                            )
                            OutlinedTextField(
                                value = notes,
                                onValueChange = { notes = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Notes (optional)") },
                                minLines = 2,
                            )
                            OutlinedTextField(
                                value = tags,
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
                                OutlinedButton(onClick = { dependentFieldsStale = false }) {
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
                        if (route != AppRoute.Review) when {
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
                                    enabled = noteTypes.isNotEmpty(),
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
                                selectedModel?.let { model ->
                                    AnkiFieldContract.mapToModelOrder(model.fields, emptyMap()).errors
                                        .forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                                }
                                Text("Destination deck", style = MaterialTheme.typography.titleMedium)
                                Button(
                                    onClick = { showDeckMenu = true },
                                    enabled = decks.isNotEmpty(),
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
                                if (!setupLoading && setupError == null && noteTypes.isEmpty()) {
                                    Text("No note types available in AnkiDroid.")
                                }
                                if (!setupLoading && setupError == null && decks.isEmpty()) {
                                    Text("No existing decks available in AnkiDroid.")
                                }
                            }
                            else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("AnkiDroid detected. Grant API permission to continue setup.")
                                Button(onClick = {
                                    apiPermissionLauncher.launch(ANKIDROID_READ_WRITE_PERMISSION)
                                }) { Text("Grant AnkiDroid API permission") }
                            }
                        }
                        submitMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        recoveryMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        if (recoveryRequired && recoveryDuplicate) {
                            TextButton(onClick = {
                                clearPendingSubmission(preferences)
                                recoveryRequired = false
                                recoveryDuplicate = false
                                submitMessage = "Pending Send resolved. The draft is unchanged; normal duplicate blocking still applies."
                            }) { Text("Treat pending Send as complete") }
                            TextButton(onClick = {
                                clearPendingSubmission(preferences)
                                recoveryRequired = false
                                recoveryDuplicate = false
                                submitMessage = "Pending Send cleared. Verify the destination before retrying."
                            }) { Text("I checked; allow retry") }
                        } else if (recoveryRequired && recoveryMessage?.startsWith("Could not verify") == true) {
                            TextButton(onClick = { setupReload++ }) { Text("Retry pending-send check") }
                        }
                        if (route != AppRoute.Review) Text(
                            "Draft autosaves on this device. Dictionary lookup is not connected yet.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(
                            enabled = canSend,
                            onClick = {
                                val model = selectedModel ?: return@Button
                                val deck = selectedDeck ?: return@Button
                                val fields = fieldsByName ?: return@Button
                                val mapping = modelMapping?.takeIf { it.isValid } ?: return@Button
                                val tagSet = AnkiFieldContract.parseTags(tags)
                                isSubmitting = true
                                submitMessage = null
                                coroutineScope.launch {
                                    try {
                                        val outcome = withContext(Dispatchers.IO) {
                                            val gateway = AnkiDroidGateway(contentResolver)
                                            if (gateway.hasDuplicate(model, deck, fields)) {
                                                preferences.edit().putString("submission_status", "blocked").commit()
                                                "duplicate"
                                            } else {
                                                val pendingSaved = preferences.edit()
                                                    .putString("submission_status", "started")
                                                    .putLong("pending_model_id", model.id)
                                                    .putLong("pending_deck_id", deck.id)
                                                    .putString("pending_sentence", fields.getValue("SentKanji"))
                                                    .putString("pending_lemma", fields.getValue("VocabKanji"))
                                                    .commit()
                                                check(pendingSaved) { "Could not save submission recovery data." }
                                                val inserted = gateway.insertNote(model, deck, mapping.values, tagSet)
                                                val foundInDestination = gateway.hasDuplicate(model, deck, fields)
                                                val foundAnywhere = foundInDestination ||
                                                    gateway.hasMatchingNoteInAnyDeck(model, fields)
                                                when {
                                                    inserted == 1 && foundInDestination -> {
                                                        check(preferences.edit().putString("submission_status", "done").commit())
                                                        clearPersistedDraft(preferences)
                                                        "created"
                                                    }
                                                    inserted == 1 || foundAnywhere -> "uncertain"
                                                    else -> {
                                                        clearPendingSubmission(preferences)
                                                        "failed"
                                                    }
                                                }
                                            }
                                        }
                                        when (outcome) {
                                            "duplicate" -> submitMessage = "Blocked: a note with this sentence and vocabulary lemma already exists in the selected note type and deck."
                                            "created" -> {
                                                clearDraft()
                                                submitMessage = "Note added to ${deck.name}."
                                            }
                                            "uncertain" -> {
                                                recoveryRequired = true
                                                recoveryDuplicate = false
                                                recoveryMessage = "AnkiDroid returned an uncertain result. Checking whether the note was added…"
                                            }
                                            else -> submitMessage = "AnkiDroid did not add the note. You can safely review and retry."
                                        }
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        if (preferences.getString("submission_status", null) == "started") {
                                            recoveryRequired = true
                                            recoveryMessage = "Could not confirm Send. Checking whether the note was added…"
                                        } else {
                                            submitMessage = "Could not send the note. Check AnkiDroid API access and retry."
                                        }
                                    } finally {
                                        isSubmitting = false
                                    }
                                }
                            },
                        ) {
                            Text(if (isSubmitting) "Sending…" else "Send to AnkiDroid")
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
        Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM)
            ?.let { it as? Uri }
            ?.takeIf { intent.type?.startsWith("image/") == true }
        else -> null
    }

    private fun clearConsumedImageIntent(uri: Uri) {
        if (imageFromIntent(intent) == uri) {
            setIntent(Intent(intent).apply {
                action = null
                type = null
                data = null
                removeExtra(Intent.EXTRA_STREAM)
            })
        }
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

private fun clearPersistedDraft(preferences: SharedPreferences) {
    val draftKeys = listOf(
        "sentence", "selection_start", "selection_end", "target_start", "target_end",
        "vocabulary", "vocabulary_reading", "pitch_accent", "definition", "definition_manual_stale", "sentence_reading",
        "leave_sentence_target_unannotated", "leave_vocabulary_unannotated", "dependent_fields_stale",
        "definition_manual", "definition_manual_stale", "translation", "notes", "tags", "submission_status", "pending_model_id", "pending_deck_id",
        "pending_sentence", "pending_lemma", "dictionary_entry_id", "dictionary_sense_indices",
        "sentence_furigana_corrections", "photo_source_path", "note_image_path", "processed_image_path",
    )
    preferences.edit().also { editor -> draftKeys.forEach(editor::remove) }.commit()
}

private fun clearPendingSubmission(preferences: SharedPreferences) {
    preferences.edit()
        .remove("submission_status")
        .remove("pending_model_id")
        .remove("pending_deck_id")
        .remove("pending_sentence")
        .remove("pending_lemma")
        .commit()
}

private fun TextRange.coerceIn(min: Int, max: Int): TextRange =
    TextRange(start.coerceIn(min, max), end.coerceIn(min, max))
