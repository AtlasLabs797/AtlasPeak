package com.atlaspeak.data.repository

import com.atlaspeak.data.db.AppDatabase
import com.atlaspeak.data.db.entity.AppSettingsEntity
import com.atlaspeak.domain.model.planning.NotificationSettings
import com.atlaspeak.domain.repository.NotificationSettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomNotificationSettingsRepository @Inject constructor(
    private val database: AppDatabase,
) : NotificationSettingsRepository {
    override suspend fun settings(): NotificationSettings {
        return (database.settingsDao().getSettings() ?: AppSettingsEntity()).toDomain()
    }

    override suspend fun update(settings: NotificationSettings) {
        database.settingsDao().insertSettings(AppSettingsEntity())
        database.settingsDao().updateNotificationSettings(
            notificationsEnabled = settings.notificationsEnabled,
            motivationalMessages = settings.motivationalMessages,
            dailySummaryEnabled = settings.dailySummaryEnabled,
            dailySummaryTime = settings.dailySummaryTime,
            weeklySummaryEnabled = settings.weeklySummaryEnabled,
        )
    }

    private fun AppSettingsEntity.toDomain() = NotificationSettings(
        notificationsEnabled = notificationsEnabled,
        motivationalMessages = motivationalMessages,
        dailySummaryEnabled = dailySummaryEnabled,
        dailySummaryTime = dailySummaryTime,
        weeklySummaryEnabled = weeklySummaryEnabled,
    )
}
