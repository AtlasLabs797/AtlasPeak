package com.atlaspeak.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlaspeak.domain.model.profile.Gender
import com.atlaspeak.domain.model.profile.Goal
import com.atlaspeak.domain.model.profile.UserProfile
import com.atlaspeak.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(EditProfileUiState())
    val state: StateFlow<EditProfileUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val profile = profileRepository.getProfile()
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        displayName = profile?.displayName.orEmpty(),
                        age = profile?.age?.toString().orEmpty(),
                        heightCm = profile?.heightCm?.let(::formatHeight).orEmpty(),
                        gender = profile?.gender,
                        goalType = profile?.goalType,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update { it.copy(isLoading = false, loadFailed = true) }
            }
        }
    }

    fun onDisplayNameChanged(value: String) = mutableState.update { it.copy(displayName = value) }

    fun onAgeChanged(value: String) = mutableState.update {
        it.copy(age = value.filter(Char::isDigit).take(MAX_AGE_LENGTH), ageInvalid = false)
    }

    fun onHeightChanged(value: String) = mutableState.update {
        it.copy(
            heightCm = value.filter { char -> char.isDigit() || char == '.' || char == ',' }.take(MAX_HEIGHT_LENGTH),
            heightInvalid = false,
        )
    }

    fun onGenderChanged(value: Gender) = mutableState.update { it.copy(gender = value) }

    fun onGoalChanged(value: Goal) = mutableState.update { it.copy(goalType = value) }

    fun save() {
        val snapshot = mutableState.value
        if (snapshot.isSubmitting) return
        val parsedAge = snapshot.age.trim().ifBlank { null }?.toIntOrNull()
        val parsedHeight = snapshot.heightCm.trim().ifBlank { null }?.replace(',', '.')?.toDoubleOrNull()
        val ageInvalid = snapshot.age.isNotBlank() && (parsedAge == null || parsedAge !in AGE_RANGE)
        val heightInvalid = snapshot.heightCm.isNotBlank() &&
            (parsedHeight == null || parsedHeight !in MIN_HEIGHT_CM..MAX_HEIGHT_CM)
        if (ageInvalid || heightInvalid) {
            mutableState.update { it.copy(ageInvalid = ageInvalid, heightInvalid = heightInvalid) }
            return
        }
        viewModelScope.launch {
            mutableState.update { it.copy(isSubmitting = true) }
            try {
                profileRepository.saveProfile(
                    UserProfile(
                        displayName = snapshot.displayName.trim().ifBlank { null },
                        age = parsedAge,
                        heightCm = parsedHeight,
                        gender = snapshot.gender,
                        goalType = snapshot.goalType,
                    ),
                )
                mutableState.update { it.copy(isSubmitting = false, saved = true) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update { it.copy(isSubmitting = false, saveFailed = true) }
            }
        }
    }

    fun consumeSaveError() = mutableState.update { it.copy(saveFailed = false) }

    private fun formatHeight(value: Double): String {
        return if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
    }

    private companion object {
        val AGE_RANGE = 10..120
        const val MIN_HEIGHT_CM = 80.0
        const val MAX_HEIGHT_CM = 250.0
        const val MAX_AGE_LENGTH = 3
        const val MAX_HEIGHT_LENGTH = 6
    }
}

data class EditProfileUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val isSubmitting: Boolean = false,
    val displayName: String = "",
    val age: String = "",
    val heightCm: String = "",
    val gender: Gender? = null,
    val goalType: Goal? = null,
    val ageInvalid: Boolean = false,
    val heightInvalid: Boolean = false,
    val saved: Boolean = false,
    val saveFailed: Boolean = false,
)
