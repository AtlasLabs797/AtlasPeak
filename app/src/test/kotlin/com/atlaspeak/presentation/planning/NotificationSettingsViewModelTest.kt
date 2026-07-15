package com.atlaspeak.presentation.planning

import com.atlaspeak.R
import com.atlaspeak.domain.model.planning.NotificationSettings
import com.atlaspeak.domain.repository.NotificationScheduler
import com.atlaspeak.domain.repository.NotificationSettingsRepository
import com.atlaspeak.domain.usecase.planning.NotificationSettingsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationSettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `save success exposes visual success feedback`() = runTest {
        val viewModel = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.save()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(R.string.notification_settings_saved, viewModel.state.value.messageRes)
        assertEquals(NotificationSettingsFeedbackTone.Success, viewModel.state.value.messageTone)
    }

    @Test
    fun `invalid save exposes visual error feedback`() = runTest {
        val viewModel = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.setDailySummaryTime("99:99")
        viewModel.save()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(R.string.weekly_plan_invalid_time, viewModel.state.value.messageRes)
        assertEquals(NotificationSettingsFeedbackTone.Error, viewModel.state.value.messageTone)
    }

    @Test
    fun `theme feedback can be surfaced by settings state`() = runTest {
        val viewModel = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.showFeedback(R.string.theme_settings_saved, NotificationSettingsFeedbackTone.Success)

        assertEquals(R.string.theme_settings_saved, viewModel.state.value.messageRes)
        assertEquals(NotificationSettingsFeedbackTone.Success, viewModel.state.value.messageTone)
    }

    private fun viewModel(): NotificationSettingsViewModel {
        return NotificationSettingsViewModel(
            NotificationSettingsUseCase(
                repository = FakeNotificationSettingsRepository(),
                notificationScheduler = FakeNotificationScheduler(),
            ),
        )
    }

    private class FakeNotificationSettingsRepository : NotificationSettingsRepository {
        private var settings = NotificationSettings()

        override suspend fun settings(): NotificationSettings = settings

        override suspend fun update(settings: NotificationSettings) {
            this.settings = settings
        }
    }

    private class FakeNotificationScheduler : NotificationScheduler {
        override suspend fun rescheduleAll() = Unit
    }
}
