package dev.pierre.japaneseanki

import android.content.SharedPreferences

/**
 * The one Google Cloud API key shared by sentence speech and translation (SPEC §4.7, §11).
 *
 * The preference id predates the shared key: it stored the Text-to-Speech credential, and reusing
 * it means an already-saved key keeps working without re-entry. The stored value is Android
 * Keystore ciphertext; plaintext never reaches disk, logs, URLs, or an error message.
 */
internal object GoogleCloudCredential {
    const val PREF = "google_tts_key_encrypted"

    /** Obsolete pre-shared-key Translation credential. Deleted, never reinterpreted as Google. */
    const val OBSOLETE_OPENROUTER_PREF = "openrouter_key_encrypted"

    /** Cheap presence check for UI state; does not decrypt. */
    fun isConfigured(settings: SharedPreferences): Boolean = settings.contains(PREF)

    /** Decrypts the shared key; failure means the saved ciphertext cannot be used. */
    fun load(settings: SharedPreferences): Result<String?> = runCatching {
        EncryptedSecretStore.decrypt(settings.getString(PREF, null)).trim().ifEmpty { null }
    }

    /** The value handed to both Google adapters; null when unset or unreadable. */
    fun read(settings: SharedPreferences): String? = load(settings).getOrNull()

    /** Stores or clears the key. Makes no network request of any kind. */
    fun write(settings: SharedPreferences, key: String): Boolean {
        val trimmed = key.trim()
        val edit = settings.edit()
        if (trimmed.isEmpty()) edit.remove(PREF) else edit.putString(PREF, EncryptedSecretStore.encrypt(trimmed))
        val saved = edit.commit()
        if (saved && trimmed.isEmpty()) runCatching { EncryptedSecretStore.removeKey() }
        return saved
    }

    /** Removes the obsolete OpenRouter ciphertext without touching drafts, media, or settings. */
    fun removeObsoleteOpenRouterStorage(settings: SharedPreferences): Boolean =
        !settings.contains(OBSOLETE_OPENROUTER_PREF) || settings.edit().remove(OBSOLETE_OPENROUTER_PREF).commit()
}
