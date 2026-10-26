package dev.pierre.japaneseanki

import dev.pierre.japaneseanki.speech.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {
    @Test fun autosaveCommitsOnlyTheLatestFormWithoutAnExecutorOrASecondCoordinator() = test { storage, vm ->
        vm.valuesChanged(SettingsValues(defaultTags = "older"))
        vm.valuesChanged(SettingsValues(defaultTags = "latest"))
        advanceTimeBy(350)
        runCurrent()
        storage.settings.first { it.getString("default_tags", null) == "latest" }
        assertEquals("latest", storage.durable(PreferenceFile.SETTINGS).getString("default_tags", null))
    }
    @Test fun flushCommitsTheCurrentFormBeforeNavigation() = test { storage, vm ->
        vm.valuesChanged(SettingsValues(defaultTags = "keep"))
        assertTrue(vm.flush())
        assertEquals("keep", storage.durable(PreferenceFile.SETTINGS).getString("default_tags", null))
    }
    @Test fun changingAKeyClearsItsOldVoiceCacheAndNeverStoresPlaintext() = test { storage, vm ->
        vm.keyChanged("new-synthetic-key")
        assertTrue(vm.flush())
        assertEquals("encrypted-fixture", storage.durable(PreferenceFile.SETTINGS).getString(GoogleCloudCredential.PREF, null))
        assertNull(storage.durable(PreferenceFile.SETTINGS).getString(SettingsPreferenceKeys.VOICE_CACHE, null))
        assertTrue(vm.uiState.value.voiceCatalog.voices.isEmpty())
        assertFalse(vm.uiState.value.toString().contains("new-synthetic-key"))
    }
    @Test fun invalidKeyDoesNotPartiallyCommitUnrelatedEdits() = test { storage, vm ->
        vm.valuesChanged(SettingsValues(defaultTags = "not yet saved"))
        vm.keyChanged("invalid key")
        assertFalse(vm.flush())
        assertEquals(SettingsProblem.InvalidKey, vm.uiState.value.problem)
        assertNull(storage.durable(PreferenceFile.SETTINGS).getString("default_tags", null))
        assertEquals("old-ciphertext", storage.durable(PreferenceFile.SETTINGS).getString(GoogleCloudCredential.PREF, null))
    }
    @Test fun cachedVerifiedMetadataCanFinishSetupWithoutAnotherNetworkRequest() = test { storage, vm ->
        assertTrue(vm.finishSetup())
        assertTrue(storage.durable(PreferenceFile.SETTINGS).getBoolean("onboarding_complete", false))
        assertEquals(VoiceCatalogProvenance.CACHED, vm.uiState.value.voiceCatalog.provenance)
    }
    @Test fun failedSaveKeepsTheEditedFormAndCanBeRetried() = runTest(timeout = 20.seconds) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val memory = MemoryAppPreferenceStorage()
        var reject = true
        val storage = object : AppPreferenceStorage by memory {
            override suspend fun persist(file: PreferenceFile, changes: PreferenceChanges): Boolean =
                if (reject) false else memory.persist(file, changes)
        }
        val vm = SettingsViewModel(repository(storage))
        try {
            vm.uiState.first { !it.loading }
            vm.valuesChanged(SettingsValues(defaultTags = "kept"))
            assertFalse(vm.flush())
            assertEquals(SettingsProblem.Save, vm.uiState.value.problem)
            assertEquals("kept", vm.uiState.value.values.defaultTags)
            reject = false
            assertTrue(vm.flush())
            assertEquals("kept", memory.durable(PreferenceFile.SETTINGS).getString("default_tags", null))
        } finally { vm.viewModelScopeForTestClear(); Dispatchers.resetMain() }
    }
    @Test fun keyEditCancelsAnOldVoiceRequestBeforeItCanReplaceOrCacheTheNewCatalog() = runTest(timeout = 20.seconds) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val response = CompletableDeferred<SpeechHttpResponse>()
        val transport = object : GoogleSpeechTransport {
            override suspend fun get(url: String, apiKey: String, timeoutMillis: Int): SpeechHttpResponse {
                started.complete(Unit)
                try { return response.await() } finally { cancelled.complete(Unit) }
            }
            override suspend fun post(url: String, apiKey: String, json: ByteArray, timeoutMillis: Int): SpeechHttpResponse = error("No billable work in Settings")
        }
        val memory = memory()
        val vm = SettingsViewModel(repository(memory, transport))
        try {
            vm.uiState.first { !it.loading }
            vm.reloadVoices()
            started.await()
            vm.keyChanged("new-synthetic-key")
            cancelled.await()
            response.complete(SpeechHttpResponse(200, "{\"voices\":[]}".toByteArray()))
            assertTrue(vm.flush())
            assertTrue(vm.uiState.value.voiceCatalog.voices.isEmpty())
            assertNull(memory.durable(PreferenceFile.SETTINGS).getString(SettingsPreferenceKeys.VOICE_CACHE, null))
        } finally { vm.viewModelScopeForTestClear(); Dispatchers.resetMain() }
    }
    private fun test(block: suspend TestScope.(MemoryAppPreferenceStorage, SettingsViewModel) -> Unit) = runTest(timeout = 20.seconds) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val storage = memory()
        val vm = SettingsViewModel(repository(storage))
        try { vm.uiState.first { !it.loading }; block(storage, vm) }
        finally { vm.viewModelScopeForTestClear(); Dispatchers.resetMain() }
    }
    private fun memory() = MemoryAppPreferenceStorage(initialSettings = PreferenceValues(mapOf(
        GoogleCloudCredential.PREF to "old-ciphertext", SettingsPreferenceKeys.GOOGLE_VOICE to VOICE.name,
        SettingsPreferenceKeys.VOICE_CACHE to encodeVoices(listOf(VOICE)),
    )))
    private fun repository(storage: AppPreferenceStorage, transport: GoogleSpeechTransport = object : GoogleSpeechTransport {
        override suspend fun get(url: String, apiKey: String, timeoutMillis: Int): SpeechHttpResponse = error("Unexpected network request")
        override suspend fun post(url: String, apiKey: String, json: ByteArray, timeoutMillis: Int): SpeechHttpResponse = error("Unexpected billable work")
    }) = SettingsRepository(storage, ReusableAudioCache(kotlin.io.path.createTempDirectory("settings-cache").toFile()),
        encrypt = { "encrypted-fixture" }, credential = { Result.success("existing-synthetic-key") }, transport = transport)
    companion object { val VOICE = CloudVoice("ja-JP-Chirp3-HD-Aoede", "Aoede", listOf("ja-JP"), 24_000) }
}

/** Use the actual ViewModelStore lifecycle rather than calling protected callbacks by hand. */
internal fun androidx.lifecycle.ViewModel.viewModelScopeForTestClear() {
    val store = androidx.lifecycle.ViewModelStore()
    store.put("test", this)
    store.clear()
}
