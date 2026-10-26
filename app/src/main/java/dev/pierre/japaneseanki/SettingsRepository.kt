package dev.pierre.japaneseanki

import dev.pierre.japaneseanki.speech.CloudVoice
import dev.pierre.japaneseanki.speech.GoogleChirpSpeechProvider
import dev.pierre.japaneseanki.speech.GoogleSpeechTransport
import dev.pierre.japaneseanki.speech.HttpGoogleSpeechTransport
import dev.pierre.japaneseanki.speech.decodeVoices
import dev.pierre.japaneseanki.speech.encodeVoices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal enum class SettingsProblem { Save, Load, InvalidKey, Voices, Cache }
internal data class AudioConfiguration(val voice: CloudVoice? = null, val credentialStored: Boolean = false)
internal data class SavedConfiguration(val values: SettingsValues = SettingsValues(), val audio: AudioConfiguration = AudioConfiguration(), val onboarded: Boolean = false)
internal class LoadedSettings(val values: SettingsValues, val googleKey: String, val credentialUnreadable: Boolean,
    val cachedVoices: List<CloudVoice>, val cachedVoiceListPresent: Boolean)
internal object SettingsPreferenceKeys {
    const val MODEL_ID = "model_id"
    const val DECK_ID = "deck_id"
    const val DEFAULT_TAGS = "default_tags"
    const val GOOGLE_VOICE = "google_tts_voice"
    const val VOICE_CACHE = "google_voice_cache"
}

/** One durable source. Only the Android Keystore and HTTP boundaries are injectable. */
internal class SettingsRepository(
    private val storage: AppPreferenceStorage,
    private val audioCache: ReusableAudioCache,
    private val encrypt: (String) -> String = EncryptedSecretStore::encrypt,
    private val credential: (PreferenceValues) -> Result<String?> = GoogleCloudCredential::load,
    private val transport: GoogleSpeechTransport = HttpGoogleSpeechTransport(),
) {
    val configuration = storage.settings.map { saved ->
        val values = values(saved)
        SavedConfiguration(values, AudioConfiguration(
            decodeVoices(saved.getString(SettingsPreferenceKeys.VOICE_CACHE, null)).firstOrNull { it.name == values.voiceName },
            saved.contains(GoogleCloudCredential.PREF),
        ), saved.getBoolean("onboarding_complete", false))
    }
    private fun values(saved: PreferenceValues) = SettingsValues(
        saved.getLong(SettingsPreferenceKeys.MODEL_ID, -1).takeIf { it > 0 },
        saved.getLong(SettingsPreferenceKeys.DECK_ID, -1).takeIf { it > 0 },
        saved.getString(SettingsPreferenceKeys.DEFAULT_TAGS, "").orEmpty(),
        saved.getString(SettingsPreferenceKeys.GOOGLE_VOICE, "").orEmpty(),
    )
    suspend fun load(): LoadedSettings = withContext(Dispatchers.IO) {
        storage.load()
        val saved = storage.readBlocking(PreferenceFile.SETTINGS)
        val key = credential(saved)
        val voices = saved.getString(SettingsPreferenceKeys.VOICE_CACHE, null)
        LoadedSettings(values(saved), key.getOrNull().orEmpty(), key.isFailure, decodeVoices(voices), !voices.isNullOrBlank())
    }
    suspend fun save(values: SettingsValues, editedKey: String?): SettingsProblem? = withContext(Dispatchers.IO) {
        if (editedKey != null && editedKey.trim().any(Char::isWhitespace)) return@withContext SettingsProblem.InvalidKey
        try {
            val changes = PreferenceChanges()
                .putLong(SettingsPreferenceKeys.MODEL_ID, values.modelId ?: -1)
                .putLong(SettingsPreferenceKeys.DECK_ID, values.deckId ?: -1)
                .putString(SettingsPreferenceKeys.DEFAULT_TAGS, values.defaultTags)
                .putString(SettingsPreferenceKeys.GOOGLE_VOICE, values.voiceName)
            if (editedKey != null) {
                val key = editedKey.trim()
                changes.putString(GoogleCloudCredential.PREF, if (key.isEmpty()) null else encrypt(key))
                    .remove(SettingsPreferenceKeys.VOICE_CACHE)
            }
            if (storage.persist(PreferenceFile.SETTINGS, changes)) null else SettingsProblem.Save
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { SettingsProblem.Save }
    }
    suspend fun voices(key: String): List<CloudVoice> = withContext(Dispatchers.IO) {
        GoogleChirpSpeechProvider(credential = { key }, transport = transport).listVoices()
    }
    suspend fun cacheVoices(voices: List<CloudVoice>): Boolean = storage.persist(
        PreferenceFile.SETTINGS, PreferenceChanges().putString(SettingsPreferenceKeys.VOICE_CACHE, encodeVoices(voices)),
    )
    suspend fun clearCache() = withContext(Dispatchers.IO) { audioCache.clear() }
    suspend fun finishSetup(): Boolean = storage.persist(PreferenceFile.SETTINGS, PreferenceChanges().putBoolean("onboarding_complete", true))
}
