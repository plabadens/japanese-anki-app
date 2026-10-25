package dev.pierre.japaneseanki

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider

import dev.pierre.japaneseanki.speech.CloudVoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsCoordinatorTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun editsAreDebouncedAndOnlyLatestValuesArePersisted() {
        val persistence = FakeSettingsPersistence()
        val scheduler = FakeSettingsScheduler()
        val coordinator = SettingsCoordinator(persistence, scheduler, debounceMillis = 25)
        coordinator.open()

        coordinator.updateValues(SettingsValues(modelId = 4, defaultTags = "first"))
        coordinator.updateValues(SettingsValues(modelId = 4, deckId = 9, defaultTags = "second"))
        assertEquals(SettingsValues(), persistence.savedValues)

        scheduler.runPending()

        assertEquals(SettingsValues(modelId = 4, deckId = 9, defaultTags = "second"), persistence.savedValues)
    }

    @Test
    fun backFlushPersistsPendingCredentialAndSettingsAndCancelsDebounce() {
        val persistence = FakeSettingsPersistence()
        val scheduler = FakeSettingsScheduler()
        val coordinator = SettingsCoordinator(persistence, scheduler, debounceMillis = 25)
        coordinator.open()
        coordinator.updateValues(SettingsValues(deckId = 8, defaultTags = "one two"))
        coordinator.updateGoogleKey("  key-without-whitespace  ")
        assertFalse(coordinator.isCredentialStored())

        assertTrue(coordinator.flush().isSuccess)
        assertTrue(coordinator.isCredentialStored())
        scheduler.runPending()

        assertEquals("key-without-whitespace", persistence.savedCredential)
        assertEquals(SettingsValues(deckId = 8, defaultTags = "one two"), persistence.savedValues)
        assertNull(coordinator.currentSaveError())
    }

    @Test
    fun persistenceFailureIsVisibleAndPendingValuesRemainRetryable() {
        val persistence = FakeSettingsPersistence().apply { failValues = true }
        val scheduler = FakeSettingsScheduler()
        val results = mutableListOf<Result<Unit>>()
        val coordinator = SettingsCoordinator(persistence, scheduler, onSaveResult = results::add)
        coordinator.open()
        coordinator.updateValues(SettingsValues(modelId = 3))

        scheduler.runPending()

        assertTrue(results.any { it.isFailure })
        assertEquals(context.getString(R.string.save_failed), coordinator.currentSaveError())
        persistence.failValues = false
        assertTrue(coordinator.flush().isSuccess)
        assertEquals(SettingsValues(modelId = 3), persistence.savedValues)
        assertNull(coordinator.currentSaveError())
    }

    @Test
    fun whitespaceCredentialIsNotPersistedAndReturnsSafeError() {
        val persistence = FakeSettingsPersistence()
        val coordinator = SettingsCoordinator(persistence, FakeSettingsScheduler())
        coordinator.open()
        coordinator.updateGoogleKey("not a key")

        val result = coordinator.flush()

        assertTrue(result.isFailure)
        assertEquals("", persistence.savedCredential)
        assertFalse(coordinator.isCredentialStored())
        assertEquals("The Google Cloud key contains whitespace.", coordinator.currentSaveError())
        assertFalse(coordinator.currentSaveError()!!.contains("not a key"))
    }

    @Test
    fun secureCredentialPersistenceFailureIsVisibleAndCanBeRetried() {
        val persistence = FakeSettingsPersistence().apply { failKey = true }
        val scheduler = FakeSettingsScheduler()
        val coordinator = SettingsCoordinator(persistence, scheduler)
        coordinator.open()
        coordinator.updateGoogleKey("test-key-only")

        scheduler.runPending()

        assertEquals("", persistence.savedCredential)
        assertEquals(context.getString(R.string.save_failed), coordinator.currentSaveError())
        assertFalse(coordinator.isCredentialStored())
        persistence.failKey = false
        assertTrue(coordinator.flush().isSuccess)
        assertTrue(coordinator.isCredentialStored())
        assertEquals("test-key-only", persistence.savedCredential)
        assertNull(coordinator.currentSaveError())
    }

    @Test
    fun keyChangeFencesInflightVoiceListAndLeavesCachedCatalogMarkedStale() {
        val cached = voice("ja-JP-Chirp3-HD-Aoede")
        val persistence = FakeSettingsPersistence(
            initial = LoadedSettings(
                values = SettingsValues(voiceName = cached.name),
                googleKey = "old-key",
                credentialUnreadable = false,
                cachedVoices = listOf(cached),
                cachedVoiceListPresent = true,
            ),
        )
        val coordinator = SettingsCoordinator(persistence, FakeSettingsScheduler())
        val opened = coordinator.open()
        val oldRequest = opened.initialVoiceRequest
        assertNotNull(oldRequest)
        assertEquals(VoiceCatalogProvenance.CACHED, coordinator.currentVoiceCatalog().provenance)

        coordinator.updateGoogleKey("new-key")

        assertFalse(coordinator.completeVoiceLoad(oldRequest!!, voices = emptyList()))
        assertEquals(VoiceCatalogProvenance.CACHED, coordinator.currentVoiceCatalog().provenance)
        assertFalse(coordinator.currentVoiceCatalog().loading)
        assertEquals(cached.name, coordinator.currentValues().voiceName)

        val currentRequest = coordinator.reloadVoices()
        assertNotNull(currentRequest)
        assertEquals("new-key", currentRequest!!.credential)
        assertTrue(coordinator.completeVoiceLoad(currentRequest, voices = listOf(voice("ja-JP-Chirp3-HD-Kore"))))
        assertEquals(VoiceCatalogProvenance.AUTHORITATIVE, coordinator.currentVoiceCatalog().provenance)
        assertTrue(coordinator.currentVoiceCatalog().selectedVoiceMissing(cached.name))
        assertFalse(coordinator.canSynthesizeSentence())
    }

    @Test
    fun newerReloadFencesEarlierRequestForSameCredential() {
        val coordinator = SettingsCoordinator(
            FakeSettingsPersistence(initial = loaded(key = "valid-key")),
            FakeSettingsScheduler(),
        )
        val initial = coordinator.open().initialVoiceRequest!!
        val newer = coordinator.reloadVoices()!!

        assertFalse(coordinator.completeVoiceLoad(initial, voices = listOf(voice("old-result"))))
        assertTrue(coordinator.completeVoiceLoad(newer, voices = listOf(voice("new-result"))))
        assertEquals("new-result", coordinator.currentVoiceCatalog().voices.single().name)
    }

    @Test
    fun removedVoiceBlocksSynthesisOnlyAfterAuthoritativeListSaysItIsMissing() {
        val selected = voice("ja-JP-Chirp3-HD-Aoede")
        val coordinator = SettingsCoordinator(
            FakeSettingsPersistence(
                initial = loaded(
                    key = "valid-key",
                    values = SettingsValues(voiceName = selected.name),
                    cachedVoices = listOf(selected),
                ),
            ),
            FakeSettingsScheduler(),
        )
        val opened = coordinator.open()

        assertTrue(coordinator.canSynthesizeSentence()) // cached metadata is not authoritative evidence
        assertTrue(coordinator.completeVoiceLoad(opened.initialVoiceRequest!!, voices = emptyList()))
        assertTrue(coordinator.currentVoiceCatalog().selectedVoiceMissing(selected.name))
        assertFalse(coordinator.canSynthesizeSentence())
    }

    @Test
    fun openingWithoutKeyDoesNotStartVoiceRequestAndTypingDoesNotRequestOne() {
        val coordinator = SettingsCoordinator(FakeSettingsPersistence(), FakeSettingsScheduler())
        val opened = coordinator.open()
        assertNull(opened.initialVoiceRequest)

        coordinator.updateGoogleKey("typed-key")

        assertFalse(coordinator.currentVoiceCatalog().loading)
    }

    @Test
    fun onboardingRequiresSavedKeyAndAuthoritativeSelectedVoice() {
        val persistence = FakeSettingsPersistence()
        val scheduler = FakeSettingsScheduler()
        val coordinator = SettingsCoordinator(persistence, scheduler)
        assertNull(coordinator.open().initialVoiceRequest)
        assertFalse(coordinator.canFinishOnboarding())

        coordinator.updateGoogleKey("configured-key")
        coordinator.updateValues(SettingsValues(voiceName = "ja-JP-Chirp3-HD-Aoede"))
        assertFalse(coordinator.canFinishOnboarding())
        val request = coordinator.reloadVoices()!!
        assertTrue(coordinator.completeVoiceLoad(request, voices = listOf(voice("ja-JP-Chirp3-HD-Aoede"))))
        assertFalse(coordinator.canFinishOnboarding()) // changes still need secure autosave

        assertTrue(coordinator.flush().isSuccess)

        assertTrue(coordinator.canFinishOnboarding())
        assertEquals("configured-key", persistence.savedCredential)
    }

    @Test
    fun selectedVoiceSurvivesSettingsBackFlushAndReopen() {
        val persistence = FakeSettingsPersistence()
        val coordinator = SettingsCoordinator(persistence, FakeSettingsScheduler())
        coordinator.open()
        coordinator.updateGoogleKey("test-key")
        val selected = voice("ja-JP-Chirp3-HD-Aoede")
        val request = coordinator.reloadVoices()!!
        assertTrue(coordinator.completeVoiceLoad(request, voices = listOf(selected)))
        coordinator.updateValues(coordinator.currentValues().copy(voiceName = selected.name))

        assertTrue(coordinator.flush().isSuccess)

        val reopened = SettingsCoordinator(persistence, FakeSettingsScheduler())
        val opened = reopened.open()
        assertEquals(selected.name, opened.values.voiceName)
        assertEquals(VoiceCatalogProvenance.CACHED, opened.voiceCatalog.provenance)
        assertNotNull(opened.initialVoiceRequest)
    }

    @Test
    fun setupReadinessReportsEveryMissingRequirementAndTheAllReadyCase() {
        fun readiness(
            audioCredentialAvailable: Boolean = true,
            selectedVoiceName: String = "ja-JP-Chirp3-HD-Aoede",
            ankiDroidInstalled: Boolean = true,
            ankiPermissionGranted: Boolean = true,
            ankiSetupLoaded: Boolean = true,
            selectedCompatibleNoteType: Boolean = true,
            selectedDeckAvailable: Boolean = true,
            dictionaryReady: Boolean = true,
        ) = SettingsCoordinator.setupReadiness(
            audioCredentialAvailable = audioCredentialAvailable,
            selectedVoiceName = selectedVoiceName,
            ankiDroidInstalled = ankiDroidInstalled,
            ankiPermissionGranted = ankiPermissionGranted,
            ankiSetupLoaded = ankiSetupLoaded,
            selectedCompatibleNoteType = selectedCompatibleNoteType,
            selectedDeckAvailable = selectedDeckAvailable,
            dictionaryReady = dictionaryReady,
        )

        listOf(
            readiness(audioCredentialAvailable = false),
            readiness(selectedVoiceName = ""),
        ).forEach { audioMissing ->
            assertFalse(audioMissing.audioReady)
            assertTrue(audioMissing.blockers.contains("Audio setup is incomplete."))
        }

        listOf(
            readiness(ankiDroidInstalled = false),
            readiness(ankiPermissionGranted = false),
            readiness(ankiSetupLoaded = false),
            readiness(selectedCompatibleNoteType = false),
            readiness(selectedDeckAvailable = false),
        ).forEach { ankiMissing ->
            assertFalse(ankiMissing.ankiReady)
            assertTrue(ankiMissing.blockers.contains("AnkiDroid setup is incomplete."))
        }

        val dictionaryMissing = readiness(dictionaryReady = false)
        assertFalse(dictionaryMissing.dictionaryReady)
        assertEquals(listOf("Dictionary is not ready."), dictionaryMissing.blockers)

        val allReady = readiness()
        assertTrue(allReady.audioReady)
        assertTrue(allReady.ankiReady)
        assertTrue(allReady.dictionaryReady)
        assertTrue(allReady.blockers.isEmpty())
    }

    private fun loaded(
        key: String,
        values: SettingsValues = SettingsValues(),
        cachedVoices: List<CloudVoice> = emptyList(),
    ) = LoadedSettings(values, key, false, cachedVoices, cachedVoices.isNotEmpty())

    private fun voice(name: String) = CloudVoice(name, name.substringAfterLast('-'), listOf("ja-JP"), 24_000)
}

private class FakeSettingsPersistence(
    private val initial: LoadedSettings = LoadedSettings(SettingsValues(), "", false, emptyList(), false),
) : SettingsPersistence {

    var failValues = false
    var failKey = false
    var savedValues = initial.values
    var savedCredential = initial.googleKey
    private var cachedVoices = initial.cachedVoices
    private var cachedVoiceListPresent = initial.cachedVoiceListPresent

    override fun load(): LoadedSettings = LoadedSettings(
        values = savedValues,
        googleKey = savedCredential,
        credentialUnreadable = initial.credentialUnreadable,
        cachedVoices = cachedVoices,
        cachedVoiceListPresent = cachedVoiceListPresent,
    )

    override fun saveValues(values: SettingsValues): Boolean {
        if (failValues) return false
        savedValues = values
        return true
    }

    override fun saveGoogleKey(key: String): Boolean {
        if (failKey) return false
        savedCredential = key.trim()
        return true
    }

    override fun saveVoiceCache(voices: List<CloudVoice>): Boolean {
        cachedVoices = voices.toList()
        cachedVoiceListPresent = true
        return true
    }
}

private class FakeSettingsScheduler : SettingsWriteScheduler {
    private data class Entry(var cancelled: Boolean, val action: () -> Unit)
    private val entries = mutableListOf<Entry>()

    override fun schedule(delayMillis: Long, action: () -> Unit): CancelScheduledSettingsWrite {
        val entry = Entry(false, action)
        entries += entry
        return CancelScheduledSettingsWrite { entry.cancelled = true }
    }

    fun runPending() {
        val pending = entries.filterNot { it.cancelled }
        entries.clear()
        pending.forEach { it.action() }
    }
}
