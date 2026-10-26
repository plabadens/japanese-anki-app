package dev.pierre.japaneseanki

import dev.pierre.japaneseanki.speech.CloudVoice
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

internal fun interface CancelScheduledSettingsWrite {
    fun cancel()
}

internal fun interface SettingsWriteScheduler {
    fun schedule(delayMillis: Long, action: () -> Unit): CancelScheduledSettingsWrite
}

/** One shared daemon scheduler; disk I/O never runs on the Compose thread. */
internal object DefaultSettingsWriteScheduler : SettingsWriteScheduler {
    private val executor = Executors.newSingleThreadScheduledExecutor { action ->
        Thread(action, "settings-autosave").apply { isDaemon = true }
    }

    override fun schedule(delayMillis: Long, action: () -> Unit): CancelScheduledSettingsWrite {
        val future: ScheduledFuture<*> = executor.schedule(action, delayMillis, TimeUnit.MILLISECONDS)
        return CancelScheduledSettingsWrite { future.cancel(false) }
    }
}

/**
 * Settings lifecycle and persistence coordinator. Each edit replaces one debounced write. Back must
 * call [flush] and leave the screen open if it fails. Voice requests carry monotonically fenced
 * credential/request revisions; only the newest result for the current key can become authoritative.
 */
internal class SettingsCoordinator(
    private val persistence: SettingsPersistence,
    private val scheduler: SettingsWriteScheduler = DefaultSettingsWriteScheduler,
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
    private val onSaveResult: (Result<Unit>) -> Unit = {},
) {
    private val lock = Any()
    private var opened = false
    private var values = SettingsValues()
    private var credential = ""
    private var persistedValues = SettingsValues()
    private var persistedCredential = ""
    private var valuesDirty = false
    private var credentialDirty = false
    private var pendingWrite: CancelScheduledSettingsWrite? = null
    private var saveError: String? = null
    private var credentialRevision = 0L
    private var requestRevision = 0L
    private var latestRequest: VoiceRequestToken? = null
    private var voiceCatalog = VoiceCatalogState()

    fun open(): SettingsOpenState = synchronized(lock) {
        check(!opened) { "A settings coordinator can be opened only once." }
        val loaded = persistence.load()
        opened = true
        values = loaded.values
        credential = loaded.googleKey
        persistedValues = loaded.values
        persistedCredential = loaded.googleKey.trim()
        voiceCatalog = VoiceCatalogState(
            voices = loaded.cachedVoices,
            provenance = if (loaded.cachedVoiceListPresent) VoiceCatalogProvenance.CACHED
                else VoiceCatalogProvenance.NONE,
        )
        val request = beginVoiceRequestLocked()
        SettingsOpenState(
            values = values,
            googleKey = credential,
            credentialUnreadable = loaded.credentialUnreadable,
            voiceCatalog = voiceCatalog,
            initialVoiceRequest = request,
        )
    }

    fun currentValues(): SettingsValues = synchronized(lock) {
        checkOpened()
        values
    }

    fun currentGoogleKey(): String = synchronized(lock) {
        checkOpened()
        credential
    }

    fun currentVoiceCatalog(): VoiceCatalogState = synchronized(lock) {
        checkOpened()
        voiceCatalog
    }

    fun currentSaveError(): String? = synchronized(lock) { saveError }

    fun isCredentialStored(): Boolean = synchronized(lock) {
        checkOpened()
        credential.isNotBlank() && !credentialDirty && saveError == null
    }

    fun updateValues(updated: SettingsValues) = synchronized(lock) {
        checkOpened()
        if (updated == values) return@synchronized
        values = updated
        valuesDirty = values != persistedValues
        saveError = null
        schedulePendingWriteLocked()
    }

    /** Key edits are held only in memory until secure persistence succeeds. */
    fun updateGoogleKey(updated: String) = synchronized(lock) {
        checkOpened()
        if (updated == credential) return@synchronized
        val previousCredential = credential.trim()
        credential = updated
        val normalizedCredential = updated.trim()
        credentialDirty = normalizedCredential != persistedCredential
        if (normalizedCredential != previousCredential) {
            credentialRevision++
            latestRequest = null
            requestRevision++
            voiceCatalog = voiceCatalog.copy(
                provenance = if (voiceCatalog.voices.isNotEmpty()) VoiceCatalogProvenance.STALE
                    else VoiceCatalogProvenance.NONE,
                loading = false,
                attempted = false,
                error = null,
            )
        }
        saveError = null
        schedulePendingWriteLocked()
    }

    /** Explicit Reload only. Opening the screen separately requests once when a usable key exists. */
    fun reloadVoices(): VoiceLoadRequest? = synchronized(lock) {
        checkOpened()
        if (saveError != null) return@synchronized null
        beginVoiceRequestLocked()
    }

    /** Returns false for stale results; stale responses cannot replace or cache the current list. */
    fun completeVoiceLoad(
        request: VoiceLoadRequest,
        voices: List<CloudVoice>? = null,
        safeError: String? = null,
    ): Boolean = synchronized(lock) {
        checkOpened()
        val token = request.token
        if (latestRequest != token || token.credentialRevision != credentialRevision) return@synchronized false
        latestRequest = null
        if (voices != null) {
            val cacheSaved = if (credentialDirty) {
                true
            } else {
                runCatching { persistence.saveVoiceCache(voices) }.getOrDefault(false)
            }
            voiceCatalog = VoiceCatalogState(
                voices = voices.toList(),
                provenance = VoiceCatalogProvenance.AUTHORITATIVE,
                loading = false,
                attempted = true,
                error = if (cacheSaved) null else "Voice list loaded, but its offline cache could not be saved.",
            )
        } else {
            voiceCatalog = voiceCatalog.copy(
                loading = false,
                attempted = true,
                error = safeError?.takeIf(String::isNotBlank) ?: "Voice listing failed.",
            )
        }
        true
    }

    /** A saved catalog stays usable until a refresh rejects the voice; onboarding still requires verification. */
    fun canSynthesizeSentence(): Boolean = synchronized(lock) {
        checkOpened()
        sentenceVoiceForSynthesis(
            credentialStored = credential.isNotBlank(),
            credentialUnreadable = false,
            voiceName = values.voiceName,
            catalog = voiceCatalog,
        ) != null
    }

    /** First-run completion requires a usable key and a voice verified against the current key. */
    fun canFinishOnboarding(): Boolean = synchronized(lock) {
        checkOpened()
        credential.isNotBlank() && values.voiceName.isNotBlank() &&
            !credentialDirty && !valuesDirty && saveError == null &&
            voiceCatalog.provenance == VoiceCatalogProvenance.AUTHORITATIVE &&
            voiceCatalog.voices.any { it.name == values.voiceName }
    }

    /** Synchronously drain pending edits before Back. Call from an I/O dispatcher. */
    fun flush(): Result<Unit> {
        val outcome = synchronized(lock) {
            checkOpened()
            pendingWrite?.cancel()
            pendingWrite = null
            persistPendingLocked()
        }
        onSaveResult(outcome)
        return outcome
    }

    private fun beginVoiceRequestLocked(): VoiceLoadRequest? {
        if (credential.isBlank()) return null
        val token = VoiceRequestToken(credentialRevision, ++requestRevision)
        latestRequest = token
        voiceCatalog = voiceCatalog.copy(loading = true, attempted = true, error = null)
        return VoiceLoadRequest(token, credential.trim())
    }

    private fun schedulePendingWriteLocked() {
        pendingWrite?.cancel()
        if (!valuesDirty && !credentialDirty) {
            pendingWrite = null
            return
        }
        pendingWrite = scheduler.schedule(debounceMillis) {
            val outcome = synchronized(lock) {
                pendingWrite = null
                persistPendingLocked()
            }
            onSaveResult(outcome)
        }
    }

    private fun persistPendingLocked(): Result<Unit> {
        if (!valuesDirty && !credentialDirty) {
            saveError = null
            return Result.success(Unit)
        }
        return try {
            if (valuesDirty) {
                check(persistence.saveValues(values)) { "Could not save your changes." }
                persistedValues = values
                valuesDirty = false
            }
            if (credentialDirty) {
                require(credential.trim().none(Char::isWhitespace)) {
                    "The Google Cloud key contains whitespace."
                }
                val keyChanged = credential.trim() != persistedCredential
                if (keyChanged) {
                    check(persistence.saveVoiceCache(emptyList())) { "Could not save your changes." }
                }
                check(persistence.saveGoogleKey(credential)) {
                    "Could not save your changes."
                }
                if (keyChanged && voiceCatalog.provenance == VoiceCatalogProvenance.AUTHORITATIVE) {
                    val cacheSaved = runCatching { persistence.saveVoiceCache(voiceCatalog.voices) }.getOrDefault(false)
                    if (!cacheSaved) {
                        voiceCatalog = voiceCatalog.copy(
                            error = "Voice list loaded, but its offline cache could not be saved.",
                        )
                    }
                }
                persistedCredential = credential.trim()
                credentialDirty = false
            }
            saveError = null
            Result.success(Unit)
        } catch (error: IllegalArgumentException) {
            val message = error.message?.takeIf {
                it == "The Google Cloud key contains whitespace."
            } ?: "Could not save your changes."
            saveError = message
            Result.failure(IllegalStateException(message))
        } catch (_: Exception) {
            val message = "Could not save your changes."
            saveError = message
            Result.failure(IllegalStateException(message))
        }
    }

    private fun checkOpened() = check(opened) { "Open settings before using its coordinator." }

    companion object {
        const val DEFAULT_DEBOUNCE_MILLIS = 350L

        fun setupReadiness(
            audioCredentialAvailable: Boolean,
            selectedVoiceName: String,
            ankiDroidInstalled: Boolean,
            ankiPermissionGranted: Boolean,
            ankiSetupLoaded: Boolean,
            selectedCompatibleNoteType: Boolean,
            selectedDeckAvailable: Boolean,
            dictionaryReady: Boolean,
        ): SetupReadiness {
            val audioReady = audioCredentialAvailable && selectedVoiceName.isNotBlank()
            val ankiReady = ankiDroidInstalled && ankiPermissionGranted && ankiSetupLoaded &&
                selectedCompatibleNoteType && selectedDeckAvailable
            val blockers = buildList {
                if (!audioReady) add("Audio setup is incomplete.")
                if (!ankiReady) add("AnkiDroid setup is incomplete.")
                if (!dictionaryReady) add("Dictionary is not ready.")
            }
            return SetupReadiness(audioReady, ankiReady, dictionaryReady, blockers)
        }
    }
}

internal data class SetupReadiness(
    val audioReady: Boolean,
    val ankiReady: Boolean,
    val dictionaryReady: Boolean,
    val blockers: List<String>,
)

/** No data-class toString: the request contains the in-memory key used for one voice-list GET. */
internal class VoiceLoadRequest internal constructor(
    internal val token: VoiceRequestToken,
    val credential: String,
)

internal data class VoiceRequestToken(val credentialRevision: Long, val requestRevision: Long)

/** No secret fields are included in this view state. */
internal class SettingsOpenState(
    val values: SettingsValues,
    val googleKey: String,
    val credentialUnreadable: Boolean,
    val voiceCatalog: VoiceCatalogState,
    val initialVoiceRequest: VoiceLoadRequest?,
)

enum class AppRoute { Capture, Card }

internal fun visibleRouteForAnalyzer(
    requestedRoute: AppRoute,
    analyzerReady: Boolean,
    frozenCardActive: Boolean,
): AppRoute = when {
    !analyzerReady -> AppRoute.Capture
    frozenCardActive -> AppRoute.Card
    else -> requestedRoute
}
