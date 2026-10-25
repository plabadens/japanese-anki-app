package dev.pierre.japaneseanki

import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** In-memory storage fake with the same main-thread and commit-failure contract as Android. */
internal class MemoryPreferenceBackingStore(
    initialDraft: PreferenceValues = PreferenceValues(),
    initialSettings: PreferenceValues = PreferenceValues(),
) {
    var draft: PreferenceValues = initialDraft
        private set
    var settings: PreferenceValues = initialSettings
        private set

    fun clear() {
        draft = PreferenceValues()
        settings = PreferenceValues()
    }

    fun update(file: PreferenceFile, changes: PreferenceChanges) {
        when (file) {
            PreferenceFile.DRAFT -> draft = draft.changed(changes)
            PreferenceFile.SETTINGS -> settings = settings.changed(changes)
        }
    }
}

internal class MemoryAppPreferenceStorage(
    private val events: MutableList<String> = mutableListOf(),
    var deferUpdates: Boolean = false,
    private val initialDraft: PreferenceValues = PreferenceValues(),
    private val initialSettings: PreferenceValues = PreferenceValues(),
    private val failReads: Boolean = false,
    private val backingStore: MemoryPreferenceBackingStore =
        MemoryPreferenceBackingStore(initialDraft, initialSettings),
    private val failCommits: Boolean = false,
) : AppPreferenceStorage {
    private val draftState = MutableStateFlow(PreferenceValues())
    private val settingsState = MutableStateFlow(PreferenceValues())
    override val draft: StateFlow<PreferenceValues> = draftState.asStateFlow()
    override val settings: StateFlow<PreferenceValues> = settingsState.asStateFlow()
    val draftCollectors: Int get() = draftState.subscriptionCount.value
    val settingsCollectors: Int get() = settingsState.subscriptionCount.value
    private val queued = mutableListOf<Pair<PreferenceFile, PreferenceChanges>>()

    override suspend fun load() {
        draftState.value = if (failReads) PreferenceValues() else backingStore.draft
        settingsState.value = if (failReads) PreferenceValues() else backingStore.settings
    }

    override fun update(file: PreferenceFile, changes: PreferenceChanges) {
        state(file).value = state(file).value.changed(changes)
        if (deferUpdates) queued += file to changes else writeDurable(file, changes)
    }

    override suspend fun persist(file: PreferenceFile, changes: PreferenceChanges): Boolean =
        withContext(Dispatchers.IO) { persistBlocking(file, changes) }

    override fun persistBlocking(file: PreferenceFile, changes: PreferenceChanges): Boolean {
        check(Looper.myLooper() != Looper.getMainLooper()) {
            "Preference commits must not run on the main thread."
        }
        flushQueuedUpdates()
        state(file).value = state(file).value.changed(changes)
        val saved = if (failCommits) false else writeDurable(file, changes)
        changes.values[SubmissionRecoveryKeys.STATUS]?.let { events += "persist:$it" }
        return saved
    }

    fun flushQueuedUpdates() {
        queued.toList().forEach { (file, changes) -> writeDurable(file, changes) }
        queued.clear()
    }

    fun durable(file: PreferenceFile): PreferenceValues = when (file) {
        PreferenceFile.DRAFT -> backingStore.draft
        PreferenceFile.SETTINGS -> backingStore.settings
    }

    private fun state(file: PreferenceFile): MutableStateFlow<PreferenceValues> = when (file) {
        PreferenceFile.DRAFT -> draftState
        PreferenceFile.SETTINGS -> settingsState
    }

    private fun writeDurable(file: PreferenceFile, changes: PreferenceChanges): Boolean {
        backingStore.update(file, changes)
        return true
    }
}
