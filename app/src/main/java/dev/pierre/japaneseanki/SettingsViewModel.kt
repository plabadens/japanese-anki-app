package dev.pierre.japaneseanki

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.pierre.japaneseanki.ui.SettingsScreenState
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Setup and Settings edit one form. Navigation never creates a second coordinator or credential. */
internal class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(SettingsScreenState(SettingsValues(), "", false, loading = true))
    val uiState = mutableState.asStateFlow()
    private var saveJob: Job? = null
    private var voicesJob: Job? = null
    private var keyEdited = false
    private var savedValues: SettingsValues? = null

    init {
        viewModelScope.launch {
            try {
                val saved = repository.load()
                savedValues = saved.values
                mutableState.value = SettingsScreenState(
                    values = saved.values, googleKey = saved.googleKey,
                    credentialStored = saved.googleKey.isNotBlank(), credentialUnreadable = saved.credentialUnreadable,
                    voiceCatalog = VoiceCatalogState(
                        saved.cachedVoices,
                        if (saved.cachedVoiceListPresent) VoiceCatalogProvenance.CACHED else VoiceCatalogProvenance.NONE,
                    ),
                )
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.update { it.copy(loading = false, problem = SettingsProblem.Load) } }
        }
    }

    fun valuesChanged(values: SettingsValues) {
        mutableState.update { it.copy(values = values, problem = null) }
        autosave()
    }

    fun keyChanged(key: String) {
        if (key == uiState.value.googleKey) return
        keyEdited = true
        voicesJob?.cancel()
        mutableState.update { it.copy(googleKey = key, credentialStored = false, voiceCatalog = VoiceCatalogState(), problem = null) }
        autosave()
    }

    private fun autosave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch { delay(350); save() }
    }

    private suspend fun save(): Boolean {
        val form = uiState.value
        if (form.loading) return false
        if (savedValues == form.values && !keyEdited) return true
        val editedKey = form.googleKey.takeIf { keyEdited }
        val problem = repository.save(form.values, editedKey)
        if (problem == null) savedValues = form.values
        // An older save may complete after another edit. Never overwrite the current form.
        if (uiState.value.values == form.values && uiState.value.googleKey == form.googleKey) {
            if (problem == null) keyEdited = false
            mutableState.update { it.copy(problem = problem, credentialStored = problem == null && form.googleKey.isNotBlank()) }
        }
        return problem == null
    }

    suspend fun flush(): Boolean {
        saveJob?.cancelAndJoin()
        saveJob = null
        return save()
    }

    fun reloadVoices() {
        voicesJob?.cancel()
        voicesJob = viewModelScope.launch {
            if (!flush()) return@launch
            val key = uiState.value.googleKey
            if (key.isBlank()) {
                mutableState.update { it.copy(problem = SettingsProblem.InvalidKey) }
                return@launch
            }
            mutableState.update { it.copy(voiceCatalog = it.voiceCatalog.copy(loading = true, error = null), problem = null) }
            try {
                val voices = repository.voices(key)
                check(repository.cacheVoices(voices))
                mutableState.update { it.copy(voiceCatalog = VoiceCatalogState(voices, VoiceCatalogProvenance.AUTHORITATIVE, attempted = true)) }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) {
                mutableState.update { it.copy(voiceCatalog = it.voiceCatalog.copy(loading = false, attempted = true), problem = SettingsProblem.Voices) }
            }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            mutableState.update { it.copy(cacheBusy = true, cacheMessage = null) }
            try {
                repository.clearCache()
                mutableState.update { it.copy(cacheBusy = false, cacheMessage = "Cache cleared", problem = null) }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.update { it.copy(cacheBusy = false, cacheMessage = "Could not clear the cache.", problem = SettingsProblem.Cache) } }
        }
    }

    suspend fun finishSetup(): Boolean {
        if (!flush()) return false
        val form = uiState.value
        if (!form.credentialStored || form.voiceCatalog.selectedVoiceForSynthesis(form.values.voiceName) == null) return false
        val saved = repository.finishSetup()
        if (!saved) mutableState.update { it.copy(problem = SettingsProblem.Save) }
        return saved
    }
}
