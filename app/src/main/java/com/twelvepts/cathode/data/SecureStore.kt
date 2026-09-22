package com.twelvepts.cathode.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureStore(context: Context, preferenceName: String, private val keyAlias: String) {
    private val preferences = context.applicationContext.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)

    fun putString(key: String, value: String?) {
        if (value == null) {
            remove(key)
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val payload = ByteArray(cipher.iv.size + encrypted.size)
        cipher.iv.copyInto(payload)
        encrypted.copyInto(payload, cipher.iv.size)
        preferences.edit().putString(key, Base64.encodeToString(payload, Base64.NO_WRAP)).apply()
    }

    fun getString(key: String): String? = runCatching {
        val payload = Base64.decode(preferences.getString(key, null) ?: return null, Base64.NO_WRAP)
        require(payload.size > IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, payload.copyOfRange(0, IV_BYTES)))
        String(cipher.doFinal(payload.copyOfRange(IV_BYTES, payload.size)), Charsets.UTF_8)
    }.getOrNull()

    fun remove(key: String) { preferences.edit().remove(key).apply() }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}
