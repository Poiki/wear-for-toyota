package com.poiki.toyotawear

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Small strings encrypted with an AES-GCM key that never leaves the Android Keystore.
 * A [strict] vault (the saved password) uses a 256-bit key kept in StrongBox when the watch has one, usable only
 * while the watch is unlocked (when it has a lock screen); if the Keystore refuses, [write] throws and nothing is saved.
 */
internal class Vault(context: Context, private val alias: String = "toyota-vault", private val strict: Boolean = false) {
    private val prefs = context.getSharedPreferences("vault", Context.MODE_PRIVATE)

    private val key: SecretKey by lazy {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey) ?: try {
            generate(strongBox = strict)
        } catch (_: StrongBoxUnavailableException) {
            generate(strongBox = false)
        }
    }

    private fun generate(strongBox: Boolean): SecretKey = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        if (strict) spec.setKeySize(256).setUnlockedDeviceRequired(true).setIsStrongBoxBacked(strongBox)
        init(spec.build())
        generateKey()
    }

    /** Whether [name] is stored, without touching the Keystore. */
    fun has(name: String) = prefs.contains(name)

    fun read(name: String): String? = prefs.getString(name, null)?.let { stored ->
        runCatching {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes, 0, IV_BYTES))
            String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES))
        }.getOrNull()
    }

    fun write(name: String, value: String?) {
        if (value == null) {
            prefs.edit().remove(name).apply()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val blob = cipher.iv + cipher.doFinal(value.toByteArray())
        prefs.edit().putString(name, Base64.encodeToString(blob, Base64.NO_WRAP)).apply()
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}
