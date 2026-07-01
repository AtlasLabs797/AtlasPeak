package com.atlaspeak.domain.repository

import com.atlaspeak.domain.model.settings.AppThemeMode
import kotlinx.coroutines.flow.Flow

interface AppSettingsRepository {
    fun observeThemeMode(): Flow<AppThemeMode>
    suspend fun setThemeMode(mode: AppThemeMode)
}
