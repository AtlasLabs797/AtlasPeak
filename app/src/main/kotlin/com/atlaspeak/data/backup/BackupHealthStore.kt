package com.atlaspeak.data.backup

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.atlaspeak.domain.model.backup.BackupFailure
import com.atlaspeak.domain.model.backup.BackupHealthStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Almacena el estado funcional del backup automatico (BUG-096 / Fase 7 P1):
 * ultimo backup correcto, ultimo intento, ultimo error y si Drive necesita
 * reautorizacion. No contiene informacion sensible (solo timestamps y el enum
 * `BackupFailure`), asi que usamos SharedPreferences plano.
 */
@Singleton
class BackupHealthStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun read(): BackupHealthStatus = withContext(Dispatchers.IO) {
        val lastSuccess = prefs.getLong(KEY_LAST_SUCCESS, 0L).takeIf { it > 0L }
        val lastAttempt = prefs.getLong(KEY_LAST_ATTEMPT, 0L).takeIf { it > 0L }
        val lastErrorName = prefs.getString(KEY_LAST_ERROR, null)
        val lastError = lastErrorName?.let { name ->
            runCatching { BackupFailure.valueOf(name) }.getOrNull()
        }
        val requiresReauth = prefs.getBoolean(KEY_REQUIRES_REAUTH, false)
        BackupHealthStatus(
            lastSuccessfulBackupAt = lastSuccess,
            lastAttemptAt = lastAttempt,
            lastError = lastError,
            requiresDriveAuthorization = requiresReauth,
        )
    }

    suspend fun recordSuccess(timestampMillis: Long) = withContext(Dispatchers.IO) {
        prefs.edit {
            putLong(KEY_LAST_SUCCESS, timestampMillis)
            putLong(KEY_LAST_ATTEMPT, timestampMillis)
            remove(KEY_LAST_ERROR)
            putBoolean(KEY_REQUIRES_REAUTH, false)
        }
    }

    suspend fun recordFailure(reason: BackupFailure, timestampMillis: Long) = withContext(Dispatchers.IO) {
        prefs.edit {
            putLong(KEY_LAST_ATTEMPT, timestampMillis)
            putString(KEY_LAST_ERROR, reason.name)
            putBoolean(KEY_REQUIRES_REAUTH, reason == BackupFailure.NotAuthorized)
        }
    }

    suspend fun clearDriveAuthorizationRequired() = withContext(Dispatchers.IO) {
        prefs.edit { putBoolean(KEY_REQUIRES_REAUTH, false) }
    }

    suspend fun markDriveAuthorizationRequired() = withContext(Dispatchers.IO) {
        prefs.edit { putBoolean(KEY_REQUIRES_REAUTH, true) }
    }

    private companion object {
        const val PREFS_NAME = "atlas_peak_backup_health"
        const val KEY_LAST_SUCCESS = "last_success_ms"
        const val KEY_LAST_ATTEMPT = "last_attempt_ms"
        const val KEY_LAST_ERROR = "last_error"
        const val KEY_REQUIRES_REAUTH = "requires_drive_reauth"
    }
}