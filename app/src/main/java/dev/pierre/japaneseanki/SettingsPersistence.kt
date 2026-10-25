package dev.pierre.japaneseanki

import android.content.SharedPreferences
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

/** SharedPreferences adapter; the API key always goes through [GoogleCloudCredential]. */
internal class SharedPreferencesSettingsPersistence(
    private val preferences: SharedPreferences,
) : SettingsPersistence {
    override fun load(): LoadedSettings {
        val key = GoogleCloudCredential.load(preferences)
        val encodedVoices = preferences.getString(SettingsPreferenceKeys.VOICE_CACHE, null)
        return LoadedSettings(
            values = SettingsValues(
                modelId = preferences.getLong(SettingsPreferenceKeys.MODEL_ID, -1L).takeIf { it > 0L },
                deckId = preferences.getLong(SettingsPreferenceKeys.DECK_ID, -1L).takeIf { it > 0L },
                defaultTags = preferences.getString(SettingsPreferenceKeys.DEFAULT_TAGS, "").orEmpty(),
                voiceName = preferences.getString(SettingsPreferenceKeys.GOOGLE_VOICE, "").orEmpty(),
            ),
            googleKey = key.getOrNull().orEmpty(),
            credentialUnreadable = key.isFailure,
            cachedVoices = decodeVoices(encodedVoices),
            cachedVoiceListPresent = !encodedVoices.isNullOrBlank(),
        )
    }

    override fun saveValues(values: SettingsValues): Boolean = preferences.edit()
        .putLong(SettingsPreferenceKeys.MODEL_ID, values.modelId ?: -1L)
        .putLong(SettingsPreferenceKeys.DECK_ID, values.deckId ?: -1L)
        .putString(SettingsPreferenceKeys.DEFAULT_TAGS, values.defaultTags)
        .putString(SettingsPreferenceKeys.GOOGLE_VOICE, values.voiceName)
        .commit()

    override fun saveGoogleKey(key: String): Boolean = GoogleCloudCredential.write(preferences, key)

    override fun saveVoiceCache(voices: List<CloudVoice>): Boolean = preferences.edit()
        .putString(SettingsPreferenceKeys.VOICE_CACHE, encodeVoices(voices))
        .commit()
}
