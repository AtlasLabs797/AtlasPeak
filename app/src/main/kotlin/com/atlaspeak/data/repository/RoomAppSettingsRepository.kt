package com.atlaspeak.data.repository

import com.atlaspeak.data.db.AppDatabase
import com.atlaspeak.domain.model.settings.AppThemeMode
import com.atlaspeak.domain.repository.AppSettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomAppSettingsRepository @Inject constructor(
    private val database: AppDatabase,
) : AppSettingsRepository {
    override fun observeThemeMode(): Flow<AppThemeMode> {
        return database.settingsDao().observeSettings().map { settings ->
            settings?.theme.toThemeMode()
        }
    }

    override suspend fun setThemeMode(mode: AppThemeMode) {
        database.settingsDao().updateTheme(mode.toStorageValue())
    }

    private fun String?.toThemeMode(): AppThemeMode {
        return when (this) {
            THEME_LIGHT -> AppThemeMode.Light
            THEME_DARK -> AppThemeMode.Dark
            else -> AppThemeMode.System
        }
    }

    private fun AppThemeMode.toStorageValue(): String {
        return when (this) {
            AppThemeMode.System -> THEME_SYSTEM
            AppThemeMode.Light -> THEME_LIGHT
            AppThemeMode.Dark -> THEME_DARK
        }
    }

    private companion object {
        const val THEME_SYSTEM = "SYSTEM"
        const val THEME_LIGHT = "LIGHT"
        const val THEME_DARK = "DARK"
    }
}
