package com.atlaspeak.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
    val restTimer: WorkoutRestTimerState? = null,
)

data class WorkoutRestTimerState(
    val id: String,
    val totalSeconds: Int,
    val endsAtMillis: Long,
    val remainingSeconds: Int,
    val alerting: Boolean,
    val soundEnabled: Boolean,
    val vibrationEnabled: Boolean,
) {
    fun tick(nowMillis: Long): WorkoutRestTimerState {
        val remainingMillis = (endsAtMillis - nowMillis).coerceAtLeast(0)
        val nextRemaining = if (remainingMillis == 0L) {
            0
        } else {
            ((remainingMillis + 999L) / 1000L).toInt().coerceIn(0, totalSeconds)
        }
        return copy(
            remainingSeconds = nextRemaining,
            alerting = alerting || nowMillis >= endsAtMillis,
        )
    }
}

object WorkoutTimerRegistry {
    private val mutableState = MutableStateFlow(WorkoutTimerState())
    val state: StateFlow<WorkoutTimerState> = mutableState.asStateFlow()

    fun update(state: WorkoutTimerState) {
        mutableState.value = state
    }

    fun tick(now: Long) {
        mutableState.update { current ->
            val startedAt = current.startedAt ?: return@update current
            current.copy(
                elapsedSeconds = ((now - startedAt) / 1000).coerceAtLeast(0),
                restTimer = current.restTimer?.tick(now),
            )
        }
    }

    fun startRestTimer(
        id: String,
        totalSeconds: Int,
        endsAtMillis: Long,
        soundEnabled: Boolean,
        vibrationEnabled: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        if (totalSeconds <= 0) return
        val restTimer = WorkoutRestTimerState(
            id = id,
            totalSeconds = totalSeconds,
            endsAtMillis = endsAtMillis,
            remainingSeconds = totalSeconds,
            alerting = false,
            soundEnabled = soundEnabled,
            vibrationEnabled = vibrationEnabled,
        ).tick(nowMillis)
        mutableState.update { it.copy(restTimer = restTimer) }
    }

    fun clearRestTimer() {
        mutableState.update { it.copy(restTimer = null) }
    }
}

class WorkoutForegroundService : LifecycleService() {
    private var timerJob: Job? = null
    private var restSoundJob: Job? = null
    private var restToneGenerator: ToneGenerator? = null
    private var activeAlertRestId: String? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> startTimer(
                sessionId = requireNotNull(intent.getStringExtra(EXTRA_SESSION_ID)),
                startedAt = intent.getLongExtra(EXTRA_STARTED_AT, System.currentTimeMillis()),
            )
            ACTION_START_REST -> startRestTimer(
                sessionId = requireNotNull(intent.getStringExtra(EXTRA_SESSION_ID)),
                startedAt = intent.getLongExtra(EXTRA_STARTED_AT, System.currentTimeMillis()),
                restId = requireNotNull(intent.getStringExtra(EXTRA_REST_ID)),
                totalSeconds = intent.getIntExtra(EXTRA_REST_TOTAL_SECONDS, 0),
                endsAtMillis = intent.getLongExtra(EXTRA_REST_ENDS_AT, System.currentTimeMillis()),
                soundEnabled = intent.getBooleanExtra(EXTRA_REST_SOUND_ENABLED, true),
                vibrationEnabled = intent.getBooleanExtra(EXTRA_REST_VIBRATION_ENABLED, true),
            )
            ACTION_CLEAR_REST -> clearRestTimer()
            ACTION_STOP -> stopTimer(clearState = true)
        }
        // START_NOT_STICKY avoids zombie timers after process pressure relaunches with null intent.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopTimer(clearState = !WorkoutTimerRegistry.state.value.failed)
        super.onDestroy()
    }

    private fun startTimer(sessionId: String, startedAt: Long) {
        ensureNotificationChannel()
        if (!ensureTimerRunning(sessionId, startedAt)) return
        notifyTimer(WorkoutTimerRegistry.state.value)
    }

    private fun startRestTimer(
        sessionId: String,
        startedAt: Long,
        restId: String,
        totalSeconds: Int,
        endsAtMillis: Long,
        soundEnabled: Boolean,
        vibrationEnabled: Boolean,
    ) {
        ensureNotificationChannel()
        if (!ensureTimerRunning(sessionId, startedAt)) return
        stopRestAlert()
        WorkoutTimerRegistry.startRestTimer(
            id = restId,
            totalSeconds = totalSeconds,
            endsAtMillis = endsAtMillis,
            soundEnabled = soundEnabled,
            vibrationEnabled = vibrationEnabled,
        )
        val state = WorkoutTimerRegistry.state.value
        syncRestAlert(state)
        notifyTimer(state)
    }

    private fun ensureTimerRunning(sessionId: String, startedAt: Long): Boolean {
        val existing = WorkoutTimerRegistry.state.value
        if (existing.sessionId == sessionId && existing.startedAt != null && existing.running) {
            return true
        }
        WorkoutTimerRegistry.update(
            WorkoutTimerState(
                sessionId = sessionId,
                startedAt = startedAt,
                elapsedSeconds = ((System.currentTimeMillis() - startedAt) / 1000).coerceAtLeast(0),
                running = true,
            ),
        )
        try {
            startForeground(NOTIFICATION_ID, buildNotification(WorkoutTimerRegistry.state.value))
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
            return false
        }
        timerJob?.cancel()
        timerJob = lifecycleScope.launch {
            while (true) {
                WorkoutTimerRegistry.tick(System.currentTimeMillis())
                val state = WorkoutTimerRegistry.state.value
                syncRestAlert(state)
                notifyTimer(state)
                delay(1000)
            }
        }
        return true
    }

    private fun stopTimer(clearState: Boolean) {
        timerJob?.cancel()
        timerJob = null
        stopRestAlert()
        if (clearState) {
            WorkoutTimerRegistry.update(WorkoutTimerState())
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun clearRestTimer() {
        WorkoutTimerRegistry.clearRestTimer()
        stopRestAlert()
        val state = WorkoutTimerRegistry.state.value
        if (state.running) {
            notifyTimer(state)
        } else {
            stopSelf()
        }
    }

    private fun syncRestAlert(state: WorkoutTimerState) {
        val restTimer = state.restTimer
        if (restTimer?.alerting == true) {
            startRestAlert(restTimer)
        } else {
            stopRestAlert()
        }
    }

    private fun startRestAlert(restTimer: WorkoutRestTimerState) {
        if (activeAlertRestId == restTimer.id) return
        stopRestAlert()
        activeAlertRestId = restTimer.id
        if (restTimer.vibrationEnabled) {
            vibrator().vibrate(VibrationEffect.createWaveform(REST_VIBRATION_PATTERN_MS, 0))
        }
        if (restTimer.soundEnabled) {
            restToneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, REST_TONE_VOLUME)
            restSoundJob = lifecycleScope.launch {
                while (true) {
                    try {
                        restToneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, REST_TONE_DURATION_MS)
                    } catch (_: RuntimeException) {
                        stopRestTone()
                        return@launch
                    }
                    delay(REST_TONE_INTERVAL_MS)
                }
            }
        }
    }

    private fun stopRestAlert() {
        if (activeAlertRestId == null && restSoundJob == null && restToneGenerator == null) return
        activeAlertRestId = null
        stopRestTone()
        vibrator().cancel()
    }

    private fun stopRestTone() {
        restSoundJob?.cancel()
        restSoundJob = null
        restToneGenerator?.stopTone()
        restToneGenerator?.release()
        restToneGenerator = null
    }

    private fun vibrator(): Vibrator {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
    }

    private fun notifyTimer(state: WorkoutTimerState) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(state))
    }

    private fun buildNotification(state: WorkoutTimerState): Notification {
        val restTimer = state.restTimer
        val elapsed = formatElapsed(state.elapsedSeconds)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(getString(R.string.workout_notification_title))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        when {
            restTimer?.alerting == true -> {
                builder
                    .setContentText(getString(R.string.workout_notification_rest_done))
                    .addAction(
                        R.drawable.ic_launcher_monochrome,
                        getString(R.string.action_stop),
                        servicePendingIntent(ACTION_CLEAR_REST, REQUEST_CLEAR_REST),
                    )
            }
            restTimer != null -> {
                builder
                    .setContentText(
                        getString(
                            R.string.workout_notification_rest_countdown,
                            formatElapsed(restTimer.remainingSeconds.toLong()),
                        ),
                    )
                    .addAction(
                        R.drawable.ic_launcher_monochrome,
                        getString(R.string.action_skip),
                        servicePendingIntent(ACTION_CLEAR_REST, REQUEST_CLEAR_REST),
                    )
            }
            else -> {
                builder.setContentText(getString(R.string.workout_notification_text, elapsed))
            }
        }
        return builder.build()
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getService(
            this,
            requestCode,
            Intent(this, WorkoutForegroundService::class.java).setAction(action),
            flags,
        )
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
        private const val ACTION_START_REST = "com.atlaspeak.action.START_REST_TIMER"
        private const val ACTION_CLEAR_REST = "com.atlaspeak.action.CLEAR_REST_TIMER"
        private const val EXTRA_SESSION_ID = "session_id"
        private const val EXTRA_STARTED_AT = "started_at"
        private const val EXTRA_REST_ID = "rest_id"
        private const val EXTRA_REST_TOTAL_SECONDS = "rest_total_seconds"
        private const val EXTRA_REST_ENDS_AT = "rest_ends_at"
        private const val EXTRA_REST_SOUND_ENABLED = "rest_sound_enabled"
        private const val EXTRA_REST_VIBRATION_ENABLED = "rest_vibration_enabled"
        private const val REQUEST_CLEAR_REST = 1202
        private const val REST_TONE_VOLUME = 80
        private const val REST_TONE_DURATION_MS = 420
        private const val REST_TONE_INTERVAL_MS = 1_000L
        private val REST_VIBRATION_PATTERN_MS = longArrayOf(0L, 450L, 650L)

        fun startIntent(context: Context, sessionId: String, startedAt: Long): Intent {
            return Intent(context, WorkoutForegroundService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_SESSION_ID, sessionId)
                .putExtra(EXTRA_STARTED_AT, startedAt)
        }

        fun startRestIntent(
            context: Context,
            sessionId: String,
            startedAt: Long,
            restId: String,
            totalSeconds: Int,
            endsAtMillis: Long,
            soundEnabled: Boolean,
            vibrationEnabled: Boolean,
        ): Intent {
            return Intent(context, WorkoutForegroundService::class.java)
                .setAction(ACTION_START_REST)
                .putExtra(EXTRA_SESSION_ID, sessionId)
                .putExtra(EXTRA_STARTED_AT, startedAt)
                .putExtra(EXTRA_REST_ID, restId)
                .putExtra(EXTRA_REST_TOTAL_SECONDS, totalSeconds)
                .putExtra(EXTRA_REST_ENDS_AT, endsAtMillis)
                .putExtra(EXTRA_REST_SOUND_ENABLED, soundEnabled)
                .putExtra(EXTRA_REST_VIBRATION_ENABLED, vibrationEnabled)
        }

        fun clearRestIntent(context: Context): Intent {
            return Intent(context, WorkoutForegroundService::class.java).setAction(ACTION_CLEAR_REST)
        }

        fun stopIntent(context: Context): Intent {
            return Intent(context, WorkoutForegroundService::class.java).setAction(ACTION_STOP)
        }
    }
}
