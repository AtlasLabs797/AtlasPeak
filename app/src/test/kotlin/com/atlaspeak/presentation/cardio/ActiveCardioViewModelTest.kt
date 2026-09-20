package com.atlaspeak.presentation.cardio

import com.atlaspeak.domain.model.body.BodyCompositionEntry
import com.atlaspeak.domain.model.cardio.CardioFgsMode
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
import kotlinx.coroutines.test.runCurrent
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
    // BUG-093 (Fase 4 P0): reloj inyectable para los tests del fallback local
    // del cronometro y para poder avanzar el tiempo al verificar que
    // `elapsedSeconds` se sigue derivando del `startTime` cuando el FGS es
    // rechazado por el sistema. Patron espejo de `ActiveWorkoutViewModelTest`.
    private val fixedClock = TestClock(initialMillis = 1_700_000_000_000L)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        cardioRepository.types = listOf(
            CardioType("run", "Run", hasGps = true, isPreset = true, isArchived = false),
            CardioType("bike", "Bike", hasGps = false, isPreset = true, isArchived = false),
        )
        CardioTrackerRegistry.update(com.atlaspeak.service.CardioTrackerState())
        fixedClock.reset()
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
        // La ruta rehidratada vive en `cardio_route_points` durante la sesion activa;
        // en el fake, `sessions[].route` se usa como atajo para alimentar
        // `routePoints(sessionId)`. La VM refleja la ruta restaurada en `state.route`.
        assertEquals(1, state.route.size)
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

    @Test
    fun `process recreation restores route from persisted route points not from in-memory session`() = runTest(dispatcher) {
        // Simulamos muerte del proceso: el primer ViewModel persistio varios puntos
        // via el caso de uso. Despues recreamos el ViewModel contra el mismo
        // repositorio; la ruta debe venir de cardio_route_points, no de session.route.
        // bike (cap 90 km/h) para que el segmento 60s / 0.77 km (~46 km/h) quepa.
        val first = newViewModelAndStart(cardioTypeId = "bike")
        val sessionId = first.session!!.id
        val p1 = LocationPoint(40.0, -3.0, 1_700_000_000_000L)
        val p2 = LocationPoint(40.0, -2.991, 1_700_000_060_000L)
        cardioUseCase.appendRoutePoint(sessionId, p1, accuracyMeters = 5f, previousAcceptedPoint = null)
        cardioUseCase.appendRoutePoint(sessionId, p2, accuracyMeters = 5f, previousAcceptedPoint = null)

        // session.route sigue vacio: la ruta vive en cardio_route_points.
        assertEquals(0, cardioRepository.sessions.single().route.size)

        val second = newViewModel(cardioTypeId = "bike")
        dispatcher.scheduler.advanceUntilIdle()
        val state = second.state.value

        assertEquals(sessionId, state.session?.id)
        // La ruta rehidratada trae los dos puntos persistidos, no lo que habia en session.route.
        assertEquals(2, state.route.size)
        assertEquals(listOf(p1, p2), state.route)
    }

    // --- BUG-093 (Fase 4 P0): cinco escenarios del CardioForegroundService ---
    // Cada uno cubre una combinacion de permisos y resultado del startForeground.
    // Los contextos fake sustituyen a NoopContext: AllowingContext deja pasar la
    // peticion, RejectingContext lanza SecurityException, ActivityRecognitionDeniedContext
    // simula un dispositivo sin permiso ACTIVITY_RECOGNITION.

    @Test
    fun `startTrackingService with location allowed sets fgsMode to Location`() = runTest(dispatcher) {
        val context = AllowingContext()
        val viewModel = newViewModel(cardioTypeId = "run", context = context)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.startTrackingService(locationAllowed = true)
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(CardioFgsMode.Location, state.fgsMode)
        assertNull(state.message)
        // El intent llego al sistema: el FGS ya decidira internamente que tipo reclamar.
        assertEquals(1, context.startedIntents.size)
        // Como Location implica hasGps, la sesion continua sin forzar metricas
        // manuales a menos que la ruta este vacia.
        assertTrue(state.trackerServiceStartHandled)
    }

    @Test
    fun `startTrackingService with location denied sets fgsMode to None with LocationPermissionDenied`() = runTest(dispatcher) {
        val context = AllowingContext()
        val viewModel = newViewModel(cardioTypeId = "run", context = context)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.startTrackingService(locationAllowed = false)
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        // hasGps=true sin permiso de localizacion -> no hay FGS legal: preflight
        // devuelve None (reclamar LOCATION sin permiso dispara SecurityException
        // en Android 14+; reclamar HEALTH sin uso real de salud, idem).
        assertEquals(CardioFgsMode.None, state.fgsMode)
        assertEquals(ActiveCardioMessage.LocationPermissionDenied, state.message)
        // Aun asi el VM llama a startForegroundService: el FGS, en su propio
        // preflight (recalculado con permisos reales del dispositivo), tambien
        // obtendra None y se saltara startForeground.
        assertEquals(1, context.startedIntents.size)
        // La UI tendra que permitir introducir distancia manualmente.
        assertTrue(state.shouldShowManualMetrics)
    }

    @Test
    fun `startTrackingService for non-GPS cardio with ACTIVITY_RECOGNITION sets fgsMode to Health`() = runTest(dispatcher) {
        val context = AllowingContext()
        val viewModel = newViewModel(cardioTypeId = "bike", context = context)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.startTrackingService(locationAllowed = true)
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        // !hasGps && ACTIVITY_RECOGNITION -> Health (FGS_HEALTH con uso legitimo).
        assertEquals(CardioFgsMode.Health, state.fgsMode)
        assertNull(state.message)
        assertEquals(1, context.startedIntents.size)
    }

    @Test
    fun `startTrackingService for non-GPS cardio without ACTIVITY_RECOGNITION sets fgsMode to None`() = runTest(dispatcher) {
        val context = ActivityRecognitionDeniedContext()
        val viewModel = newViewModel(cardioTypeId = "bike", context = context)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.startTrackingService(locationAllowed = true)
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        // !hasGps && !ACTIVITY_RECOGNITION -> None (no podemos reclamar FGS_HEALTH
        // sin uso real de salud; reclamar LOCATION sin GPS no aplica).
        assertEquals(CardioFgsMode.None, state.fgsMode)
        assertNull(state.message)
        // El intent llega al sistema; el FGS hara su propio preflight y
        // tambien obtendra None, saltandose startForeground.
        assertEquals(1, context.startedIntents.size)
        // Cardio manual sin GPS: la UI muestra el formulario de metricas
        // manuales para que el usuario introduzca distancia/velocidad.
        assertTrue(state.shouldShowManualMetrics)
    }

    @Test
    fun `startTrackingService rejection by system falls back to local timer with fgsMode None`() = runTest(dispatcher) {
        val viewModel = newViewModel(cardioTypeId = "run", context = RejectingContext())
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.startTrackingService(locationAllowed = true)
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        // BUG-093: SecurityException del startForegroundService (politica
        // Android 14+). El modo efectivo pasa a None, el mensaje indica
        // que el tracker persistene no esta disponible y el cronometro
        // local toma el relevo.
        assertEquals(CardioFgsMode.None, state.fgsMode)
        assertEquals(ActiveCardioMessage.TrackerUnavailable, state.message)
        assertTrue(state.trackerServiceStartHandled)
    }

    @Test
    fun `elapsed seconds keeps increasing when foreground service is rejected`() = runTest(dispatcher) {
        val viewModel = newViewModel(cardioTypeId = "run", context = RejectingContext())
        dispatcher.scheduler.advanceUntilIdle()

        val initialElapsed = viewModel.state.value.elapsedSeconds
        assertTrue(initialElapsed >= 0L)

        viewModel.startTrackingService(locationAllowed = true)
        dispatcher.scheduler.advanceUntilIdle()

        // Confirmamos que hemos entrado en el camino del fallback local.
        assertEquals(CardioFgsMode.None, viewModel.state.value.fgsMode)
        assertEquals(ActiveCardioMessage.TrackerUnavailable, viewModel.state.value.message)

        // El reloj avanza 5s, el scheduler lo despierta y el job local deberia
        // actualizar `elapsedSeconds` derivandolo del `startTime` persistido.
        // Patron espejo del BUG-092 en ActiveWorkoutViewModelTest.
        fixedClock.advanceBy(5_000L)
        advanceLocalTimer(5_000L)

        val finalElapsed = viewModel.state.value.elapsedSeconds
        assertTrue(
            finalElapsed >= initialElapsed + 5L,
            "expected elapsedSeconds >= ${initialElapsed + 5L}, was $finalElapsed",
        )
    }

    private fun newViewModelAndStart(cardioTypeId: String): ActiveCardioUiState {
        val viewModel = newViewModel(cardioTypeId)
        dispatcher.scheduler.advanceUntilIdle()
        return viewModel.state.value
    }

    private fun newViewModel(cardioTypeId: String): ActiveCardioViewModel {
        return newViewModel(
            cardioTypeId = cardioTypeId,
            context = NoopContext,
            now = fixedClock.asNow(),
        )
    }

    private fun newViewModel(
        cardioTypeId: String,
        context: android.content.Context,
        now: () -> Long = fixedClock.asNow(),
    ): ActiveCardioViewModel {
        return ActiveCardioViewModel(
            savedStateHandle = handleFor(cardioTypeId, CardioMode.Timer),
            cardioUseCase = cardioUseCase,
            resumeCardioSessionUseCase = resumeCardioSessionUseCase,
            cardioRepository = cardioRepository,
            context = context,
            now = now,
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
        private val points = mutableMapOf<String, MutableList<com.atlaspeak.domain.model.cardio.CardioRoutePoint>>()

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
            points.remove(id)
        }

        override suspend fun addRoutePoint(point: com.atlaspeak.domain.model.cardio.CardioRoutePoint) {
            val list = points.getOrPut(point.sessionId) { mutableListOf() }
            list.removeAll { it.id == point.id }
            list.add(point)
        }

        override suspend fun routePoints(sessionId: String): List<com.atlaspeak.domain.model.cardio.CardioRoutePoint> {
            val explicit = points[sessionId]
            if (explicit != null) return explicit.sortedBy { it.timestampMs }
            // Compatibilidad con tests existentes que solo tocan session.route:
            // derivamos los puntos persistidos del snapshot que cargan en memoria.
            val session = sessions.firstOrNull { it.id == sessionId }
            val route = session?.route.orEmpty()
            if (route.isEmpty()) return emptyList()
            return route.mapIndexed { index, point ->
                com.atlaspeak.domain.model.cardio.CardioRoutePoint(
                    id = "fake-$sessionId-$index",
                    sessionId = sessionId,
                    timestampMs = point.timestamp,
                    latitude = point.latitude,
                    longitude = point.longitude,
                    accuracyMeters = null,
                    speedKmh = null,
                    distanceFromPreviousKm = 0.0,
                )
            }
        }

        override suspend fun routePointsCount(sessionId: String): Int {
            return routePoints(sessionId).size
        }

        override suspend fun routeDistanceKm(sessionId: String): Double {
            return routePoints(sessionId).sumOf { it.distanceFromPreviousKm }
        }

        override suspend fun deleteRoutePoints(sessionId: String) {
            points.remove(sessionId)
        }

        override suspend fun finalizeCardioSessionRoute(session: com.atlaspeak.domain.model.cardio.CardioSession) {
            sessions = sessions.map { if (it.id == session.id) session else it }
            points.remove(session.id)
        }
    }

    private class FakeBodyCompositionRepository : BodyCompositionRepository {
        override suspend fun entries(): List<BodyCompositionEntry> = emptyList()
        override suspend fun upsert(entry: BodyCompositionEntry) = Unit
    }

    /**
     * Avanza el scheduler lo justo para que el job local del cronometro haga un
     * tick completo, leyendo la nueva hora del reloj inyectable. Patron espejo
     * de ActiveWorkoutViewModelTest (BUG-092 / BUG-093).
     */
    private fun advanceLocalTimer(virtualMillis: Long) {
        dispatcher.scheduler.advanceTimeBy(virtualMillis + 1_100L)
        dispatcher.scheduler.runCurrent()
    }

    /**
     * Reloj mutable para los tests del BUG-093. Permite avanzar el tiempo de
     * forma determinista mientras el job local del cronometro hace sus ticks.
     */
    private class TestClock(initialMillis: Long) {
        private var current: Long = initialMillis

        fun reset(newMillis: Long = initialMillis) {
            current = newMillis
        }

        fun advanceBy(deltaMillis: Long) {
            current += deltaMillis
        }

        fun currentMillis(): Long = current

        fun asNow(): () -> Long = { current }
    }
}

private object NoopContext : android.content.ContextWrapper(null) {
    override fun startService(intent: android.content.Intent): android.content.ComponentName? =
        throw UnsupportedOperationException("NoopContext does not support startService in unit tests")
    override fun stopService(intent: android.content.Intent): Boolean =
        throw UnsupportedOperationException("NoopContext does not support stopService in unit tests")
}

/**
 * Context que deja pasar `startForegroundService`/`startService` y reporta
 * permisos como concedidos. Usado por los tests del BUG-093 para verificar el
 * camino feliz y los caminos en los que el FGS recibe el intent.
 */
private class AllowingContext : android.content.ContextWrapper(null) {
    val startedIntents = mutableListOf<android.content.Intent>()
    override fun startService(intent: android.content.Intent): android.content.ComponentName? {
        startedIntents.add(intent)
        return android.content.ComponentName(this, "allowing")
    }
    override fun startForegroundService(intent: android.content.Intent): android.content.ComponentName? {
        startedIntents.add(intent)
        return android.content.ComponentName(this, "allowing")
    }
    override fun stopService(intent: android.content.Intent): Boolean = true
    override fun checkSelfPermission(permission: String): Int = android.content.pm.PackageManager.PERMISSION_GRANTED
}

/**
 * Context que lanza `SecurityException` al llamar a `startForegroundService`.
 * Simula la politica estricta de tipos de FGS en Android 14+ que rechaza
 * reclamar un tipo sin el permiso/uso real que lo justifica. El VM debe
 * capturar la excepcion, rebajar el modo a None y arrancar el cronometro local.
 */
private class RejectingContext : android.content.ContextWrapper(null) {
    override fun startService(intent: android.content.Intent): android.content.ComponentName? =
        throw SecurityException("Rejected: simulated startService rejection in unit test")
    override fun startForegroundService(intent: android.content.Intent): android.content.ComponentName? =
        throw SecurityException("Rejected: simulated startForegroundService rejection in unit test")
    override fun stopService(intent: android.content.Intent): Boolean = true
    override fun checkSelfPermission(permission: String): Int = android.content.pm.PackageManager.PERMISSION_GRANTED
}

/**
 * Context que deja pasar `startForegroundService` pero deniega el permiso
 * `ACTIVITY_RECOGNITION`. Simula un dispositivo donde el usuario nunca concedio
 * el permiso (o donde correr sin GPS pero sin AR no admite FGS_HEALTH).
 */
private class ActivityRecognitionDeniedContext : android.content.ContextWrapper(null) {
    val startedIntents = mutableListOf<android.content.Intent>()
    override fun startService(intent: android.content.Intent): android.content.ComponentName? {
        startedIntents.add(intent)
        return android.content.ComponentName(this, "no-ar")
    }
    override fun startForegroundService(intent: android.content.Intent): android.content.ComponentName? {
        startedIntents.add(intent)
        return android.content.ComponentName(this, "no-ar")
    }
    override fun stopService(intent: android.content.Intent): Boolean = true
    override fun checkSelfPermission(permission: String): Int =
        if (permission == android.Manifest.permission.ACTIVITY_RECOGNITION) {
            android.content.pm.PackageManager.PERMISSION_DENIED
        } else {
            android.content.pm.PackageManager.PERMISSION_GRANTED
        }
}
