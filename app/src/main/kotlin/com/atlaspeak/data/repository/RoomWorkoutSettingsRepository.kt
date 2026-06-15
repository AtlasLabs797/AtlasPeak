package com.atlaspeak.data.repository

import com.atlaspeak.data.db.AppDatabase
import com.atlaspeak.domain.model.workout.RestTimerFeedbackSettings
import com.atlaspeak.domain.repository.WorkoutSettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomWorkoutSettingsRepository @Inject constructor(
    private val database: AppDatabase,
) : WorkoutSettingsRepository {
    override suspend fun restTimerFeedbackSettings(): RestTimerFeedbackSettings {
        val settings = database.settingsDao().getSettings()
        return RestTimerFeedbackSettings(
            soundEnabled = settings?.restSoundEnabled ?: true,
            vibrationEnabled = settings?.restVibrationEnabled ?: true,
        )
    }

    override suspend fun updateRestTimerFeedbackSettings(settings: RestTimerFeedbackSettings) {
        database.settingsDao().updateRestTimerFeedbackSettings(
            soundEnabled = settings.soundEnabled,
            vibrationEnabled = settings.vibrationEnabled,
        )
    }
}
