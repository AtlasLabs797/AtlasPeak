package com.atlaspeak.presentation.profile

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlaspeak.R
import com.atlaspeak.domain.repository.ProfileRepository
import com.atlaspeak.domain.usecase.privacy.DeleteAllUserDataResult
import com.atlaspeak.domain.usecase.privacy.DeleteAllUserDataUseCase
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
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val deleteAllUserDataUseCase: DeleteAllUserDataUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = mutableState.asStateFlow()

    private val mutableEvents = MutableSharedFlow<ProfileEvent>()
    val events: SharedFlow<ProfileEvent> = mutableEvents.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                val profile = profileRepository.getProfile()
                mutableState.update { it.copy(displayName = profile?.displayName) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // El nombre es decorativo en esta pantalla; sin dato se muestra el fallback.
            }
        }
    }

    /** P2 auditoria de privacidad (derecho al olvido): abre el dialogo de confirmacion. */
    fun requestDeleteAllData() {
        mutableState.update {
            it.copy(showDeleteAllDataDialog = true, deleteAllDataMessageRes = null)
        }
    }

    fun cancelDeleteAllData() {
        mutableState.update { it.copy(showDeleteAllDataDialog = false) }
    }

    fun setDeleteDriveBackupsToo(enabled: Boolean) {
        mutableState.update { it.copy(deleteDriveBackupsToo = enabled) }
    }

    fun confirmDeleteAllData() {
        val current = mutableState.value
        if (current.isDeletingAllData) return
        mutableState.update {
            it.copy(isDeletingAllData = true, showDeleteAllDataDialog = false, deleteAllDataMessageRes = null)
        }
        viewModelScope.launch {
            when (val result = deleteAllUserDataUseCase(current.deleteDriveBackupsToo)) {
                DeleteAllUserDataResult.Success -> {
                    mutableState.update { it.copy(isDeletingAllData = false) }
                    mutableEvents.emit(ProfileEvent.DataDeleted(driveNotDeleted = false))
                }
                is DeleteAllUserDataResult.PartialSuccess -> {
                    mutableState.update {
                        it.copy(
                            isDeletingAllData = false,
                            deleteAllDataMessageRes = R.string.delete_all_data_partial_success,
                        )
                    }
                    mutableEvents.emit(ProfileEvent.DataDeleted(driveNotDeleted = result.driveNotDeleted))
                }
                DeleteAllUserDataResult.Failed -> {
                    mutableState.update {
                        it.copy(
                            isDeletingAllData = false,
                            deleteAllDataMessageRes = R.string.delete_all_data_failed,
                        )
                    }
                }
            }
        }
    }
}

data class ProfileUiState(
    val displayName: String? = null,
    val showDeleteAllDataDialog: Boolean = false,
    val deleteDriveBackupsToo: Boolean = true,
    val isDeletingAllData: Boolean = false,
    @StringRes val deleteAllDataMessageRes: Int? = null,
)

sealed interface ProfileEvent {
    /**
     * Todos los datos locales se borraron: la app debe volver a onboarding. [driveNotDeleted]
     * se avisa en la propia navegacion porque el mensaje del estado no llega a verse al salir.
     */
    data class DataDeleted(val driveNotDeleted: Boolean) : ProfileEvent
}
