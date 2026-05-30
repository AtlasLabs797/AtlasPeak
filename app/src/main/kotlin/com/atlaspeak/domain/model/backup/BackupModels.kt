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
