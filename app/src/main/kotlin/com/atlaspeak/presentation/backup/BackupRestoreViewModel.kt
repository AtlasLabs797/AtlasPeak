package com.atlaspeak.presentation.backup

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlaspeak.R
import com.atlaspeak.domain.usecase.backup.BackupUseCase
import com.atlaspeak.domain.model.backup.BackupFailure
import com.atlaspeak.domain.model.backup.BackupResult
import com.atlaspeak.domain.model.backup.DriveBackup
import com.atlaspeak.domain.model.backup.SharedBackupExport
import com.atlaspeak.domain.usecase.backup.BackupPassphrasePolicy
import com.atlaspeak.domain.usecase.backup.BackupPassphraseValidation
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
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
    private val backupPassphrasePolicy: BackupPassphrasePolicy,
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
     * Limpia el bloqueo solo despues de que AuthorizationClient haya devuelto
     * un token valido. Si el usuario cancela, el aviso sigue visible.
     */
    fun onDriveReauthorized() {
        viewModelScope.launch {
            backupUseCase.clearDriveAuthorizationRequired()
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

    fun onConfirmPasswordChanged(value: String) {
        _state.update { it.copy(confirmPassword = value) }
    }

    fun confirmRestore(fileId: String) {
        _state.update { it.copy(pendingRestoreFileId = fileId) }
    }

    fun cancelRestore() {
        _state.update { it.copy(pendingRestoreFileId = null) }
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        if (!enabled) {
            viewModelScope.launch {
                try {
                    backupUseCase.clearAutoBackupPassword()
                    backupUseCase.setAutoBackupEnabled(false)
                    _state.update { it.copy(autoBackupEnabled = false, messageRes = R.string.backup_auto_disabled) }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    // BUG P1: un fallo de Keystore aqui no debe tirar la app
                    // ni dejar `autoBackupEnabled` en un estado que no refleja
                    // lo que realmente se guardo.
                    _state.update { it.copy(messageRes = R.string.backup_auto_backup_error) }
                }
            }
            return
        }
        val password = state.value.password.toCharArray()
        val confirmation = state.value.confirmPassword.toCharArray()
        viewModelScope.launch {
            try {
                val errorRes = backupPassphrasePolicy.evaluate(password, confirmation).errorMessageRes()
                if (errorRes != null) {
                    _state.update { it.copy(messageRes = errorRes) }
                    return@launch
                }
                backupUseCase.saveAutoBackupPassword(password)
                backupUseCase.setAutoBackupEnabled(true)
                _state.update { it.copy(autoBackupEnabled = true, messageRes = R.string.backup_auto_enabled) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // BUG P1: no dejar `autoBackupEnabled = true` si guardar la
                // contraseña en Keystore falla a mitad de camino.
                _state.update { it.copy(messageRes = R.string.backup_auto_backup_error) }
            } finally {
                password.fill('\u0000')
                confirmation.fill('\u0000')
                clearPassword()
                clearConfirmPassword()
            }
        }
    }

    fun updateAutoBackupPassword() {
        val password = state.value.password.toCharArray()
        val confirmation = state.value.confirmPassword.toCharArray()
        viewModelScope.launch {
            try {
                val errorRes = backupPassphrasePolicy.evaluate(password, confirmation).errorMessageRes()
                if (errorRes != null) {
                    _state.update { it.copy(messageRes = errorRes) }
                    return@launch
                }
                backupUseCase.saveAutoBackupPassword(password)
                _state.update { it.copy(messageRes = R.string.backup_auto_password_updated) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // BUG P1: un fallo de Keystore aqui no debe tirar la app.
                _state.update { it.copy(messageRes = R.string.backup_auto_backup_error) }
            } finally {
                password.fill('\u0000')
                confirmation.fill('\u0000')
                clearPassword()
                clearConfirmPassword()
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
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    _state.update { it.copy(isLoading = false, messageRes = R.string.backup_drive_failed) }
                }
        }
    }

    fun createDriveBackup(accessToken: String) {
        val password = state.value.password.toCharArray()
        val confirmation = state.value.confirmPassword.toCharArray()
        viewModelScope.launch {
            try {
                val errorRes = backupPassphrasePolicy.evaluate(password, confirmation).errorMessageRes()
                if (errorRes != null) {
                    _state.update { it.copy(messageRes = errorRes) }
                    return@launch
                }
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
                confirmation.fill('\u0000')
                clearPassword()
                clearConfirmPassword()
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
                when (val result = backupUseCase.restoreDriveBackup(accessToken, fileId, password)) {
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
                        // P1: distingue contraseña incorrecta / sin red /
                        // archivo invalido del mensaje generico anterior.
                        _state.update { it.copy(isLoading = false, messageRes = result.reason.toRestoreErrorMessageRes()) }
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
        val confirmation = state.value.confirmPassword.toCharArray()
        viewModelScope.launch {
            try {
                val errorRes = backupPassphrasePolicy.evaluate(password, confirmation).errorMessageRes()
                if (errorRes != null) {
                    _state.update { it.copy(messageRes = errorRes) }
                    return@launch
                }
                setLoading(true)
                runCatching { backupUseCase.writeEncryptedBackup(password) }
                    .onSuccess { file ->
                        _state.update { it.copy(isLoading = false, messageRes = R.string.backup_local_created) }
                        _events.emit(BackupRestoreEvent.Share(file))
                    }
                    .onFailure { error ->
                        if (error is CancellationException) throw error
                        _state.update { it.copy(isLoading = false, messageRes = R.string.backup_export_failed) }
                    }
            } finally {
                password.fill('\u0000')
                confirmation.fill('\u0000')
                clearPassword()
                clearConfirmPassword()
            }
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
                    .onFailure { error ->
                        if (error is CancellationException) throw error
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

    private fun clearConfirmPassword() {
        _state.update { it.copy(confirmPassword = "") }
    }

    @StringRes
    private fun BackupPassphraseValidation.errorMessageRes(): Int? = when (this) {
        BackupPassphraseValidation.TooShort -> R.string.backup_password_too_short
        BackupPassphraseValidation.Common -> R.string.backup_password_common
        BackupPassphraseValidation.Mismatch -> R.string.backup_password_mismatch
        BackupPassphraseValidation.Valid -> null
    }

    @StringRes
    private fun BackupFailure.toRestoreErrorMessageRes(): Int = when (this) {
        BackupFailure.Crypto -> R.string.backup_restore_error_wrong_password
        BackupFailure.Network -> R.string.backup_restore_error_network
        BackupFailure.InvalidBackup -> R.string.backup_restore_error_invalid
        BackupFailure.NotAuthorized,
        BackupFailure.EmptyPassword,
        BackupFailure.Unknown,
        -> R.string.backup_restore_failed
    }
}

data class BackupRestoreUiState(
    val isLoading: Boolean = false,
    val password: String = "",
    val confirmPassword: String = "",
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
