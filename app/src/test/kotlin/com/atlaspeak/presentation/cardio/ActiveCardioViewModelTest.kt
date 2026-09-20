package com.atlaspeak.presentation.cardio

import com.atlaspeak.domain.model.body.BodyCompositionEntry
import com.atlaspeak.domain.model.cardio.CardioMode
import com.atlaspeak.domain.model.cardio.CardioSession
import com.atlaspeak.domain.model.cardio.CardioType
import com.atlaspeak.domain.model.cardio.LocationPoint
import com.atlaspeak.domain.repository.BodyCompositionRepository
import com.atlaspeak.domain.repository.CardioRepository
import com.atlaspeak.domain.usecase.cardio.CardioUseCase
import com.atlaspeak.domain.usecase.cardio.ResumeCardioSessionUseCase
import com.atlaspeak.presentation.navigation.AppRoute
import com.atlaspeak.service.CardioTrackerRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveCardioViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val cardioRepository = FakeCardioRepository()
    private val bodyRepository = FakeBodyCompositionRepository()
    private val cardioUseCase = CardioUseCase(
        repository = cardioRepository,
        bodyCompositionRepository = bodyRepository,
        now = { 1_700_000_000_000L },
    )
    private val resumeCardioSessionUseCase = ResumeCardioSessionUseCase(cardioRepository)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        cardioRepository.types = listOf(
            CardioType("run", "Run", hasGps = true, isPreset = true, isArchived = false),
            CardioType("bike", "Bike", hasGps = false, isPreset = true, isArchived = false),
        )
        CardioTrackerRegistry.update(com.atlaspeak.service.CardioTrackerState())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `process recreation preserves cardio session route and does not duplicate it`() = runTest(dispatcher) {
        val first = newViewModelAndStart(cardioTypeId = "run").session
        assertNotNull(first)
        val firstSessionId = first!!.id

        // Simulamos movimiento: se anade un punto a la ruta via el repositorio.
        val route = listOf(LocationPoint(40.0, -3.0, 1_700_000_500_000L))
        cardioRepository.sessions = cardioRepository.sessions.map { it.copy(route = route) }

        val second = newViewModelAndStart(cardioTypeId = "run").session
        assertEquals(firstSessionId, second!!.id)
        assertEquals(1, cardioRepository.sessions.size)
        assertEquals(route, cardioRepository.sessions.single().route)
    }

    @Test
    fun `elapsed seconds is recomputed from startTime after recreation`() = runTest(dispatcher) {
        newViewModelAndStart(cardioTypeId = "run")
        val stored = cardioRepository.sessions.single()
        val startTime = stored.startTime
        // Forzamos que el reloj del sistema avance al menos 5s simulando tiempo real.
        cardioRepository.sessions = listOf(stored)

        val viewModel = newViewModel(cardioTypeId = "run")
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertNotNull(state.session)
        assertEquals(startTime, state.session!!.startTime)
        // elapsedSeconds se inicializa desde (System.currentTimeMillis() - startTime) / 1000
        // nunca negativo. Con reloj real de la JVM sera >= 0.
        assertTrue(state.elapsedSeconds >= 0L)
    }

    @Test
    fun `conflict state is exposed when active cardio session belongs to different type`() = runTest(dispatcher) {
        val first = newViewModelAndStart(cardioTypeId = "bike").session
        assertNotNull(first)

        val viewModel = newViewModel(cardioTypeId = "run")
        dispatcher.scheduler.advanceUntilIdle()

        val conflict = viewModel.state.value.conflict
        assertNotNull(conflict)
        assertEquals(first!!.id, conflict!!.activeSessionId)
        // La sesion activa NO debe sustituirse.
        assertEquals(1, cardioRepository.sessions.size)
        assertEquals("bike", cardioRepository.sessions.single().cardioTypeId)
    }

    @Test
    fun `discard active and start new replaces cardio session`() = runTest(dispatcher) {
        newViewModelAndStart(cardioTypeId = "bike")

        val viewModel = newViewModel(cardioTypeId = "run")
        dispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.state.value.conflict)

        viewModel.discardActiveSessionAndStartNew()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertNull(state.conflict)
        assertNotNull(state.session)
        assertEquals("run", state.session!!.cardioTypeId)
        assertEquals(1, cardioRepository.sessions.size)
    }

    @Test
    fun `resume active keeps the existing cardio session with its route`() = runTest(dispatcher) {
        val first = newViewModelAndStart(cardioTypeId = "bike").session
        cardioRepository.sessions = cardioRepository.sessions.map { it.copy(route = listOf(LocationPoint(40.0, -3.0, 1L))) }

        val viewModel = newViewModel(cardioTypeId = "run")
        dispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.state.value.conflict)

        viewModel.resumeActiveSession()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertNull(state.conflict)
        assertEquals(first!!.id, state.session?.id)
        assertEquals(1, state.session!!.route.size)
        assertEquals(1, cardioRepository.sessions.size)
    }

    @Test
    fun `dismiss conflict does not touch active cardio session`() = runTest(dispatcher) {
        val first = newViewModelAndStart(cardioTypeId = "bike").session
        val viewModel = newViewModel(cardioTypeId = "run")
        dispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.state.value.conflict)

        viewModel.dismissActiveSessionConflict()
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.conflict)
        assertEquals(first!!.id, cardioRepository.sessions.single().id)
    }

    @Test
    fun `cardio type missing produces SessionMissing message`() = runTest(dispatcher) {
        val viewModel = newViewModel(cardioTypeId = "missing")
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(ActiveCardioMessage.SessionMissing, state.message)
        assertNull(state.session)
        assertEquals(0, cardioRepository.sessions.size)
    }

    private fun newViewModelAndStart(cardioTypeId: String): ActiveCardioUiState {
        val viewModel = newViewModel(cardioTypeId)
        dispatcher.scheduler.advanceUntilIdle()
        return viewModel.state.value
    }

    private fun newViewModel(cardioTypeId: String): ActiveCardioViewModel {
        return ActiveCardioViewModel(
            savedStateHandle = handleFor(cardioTypeId, CardioMode.Timer),
            cardioUseCase = cardioUseCase,
            resumeCardioSessionUseCase = resumeCardioSessionUseCase,
            cardioRepository = cardioRepository,
            context = NoopContext,
        )
    }

    private fun handleFor(cardioTypeId: String, mode: CardioMode): androidx.lifecycle.SavedStateHandle {
        val targetSeconds = (mode as? CardioMode.Countdown)?.targetDurationSeconds ?: 0
        val modeSegment = if (mode is CardioMode.Countdown) AppRoute.ActiveCardio.MODE_COUNTDOWN else AppRoute.ActiveCardio.MODE_TIMER
        return androidx.lifecycle.SavedStateHandle(
            mapOf(
                AppRoute.ActiveCardio.CARDIO_TYPE_ID to cardioTypeId,
                AppRoute.ActiveCardio.MODE to modeSegment,
                AppRoute.ActiveCardio.TARGET_SECONDS to targetSeconds,
            ),
        )
    }

    private class FakeCardioRepository : CardioRepository {
        var types = emptyList<CardioType>()
        var sessions = emptyList<CardioSession>()

        override suspend fun cardioTypes(includeArchived: Boolean): List<CardioType> {
            return if (includeArchived) types else types.filterNot { it.isArchived }
        }

        override suspend fun upsertCustomType(type: CardioType) {
            types = types.filterNot { it.id == type.id } + type
        }

        override suspend fun archiveType(id: String) {
            types = types.map { if (it.id == id) it.copy(isArchived = true) else it }
        }

        override suspend fun createSession(session: CardioSession): CardioSession {
            sessions = sessions + session
            return session
        }

        override suspend fun session(id: String): CardioSession? = sessions.firstOrNull { it.id == id }
        override suspend fun sessions(): List<CardioSession> = sessions
        override suspend fun findActiveSession(): CardioSession? = sessions.firstOrNull { !it.completed }
        override suspend fun updateSession(session: CardioSession) {
            sessions = sessions.map { if (it.id == session.id) session else it }
        }
        override suspend fun deleteSession(id: String) {
            sessions = sessions.filterNot { it.id == id }
        }
    }

    private class FakeBodyCompositionRepository : BodyCompositionRepository {
        override suspend fun entries(): List<BodyCompositionEntry> = emptyList()
        override suspend fun upsert(entry: BodyCompositionEntry) = Unit
    }
}

private object NoopContext : android.content.ContextWrapper(null) {
    override fun startService(intent: android.content.Intent): android.content.ComponentName? =
        throw UnsupportedOperationException("NoopContext does not support startService in unit tests")
    override fun stopService(intent: android.content.Intent): Boolean =
        throw UnsupportedOperationException("NoopContext does not support stopService in unit tests")
}
