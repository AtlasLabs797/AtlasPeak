package com.atlaspeak.domain.repository

import com.atlaspeak.domain.model.workout.RestTimerFeedbackSettings

interface WorkoutSettingsRepository {
    suspend fun restTimerFeedbackSettings(): RestTimerFeedbackSettings
    suspend fun updateRestTimerFeedbackSettings(settings: RestTimerFeedbackSettings)
}
