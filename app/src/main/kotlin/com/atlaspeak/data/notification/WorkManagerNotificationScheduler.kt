package com.atlaspeak.data.notification

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.atlaspeak.domain.model.planning.NotificationSettings
import com.atlaspeak.domain.repository.NotificationScheduler
import com.atlaspeak.domain.repository.NotificationSettingsRepository
import com.atlaspeak.domain.repository.WeeklyPlanRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkManagerNotificationScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: NotificationSettingsRepository,
    private val weeklyPlanRepository: WeeklyPlanRepository,
    private val scheduleCalculator: NotificationScheduleCalculator,
    private val permissionChecker: NotificationPermissionChecker,
    private val notificationHelper: AtlasPeakNotificationHelper,
) : NotificationScheduler {
    override suspend fun rescheduleAll() {
        val workManager = WorkManager.getInstance(context)
        notificationHelper.ensureChannels()

        val settings = settingsRepository.settings()
        if (!settings.notificationsEnabled || !permissionChecker.canPostNotifications()) {
            cancelAll(workManager)
            return
        }

        val now = System.currentTimeMillis()
        val enabledWork = mutableSetOf<String>()
        weeklyPlanRepository.plan()
            .filter { !it.isRestDay && it.routineId != null && it.notificationEnabled && it.notificationTime != null }
            .forEach { day ->
                enabledWork += NotificationWorkNames.trainingReminder(day.dayOfWeek)
                enqueueTrainingReminder(workManager, now, day.dayOfWeek, requireNotNull(day.notificationTime), ExistingWorkPolicy.REPLACE)
            }

        if (settings.dailySummaryEnabled) {
            enabledWork += NotificationWorkNames.DAILY_SUMMARY
            enqueueDailySummary(workManager, now, settings, ExistingWorkPolicy.REPLACE)
        }
        if (settings.weeklySummaryEnabled) {
            enabledWork += NotificationWorkNames.WEEKLY_SUMMARY
            enqueueWeeklySummary(workManager, now, ExistingWorkPolicy.REPLACE)
        }
        if (settings.motivationalMessages) {
            enabledWork += NotificationWorkNames.MOTIVATIONAL_MESSAGE
            enqueueMotivationalMessage(workManager, now, ExistingWorkPolicy.REPLACE)
        }
        cancelDisabled(workManager, enabledWork)
    }

    override suspend fun rescheduleTrainingReminder(dayOfWeek: Int) {
        val workManager = WorkManager.getInstance(context)
        val settings = settingsRepository.settings()
        val day = weeklyPlanRepository.plan().firstOrNull { it.dayOfWeek == dayOfWeek }
        if (
            !settings.notificationsEnabled ||
            !permissionChecker.canPostNotifications() ||
            day == null ||
            day.isRestDay ||
            day.routineId == null ||
            !day.notificationEnabled ||
            day.notificationTime == null
        ) {
            workManager.cancelUniqueWork(NotificationWorkNames.trainingReminder(dayOfWeek))
            return
        }
        enqueueTrainingReminder(
            workManager = workManager,
            now = System.currentTimeMillis(),
            dayOfWeek = day.dayOfWeek,
            time = day.notificationTime,
            policy = ExistingWorkPolicy.APPEND_OR_REPLACE,
        )
    }

    override suspend fun rescheduleDailySummary() {
        val workManager = WorkManager.getInstance(context)
        val settings = settingsRepository.settings()
        if (!settings.notificationsEnabled || !settings.dailySummaryEnabled || !permissionChecker.canPostNotifications()) {
            workManager.cancelUniqueWork(NotificationWorkNames.DAILY_SUMMARY)
            return
        }
        enqueueDailySummary(workManager, System.currentTimeMillis(), settings, ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    override suspend fun rescheduleWeeklySummary() {
        val workManager = WorkManager.getInstance(context)
        val settings = settingsRepository.settings()
        if (!settings.notificationsEnabled || !settings.weeklySummaryEnabled || !permissionChecker.canPostNotifications()) {
            workManager.cancelUniqueWork(NotificationWorkNames.WEEKLY_SUMMARY)
            return
        }
        enqueueWeeklySummary(workManager, System.currentTimeMillis(), ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    override suspend fun rescheduleMotivationalMessage() {
        val workManager = WorkManager.getInstance(context)
        val settings = settingsRepository.settings()
        if (!settings.notificationsEnabled || !settings.motivationalMessages || !permissionChecker.canPostNotifications()) {
            workManager.cancelUniqueWork(NotificationWorkNames.MOTIVATIONAL_MESSAGE)
            return
        }
        enqueueMotivationalMessage(workManager, System.currentTimeMillis(), ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    private fun enqueueTrainingReminder(
        workManager: WorkManager,
        now: Long,
        dayOfWeek: Int,
        time: String,
        policy: ExistingWorkPolicy,
    ) {
        val runAt = scheduleCalculator.nextWeeklyRunAt(now, dayOfWeek, time)
        workManager.enqueueUniqueWork(
            NotificationWorkNames.trainingReminder(dayOfWeek),
            policy,
            OneTimeWorkRequestBuilder<TrainingReminderWorker>()
                .setInputData(workDataOf(NotificationWorkNames.KEY_DAY_OF_WEEK to dayOfWeek))
                .setInitialDelay(delayMillis(now, runAt), TimeUnit.MILLISECONDS)
                .build(),
        )
    }

    private fun enqueueDailySummary(
        workManager: WorkManager,
        now: Long,
        settings: NotificationSettings,
        policy: ExistingWorkPolicy,
    ) {
        val runAt = scheduleCalculator.nextDailyRunAt(now, settings.dailySummaryTime)
        workManager.enqueueUniqueWork(
            NotificationWorkNames.DAILY_SUMMARY,
            policy,
            OneTimeWorkRequestBuilder<DailySummaryWorker>()
                .setInitialDelay(delayMillis(now, runAt), TimeUnit.MILLISECONDS)
                .build(),
        )
    }

    private fun enqueueWeeklySummary(workManager: WorkManager, now: Long, policy: ExistingWorkPolicy) {
        val runAt = scheduleCalculator.nextWeeklyRunAt(now, MONDAY, NotificationSettings.DEFAULT_SUMMARY_TIME)
        workManager.enqueueUniqueWork(
            NotificationWorkNames.WEEKLY_SUMMARY,
            policy,
            OneTimeWorkRequestBuilder<WeeklySummaryWorker>()
                .setInitialDelay(delayMillis(now, runAt), TimeUnit.MILLISECONDS)
                .build(),
        )
    }

    private fun enqueueMotivationalMessage(workManager: WorkManager, now: Long, policy: ExistingWorkPolicy) {
        val runAt = scheduleCalculator.nextDailyRunAt(now, MOTIVATIONAL_MESSAGE_TIME)
        workManager.enqueueUniqueWork(
            NotificationWorkNames.MOTIVATIONAL_MESSAGE,
            policy,
            OneTimeWorkRequestBuilder<MotivationalMessageWorker>()
                .setInitialDelay(delayMillis(now, runAt), TimeUnit.MILLISECONDS)
                .build(),
        )
    }

    private fun cancelDisabled(workManager: WorkManager, enabledWork: Set<String>) {
        allWorkNames()
            .filterNot { it in enabledWork }
            .forEach(workManager::cancelUniqueWork)
    }

    private fun cancelAll(workManager: WorkManager) {
        allWorkNames().forEach(workManager::cancelUniqueWork)
    }

    private fun allWorkNames(): List<String> {
        return (1..DAYS_PER_WEEK).map(NotificationWorkNames::trainingReminder) +
            listOf(
                NotificationWorkNames.DAILY_SUMMARY,
                NotificationWorkNames.WEEKLY_SUMMARY,
                NotificationWorkNames.MOTIVATIONAL_MESSAGE,
            )
    }

    private fun delayMillis(now: Long, runAt: Long): Long = (runAt - now).coerceAtLeast(0L)

    private companion object {
        const val DAYS_PER_WEEK = 7
        const val MONDAY = 1
        const val MOTIVATIONAL_MESSAGE_TIME = "12:00"
    }
}
