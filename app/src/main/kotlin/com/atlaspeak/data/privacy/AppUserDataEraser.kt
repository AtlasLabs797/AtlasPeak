package com.atlaspeak.data.privacy

import android.content.Context
import androidx.work.WorkManager
import com.atlaspeak.data.backup.BackupCredentialStore
import com.atlaspeak.data.backup.BackupHealthStore
import com.atlaspeak.data.backup.DriveBackupService
import com.atlaspeak.data.db.AppDatabase
import com.atlaspeak.data.db.seed.DatabaseSeeder
import com.atlaspeak.data.drive.DriveAccessTokenProvider
import com.atlaspeak.data.drive.DriveAccessTokenResult
import com.atlaspeak.domain.repository.OnboardingRepository
import com.atlaspeak.domain.usecase.privacy.UserDataEraser
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implementacion de `UserDataEraser` (P2 de la auditoria de privacidad / derecho
 * al olvido). No toca la clave de cifrado de la base de datos
 * (`DatabasePassphraseProvider`, prefs `atlas_peak_secure`): la DB sigue cifrada
 * con la misma clave tras el borrado.
 */
@Singleton
class AppUserDataEraser @Inject constructor(
    @ApplicationContext private val context: Context,
    private val driveAccessTokenProvider: DriveAccessTokenProvider,
    private val driveBackupService: DriveBackupService,
    private val database: AppDatabase,
    private val databaseSeeder: DatabaseSeeder,
    private val onboardingRepository: OnboardingRepository,
    private val backupCredentialStore: BackupCredentialStore,
    private val backupHealthStore: BackupHealthStore,
) : UserDataEraser {

    override suspend fun cancelBackgroundWork() = withContext(Dispatchers.IO) {
        WorkManager.getInstance(context).cancelAllWork()
    }

    override suspend fun deleteDriveBackups(): Boolean = withContext(Dispatchers.IO) {
        when (val token = driveAccessTokenProvider.silentAccessToken()) {
            is DriveAccessTokenResult.Granted -> runCatching {
                driveBackupService.listBackups(token.accessToken).forEach { file ->
                    driveBackupService.deleteBackup(token.accessToken, file.id)
                }
            }.isSuccess
            DriveAccessTokenResult.MissingAuthorization,
            DriveAccessTokenResult.Failed,
            -> false
        }
    }

    override suspend fun eraseLocalData() = withContext(Dispatchers.IO) {
        // Preferencias/DataStore fuera de Room primero: si algo falla despues, es
        // preferible que el usuario vuelva a pasar por onboarding a que quede
        // credenciales de backup huerfanas.
        onboardingRepository.setOnboardingCompleted(false)
        backupCredentialStore.clearAutoBackupPassword()
        backupHealthStore.clear()
        File(context.filesDir, EXPORTS_DIRECTORY).deleteRecursively()

        database.clearAllTables()
        databaseSeeder.seed()
    }

    private companion object {
        const val EXPORTS_DIRECTORY = "exports"
    }
}
