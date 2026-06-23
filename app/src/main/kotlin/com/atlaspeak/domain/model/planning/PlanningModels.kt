package com.atlaspeak.domain.model.planning

enum class WeeklyPlanDayType {
    Strength,
    Cardio,
}

data class WeeklyPlanDay(
    val dayOfWeek: Int,
    val type: WeeklyPlanDayType = WeeklyPlanDayType.Strength,
    val routineId: String? = null,
    val routineName: String? = null,
    val cardioTypeId: String? = null,
    val cardioTypeName: String? = null,
    val cardioTargetDurationSec: Int? = null,
    val isRestDay: Boolean = false,
    val notificationEnabled: Boolean = true,
    val notificationTime: String? = null,
    val completedThisWeek: Boolean = false,
)

data class WeeklyPlanUpdate(
    val dayOfWeek: Int,
    val type: WeeklyPlanDayType = WeeklyPlanDayType.Strength,
    val routineId: String?,
    val cardioTypeId: String? = null,
    val cardioTargetDurationSec: Int? = null,
    val isRestDay: Boolean,
    val notificationEnabled: Boolean,
    val notificationTime: String?,
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
