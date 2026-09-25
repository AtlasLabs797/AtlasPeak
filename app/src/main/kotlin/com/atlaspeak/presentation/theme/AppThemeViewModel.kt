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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
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

    // Tema flash en arranque en frio (auditoria): themeMode arrancaba en System hasta que Room
    // emitia el valor real. `isLoaded` deja a MainActivity mantener el splash visible hasta que
    // haya un valor definitivo (real o, si la DB no abre, el fallback a System de mas abajo).
    private val mutableIsLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = mutableIsLoaded.asStateFlow()

    val themeMode: StateFlow<AppThemeMode> = repository.observeThemeMode()
        .onEach { mutableIsLoaded.value = true }
        .catch { error ->
            // Si la DB no abre (DatabaseKeyUnavailableException u otro fallo), el tema cae a
            // System y el splash se libera igualmente: nunca debe quedar colgado.
            mutableIsLoaded.value = true
            emit(AppThemeMode.System)
        }
        // Eagerly (no WhileSubscribed): la coleccion arranca en el init del ViewModel, no
        // cuando Compose se suscribe. MainActivity consulta `isLoaded` desde
        // setKeepOnScreenCondition antes de que setContent monte la UI; si dependiera de un
        // primer subscriber de Compose, el splash podria quedarse colgado hasta ese momento.
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppThemeMode.System)

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
