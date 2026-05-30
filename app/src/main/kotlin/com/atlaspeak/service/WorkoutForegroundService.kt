package com.atlaspeak.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.atlaspeak.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WorkoutTimerState(
    val sessionId: String? = null,
    val startedAt: Long? = null,
    val elapsedSeconds: Long = 0L,
    val running: Boolean = false,
    val failed: Boolean = false,
)

object WorkoutTimerRegistry {
    private val mutableState = MutableStateFlow(WorkoutTimerState())
    val state: StateFlow<WorkoutTimerState> = mutableState.asStateFlow()

    fun update(state: WorkoutTimerState) {
        mutableState.value = state
    }

    fun tick(now: Long) {
        mutableState.update { current ->
            val startedAt = current.startedAt ?: return@update current
            current.copy(elapsedSeconds = ((now - startedAt) / 1000).coerceAtLeast(0))
        }
    }
}

class WorkoutForegroundService : LifecycleService() {
    private var timerJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> startTimer(
                sessionId = requireNotNull(intent.getStringExtra(EXTRA_SESSION_ID)),
                startedAt = intent.getLongExtra(EXTRA_STARTED_AT, System.currentTimeMillis()),
            )
            ACTION_STOP -> stopTimer(clearState = true)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopTimer(clearState = !WorkoutTimerRegistry.state.value.failed)
        super.onDestroy()
    }

    private fun startTimer(sessionId: String, startedAt: Long) {
        ensureNotificationChannel()
        WorkoutTimerRegistry.update(
            WorkoutTimerState(
                sessionId = sessionId,
                startedAt = startedAt,
                elapsedSeconds = ((System.currentTimeMillis() - startedAt) / 1000).coerceAtLeast(0),
                running = true,
            ),
        )
        try {
            startForeground(NOTIFICATION_ID, buildNotification(WorkoutTimerRegistry.state.value.elapsedSeconds))
        } catch (_: SecurityException) {
            WorkoutTimerRegistry.update(
                WorkoutTimerState(
                    sessionId = sessionId,
                    startedAt = startedAt,
                    running = false,
                    failed = true,
                ),
            )
            stopSelf()
            return
        }
        timerJob?.cancel()
        timerJob = lifecycleScope.launch {
            while (true) {
                WorkoutTimerRegistry.tick(System.currentTimeMillis())
                notifyTimer(WorkoutTimerRegistry.state.value.elapsedSeconds)
                delay(1000)
            }
        }
    }

    private fun stopTimer(clearState: Boolean) {
        timerJob?.cancel()
        timerJob = null
        if (clearState) {
            WorkoutTimerRegistry.update(WorkoutTimerState())
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notifyTimer(elapsedSeconds: Long) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(elapsedSeconds))
    }

    private fun buildNotification(elapsedSeconds: Long): Notification {
        val elapsed = formatElapsed(elapsedSeconds)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(getString(R.string.workout_notification_title))
            .setContentText(getString(R.string.workout_notification_text, elapsed))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.workout_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
        )
    }

    private fun formatElapsed(seconds: Long): String {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return "%02d:%02d".format(minutes, remainingSeconds)
    }

    companion object {
        private const val CHANNEL_ID = "active_workout"
        private const val NOTIFICATION_ID = 1201
        private const val ACTION_START = "com.atlaspeak.action.START_WORKOUT_TIMER"
        private const val ACTION_STOP = "com.atlaspeak.action.STOP_WORKOUT_TIMER"
        private const val EXTRA_SESSION_ID = "session_id"
        private const val EXTRA_STARTED_AT = "started_at"

        fun startIntent(context: Context, sessionId: String, startedAt: Long): Intent {
            return Intent(context, WorkoutForegroundService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_SESSION_ID, sessionId)
                .putExtra(EXTRA_STARTED_AT, startedAt)
        }

        fun stopIntent(context: Context): Intent {
            return Intent(context, WorkoutForegroundService::class.java).setAction(ACTION_STOP)
        }
    }
}
