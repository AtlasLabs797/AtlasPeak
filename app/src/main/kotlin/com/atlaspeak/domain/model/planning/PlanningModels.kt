package com.atlaspeak.domain.model.planning

data class WeeklyPlanDay(
    val dayOfWeek: Int,
    val routineId: String? = null,
    val routineName: String? = null,
    val isRestDay: Boolean = false,
    val notificationEnabled: Boolean = true,
    val notificationTime: String? = null,
    val completedThisWeek: Boolean = false,
)

data class WeeklyPlanUpdate(
    val dayOfWeek: Int,
    val routineId: String?,
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
