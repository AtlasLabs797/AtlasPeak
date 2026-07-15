package com.atlaspeak.presentation.planning

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlaspeak.R
import com.atlaspeak.domain.model.planning.NotificationSettings
import com.atlaspeak.domain.usecase.planning.NotificationSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val useCase: NotificationSettingsUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(NotificationSettingsUiState())
    val state: StateFlow<NotificationSettingsUiState> = mutableState.asStateFlow()

    init {
        refresh()
    }

    fun reconcileSystemNotificationAvailability(canPostNotifications: Boolean) {
        if (canPostNotifications || !mutableState.value.settings.notificationsEnabled) return
        markSystemNotificationsUnavailable()
    }

    fun markSystemNotificationsUnavailable() {
        viewModelScope.launch {
            val updated = mutableState.value.settings.copy(notificationsEnabled = false)
            useCase.update(updated)
            mutableState.update {
                it.copy(
                    settings = updated,
                    messageRes = R.string.notification_settings_system_denied,
                    messageTone = NotificationSettingsFeedbackTone.Error,
                )
            }
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) = updateDraft { it.copy(notificationsEnabled = enabled) }
    fun setMotivationalMessages(enabled: Boolean) = updateDraft { it.copy(motivationalMessages = enabled) }
    fun setDailySummaryEnabled(enabled: Boolean) = updateDraft { it.copy(dailySummaryEnabled = enabled) }
    fun setDailySummaryTime(value: String) = updateDraft {
        it.copy(dailySummaryTime = value.filter { char -> char.isDigit() || char == ':' }.take(MAX_TIME_LENGTH))
    }
    fun setWeeklySummaryEnabled(enabled: Boolean) = updateDraft { it.copy(weeklySummaryEnabled = enabled) }

    fun save() {
        viewModelScope.launch {
            val saved = useCase.update(mutableState.value.settings)
            mutableState.update {
                it.copy(
                    messageRes = if (saved) R.string.notification_settings_saved else R.string.weekly_plan_invalid_time,
                    messageTone = if (saved) {
                        NotificationSettingsFeedbackTone.Success
                    } else {
                        NotificationSettingsFeedbackTone.Error
                    },
                )
            }
        }
    }

    fun showFeedback(@StringRes messageRes: Int, tone: NotificationSettingsFeedbackTone) {
        mutableState.update { it.copy(messageRes = messageRes, messageTone = tone) }
    }

    fun refresh() {
        viewModelScope.launch {
            mutableState.update { it.copy(isLoading = true, messageRes = null) }
            try {
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        settings = useCase.settings(),
                        messageRes = null,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update { it.copy(isLoading = false, messageRes = R.string.error_generic) }
            }
        }
    }

    private fun updateDraft(transform: (NotificationSettings) -> NotificationSettings) {
        mutableState.update {
            it.copy(
                settings = transform(it.settings),
                messageRes = null,
                messageTone = NotificationSettingsFeedbackTone.Success,
            )
        }
    }

    private companion object {
        const val MAX_TIME_LENGTH = 5
    }
}

data class NotificationSettingsUiState(
    val isLoading: Boolean = true,
    val settings: NotificationSettings = NotificationSettings(),
    @StringRes val messageRes: Int? = null,
    val messageTone: NotificationSettingsFeedbackTone = NotificationSettingsFeedbackTone.Success,
)

enum class NotificationSettingsFeedbackTone {
    Success,
    Error,
}
