# BUGS.md - Registro de bugs

> Log de bugs detectados y su resolucion. Trazabilidad: cada bug arreglado deja rastro aqui.
> Orden: mas reciente arriba.

---

## Formato de entrada

```
### BUG-NNN - Titulo corto
- **Estado:** Abierto / En progreso / Resuelto / No reproducible
- **Fecha deteccion:** AAAA-MM-DD
- **Fase:** N
- **Severidad:** Critica / Alta / Media / Baja
- **Sintoma:** que se observo (pasos para reproducir si aplica).
- **Causa raiz:** por que pasaba realmente.
- **Solucion:** que se cambio (archivos / commits).
- **Prevencion:** test anadido / regla para que no vuelva.
- **Fecha resolucion:** AAAA-MM-DD
```

---

## Entradas

### BUG-094 - Cardio sin pause/resume y boton Finalizar siempre habilitado
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-09-20
- **Fase:** V-01.10 (P1 - Alta del plan de mejora, Fase 5)
- **Severidad:** Media
- **Sintoma:** (a) `state.canComplete` ya estaba calculado en el VM pero el boton Finalizar de
  `ActiveCardioScreen` solo respetaba `!completionInProgress`, ignorando la validez de las
  metricas manuales. Resultado: el usuario podia pulsar Finalizar sin haber rellenado distancia
  manual, quedandose en un estado bloqueado con `ManualMetricsRequired`. (b) No existia
  funcionalidad de Pausar/Reanudar para sesiones de cardio: si el usuario queria parar
  momentaneamente (por un semaforo, una parada tecnica, etc.) el cronometro seguia contando,
  inflando el tiempo efectivo de entrenamiento.
- **Causa raiz:** (a) El composable de pantalla leia `!state.completionInProgress` y nunca
  pasaba `state.canComplete` como `enabled`. (b) El modelo de dominio `CardioSession` no
  tenia campos para representar pausa y el VM solo ofrecia start/stop/cancel. Faltaba una
  fuente canonica de tiempo efectivo que restase el tiempo pausado sin falsear `startTime`.
- **Solucion:**
    - CardioSessionEntity gana `paused_at_ms INTEGER` (nullable) y `total_paused_duration_ms
      INTEGER NOT NULL DEFAULT 0`. Migracion Room `MIGRATION_7_8` con `ALTER TABLE` no
      destructiva.
    - CardioSession (dominio) replica los dos campos con semantica identica: `pausedAtMillis`
      mientras este pausada, `totalPausedDurationMillis` acumulado tras cada reanudacion.
    - Helper `effectiveElapsedSeconds(session, now)` en `domain/model/cardio` que resta
      `totalPausedDurationMillis` y, si esta pausada, `(now - pausedAtMillis)`. Es la unica
      fuente de verdad del tiempo efectivo; la reutilizan el VM (local fallback y loadSession)
      y el caso de uso (`completeSession`).
    - ActiveCardioViewModel: `pauseCardio()` / `resumeCardio()` idempotentes, `state.canComplete`
      considera `!completionInProgress && completedSessionId == null && (!requiresManualMetrics ||
      hasValidManualMetrics)`, nuevo `state.isPaused` derivado.
    - ActiveCardioScreen: boton Finalizar con `enabled = state.canComplete`, botones Pausar /
      Reanudar conmutados por `state.isPaused`. Strings ES + EN nuevos.
    - CardioForegroundService: nuevas acciones `ACTION_PAUSE` y `ACTION_RESUME` que cancelan o
      reactivan los jobs (`timerJob`, `locationJob`, `persistJob`) sin tocar el registro ni
      reclamar foreground nuevo (la notificacion existente sigue visible durante la pausa).
- **Prevencion:** `ActiveCardioViewModelTest` cubre 6 casos (pausa simple, multiples pausas,
  process recreation pausada, countdown durante pausa, gating del Finalizar segun
  `canComplete`, idempotencia de `completeCardio`). El helper `effectiveElapsedSeconds` vive
  en `domain/model/cardio` y cualquier futuro consumidor (ej. historial, statistics) debe
  usarlo en lugar de reinventar la formula.
- **Limitacion conocida:** `completeCardio` mientras la sesion esta pausada deja el tiempo
  pausado excluido del tiempo total registrado, lo cual es coherente. No se permite reanudar
  automaticamente al finalizar.
- **Limitacion conocida:** el resume del FGS usa la heuristica "si la ruta antes de pausar no
  estaba vacia, asume GPS y reanuda `locationJob`". Si el escenario es GPS-permitido-pero-
  sin-puntos-todavia (raro: el usuario pauso antes del primer fix), no se reanuda la captura
  GPS hasta que el VM fuerce un reinicio manual. Documentado.
- **Fecha resolucion:** 2026-09-20

---

### BUG-093 - CardioForegroundService no cubre todos los caminos de tipos de FGS en Android 14+/15+
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-09-20
- **Fase:** V-01.10 (P0 - Bloqueante del plan de mejora, Fase 4)
- **Severidad:** Alta
- **Sintoma:** `CardioForegroundService` distinguia entre `FOREGROUND_SERVICE_TYPE_LOCATION` y
  `FOREGROUND_SERVICE_TYPE_HEALTH` con `if (locationTracking)` en `startForegroundCompat`, pero
  la logica no cubria tres escenarios problemáticos en Android 14+ (API 34+) y 15+ (API 35):
    1. Sesion de cardio GPS sin permiso `ACCESS_FINE_LOCATION` concedido: el VM enviaba
       `gpsEnabled = false` pero el FGS seguia intentando reclamar `FOREGROUND_SERVICE_TYPE_HEALTH`
       sin uso real de datos de salud (no habia `ACTIVITY_RECOGNITION` ni escritura a Health
       Connect desde el FGS), lo que en Android 14+ dispara `SecurityException`. Resultado: el
       servicio se mata y la sesion se queda sin notificacion persistente.
    2. Sesion de cardio sin GPS en un dispositivo sin `ACTIVITY_RECOGNITION`: la politica estricta
       de tipos FGS exige un uso real de datos de salud para reclamar `HEALTH`. Reclamarlo sin
       ese uso hace que el sistema rechace el `startForeground` con `SecurityException`.
    3. Cualquier combinacion anterior + un dispositivo que ya ni siquiera acepta
       `startForegroundService` por la politica de background activities: el VM capturaba
       `RuntimeException` pero el catch englobaba `SecurityException` y otras subclases sin
       distinguir el caso `SecurityException` explicito del tipo FGS.
  En todos los casos, el resultado era el mismo: `CardioTrackerRegistry` quedaba en
  `failed=true`, el usuario veia la nota `cardio_tracker_unavailable` y el cronometro quedaba
  congelado (antes del BUG-091 / BUG-092 ya tenia fallback local, pero el VM no distinguia el
  modo "ningun FGS legal" del modo "FGS fallo por politica").
- **Causa raiz:** la decision del tipo se tomaba inline en `startForegroundCompat` con un
  booleano `locationTracking`, sin una funcion pura que modele "que tipos de FGS son legales
  AHORA, dados los permisos reales del dispositivo". El VM no tenia forma de etiquetar el
  escenario (Location/Health/None) para que la UI pudiera mostrar la nota apropiada ("modo
  local: cronometro sigue, pero el sistema puede parar el proceso en background") cuando
  no hay un FGS legal. El catch era `catch (_: RuntimeException)` sin distinguir
  `SecurityException`, que es la excepcion que Android 14+ lanza cuando incumple la politica
  de tipos.
- **Solucion:** preflight puro `CardioForegroundService.preflightFgsType(hasGps,
  hasFineLocation, hasActivityRecognition)` que devuelve `CardioFgsMode` (`Location`, `Health`
  o `None`). La UI expone este modo en `ActiveCardioUiState.fgsMode` para que la pantalla
  pueda etiquetar el escenario. Cambios:
    - `domain/model/cardio/CardioModels.kt`: nuevo enum `CardioFgsMode { Location, Health,
      None }`.
    - `CardioForegroundService.startTracking` calcula `fgsMode = preflightFgsType(...)`. Si
      es `None`, salta `startForeground` y deja correr `timerJob` + `persistJob` sin
      notificacion persistente. Si es `Location` o `Health`, reclama el tipo correspondiente.
      El catch de `startForeground` ahora tiene un `catch (_: SecurityException)` explicito
      seguido de un `catch (_: RuntimeException)` (mismo cuerpo) para claridad del lector:
      SecurityException es subclase de RuntimeException, pero queremos que la intencion sea
      obvia. Nuevo `hasActivityRecognitionPermission()` (Android 10+ runtime grant via
      `ContextCompat.checkSelfPermission`).
    - `ActiveCardioViewModel.startTrackingService` calcula `requestedFgsMode` con
      `preflightFgsType` antes de llamar a `startForegroundService`. Si la llamada lanza
      `SecurityException` (explicit catch) o `RuntimeException` (fallback), baja el modo a
      `None` y arranca el cronometro local. El collector del registry, cuando detecta
      `tracker.failed && sessionId == ours`, baja `fgsMode` a `None` para reflejar el modo
      efectivo. Ademas, ahora recibe un reloj inyectable `now: () -> Long` (mismo patron que
      `ActiveWorkoutViewModel` post-BUG-092) para que los tests del fallback local sean
      deterministas.
    - `ActiveCardioUiState.fgsMode: CardioFgsMode` (default `None`).
  La pantalla (`ActiveCardioScreen`) NO referencia `fgsMode` en esta fase: el plan lo
  reservaba como dato disponible para iteraciones posteriores (la nota visible
  "modo local, sin notificacion persistente" entraria en una fase UX posterior, no aqui).
- **Prevencion:**
    - Tests en `ActiveCardioViewModelTest` (JUnit5, hand-written fakes, `StandardTestDispatcher`):
      - `startTrackingService with location allowed sets fgsMode to Location`: contexto
        AllowingContext, cardio GPS, `locationAllowed = true` -> `fgsMode = Location`,
        intent enviado al sistema.
      - `startTrackingService with location denied sets fgsMode to None with
        LocationPermissionDenied`: cardio GPS, `locationAllowed = false` -> `fgsMode =
        None`, mensaje `LocationPermissionDenied`, formulario manual visible. El intent
        llega al sistema; el FGS re-evalua el preflight y tambien devuelve None.
      - `startTrackingService for non-GPS cardio with ACTIVITY_RECOGNITION sets fgsMode to
        Health`: cardio manual + AllowingContext -> `fgsMode = Health`.
      - `startTrackingService for non-GPS cardio without ACTIVITY_RECOGNITION sets fgsMode
        to None`: cardio manual + ActivityRecognitionDeniedContext -> `fgsMode = None`,
        formulario manual visible.
      - `startTrackingService rejection by system falls back to local timer with fgsMode
        None`: RejectingContext (lanza SecurityException al `startForegroundService`) ->
        `fgsMode = None`, mensaje `TrackerUnavailable`.
      - `elapsed seconds keeps increasing when foreground service is rejected`: misma
        configuracion que el anterior + reloj inyectable +5s -> `elapsedSeconds` sube al
        menos 5. Patron espejo del BUG-092.
    - Fake contexts `AllowingContext`, `RejectingContext`, `ActivityRecognitionDeniedContext`
      (mismo patron que `NoopContext` existente), siguiendo el estilo de
      `ActiveWorkoutViewModelTest`.
    - `TestClock(initialMillis)` con `reset / advanceBy / currentMillis / asNow()` para
      inyectar reloj determinista.
- **Limitacion conocida (documentada en CHANGELOG):** el VM computa `fgsMode` con su propia
  copia del preflight y lo publica antes de llamar a `startForegroundService`. El FGS vuelve a
  calcular el preflight en `startTracking` y puede divergir en una ventana de carrera (p. ej.,
  el usuario revoca el permiso entre el check del VM y el check del FGS). En ese caso el modo
  que refleja el VM refleja su intencion; el modo efectivo del FGS seria None si la
  disponibilidad de permiso cambia. Ambos caminos convergen a `tracker.running=true` /
  `failed=false` o a `failed=true` y el collector del VM baja a `None` automaticamente. No se
  expone el fgsMode del FGS en el registry: aceptado como deuda para una fase posterior si se
  necesita precision bit-exact entre el modo pedido y el modo realmente reclamado.
- **Limitacion conocida (documentada en CHANGELOG):** la nota visible en la UI para
  `fgsMode == None` ("modo local, sin notificacion persistente") NO se añade en esta fase; el
  plan la reservaba para una iteracion UX posterior. El campo ya esta disponible en el estado
  para cuando se anada.
- **Fecha resolucion:** 2026-09-20

### BUG-092 - Cronometro visual de fuerza se congela si WorkoutForegroundService falla o no llega a arrancar
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-09-20
- **Fase:** V-01.10 (P0 - Bloqueante del plan de mejora, Fase 3)
- **Severidad:** Alta
- **Sintoma:** durante una sesion activa de fuerza, si el `WorkoutForegroundService`
  no podia arrancar (SecurityException al llamar a `ContextCompat.startForegroundService`,
  `startForeground` lanzaba SecurityException por falta de `FOREGROUND_SERVICE_HEALTH`
  o por la politica runtime de Android 14+, el servicio era matado por el OS, o el
  permiso `ACTIVITY_RECOGNITION` era denegado por el usuario), `WorkoutTimerRegistry`
  quedaba en `failed=true` o vacio. La UI mostraba el mensaje `TimerServiceUnavailable`
  (introducido en BUG-042) pero el contador `elapsedSeconds` se quedaba congelado en el
  ultimo valor emitido por el FGS: el usuario veia el tiempo parado aunque la sesion
  siguiera avanzando en Room.
- **Causa raiz:** el `WorkoutTimerRegistry` era la unica fuente de verdad del tiempo
  visible. El VM tenia un collector que espejaba `timer.elapsedSeconds` al estado, pero
  ese valor solo se actualizaba cuando el FGS llamaba a `WorkoutTimerRegistry.tick(...)`
  una vez por segundo. Si el FGS nunca arrancaba o moria, nadie empujaba nuevos valores
  al estado. La formula matematica (`elapsed = (System.currentTimeMillis() -
  session.startTime) / 1000`) solo se aplicaba una vez en `loadSession` (hidratacion)
  y otra vez en cada tick del FGS, no en cada composicion. El diseno confundia "el FGS
  existe" con "el tiempo existe".
- **Solucion:** `ActiveWorkoutViewModel` ahora mantiene un reloj local de respaldo
  mientras el registry no este escribiendo tiempo para la sesion actual. Concretamente:
  - Constructor primario recibe un `now: () -> Long` inyectable. El `@Inject`
    secundario (Hilt) pasa `{ System.currentTimeMillis() }`; los tests inyectan un
    reloj controlado (`TestClock`).
  - `loadSession` deriva `elapsedSeconds` desde `now() - session.startTime` (antes
    usaba `System.currentTimeMillis()` directo, no testeable).
  - Nuevo `localTimerJob: Job?` y `startLocalTimerIfNeeded()` / `stopLocalTimer()` que
    ejecutan `while (isActive) { state.elapsedSeconds = (now() - session.startTime) / 1000; delay(1000) }`
    en `viewModelScope`. La formula es identica a la del FGS, asi que ambos caminos
    producen el mismo valor modulo el momento del tick.
  - El collector del registry decide cuando arrancar/parar el job local, a traves
    de `reconcileTimerState()`:
    - `timer.failed && timer.sessionId == sessionId`: arranca fallback, fija
      `message = TimerServiceUnavailable`, limpia `restTimer` (el FGS es dueno del
      rest timer; al morir, el VM no puede mantenerlo).
    - `timer.sessionId == sessionId && timer.running`: FGS sano -> para fallback,
      espeja `timer.elapsedSeconds` y `restTimer` del registry.
    - `timer.sessionId == null`: FGS no ha arrancado o ya cerro -> arranca fallback
      mientras la VM tenga sesion cargada, limpia `restTimer`.
    - Cualquier otro caso (registry de otra sesion): no toca nada, evita pisar el
      estado.
  - `reconcileTimerState()` se invoca tambien desde `loadSession()` tras cargar la
    sesion en el estado. Esto cubre una carrera posible en produccion con el
    dispatcher Main: el `WorkoutTimerRegistry.state.collect` puede emitir su valor
    inicial antes de que `loadSession` haya terminado (las queries de Room hacen
    suspension), en cuyo caso `startLocalTimerIfNeeded` arranca el job local pero
    sale inmediatamente por `session == null`, y al no haber un re-trigger el
    cronometro queda muerto hasta el siguiente cambio del FGS. Sin este segundo
    punto de llamada el fallback fallaria en escenarios donde el FGS nunca
    arranca (permiso denegado, kill del OS, `startForegroundService` que lanza
    SecurityException sin tocar el registry).
  - `onCleared()` llama a `stopLocalTimer()` ademas del `viewModelScope.cancel()`
  implicito al destruirse el VM, para que el job no quede zombi en escenarios
  raros de doble `onCleared`.
- **Prevencion:**
  - `ActiveWorkoutViewModelTest` añade 5 casos (`elapsed_seconds keeps increasing
    when foreground service fails to start`, `elapsed_seconds keeps increasing when
    foreground service never started`, `elapsed_seconds keeps increasing across
    recreation when foreground service is unavailable`, `local fallback timer stops
    when foreground service becomes healthy`, `elapsed_seconds matches math from
    startTime`) que ejercitan el camino sin FGS y la transicion FGS-failed ->
    FGS-healthy. Cubren tambien que el fallback local se para cuando el registry
    pasa a `running=true`, evitando doble escritor.
  - El patron se inspira en `ActiveCardioViewModel.startLocalTimerIfNeeded()` que ya
    hacia esto desde BUG-090; se ha replicado para fuerza con la diferencia de que
    el fallback arranca tambien cuando el registry esta vacio (no solo cuando
    `failed=true`), porque `startForegroundService` puede lanzar SecurityException
    sin tocar el registry y eso era otra ventana de tiempo congelado.
- **Limitacion conocida (documentada en CHANGELOG):** el job local y el FGS usan
  `System.currentTimeMillis()` directamente (la formula `(now - startTime) / 1000`
  es la misma en ambos). Si el reloj del sistema cambia hacia atras durante la
  sesion (cambio manual de hora, NTP agresivo), `elapsedSeconds` puede dar saltos.
  Es la misma limitacion que ya tenia el FGS; aceptada como deuda.
- **Fecha resolucion:** 2026-09-20

### BUG-091 - Ruta GPS de cardio se pierde si Android mata el proceso durante la sesion
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-09-20
- **Fase:** V-01.10 (P0 - Bloqueante del plan de mejora, Fase 2)
- **Severidad:** Alta
- **Sintoma:** durante una sesion de cardio con GPS, si Android mataba el proceso por
  presion de memoria (LMK) o el usuario cerraba la app desde recientes, al volver a
  abrir `ActiveCardioScreen` la sesion activa se reanuda con la `startTime`
  correcta (BUG-090) pero la ruta GPS estaba en cero: el recorrido se perdia aunque
  la sesion siguiera marcada como activa en Room. Ademas, en cada fix entrante
  `CardioTrackerRegistry.addPoint` hacia `route = route + point` y `route.distanceKm()`,
  lo que es O(N) por punto y hacia crecer el coste con la duracion del entrenamiento.
- **Causa raiz:** `CardioTrackerRegistry` mantenia la ruta exclusivamente en memoria
  (un `MutableStateFlow<CardioTrackerState>`). El snapshot JSON en
  `cardio_sessions.route_polyline_json` solo se escribia al ejecutar `completeSession`,
  asi que cualquier salida del proceso antes de finalizar descartaba la ruta. El
  recomputo O(N) por fix era residuo del diseno inicial donde la ruta se reconstruia
  desde el primer punto cada vez; a pequenas escalas (carrera de 1h con ~720 puntos) el
  coste es despreciable pero el diseno lo arrastraba.
- **Solucion:** nueva tabla `cardio_route_points` indexada por `(session_id,
  timestamp_ms)` con FK `CASCADE` a `workout_sessions(id)`. Cada punto aceptado por
  `LocationTracker.isUsableForCardioTracking` se persiste via
  `CardioUseCase.appendRoutePoint`, que:
  - valida coordenadas (`lat` en [-90, 90], `lon` en [-180, 180]; `NaN` rechazado por
    la semantica del operador `in` en rangos),
  - rechaza `accuracyMeters <= 0` (defensa en profundidad: `LocationTracker` ya
    descarta accuracy > 30 m, y valores nulos se aceptan para no romper al FGS que
    todavia no expone esa senal),
  - calcula `distance_from_previous_km` incremental (haverine entre el ultimo punto
    aceptado y el nuevo),
  - aplica el cap de velocidad por tipo (45 km/h correr, 90 km/h ciclismo, 15 km/h
    natacion, 35 km/h remo/eliptica, 60 km/h resto),
  - rechaza puntos imposibles sin escribirlos.
  El `CardioForegroundService` (ahora `@AndroidEntryPoint`) mantiene un `persistJob`
  que observa `CardioTrackerRegistry.state.map { it.route }.distinctUntilChanged()` y
  persiste solo los puntos nuevos (delta desde el ultimo seen), evitando duplicar la
  ruta preexistente al arrancar. Al volver a abrir la sesion tras una muerte de
  proceso, `ActiveCardioViewModel.loadSession` llama a
  `cardioUseCase.restoreRoute(sessionId)` que devuelve `List<LocationPoint>` +
  distancia total, y los vuelca al registro antes de que el FGS levante su
  `persistJob`. `completeSession` ya no recibe la ruta como parametro: la reconstruye
  desde Room, aplica `sanitizedRoute` (mismo cap de velocidad) sobre el perimetro
  persistido y delega en `CardioRepository.finalizeCardioSessionRoute`, que escribe
  el snapshot final en `cardio_sessions.route_polyline_json` y borra los puntos en
  vuelo en una unica transaccion de base de datos (asi no quedan residuos si el
  proceso muere a mitad del cierre).
- **Revision 2026-09-20:** se anade el filtro de `accuracyMeters <= 0` en
  `appendRoutePoint` y se mueve el borrado de route points a la misma transaccion
  que el snapshot final (`finalizeCardioSessionRoute`). El plan marcaba ambos como
  requisito; el primer pase los habia dejado fuera.
- **Prevencion:**
  - Migracion Room explicita `MIGRATION_6_7` con test instrumentado
    `migration6To7CreatesCardioRoutePointsTable` que valida la tabla, los dos indices
    (`session_id`, `(session_id, timestamp_ms)`) y permite una insercion de prueba.
  - `CardioRoutePointsInstrumentedTest` (androidTest, archivo real) cubre los
    escenarios del plan: N puntos persistidos, cerrar y reabrir mantiene la distancia
    y el conteo, `deleteSession` borra los route points via `CASCADE`,
    `deleteRoutePoints` no toca la sesion padre, FK rechaza huérfanos.
  - `CardioUseCaseTest`: nuevos casos `appendRoutePoint` (5 puntos + restore, ahora
    con tipo `bike` para que el segmento 60s/0.77 km no supere el cap de velocidad),
    `rechaza coordenadas invalidas`, `rechaza velocidades imposibles`,
    `rechaza accuracy <= 0 y acepta accuracy nula` (cubren la regla de defensa en
    profundidad anadida en la revision), `continua desde el ultimo punto tras
    recreacion` (tipo `bike` por el mismo motivo), `completeSession borra los route
    points`.
  - `ActiveCardioViewModelTest`: nuevo caso `process recreation restores route from
    persisted route points not from in-memory session` que verifica que la ruta
    rehidratada viene de `cardio_route_points` (no de `CardioSession.route`, que
    permanece vacia mientras la sesion esta activa).
- **Limitacion conocida (documentada en CHANGELOG):** existe una ventana minima
  (sub-milisegundo por fix) entre `addPoint` y la escritura a Room del `persistJob`:
  si el proceso muere justo ahi, el ultimo fix se pierde. Room serializa lecturas y
  escrituras, asi que `completeSession` siempre ve un estado consistente. Se acepta
  como deuda: seguir este camino requeria acoplar el registro a Room (rompiendo el
  contrato de `CardioTrackerRegistry` como cache en memoria).
- **Limitacion conocida (documentada en CHANGELOG):** `CardioForegroundService` ahora
  requiere `@AndroidEntryPoint` e inyeccion de Hilt para acceder a `CardioUseCase`.
  Los tests del FGS no se han ampliado en esta fase (la cobertura del `persistJob`
  se ejerce indirectamente via `appendRoutePoint`).
- **Fecha resolucion:** 2026-09-20

### BUG-090 - Sesiones activas duplicadas tras muerte de proceso y sin dialogo de conflicto
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-09-20
- **Fase:** V-01.10 (P0 - Bloqueante del plan de mejora)
- **Severidad:** Alta
- **Sintoma:** al iniciar un entrenamiento de fuerza o cardio con una sesion ya activa en Room
  (caso tipico: Android mata el proceso por presion de memoria y el usuario vuelve a abrir la
  pantalla de entrenamiento), `ActiveWorkoutViewModel.startWorkout()` y
  `ActiveCardioViewModel.startCardio()` creaban SIEMPRE una sesion nueva con un UUID nuevo. La
  sesion original quedaba huérfana, los sets/ruta ya registrados se perdian y el historial se
  contaminaba con duplicados. Ademas, abrir una rutina/tipo de cardio distinto al de la sesion
  activa pisaba en silencio la sesion en curso sin pedir confirmacion al usuario.
- **Causa raiz:** los casos de uso `StartWorkoutSessionUseCase.invoke` y `CardioUseCase.startSession`
  ignoraban por completo la sesion activa persistida. No existian operaciones de repositorio para
  localizarla. La identidad de la sesion dependia del ciclo de vida del ViewModel (que se destruye
  con el proceso), en lugar de vivir en Room.
- **Solucion:** se anade `findActiveSession()` a `WorkoutRepository` y `CardioRepository` con una
  consulta Room que devuelve la sesion mas reciente con `completed = false`. Los casos de uso
  colapsan los caminos "arrancar" y "reanudar" en un unico resultado sellado
  `ActiveSessionStartResult { Started | Resumed | Conflict | NotFound }`. Si la sesion activa
  pertenece a la misma rutina/tipo de cardio se reanuda; si pertenece a otra se devuelve
  `Conflict` y la UI muestra un dialogo con tres acciones (Continuar / Descartar y empezar uno
  nuevo / Cancelar) sin sustituir automaticamente. El `elapsedSeconds` se recalcula desde
  `startTime` al cargar la sesion (no desde un contador en memoria). Se crean
  `ResumeWorkoutSessionUseCase` y `ResumeCardioSessionUseCase` idempotentes para rehidratar la
  sesion sin crear nada nuevo.
- **Prevencion:** `ActiveWorkoutViewModelTest` y `ActiveCardioViewModelTest` cubren el caso de
  "process recreation" (dos ViewModels sobre el mismo repositorio) y verifican que solo exista
  una sesion, que los sets completados se preservan y que `elapsedSeconds` se deriva de
  `startTime`. Tambien cubren `Conflict` con sesion activa de otra rutina (no se sustituye),
  `discardActiveSessionAndStartNew` (reemplaza) y `resumeActiveSession` (mantiene). Los tests de
  `StartWorkoutSessionUseCaseTest`, `ResumeWorkoutSessionUseCaseTest`,
  `CardioUseCaseTest` (casos `start session` y `startSession`) y `ResumeCardioSessionUseCaseTest`
  cubren los cuatro caminos del resultado sellado.
- **Limitacion conocida (documentada en CHANGELOG):** el descanso (rest timer) que vive en
  `WorkoutTimerRegistry` sigue siendo en memoria y se pierde tras muerte del proceso. Solo se
  restaura si el `WorkoutForegroundService` esta vivo. Para Fase 1 esto es aceptable porque el
  descanso es efimero; persistirlo requeriria extender el modelo.
- **Limitacion conocida (documentada en CHANGELOG):** en cardio, si el usuario abre con un
  `mode` (Timer/Countdown) distinto al de la sesion activa persistida, el caso de uso
  reanuda por `cardioTypeId` (no chequea `mode`); el contador `remainingSeconds` de la UI
  refleja el `mode` del SavedStateHandle, no el de la sesion cargada. Mitigacion: el
  usuario puede descartar y empezar una nueva desde el dialogo de conflicto.
- **Limitacion conocida (documentada en CHANGELOG):** el conflicto cross-domain (sesion de
  fuerza activa + abrir cardio, o viceversa) no se detecta; cada caso de uso solo mira
  sesiones de su mismo tipo. Fuera del alcance del plan.
- **Fecha resolucion:** 2026-09-20

---

### BUG-089 - Descanso avisaba una sola vez y se apagaba solo
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-07-03
- **Fase:** V-01.08
- **Severidad:** Media
- **Sintoma:** al terminar el cronometro de descanso sonaba/vibraba una vez durante unos
  milisegundos y el panel desaparecia solo, incluso si el usuario no habia parado el aviso.
- **Causa raiz:** el feedback del descanso vivia como efecto puntual de Compose (`ToneGenerator`
  y vibracion one-shot) y el ViewModel borraba el rest timer al llegar a cero.
- **Solucion:** el `WorkoutForegroundService` controla el descanso, mantiene estado expirado,
  vibra/suena en bucle hasta `Parar`, y expone accion de notificacion para saltar/parar.
- **Prevencion:** `WorkoutTimerRegistryTest` cubre expiracion a `alerting`, persistencia hasta
  limpieza explicita y reemplazo de un descanso por otro.
- **Fecha resolucion:** 2026-07-03

---

### BUG-088 - Entrenamiento activo no mostraba volumen vivo y recortaba controles
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-07-03
- **Fase:** V-01.08
- **Severidad:** Media
- **Sintoma:** en la pantalla de entrenamiento activo el volumen permanecia en `0 kg`,
  la cabecera dejaba demasiado aire superior, el descanso ocupaba una card opaca y las
  acciones inferiores partian el texto en moviles estrechos.
- **Causa raiz:** la UI leia `workout_sessions.total_volume_kg`, que solo se calcula al cerrar
  la sesion; ademas el layout vertical dedicaba una fila entera a cerrar, reservaba demasiada
  altura para el panel de descanso y dividia dos acciones textuales en media pantalla.
- **Solucion:** se anade calculo compartido de volumen completado, `ActiveWorkoutUiState` expone
  volumen vivo y progreso por ejercicio completo, el header integra la salida, el descanso pasa
  a overlay transparente con padding en la lista y la accion de ejercicios pasa a icon-only.
- **Prevencion:** tests de volumen completado y estado UI cubren volumen vivo, progreso por
  ejercicio completo y sets incompletos ignorados.
- **Fecha resolucion:** 2026-07-03

---

### BUG-087 - Ajustes guardaban sin feedback visual claro
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-07-03
- **Fase:** V-01.08
- **Severidad:** Baja
- **Sintoma:** cambiar el tema persistia inmediatamente sin confirmacion visual, y el guardado de
  notificaciones mostraba un texto discreto que podia pasar desapercibido.
- **Causa raiz:** el ViewModel de tema no exponia eventos de guardado y Ajustes no tenia una
  superficie visual comun para estados de exito/error.
- **Solucion:** `AppThemeViewModel` emite eventos tras guardar, `NotificationSettingsViewModel`
  modela el tono del feedback y `NotificationSettingsScreen` renderiza una tarjeta de estado
  con icono para tema y notificaciones.
- **Prevencion:** tests de ViewModel cubren evento de tema guardado, fallo al guardar tema y
  feedback de exito/error en ajustes de notificaciones.
- **Fecha resolucion:** 2026-07-03

---

### BUG-086 - ThemeMode existia pero no habia switching real
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-07-01
- **Fase:** Fase G theme switching
- **Severidad:** Baja
- **Sintoma:** `ThemeMode` estaba definido y habia strings de tema, pero la app siempre seguia
  el modo por defecto y no exponia control al usuario.
- **Causa raiz:** `app_settings.theme` no tenia repositorio/Flow ni UI conectada al theme root.
- **Solucion:** repositorio Room persistido, `AppThemeViewModel`, wiring en `MainActivity` y selector
  en Ajustes.
- **Prevencion:** settings persistidos deben tener repositorio, UI y aplicacion efectiva antes de
  considerar cerrada la feature.
- **Fecha resolucion:** 2026-07-01

---

### BUG-085 - Scheduler podia crashear con horarios corruptos
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-07-01
- **Fase:** Fase F notificaciones
- **Severidad:** Media
- **Sintoma:** si datos legados/corruptos contenian `notification_time` invalido, el scheduler
  podia llegar a `LocalTime.parse` y fallar durante reschedule.
- **Causa raiz:** el scheduler confiaba en que todos los datos ya habian pasado por validacion
  de dominio/UI.
- **Solucion:** se filtran horas nulas/invalidas antes de encolar recordatorios y resumen diario.
- **Prevencion:** WorkManager debe validar entradas persistidas antes de parsear/enqueue.
- **Fecha resolucion:** 2026-07-01

---

### BUG-084 - Lista Drive aceptaba nombres de backup demasiado laxos
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-07-01
- **Fase:** Fase E backup/security
- **Severidad:** Baja
- **Sintoma:** la query Drive usaba `contains` para prefijo y extension, pudiendo listar archivos
  no generados por Atlas Peak dentro de App Data.
- **Causa raiz:** Drive query no soporta regex y no habia filtro local estricto.
- **Solucion:** se filtra localmente el patron exacto `atlas_peak_backup_yyyyMMdd_HHmmss.enc` y
  la query excluye papelera.
- **Prevencion:** `RetrofitDriveBackupServiceTest` cubre nombres validos e invalidos.
- **Fecha resolucion:** 2026-07-01

---

### BUG-083 - Worker de backup no exigia bateria no baja ni backoff explicito
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-07-01
- **Fase:** Fase E backup/security
- **Severidad:** Media
- **Sintoma:** el backup periodico podia ejecutarse con bateria baja y dependia de defaults para backoff.
- **Causa raiz:** `BackupWorkScheduler` solo declaraba red conectada.
- **Solucion:** constraints con `setRequiresBatteryNotLow(true)` y backoff exponencial de 30 min.
- **Prevencion:** cualquier worker de IO pesado debe declarar constraints y backoff explicitos.
- **Fecha resolucion:** 2026-07-01

---

### BUG-082 - Source desconocido de composicion corporal se convertia en Manual
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-07-01
- **Fase:** Fase D Health Connect
- **Severidad:** Media
- **Sintoma:** un valor `source` nuevo o corrupto en `body_composition` aparecia como manual,
  ocultando el origen real del dato.
- **Causa raiz:** el mapper Room usaba `else -> BodyCompositionSource.Manual`.
- **Solucion:** el mapper reconoce `MANUAL`, `HEALTH_CONNECT`, `SCALE_APP` y falla de forma
  explicita para cualquier otro valor.
- **Prevencion:** nuevas fuentes deben agregarse al enum/mapeo y cubrirse con test o migracion.
- **Fecha resolucion:** 2026-07-01

---

### BUG-081 - HC y entrada manual duplicaban metricas con el mismo timestamp
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-07-01
- **Fase:** Fase D Health Connect
- **Severidad:** Alta
- **Sintoma:** si Health Connect importaba una medicion con el mismo timestamp que una entrada
  manual, ambas podian aparecer en chart/latest y producir valores inconsistentes.
- **Causa raiz:** `BodyCompositionUseCase.snapshot()` ordenaba solo por timestamp y no aplicaba
  politica de resolucion de conflicto por fuente.
- **Solucion:** los snapshots priorizan Health Connect por metrica/timestamp y deduplican la serie.
- **Prevencion:** `BodyCompositionUseCaseTest` cubre que Health Connect gana frente a Manual.
- **Fecha resolucion:** 2026-07-01

---

### BUG-080 - Rutinas vacias o con parametros absurdos se podian guardar
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-07-01
- **Fase:** Fase C UX y bugs medios
- **Severidad:** Media
- **Sintoma:** el constructor permitia guardar una rutina sin ejercicios o con sets/reps/rest
  fuera de rango util.
- **Causa raiz:** `RoutineUseCase.createOrUpdateRoutine` solo validaba nombre.
- **Solucion:** se exige al menos un ejercicio y rangos razonables para sets, reps, peso y descanso.
- **Prevencion:** `RoutineUseCaseTest` cubre rutinas vacias y parametros fuera de rango.
- **Fecha resolucion:** 2026-07-01

---

### BUG-079 - Composicion corporal aceptaba valores fuera de rango
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-07-01
- **Fase:** Fase C UX y bugs medios
- **Severidad:** Media
- **Sintoma:** campos como grasa corporal, agua o edad corporal aceptaban valores parseables
  pero imposibles, por ejemplo porcentajes mayores que 100.
- **Causa raiz:** `BodyCompositionDraft.isValidRaw()` solo comprobaba parseo numerico.
- **Solucion:** validacion por rango para porcentajes, peso, masas, grasa visceral y edad corporal.
- **Prevencion:** `BodyCompositionDraftTest` cubre valores imposibles.
- **Fecha resolucion:** 2026-07-01

---

### BUG-078 - Home y Body no ofrecian retry tras error inicial
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-07-01
- **Fase:** Fase C UX y bugs medios
- **Severidad:** Media
- **Sintoma:** un fallo transitorio en Home o Composicion corporal dejaba solo texto/error sin
  accion para reintentar.
- **Causa raiz:** las pantallas no exponian `refresh()` como accion UI en estados de error.
- **Solucion:** se anade boton `Reintentar` en carga inicial fallida y en aviso de Home con
  snapshot existente.
- **Prevencion:** nuevos estados de error transitorio deben exponer accion de recuperacion.
- **Fecha resolucion:** 2026-07-01

---

### BUG-077 - Busqueda de ejercicios refetcheaba por cada tecla
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-07-01
- **Fase:** Fase C UX y bugs medios
- **Severidad:** Media
- **Sintoma:** buscar ejercicios en Entrenar podia causar flicker y lecturas Room por cada
  cambio de texto.
- **Causa raiz:** `onSearchQueryChanged` llamaba `refreshExercises()` y esta consultaba el
  use case/repositorio cada vez.
- **Solucion:** `TrainViewModel` mantiene `allExercises` en estado y filtra busqueda/grupo en memoria.
- **Prevencion:** filtros de listas ya cargadas deben ser transformaciones locales salvo que haya
  paginacion real.
- **Fecha resolucion:** 2026-07-01

---

### BUG-076 - Home podia pisar estado entre sync Health Connect y refresh
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-07-01
- **Fase:** Fase C UX y bugs medios
- **Severidad:** Media
- **Sintoma:** al abrir Home, sync Health Connect y refresh de dashboard corrian en paralelo;
  el resultado tardio podia dejar metricas desactualizadas o limpiar errores de otra carga.
- **Causa raiz:** `HomeViewModel.init` lanzaba dos jobs independientes sin coordinacion.
- **Solucion:** el arranque usa un unico refresh con sync previo opcional dentro del mismo job.
- **Prevencion:** cargas que escriben el mismo estado deben compartir job o versionado de filtros.
- **Fecha resolucion:** 2026-07-01

---

### BUG-075 - Launch podia quedar en loading indefinidamente
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-07-01
- **Fase:** Fase B UX critica
- **Severidad:** Alta
- **Sintoma:** si `onboardingCompleted` fallaba o no emitia, la pantalla inicial podia quedarse
  en `Loading` sin salida.
- **Causa raiz:** `LaunchViewModel` colectaba el flujo sin `catch` ni timeout de fallback.
- **Solucion:** se captura el error y se aplica fallback a onboarding; un timeout de 3 s evita
  loading perpetuo si no hay emision.
- **Prevencion:** `LaunchViewModelTest` cubre emision normal, fallo y flujo sin emisiones.
- **Fecha resolucion:** 2026-07-01

---

### BUG-074 - Cardio sin GPS no mantenia foreground service
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-07-01
- **Fase:** Fase B UX critica
- **Severidad:** Alta
- **Sintoma:** cardio manual o GPS denegado caia a timer local; al salir de la UI no habia
  servicio foreground que mantuviera cronometro/notificacion.
- **Causa raiz:** `ActiveCardioViewModel` no arrancaba el servicio si `gpsEnabled=false` y
  `CardioForegroundService` se marcaba fallido si no habia location tracking.
- **Solucion:** el servicio arranca tambien sin GPS, usa FGS `health` y solo crea job de
  ubicacion cuando hay permiso/tipo GPS.
- **Prevencion:** revisar cualquier nuevo modo cardio contra los dos caminos FGS: `health` sin
  ubicacion y `location` con GPS real.
- **Fecha resolucion:** 2026-07-01

---

### BUG-073 - Plan semanal podia borrar sesiones al marcar descanso
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-07-01
- **Fase:** Fase B UX critica
- **Severidad:** Alta
- **Sintoma:** activar "Dia de descanso" en un dia con sesiones vaciaba la lista sin confirmacion.
- **Causa raiz:** `setRestDay(true)` limpiaba `sessions` directamente.
- **Solucion:** el primer intento solo muestra aviso y guarda confirmacion pendiente; el segundo
  intento sobre el mismo dia confirma el borrado.
- **Prevencion:** `WeeklyPlanViewModelTest` cubre que el primer toque no elimina sesiones.
- **Fecha resolucion:** 2026-07-01

---

### BUG-072 - Entrenamiento activo bloqueaba composicion con runBlocking
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-07-01
- **Fase:** Fase B UX critica
- **Severidad:** Media
- **Sintoma:** abrir el sheet de quick-add podia bloquear UI al leer ejercicios sincronamente.
- **Causa raiz:** `availableExercisesForQuickAdd()` usaba `runBlocking` desde el arbol Compose.
- **Solucion:** los ejercicios disponibles se cargan asincronamente en `ActiveWorkoutViewModel`
  y se exponen como estado.
- **Prevencion:** no usar `runBlocking` en presentation salvo tests; cargas UI deben ir por
  `viewModelScope`/estado.
- **Fecha resolucion:** 2026-07-01

---

### BUG-071 - Cancelar cardio activo descartaba la sesion sin confirmacion
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-07-01
- **Fase:** Fase B UX critica
- **Severidad:** Alta
- **Sintoma:** back del sistema o Cancelar en cardio activo borraban la sesion inmediatamente.
- **Causa raiz:** `ActiveCardioRoute` llamaba `cancelCardio()` directamente desde `BackHandler`
  y desde la accion de cancelar.
- **Solucion:** ambos caminos abren `AtlasDialog` con conservar entrenamiento o descartar sesion.
- **Prevencion:** acciones destructivas de sesiones activas deben pasar por confirmacion o undo.
- **Fecha resolucion:** 2026-07-01

---

### BUG-070 - Backups antiguos podian restaurar plan semanal con order_index colisionado
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-07-01
- **Fase:** Fase A seguridad e integridad
- **Severidad:** Alta
- **Sintoma:** un backup anterior al schema con `weekly_plan.order_index` podia ser actualizado
  con `order_index = 0` para varias sesiones del mismo dia y fallar contra el indice unico
  `(day_of_week, order_index)` durante el restore.
- **Causa raiz:** `BackupSnapshotUpgrader` aplicaba el mismo backfill escalar a todas las filas
  y `MIGRATION_4_5` hacia lo mismo a nivel SQL.
- **Solucion:** backup schema v5 reindexa sesiones por dia; Room schema v6 incluye
  `MIGRATION_5_6` idempotente y `MIGRATION_4_5` queda corregida para futuros upgrades 4->5.
- **Prevencion:** tests de upgrader cubren snapshots v3/v4 con multiples sesiones por dia y
  `AppDatabaseMigrationTest` cubre migraciones 4->5 y 5->6 con colisiones.
- **Fecha resolucion:** 2026-07-01

---

### BUG-069 - Auto-backup de Drive podia fallar silenciosamente
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-07-01
- **Fase:** Fase A seguridad e integridad
- **Severidad:** Alta
- **Sintoma:** si el grant silencioso de Drive fallaba o requeria consentimiento, el worker
  terminaba como exito sin subir backup ni distinguir la causa.
- **Causa raiz:** `DriveAccessTokenProvider.silentAccessToken()` devolvia `String?`, mezclando
  falta de autorizacion y fallo transitorio en `null`, y no usaba `OAUTH_WEB_CLIENT_ID` para
  offline access.
- **Solucion:** el provider devuelve `DriveAccessTokenResult` tipado y el worker reintenta solo
  fallos transitorios; la solicitud OAuth usa `requestOfflineAccess` cuando hay Web Client ID.
- **Prevencion:** `BackupWorkerRunnerTest` cubre grant faltante, fallo de grant, exito y fallos
  permanentes sin retry.
- **Fecha resolucion:** 2026-07-01

---

### BUG-068 - Backup restore no aplicaba upgrader de snapshots
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-06-28
- **Fase:** Auditoria Atlas Peak
- **Severidad:** Alta
- **Sintoma:** un backup antiguo podia descifrarse y decodificarse, pero fallar al restaurar por columnas nuevas de `weekly_plan`.
- **Causa raiz:** `BackupSnapshotUpgrader` existia, pero `BackupJsonCodec.decode()` no lo aplicaba antes de entregar el snapshot al restore.
- **Solucion:** `BackupJsonCodec` inyecta y aplica el upgrader; el schema de backup sube a 4 y rellena `order_index`.
- **Prevencion:** tests de upgrader y restore cubren snapshots antiguos.
- **Fecha resolucion:** 2026-06-28

---

### BUG-067 - Plan semanal solo soportaba una sesion por dia
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-06-28
- **Fase:** Auditoria Atlas Peak
- **Severidad:** Alta
- **Sintoma:** el usuario tenia que elegir fuerza o cardio por dia; no podia planificar fuerza + cardio o doble sesion.
- **Causa raiz:** `weekly_plan` modelaba una unica fila efectiva por dia sin orden de sesion.
- **Solucion:** Room v5 agrega `order_index`, el dominio expone `WeeklyPlanSession`, la UI permite varias sesiones y las notificaciones se programan por sesion.
- **Prevencion:** migracion 4->5, schema exportado y tests de use case/notificaciones.
- **Fecha resolucion:** 2026-06-28

---

### BUG-066 - Cardio usaba peso fijo y aceptaba metricas imposibles
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-06-28
- **Fase:** Auditoria Atlas Peak
- **Severidad:** Alta
- **Sintoma:** las calorias se estimaban con 75 kg aunque hubiera peso real; distancias, velocidades y puntos GPS anómalos podian contaminar metricas.
- **Causa raiz:** `CardioUseCase` no consultaba composicion corporal y tenia validacion minima de manual/GPS.
- **Solucion:** calorias usan ultimo peso valido con fallback 75 kg; ruta, timestamps, distancia y velocidad se filtran por limites razonables por deporte.
- **Prevencion:** tests de calorias con peso real y validacion manual/GPS en el use case.
- **Fecha resolucion:** 2026-06-28

---

### BUG-065 - Health Connect era todo-o-nada y no importaba composicion corporal
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-06-28
- **Fase:** Auditoria Atlas Peak
- **Severidad:** Alta
- **Sintoma:** si faltaba un permiso se bloqueaba toda la sync; la documentacion prometia datos de bascula pero no se leian records corporales.
- **Causa raiz:** permisos agrupados en una lista global y sin pipeline de import para peso/grasa/masa magra/agua.
- **Solucion:** sync parcial por capacidad e import de `WeightRecord`, `BodyFatRecord`, `LeanBodyMassRecord` y `BodyWaterMassRecord`.
- **Prevencion:** tests de sync parcial y documentacion alineada con permisos reales.
- **Fecha resolucion:** 2026-06-28

---

### BUG-064 - Borrado de serie sin undo y campos poco accesibles
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-06-28
- **Fase:** Auditoria Atlas Peak
- **Severidad:** Media
- **Sintoma:** borrar una serie era inmediato y los inputs peso/reps no indicaban con claridad la serie a lectores de pantalla.
- **Causa raiz:** el flujo no tenia estado de eliminacion pendiente ni etiquetas semanticas por serie.
- **Solucion:** confirmacion ligera cuando hay datos, snackbar con Deshacer, y content descriptions tipo "Peso serie N" / "Repeticiones serie N".
- **Prevencion:** mantener acciones destructivas con undo o confirmacion cuando hay datos introducidos.
- **Fecha resolucion:** 2026-06-28

---

### BUG-063 - Export JSON/CSV claro no advertia suficiente
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-06-28
- **Fase:** Auditoria Atlas Peak
- **Severidad:** Media
- **Sintoma:** el usuario podia exportar datos sensibles en claro sin un aviso justo antes de compartir.
- **Causa raiz:** el flujo trataba export portable y backup cifrado como acciones vecinas, pero no explicitaba el riesgo del export en claro.
- **Solucion:** confirmacion antes de JSON/CSV y limpieza de exports temporales antiguos.
- **Prevencion:** cualquier export claro nuevo debe pasar por aviso visible y excluir secretos.
- **Fecha resolucion:** 2026-06-28

---

### BUG-062 - Politica estatica de bottom nav contradecia la navegacion real
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-06-23
- **Fase:** Redisenio UI/UX
- **Severidad:** Media
- **Sintoma:** `BottomNavigationPolicyTest` fallaba porque esperaba tabs sin `saveState/restoreState`, mientras la app ya conservaba estado de tabs para no perder borradores ni scroll.
- **Causa raiz:** el test y `DOCS_TECNICA.md` quedaron anclados a una politica anterior despues de cambiar el patron de navegacion.
- **Solucion:** el test ahora exige `saveState=true`, `restoreState=true`, bottom nav icon-only accesible y subrutas de Perfil con Perfil seleccionado; `DOCS_TECNICA.md` documenta la politica real.
- **Prevencion:** la politica de bottom nav queda cubierta por test estatico antes de aceptar cambios de navegacion.
- **Fecha resolucion:** 2026-06-23

---

### BUG-061 - EditProfileScreen estaba implementada pero no era enrutable
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-06-23
- **Fase:** Redisenio UI/UX
- **Severidad:** Media
- **Sintoma:** existian `EditProfileScreen` y `EditProfileViewModel`, pero Perfil no tenia accion para abrirlos y el NavHost no exponia la ruta.
- **Causa raiz:** la pantalla se habia creado sin integrarla en `AppRoute`, `AtlasPeakNavHost`, bottom navigation chrome routes ni la politica de rutas sensibles.
- **Solucion:** se anadio `AppRoute.EditProfile`, Perfil enlaza al editor, NavHost registra `EditProfileRoute`, la bottom nav mantiene Perfil seleccionado en esa subruta y `SensitiveRoutePolicy` la clasifica.
- **Prevencion:** `BottomNavigationPolicyTest` y `SensitiveRoutePolicyTest` cubren `EditProfile` como subruta de Perfil.
- **Fecha resolucion:** 2026-06-23

---

### BUG-060 - Crash al cambiar periodos en Progreso
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-06-11
- **Fase:** Auditoria pre-publicacion
- **Severidad:** Alta
- **Sintoma:** al cambiar rapidamente entre Semana, Mes, 3 meses, Ano o Ano actual en Progreso, la app podia cerrarse en el movil.
- **Causa raiz:** `ProgressViewModel` lanzaba recargas concurrentes sin cancelar la anterior, sin descartar resultados obsoletos y sin capturar excepciones del flujo de carga; ademas los graficos podian recibir valores no finitos.
- **Solucion:** `ProgressViewModel` cancela el refresh anterior, aplica resultados solo si siguen correspondiendo al filtro actual y convierte fallos en estado de error UI. Los graficos de Progreso, Home y Composicion corporal filtran `NaN`/`Infinity`.
- **Prevencion:** `ProgressViewModelTest` cubre cambios rapidos de periodo, fallo de carga sin crash y seleccion repetida sin recarga.
- **Fecha resolucion:** 2026-06-11

---

### BUG-044 - Export CSV no neutralizaba formulas de hoja de calculo
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-06-08
- **Fase:** Auditoria seguridad/backup
- **Severidad:** Media
- **Sintoma:** valores textuales exportados a CSV podian empezar por `=`, `+`, `-` o `@`; al abrir el archivo en una hoja de calculo podian evaluarse como formulas.
- **Causa raiz:** `BackupExportFormatter.escapeCsv()` solo escapaba caracteres de CSV, pero no aplicaba la defensa propia del contexto de consumo: hojas de calculo.
- **Solucion:** `BackupExportFormatter` neutraliza celdas textuales de formula con apostrofe antes del escapado CSV y mantiene intactos los primitivos JSON numericos.
- **Prevencion:** test unitario sobre `hc_sleep_sessions` cubre los prefijos peligrosos y un numero negativo legitimo.
- **Fecha resolucion:** 2026-06-08

---

### BUG-043 - Restore de backup aceptaba filas JSON malformadas
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-30
- **Fase:** Auditoria seguridad/bugs
- **Severidad:** Media
- **Sintoma:** un backup descifrado podia contener filas con columnas faltantes, tipos incompatibles o valores JSON anidados; el restore solo rechazaba tablas/columnas desconocidas y dejaba que SQLite/Room lidiaran con el resto.
- **Causa raiz:** `RoomBackupSnapshotStore.restore()` validaba nombres de tabla y columnas, pero no exigia set exacto de columnas ni compatibilidad entre valor JSON y afinidad SQLite antes de escribir.
- **Solucion:** `RoomBackupSnapshotStore` valida cada fila contra `PRAGMA table_info`: columnas exactas, `NULL` solo en columnas nullable, valores primitivos y afinidad INTEGER/REAL/TEXT/BLOB/NUMERIC compatible antes de borrar o insertar datos.
- **Prevencion:** tests instrumentados cubren columnas faltantes, valores JSON no primitivos y tipos incompatibles sin perder los datos existentes.
- **Fecha resolucion:** 2026-05-30

---

### BUG-042 - El fallo del foreground service de fuerza podia borrarse antes de llegar a la UI
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-30
- **Fase:** Auditoria seguridad/bugs
- **Severidad:** Media
- **Sintoma:** si Android rechazaba el `WorkoutForegroundService` por permiso runtime/FGS, el servicio marcaba `failed=true`, llamaba a `stopSelf()` y `onDestroy()` reseteaba inmediatamente `WorkoutTimerRegistry`.
- **Causa raiz:** el teardown de fuerza siempre limpiaba el estado, a diferencia de cardio, que conserva el estado fallido para que la UI pueda avisar.
- **Solucion:** `WorkoutForegroundService.onDestroy()` preserva el estado cuando `WorkoutTimerRegistry.state.failed` es true; `ACTION_STOP` sigue limpiando explicitamente.
- **Prevencion:** `StaticSecurityPolicyTest` bloquea que el servicio vuelva a borrar el fallo antes de que la UI lo observe.
- **Fecha resolucion:** 2026-05-30

---

### BUG-041 - Entrenamiento planificado no era accion principal en Home ni Entrenar
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-26
- **Fase:** Polish UI/UX
- **Severidad:** Media
- **Sintoma:** Inicio mostraba metricas antes que la rutina planificada del dia, y Entrenar abria por defecto la biblioteca/creacion de ejercicios en vez de la accion de iniciar una rutina.
- **Causa raiz:** `HomeScreen` no consultaba el plan semanal y `TrainUiState` arrancaba en `TrainTab.Exercises`; ademas `RoutineContent` mostraba el constructor antes del CTA de inicio.
- **Solucion:** `HomeViewModel` carga la rutina de hoy desde `WeeklyPlanUseCase`, `HomeScreen` muestra una card "Entrenamiento de hoy" con CTA, `TrainUiState` abre en `Routines` y el detalle/inicio de rutina se renderiza antes del builder.
- **Prevencion:** mantener el inicio de sesion de entrenamiento como primer CTA visible cuando exista una rutina planificada o seleccionada.
- **Fecha resolucion:** 2026-05-26

---

### BUG-040 - Iconos de cardio ignoraban el icon_name de seed data
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-26
- **Fase:** Polish UI/UX
- **Severidad:** Baja
- **Sintoma:** todos los tipos de cardio se mostraban con el mismo icono de correr aunque la base de datos seed ya tenia `icon_name` distintos para bici, remo, natacion, etc.
- **Causa raiz:** `CardioType` de dominio no exponia `iconName` y `TrainScreen.CardioTypeCard` hardcodeaba `DirectionsRun`.
- **Solucion:** `CardioType` expone `iconName`, `RoomCardioRepository` lo mapea desde Room y `CardioTypeCard` resuelve iconos Material distintos por tipo.
- **Prevencion:** si un modelo trae metadatos visuales desde seed/schema, la UI no debe reemplazarlos por un icono generico.
- **Fecha resolucion:** 2026-05-26

---

### BUG-039 - Onboarding de perfil usaba texto libre para genero y objetivo
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-26
- **Fase:** Polish UI/UX
- **Severidad:** Media
- **Sintoma:** al tocar Genero u Objetivo aparecia el teclado, no un selector; el usuario podia escribir valores libres inconsistentes.
- **Causa raiz:** `OnboardingScreen` usaba `AtlasTextField` para campos de opcion cerrada.
- **Solucion:** se sustituyen esos campos por selectores con `FilterChip`: Masculino/Femenino y objetivos predefinidos.
- **Prevencion:** campos de taxonomia cerrada se implementan como selector, no como input de texto.
- **Fecha resolucion:** 2026-05-26

---

### BUG-038 - Entrenamiento activo no avanzaba de ejercicio y el descanso acababa en silencio
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-26
- **Fase:** Polish UI/UX
- **Severidad:** Alta
- **Sintoma:** al completar todos los sets de un ejercicio la pantalla no pasaba al siguiente; el CTA seguia diciendo Finalizar y podia cerrar la sesion. Al llegar a cero, el descanso desaparecia sin sonido ni vibracion final.
- **Causa raiz:** `ActiveWorkoutScreen` no sincronizaba el `HorizontalPager` con el estado de sets completados y el feedback sonoro/haptico estaba ligado al inicio del timer, no al final. El boton primario no distinguia "siguiente ejercicio" de "finalizar entrenamiento".
- **Solucion:** el pager avanza al siguiente ejercicio tras acabar/omitir el descanso, el CTA cambia a Siguiente o queda deshabilitado hasta completar sets, y el feedback se dispara cuando el rest timer llega a cero.
- **Prevencion:** el estado completado de un ejercicio debe tener una transicion explicita de pagina y el feedback del descanso debe probarse en el evento de fin, no solo en el de inicio.
- **Fecha resolucion:** 2026-05-26

---

### BUG-037 - AssistChip de badge en composicion corporal sugeria ser interactivo
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-26
- **Fase:** Auditoria final
- **Severidad:** Baja
- **Sintoma:** la lista de metricas de composicion corporal mostraba un `AssistChip` (Health Connect / Manual only) que respondia al tap con ripple pero no hacia nada (`onClick = {}`), confundiendo al usuario sobre si era una accion.
- **Causa raiz:** el chip se uso como etiqueta informativa con `onClick` vacio en `BodyCompositionScreen.kt`. Material da ripple por defecto a cualquier `AssistChip` clickable.
- **Solucion:** se anade `enabled = false` al chip en `BodyCompositionScreen.kt`, asi pierde el ripple y queda claramente como badge informativo.
- **Prevencion:** revisar `Grep` periodico de `onClick = \{\}` para detectar componentes interactivos sin accion.
- **Fecha resolucion:** 2026-05-26

---

### BUG-036 - Teclado virtual tapa los TextField (edge-to-edge sin imePadding)
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-26
- **Fase:** Auditoria final
- **Severidad:** Alta
- **Sintoma:** al tocar un `TextField` (onboarding, backup, ajustes, plan semanal, entrenamiento) el teclado se abria por encima del campo, dejando al usuario escribiendo a ciegas. El borde inferior de la pantalla seguia en su sitio en vez de empujar el contenido hacia arriba.
- **Causa raiz:** `MainActivity` activa `enableEdgeToEdge()` pero el contenedor del `NavHost` en `AtlasPeakApp.kt` no aplicaba `imePadding()` y ninguna pantalla individual lo hacia tampoco. El `Scaffold` de Material 3 no incluye `WindowInsets.ime` en su `contentWindowInsets` por defecto, asi que el contenido quedaba debajo del teclado.
- **Solucion:** el `Box` que envuelve `AtlasPeakNavHost` ahora aplica `.fillMaxSize().padding(innerPadding).imePadding()`. Asi todas las pantallas se empujan hacia arriba cuando aparece el teclado, sin tocar pantalla por pantalla.
- **Prevencion:** el `imePadding()` esta centralizado en el contenedor raiz, asi que cualquier pantalla nueva con inputs hereda el comportamiento automaticamente.
- **Fecha resolucion:** 2026-05-26

---

### BUG-035 - Menu inferior con taps no registrados por doble inset y altura insuficiente
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-26
- **Fase:** Auditoria final
- **Severidad:** Alta
- **Sintoma:** el menu inferior se renderizaba pero al tocar los items no cambiaba de pantalla; los arreglos previos (BUG-032/033/034) corrigieron la logica de navegacion pero el problema persistia a nivel de interaccion.
- **Causa raiz:** el `NavigationBar` Material 3 aplica por defecto `NavigationBarDefaults.windowInsets` (system navigation bars). El contenedor `Box` exterior ya aplicaba `navigationBarsPadding()`, asi que el padding de la barra del sistema se sumaba dos veces. Combinado con `Modifier.height(spacing.minTouchTarget + spacing.md)` (64dp, frente a los 80dp por defecto de Material), el area util quedaba comprimida y los `NavigationBarItem` perdian o desplazaban su touch target detras del system nav bar.
- **Solucion:** en `AtlasPeakApp.kt` se elimina la altura forzada del `NavigationBar` (usa la altura por defecto de Material 3, 80dp) y se le pasa `windowInsets = WindowInsets(0, 0, 0, 0)` para que el unico responsable de los insets sea el `Box` envolvente. Asi cada `NavigationBarItem` recibe su touch target completo y los taps cambian de pestana de forma fiable.
- **Prevencion:** el fix mantiene a `BottomNavigationPolicyTest` verde sin nuevas suposiciones. No hay regresion de logica de navegacion (todas las aserciones siguen pasando).
- **Fecha resolucion:** 2026-05-26

---

### BUG-034 - Menu inferior restauraba stacks inestables
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-25
- **Fase:** Auditoria final
- **Severidad:** Alta
- **Sintoma:** el menu inferior seguia sin comportarse de forma fiable al cambiar entre `Home`, `Entrenar`, `Progreso`, `Cuerpo` y `Perfil`; en especial podia depender de un back stack anterior o volver a subpantallas.
- **Causa raiz:** el arreglo previo uso `Home` como raiz global y mantuvo `saveState/restoreState` en los tabs. Eso es fragil: `Home` es una pantalla, no el contenedor autenticado, y restaurar estado en tabs puede revivir rutas hijas en vez de abrir la raiz del tab.
- **Solucion:** se anade `AppRoute.AppGraph` como grafo autenticado estable con `Home` como start destination. El menu inferior navega con `popUpTo(AppGraph)`, `launchSingleTop`, `saveState = false` y `restoreState = false`; tocar un tab siempre abre su ruta raiz.
- **Prevencion:** `BottomNavigationPolicyTest` ahora exige `AppGraph` como raiz de tabs, prohibe `restoreState = true`/`saveState = true` en el menu inferior y mantiene cubiertas las rutas visibles/ocultas.
- **Fecha resolucion:** 2026-05-25

---

### BUG-033 - Regresion de tabs inferiores tras wrapper de navegacion
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-25
- **Fase:** Auditoria final
- **Severidad:** Alta
- **Sintoma:** `Entrenar`, `Progreso`, `Cuerpo` y `Perfil` seguian sin abrir de forma fiable desde el menu inferior.
- **Causa raiz:** el arreglo anterior volvio a meter las tabs dentro de un grafo wrapper `main` y el menu hizo `popUpTo(main)`. Ese grafo no aportaba nada y dejaba la navegacion dependiendo de un destino intermedio innecesario.
- **Solucion:** se elimina `AppRoute.Main`; `Launch` y `Onboarding` entran directamente en `Home`, y el menu inferior usa `Home` como raiz estable del back stack autenticado.
- **Prevencion:** `BottomNavigationPolicyTest` bloquea el regreso del wrapper `main`, comprueba las cinco rutas de tabs y mantiene prohibido `findStartDestination()` en el menu inferior.
- **Nota 2026-05-25:** supersedido por `BUG-034`. La conclusion correcta no era "sin grafo autenticado", sino "sin grafo wrapper mal usado"; el fix definitivo usa `AppGraph`.
- **Fecha resolucion:** 2026-05-25

---

### BUG-032 - Bottom navigation crasheaba al abrir tabs autenticados
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-25
- **Fase:** Auditoria final
- **Severidad:** Alta
- **Sintoma:** desde el dashboard, tocar `Entrenar`, `Progreso`, `Cuerpo` o `Perfil` cerraba la app.
- **Causa raiz:** la bottom navigation hacia `popUpTo(navController.graph.findStartDestination().id)`, pero el start destination real del grafo es `launch`, un destino transitorio eliminado del back stack despues de login/onboarding.
- **Solucion:** `AtlasPeakApp` usa `popUpTo(AppRoute.Home.route)` como raiz estable de los tabs.
- **Prevencion:** `BottomNavigationPolicyTest` bloquea que los tabs vuelvan a depender de `findStartDestination`.
- **Fecha resolucion:** 2026-05-25

---

### BUG-031 - SQLCipher nativo no se cargaba antes de abrir Room
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-24
- **Fase:** Auditoria final
- **Severidad:** Alta
- **Sintoma:** la app instalada en el emulador arrancaba `MainActivity` y caia con `UnsatisfiedLinkError` en `net.zetetic.database.sqlcipher.SQLiteConnection.nativeOpen`.
- **Causa raiz:** `sqlcipher-android` incluye `libsqlcipher.so`, pero la app no llamaba a `System.loadLibrary("sqlcipher")` antes de que Hilt construyera la DB Room cifrada.
- **Solucion:** `AtlasPeakApplication.attachBaseContext()` carga `sqlcipher` antes de `onCreate()` y de cualquier acceso a Room.
- **Prevencion:** `StaticSecurityPolicyTest` verifica que la carga nativa de SQLCipher ocurre antes del acceso de aplicacion a la DB.
- **Fecha resolucion:** 2026-05-24

---

### BUG-030 - Timeout de desbloqueo local no estaba conectado a lifecycle
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-24
- **Fase:** Auditoria final
- **Severidad:** Alta
- **Sintoma:** al volver a la app desde background, una ruta sensible seguia accesible aunque el timeout hubiera expirado.
- **Causa raiz:** `LaunchViewModel` solo decidia onboarding/login en arranque; no habia observador `ON_RESUME` ni guard central de sesion.
- **Solucion:** `SessionLockViewModel` evalua rutas sensibles en resume y navega a `Login` si `LocalAuthUseCase.shouldRequireSessionUnlock()` lo exige.
- **Prevencion:** `SessionLockViewModelTest` y tests de timeout en `LocalAuthUseCaseTest`.
- **Fecha resolucion:** 2026-05-24

---

### BUG-029 - Export plaintext no exigia reautenticacion y retenia password UI
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-24
- **Fase:** Auditoria final
- **Severidad:** Media
- **Sintoma:** JSON/CSV en claro podian exportarse desde una sesion abierta sin step-up auth; la password escrita seguia en `BackupRestoreUiState`.
- **Causa raiz:** backup/export mezclaba accion sensible con sesion ya autenticada y limpiaba el `CharArray`, pero no el `String` de origen.
- **Solucion:** export JSON/CSV verifica contrasena local antes de escribir; create/restore/export/autobackup limpian el estado `password`.
- **Prevencion:** `BackupRestoreViewModelTest` cubre password incorrecta, export correcto y limpieza de estado.
- **Fecha resolucion:** 2026-05-24

---

### BUG-028 - Presentation de backup importaba data layer
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-24
- **Fase:** Auditoria final
- **Severidad:** Media
- **Sintoma:** `BackupRestoreViewModel` y `BackupRestoreScreen` importaban clases de `com.atlaspeak.data.backup`.
- **Causa raiz:** Fase 12 implemento backup rapido alrededor de managers de data sin contrato de dominio.
- **Solucion:** se anaden modelos/usecase/repositorio de backup en `domain`, `DataBackupRepository` mapea data->domain y `presentation` consume solo dominio.
- **Prevencion:** `StaticArchitecturePolicyTest` falla si `presentation` vuelve a importar `data`, Room, Retrofit u OkHttp.
- **Fecha resolucion:** 2026-05-24

---

### BUG-027 - Graficos, mapa y toggles tenian semantica insuficiente para TalkBack
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-24
- **Fase:** 16
- **Severidad:** Media
- **Sintoma:** charts, mapa GPS y filas de switch/checkbox podian ser visualmente correctos pero pobres para lectores de pantalla.
- **Causa raiz:** Vico/GoogleMap se renderizaban sin resumen semantico y algunos controles dejaban label y switch/checkbox como nodos separados.
- **Solucion:** resumen localizado para charts y ruta GPS; filas de switch/checkbox usan click del row, rol y merge semantico; rest timer expone progreso y texto.
- **Prevencion:** revision lateral de UI obligatoria en Fase 16 y `StaticUiPolicyTest` mantiene i18n/descripcion minima.
- **Fecha resolucion:** 2026-05-24

---

### BUG-026 - Estados seleccionados usaban primaryContainer sin token Atlas
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-24
- **Fase:** 16
- **Severidad:** Media
- **Sintoma:** tarjetas seleccionadas usaban `primaryContainer`, pero el tema no lo definia y caia en los colores baseline de Material.
- **Causa raiz:** el tema solo fijo `primary/onPrimary` y dejo roles contenedor a defaults de Material.
- **Solucion:** se definen `primaryContainer/onPrimaryContainer` light/dark, los textos secundarios seleccionados usan `onPrimaryContainer`, y el test de contraste cubre esos pares.
- **Prevencion:** `ThemeAccessibilityTest` cubre roles contenedor ademas de roles principales.
- **Fecha resolucion:** 2026-05-24

---

### BUG-025 - Tipografia del sistema de diseno no estaba aplicada
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-24
- **Fase:** 16
- **Severidad:** Media
- **Sintoma:** `Typography.kt` usaba `FontFamily.SansSerif` aunque `DESIGN.md` y `SPEC.md` exigen Poppins para titulos/numeros e Inter para cuerpo/labels.
- **Causa raiz:** la fase de fundacion dejo fallback generico en vez de assets locales o Google Fonts.
- **Solucion:** fuentes Poppins 600/700 e Inter variable vendorizadas en `res/font/`, `Typography.kt` usa esos assets y `THIRD_PARTY_NOTICES.md` registra origen/licencia.
- **Prevencion:** `StaticUiPolicyTest` falla si vuelve `FontFamily.SansSerif` o faltan los assets esperados.
- **Fecha resolucion:** 2026-05-24

---

### BUG-024 - Primary light no cumplia contraste AA con texto blanco
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-24
- **Fase:** 16
- **Severidad:** Alta
- **Sintoma:** `onPrimary` blanco sobre `primary #E53935` daba contraste ~4.23:1, por debajo del minimo WCAG AA 4.5:1 para texto normal.
- **Causa raiz:** el rojo de marca se copio como valor visual aproximado sin test de contraste automatizado.
- **Solucion:** primary light cambia a `#D32F2F`; icono, swatch rojo por defecto y specs quedan alineados.
- **Prevencion:** `ThemeAccessibilityTest` verifica pares `on*`/fondo de light y dark contra WCAG AA.
- **Fecha resolucion:** 2026-05-24

---

### BUG-023 - Objetivo de cobertura domain/data no era verificable
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-24
- **Fase:** 15
- **Severidad:** Media
- **Sintoma:** el spec exigia >=70% de cobertura en `domain` y `data`, pero Gradle/CI no tenian tarea de cobertura ni threshold.
- **Causa raiz:** se habian anadido tests por fase, pero no una metrica ejecutable que fallara el build si la cobertura bajaba.
- **Solucion:** JaCoCo en `app/build.gradle.kts`, tarea `jacocoDebugDomainDataCoverageVerification` con umbral 70% y paso CI dedicado.
- **Prevencion:** `DOCS_TECNICA.md` documenta la tarea y CI la ejecuta en cada push/PR.
- **Fecha resolucion:** 2026-05-24

---

### BUG-022 - Recordatorios aceptaban dias de semana invalidos
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-24
- **Fase:** 15
- **Severidad:** Baja
- **Sintoma:** `NotificationWorkNames.trainingReminder()` podia generar nombres como `training_reminder_0` o `training_reminder_8`.
- **Causa raiz:** no habia validacion del rango ISO 1..7 en la fabrica de nombres de WorkManager.
- **Solucion:** `NotificationWorkNames` valida `dayOfWeek in 1..7`; `TrainingReminderWorker` trata input corrupto como `Result.success()` para evitar retries inutiles.
- **Prevencion:** `NotificationWorkNamesTest` cubre dias validos e invalidos.
- **Fecha resolucion:** 2026-05-24

---

### BUG-021 - Notificaciones foreground exponian actividad en lockscreen
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-24
- **Fase:** 14
- **Severidad:** Media
- **Sintoma:** las notificaciones de entrenamiento/cardio podian mostrar tiempo/distancia en pantalla bloqueada.
- **Causa raiz:** los servicios foreground tenian builders y canales propios sin `VISIBILITY_PRIVATE`.
- **Solucion:** visibilidad privada en builders/canales de fuerza, cardio y canales generales de notificacion.
- **Prevencion:** test estatico de builders/canales privados en `StaticSecurityPolicyTest`.
- **Fecha resolucion:** 2026-05-24

---

### BUG-020 - Spec seguia pidiendo Wear OS dentro de v1
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-24
- **Fase:** 13
- **Severidad:** Media
- **Sintoma:** aunque la tabla de fases marcaba Wear OS como diferido a v2, secciones antiguas seguian listando dependencias, modulo y tareas de Wear como si fueran parte de v1.
- **Causa raiz:** se aplazo Wear en v2.2, pero no se limpio todo el texto operativo del spec.
- **Solucion:** `SPEC.md` deja Fase 13 como verificacion de aplazamiento, marca dependencias/protocolo Wear como v2 y elimina la instruccion de crear estructura Wear en Fase 1.
- **Prevencion:** cuando una fase se difiere, limpiar tabla, detalle de fase, dependencias y arbol de modulos en la misma sesion.
- **Fecha resolucion:** 2026-05-24

---

### BUG-019 - Drive upload usaba multipart form-data
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-24
- **Fase:** 12
- **Severidad:** Alta
- **Sintoma:** el backup a Drive compilaba, pero el endpoint `uploadType=multipart` podia rechazar la subida porque el cuerpo era `multipart/form-data`.
- **Causa raiz:** se uso `@Multipart` de Retrofit, pensado para formularios, y se asumio que equivalia al multipart de Drive.
- **Solucion:** `DriveApiService` recibe un `RequestBody` `multipart/related`; `RetrofitDriveBackupService` construye metadata JSON primero y binario cifrado despues.
- **Prevencion:** test unitario `Drive upload body uses multipart related with metadata before encrypted media`.
- **Fecha resolucion:** 2026-05-24

---

### BUG-018 - Auto-backup repetia subidas por last_backup_at
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-24
- **Fase:** 12
- **Severidad:** Media
- **Sintoma:** el worker podia subir un backup diario aunque el usuario no cambiara datos, porque el backup anterior actualizaba `app_settings.last_backup_at`.
- **Causa raiz:** el hash estable incluia una columna que muta como efecto lateral del propio backup.
- **Solucion:** la canonicalizacion del snapshot para hash ignora `last_backup_at` y mantiene el resto de settings.
- **Prevencion:** test unitario `backup change hash canonicalization ignores last backup timestamp only`.
- **Fecha resolucion:** 2026-05-24

---

### BUG-017 - Accion Drive pendiente se perdia al volver del consentimiento
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-24
- **Fase:** 12
- **Severidad:** Media
- **Sintoma:** tras autorizar Drive, la app podia no ejecutar listar/backup/restore si la Activity se recreaba durante el flujo de consentimiento.
- **Causa raiz:** `BackupRestoreRoute` guardaba la accion pendiente en `remember`, que no sobrevive recreacion.
- **Solucion:** la accion pendiente se guarda como clave `rememberSaveable` y se reconstruye al recibir el resultado de AuthorizationClient.
- **Prevencion:** no guardar acciones pendientes de Activity Result en estado no saveable.
- **Fecha resolucion:** 2026-05-24

---

### BUG-016 - Health Connect no soporta porcentaje de agua corporal
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-24
- **Fase:** 10
- **Severidad:** Alta
- **Sintoma:** el spec marcaba `% agua corporal` como sincronizable con Health Connect, pero el API disponible es `BodyWaterMassRecord`, expresado como masa, no porcentaje.
- **Causa raiz:** se mezclo la metrica comun de basculas inteligentes (`water_percent`) con el contrato real de Health Connect.
- **Solucion:** se anade `body_water_mass_kg` con migracion Room v1->v2; `% agua corporal` queda manual y la exportacion Health Connect usa masa de agua corporal.
- **Prevencion:** tests de mapper para `BodyWaterMassRecord` y actualizacion de `SPEC.md`/`DOCS_TECNICA.md` para separar porcentaje vs masa.
- **Fecha resolucion:** 2026-05-24

---

### BUG-015 - Dashboard etiquetaba minimo cardiaco como frecuencia en reposo
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-24
- **Fase:** 8
- **Severidad:** Media
- **Sintoma:** el dashboard llamaba "frecuencia cardiaca en reposo" a un minimo diario calculado desde muestras genericas de pulso.
- **Causa raiz:** la DB v1 tiene `hc_heart_rate_samples`, pero no una cache dedicada para `RestingHeartRateRecord`.
- **Solucion:** el widget queda etiquetado como minimo diario de frecuencia cardiaca hasta que Fase 10 anada la lectura/caché real de reposo.
- **Prevencion:** no usar nombres clinicos o fisiologicos si el dato importado no tiene esa semantica exacta.
- **Fecha resolucion:** 2026-05-24

---

### BUG-014 - Consistencia semanal contaba dias fuera del plan
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-24
- **Fase:** 8
- **Severidad:** Media
- **Sintoma:** la consistencia con plan podia contar entrenamientos hechos en dias no planificados y, ademas, el target semanal podia duplicar un dia al convertir `now - 7 dias` a fechas inclusivas.
- **Causa raiz:** se mezclo una ventana exacta en milisegundos con conteo inclusivo de calendario y el numerador no filtraba por `weekly_plan`.
- **Solucion:** la semana empieza el lunes local, el numerador con plan solo cuenta dias activos planificados y el target usa los dias calendario del periodo.
- **Prevencion:** tests unitarios `snapshot ignores active days outside weekly plan` y `snapshot does not count eight calendar days for weekly plan target`.
- **Fecha resolucion:** 2026-05-24

---

### BUG-013 - Cardio aparecia duplicado como fuerza en Progreso
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-24
- **Fase:** 7
- **Severidad:** Alta
- **Sintoma:** una sesion de cardio completada podia aparecer dos veces en Progreso: como cardio real y como fuerza vacia con `0.0 kg`.
- **Causa raiz:** `workout_sessions` es cabecera comun para fuerza/cardio, pero `WorkoutRepository.sessions()` leia todas las filas sin filtrar `type = 'STRENGTH'`.
- **Solucion:** `WorkoutDao` expone queries especificas de fuerza, `RoomWorkoutRepository` usa solo esas queries y `ProgressUseCase` ignora defensivamente sesiones sin sets completados.
- **Prevencion:** test de regresion en `ProgressUseCaseTest` con una cabecera sin sets para evitar duplicados en historial.
- **Fecha resolucion:** 2026-05-24

### BUG-012 - Fallo interno del FGS cardio podia borrar el estado de error
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-23
- **Fase:** 6
- **Severidad:** Media
- **Sintoma:** si `CardioForegroundService` fallaba dentro de `startForeground`, el servicio podia destruirse y resetear el registry antes de que la UI mostrara el error.
- **Causa raiz:** `onDestroy()` llamaba siempre a `stopTracking()` y este limpiaba `CardioTrackerRegistry`.
- **Solucion:** `stopTracking(clearState)` conserva el estado `failed=true` cuando el servicio muere por fallo interno y solo limpia en parada normal.
- **Prevencion:** revisar errores asincronos de cada foreground service, no solo excepciones en el punto de arranque.
- **Fecha resolucion:** 2026-05-23

### BUG-011 - Cardio GPS denegado podia degradar mal a una sesion sin ruta
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-23
- **Fase:** 6
- **Severidad:** Alta
- **Sintoma:** un tipo de cardio con GPS podia arrancar sin permiso de ubicacion y acabar como sesion sin puntos, confundiendo GPS real con entrada manual.
- **Causa raiz:** la primera implementacion del servicio comprobaba permisos internamente, pero la UI no tenia flujo explicito para denegacion.
- **Solucion:** `ActiveCardio` solicita `ACCESS_FINE_LOCATION`; si se deniega, muestra aviso y habilita distancia/velocidad manual. El repositorio marca `source` como `GPS` solo si existe ruta.
- **Prevencion:** cada permiso opcional debe tener estado de UI y degradacion de datos explicita.
- **Fecha resolucion:** 2026-05-23

### BUG-010 - Historial de cardio ordenado por UUID
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-23
- **Fase:** 6
- **Severidad:** Media
- **Sintoma:** `CardioDao.getCardioSessions()` devolvia sesiones ordenadas por `id DESC`, que no representa cronologia.
- **Causa raiz:** se uso un campo UUID como atajo de orden.
- **Solucion:** la query une `cardio_sessions` con `workout_sessions` y ordena por `workout_sessions.start_time DESC`.
- **Prevencion:** los historiales se ordenan por timestamps, nunca por identificadores aleatorios.
- **Fecha resolucion:** 2026-05-23

### BUG-009 - Ultimo set no navegaba automaticamente al resumen
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-23
- **Fase:** 5
- **Severidad:** Media
- **Sintoma:** completar el ultimo set no cerraba la sesion automaticamente; el usuario tenia que pulsar `Finalizar`.
- **Causa raiz:** `onSetCompleted` solo guardaba el set y arrancaba descanso, sin comprobar si toda la sesion estaba completada.
- **Solucion:** `ActiveWorkoutViewModel` completa la sesion al detectar todos los sets completados y expone `completedSessionId` para navegar al resumen.
- **Prevencion:** revisar flujos automaticos del spec, no solo botones manuales.
- **Fecha resolucion:** 2026-05-23

### BUG-008 - Rest timer ignoraba ajustes y no liberaba audio
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-23
- **Fase:** 5
- **Severidad:** Baja
- **Sintoma:** el descanso siempre vibraba y sonaba, aunque `app_settings` permite desactivar sonido/vibracion; `ToneGenerator` no se liberaba.
- **Causa raiz:** la UI del rest timer no tenia contrato de settings y creaba `ToneGenerator` inline.
- **Solucion:** anadido `WorkoutSettingsRepository`, lectura de `rest_sound_enabled`/`rest_vibration_enabled`, y liberacion de `ToneGenerator`.
- **Prevencion:** cualquier feature que ya tenga settings en schema debe leerlos desde dominio antes de usar defaults.
- **Fecha resolucion:** 2026-05-23

### BUG-007 - Orden de ejercicios durante sesion se perdia al editar sets
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-23
- **Fase:** 5
- **Severidad:** Media
- **Sintoma:** reordenar ejercicios en el bottom sheet se revertia al marcar o editar un set.
- **Causa raiz:** `reloadSession()` reconstruia la sesion desde Room usando el orden de la rutina original.
- **Solucion:** `ActiveWorkoutViewModel` conserva `exerciseOrder` y lo reaplica tras cada reload de sesion.
- **Prevencion:** test/revision de interacciones combinadas: reordenar + editar + recargar.
- **Fecha resolucion:** 2026-05-23

### BUG-006 - Fallo interno del foreground service podia dejar la UI sin aviso
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-23
- **Fase:** 5
- **Severidad:** Alta
- **Sintoma:** si `startForeground()` fallaba dentro de `WorkoutForegroundService`, el servicio se paraba y la pantalla no mostraba aviso.
- **Causa raiz:** el ViewModel solo capturaba excepciones de `startForegroundService()`, no fallos posteriores dentro del servicio.
- **Solucion:** `ActiveWorkout` pide `ACTIVITY_RECOGNITION` antes de arrancar el FGS; el servicio publica estado `failed=true` y el ViewModel lo traduce a mensaje de UI.
- **Prevencion:** revisar errores asincronos de servicios, no solo el punto de llamada.
- **Fecha resolucion:** 2026-05-23

---

### BUG-005 - Records personales duplicados dentro de la misma sesion
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-23
- **Fase:** 5
- **Severidad:** Media
- **Sintoma:** dos sets completados con el mismo peso, ambos por encima del maximo historico, podian contarse como dos records personales.
- **Causa raiz:** `CompleteWorkoutSessionUseCase` comparaba cada set solo contra el maximo previo a la sesion, no contra el maximo ya visto dentro de la sesion actual.
- **Solucion:** el cierre de sesion mantiene `maxSeenByExercise` y solo marca nuevo PR cuando el peso supera ese maximo acumulado.
- **Prevencion:** test unitario `complete session calculates duration volume completed sets and personal records`.
- **Fecha resolucion:** 2026-05-23

---

### BUG-004 - Seed data de ejercicios ignoraba locale EN
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-23
- **Fase:** 4
- **Severidad:** Media
- **Sintoma:** en locale ingles la biblioteca mostraba nombres seed en espanol aunque la DB tenia columnas `name_en`.
- **Causa raiz:** los repositorios de ejercicios/rutinas mapeaban siempre `nameEs`/`descriptionEs`.
- **Solucion:** `RoomExerciseRepository` y `RoomRoutineRepository` seleccionan `nameEn`/`descriptionEn` cuando el locale actual es `en`.
- **Prevencion:** revision de i18n en cada pantalla con seed data o contenido predefinido.
- **Fecha resolucion:** 2026-05-23

### BUG-003 - Reordenamiento de rutinas no tenia drag-and-drop real
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-23
- **Fase:** 4
- **Severidad:** Media
- **Sintoma:** el constructor de rutinas permitia reordenar solo con botones subir/bajar, pero el spec exige drag-and-drop.
- **Causa raiz:** se intento cerrar la fase con una alternativa funcional pero incompleta respecto al spec.
- **Solucion:** `DraftExerciseCard` acepta gesto vertical de drag para mover ejercicios y conserva botones accesibles como fallback.
- **Prevencion:** revision de spec bloqueante antes de cerrar cada fase; no rebajar requisitos funcionales en docs.
- **Fecha resolucion:** 2026-05-23

### BUG-002 - Edicion de rutinas podia convertirse en duplicado
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-23
- **Fase:** 4
- **Severidad:** Media
- **Sintoma:** la primera implementacion de Fase 4 creaba rutinas nuevas sin contrato claro de actualizacion por id; eso habria duplicado una rutina al editarla.
- **Causa raiz:** el use case generaba id por defecto antes de los argumentos de negocio y la UI todavia no tenia modo de edicion real.
- **Solucion:** `createOrUpdateRoutine` y `createOrUpdateCustomExercise` aceptan `id` explicito al final, la UI rellena formularios de edicion y los repositorios preservan `created_at`.
- **Prevencion:** tests unitarios de actualizacion por id en `RoutineUseCaseTest` y `ExerciseUseCaseTest`.
- **Fecha resolucion:** 2026-05-23

---

### BUG-001 - Opt-in de biometria del onboarding no persistia
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-23
- **Fase:** 3
- **Severidad:** Media
- **Sintoma:** activar biometria en el paso 8 del onboarding cambiaba solo el estado de UI; el valor no llegaba a `app_settings.biometrics_enabled`.
- **Causa raiz:** `OnboardingViewModel` persistia biometria durante el paso de contrasena, antes de que el usuario pudiera activar la opcion en el paso dedicado.
- **Solucion:** `finish()` llama a `LocalAuthUseCase.setBiometricUnlockEnabled()` con el valor final antes de marcar `onboarding_completed=true`.
- **Prevencion:** test unitario `finish persists biometric opt in after password step`.
- **Fecha resolucion:** 2026-05-23

---

*Atlas Peak - BUGS.md*
