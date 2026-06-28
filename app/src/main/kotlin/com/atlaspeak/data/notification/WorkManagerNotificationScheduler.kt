package com.atlaspeak.data.notification

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.atlaspeak.domain.model.planning.NotificationSettings
import com.atlaspeak.domain.model.planning.WeeklyPlanSession
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
            .filter { !it.isRestDay }
            .forEach { day ->
                day.sessions
                    .filter { it.hasPlannedTarget() && it.notificationEnabled && it.notificationTime != null }
                    .forEach { session ->
                        val workName = NotificationWorkNames.trainingReminder(day.dayOfWeek, session.orderIndex)
                        enabledWork += workName
                        enqueueTrainingReminder(
                            workManager = workManager,
                            now = now,
                            dayOfWeek = day.dayOfWeek,
                            orderIndex = session.orderIndex,
                            time = requireNotNull(session.notificationTime),
                            policy = ExistingWorkPolicy.REPLACE,
                        )
                    }
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
        val candidateSessions = day?.sessions.orEmpty()
            .filter { it.hasPlannedTarget() && it.notificationEnabled && it.notificationTime != null }
        if (
            !settings.notificationsEnabled ||
            !permissionChecker.canPostNotifications() ||
            day == null ||
            day.isRestDay ||
            candidateSessions.isEmpty()
        ) {
            trainingReminderNamesForDay(dayOfWeek).forEach(workManager::cancelUniqueWork)
            return
        }
        val enabledWork = mutableSetOf<String>()
        val now = System.currentTimeMillis()
        candidateSessions.forEach { session ->
            val workName = NotificationWorkNames.trainingReminder(day.dayOfWeek, session.orderIndex)
            enabledWork += workName
            enqueueTrainingReminder(
                workManager = workManager,
                now = now,
                dayOfWeek = day.dayOfWeek,
                orderIndex = session.orderIndex,
                time = requireNotNull(session.notificationTime),
                policy = ExistingWorkPolicy.APPEND_OR_REPLACE,
            )
        }
        trainingReminderNamesForDay(dayOfWeek)
            .filterNot { it in enabledWork }
            .forEach(workManager::cancelUniqueWork)
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
        orderIndex: Int,
        time: String,
        policy: ExistingWorkPolicy,
    ) {
        val runAt = scheduleCalculator.nextWeeklyRunAt(now, dayOfWeek, time)
        workManager.enqueueUniqueWork(
            NotificationWorkNames.trainingReminder(dayOfWeek, orderIndex),
            policy,
            OneTimeWorkRequestBuilder<TrainingReminderWorker>()
                .setInputData(
                    workDataOf(
                        NotificationWorkNames.KEY_DAY_OF_WEEK to dayOfWeek,
                        NotificationWorkNames.KEY_SESSION_ORDER_INDEX to orderIndex,
                    ),
                )
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
        return (1..DAYS_PER_WEEK).flatMap(::trainingReminderNamesForDay) +
            listOf(
                NotificationWorkNames.DAILY_SUMMARY,
                NotificationWorkNames.WEEKLY_SUMMARY,
                NotificationWorkNames.MOTIVATIONAL_MESSAGE,
            )
    }

    private fun delayMillis(now: Long, runAt: Long): Long = (runAt - now).coerceAtLeast(0L)

    private fun trainingReminderNamesForDay(dayOfWeek: Int): List<String> {
        return listOf(NotificationWorkNames.trainingReminder(dayOfWeek)) +
            (0..MAX_SESSIONS_PER_DAY).map { orderIndex ->
                NotificationWorkNames.trainingReminder(dayOfWeek, orderIndex)
            }
    }

    private companion object {
        const val DAYS_PER_WEEK = 7
        const val MAX_SESSIONS_PER_DAY = 8
        const val MONDAY = 1
        const val MOTIVATIONAL_MESSAGE_TIME = "12:00"
    }
}

private fun WeeklyPlanSession.hasPlannedTarget(): Boolean {
    return routineId != null || cardioTypeId != null
}
