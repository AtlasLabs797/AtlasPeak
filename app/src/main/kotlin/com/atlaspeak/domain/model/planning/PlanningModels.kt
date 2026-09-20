package com.atlaspeak.domain.model.planning

enum class WeeklyPlanDayType {
    Strength,
    Cardio,
}

data class WeeklyPlanSession(
    val id: String,
    val dayOfWeek: Int,
    val orderIndex: Int,
    val type: WeeklyPlanDayType = WeeklyPlanDayType.Strength,
    val routineId: String? = null,
    val routineName: String? = null,
    val cardioTypeId: String? = null,
    val cardioTypeName: String? = null,
    val cardioTargetDurationSec: Int? = null,
    val notificationEnabled: Boolean = true,
    val notificationTime: String? = null,
    val completedThisWeek: Boolean = false,
)

data class WeeklyPlanDay(
    val dayOfWeek: Int,
    val isRestDay: Boolean = false,
    val sessions: List<WeeklyPlanSession> = emptyList(),
) {
    val completedThisWeek: Boolean = sessions.isNotEmpty() && sessions.all { it.completedThisWeek }

    /**
     * Backwards-compatible primary-session accessors for callers that still render one CTA.
     * New planning code should use [sessions].
     */
    val primarySession: WeeklyPlanSession? = sessions.firstOrNull()
    val type: WeeklyPlanDayType = primarySession?.type ?: WeeklyPlanDayType.Strength
    val routineId: String? = primarySession?.routineId
    val routineName: String? = primarySession?.routineName
    val cardioTypeId: String? = primarySession?.cardioTypeId
    val cardioTypeName: String? = primarySession?.cardioTypeName
    val cardioTargetDurationSec: Int? = primarySession?.cardioTargetDurationSec
    val notificationEnabled: Boolean = primarySession?.notificationEnabled ?: false
    val notificationTime: String? = primarySession?.notificationTime
}

data class WeeklyPlanSessionUpdate(
    val id: String? = null,
    val type: WeeklyPlanDayType = WeeklyPlanDayType.Strength,
    val routineId: String? = null,
    val cardioTypeId: String? = null,
    val cardioTargetDurationSec: Int? = null,
    val notificationEnabled: Boolean = true,
    val notificationTime: String? = null,
)

data class WeeklyPlanUpdate(
    val dayOfWeek: Int,
    val sessions: List<WeeklyPlanSessionUpdate> = emptyList(),
    val type: WeeklyPlanDayType = WeeklyPlanDayType.Strength,
    val routineId: String? = null,
    val cardioTypeId: String? = null,
    val cardioTargetDurationSec: Int? = null,
    val isRestDay: Boolean = false,
    val notificationEnabled: Boolean,
    val notificationTime: String?,
)

data class WeeklyPlanCompletionKey(
    val dayOfWeek: Int,
    val type: WeeklyPlanDayType,
    val targetId: String,
    // BUG-097 (Fase 8 P1): si la sesion completada referencia una entrada
    // concreta del plan semanal, este campo lleva su id. El caso de uso del
    // plan mira primero esta clave antes de recurrir al fallback por
    // (day, type, targetId), que se mantiene para sesiones iniciadas sin
    // plan (compatibilidad hacia atras).
    val planSessionId: String? = null,
)

data class NotificationSettings(
    val notificationsEnabled: Boolean = true,
    val motivationalMessages: Boolean = true,
    val dailySummaryEnabled: Boolean = true,
    val dailySummaryTime: String = DEFAULT_SUMMARY_TIME,
    val weeklySummaryEnabled: Boolean = true,
) {
    companion object {
        const val DEFAULT_SUMMARY_TIME = "08:30"
    }
}
