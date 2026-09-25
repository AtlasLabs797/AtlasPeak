package com.atlaspeak.data.backup

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.AEADBadTagException
import kotlinx.coroutines.CancellationException

class DriveBackupManager(
    private val snapshotStore: BackupSnapshotStore,
    private val backupJsonCodec: BackupJsonCodec,
    private val backupFileCodec: BackupFileCodec,
    private val driveBackupService: DriveBackupService,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    /**
     * [snapshot] permite reutilizar una captura ya tomada por el llamador
     * (p. ej. `BackupWorkerRunner`, que la necesita para calcular el hash de
     * cambios) en vez de serializar la base de datos dos veces. Si se omite,
     * se toma una captura nueva aqui (comportamiento previo).
     */
    suspend fun createBackup(
        accessToken: String?,
        password: CharArray,
        snapshot: DatabaseBackupSnapshot? = null,
    ): BackupOperationResult {
        if (accessToken.isNullOrBlank()) return BackupOperationResult.Failed(BackupFailureReason.NotAuthorized)
        if (password.isEmpty()) return BackupOperationResult.Failed(BackupFailureReason.EmptyPassword)

        return runCatching {
            val now = clock()
            val payload = backupJsonCodec.encode(snapshot ?: snapshotStore.snapshot()).encodeToByteArray()
            val encrypted = try {
                backupFileCodec.encrypt(payload, password)
            } finally {
                payload.fill(0)
            }
            val uploaded = try {
                driveBackupService.uploadBackup(
                    accessToken = accessToken,
                    fileName = backupFileName(now),
                    encryptedBytes = encrypted,
                )
            } finally {
                encrypted.fill(0)
            }
            // P1: un fallo al borrar backups antiguos tras una subida
            // correcta es best-effort. No debe convertir el resultado en
            // Failed ni saltarse el registro de exito (`markBackupCompleted`),
            // o un backup real quedaria marcado como fallido por un error de
            // limpieza no relacionado.
            try {
                trimOldBackups(accessToken)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // Ignorado a proposito: la subida ya se completo.
            }
            snapshotStore.markBackupCompleted(now)
            BackupOperationResult.Success(uploaded)
        }.fold(
            onSuccess = { it },
            onFailure = { error ->
                if (error is CancellationException) throw error
                BackupOperationResult.Failed(error.toBackupFailure())
            },
        )
    }

    suspend fun restoreBackup(
        accessToken: String?,
        fileId: String,
        password: CharArray,
    ): BackupOperationResult {
        if (accessToken.isNullOrBlank()) return BackupOperationResult.Failed(BackupFailureReason.NotAuthorized)
        if (password.isEmpty()) return BackupOperationResult.Failed(BackupFailureReason.EmptyPassword)

        return runCatching {
            val encrypted = driveBackupService.downloadBackup(accessToken, fileId)
            val plaintext = try {
                backupFileCodec.decrypt(encrypted, password)
            } finally {
                encrypted.fill(0)
            }
            val json = try {
                plaintext.decodeToString()
            } finally {
                plaintext.fill(0)
            }
            val snapshot = backupJsonCodec.decode(json)
            snapshotStore.restore(snapshot)
            BackupOperationResult.Success()
        }.fold(
            onSuccess = { it },
            onFailure = { error ->
                if (error is CancellationException) throw error
                BackupOperationResult.Failed(error.toBackupFailure())
            },
        )
    }

    suspend fun listBackups(accessToken: String?): List<DriveBackupFile> {
        if (accessToken.isNullOrBlank()) return emptyList()
        return driveBackupService.listBackups(accessToken)
            .sortedWith(compareByDescending<DriveBackupFile> { it.createdTimeMillis ?: Long.MIN_VALUE }.thenBy { it.name })
    }

    private suspend fun trimOldBackups(accessToken: String) {
        val backups = listBackups(accessToken)
        backups.drop(MAX_BACKUPS).forEach { driveBackupService.deleteBackup(accessToken, it.id) }
    }

    private fun backupFileName(timestampMillis: Long): String {
        val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return "atlas_peak_backup_${formatter.format(Date(timestampMillis))}.enc"
    }

    private fun Throwable.toBackupFailure(): BackupFailureReason = when (this) {
        is AEADBadTagException -> BackupFailureReason.Crypto
        // Incluye kotlinx.serialization.SerializationException, que hereda de
        // IllegalArgumentException: un JSON/backup mal formado es un backup
        // invalido, no un error desconocido.
        is IllegalArgumentException -> BackupFailureReason.InvalidBackup
        is retrofit2.HttpException -> if (code() == 401 || code() == 403) {
            BackupFailureReason.NotAuthorized
        } else {
            BackupFailureReason.Network
        }
        is java.io.IOException -> BackupFailureReason.Network
        else -> BackupFailureReason.Unknown
    }

    companion object {
        const val MAX_BACKUPS = 5
    }
}
