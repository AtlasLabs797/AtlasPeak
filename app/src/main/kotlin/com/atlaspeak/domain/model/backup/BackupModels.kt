package com.atlaspeak.domain.model.backup

data class DriveBackup(
    val id: String,
    val name: String,
    val createdTimeMillis: Long?,
    val sizeBytes: Long?,
)

data class SharedBackupExport(
    val uri: String,
    val mimeType: String,
    val fileName: String,
)

data class BackupStatus(
    val autoBackupEnabled: Boolean,
    val lastBackupAt: Long?,
)

/**
 * Estado funcional del backup automatico, separado del resultado de la ejecucion
 * tecnica del Worker. BUG-096 (Fase 7 P1): antes `BackupWorkerRunner` trataba
 * `MissingAuthorization` como ejecucion exitosa, asi un backup automatico podia
 * fallar durante semanas sin que el usuario se enterase.
 *
 * - [lastSuccessfulBackupAt]: timestamp del ultimo backup correcto.
 * - [lastAttemptAt]: timestamp del ultimo intento (exito o fallo).
 * - [lastError]: tipo de error del ultimo intento (null si el ultimo fue OK).
 * - [requiresDriveAuthorization]: true cuando el token de Drive se ha invalidado
 *   o el usuario ha revocado el permiso. No se hacen reintentos permanentes; la
 *   UI muestra un aviso y un boton "Reconectar Drive".
 *
 * Nunca se almacena informacion sensible (no se guarda el mensaje crudo del SDK,
 * solo el enum).
 */
data class BackupHealthStatus(
    val lastSuccessfulBackupAt: Long?,
    val lastAttemptAt: Long?,
    val lastError: BackupFailure?,
    val requiresDriveAuthorization: Boolean,
)

sealed interface BackupResult {
    data class Success(val file: DriveBackup? = null) : BackupResult
    data class Failed(val reason: BackupFailure) : BackupResult
}

enum class BackupFailure {
    NotAuthorized,
    EmptyPassword,
    Network,
    Crypto,
    InvalidBackup,
    Unknown,
}
