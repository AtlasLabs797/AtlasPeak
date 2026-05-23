package com.atlaspeak.data.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabasePassphraseProvider @Inject constructor(
    @ApplicationContext
    private val context: Context,
) {
    private val secureRandom = SecureRandom()

    fun getPassphrase(): ByteArray {
        val encoded = preferences().getString(KEY_DATABASE_PASSPHRASE, null)
            ?: generateAndStorePassphrase()
        return Base64.decode(encoded, Base64.NO_WRAP)
    }

    private fun generateAndStorePassphrase(): String {
        val bytes = ByteArray(KEY_SIZE_BYTES)
        secureRandom.nextBytes(bytes)
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        preferences().edit { putString(KEY_DATABASE_PASSPHRASE, encoded) }
        return encoded
    }

    private fun preferences(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            SECURE_PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private companion object {
        const val SECURE_PREFS = "atlas_peak_secure"
        const val KEY_DATABASE_PASSPHRASE = "database_passphrase"
        const val KEY_SIZE_BYTES = 32
    }
}
