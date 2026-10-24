package dev.pierre.japaneseanki

import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

internal enum class PreferenceFile { DRAFT, SETTINGS }

/** Immutable, non-blocking view of values loaded once before composition starts. */
internal class PreferenceValues internal constructor(private val values: Map<String, Any?> = emptyMap()) {
    fun contains(key: String): Boolean = values.containsKey(key)
    fun getString(key: String, default: String?): String? = values[key] as? String ?: default
    fun getInt(key: String, default: Int): Int = values[key] as? Int ?: default
    fun getLong(key: String, default: Long): Long = values[key] as? Long ?: default
    fun getBoolean(key: String, default: Boolean): Boolean = values[key] as? Boolean ?: default
    fun getStringSet(key: String, default: Set<String>?): Set<String>? =
        (values[key] as? Set<*>)?.filterIsInstance<String>()?.toSet() ?: default

    internal fun changed(changes: PreferenceChanges): PreferenceValues {
        val updated = values.toMutableMap()
        changes.values.forEach { (key, value) ->
            if (value == null) updated.remove(key) else updated[key] = value
        }
        return PreferenceValues(updated)
    }
}

/** A small, Android-free batch of preference changes. Null string values mean removal. */
internal class PreferenceChanges {
    internal val values = linkedMapOf<String, Any?>()
    fun putString(key: String, value: String?): PreferenceChanges = set(key, value)
    fun putInt(key: String, value: Int): PreferenceChanges = set(key, value)
    fun putLong(key: String, value: Long): PreferenceChanges = set(key, value)
    fun putBoolean(key: String, value: Boolean): PreferenceChanges = set(key, value)
    fun putStringSet(key: String, value: Set<String>?): PreferenceChanges = set(key, value?.toSet())
    fun remove(key: String): PreferenceChanges = set(key, null)
    private fun set(key: String, value: Any?): PreferenceChanges = apply { values[key] = value }
}

/** The only persistence boundary for drafts, settings, and frozen-send recovery. */
internal interface AppPreferenceStorage {
    val draft: StateFlow<PreferenceValues>
    val settings: StateFlow<PreferenceValues>
    suspend fun load()
    fun update(file: PreferenceFile, changes: PreferenceChanges)
    suspend fun persist(file: PreferenceFile, changes: PreferenceChanges): Boolean
    fun persistBlocking(file: PreferenceFile, changes: PreferenceChanges): Boolean
}

/**
 * SharedPreferences adapter. All Android reads happen on [dispatcher] before composition; every
 * write is serialized through one ordered queue there. Recovery writes use awaited commits. The
 * synchronous settings adapter is used only by its existing background scheduler.
 */
internal class AndroidPreferenceStorage(
    context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AppPreferenceStorage {
    private data class WriteRequest(
        val file: PreferenceFile,
        val changes: PreferenceChanges,
        val await: Boolean,
        val completion: (Boolean) -> Unit,
    )

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val requestLock = Any()
    private val requests = Channel<WriteRequest>(Channel.UNLIMITED)
    private lateinit var draftPreferences: SharedPreferences
    private lateinit var settingsPreferences: SharedPreferences

    private val mutableDraft = MutableStateFlow(PreferenceValues())
    private val mutableSettings = MutableStateFlow(PreferenceValues())
    override val draft: StateFlow<PreferenceValues> = mutableDraft.asStateFlow()
    override val settings: StateFlow<PreferenceValues> = mutableSettings.asStateFlow()

    init {
        scope.launch {
            for (request in requests) {
                val saved = runCatching { write(request.file, request.changes, request.await) }.getOrDefault(false)
                request.completion(saved)
            }
        }
    }

    override suspend fun load() {
        val loaded = kotlinx.coroutines.withContext(dispatcher) {
            val loadedDraft = runCatching {
                draftPreferences = appContext.getSharedPreferences("draft", Context.MODE_PRIVATE)
                PreferenceValues(draftPreferences.all.toMap())
            }.getOrDefault(PreferenceValues())
            val loadedSettings = runCatching {
                settingsPreferences = appContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
                PreferenceValues(settingsPreferences.all.toMap())
            }.getOrDefault(PreferenceValues())
            loadedDraft to loadedSettings
        }
        synchronized(requestLock) {
            mutableDraft.value = loaded.first
            mutableSettings.value = loaded.second
        }
    }

    override fun update(file: PreferenceFile, changes: PreferenceChanges) {
        if (changes.values.isEmpty()) return
        synchronized(requestLock) {
            state(file).value = state(file).value.changed(changes)
            check(requests.trySend(WriteRequest(file, changes, await = false) {}).isSuccess) {
                "Preference writer is closed."
            }
        }
    }

    override suspend fun persist(file: PreferenceFile, changes: PreferenceChanges): Boolean {
        if (changes.values.isEmpty()) return true
        val completion = CompletableDeferred<Boolean>()
        synchronized(requestLock) {
            state(file).value = state(file).value.changed(changes)
            check(requests.trySend(WriteRequest(file, changes, await = true, completion::complete)).isSuccess) {
                "Preference writer is closed."
            }
        }
        return completion.await()
    }

    override fun persistBlocking(file: PreferenceFile, changes: PreferenceChanges): Boolean {
        check(Looper.myLooper() != Looper.getMainLooper()) { "Preference commits must not run on the main thread." }
        if (changes.values.isEmpty()) return true
        val result = AtomicBoolean(false)
        val completed = CountDownLatch(1)
        synchronized(requestLock) {
            state(file).value = state(file).value.changed(changes)
            check(requests.trySend(
                WriteRequest(file, changes, await = true) { saved ->
                    result.set(saved)
                    completed.countDown()
                },
            ).isSuccess) { "Preference writer is closed." }
        }
        completed.await()
        return result.get()
    }

    private fun state(file: PreferenceFile): MutableStateFlow<PreferenceValues> = when (file) {
        PreferenceFile.DRAFT -> mutableDraft
        PreferenceFile.SETTINGS -> mutableSettings
    }

    private fun preferences(file: PreferenceFile): SharedPreferences = when (file) {
        PreferenceFile.DRAFT -> draftPreferences
        PreferenceFile.SETTINGS -> settingsPreferences
    }

    private fun write(file: PreferenceFile, changes: PreferenceChanges, await: Boolean): Boolean {
        val editor = preferences(file).edit()
        for ((key, value) in changes.values) {
            when (value) {
                null -> editor.remove(key)
                is String -> editor.putString(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toMutableSet())
                else -> error("Unsupported preference value for $key")
            }
        }
        return if (await) editor.commit() else {
            editor.apply()
            true
        }
    }
}

/** Adapter for the draft audio controller; its values are queued on the storage dispatcher. */
internal class PreferenceDraftAudioPersistence(private val storage: AppPreferenceStorage) : DraftAudioPersistence {
    override fun read(key: String): String? = storage.draft.value.getString(key, null)
    override fun write(changes: Map<String, String?>): Boolean {
        storage.update(PreferenceFile.DRAFT, PreferenceChanges().also { editor ->
            changes.forEach { (key, value) -> editor.putString(key, value) }
        })
        return true
    }
}

/** Side effects attached to a verified send, invoked from SendCoordinator's I/O dispatcher. */
internal class PreferenceFrozenSubmissionStore(
    private val storage: AppPreferenceStorage,
    private val draftDirectory: java.io.File,
) : dev.pierre.japaneseanki.send.FrozenSubmissionStore {
    override fun persistLastSentTags(tags: Set<String>): Boolean = storage.persistBlocking(
        PreferenceFile.SETTINGS,
        PreferenceChanges().putString("last_note_tags", tags.joinToString(" ")),
    )

    override fun clearDraft() = clearPersistedDraftBlocking(storage, draftDirectory)
}

internal fun clearPersistedDraftBlocking(storage: AppPreferenceStorage, draftDirectory: java.io.File?) {
    val preferences = storage.draft.value
    val ownedDirectory = draftDirectory?.canonicalFile
    listOf("photo_source_path", "note_image_path", "processed_image_path", "pending_replacement_image_path")
        .mapNotNull { preferences.getString(it, null) }
        .distinct()
        .forEach { path ->
            val file = java.io.File(path)
            if (ownedDirectory != null && file.exists() && file.canonicalFile.parentFile == ownedDirectory) file.delete()
        }
    storage.persistBlocking(PreferenceFile.DRAFT, draftClearChanges())
}

internal fun draftClearChanges(): PreferenceChanges {
    val keys = listOf(
        "sentence", "selection_start", "selection_end", "target_start", "target_end", "vocabulary",
        "vocabulary_reading", "pitch_accent", "definition", "definition_manual_stale", "sentence_reading",
        "sentence_reading_corrections", "leave_sentence_target_unannotated", "leave_vocabulary_unannotated",
        "dependent_fields_stale", "definition_manual", "translation", "translation_sentence", "translation_source",
        "translation_manual", "translation_stale", "translation_pending", "translation_error", "translation_suggestion",
        "translation_suggestion_source", "translation_suggestion_error", "translation_attempted_sentence", "notes", "tags",
        "submission_status", "pending_model_id", "pending_deck_id", "pending_sentence", "pending_lemma",
        "dictionary_entry_id", "dictionary_sense_indices", "sentence_furigana_corrections", "photo_source_path",
        "note_image_path", "processed_image_path", IMAGE_RECEIPT_PATH, IMAGE_RECEIPT_FILENAME, IMAGE_RECEIPT_SIZE,
        IMAGE_RECEIPT_MODIFIED, SENTENCE_AUDIO_RECEIPT_PATH, SENTENCE_AUDIO_RECEIPT_FILENAME,
        SENTENCE_AUDIO_RECEIPT_SIZE, SENTENCE_AUDIO_RECEIPT_MODIFIED, WORD_AUDIO_RECEIPT_PATH,
        WORD_AUDIO_RECEIPT_FILENAME, WORD_AUDIO_RECEIPT_SIZE, WORD_AUDIO_RECEIPT_MODIFIED,
        SubmissionRecoveryKeys.MEDIA_OPERATION, SubmissionRecoveryKeys.MEDIA_SOURCE_PATH,
        SubmissionRecoveryKeys.MEDIA_SOURCE_SIZE, SubmissionRecoveryKeys.MEDIA_SOURCE_MODIFIED,
        "pending_replacement_image_path", "draft_origin", SubmissionRecoveryKeys.REVISION,
        SubmissionRecoveryKeys.MODEL_FIELDS, SubmissionRecoveryKeys.ORDERED_FIELDS, SubmissionRecoveryKeys.VOCABULARY_READING,
        SubmissionRecoveryKeys.SENTENCE_READING, SubmissionRecoveryKeys.DEFINITION, SubmissionRecoveryKeys.TRANSLATION,
        SubmissionRecoveryKeys.NOTES, SubmissionRecoveryKeys.TAGS, SubmissionRecoveryKeys.PITCH,
        SubmissionRecoveryKeys.TARGET_START, SubmissionRecoveryKeys.TARGET_END, SubmissionRecoveryKeys.MEDIA_PATH,
        SubmissionRecoveryKeys.MEDIA_SIZE, SubmissionRecoveryKeys.MEDIA_MODIFIED, SubmissionRecoveryKeys.IMPORTED_FILENAME,
        SubmissionRecoveryKeys.NOTE_ID,
    ) + draftAudioPreferenceKeys()
    return PreferenceChanges().also { editor -> keys.forEach(editor::remove) }
}

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
