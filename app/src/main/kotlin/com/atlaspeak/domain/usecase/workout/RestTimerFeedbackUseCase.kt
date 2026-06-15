package com.atlaspeak.domain.usecase.workout

import com.atlaspeak.domain.model.workout.RestTimerFeedbackSettings
import com.atlaspeak.domain.repository.WorkoutSettingsRepository
import javax.inject.Inject

class RestTimerFeedbackUseCase @Inject constructor(
    private val repository: WorkoutSettingsRepository,
) {
    suspend fun settings(): RestTimerFeedbackSettings = repository.restTimerFeedbackSettings()

    suspend fun update(settings: RestTimerFeedbackSettings) {
        repository.updateRestTimerFeedbackSettings(settings)
    }
}
