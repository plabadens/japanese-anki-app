package dev.pierre.japaneseanki

import dev.pierre.japaneseanki.speech.CloudVoice
import dev.pierre.japaneseanki.speech.decodeVoices
import dev.pierre.japaneseanki.speech.encodeVoices

/** Loaded values keep the secret separate from the ordinary settings data class/toString. */
internal class LoadedSettings(
    val values: SettingsValues,
    val googleKey: String,
    val credentialUnreadable: Boolean,
    val cachedVoices: List<CloudVoice>,
    val cachedVoiceListPresent: Boolean,
)

/** Narrow boundary so persistence failures and scheduling can be tested without Android or keys. */
internal interface SettingsPersistence {
    fun load(): LoadedSettings
    fun saveValues(values: SettingsValues): Boolean
    fun saveGoogleKey(key: String): Boolean
    fun saveVoiceCache(voices: List<CloudVoice>): Boolean
}

internal object SettingsPreferenceKeys {
    const val MODEL_ID = "model_id"
    const val DECK_ID = "deck_id"
    const val DEFAULT_TAGS = "default_tags"
    const val GOOGLE_VOICE = "google_tts_voice"
    const val VOICE_CACHE = "google_voice_cache"
}

/** App adapter; all actual preference access remains in the storage boundary. */
internal class PreferenceBackedSettingsPersistence(
    private val storage: AppPreferenceStorage,
) : SettingsPersistence {
    override fun load(): LoadedSettings {
        val settings = storage.settings.value
        val key = GoogleCloudCredential.load(settings)
        val encodedVoices = settings.getString(SettingsPreferenceKeys.VOICE_CACHE, null)
        return LoadedSettings(
            values = SettingsValues(
                modelId = settings.getLong(SettingsPreferenceKeys.MODEL_ID, -1L).takeIf { it > 0L },
                deckId = settings.getLong(SettingsPreferenceKeys.DECK_ID, -1L).takeIf { it > 0L },
                defaultTags = settings.getString(SettingsPreferenceKeys.DEFAULT_TAGS, "").orEmpty(),
                voiceName = settings.getString(SettingsPreferenceKeys.GOOGLE_VOICE, "").orEmpty(),
            ),
            googleKey = key.getOrNull().orEmpty(),
            credentialUnreadable = key.isFailure,
            cachedVoices = decodeVoices(encodedVoices),
            cachedVoiceListPresent = !encodedVoices.isNullOrBlank(),
        )
    }

    override fun saveValues(values: SettingsValues): Boolean = storage.persistBlocking(
        PreferenceFile.SETTINGS,
        PreferenceChanges()
            .putLong(SettingsPreferenceKeys.MODEL_ID, values.modelId ?: -1L)
            .putLong(SettingsPreferenceKeys.DECK_ID, values.deckId ?: -1L)
            .putString(SettingsPreferenceKeys.DEFAULT_TAGS, values.defaultTags)
            .putString(SettingsPreferenceKeys.GOOGLE_VOICE, values.voiceName),
    )

    override fun saveGoogleKey(key: String): Boolean = GoogleCloudCredential.write(storage, key)

    override fun saveVoiceCache(voices: List<CloudVoice>): Boolean = runCatching {
        storage.update(
            PreferenceFile.SETTINGS,
            PreferenceChanges().putString(SettingsPreferenceKeys.VOICE_CACHE, encodeVoices(voices)),
        )
        true
    }.getOrDefault(false)
}
