package dev.pierre.japaneseanki

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Android-free storage fake. Draft updates can be held to model delayed disk writes. */
internal class MemoryAppPreferenceStorage(
    private val events: MutableList<String> = mutableListOf(),
    var deferUpdates: Boolean = false,
    private val initialDraft: PreferenceValues = PreferenceValues(),
    private val initialSettings: PreferenceValues = PreferenceValues(),
    private val failReads: Boolean = false,
) : AppPreferenceStorage {
    private val draftState = MutableStateFlow(PreferenceValues())
    private val settingsState = MutableStateFlow(PreferenceValues())
    override val draft: StateFlow<PreferenceValues> = draftState.asStateFlow()
    override val settings: StateFlow<PreferenceValues> = settingsState.asStateFlow()
    private var durableDraft = PreferenceValues()
    private var durableSettings = PreferenceValues()
    private val queued = mutableListOf<Pair<PreferenceFile, PreferenceChanges>>()

    override suspend fun load() {
        draftState.value = if (failReads) PreferenceValues() else initialDraft
        settingsState.value = if (failReads) PreferenceValues() else initialSettings
    }

    override fun update(file: PreferenceFile, changes: PreferenceChanges) {
        state(file).value = state(file).value.changed(changes)
        if (deferUpdates) queued += file to changes else writeDurable(file, changes)
    }

    override suspend fun persist(file: PreferenceFile, changes: PreferenceChanges): Boolean =
        persistBlocking(file, changes)

    override fun persistBlocking(file: PreferenceFile, changes: PreferenceChanges): Boolean {
        flushQueuedUpdates()
        state(file).value = state(file).value.changed(changes)
        writeDurable(file, changes)
        changes.values[SubmissionRecoveryKeys.STATUS]?.let { events += "persist:$it" }
        return true
    }

    fun flushQueuedUpdates() {
        queued.toList().forEach { (file, changes) -> writeDurable(file, changes) }
        queued.clear()
    }

    fun durable(file: PreferenceFile): PreferenceValues = when (file) {
        PreferenceFile.DRAFT -> durableDraft
        PreferenceFile.SETTINGS -> durableSettings
    }

    private fun state(file: PreferenceFile): MutableStateFlow<PreferenceValues> = when (file) {
        PreferenceFile.DRAFT -> draftState
        PreferenceFile.SETTINGS -> settingsState
    }

    private fun writeDurable(file: PreferenceFile, changes: PreferenceChanges) {
        when (file) {
            PreferenceFile.DRAFT -> durableDraft = durableDraft.changed(changes)
            PreferenceFile.SETTINGS -> durableSettings = durableSettings.changed(changes)
        }
    }
}
