package com.atlaspeak.presentation.recovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlaspeak.domain.usecase.recovery.DatabaseRecoveryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * P1 (auditoria) - recuperacion cuando la base de datos cifrada no abre en este dispositivo
 * (Keystore/keyset corrupto). La unica salida sin backend es borrar los datos locales; el
 * trabajo real (Room/Keystore) vive detras de [DatabaseRecoveryUseCase] (`domain`) para que
 * este ViewModel (presentation) no dependa de la capa de datos directamente
 * (StaticArchitecturePolicyTest).
 */
@HiltViewModel
class RecoveryViewModel @Inject constructor(
    private val databaseRecoveryUseCase: DatabaseRecoveryUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(RecoveryUiState())
    val state: StateFlow<RecoveryUiState> = mutableState.asStateFlow()

    fun resetLocalData() {
        if (mutableState.value.isResetting || mutableState.value.resetCompleted) return
        mutableState.value = mutableState.value.copy(isResetting = true, resetFailed = false)
        viewModelScope.launch {
            val success = databaseRecoveryUseCase.resetLocalData()
            mutableState.value = if (success) {
                mutableState.value.copy(isResetting = false, resetCompleted = true)
            } else {
                mutableState.value.copy(isResetting = false, resetFailed = true)
            }
        }
    }
}

data class RecoveryUiState(
    val isResetting: Boolean = false,
    val resetCompleted: Boolean = false,
    val resetFailed: Boolean = false,
)
