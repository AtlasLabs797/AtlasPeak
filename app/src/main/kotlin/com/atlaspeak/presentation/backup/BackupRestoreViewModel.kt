package com.atlaspeak.presentation.backup

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlaspeak.R
import com.atlaspeak.domain.usecase.backup.BackupUseCase
import com.atlaspeak.domain.model.backup.BackupResult
import com.atlaspeak.domain.model.backup.DriveBackup
import com.atlaspeak.domain.model.backup.SharedBackupExport
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class BackupRestoreViewModel @Inject constructor(
    private val backupUseCase: BackupUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(BackupRestoreUiState())
    val state: StateFlow<BackupRestoreUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<BackupRestoreEvent>()
    val events: SharedFlow<BackupRestoreEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            val status = backupUseCase.status()
            val health = backupUseCase.health()
            _state.update {
                it.copy(
                    autoBackupEnabled = status.autoBackupEnabled,
                    lastBackupAt = status.lastBackupAt,
                    requiresDriveAuthorization = health.requiresDriveAuthorization,
                    lastError = health.lastError,
                )
            }
        }
    }

    /**
     * BUG-096 (Fase 7 P1): limpia la marca de "Drive requiere reautorizacion"
     * y reintenta la peticion del token silencioso. Pensado para el boton
     * "Reconectar Drive" de la UI.
     */
    fun reconnectDrive() {
        viewModelScope.launch {
            backupUseCase.clearDriveAuthorizationRequired()
            _state.update { it.copy(requiresDriveAuthorization = false, messageRes = R.string.backup_drive_reconnect_attempting) }
            refreshStatus()
        }
    }

    fun refreshStatus() {
        viewModelScope.launch {
            val status = backupUseCase.status()
            val health = backupUseCase.health()
            _state.update {
                it.copy(
                    autoBackupEnabled = status.autoBackupEnabled,
                    lastBackupAt = status.lastBackupAt,
                    requiresDriveAuthorization = health.requiresDriveAuthorization,
                    lastError = health.lastError,
                )
            }
        }
    }

    fun onPasswordChanged(value: String) {
        _state.update { it.copy(password = value) }
    }

    fun confirmRestore(fileId: String) {
        _state.update { it.copy(pendingRestoreFileId = fileId) }
    }

    fun cancelRestore() {
        _state.update { it.copy(pendingRestoreFileId = null) }
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val password = state.value.password.toCharArray()
            if (enabled && password.isEmpty()) {
                _state.update { it.copy(messageRes = R.string.backup_password_required) }
                return@launch
            }
            try {
                if (enabled) {
                    backupUseCase.saveAutoBackupPassword(password)
                } else {
                    backupUseCase.clearAutoBackupPassword()
                }
            } finally {
                password.fill('\u0000')
                clearPassword()
            }
            backupUseCase.setAutoBackupEnabled(enabled)
            _state.update {
                it.copy(
                    autoBackupEnabled = enabled,
                    messageRes = if (enabled) R.string.backup_auto_enabled else R.string.backup_auto_disabled,
                )
            }
        }
    }

    fun updateAutoBackupPassword() {
        viewModelScope.launch {
            val password = state.value.password.toCharArray()
            if (password.isEmpty()) {
                _state.update { it.copy(messageRes = R.string.backup_password_required) }
                return@launch
            }
            try {
                backupUseCase.saveAutoBackupPassword(password)
                _state.update { it.copy(messageRes = R.string.backup_auto_password_updated) }
            } finally {
                password.fill('\u0000')
                clearPassword()
            }
        }
    }

    fun loadDriveBackups(accessToken: String) {
        viewModelScope.launch {
            setLoading(true)
            runCatching { backupUseCase.listDriveBackups(accessToken) }
                .onSuccess { backups ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            driveBackups = backups,
                            messageRes = if (backups.isEmpty()) R.string.backup_drive_empty else R.string.backup_drive_loaded,
                        )
                    }
                }
                .onFailure {
                    _state.update { it.copy(isLoading = false, messageRes = R.string.backup_drive_failed) }
                }
        }
    }

    fun createDriveBackup(accessToken: String) {
        val password = state.value.password.toCharArray()
        if (password.isEmpty()) {
            _state.update { it.copy(messageRes = R.string.backup_password_required) }
            return
        }
        viewModelScope.launch {
            try {
                setLoading(true)
                when (backupUseCase.createDriveBackup(accessToken, password)) {
                    is BackupResult.Success -> {
                        val status = backupUseCase.status()
                        _state.update {
                            it.copy(
                                isLoading = false,
                                lastBackupAt = status.lastBackupAt,
                                messageRes = R.string.backup_drive_created,
                            )
                        }
                        loadDriveBackups(accessToken)
                    }
                    is BackupResult.Failed -> {
                        _state.update { it.copy(isLoading = false, messageRes = R.string.backup_drive_failed) }
                    }
                }
            } finally {
                password.fill('\u0000')
                clearPassword()
            }
        }
    }

    fun restoreDriveBackup(accessToken: String, fileId: String) {
        val password = state.value.password.toCharArray()
        if (password.isEmpty()) {
            _state.update { it.copy(messageRes = R.string.backup_password_required) }
            return
        }
        viewModelScope.launch {
            try {
                setLoading(true)
                when (backupUseCase.restoreDriveBackup(accessToken, fileId, password)) {
                    is BackupResult.Success -> {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                pendingRestoreFileId = null,
                                messageRes = R.string.backup_restore_success,
                            )
                        }
                    }
                    is BackupResult.Failed -> {
                        _state.update { it.copy(isLoading = false, messageRes = R.string.backup_restore_failed) }
                    }
                }
            } finally {
                password.fill('\u0000')
                clearPassword()
            }
        }
    }

    fun createLocalEncryptedBackup() {
        val password = state.value.password.toCharArray()
        if (password.isEmpty()) {
            _state.update { it.copy(messageRes = R.string.backup_password_required) }
            return
        }
        writeSharedFile(
            successRes = R.string.backup_local_created,
            finallyBlock = {
                password.fill('\u0000')
                clearPassword()
            },
        ) {
            backupUseCase.writeEncryptedBackup(password)
        }
    }

    fun requestExportJson() {
        _state.update { it.copy(pendingCleartextExport = CleartextExportType.Json, messageRes = null) }
    }

    fun requestExportCsv() {
        _state.update { it.copy(pendingCleartextExport = CleartextExportType.Csv, messageRes = null) }
    }

    fun cancelCleartextExport() {
        _state.update { it.copy(pendingCleartextExport = null) }
    }

    fun confirmCleartextExport() {
        val type = state.value.pendingCleartextExport ?: return
        _state.update { it.copy(pendingCleartextExport = null) }
        when (type) {
            CleartextExportType.Json -> writeSharedFile(successRes = R.string.backup_export_created) {
                backupUseCase.writeManualJson()
            }
            CleartextExportType.Csv -> writeSharedFile(successRes = R.string.backup_export_created) {
                backupUseCase.writeCsvZip()
            }
        }
    }

    fun onDriveAuthorizationFailed() {
        _state.update { it.copy(messageRes = R.string.backup_drive_auth_failed) }
    }

    private fun writeSharedFile(
        @StringRes successRes: Int,
        finallyBlock: () -> Unit = {},
        block: suspend () -> SharedBackupExport,
    ) {
        viewModelScope.launch {
            try {
                setLoading(true)
                runCatching { block() }
                    .onSuccess { file ->
                        _state.update { it.copy(isLoading = false, messageRes = successRes) }
                        _events.emit(BackupRestoreEvent.Share(file))
                    }
                    .onFailure {
                        _state.update { it.copy(isLoading = false, messageRes = R.string.backup_export_failed) }
                    }
            } finally {
                finallyBlock()
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        _state.update { it.copy(isLoading = loading, messageRes = null) }
    }

    private fun clearPassword() {
        _state.update { it.copy(password = "") }
    }
}

data class BackupRestoreUiState(
    val isLoading: Boolean = false,
    val password: String = "",
    val autoBackupEnabled: Boolean = false,
    val lastBackupAt: Long? = null,
    val driveBackups: List<DriveBackup> = emptyList(),
    val pendingRestoreFileId: String? = null,
    val pendingCleartextExport: CleartextExportType? = null,
    @StringRes val messageRes: Int? = null,
    // BUG-096 (Fase 7 P1): estado funcional del backup automatico para que
    // la UI muestre avisos no silenciosos cuando Drive requiere reautorizacion
    // o el ultimo intento fallo.
    val requiresDriveAuthorization: Boolean = false,
    val lastError: com.atlaspeak.domain.model.backup.BackupFailure? = null,
)

sealed interface BackupRestoreEvent {
    data class Share(val file: SharedBackupExport) : BackupRestoreEvent
}

enum class CleartextExportType {
    Json,
    Csv,
}
