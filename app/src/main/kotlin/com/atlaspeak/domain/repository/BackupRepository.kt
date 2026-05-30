package com.atlaspeak.domain.repository

import com.atlaspeak.domain.model.backup.BackupResult
import com.atlaspeak.domain.model.backup.BackupStatus
import com.atlaspeak.domain.model.backup.DriveBackup
import com.atlaspeak.domain.model.backup.SharedBackupExport

interface BackupRepository {
    suspend fun status(): BackupStatus
    suspend fun setAutoBackupEnabled(enabled: Boolean)
    suspend fun saveAutoBackupPassword(password: CharArray)
    suspend fun clearAutoBackupPassword()
    suspend fun listDriveBackups(accessToken: String): List<DriveBackup>
    suspend fun createDriveBackup(accessToken: String, password: CharArray): BackupResult
    suspend fun restoreDriveBackup(accessToken: String, fileId: String, password: CharArray): BackupResult
    suspend fun writeEncryptedBackup(password: CharArray): SharedBackupExport
    suspend fun writeManualJson(): SharedBackupExport
    suspend fun writeCsvZip(): SharedBackupExport
}
