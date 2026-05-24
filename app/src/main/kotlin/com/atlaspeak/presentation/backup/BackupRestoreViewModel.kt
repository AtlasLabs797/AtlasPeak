package com.atlaspeak.presentation.backup

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlaspeak.R
import com.atlaspeak.data.backup.BackupCredentialStore
import com.atlaspeak.data.backup.BackupOperationResult
import com.atlaspeak.data.backup.BackupSnapshotStore
import com.atlaspeak.data.backup.DriveBackupFile
import com.atlaspeak.data.backup.DriveBackupManager
import com.atlaspeak.data.backup.LocalBackupExportManager
import com.atlaspeak.data.backup.SharedExportFile
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
    private val driveBackupManager: DriveBackupManager,
    private val localBackupExportManager: LocalBackupExportManager,
    private val snapshotStore: BackupSnapshotStore,
    private val credentialStore: BackupCredentialStore,
) : ViewModel() {
    private val _state = MutableStateFlow(BackupRestoreUiState())
    val state: StateFlow<BackupRestoreUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<BackupRestoreEvent>()
    val events: SharedFlow<BackupRestoreEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    autoBackupEnabled = snapshotStore.autoBackupEnabled(),
                    lastBackupAt = snapshotStore.lastBackupAt(),
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
                    credentialStore.saveAutoBackupPassword(password)
                } else {
                    credentialStore.clearAutoBackupPassword()
                }
            } finally {
                password.fill('\u0000')
            }
            snapshotStore.setAutoBackupEnabled(enabled)
            _state.update {
                it.copy(
                    autoBackupEnabled = enabled,
                    messageRes = if (enabled) R.string.backup_auto_enabled else R.string.backup_auto_disabled,
                )
            }
        }
    }

    fun loadDriveBackups(accessToken: String) {
        viewModelScope.launch {
            setLoading(true)
            runCatching { driveBackupManager.listBackups(accessToken) }
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
                when (driveBackupManager.createBackup(accessToken, password)) {
                    is BackupOperationResult.Success -> {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                lastBackupAt = snapshotStore.lastBackupAt(),
                                messageRes = R.string.backup_drive_created,
                            )
                        }
                        loadDriveBackups(accessToken)
                    }
                    is BackupOperationResult.Failed -> {
                        _state.update { it.copy(isLoading = false, messageRes = R.string.backup_drive_failed) }
                    }
                }
            } finally {
                password.fill('\u0000')
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
                when (driveBackupManager.restoreBackup(accessToken, fileId, password)) {
                    is BackupOperationResult.Success -> {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                pendingRestoreFileId = null,
                                messageRes = R.string.backup_restore_success,
                            )
                        }
                    }
                    is BackupOperationResult.Failed -> {
                        _state.update { it.copy(isLoading = false, messageRes = R.string.backup_restore_failed) }
                    }
                }
            } finally {
                password.fill('\u0000')
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
            finallyBlock = { password.fill('\u0000') },
        ) {
            localBackupExportManager.writeEncryptedBackup(password)
        }
    }

    fun exportJson() {
        writeSharedFile(R.string.backup_export_created) {
            localBackupExportManager.writeManualJson()
        }
    }

    fun exportCsv() {
        writeSharedFile(R.string.backup_export_created) {
            localBackupExportManager.writeCsvZip()
        }
    }

    fun onDriveAuthorizationFailed() {
        _state.update { it.copy(messageRes = R.string.backup_drive_auth_failed) }
    }

    private fun writeSharedFile(
        @StringRes successRes: Int,
        finallyBlock: () -> Unit = {},
        block: suspend () -> SharedExportFile,
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
}

data class BackupRestoreUiState(
    val isLoading: Boolean = false,
    val password: String = "",
    val autoBackupEnabled: Boolean = false,
    val lastBackupAt: Long? = null,
    val driveBackups: List<DriveBackupFile> = emptyList(),
    val pendingRestoreFileId: String? = null,
    @StringRes val messageRes: Int? = null,
)

sealed interface BackupRestoreEvent {
    data class Share(val file: SharedExportFile) : BackupRestoreEvent
}
