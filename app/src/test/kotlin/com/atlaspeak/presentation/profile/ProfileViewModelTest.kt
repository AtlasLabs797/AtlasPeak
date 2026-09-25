package com.atlaspeak.presentation.profile

import com.atlaspeak.R
import com.atlaspeak.domain.model.profile.UserProfile
import com.atlaspeak.domain.repository.ProfileRepository
import com.atlaspeak.domain.usecase.privacy.DeleteAllUserDataUseCase
import com.atlaspeak.domain.usecase.privacy.UserDataEraser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
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
class ProfileViewModelTest {
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
    fun `refresh loads display name from repository`() = runTest {
        val profileRepository = FakeProfileRepository(
            UserProfile(displayName = "Ada", age = null, heightCm = null, gender = null, goalType = null),
        )
        val viewModel = viewModel(profileRepository = profileRepository)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Ada", viewModel.state.value.displayName)
    }

    @Test
    fun `confirming deletion with drive success closes dialog and emits DataDeleted`() = runTest {
        val eraser = FakeUserDataEraser(driveDeletionSucceeds = true)
        val viewModel = viewModel(eraser = eraser)
        dispatcher.scheduler.advanceUntilIdle()
        val events = mutableListOf<ProfileEvent>()
        val collector = CoroutineScope(dispatcher).launch { viewModel.events.toList(events) }

        viewModel.requestDeleteAllData()
        assertTrue(viewModel.state.value.showDeleteAllDataDialog)

        viewModel.confirmDeleteAllData()
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.showDeleteAllDataDialog)
        assertFalse(viewModel.state.value.isDeletingAllData)
        assertEquals(null, viewModel.state.value.deleteAllDataMessageRes)
        assertEquals(listOf(ProfileEvent.DataDeleted(driveNotDeleted = false)), events)
        collector.cancel()
    }

    @Test
    fun `partial success shows message and still emits DataDeleted`() = runTest {
        val eraser = FakeUserDataEraser(driveDeletionSucceeds = false)
        val viewModel = viewModel(eraser = eraser)
        dispatcher.scheduler.advanceUntilIdle()
        val events = mutableListOf<ProfileEvent>()
        val collector = CoroutineScope(dispatcher).launch { viewModel.events.toList(events) }

        viewModel.requestDeleteAllData()
        viewModel.setDeleteDriveBackupsToo(true)
        viewModel.confirmDeleteAllData()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(R.string.delete_all_data_partial_success, viewModel.state.value.deleteAllDataMessageRes)
        assertEquals(listOf(ProfileEvent.DataDeleted(driveNotDeleted = true)), events)
        collector.cancel()
    }

    @Test
    fun `failure shows error message and does not navigate`() = runTest {
        val eraser = FakeUserDataEraser(driveDeletionSucceeds = true, throwOnErase = true)
        val viewModel = viewModel(eraser = eraser)
        dispatcher.scheduler.advanceUntilIdle()
        val events = mutableListOf<ProfileEvent>()
        val collector = CoroutineScope(dispatcher).launch { viewModel.events.toList(events) }

        viewModel.requestDeleteAllData()
        viewModel.confirmDeleteAllData()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(R.string.delete_all_data_failed, viewModel.state.value.deleteAllDataMessageRes)
        assertTrue(events.isEmpty())
        collector.cancel()
    }

    @Test
    fun `confirming twice while deleting only runs the use case once`() = runTest {
        val eraser = FakeUserDataEraser(driveDeletionSucceeds = true)
        val viewModel = viewModel(eraser = eraser)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.requestDeleteAllData()
        viewModel.confirmDeleteAllData()
        viewModel.confirmDeleteAllData()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, eraser.eraseLocalDataCalls)
    }

    @Test
    fun `cancelling the dialog does not call the use case`() = runTest {
        val eraser = FakeUserDataEraser(driveDeletionSucceeds = true)
        val viewModel = viewModel(eraser = eraser)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.requestDeleteAllData()
        viewModel.cancelDeleteAllData()
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.showDeleteAllDataDialog)
        assertEquals(0, eraser.eraseLocalDataCalls)
    }

    private fun viewModel(
        profileRepository: ProfileRepository = FakeProfileRepository(null),
        eraser: UserDataEraser = FakeUserDataEraser(driveDeletionSucceeds = true),
    ): ProfileViewModel = ProfileViewModel(
        profileRepository = profileRepository,
        deleteAllUserDataUseCase = DeleteAllUserDataUseCase(eraser),
    )

    private class FakeProfileRepository(private val profile: UserProfile?) : ProfileRepository {
        override suspend fun getProfile(): UserProfile? = profile
        override suspend fun saveProfile(profile: UserProfile) = Unit
    }

    private class FakeUserDataEraser(
        private val driveDeletionSucceeds: Boolean,
        private val throwOnErase: Boolean = false,
    ) : UserDataEraser {
        var eraseLocalDataCalls = 0

        override suspend fun cancelBackgroundWork() = Unit

        override suspend fun deleteDriveBackups(): Boolean = driveDeletionSucceeds

        override suspend fun eraseLocalData() {
            eraseLocalDataCalls += 1
            if (throwOnErase) error("boom")
        }
    }
}
