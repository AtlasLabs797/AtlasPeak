package com.atlaspeak.data.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.atlaspeak.domain.model.dashboard.DashboardFilters
import com.atlaspeak.domain.repository.NotificationScheduler
import com.atlaspeak.domain.repository.NotificationSettingsRepository
import com.atlaspeak.domain.usecase.dashboard.DashboardUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

@HiltWorker
class DailySummaryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val dashboardUseCase: DashboardUseCase,
    private val settingsRepository: NotificationSettingsRepository,
    private val permissionChecker: NotificationPermissionChecker,
    private val notificationHelper: AtlasPeakNotificationHelper,
    private val notificationScheduler: NotificationScheduler,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            val settings = settingsRepository.settings()
            if (settings.notificationsEnabled && settings.dailySummaryEnabled && permissionChecker.canPostNotifications()) {
                notificationHelper.showDailySummary(dashboardUseCase.snapshot(DashboardFilters()))
            }
            notificationScheduler.rescheduleDailySummary()
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
