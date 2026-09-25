package com.atlaspeak.presentation.theme

import com.atlaspeak.R
import com.atlaspeak.domain.model.settings.AppThemeMode
import com.atlaspeak.domain.repository.AppSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppThemeViewModelTest {
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
    fun `set theme emits saved feedback after repository update`() = runTest {
        val repository = FakeAppSettingsRepository()
        val viewModel = AppThemeViewModel(repository)
        val events = mutableListOf<ThemeSaveEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.saveEvents.toList(events)
        }

        viewModel.setThemeMode(AppThemeMode.Dark)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(AppThemeMode.Dark, repository.savedMode)
        assertEquals(R.string.theme_settings_saved, events.single().messageRes)
        assertFalse(events.single().isError)
    }

    @Test
    fun `set theme emits error feedback when repository update fails`() = runTest {
        val viewModel = AppThemeViewModel(FakeAppSettingsRepository(failOnSave = true))
        val events = mutableListOf<ThemeSaveEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.saveEvents.toList(events)
        }

        viewModel.setThemeMode(AppThemeMode.Light)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(R.string.error_generic, events.single().messageRes)
        assertTrue(events.single().isError)
    }

    @Test
    fun `isLoaded stays false until the repository emits a theme value`() = runTest {
        val viewModel = AppThemeViewModel(FakeAppSettingsRepository())

        assertFalse(viewModel.isLoaded.value)

        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.themeMode.collect {}
        }
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.isLoaded.value)
        collector.cancel()
    }

    @Test
    fun `theme falls back to System and isLoaded still completes when the database is unavailable`() = runTest {
        val repository = FakeAppSettingsRepository(observeThemeModeFails = true)
        val viewModel = AppThemeViewModel(repository)

        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.themeMode.collect {}
        }
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.isLoaded.value)
        assertEquals(AppThemeMode.System, viewModel.themeMode.value)
        collector.cancel()
    }

    private class FakeAppSettingsRepository(
        private val failOnSave: Boolean = false,
        private val observeThemeModeFails: Boolean = false,
    ) : AppSettingsRepository {
        private val themeMode = MutableStateFlow(AppThemeMode.System)
        var savedMode: AppThemeMode? = null

        override fun observeThemeMode(): Flow<AppThemeMode> =
            if (observeThemeModeFails) flow { throw IllegalStateException("db unavailable") } else themeMode

        override suspend fun setThemeMode(mode: AppThemeMode) {
            if (failOnSave) error("save failed")
            savedMode = mode
            themeMode.value = mode
        }
    }
}
