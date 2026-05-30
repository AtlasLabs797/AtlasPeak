package com.atlaspeak.domain.usecase.planning

import com.atlaspeak.domain.model.planning.NotificationSettings
import com.atlaspeak.domain.repository.NotificationScheduler
import com.atlaspeak.domain.repository.NotificationSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NotificationSettingsUseCaseTest {
    private val repository = FakeNotificationSettingsRepository()
    private val scheduler = FakeNotificationScheduler()
    private val useCase = NotificationSettingsUseCase(repository, scheduler)

    @Test
    fun `settings returns repository values`() = runTest {
        repository.settings = NotificationSettings(
            notificationsEnabled = false,
            motivationalMessages = false,
            dailySummaryEnabled = false,
            dailySummaryTime = "07:45",
            weeklySummaryEnabled = true,
        )

        val settings = useCase.settings()

        assertEquals(repository.settings, settings)
    }

    @Test
    fun `update rejects invalid daily summary time`() = runTest {
        assertFalse(useCase.update(repository.settings.copy(dailySummaryTime = "99:00")))

        assertEquals("08:30", repository.settings.dailySummaryTime)
        assertEquals(0, scheduler.rescheduleAllCount)
    }

    @Test
    fun `update stores settings and reschedules notifications`() = runTest {
        assertTrue(
            useCase.update(
                NotificationSettings(
                    notificationsEnabled = true,
                    motivationalMessages = false,
                    dailySummaryEnabled = true,
                    dailySummaryTime = "06:15",
                    weeklySummaryEnabled = false,
                ),
            ),
        )

        assertEquals("06:15", repository.settings.dailySummaryTime)
        assertFalse(repository.settings.motivationalMessages)
        assertEquals(1, scheduler.rescheduleAllCount)
    }

    private class FakeNotificationSettingsRepository : NotificationSettingsRepository {
        var settings = NotificationSettings()

        override suspend fun settings(): NotificationSettings = settings

        override suspend fun update(settings: NotificationSettings) {
            this.settings = settings
        }
    }

    private class FakeNotificationScheduler : NotificationScheduler {
        var rescheduleAllCount = 0

        override suspend fun rescheduleAll() {
            rescheduleAllCount += 1
        }
    }
}
