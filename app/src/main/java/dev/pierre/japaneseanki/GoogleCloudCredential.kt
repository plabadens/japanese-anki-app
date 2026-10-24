package dev.pierre.japaneseanki

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

    /** Decrypts the shared key; failure means the saved ciphertext cannot be used. */
    fun load(settings: PreferenceValues): Result<String?> = runCatching {
        EncryptedSecretStore.decrypt(settings.getString(PREF, null)).trim().ifEmpty { null }
    }

    /** The value handed to both Google adapters; null when unset or unreadable. */
    fun read(settings: PreferenceValues): String? = load(settings).getOrNull()

    /** Stores or clears the key. Called by the settings persistence worker, never composition. */
    fun write(storage: AppPreferenceStorage, key: String): Boolean {
        val trimmed = key.trim()
        val saved = storage.persistBlocking(
            PreferenceFile.SETTINGS,
            PreferenceChanges().putString(PREF, if (trimmed.isEmpty()) null else EncryptedSecretStore.encrypt(trimmed)),
        )
        if (saved && trimmed.isEmpty()) runCatching { EncryptedSecretStore.removeKey() }
        return saved
    }

    /** Removes the obsolete ciphertext without touching drafts, media, or settings. */
    fun removeObsoleteOpenRouterStorage(storage: AppPreferenceStorage): Boolean =
        !storage.settings.value.contains(OBSOLETE_OPENROUTER_PREF) || storage.persistBlocking(
            PreferenceFile.SETTINGS,
            PreferenceChanges().remove(OBSOLETE_OPENROUTER_PREF),
        )
}
