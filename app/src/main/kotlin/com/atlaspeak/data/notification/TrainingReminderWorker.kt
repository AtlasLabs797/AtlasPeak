package com.atlaspeak.data.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.atlaspeak.domain.repository.NotificationScheduler
import com.atlaspeak.domain.repository.NotificationSettingsRepository
import com.atlaspeak.domain.repository.WeeklyPlanRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

@HiltWorker
class TrainingReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val weeklyPlanRepository: WeeklyPlanRepository,
    private val settingsRepository: NotificationSettingsRepository,
    private val permissionChecker: NotificationPermissionChecker,
    private val notificationHelper: AtlasPeakNotificationHelper,
    private val notificationScheduler: NotificationScheduler,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            val dayOfWeek = inputData.getInt(NotificationWorkNames.KEY_DAY_OF_WEEK, 0)
            val orderIndex = inputData.getInt(NotificationWorkNames.KEY_SESSION_ORDER_INDEX, 0)
            if (dayOfWeek !in NotificationWorkNames.ISO_WEEKDAYS) return Result.success()
            val settings = settingsRepository.settings()
            if (settings.notificationsEnabled && permissionChecker.canPostNotifications()) {
                weeklyPlanRepository.plan()
                    .firstOrNull { it.dayOfWeek == dayOfWeek && !it.isRestDay }
                    ?.let { day ->
                        day.sessions
                            .firstOrNull {
                                it.orderIndex == orderIndex &&
                                    it.notificationEnabled &&
                                    (it.routineId != null || it.cardioTypeId != null)
                            }
                            ?.let { session -> notificationHelper.showTrainingReminder(day, session) }
                    }
            }
            notificationScheduler.rescheduleTrainingReminder(dayOfWeek)
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
