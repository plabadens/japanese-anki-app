package dev.pierre.japaneseanki

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts optional user credentials with a non-exportable Android Keystore key. */
internal object EncryptedSecretStore {
    private const val KEY_ALIAS = "japanese-anki.credentials"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12

    fun encrypt(secret: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(secret.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(
            ByteBuffer.allocate(cipher.iv.size + encrypted.size).put(cipher.iv).put(encrypted).array(),
            Base64.NO_WRAP,
        )
    }

    fun decrypt(encoded: String?): String {
        if (encoded.isNullOrBlank()) return ""
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        require(payload.size > IV_SIZE) { "Encrypted credential is incomplete" }
        val iv = payload.copyOfRange(0, IV_SIZE)
        val encrypted = payload.copyOfRange(IV_SIZE, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
    }

    fun removeKey() {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (store.containsAlias(KEY_ALIAS)) store.deleteEntry(KEY_ALIAS)
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }
}
