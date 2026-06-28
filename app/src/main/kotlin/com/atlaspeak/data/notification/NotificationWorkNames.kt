package com.atlaspeak.data.notification

object NotificationWorkNames {
    const val DAILY_SUMMARY = "daily_summary"
    const val WEEKLY_SUMMARY = "weekly_summary"
    const val MOTIVATIONAL_MESSAGE = "motivational_message"
    const val KEY_DAY_OF_WEEK = "day_of_week"
    const val KEY_SESSION_ORDER_INDEX = "session_order_index"

    fun trainingReminder(dayOfWeek: Int): String {
        require(dayOfWeek in ISO_WEEKDAYS) { "dayOfWeek must be in 1..7" }
        return "training_reminder_$dayOfWeek"
    }

    fun trainingReminder(dayOfWeek: Int, orderIndex: Int): String {
        require(dayOfWeek in ISO_WEEKDAYS) { "dayOfWeek must be in 1..7" }
        require(orderIndex >= 0) { "orderIndex must be >= 0" }
        return "training_reminder_${dayOfWeek}_$orderIndex"
    }

    val ISO_WEEKDAYS = 1..7
}
