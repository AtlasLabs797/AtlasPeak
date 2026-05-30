package com.atlaspeak.domain.usecase.planning

import com.atlaspeak.domain.model.planning.NotificationSettings
import com.atlaspeak.domain.repository.NotificationScheduler
import com.atlaspeak.domain.repository.NotificationSettingsRepository
import javax.inject.Inject

class NotificationSettingsUseCase @Inject constructor(
    private val repository: NotificationSettingsRepository,
    private val notificationScheduler: NotificationScheduler,
) {
    suspend fun settings(): NotificationSettings = repository.settings()

    suspend fun update(settings: NotificationSettings): Boolean {
        if (!settings.dailySummaryTime.isValidClockTime()) return false
        repository.update(settings)
        notificationScheduler.rescheduleAll()
        return true
    }
}
