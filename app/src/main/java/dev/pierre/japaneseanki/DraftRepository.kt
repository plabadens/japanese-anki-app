package dev.pierre.japaneseanki

import android.content.Context
import android.util.Log
import android.net.Uri
import androidx.core.content.ContextCompat
import dev.pierre.japaneseanki.send.SendCoordinator
import dev.pierre.japaneseanki.send.SendOutcome
import dev.pierre.japaneseanki.send.SendRequest
import dev.pierre.japaneseanki.speech.CloudVoice
import dev.pierre.japaneseanki.speech.GoogleChirpSpeechProvider
import dev.pierre.japaneseanki.speech.SentenceSpeechRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private const val AUDIO_LOG = "DraftAudio"

internal data class DraftRecord(
    val draft: DraftModel = DraftModel(),
    val selectionStart: Int = 0,
    val selectionEnd: Int = 0,
    val translation: TranslationDraft = TranslationDraft("", TranslationResult.Pending),
    val activeSentence: String? = null,
    val entryId: Long? = null,
    val senseIndex: Int = 0,
    val pitchHtml: String? = null,
    val sourcePath: String? = null,
    val imagePath: String? = null,
    val captureMode: String = "Photo",
    val route: AppRoute = AppRoute.Capture,
)
internal data class AnkiConnection(
    val installed: Boolean = false,
    val permission: Boolean = false,
    val models: List<AnkiNoteType> = emptyList(),
    val decks: List<AnkiDeck> = emptyList(),
    val failed: Boolean = false,
)
internal data class SubmissionRecord(val phase: SubmissionPhase?, val frozen: FrozenSubmission?, val operation: MediaImportOperation?)
internal data class LocalDictionary(val ready: Boolean, val storageFull: Boolean = false, val notices: Map<String, String> = emptyMap())

/** Android, providers and files are confined here. A ViewModel owns decisions, never a Context. */
internal class DraftRepository(
    context: Context,
    private val storage: AppPreferenceStorage,
    private val speech: dev.pierre.japaneseanki.speech.SentenceSpeechProvider = GoogleChirpSpeechProvider(
        credential = { GoogleCloudCredential.read(storage.readBlocking(PreferenceFile.SETTINGS)) },
    ),
    private val wordAudio: AudioProvider = Nhk2016PronunciationSource(readIndexText = {
        context.applicationContext.assets.open(Nhk2016PronunciationSource.ASSET_NAME).bufferedReader().use { it.readText() }
    }),
) {
    private val context = context.applicationContext
    internal val analyzer = AppServices.offlineAnalyzer(this.context)
    private val dictionary = AppServices.offlineDictionary(this.context) { OfflineDictionary(it) }
    private val pitches = AjtPitchRepository.shared(this.context)
    private val directory = File(this.context.filesDir, "draft")
    private val files = DraftAudioFileStore(directory, File(this.context.filesDir, "audio-staging"))
    private val cache get() = (context as JapaneseAnkiApplication).audioCache
    private lateinit var audio: DraftAudioController
    private lateinit var sender: SendCoordinator
    val outputDirectory: File get() = directory

    private fun credential(): String? = GoogleCloudCredential.read(storage.readBlocking(PreferenceFile.SETTINGS))

    suspend fun restore(): DraftRecord = withContext(Dispatchers.IO) {
        storage.load()
        directory.mkdirs()
        if (storage.readBlocking(PreferenceFile.DRAFT).getString(SubmissionRecoveryKeys.STATUS, null) == "done") {
            clearPersistedDraftBlocking(storage, directory)
        }
        val saved = storage.readBlocking(PreferenceFile.DRAFT)
        audio = DraftAudioController(files, PreferenceDraftAudioPersistence(storage), synthesize = { input, file ->
            when (val payload = input.providerPayload) {
                is SentenceSpeechRequest -> speech.synthesize(payload, file)
                is AudioSynthesisRequest -> wordAudio.synthesize(payload, file)
                else -> error("Unsupported audio request")
            }
            Unit
        }, cache = cache)
        sender = SendCoordinator(AnkiDroidGateway(context.contentResolver, context), storage, audio)
        val sentence = saved.getString("sentence", "").orEmpty()
        DraftRecord(
            draft = restoreDraftModel(saved, sentence, storage.settings.value.getString(SettingsPreferenceKeys.DEFAULT_TAGS, "").orEmpty()),
            selectionStart = saved.getInt("selection_start", 0).coerceIn(0, sentence.length),
            selectionEnd = saved.getInt("selection_end", 0).coerceIn(0, sentence.length),
            translation = restoreTranslationDraft(saved).draft.let { if (it.stale) TranslationDraft(sentence, TranslationResult.Pending) else it },
            activeSentence = saved.getString("committed_sentence", null),
            entryId = saved.getLong("dictionary_entry_id", -1).takeIf { it > 0 },
            senseIndex = saved.getStringSet("dictionary_sense_indices", null)?.firstOrNull()?.toIntOrNull() ?: 0,
            pitchHtml = saved.getString("pitch_html", null),
            sourcePath = saved.getString("note_image_path", saved.getString("photo_source_path", null)),
            imagePath = saved.getString("processed_image_path", null),
            captureMode = saved.getString("capture_mode", "Photo").orEmpty(),
            route = if (saved.getString("route", null) in listOf("Card", "Target", "Review")) AppRoute.Card else AppRoute.Capture,
        )
    }

    suspend fun save(record: DraftRecord): Boolean = withContext(Dispatchers.IO) {
        val translation = record.translation
        val result = translation.result
        storage.persist(PreferenceFile.DRAFT, PreferenceChanges()
            .putString("sentence", record.draft.sentence)
            .putInt("selection_start", record.selectionStart).putInt("selection_end", record.selectionEnd)
            .putInt("target_start", record.draft.target?.start ?: -1).putInt("target_end", record.draft.target?.end ?: -1)
            .putString("vocabulary", record.draft.vocabulary).putString("vocabulary_reading", record.draft.vocabularyReading)
            .putString("definition", record.draft.definition).putString("sentence_reading", record.draft.sentenceReading)
            .putString("sentence_reading_corrections", SentenceReadings.encode(record.draft.readingCorrections))
            .putString("pitch_accent", record.draft.pitchAccent).putString("pitch_html", record.pitchHtml)
            .putString("notes", record.draft.notes).putString("tags", record.draft.tags)
            .putString("translation", (result as? TranslationResult.Ready)?.text)
            .putString("translation_source", (result as? TranslationResult.Ready)?.source?.name)
            .putString("translation_sentence", translation.sentence)
            .putBoolean("translation_manual", translation.manuallyEdited).putBoolean("translation_stale", translation.stale)
            .putBoolean("translation_pending", result is TranslationResult.Pending)
            .putString("translation_error", (result as? TranslationResult.Failed)?.message)
            .putString("translation_failure", (result as? TranslationResult.Failed)?.failure?.name)
            .putString("committed_sentence", record.activeSentence)
            .putLong("dictionary_entry_id", record.entryId ?: -1)
            .putStringSet("dictionary_sense_indices", setOf(record.senseIndex.toString()))
            .putString("note_image_path", record.sourcePath).putString("processed_image_path", record.imagePath)
            .putString("route", record.route.name).putString("capture_mode", record.captureMode),
        )
    }

    suspend fun prepareDictionary(): LocalDictionary = withContext(Dispatchers.IO) {
        try {
            if (!analyzer.loadInstalled()) analyzer.prepare()
            check(analyzer.loadInstalled())
            val notices = dictionary.prepare()
            pitches.prepare()
            LocalDictionary(true, notices = notices)
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (error: Exception) { LocalDictionary(false, storageFull = error is BundledDictionaryStorageException) }
    }

    suspend fun analyze(sentence: String): List<JapaneseToken> = withContext(Dispatchers.IO) {
        groupTokens(analyzer.analyze(sentence)) { dictionary.hasEntry(it) }
    }
    suspend fun meanings(sentence: String, target: TargetSpan, tokens: List<JapaneseToken>): List<DictionaryMeaningChoice> =
        withContext(Dispatchers.IO) {
            DictionaryMeaningChoices.from(dictionary.search(target.textIn(sentence).orEmpty(), resolveAnalyzedLemma(tokens, target)?.expression))
        }
    suspend fun pitch(entry: DictionaryEntry): List<AjtPitchRecord> = withContext(Dispatchers.IO) { pitches.pitch(entry.expression, entry.reading) }
    suspend fun translate(sentence: String): TranslationResult = withContext(Dispatchers.IO) {
        TranslationService(credential = ::credential).translate(sentence)
    }

    suspend fun connection(): AnkiConnection = withContext(Dispatchers.IO) {
        val installed = runCatching { context.packageManager.getPackageInfo("com.ichi2.anki", 0) }.isSuccess
        val permission = installed && ContextCompat.checkSelfPermission(context, ANKIDROID_READ_WRITE_PERMISSION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!permission) return@withContext AnkiConnection(installed, false)
        try {
            val gateway = AnkiDroidGateway(context.contentResolver)
            AnkiConnection(true, true, gateway.noteTypes(), gateway.decks())
        } catch (_: Exception) { AnkiConnection(true, true, failed = true) }
    }

    suspend fun sentenceAudio(record: DraftRecord, tokens: List<JapaneseToken>, voice: CloudVoice, retry: Boolean, onStarted: (DraftAudioSnapshot) -> Unit): DraftAudioSnapshot =
        withContext(Dispatchers.IO) {
            val sentence = record.draft.sentence
            val request = SentenceSpeechRequest(sentence, voice)
            prepareAudio(DraftAudioInput(
                AudioItem.SENTENCE, DraftAudioIdentity.sentenceRequest(sentence, voice.name), sentence, 0,
                cacheKey = AudioCacheKey("google-chirp3-hd", sentence, voice.name, request.speakingRate.toFloat()),
                expectedFormat = AudioFormat.MP3, providerPayload = request,
            ), retry, onStarted)
        }

    suspend fun wordAudio(entry: DictionaryEntry, retry: Boolean, onStarted: (DraftAudioSnapshot) -> Unit): DraftAudioSnapshot = withContext(Dispatchers.IO) {
        val identity = "${entry.id}|${entry.expression}|${entry.reading}"
        prepareAudio(DraftAudioInput(
            AudioItem.VOCABULARY, DraftAudioIdentity.word(entry.id, entry.expression, entry.reading), entry.expression, 0,
            entryIdentity = identity, cacheKey = AudioCacheKey("nhk-2016", entry.expression, "NHK-2016", 1f, entryIdentity = identity),
            expectedFormat = AudioFormat.OGG, providerPayload = AudioSynthesisRequest(AudioItem.VOCABULARY, entry.expression, 0, entryIdentity = identity),
        ), retry, onStarted)
    }

    private suspend fun prepareAudio(input: DraftAudioInput, retry: Boolean, onStarted: (DraftAudioSnapshot) -> Unit): DraftAudioSnapshot {
        audio.setInput(input)
        val attempt = audio.confirm(input.item) ?: if (retry) audio.retry(input.item) else null
        onStarted(audio.snapshot())
        val result = attempt?.let { audio.runAttempt(it) }
        logAudioOutcome(input, result)
        return audio.snapshot()
    }

    /**
     * Local diagnostics only: bounded enums, HTTP codes, voice ids and counts. Never the sentence,
     * the reading, a path, or the credential.
     */
    private fun logAudioOutcome(input: DraftAudioInput, result: DraftAudioAttemptResult?) {
        when (result) {
            is DraftAudioAttemptResult.Accepted ->
                Log.i(AUDIO_LOG, "${input.item.name} accepted fromCache=${result.fromCache}")
            is DraftAudioAttemptResult.Failed -> {
                val diagnostic = result.diagnostic
                Log.e(
                    AUDIO_LOG,
                    "${input.item.name} generation failed retryable=${result.retryable}" +
                        " category=${diagnostic?.category ?: "UNKNOWN"}" +
                        " http=${diagnostic?.httpStatus ?: "-"}" +
                        " status=${diagnostic?.googleStatus ?: "-"}" +
                        " reason=${diagnostic?.googleReason ?: "-"}" +
                        " voice=${diagnostic?.voiceName ?: "-"}" +
                        " hints=${diagnostic?.hintCount ?: "-"}" +
                        " inputBytes=${diagnostic?.inputBytes ?: "-"}" +
                        " requestBytes=${diagnostic?.requestBytes ?: "-"}",
                )
            }
            DraftAudioAttemptResult.Stale -> Log.i(AUDIO_LOG, "${input.item.name} attempt became stale")
            null -> Log.w(AUDIO_LOG, "${input.item.name} was not authorized; no attempt started")
        }
    }
    suspend fun invalidateAudio(item: AudioItem) = withContext(Dispatchers.IO) { audio.clear(item); audio.snapshot() }
    fun audioSnapshot(): DraftAudioSnapshot = audio.snapshot()
    suspend fun playback(item: AudioItem): Pair<DraftAudioFile, AudioFileLease>? = withContext(Dispatchers.IO) {
        val ready = audio.state(item) as? DraftAudioState.Ready ?: return@withContext null
        ready.file to files.lease(ready.file, AudioFileOwner.PLAYBACK)
    }

    suspend fun submission(): SubmissionRecord = withContext(Dispatchers.IO) {
        val saved = storage.readBlocking(PreferenceFile.DRAFT)
        SubmissionRecord(SubmissionPhase.fromPersisted(saved.getString(SubmissionRecoveryKeys.STATUS, null)), readFrozenSubmission(saved),
            saved.getString(SubmissionRecoveryKeys.MEDIA_OPERATION, null)?.let { name -> MediaImportOperation.entries.firstOrNull { it.name == name } })
    }
    suspend fun submit(request: SendRequest): SendOutcome = sender.submit(request)
    suspend fun recover(): SendOutcome = sender.recover()
    suspend fun discardSubmission(): Boolean = withContext(Dispatchers.IO) {
        val saved = storage.persist(PreferenceFile.DRAFT, clearFrozenSubmission())
        if (saved) audio.releaseFreeze(retainFiles = false)
        saved
    }

    suspend fun copyImage(uri: String): File = withContext(Dispatchers.IO) {
        val file = File(directory, "source-${UUID.randomUUID()}.image")
        try {
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { input -> file.outputStream().use(input::copyTo) }
                ?: error("Image unavailable")
            check(file.length() > 0)
            file
        } catch (error: Exception) { file.delete(); throw error }
    }
    suspend fun processImage(source: File): File = withContext(Dispatchers.IO) {
        ImageMediaProcessor.process(source, File(directory, "${UUID.randomUUID()}.webp")).file
    }
    suspend fun recognize(source: File): JapaneseOcrResult = JapaneseOcr().recognize(source)
    suspend fun clear(defaultTags: String) = withContext(Dispatchers.IO) {
        audio.releaseFreeze(retainFiles = false)
        audio.discard()
        clearPersistedDraftBlocking(storage, directory)
        DraftRecord(draft = DraftModel(tags = defaultTags))
    }
    suspend fun licenses(): String = withContext(Dispatchers.IO) {
        fun read(path: String, charset: java.nio.charset.Charset = Charsets.UTF_8) =
            context.assets.open(path).bufferedReader(charset).use { it.readText() }
        listOf(
            android.text.Html.fromHtml(read("licenses/EDRDG-licence.html", Charsets.ISO_8859_1), android.text.Html.FROM_HTML_MODE_LEGACY).toString(),
            read("licenses/CC-BY-SA-4.0.txt"), read("licenses/mecab/BSD"), read("licenses/unidic/BSD"),
        ).joinToString("\n\n")
    }
    fun close() { if (::audio.isInitialized) audio.dispose() }
}
