package com.atlaspeak.data.notification

object NotificationWorkNames {
    const val DAILY_SUMMARY = "daily_summary"
    const val WEEKLY_SUMMARY = "weekly_summary"
    const val MOTIVATIONAL_MESSAGE = "motivational_message"
    const val KEY_DAY_OF_WEEK = "day_of_week"

    fun trainingReminder(dayOfWeek: Int): String = "training_reminder_$dayOfWeek"
}
