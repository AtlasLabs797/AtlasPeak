package com.atlaspeak.domain.repository

interface NotificationScheduler {
    suspend fun rescheduleAll()

    suspend fun rescheduleTrainingReminder(dayOfWeek: Int) {
        rescheduleAll()
    }

    suspend fun rescheduleDailySummary() {
        rescheduleAll()
    }

    suspend fun rescheduleWeeklySummary() {
        rescheduleAll()
    }

    suspend fun rescheduleMotivationalMessage() {
        rescheduleAll()
    }
}
