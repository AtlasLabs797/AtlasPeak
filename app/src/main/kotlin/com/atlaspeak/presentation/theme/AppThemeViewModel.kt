package com.atlaspeak.presentation.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.annotation.StringRes
import com.atlaspeak.R
import com.atlaspeak.domain.model.settings.AppThemeMode
import com.atlaspeak.domain.repository.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AppThemeViewModel @Inject constructor(
    private val repository: AppSettingsRepository,
) : ViewModel() {
    private val mutableSaveEvents = MutableSharedFlow<ThemeSaveEvent>()
    val saveEvents: SharedFlow<ThemeSaveEvent> = mutableSaveEvents.asSharedFlow()

    val themeMode: StateFlow<AppThemeMode> = repository.observeThemeMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppThemeMode.System)

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            try {
                repository.setThemeMode(mode)
                mutableSaveEvents.emit(ThemeSaveEvent(R.string.theme_settings_saved))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableSaveEvents.emit(ThemeSaveEvent(R.string.error_generic, isError = true))
            }
        }
    }
}

data class ThemeSaveEvent(
    @StringRes val messageRes: Int,
    val isError: Boolean = false,
)
