package com.atlaspeak.data.backup

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class BackupCredentialStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun saveAutoBackupPassword(password: CharArray) = withContext(Dispatchers.IO) {
        preferences().edit { putString(KEY_AUTO_BACKUP_CREDENTIAL, password.concatToString()) }
    }

    suspend fun autoBackupPassword(): CharArray? = withContext(Dispatchers.IO) {
        preferences().getString(KEY_AUTO_BACKUP_CREDENTIAL, null)?.toCharArray()
    }

    suspend fun clearAutoBackupPassword() = withContext(Dispatchers.IO) {
        preferences().edit { remove(KEY_AUTO_BACKUP_CREDENTIAL) }
    }

    suspend fun lastSnapshotHash(): String? = withContext(Dispatchers.IO) {
        preferences().getString(KEY_LAST_SNAPSHOT_HASH, null)
    }

    suspend fun saveLastSnapshotHash(hash: String) = withContext(Dispatchers.IO) {
        preferences().edit { putString(KEY_LAST_SNAPSHOT_HASH, hash) }
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
        const val SECURE_PREFS = "atlas_peak_backup_secure"
        const val KEY_AUTO_BACKUP_CREDENTIAL = "auto_backup_credential"
        const val KEY_LAST_SNAPSHOT_HASH = "last_snapshot_hash"
    }
}
