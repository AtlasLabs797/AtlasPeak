package com.atlaspeak.domain.repository

import com.atlaspeak.domain.model.planning.NotificationSettings

interface NotificationSettingsRepository {
    suspend fun settings(): NotificationSettings
    suspend fun update(settings: NotificationSettings)
}
