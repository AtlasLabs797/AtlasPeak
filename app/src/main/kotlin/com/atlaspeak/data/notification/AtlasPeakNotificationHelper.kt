package com.atlaspeak.data.notification

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.atlaspeak.MainActivity
import com.atlaspeak.R
import com.atlaspeak.domain.model.dashboard.DashboardSnapshot
import com.atlaspeak.domain.model.planning.WeeklyPlanDay
import com.atlaspeak.domain.model.planning.WeeklyPlanSession
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AtlasPeakNotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissionChecker: NotificationPermissionChecker,
) {
    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channels = listOf(
            NotificationChannel(
                TRAINING_REMINDERS_CHANNEL_ID,
                context.getString(R.string.notification_channel_training_reminders),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notification_channel_training_reminders_desc)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            },
            NotificationChannel(
                MOTIVATION_CHANNEL_ID,
                context.getString(R.string.notification_channel_motivation),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notification_channel_motivation_desc)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            },
            NotificationChannel(
                SUMMARIES_CHANNEL_ID,
                context.getString(R.string.notification_channel_summaries),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notification_channel_summaries_desc)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            },
        )
        manager.createNotificationChannels(channels)
    }

    @SuppressLint("MissingPermission")
    fun showTrainingReminder(day: WeeklyPlanDay, session: WeeklyPlanSession) {
        if (!permissionChecker.canPostNotifications()) return
        ensureChannels()
        val plannedName = session.routineName
            ?: session.cardioTypeName
            ?: context.getString(R.string.notifications_training_generic_routine)
        val time = session.notificationTime ?: context.getString(R.string.notifications_time_unspecified)
        val notification = NotificationCompat.Builder(context, TRAINING_REMINDERS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(context.getString(R.string.notifications_training_title))
            .setContentText(context.getString(R.string.notifications_training_text, plannedName, time))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(R.string.notifications_training_text, plannedName, time)),
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(contentIntent(REQUEST_TRAINING))
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        NotificationManagerCompat.from(context).notify(
            TRAINING_NOTIFICATION_ID_BASE + day.dayOfWeek * 10 + session.orderIndex,
            notification,
        )
    }

    @SuppressLint("MissingPermission")
    fun showDailySummary(snapshot: DashboardSnapshot) {
        if (!permissionChecker.canPostNotifications()) return
        ensureChannels()
        showSummary(
            notificationId = DAILY_SUMMARY_NOTIFICATION_ID,
            title = context.getString(R.string.notifications_daily_summary_title),
            snapshot = snapshot,
        )
    }

    @SuppressLint("MissingPermission")
    fun showWeeklySummary(snapshot: DashboardSnapshot) {
        if (!permissionChecker.canPostNotifications()) return
        ensureChannels()
        showSummary(
            notificationId = WEEKLY_SUMMARY_NOTIFICATION_ID,
            title = context.getString(R.string.notifications_weekly_summary_title),
            snapshot = snapshot,
        )
    }

    @SuppressLint("MissingPermission")
    fun showMotivationalMessage(nowMillis: Long) {
        if (!permissionChecker.canPostNotifications()) return
        ensureChannels()
        val messages = context.resources.getStringArray(R.array.notifications_motivation_messages)
        if (messages.isEmpty()) return
        val message = messages[(nowMillis / MILLIS_PER_DAY % messages.size).toInt()]
        val notification = NotificationCompat.Builder(context, MOTIVATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(context.getString(R.string.notifications_motivation_title))
            .setContentText(message)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(contentIntent(REQUEST_MOTIVATION))
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        NotificationManagerCompat.from(context).notify(MOTIVATION_NOTIFICATION_ID, notification)
    }

    @SuppressLint("MissingPermission")
    private fun showSummary(notificationId: Int, title: String, snapshot: DashboardSnapshot) {
        val consistency = snapshot.consistency
        val recentBodyWeight = snapshot.bodyWeightPoints.maxByOrNull { it.timestamp }?.value
        val text = if (recentBodyWeight != null) {
            context.getString(
                R.string.notifications_summary_text_with_weight,
                snapshot.totalVolumeKg,
                consistency.activeDays,
                consistency.targetDays,
                recentBodyWeight,
            )
        } else {
            context.getString(
                R.string.notifications_summary_text,
                snapshot.totalVolumeKg,
                consistency.activeDays,
                consistency.targetDays,
            )
        }
        val notification = NotificationCompat.Builder(context, SUMMARIES_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(contentIntent(REQUEST_SUMMARY))
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    private fun contentIntent(requestCode: Int): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java),
            flags,
        )
    }

    companion object {
        const val TRAINING_REMINDERS_CHANNEL_ID = "training_reminders"
        const val MOTIVATION_CHANNEL_ID = "motivational_messages"
        const val SUMMARIES_CHANNEL_ID = "summaries"

        private const val TRAINING_NOTIFICATION_ID_BASE = 2100
        private const val DAILY_SUMMARY_NOTIFICATION_ID = 2201
        private const val WEEKLY_SUMMARY_NOTIFICATION_ID = 2202
        private const val MOTIVATION_NOTIFICATION_ID = 2301
        private const val REQUEST_TRAINING = 3101
        private const val REQUEST_SUMMARY = 3102
        private const val REQUEST_MOTIVATION = 3103
        private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1_000L
    }
}
