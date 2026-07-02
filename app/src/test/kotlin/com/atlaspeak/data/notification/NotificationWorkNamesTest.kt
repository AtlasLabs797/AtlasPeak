package com.atlaspeak.data.notification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class NotificationWorkNamesTest {
    @Test
    fun `training reminder work name accepts iso weekdays only`() {
        assertEquals("training_reminder_1", NotificationWorkNames.trainingReminder(1))
        assertEquals("training_reminder_7", NotificationWorkNames.trainingReminder(7))
        assertEquals("training_reminder_1_0", NotificationWorkNames.trainingReminder(1, 0))
        assertEquals("training_reminder_1_2", NotificationWorkNames.trainingReminder(1, 2))

        assertThrows(IllegalArgumentException::class.java) {
            NotificationWorkNames.trainingReminder(0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NotificationWorkNames.trainingReminder(8)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NotificationWorkNames.trainingReminder(1, -1)
        }
    }
}
