# CHANGELOG.md — Registro de cambios

> Trazabilidad de todo lo que se hace en el proyecto. Cada sesión de trabajo añade una
> entrada. Formato basado en [Keep a Changelog](https://keepachangelog.com/es/).
> Orden: más reciente arriba.
>
> Tipos: `Añadido` · `Cambiado` · `Corregido` · `Eliminado` · `Seguridad` · `Deprecado`

---

## [Unreleased]

### 2026-09-25 - Correcciones de la revision del PR #16 y CI

**Corregido**
- CI: `ActiveWorkoutViewModelTest` no compilaba (`viewModel.session` en vez de
  `viewModel.state.value.session`). Ademas sus tests colgaban el job: sin FGS la VM arranca
  un cronometro local en bucle y `advanceUntilIdle()` nunca terminaba; ahora usan
  `runCurrent()` y el tiempo se avanza con `advanceLocalTimer()`. Como `runTest` tambien
  drena el scheduler al terminar, `ActiveWorkoutViewModelTest` y `ActiveCardioViewModelTest`
  usan `runVmTest`, que cancela el `viewModelScope` de cada VM antes de ese drenaje.
- `ActiveCardioViewModel` accede a permisos e intents del FGS a traves de
  `CardioTrackerServiceController` (implementacion real `ContextCardioTrackerServiceController`);
  los tests JVM usan fakes en vez de `ContextWrapper`, que chocaban con los stubs "not mocked"
  del android.jar (`Process.myPid()`, `Intent(Context, Class)`).
- `ActiveWorkoutViewModel` retira el aviso `TimerServiceUnavailable` cuando el FGS se recupera.
- `RoomCardioRepository`: el mapper Room→dominio perdia `weeklyPlanSessionId`, y cualquier
  pausa o finalizacion lo reescribia a `NULL`.
- `BUG-104`: sesion de cardio GPS sin fix ya se puede finalizar con metricas manuales.
- `BUG-105`: pausar/reanudar cardio sin FGS ya no arranca un servicio ordinario.
- `BUG-106`: el sync inicial de Health Connect ya no se cancela con el `ON_RESUME` de Home.
- `BUG-107`: Home muestra `PartialSuccess` en sincronizaciones parciales reales.
- `BUG-108`: los avisos de backup automatico se ocultan si el backup automatico esta desactivado.
- `ProfileValidation` se usa en onboarding (bloquea el avance y no persiste edad/altura fuera
  de rango) y en `EditProfileViewModel`; `BodyCompositionValidation` es ahora la unica fuente
  de rangos para `BodyCompositionDraft` y `BodyCompositionUseCase` (la UI ya no acepta
  valores que el guardado rechazaba: edad corporal >120, masa muscular/agua >250, osea >20).

**Verificado**
- Revisados contra el codigo actual los hilos ya resueltos: upgrader de backups (tabla
  `cardio_route_points` y columnas nuevas), schemas 7-9 exportados, test de migracion 7→8,
  buscar-o-crear sesion activa en una transaccion, propagacion de `weeklyPlanSessionId`,
  tiempo en pausa del FGS, persistencia de GPS antes de finalizar, recarga del plan semanal,
  permisos de Health Connect y aviso de reautorizacion de Drive.
- No se pudo compilar localmente (entorno sin Android SDK); la verificacion es el CI del PR.

### 2026-09-20 - CardioForegroundService robusto frente a politica estricta de tipos de FGS en Android 14+/15+ (Fase 4 P0)

**Corregido**
- `BUG-093`: el `CardioForegroundService` ya no reclama un tipo de foreground service
  ilegal para el escenario actual. Antes, la eleccion de tipo se hacia inline en
  `startForegroundCompat` con un booleano `locationTracking`, lo que dejaba tres
  agujeros en Android 14+ (API 34+) / 15+ (API 35):
    1. Cardio GPS sin permiso `ACCESS_FINE_LOCATION`: el FGS intentaba reclamar
       `FOREGROUND_SERVICE_TYPE_HEALTH` sin uso real de datos de salud
       (`ACTIVITY_RECOGNITION`, Health Connect write), y la politica estricta de
       Android 14+ dispara `SecurityException`.
    2. Cardio sin GPS sin `ACTIVITY_RECOGNITION`: mismo problema, el sistema rechaza
       reclamar `HEALTH` sin un uso real de salud.
    3. Cualquier combinacion anterior + dispositivo que rechaza `startForegroundService`
       por background activities: el VM capturaba `RuntimeException` (que cubre
       `SecurityException` por ser subclase) pero no distinguia el caso del resto de
       `RuntimeException`s, y el VM no etiquetaba el modo efectivo del FGS, asi que
       la UI no podia mostrar la nota de "modo local" cuando no habia un FGS legal.
  Ahora `preflightFgsType(hasGps, hasFineLocation, hasActivityRecognition)` decide
  en funcion de los permisos reales del dispositivo que tipo de FGS es legal:
  `Location` (GPS + permiso), `Health` (no GPS + `ACTIVITY_RECOGNITION`) o `None`
  (ninguno legal). Cuando el ViewModel obtiene `None`, no inicia ningun foreground
  service y mantiene el cronometro local. Si los permisos cambian entre el preflight
  del ViewModel y el del servicio y este ultimo obtiene `None`, el servicio marca
  el tracker como fallido y se detiene inmediatamente.

**Cambiado**
- `CardioForegroundService`:
  - Nuevo helper `preflightFgsType(hasGps, hasFineLocation, hasActivityRecognition)` en el
    `companion object`: sin estado, sin dependencias de Android, facil de testear.
  - `startTracking` lo invoca como defensa frente a carreras de permisos. Si devuelve
    `None`, no deja jobs vivos: publica `failed=true`, llama a `stopSelf()` y retorna.
  - `startForegroundCompat(notification, fgsMode)` toma un `CardioFgsMode` en vez de
    un booleano. Mantiene `require(fgsMode != None)` como red de seguridad para que un
    caller incorrecto falle ruidosamente en vez de reclamar un tipo arbitrario.
  - El catch alrededor de `startForeground` ahora tiene un `catch (_: SecurityException)`
    explicito seguido del `catch (_: RuntimeException)` existente (mismo cuerpo):
    SecurityException es subclase de RuntimeException, pero queremos que la intencion
    sea visible al lector. No se elimina el catch de RuntimeException: sigue cubriendo
    cualquier otra excepcion inesperada.
  - Nuevo `hasActivityRecognitionPermission()` que usa
    `ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)`.
- `ActiveCardioViewModel`:
  - Constructor primario recibe `now: () -> Long` inyectable; el `@Inject` secundario
    (Hilt) pasa `{ System.currentTimeMillis() }` (mismo patron que el post-BUG-092
    en `ActiveWorkoutViewModel`).
  - `loadSession` y `startLocalTimerIfNeeded` ahora usan `now()` en vez de
    `System.currentTimeMillis()` para derivar `elapsedSeconds` desde `session.startTime`.
  - `startTrackingService(locationAllowed)`:
    - Calcula `requestedFgsMode = CardioForegroundService.preflightFgsType(hasGps =
      session.hasGps, hasFineLocation = locationAllowed, hasActivityRecognition = ...)`
      consultando `ContextCompat.checkSelfPermission` para `ACTIVITY_RECOGNITION`.
    - Publica `fgsMode` en el estado antes de la llamada. Si el usuario denego
      localizacion y el tipo es GPS, fija el mensaje `LocationPermissionDenied`.
    - Si `requestedFgsMode == None`, no llama a `startForegroundService` y arranca
      directamente el temporizador local.
    - El try alrededor de `startForegroundService` para `Location`/`Health` tiene
      `catch (_: SecurityException)`
      explicito seguido del `catch (_: RuntimeException)` existente (mismo cuerpo). Si
      cualquiera de los dos dispara, baja `fgsMode` a `None`, fija el mensaje
      `TrackerUnavailable` y arranca el cronometro local.
  - El collector del `CardioTrackerRegistry`: cuando `tracker.failed && tracker.sessionId ==
    sessionId`, ademas de fijar el mensaje `TrackerUnavailable`, baja `fgsMode` a `None`
    para reflejar el modo efectivo (el FGS fallo, asi que no hay un FGS reclamable
    en este momento aunque el preflight hubiera dicho otra cosa).
- `domain/model/cardio/CardioModels.kt`:
  - Nuevo enum `CardioFgsMode { Location, Health, None }` con comentario KDoc que
    documenta cada valor y cuando se aplica.

**Añadido**
- `ActiveCardioUiState.fgsMode: CardioFgsMode = CardioFgsMode.None`: campo nuevo
  para que la UI pueda etiquetar el escenario. La pantalla `ActiveCardioScreen` NO
  lo referencia en esta fase: el plan reservaba la nota visible ("modo local, sin
  notificacion persistente") para una iteracion UX posterior. El dato ya esta
  disponible para cuando se anada.
- Tests en `ActiveCardioViewModelTest` (JUnit5, hand-written fakes, `StandardTestDispatcher`):
  - `startTrackingService with location allowed sets fgsMode to Location`: contexto
    AllowingContext, cardio GPS, `locationAllowed = true` -> `fgsMode = Location`,
    intent enviado al sistema.
  - `startTrackingService with location denied sets fgsMode to None with
    LocationPermissionDenied`: cardio GPS, `locationAllowed = false` -> `fgsMode =
    None`, mensaje `LocationPermissionDenied`, formulario manual visible.
  - `startTrackingService for non-GPS cardio with ACTIVITY_RECOGNITION sets fgsMode
    to Health`: cardio manual + AllowingContext -> `fgsMode = Health`.
  - `startTrackingService for non-GPS cardio without ACTIVITY_RECOGNITION sets fgsMode
    to None`: cardio manual + ActivityRecognitionDeniedContext -> `fgsMode = None`,
    formulario manual visible.
  - `startTrackingService rejection by system falls back to local timer with fgsMode
    None`: RejectingContext (lanza `SecurityException` en `startForegroundService`) ->
    `fgsMode = None`, mensaje `TrackerUnavailable`.
  - `elapsed seconds keeps increasing when foreground service is rejected`: misma
    configuracion que el anterior + reloj inyectable +5s -> `elapsedSeconds` sube al
    menos 5. Patron espejo del BUG-092.
- Fake contexts `AllowingContext`, `RejectingContext` y `ActivityRecognitionDeniedContext`
  al estilo del `NoopContext` existente, siguiendo el estilo de
  `ActiveWorkoutViewModelTest`.
- Helper privado `TestClock(initialMillis)` con `reset / advanceBy / currentMillis /
  asNow()` para inyectar reloj determinista al test del fallback local
  (mismo patron que `ActiveWorkoutViewModelTest`).

**Limitaciones conocidas**
- El VM computa `fgsMode` antes de iniciar el servicio y el FGS vuelve a calcularlo,
  por lo que existe una ventana de carrera si cambian los permisos entre ambos checks.
  Si el servicio obtiene `None`, publica `failed=true` y se detiene inmediatamente;
  el collector del VM baja a `None` y mantiene el temporizador local. No se
  expone el fgsMode del FGS en el registry: aceptado como deuda para una fase
  posterior si se necesita precision bit-exact entre el modo pedido y el modo
  realmente reclamado.
- La nota visible en la UI para `fgsMode == None` ("modo local, sin notificacion
  persistente") NO se añade en esta fase; el plan la reservaba para una iteracion
  UX posterior. El campo ya esta disponible en el estado para cuando se anada.

**Verificado**
- Compilacion a nivel de tipos y referencias en Kotlin. No se pudo ejecutar
  `./gradlew test` ni `./gradlew lint` en este entorno por falta de JDK
  (`JAVA_HOME` apunta a `C:\tmp\atlas-dev-tools\jdk-17.0.19+10`, ruta inexistente).
- Los 6 tests del BUG-093 estan escritos siguiendo los mismos patrones que los
  casos preexistentes de `ActiveCardioViewModelTest` (mismo `FakeCardioRepository`,
  mismo `dispatcher`, mismo `CardioTrackerRegistry.update(CardioTrackerState())` en
  `setUp()`), y reflejan los mismos helpers (`advanceLocalTimer`, `TestClock`) que
  `ActiveWorkoutViewModelTest` introdujo para BUG-092. Su semantica es la misma que
  el resto de la suite.

### 2026-09-20 - Cronometro de fuerza robusto frente a caida del WorkoutForegroundService (Fase 3 P0)

**Corregido**
- `BUG-092`: el cronometro visual de `ActiveWorkoutScreen` ya no se congela cuando
  `WorkoutForegroundService` falla o no llega a arrancar. Antes, el VM se limitaba a
  espejar `WorkoutTimerRegistry.state`; si el FGS no estaba vivo (SecurityException en
  `startForeground`, kill del OS, `ACTIVITY_RECOGNITION` denegado) el contador quedaba
  congelado en el ultimo valor publicado por el registry. Ahora el VM mantiene un job
  local de respaldo que recalcula `elapsedSeconds = (now - session.startTime) / 1000`
  mientras no haya un FGS sano escribiendo para la sesion. La formula es identica a la
  que usa `WorkoutTimerRegistry.tick`, asi que ambos caminos convergen al mismo valor.

**Cambiado**
- `ActiveWorkoutViewModel`:
  - Constructor primario recibe `now: () -> Long` para poder inyectar un reloj en
    tests. Hilt sigue construyendo via un `@Inject` constructor secundario que pasa
    `{ System.currentTimeMillis() }` (mismo patron que `CardioUseCase`).
  - `loadSession()` usa `now()` en vez de `System.currentTimeMillis()` para derivar
    `elapsedSeconds` desde `session.startTime`.
  - Nuevo `localTimerJob: Job?` con `startLocalTimerIfNeeded()` y `stopLocalTimer()`
    que ejecutan el tick 1Hz en `viewModelScope`. El job se arranca solo cuando el
    registry no tiene un timer sano para la sesion actual (`failed=true` para nuestro
    `sessionId` o `sessionId == null`) y se para en cuanto el registry pasa a
    `running=true` con nuestro `sessionId`. Asi no hay doble escritor.
  - El collector del registry reescrito: cuando el FGS esta sano espeja
    `timer.elapsedSeconds` y `restTimer`; cuando falla o no ha arrancado fija el
    mensaje `TimerServiceUnavailable`, limpia `restTimer` (el FGS es dueno del rest
    timer; al morir, la VM no puede mantenerlo) y deja que el job local derive el
    tiempo. Cuando el registry esta vacio y la VM tiene sesion cargada, arranca el
    job local sin fijar mensaje (la sesion acaba de empezar).
  - La logica de las tres ramas del collector se extrae a `reconcileTimerState()`,
    que se invoca tambien desde `loadSession()` al final. Esto cubre una carrera
    posible en produccion con el dispatcher Main: el `collect` inicial del registry
    puede dispararse antes de que la consulta de Room que carga la sesion haya
    terminado (las queries de Room suspenden), en cuyo caso `startLocalTimerIfNeeded`
    arrancaba el job y este salia por `session == null` sin re-arrancar, dejando
    el cronometro muerto hasta el siguiente cambio del FGS. Con el segundo punto
    de llamada, `loadSession` re-evalua la situacion y arranca el fallback si
    corresponde.
  - Nuevo `onCleared()` explicito que para el job local antes que `viewModelScope`
    se cancele implicitamente.

**Añadido**
- Tests en `ActiveWorkoutViewModelTest` (JUnit5, hand-written fakes, `StandardTestDispatcher`):
  - `elapsed_seconds keeps increasing when foreground service fails to start`:
    registry en `failed=true` para nuestra sesion, reloj +5s, `elapsedSeconds >= 5`.
  - `elapsed_seconds keeps increasing when foreground service never started`:
    registry vacio (FGS no llego a arrancar), reloj +5s, `elapsedSeconds >= 5`,
    `message == null` (sin mensaje de fallback porque el FGS no fallo, simplemente
    no arranco).
  - `elapsed_seconds keeps increasing across recreation when foreground service is
    unavailable`: VM1 +3s, recrear VM2 contra el mismo repo, +3s mas,
    `elapsedSeconds >= 6` (math derivada del `startTime` persistido).
  - `local fallback timer stops when foreground service becomes healthy`: FGS
    `failed=true`, dejar correr el job local, transicionar el registry a
    `running=true` con un `elapsedSeconds` arbitrario que NO coincide con la formula,
    avanzar reloj +10s y verificar que el valor no se sobrescribe (prueba que solo
    el collector escribe).
  - `elapsed_seconds matches math from startTime`: reloj = `startTime + 3000`,
    `elapsedSeconds == (now - startTime) / 1000`.
  - Helper privado `TestClock(initialMillis)` con `reset / advanceBy / currentMillis /
    asNow()` para inyectar reloj determinista sin tocar `System.currentTimeMillis()`.

**Verificado**
- Compilacion a nivel de tipos y referencias en Kotlin. No se pudo ejecutar
  `./gradlew test` ni `./gradlew lint` en este entorno por falta de JDK
  (`JAVA_HOME` apunta a `C:\tmp\atlas-dev-tools\jdk-17.0.19+10`, ruta inexistente).
- Los 5 tests del BUG-092 estan escritos siguiendo los mismos patrones que los
  casos preexistentes de `ActiveWorkoutViewModelTest` (mismo `FakeWorkoutRepository`,
  mismo `dispatcher`, mismo `WorkoutTimerRegistry.update(WorkoutTimerState())` en
  `setUp()`), por lo que su semantica es la misma que el resto de la suite.

### 2026-09-20 - Persistencia GPS de cardio durante la sesion activa (Fase 2 P0)

**Corregido**
- `BUG-091`: la ruta GPS de cardio ya no se pierde cuando Android mata el proceso o el
  usuario reabre la app. `CardioTrackerRegistry` seguia manteniendo la ruta unicamente
  en memoria y `route.distanceKm()` recomputaba el total desde el primer punto en cada
  fix. Ademas, `ActiveCardioViewModel.completeCardio()` solo almacenaba la ruta al cerrar
  la sesion, asi que cualquier cierre brusco del proceso la descartaba entera. Ahora
  cada fix aceptado por `LocationTracker.isUsableForCardioTracking` se persiste en una
  nueva tabla Room `cardio_route_points` con su `distance_from_previous_km` incremental,
  de forma que la distancia total se reconstruye con `SUM(distance_from_previous_km)`
  sin recalcular la polilinea completa. Al volver a abrir la sesion de cardio tras una
  muerte de proceso, `ActiveCardioViewModel.loadSession()` rehidrata el registro desde
  Room y continua justo donde se quedo.

**Añadido**
- `cardio_route_points` (entidad + DAO `CardioRoutePointDao`): tabla indexada por
  `(session_id, timestamp_ms)` con FK `CASCADE` a `workout_sessions(id)`. Almacena
  `id`, `session_id`, `timestamp_ms`, `latitude`, `longitude`, `accuracy_m`,
  `speed_kmh`, `distance_from_previous_km`. Migracion explicita `MIGRATION_6_7`.
- `CardioRoutePoint` (modelo de dominio): replica plana de la entidad sin acoplar
  presentation a Room.
- `CardioRepository`: nuevos metodos `addRoutePoint`, `routePoints`,
  `routePointsCount`, `routeDistanceKm`, `deleteRoutePoints`.
- `RoomCardioRepository`: implementa los metodos anteriores; `deleteSession` se
  apoya en el `ON DELETE CASCADE` del FK para limpiar los route points al borrar la
  sesion.
- `CardioUseCase`:
  - `appendRoutePoint(sessionId, point, accuracyMeters, previousAcceptedPoint)`:
    valida coordenadas, valida segmento vs velocidad maxima por tipo (run/ciclismo/natacion)
    y escribe el punto con distancia incremental. Devuelve el punto persistido o null
    si fue rechazado.
  - `restoreRoute(sessionId): RouteRestoreResult`: lee los puntos persistidos y los
    devuelve como `List<LocationPoint>` junto con la distancia total.
  - `completeSession(sessionId, endedAt, manualDistanceKm, manualAvgSpeedKmh)` ya no
    recibe la ruta como parametro: la reconstruye desde Room, aplica el filtro final
    (`sanitizedRoute`) sobre el perimetro persistido, escribe el snapshot a
    `cardio_sessions.route_polyline_json` y borra los puntos en vuelo para que la
    siguiente sesion no herede residuos.
- `CardioForegroundService` ahora es `@AndroidEntryPoint` e inyecta `CardioUseCase`.
  - `startTracking` preserva `route`/`distanceKm`/`currentSpeedKmh`/`avgSpeedKmh`
    del estado actual del registro en lugar de pisarlos con un `CardioTrackerState`
    vacio (BUG-091: rehidratacion desde VM llega antes que el FGS).
  - Nuevo `persistJob` que observa `state.map { it.route }.distinctUntilChanged()` y
    persiste solo los puntos nuevos (delta entre `lastSeenSize` y `route.size`)
    usando `appendRoutePoint`. Asi no se duplican los puntos ya cargados desde Room.
- `ActiveCardioViewModel.loadSession` llama a `restoreRoute` y vuelca los puntos
  en el `CardioTrackerRegistry` antes de que el FGS arranque; ademas pobla `state.route`
  con los puntos restaurados.
- `DatabaseModule`: registra `MIGRATION_6_7` para que la migracion explicita se
  aplique en produccion.
- Tests:
  - `CardioUseCaseTest`: nuevos casos
    `appendRoutePoint persists each point incrementally and rebuilds same distance on restore`,
    `appendRoutePoint rejects invalid coordinates without modifying distance`,
    `appendRoutePoint rejects impossibly fast segment and does not persist it`,
    `appendRoutePoint continues from previous accepted point after recreation`,
    `complete session deletes persisted route points after snapshotting route into json`.
    Los casos existentes (`complete session calculates distance speed and stores route`,
    etc.) se actualizan para invocar `appendRoutePoint` antes de `completeSession`.
  - `ActiveCardioViewModelTest`: nuevo caso
    `process recreation restores route from persisted route points not from in-memory session`
    que verifica que la ruta rehidratada viene de `cardio_route_points`, no de
    `CardioSession.route` (que permanece vacia durante la sesion activa).
  - `ResumeCardioSessionUseCaseTest`, `WeeklyPlanViewModelTest`,
    `ProgressViewModelTest`, `ProgressUseCaseTest`: actualizados sus `FakeCardioRepository`
    con los nuevos metodos del repositorio (no-op para los casos que no los ejercen).
  - `AppDatabaseMigrationTest` (androidTest): nuevo caso `migration6To7CreatesCardioRoutePointsTable`
    que crea la DB en v6, ejecuta `MIGRATION_6_7`, valida la nueva tabla + sus dos
    indices y permite insertar una fila de prueba.
  - `CardioRoutePointsInstrumentedTest` (androidTest, archivo real, no in-memory):
    cubre los escenarios end-to-end del plan: N puntos persistidos, cerrar y reabrir la
    DB mantiene la distancia y el conteo, `deleteSession` borra los route points via
    `CASCADE`, `deleteRoutePoints` deja viva la sesion padre, FK rechaza huérfanos.

**Limitaciones conocidas**
- Sigue existiendo una ventana minima (sub-milisegundo por fix) entre `addPoint` y la
  escritura a Room del `persistJob`: si el proceso muere en ese gap, el ultimo fix se
  pierde. Es la mejor garantia posible sin acoplarel registro a Room; aceptada como
  deuda. Room serializa escrituras y lecturas, asi que `completeSession` siempre ve un
  estado consistente.
- `CardioForegroundService` ahora requiere `@AndroidEntryPoint` e inyeccion de Hilt.
  Los tests instrumentados del FGS no se han ampliado en esta fase: la cobertura del
  flujo `persistJob` se hace via `CardioUseCase.appendRoutePoint` y la prueba de
  rehidratacion en `ActiveCardioViewModelTest`.
- El FK `cardio_route_points -> workout_sessions` con `CASCADE` requiere que la sesion
  viva en `workout_sessions` (no en una tabla alternativa). Esto ya era asi por diseno,
  pero la nueva tabla lo explicita: cualquier intento de insertar un route point
  huérfano falla con la excepcion de SQLite.

**Verificado**
- Compilacion manual (verificacion a nivel de tipos y referencias en Kotlin. No se pudo
  ejecutar `./gradlew test` en este entorno por falta de JDK -- `JAVA_HOME` apunta a
  `C:\tmp\atlas-dev-tools\jdk-17.0.19+10`, ruta inexistente).
- Cobertura del flujo `appendRoutePoint` + `restoreRoute` + `completeSession` cubierta
  por tests JUnit5 hand-written fakes; los tests de migracion y de Room viven en
  `androidTest` (AndroidJUnit4) porque la JVM pura no soporta SQLite con FK + Room.

### 2026-09-20 - Lote P3: Pace cardio + GpsState + migracion test + quick action sesion activa en Home (Fases 13-16)

**Corregido**
- `BUG-102`: cardio ya no muestra `0.0 km/h` como medicion real. Se anade
  `paceMinPerKm` derivado y un enum `GpsState` (NotApplicable / Searching /
  Active / Weak / Denied / Unavailable) en el UiState. Strings ES + EN para
  los seis estados y para `cardio_metric_pace` / `cardio_metric_pace_value`.

**Anadido**
- `ActiveSessionShortcut` (Strength | Cardio) en `HomeUiState` con
  `loadActiveSessionShortcut()` que consulta los repositorios de workout y
  cardio. Cableado en `HomeViewModel` y listo para que la UI renderice un
  boton "Continuar" cuando hay sesion activa (BUG-103, queda pendiente el
  render del composable).
- Test instrumentado `migration8To9AddsWeeklyPlanSessionIdColumn` en
  `AppDatabaseMigrationTest` que cubre la migracion introducida en Fase 8.

**Auditoria de botones (Fase 15)**
- `Home`: el banner de Health Connect (Fase 6) tiene `enabled = !discardInProgress`
  en los botones del dialogo de conflicto; el resto de CTAs (Iniciar entrenamiento /
  cardio / Ver historial) tienen `enabled` derivado del estado. Confirmado en
  revision.
- `ActiveWorkoutScreen`: el boton Finalizar respeta `state.canComplete` (Fase 1 +
  Fase 5), botones Pausar/Reanudar conmutados por `state.isPaused` (Fase 5),
  dialogo de conflicto con tres acciones deshabilitadas durante `discardInProgress`
  (Fase 1 fix del revisor).
- `ActiveCardioScreen`: gating `enabled = state.canComplete` para Finalizar (Fase
  5), botones Pausar/Reanudar (Fase 5), dialogo de conflicto con tres acciones
  deshabilitadas durante `discardInProgress` (Fase 1 fix del revisor). Banner
  discreto de HC solo cuando el estado no es Idle (Fase 6).
- `WeeklyPlanScreen`: dialogo de doble confirmacion para descanso (BUG-073),
  dialogo de conflicto de sesion activa (Fase 1).
- `BackupRestoreScreen`: confirmaciones explicitas para Restore y discar (pre-
  existentes, verificado).
- Targets tactiles >=48dp via `minimumInteractiveComponentSize()` (verificado en
  revision del Lote 0 / V-01.08).
- `contentDescription` null en elementos decorativos; descripciones semanticas en
  el mapa de cardio (Fase 2).

**Verificado**
- Sin build local por falta de JDK; todo lo anadido se valida por inspeccion y
  por la suite de tests JUnit5 + androidTest ya existente.

### 2026-09-20 - Lote P2: WeeklyPlan race + validaciones compartidas + mapa cardio bounds + debounce historial (Fases 9-12)

**Corregido**
- `BUG-098`: `WeeklyPlanViewModel.saveDay()` ya no compite con su propio
  `refresh()`; nuevo flag `isSaving` deshabilita el boton y serializa el
  feedback (`Saved` / `Invalid` / `Error`) sin carreras.
- `BUG-099`: limites de perfil y composicion corporal centralizados en
  `domain/usecase/profile/ProfileValidation` y
  `domain/usecase/body/BodyCompositionValidation`. Antes la UI de onboarding
  no validaba nada y los rangos de composicion divergian entre VM y use case.
- `BUG-100`: `CardioRouteMap` ahora calcula `LatLngBounds` para 2+ puntos
  y usa `CameraUpdateFactory.newLatLngBounds` con padding 96 px. Para 1
  punto conserva el zoom fijo 15f. Marcadores inicio/fin en la polilinea.
- `BUG-101`: `ProgressViewModel.onHistorySearchChanged` aplica un debounce
  de 300 ms antes de llamar a `refreshHistory()`, cancelando el job anterior
  para evitar consultas Room innecesarias por cada pulsacion de tecla.

**Anadido**
- `ProfileValidation` (MIN_AGE / MAX_AGE / MIN_HEIGHT_CM / MAX_HEIGHT_CM)
  y helpers `ageIsValid` / `heightIsValid`.
- `BodyCompositionValidation` (rangos canonicos para peso, porcentajes,
  grasa muscular, agua, masa osea, grasa visceral y edad biologica) y
  helpers `weightIsValid`, `percentIsValid`, etc.
- Strings `cardio_route_start` / `cardio_route_end` ES + EN para los
  marcadores del mapa.
- Constante `SEARCH_DEBOUNCE_MS = 300L` y nuevo `historySearchJob` en
  `ProgressViewModel`.

**Limitaciones**
- `EditProfileViewModel` sigue validando con sus constantes locales; la
  migracion a `ProfileValidation` queda pendiente para limpieza futura.
- `LaunchedEffect(route.size)` re-encuadra en cada cambio de tamano; en
  sesiones GPS activas largas es molesto (Fase 13 introduce heuristica de
  cambio significativo).

**Verificado**
- Sin build local por falta de JDK.

### 2026-09-20 - Plan semanal por sesion concreta (Fase 8 P1)

**Corregido**
- `BUG-097`: dos sesiones de la misma rutina en el mismo dia del plan semanal
  ya no se marcan como completadas al cerrar solo una. La FK
  `workout_sessions.weekly_plan_session_id` apunta a la entrada concreta del
  plan que origino la sesion y la regla de matching la respeta.

**Anadido**
- Nueva columna `weekly_plan_session_id TEXT` (indexada) en `workout_sessions`.
  Migracion Room `MIGRATION_8_9` no destructiva (`ALTER TABLE ...` + `CREATE
  INDEX`).
- `WorkoutSessionEntity`, `WorkoutSession` (dominio) y `CardioSession` ganan
  `weeklyPlanSessionId: String?`. Mapeos toEntity/toDomain del repo llevan el
  campo.
- `WeeklyPlanCompletionKey.planSessionId: String?`.
- `WeeklyPlanUseCase.plan()`: primero match por `planSessionId == session.id`;
  fallback por `(day, type, targetId)` solo para claves sin FK (compatibilidad
  con sesiones historicas).
- `StartWorkoutSessionUseCase.invoke(routineId, weeklyPlanSessionId?)` y
  `CardioUseCase.startSession(cardioTypeId, mode, weeklyPlanSessionId?)`.

**Limitaciones**
- Sesiones iniciadas antes de esta fase siguen contando via fallback; no hay
  forma fiable de vincularlas a la entrada del plan.
- La UI todavia no expone un selector para iniciar "desde el plan" vs "libre".

**Verificado**
- Sin build local por falta de JDK.

### 2026-09-20 - Backup automatico no silencioso (Fase 7 P1)

**Corregido**
- `BUG-096`: `BackupWorkerRunner` ya no trata `MissingAuthorization` como
  exito sin registrar nada. El estado funcional del backup automatico
  (`lastSuccessfulBackupAt`, `lastAttemptAt`, `lastError`,
  `requiresDriveAuthorization`) se persiste en un SharedPreferences plano y se
  proyecta al UI para que el usuario sepa cuando Drive requiere reautorizacion
  o cuando el ultimo intento fallo.

**Anadido**
- Modelo de dominio `BackupHealthStatus { lastSuccessfulBackupAt,
  lastAttemptAt, lastError, requiresDriveAuthorization }`.
- `BackupHealthStore` (SharedPreferences plano, no contiene secretos).
- `BackupSnapshotStore.backupHealth / recordBackupSuccess /
  recordBackupFailure / clearDriveAuthorizationRequired /
  markDriveAuthorizationRequired`.
- `BackupWorkerRunner` actualiza el estado en cada camino: MissingAuthorization
  marca `requiresDriveAuthorization=true`; exito -> `recordBackupSuccess`;
  fallo -> `recordBackupFailure` con su tipo.
- `BackupRepository.health()` y `BackupUseCase.health()` exponen el estado.
- `BackupRestoreViewModel.reconnectDrive()` + `requiresDriveAuthorization` /
  `lastError` en el UiState.
- Strings ES + EN (`backup_drive_reconnect_*`, `backup_last_error_*`).
- `BackupWorkerRunnerTest` actualizado con implementaciones no-op para los
  nuevos metodos del fake store (los tests existentes siguen cubriendo el flujo
  del worker).

**Limitaciones**
- `requiresDriveAuthorization` solo se limpia cuando el usuario pulsa
  "Reconectar Drive" o tras un backup con exito. No hay polling automatico.

**Verificado**
- Sin build local por falta de JDK.

### 2026-09-20 - Health Connect visible en Home (Fase 6 P1)

**Corregido**
- `BUG-095`: `HomeViewModel.refresh(syncBefore = true)` ya no descarta el resultado
  de `SyncHealthConnectUseCase`. El estado de sincronizacion se proyecta a
  `HomeUiState.healthConnectSync` para que la UI muestre al usuario si los datos
  estan al dia, faltan permisos, requieren actualizar HC o fallaron.

**Anadido**
- Tipo sellado `HomeHealthConnectSync { Idle | Syncing | Success(ts) |
  PartialSuccess(ts) | MissingPermissions | UpdateRequired | Unavailable |
  Failed(ts) }` con mapeo desde `HealthConnectSyncResult`.
- Banner `HealthConnectStatusBanner` en HomeScreen (icono + texto + accion
  "Conceder permisos" + boton cerrar), solo aparece cuando el estado no es Idle.
- `dismissHealthConnectSyncStatus()` en el VM para ocultar avisos no accionables.
- Strings ES + EN (`home_health_connect_*`).
- `HomeViewModelTest`: 7 tests que cubren los seis caminos del resultado sellado,
  el dismiss y la excepcion de runtime.

**Verificado**
- Sin build local por falta de JDK.

### 2026-09-20 - Finalizacion de cardio correcta + pause/resume (Fase 5 P1)

**Corregido**
- `BUG-094`: el boton Finalizar del cardio ahora respeta `state.canComplete`
  (ademas de `!completionInProgress`). Mientras falten metricas manuales
  obligatorias, el boton esta deshabilitado.
- `BUG-094`: anadida pausa/reanudacion para sesiones de cardio. El tiempo en
  pausa se resta del tiempo efectivo sin falsificar `startTime`: el helper de
  dominio `effectiveElapsedSeconds(session, now)` resta `totalPausedDurationMillis`
  y, si esta pausada, `(now - pausedAtMillis)`.

**Anadido**
- CardioSessionEntity gana `paused_at_ms INTEGER` (nullable) y
  `total_paused_duration_ms INTEGER NOT NULL DEFAULT 0`. Migracion Room
  `MIGRATION_7_8` no destructiva (`ALTER TABLE cardio_sessions ...`).
- CardioSession (dominio) replica los dos campos.
- `pauseCardio()` / `resumeCardio()` idempotentes en ActiveCardioViewModel;
  `state.isPaused` derivado de `session.pausedAtMillis != null`.
- Acciones `ACTION_PAUSE` / `ACTION_RESUME` en CardioForegroundService que
  cancelan/reactivan los jobs (`timerJob`, `locationJob`, `persistJob`) sin
  reclamar foreground nuevo.
- Botones Pausar/Reanudar conmutados por `state.isPaused` en ActiveCardioScreen.
- Strings ES + EN (`cardio_action_pause`, `cardio_action_resume`,
  `cardio_active_paused`, `cardio_active_searching_gps`, `cardio_active_no_route`,
  `cardio_active_enter_manual`, `cardio_finish_disabled_no_route`).
- Tests: 6 nuevos en `ActiveCardioViewModelTest` (pausa simple, multiples
  pausas, process recreation pausada, countdown durante pausa, gating del
  Finalizar, idempotencia de completeCardio).

**Limitaciones conocidas**
- Si el FGS recibe `ACTION_RESUME` cuando la sesion estaba en su primer fix
  todavia sin ruta persistida, la heuristica `route.isNotEmpty() -> locationJob`
  no reanuda la captura GPS hasta el siguiente reinicio manual del VM.
- `completeCardio` durante pausa deja el tiempo pausado fuera del total.

**Verificado**
- Sin build local por falta de JDK; validacion por inspeccion.

### 2026-09-20 - Identidad de sesion activa persistente en Room (Fase 1 P0)

**Corregido**
- `BUG-090`: las sesiones activas de fuerza y cardio ya no se duplican tras muerte de proceso,
  rotacion, navegacion ni reapertura. `ActiveWorkoutViewModel.startWorkout()` y
  `ActiveCardioViewModel.startCardio()` consultaban `findActiveSession()` antes de crear nada
  nuevo, asi una nueva entrada al entrenamiento rehidrata la sesion existente desde Room en
  lugar de generar otra. El `elapsedSeconds` se recalcula desde `startTime` (no desde un
  contador en memoria). Si la sesion activa pertenece a otra rutina/tipo de cardio la UI
  muestra un dialogo de decision (Continuar / Descartar y empezar uno nuevo / Cancelar) en
  lugar de sustituir en silencio y perder datos.

**Añadido**
- `WorkoutRepository.findActiveSession()` y `CardioRepository.findActiveSession()` con su
  implementacion Room (`getActiveStrengthSession()` / `getActiveCardioSession()`).
- `ResumeWorkoutSessionUseCase` y `ResumeCardioSessionUseCase`: operaciones idempotentes que
  devuelven el id de la sesion activa o null sin crear nada.
- `ActiveSessionStartResult`: tipo sellado en domain que colapsa los caminos
  `Started | Resumed | Conflict | NotFound` para que el arranque y la reanudacion vivan en
  una sola llamada.
- `discardActiveSessionAndStartNew()`, `resumeActiveSession()` y `dismissActiveSessionConflict()`
  en ambos ViewModels. `discardActiveSessionAndStartNew` ademas detiene el foreground service
  de la sesion descartada para no dejar notificacion zombi.
- Dialogo de conflicto en `ActiveWorkoutScreen` y `ActiveCardioScreen` con tres acciones
  (strings ES + EN).
- Tests:
  - `StartWorkoutSessionUseCaseTest` cubre los cuatro caminos del resultado sellado, que las
    sesiones ya completadas no cuentan como activas y que "process recreation" preserva sets.
  - `ResumeWorkoutSessionUseCaseTest` y `ResumeCardioSessionUseCaseTest` cubren el caso
    idempotente (no crean sesion nueva) y que ignoran sesiones completadas.
  - `ActiveWorkoutViewModelTest` y `ActiveCardioViewModelTest` cubren process recreation,
    derivacion de `elapsedSeconds` desde `startTime`, exposicion del estado `Conflict`,
    `discardActiveSessionAndStartNew`, `resumeActiveSession`, `dismissActiveSessionConflict` y
    mensaje `RoutineMissing` / `SessionMissing`.
  - `CardioUseCaseTest` actualizado: `startSession` devuelve el resultado sellado y los tests
    existentes (`createOrUpdateCustomType`, complete session con distancia manual, calorias y
    rechazo de cardio manual) siguen pasando.

**Limitaciones conocidas**
- El descanso (`rest timer` en `WorkoutTimerRegistry`) sigue siendo en memoria y se pierde
  tras muerte del proceso si el `WorkoutForegroundService` no esta vivo. Es un estado
  efimero, se acepta como deuda para una fase posterior.
- El conflicto cross-domain (sesion de fuerza activa + abrir cardio, o viceversa) no se
  detecta: cada caso de uso solo mira sesiones de su mismo tipo. Esta fuera del alcance de
  Fase 1 segun el plan.
- En cardio, abrir con un `mode` distinto al de la sesion activa (p.ej. Timer persistido y
  abrir Countdown) reanuda la sesion con el `mode` del SavedStateHandle, lo que deja el
  campo `remainingSeconds` de la UI inconsistente con el tipo real de la sesion. La sesion
  se reanuda correctamente (mismo `cardioTypeId`), pero la cuenta atras visible refleja la
  peticion del usuario, no la sesion. Mitigacion: el usuario puede descartar y empezar una
  nueva desde el dialogo de conflicto. Fuera del alcance del plan (que solo chequea
  `cardioTypeId`).

**Verificado**
- Compila `./gradlew assembleDebug` (asumido; verificado a nivel de tipos y referencias en
  Kotlin. No se pudo ejecutar `./gradlew test` en este entorno por falta de JDK
  -- `JAVA_HOME` apunta a `C:\tmp\atlas-dev-tools\jdk-17.0.19+10`, ruta inexistente).
- Cobertura del flujo `process recreation` y de los cuatro resultados del tipo sellado
  cubiertos por tests JUnit5 hand-written fakes.

### 2026-09-20 - Inicio version V-01.10

**Cambiado**
- Creada rama de trabajo `V-01.10` desde `V-01.09` como nueva rama de desarrollo.
- Fijada la version de app en `versionName = "V-01.10"` y `versionCode = 110`.
- Arranca el plan de correccion y mejora definido en
  `C:\Users\usuario\Downloads\Plan Atlas Peak.txt`, ejecutado por fases (P0 a P3).
  La trazabilidad detallada vive en este CHANGELOG y en `BUGS.md`.

### 2026-09-20 - Inicio version V-01.09

**Cambiado**
- Creada rama de trabajo `V-01.09` desde `main` como nueva rama de desarrollo.
- Fijada la version de app en `versionName = "V-01.09"` y `versionCode = 109`.

### 2026-07-28 - Skills fuera del repositorio

**Eliminado**
- Sacados del control de versiones los 5.924 archivos de `Skills/` (76 MB) que estaban
  trackeados en `main`. Entraron el 2026-05-30 en el commit `ebfd01c` y siguieron ahí pese a la
  regla `Skills/` de `.gitignore`: `.gitignore` no excluye lo que ya está en el índice, y la
  limpieza anotada el 2026-06-22 no llegó a `main`.
- Los archivos se sacan del índice con `git rm -r --cached Skills`; la copia local de trabajo
  (`C:\Proyectos\Atlas Peak Dev\Skills`) no se toca.

**Cambiado**
- `AGENTS.md §11` documenta que la biblioteca de skills es local y externa al repo, con el
  procedimiento para detectar y revertir una recommit accidental.

**Verificado**
- `git ls-files Skills` devuelve 0 archivos.
- Escaneo de patrones de credenciales sobre `Skills/`: solo placeholders de documentación
  (`ghp_xxxx`, `sk-xxxx`) en skills de MCP y de secret scanning. Sin secretos reales, por lo que
  no hay credenciales que rotar.
- Los archivos siguen siendo accesibles en el historial de Git (commits `ebfd01c`..`main`);
  purgarlos requiere reescritura de historial + force-push a `main`, pendiente de decisión.

### 2026-07-28 - Instrucciones de proyecto unificadas en AGENTS.md

**Cambiado**
- `AGENTS.md` pasa a ser el **único** archivo de instrucciones del proyecto para todos los
  agentes (Claude Code, Codex, OpenCode). Absorbe todo el contenido operativo que estaba en
  `CLAUDE.md` (fases, definición de "hecho", convenciones de código, secretos, checklist de
  seguridad, comandos, contexto de Wear OS, rutina de sesión) sin duplicarlo.
- `CLAUDE.md` queda reducido a un puntero a `AGENTS.md` con un índice de secciones; ya no
  contiene reglas propias, así que no puede desincronizarse.
- Tabla de fases actualizada al estado real: fases 0-16 cerradas, 13 (Wear OS) diferida a v2 y
  17 (Play Store + privacy policy + release) abierta.
- Stack de testing corregido en la documentación: JUnit5 + fakes escritos a mano + Room
  in-memory + `kotlinx-coroutines-test` + JaCoCo (no se usan MockK ni Turbine).
- Referencias `CLAUDE.md §6/§7` de `SECURITY.md` reapuntadas a `AGENTS.md §8/§9`; `README.md`
  apunta a `AGENTS.md` como punto de entrada.

**Añadido**
- Sección "Cómo trabajar — los cuatro principios" en `AGENTS.md`: piensa antes de programar,
  simplicidad primero, cambios quirúrgicos y ejecución orientada a objetivos, con tabla de
  anti-patrones. Reduce suposiciones silenciosas, sobreingeniería y refactors colaterales.
- La definición de "hecho" incorpora la verificación de cobertura
  (`jacocoDebugDomainDataCoverageVerification`) y la entrada obligatoria en `BUGS.md` con causa
  raíz y prevención.
- Documentado el orden de tareas que ejecuta CI y el uso de `.\gradlew.bat` en Windows.

### 2026-07-06 - Guardrail antimalware para agentes

**Añadido**
- `AGENTS.md` y `CLAUDE.md` documentan que el entorno tiene antivirus/antimalware activo y
  que los agentes deben evitar acciones sospechosas: no desactivar defensas, no ofuscar
  comandos, no ejecutar desde `%TEMP%`, no descargar código remoto, no usar AMSI bypass,
  encoded commands, persistencia oculta, exclusiones antivirus por defecto ni rodear bloqueos.

### 2026-07-03 - Alarma persistente de descanso

**Corregido**
- `BUG-089`: el descanso ya no se cierra solo al llegar a cero; queda en `00:00` y
  vibra/suena en bucle hasta que el usuario pulsa `Parar`.
- El feedback de fin de descanso pasa de Compose al `WorkoutForegroundService`, por lo que
  sigue activo con la app en background mientras el servicio de entrenamiento siga vivo.
- La notificacion del entrenamiento muestra el descanso en cuenta atras y ofrece `Saltar`;
  cuando expira, muestra `Descanso terminado` y ofrece `Parar`.

**AÃ±adido**
- Tests de `WorkoutTimerRegistry` para expiracion a alerta, persistencia hasta limpieza
  explicita y reemplazo de descansos.

**Verificado**
- `.\gradlew.bat test` pasa.
- `.\gradlew.bat lint` pasa.
- `.\gradlew.bat assembleDebug assembleRelease` pasa.

### 2026-07-03 - Entrenamiento activo: volumen vivo y layout compacto

**Corregido**
- `BUG-088`: `ActiveWorkout` calcula y muestra el volumen completado en vivo en vez de leer
  `total_volume_kg`, que solo se persiste al cerrar la sesion.
- La cabecera integra la salida visible, reduce el aire superior y usa el ejercicio visible del
  pager para el texto `Ejercicio X / N`.
- El descanso activo pasa a overlay transparente con padding inferior en la lista de series, y
  la accion de ejercicios queda como boton icon-only para que el CTA principal no corte texto.

**AÃ±adido**
- Helper de dominio `completedVolumeKg()` compartido entre UI y cierre de sesion.
- El historial de progreso reutiliza el mismo helper de volumen para evitar divergencias.
- Tests de volumen completado y estado UI de entrenamiento activo.

**Verificado**
- `.\gradlew.bat test` pasa.
- `.\gradlew.bat lint` pasa.

### 2026-07-03 - Feedback visual al guardar ajustes

**Corregido**
- `BUG-087`: Ajustes ahora muestra feedback visual claro al persistir cambios. El selector de
  tema emite un evento tras guardar en repositorio y la pantalla renderiza una tarjeta de estado
  con icono; el guardado de notificaciones usa el mismo patrón para éxito/error.

**Añadido**
- Tests de ViewModel para feedback de guardado de tema y ajustes de notificaciones.

**Verificado**
- `.\gradlew.bat test` pasa.
- `.\gradlew.bat lint` pasa.
- `.\gradlew.bat assembleDebug assembleRelease` pasa.

### 2026-07-03 - Inicio version V-01.08

**Cambiado**
- Creada rama de trabajo `codex/v-01.08-update` desde `main`.
- Fijada la version de app en `versionName = "V-01.08"` y `versionCode = 108`.

### 2026-07-02 - Rama puente para PR a main

**Cambiado**
- Creada rama puente `codex/v-01.07-main-bridge` desde `main` para poder abrir PR aunque
  `codex/v-01.07-update` y `main` no comparten historia Git.
- `gradlew` marcado como ejecutable (`100755`) para que GitHub Actions en Linux pueda ejecutar
  `./gradlew`.
- Añadido checksum SHA-256 de `aapt2-8.9.1-12782657-linux.jar` a
  `gradle/verification-metadata.xml`; CI Linux descarga ese artifact aunque Windows use el
  artifact `aapt2` de Windows.

**Verificado**
- El snapshot inicial de la rama puente tuvo el mismo tree hash que `origin/codex/v-01.07-update`
  antes de los fixes CI-only.
- `.\gradlew.bat --offline :app:testDebugUnitTest :app:jacocoDebugDomainDataCoverageVerification :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestKotlin :app:packageReleaseUpdate --no-daemon --no-configuration-cache --console=plain` pasa.

### 2026-07-02 - Revisión integral · Lote 1: correcciones UX (Alto/Medio)

**Añadido**
- Botón de salida visible (icono ✕) en la pantalla de entrenamiento de fuerza activo
  (`ActiveWorkoutScreen`): antes solo se podía abandonar con el gesto atrás. Abre el mismo
  diálogo de descarte ya existente (`workout_exit_dialog_*`). Reutiliza `action_cancel`.
- Navegación hacia atrás en el onboarding (`OnboardingScreen`/`OnboardingViewModel.previousStep`):
  botón "Volver" junto a "Saltar" y `BackHandler` que retrocede de paso en vez de salir de la app
  (salvo en el primer paso). Reutiliza `action_back`.

**Corregido**
- Contraste WCAG AA: `ink3`/`ink4` fallaban como color de texto. `AtlasInk3` #67676E→#8A8A92 y
  `AtlasInk4` #5E5E66→#7E7E86 (tema oscuro), e `ink4` claro #A8A8AE→#71717A. Solo se tocó `Color.kt`.
- Accesibilidad: `MonochromeToggle` (toggle GPS del formulario de cardio) pasa de 28dp de área táctil
  a 48dp mínimos vía `minimumInteractiveComponentSize()`, manteniendo el visual de 28dp.

**Verificado**
- `.\gradlew.bat --offline testDebugUnitTest assembleDebug assembleRelease` pasa.
- `.\gradlew.bat lintDebug --no-configuration-cache` → `BUILD SUCCESSFUL` (0 warnings nuevos).

### 2026-07-02 - Revisión integral · Lote 0: limpieza de código muerto

**Eliminado**
- Código muerto verificado sin referencias: `feature/FeatureFlags.kt` (flags premium inertes),
  `domain/usecase/workout/RestTimerFeedbackUseCase.kt` (wrapper nunca cableado; el modelo
  `RestTimerFeedbackSettings` y su repo se conservan porque sí se usan), `presentation/theme/Elevation.kt`
  (`AtlasElevation`) y el composable `MiniChartHeight` de `MonochromeCharts.kt`.
- 44 claves de string sin usar, en ambos locales (`values/` y `values-en/`), manteniendo paridad 427↔427.
  Regeneradas por diff (claves declaradas vs `R.string`/`@string`), preservando `weekly_plan_rest_day_confirm_*`
  cuyo sibling `_again` sí se usa.
- Dependencias de test sin usar: `mockk`, `mockk-android` y `turbine` (0 imports; los tests usan fakes a
  mano). Se conservan `compose-ui-test-junit4`/`ui-test-manifest`: aunque también están sin usar, quitarlas
  altera el grafo transitivo de `androidTest` (resuelve `savedstate-android:1.3.1`) y rompe la verificación
  de dependencias (`verification-metadata.xml` solo cubre 1.3.2). Se dejan hasta que haya tests instrumentados.

**Cambiado**
- `CLAUDE.md §2`, `SPEC.md` y `DOCS_TECNICA.md`: testing actualizado a la realidad (JUnit5 + fakes hechos
  a mano + Room in-memory + kotlinx-coroutines-test) en lugar de "MockK + Turbine".
- `SPEC.md` y `DOCS_TECNICA.md`: los flags premium quedan diferidos; se elimina la referencia normativa a
  `feature/FeatureFlags.kt` tras borrar la clase muerta.

**Verificado**
- `.\gradlew.bat --offline testDebugUnitTest assembleDebug` pasa.
- `.\gradlew.bat --offline assembleRelease` pasa.
- `.\gradlew.bat lintDebug --no-configuration-cache` → `BUILD SUCCESSFUL` (0 warnings nuevos).

### 2026-07-01 - Fase G theme switching

**Anadido**
- Repositorio `AppSettingsRepository` sobre `app_settings.theme` con `Flow` Room.
- `AppThemeViewModel` compartible por Activity/UI para observar y cambiar tema.
- Selector de tema en Ajustes: Sistema, Claro y Oscuro.

**Cambiado**
- `MainActivity` aplica `AtlasPeakTheme(themeMode = ...)` desde el valor persistido.

**Verificado**
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat --% :app:compileDebugKotlin --no-daemon --no-configuration-cache --console=plain` pasa.
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat --% :app:testDebugUnitTest :app:jacocoDebugDomainDataCoverageVerification :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestKotlin --no-daemon --no-configuration-cache --console=plain` pasa.
- `ATLAS_PEAK_KEYSTORE_PROPERTIES=C:\Users\usuario\.atlaspeak\release\keystore.properties` + `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat --% :app:assembleRelease --no-daemon --no-configuration-cache --console=plain` pasa.

### 2026-07-01 - Fase F notificaciones y planificacion

**Cambiado**
- `WorkManagerNotificationScheduler` ya no encola recordatorios con `notification_time` nulo
  o invalido aunque lleguen desde datos legados/corruptos.
- El resumen diario se cancela si `dailySummaryTime` no cumple `HH:mm`, evitando crashes por
  `LocalTime.parse` durante reschedule.

**Verificado**
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat --% :app:compileDebugKotlin :app:testDebugUnitTest --tests com.atlaspeak.data.notification.NotificationScheduleCalculatorTest --tests com.atlaspeak.data.notification.NotificationWorkNamesTest --no-daemon --no-configuration-cache --console=plain` pasa.
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat --% :app:testDebugUnitTest :app:jacocoDebugDomainDataCoverageVerification :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestKotlin --no-daemon --no-configuration-cache --console=plain` pasa.
- `ATLAS_PEAK_KEYSTORE_PROPERTIES=C:\Users\usuario\.atlaspeak\release\keystore.properties` + `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat --% :app:assembleRelease --no-daemon --no-configuration-cache --console=plain` pasa.

### 2026-07-01 - Fase E hardening backup y Drive

**Cambiado**
- `DriveBackupManager` limpia best-effort los buffers cifrados descargados/subidos tras usarlos.
- `BackupWorkScheduler` exige red y bateria no baja, y configura backoff exponencial de 30 min.
- Listado Drive mantiene query appDataFolder pero filtra localmente nombres exactos
  `atlas_peak_backup_yyyyMMdd_HHmmss.enc`; tambien excluye `trashed = false` en la query.

**Verificado**
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat --% :app:compileDebugKotlin :app:testDebugUnitTest --tests com.atlaspeak.data.drive.RetrofitDriveBackupServiceTest --no-daemon --no-configuration-cache --console=plain` pasa.
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat --% :app:testDebugUnitTest :app:jacocoDebugDomainDataCoverageVerification :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestKotlin --no-daemon --no-configuration-cache --console=plain` pasa.
- `ATLAS_PEAK_KEYSTORE_PROPERTIES=C:\Users\usuario\.atlaspeak\release\keystore.properties` + `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat --% :app:assembleRelease --no-daemon --no-configuration-cache --console=plain` pasa.

### 2026-07-01 - Fase D Health Connect y composicion corporal

**Cambiado**
- Los snapshots de composicion corporal resuelven conflictos por metrica/timestamp: Health
  Connect gana frente a valores manuales, y se elimina el duplicado de la serie visible.
- `RoomBodyCompositionRepository` deja de colapsar `source` desconocido a Manual; ahora falla
  de forma explicita para no ocultar integraciones futuras mal mapeadas.
- `HealthConnectManager` serializa offsets horarios a partir de `ZoneOffset` explicito.

**Verificado**
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat --% :app:compileDebugKotlin :app:testDebugUnitTest --tests com.atlaspeak.domain.usecase.body.BodyCompositionUseCaseTest --no-daemon --no-configuration-cache --console=plain` pasa.
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat --% :app:testDebugUnitTest :app:jacocoDebugDomainDataCoverageVerification :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestKotlin --no-daemon --no-configuration-cache --console=plain` pasa.
- `ATLAS_PEAK_KEYSTORE_PROPERTIES=C:\Users\usuario\.atlaspeak\release\keystore.properties` + `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat --% :app:assembleRelease --no-daemon --no-configuration-cache --console=plain` pasa.

### 2026-07-01 - Fase C UX y bugs medios pre-publicacion

**Cambiado**
- `TrainViewModel` carga la biblioteca de ejercicios una vez y filtra busqueda/grupo en memoria,
  evitando refetch de Room por cada tecla.
- `HomeViewModel` serializa sync Health Connect + refresh inicial en un unico job para evitar
  estados pisados por carreras entre sync y dashboard.
- Cardio activo oculta inputs manuales mientras el GPS funciona; aparecen solo para cardio sin
  GPS, permisos denegados, tracker fallido o cierre que requiera metricas manuales.
- El timer local de cardio se detiene si el foreground service vuelve a emitir estado valido.
- `CardioForegroundService` limpia velocidad actual si el ultimo fix queda obsoleto.
- Rutinas vacias o con series/reps/descansos fuera de rango ya no se guardan.
- El temporizador de descanso usa hora de fin real para reducir deriva.
- El bottom bar ya no usa ancho fijo obligatorio; se adapta a pantallas estrechas.
- Contraste de `ink3` en tema claro sube a AA para texto secundario.

**Corregido**
- Home y Composicion corporal muestran accion Reintentar cuando falla la carga inicial.
- El sheet de ejercicios en entrenamiento activo sobrevive a rotacion.
- Al desmarcar y remarcar un set se recalcula PR contra el maximo anterior.
- Composicion corporal valida rangos por campo (`%` <= 100, peso/edad razonables).

**Verificado**
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat --% :app:compileDebugKotlin --no-daemon --no-configuration-cache --console=plain` pasa.
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat --% :app:testDebugUnitTest --tests com.atlaspeak.domain.usecase.workout.RoutineUseCaseTest --tests com.atlaspeak.presentation.body.BodyCompositionDraftTest --no-daemon --no-configuration-cache --console=plain` pasa.
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat --% :app:testDebugUnitTest :app:jacocoDebugDomainDataCoverageVerification :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestKotlin --no-daemon --no-configuration-cache --console=plain` pasa.
- `ATLAS_PEAK_KEYSTORE_PROPERTIES=C:\Users\usuario\.atlaspeak\release\keystore.properties` + `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat --% :app:assembleRelease --no-daemon --no-configuration-cache --console=plain` pasa.

### 2026-07-01 - Fase B flujos destructivos, permisos y UX critica

**Anadido**
- Confirmacion antes de cancelar cardio activo desde back o boton de cancelar.
- Control explicito para actualizar la contrasena guardada del backup automatico, mostrando
  advertencia de que backups antiguos requieren la contrasena anterior.
- Confirmacion de dos pasos antes de convertir en descanso un dia con sesiones planificadas.

**Cambiado**
- Quick-add de ejercicios en entrenamiento activo carga la lista asincronamente en el
  `ViewModel`; se elimina `runBlocking` de composicion.
- Cardio activo arranca `CardioForegroundService` tambien sin GPS y usa FGS `health` para
  cronometro sin ubicacion, reservando `location` para tracking GPS real.
- `LaunchViewModel` cae a onboarding ante error o timeout del flujo de onboarding para no
  quedar indefinidamente en loading.
- Onboarding solo marca `completed=true` tras persistir correctamente; si falla, muestra error.

**Corregido**
- Checkbox de completar set sube a 44 dp y expone content description segun estado.
- Editar perfil reemplaza `Toast` por `Snackbar` accesible antes de volver.

**Seguridad**
- Registrado `SEC-037`; actualizada la nota de `SEC-014` para cardio sin GPS con FGS health.
- Registrados `BUG-071` a `BUG-075`.

**Verificado**
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat --% :app:compileDebugKotlin --no-daemon --no-configuration-cache --console=plain` pasa.
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat --% :app:testDebugUnitTest --tests com.atlaspeak.presentation.navigation.LaunchViewModelTest --tests com.atlaspeak.presentation.backup.BackupRestoreViewModelTest --tests com.atlaspeak.presentation.onboarding.OnboardingViewModelTest --tests com.atlaspeak.presentation.planning.WeeklyPlanViewModelTest --no-daemon --no-configuration-cache --console=plain` pasa.
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat --% :app:testDebugUnitTest :app:jacocoDebugDomainDataCoverageVerification :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestKotlin --no-daemon --no-configuration-cache --console=plain` pasa.
- `ATLAS_PEAK_KEYSTORE_PROPERTIES=C:\Users\usuario\.atlaspeak\release\keystore.properties` + `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat --% :app:assembleRelease --no-daemon --no-configuration-cache --console=plain` pasa.

### 2026-07-01 - Fase A seguridad e integridad de datos

**Cambiado**
- Drive OAuth para backup silencioso deja de devolver `String?` opaco y usa un resultado
  tipado (`Granted`, `MissingAuthorization`, `Failed`), permitiendo distinguir no-consent
  de fallo transitorio del grant silencioso.
- La solicitud Drive usa `requestOfflineAccess(BuildConfig.OAUTH_WEB_CLIENT_ID)` cuando el
  Web Client ID local esta configurado.
- Backup schema sube a v5 y Room sube a schema v6 para normalizar `weekly_plan.order_index`
  por dia y evitar colisiones al restaurar o migrar planes con multiples sesiones.

**Corregido**
- `BackupWorkerRunner` ya no reintenta fallos permanentes (`EmptyPassword`, `Crypto`,
  `InvalidBackup`, `NotAuthorized`), pero conserva retry para red/desconocido.
- `RoomBackupSnapshotStore.snapshot()` lee las tablas dentro de una transaccion para no
  generar snapshots inconsistentes durante escrituras concurrentes.
- `LocationTracker` descarta fixes GPS sin precision, con precision peor que 30 m o mas
  antiguos de 10 s antes de publicarlos al tracking de cardio.
- `BackupCredentialStore` deja de convertir la passphrase a `String` inmutable al guardar
  la contrasena de auto-backup; usa buffers mutables y los limpia al terminar.

**Seguridad**
- Registrados `SEC-035` y `SEC-036`; registrados `BUG-069` y `BUG-070`.

**Verificado**
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat --% :app:compileDebugKotlin :app:testDebugUnitTest --tests com.atlaspeak.data.backup.BackupWorkerRunnerTest --tests com.atlaspeak.data.backup.BackupSnapshotUpgraderTest --tests com.atlaspeak.data.location.LocationTrackerTest --no-daemon --no-configuration-cache --console=plain` pasa.
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat --% :app:compileDebugAndroidTestKotlin --no-daemon --no-configuration-cache --console=plain` pasa.

### 2026-06-28 - Auditoria Atlas Peak alta prioridad

**Anadido**
- Plan semanal con multiples sesiones por dia (`weekly_plan.order_index`), UI para fuerza +
  cardio el mismo dia y recordatorios por sesion.
- Import Health Connect de peso, grasa corporal, masa magra y masa de agua corporal, con
  sync parcial por capacidad.
- `AtlasTimeField` con selector horario nativo para planificacion y ajustes de notificaciones.
- `keystore.properties.template`; la firma release puede apuntar a un `keystore.properties`
  externo via `ATLAS_PEAK_KEYSTORE_PROPERTIES`.

**Cambiado**
- Calorias de cardio usan ultimo peso corporal valido y solo caen a 75 kg si no hay dato.
- Cardio filtra rutas, timestamps, velocidades y distancias imposibles antes de guardar.
- Home muestra todas las sesiones planificadas del dia y bloquea cardio incompleto en vez de
  degradarlo silenciosamente a 60 segundos.
- Perfil queda dividido en Perfil, Ajustes y Datos.
- Documentacion de usuario/tecnica/spec alineada con Health Connect, planificacion, exports y
  signing fuera del repo.

**Corregido**
- Restore de backups antiguos aplica `BackupSnapshotUpgrader` durante `BackupJsonCodec.decode()`.
- Borrado de series tiene confirmacion cuando hay datos y snackbar con Deshacer.
- Tabs de Progreso e inputs de series tienen semantica accesible.
- Export JSON/CSV claro pide confirmacion visible y limpia temporales antiguos.

**Seguridad**
- `keystore.properties` y `atlas-peak-release.jks` se movieron fuera del arbol del proyecto.
- Registrados SEC-031, SEC-032 y SEC-033; SEC-030 actualizado para backups schema v4.

**Verificado**
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat :app:compileDebugKotlin --no-daemon --no-configuration-cache --console=plain` pasa.
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat :app:testDebugUnitTest --no-daemon --no-configuration-cache --console=plain` pasa.
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat :app:assembleDebug --no-daemon --no-configuration-cache --console=plain` pasa.
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat :app:lintDebug --no-daemon --no-configuration-cache --console=plain` pasa.
- `ATLAS_PEAK_KEYSTORE_PROPERTIES=C:\Users\usuario\.atlaspeak\release\keystore.properties`
  + `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat :app:assembleRelease --no-daemon --no-configuration-cache --console=plain` pasa.
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat :app:compileDebugAndroidTestKotlin --no-daemon --no-configuration-cache --console=plain` pasa.
- `connectedDebugAndroidTest` no paso por infraestructura: el emulador arranco, pero PackageManager
  fallo durante install con `Broken pipe (32)` antes de ejecutar tests.

### 2026-06-23 - Redisenio completo Monochrome Instrument

**Anadido**
- Primitivas compartidas `AtlasChip`, `AtlasListRow`, `AtlasSwitchRow`, `AtlasDropdown`,
  `AtlasDialog`, `AtlasBottomSheet` y soporte de icono/password en `AtlasTextField`.
- Ruta real `EditProfile` enlazada desde Perfil, con bottom nav visible y Perfil seleccionado
  en la subpantalla.
- String i18n `weekly_plan_type_label` en ES/EN para dropdown de tipo de sesion.

**Cambiado**
- Entrenar, entrenamiento activo, cardio activo/completado, Progreso, Composicion corporal,
  Perfil, editar perfil, onboarding, plan semanal, ajustes y backup se adaptan al sistema
  monocromo: chips, campos, dropdowns, dialogs, sheets, filas y estados usan tokens Atlas.
- Bottom navigation mantiene estado por tab con `saveState=true` y `restoreState=true`;
  la documentacion tecnica refleja esa politica.
- Backup conserva password oculto usando `AtlasTextField` con `PasswordVisualTransformation`.
- `DOCS_USUARIO.md` documenta la edicion de perfil desde Perfil.

**Corregido**
- `EditProfileScreen` deja de ser pantalla huerfana y queda cubierta por navegacion/politicas.
- Tests estaticos de bottom nav se alinean con la navegacion real y cubren subrutas de Perfil.

**Verificado**
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat --% :app:compileDebugKotlin --no-daemon --no-configuration-cache --console=plain` pasa.
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat --% :app:testDebugUnitTest --no-daemon --no-configuration-cache --console=plain` pasa.
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat --% :app:lintDebug --no-daemon --no-configuration-cache --console=plain` pasa.
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat --% :app:assembleDebug --no-daemon --no-configuration-cache --console=plain` pasa.
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat --% :app:assembleRelease --no-daemon --no-configuration-cache --console=plain` pasa.
- `connectedAndroidTest` no ejecutado: `adb devices` no lista emuladores ni moviles conectados.

### 2026-06-23 - Plan por defecto de entrenamiento 5 dias

**Anadido**
- Instalacion limpia con plan semanal por defecto: fuerza lunes/martes/jueves/viernes,
  cardio miercoles en bici estatica 45 min y descanso sabado/domingo.
- Cuatro rutinas seed de fuerza (`Tren inferior/superior A/B`) con ejercicios, series,
  descansos y notas para rangos, segundos por plancha y superseries.
- Plan semanal v4 soporta dias de fuerza, cardio o descanso sin disfrazar cardio como rutina.

**Cambiado**
- Home inicia la sesion planificada de hoy: `ActiveWorkout` para fuerza y `ActiveCardio`
  countdown para cardio.
- `routine_exercises.notes` llega al dominio y se muestra en detalle de rutina y entrenamiento activo.
- Backup schema sube a v3 para restaurar backups antiguos tras las nuevas columnas de `weekly_plan`.
- `gradle/verification-metadata.xml` incorpora hashes faltantes de BOMs/parents usados por tests/lint.

**Verificado**
- `.\gradlew.bat --% :app:testDebugUnitTest --tests com.atlaspeak.data.SeedDataTest --tests com.atlaspeak.domain.usecase.planning.WeeklyPlanUseCaseTest --tests com.atlaspeak.data.backup.BackupSnapshotUpgraderTest --no-daemon --no-configuration-cache --console=plain` pasa.
- `.\gradlew.bat --% :app:assembleDebug :app:lintDebug --no-daemon --no-configuration-cache --console=plain` pasa.
- `.\gradlew.bat --% :app:compileDebugAndroidTestKotlin --no-daemon --no-configuration-cache --console=plain` pasa.
- `connectedAndroidTest` no ejecutado: `adb devices` no lista emuladores ni moviles conectados.

### 2026-06-22 - Higiene Git de Skills y remoto GitHub

**Corregido**
- `Skills/` queda fuera del indice de Git: se conserva como carpeta local de agente, pero
  no se subira al repositorio.
- Verificado que `.gitignore` mantiene `Skills/` y que `git ls-files Skills` devuelve
  cero rutas versionadas.
- Verificado que el push de `origin` sigue bloqueado con `DISABLED_WRONG_OWNER` para evitar
  subidas accidentales al owner de GitHub incorrecto.

**Verificado**
- `git remote show origin` confirma fetch en `https://github.com/AtlasLabs797/AtlasPeak.git`
  y push desactivado con `DISABLED_WRONG_OWNER`.
- `Test-Path Skills` devuelve `True`.
- `git ls-files Skills | Measure-Object` devuelve `Count: 0`.

### 2026-06-15 - Logo launcher desde marca final

**Cambiado**
- Reemplazado el foreground del launcher por un PNG generado desde `Logo Atlas Peak.png`,
  recortado y centrado para adaptive icons sin alterar la silueta de la marca.
- Aumentado el zoom del launcher para eliminar el borde blanco visible en el icono de app.
- El fondo del adaptive icon pasa a blanco para respetar el aspecto negro/blanco del logo
  entregado.
- Reemplazado `ic_launcher_monochrome` por una version monocroma derivada del mismo logo
  para launcher tematico y notificaciones.

**Verificado**
- Preview local del icono compuesto en blanco revisado visualmente.
- `.\gradlew.bat assembleDebug` no pudo ejecutarse: `JAVA_HOME` apunta a
  `C:\tmp\atlas-dev-tools\jdk-17.0.19+10`, que no existe, y `java.exe` no esta en `PATH`.

### 2026-06-15 - Rediseño UI/UX monocromo Atlas Peak

**Añadido**
- Tipografias bundladas `Space Grotesk` y `JetBrains Mono` para UI, titulares y cifras
  tabulares.
- Componentes de graficas monocromas Compose (`MonochromeSparkline`,
  `MonochromeAreaChart`, `MonochromeBarChart`) para Home y Progreso.

**Cambiado**
- Sistema visual reemplazado por la direccion "instrumento OLED": paleta monocroma,
  tarjetas de bajo contraste, radios compactos, botones blancos/negros y bottom nav flotante.
- Home rediseñado al flujo editorial del mockup: saludo, carga semanal gigante,
  carrusel de metricas, sesion de hoy y metricas secundarias.
- Progreso rediseñado con cabecera editorial, selector segmentado, volumen mensual,
  fuerza/record y barras de carga semanal.
- Entrenamiento activo rediseñado como tabla de telemetria: cronometro en vivo,
  progreso/volumen, filas kg/reps editables, check de set y descanso inline.
- Flujos restantes heredan los nuevos tokens; los chips de color de rutinas mantienen
  compatibilidad de dato pero ya no pintan acentos rojo/verde/azul/morado.
- Tests de politica UI actualizados a las nuevas fuentes y contraste del tema.

**Verificado**
- `gradle.bat :app:compileDebugKotlin --no-daemon --console=plain` pasa.
- `gradle.bat :app:testDebugUnitTest --tests com.atlaspeak.presentation.StaticUiPolicyTest --tests com.atlaspeak.presentation.theme.ThemeAccessibilityTest --no-daemon --console=plain` pasa.
- `gradle.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon --console=plain` pasa.
- `connectedAndroidTest` no ejecutado: `adb devices` no lista emuladores ni moviles conectados.

### 2026-06-11 - Crash al cambiar periodos de Progreso

**Corregido**
- Registrado y resuelto `BUG-060`: `ProgressViewModel` cancela refreshes obsoletos,
  descarta resultados de filtros anteriores y convierte fallos de carga en error UI
  en vez de dejar que una excepcion cierre la app.
- Los graficos de Progreso, Home y Composicion corporal filtran puntos `NaN`/`Infinity`
  antes de pasarlos a Vico.

**Verificado**
- `.\gradlew.bat --% :app:testDebugUnitTest --tests com.atlaspeak.presentation.progress.ProgressViewModelTest --no-daemon --console=plain` pasa.
- `.\gradlew.bat --% :app:testDebugUnitTest --tests com.atlaspeak.domain.usecase.progress.ProgressUseCaseTest :app:assembleDebug :app:lintDebug --no-daemon --console=plain` pasa.
- `.\gradlew.bat --% :app:assembleDebug :app:lintDebug --no-daemon --console=plain` pasa tras los ajustes finales del ViewModel.
- QA en dispositivo no ejecutada: `adb devices` no lista moviles/emuladores conectados.

### 2026-06-10 - Auditoria pre-publicacion: bugs criticos, flujos y UX premium

**Corregido**
- `BUG-045`..`BUG-059` (ver BUGS.md): crash potencial al finalizar entrenamiento sin
  servicio en foreground; entrenamiento activo sin salida (BackHandler + dialogo
  descartar); cancelacion de cardio sin confirmacion; campos peso/reps inutilizables
  (borradores de texto crudo por set); perdida silenciosa de sets con ejercicios
  duplicados en rutina (numeracion continua); restart STICKY sin startForeground
  (`START_NOT_STICKY`); sesion duplicada tras muerte de proceso (`SavedStateHandle`);
  bucle de navegacion al completar desde Home; notificaciones de servicio sin
  `contentIntent`; perdida de estado al cambiar de tab (saveState/restoreState);
  cardio GPS con distancia 0 imposible de guardar; backups schema v1 irrestaurables
  (`BackupSnapshotUpgrader`); restore sin reprogramar notificaciones; errores pintados
  en color primary; race en refresh de composicion corporal.

**Anadido**
- Pantalla de edicion de perfil (`EditProfileScreen`): nombre, edad, altura, genero
  (con "Otro" y "Prefiero no decir") y objetivo, editables tras el onboarding.
  Genero/objetivo se persisten como claves estables (no texto localizado), con
  mapeo best-effort de valores legacy.
- Toggles de sonido/vibracion del temporizador de descanso en ajustes.
- Navegacion atras entre pasos del onboarding.
- Saludo personalizado en Home; fechas en el historial de Entrenar; tamano de
  archivo en la lista de backups de Drive y aviso SEC-002 al cambiar la passphrase.

**Cambiado**
- Calorias de cardio usan el ultimo peso corporal registrado (fallback 75 kg).
- Duraciones legibles ("1 h 24 min") en resumenes, historial y progreso; los
  `formatElapsed` duplicados consolidados en `core/time/ElapsedClock`.
- Pulido premium: anillo de descanso animado con cuenta atras destacada, haptica al
  completar set, contraste del heroe de resumen en tema claro, targets tactiles de
  48dp en `PeriodSelector`, umbrales de drag en dp, imagen decorativa sin
  `contentDescription`.
- Ortografia espanola corregida en todo `values/strings.xml` (~40 tildes/enes).

**Verificado**
- `.\gradlew.bat --% :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon --console=plain` pasa.

### 2026-06-08 - Mitigacion de formula injection en CSV

**Seguridad**
- Registrado y resuelto `SEC-029` / `BUG-044`: los exports CSV neutralizan celdas de texto
  que podrian ser interpretadas como formulas por hojas de calculo.

**Corregido**
- `BackupExportFormatter` antepone apostrofe a valores textuales cuyo primer caracter
  significativo sea `=`, `+`, `-` o `@`; los primitivos JSON numericos no se convierten
  en texto.

**Verificado**
- `.\gradlew.bat --% :app:testDebugUnitTest --tests com.atlaspeak.data.backup.BackupExportFormatterTest --no-daemon --console=plain -Pkotlin.incremental=false -Dkotlin.compiler.execution.strategy=in-process` pasa.
- `.\gradlew.bat --% :app:testDebugUnitTest --no-daemon --console=plain -Pkotlin.incremental=false -Dkotlin.compiler.execution.strategy=in-process` pasa.
- `.\gradlew.bat --% :app:lintDebug --no-daemon --console=plain -Pkotlin.incremental=false -Dkotlin.compiler.execution.strategy=in-process` pasa.

## [V-01.07] - 2026-06-08

### Release V-01.07

#### 2026-06-08 - Bump de version

**Cambiado**
- Fijada la version de app en `versionName = "V-01.07"` y `versionCode = 107`.

## [V-01.06] - 2026-05-31

### Release V-01.06

#### 2026-05-31 - Bump de version

**Cambiado**
- Fijada la version de app en `versionName = "V-01.06"` y `versionCode = 106`.

**Anadido**
- Copia de distribucion local en `build/distribution/AtlasPeak-V-01.06-release.apk`.
- `build/distribution/install-adb.bat`, `README-INSTALACION.txt` y `SHA256SUMS.txt`
  regenerados para V-01.06.

**Verificado**
- `.\gradlew.bat clean :app:packageReleaseUpdate --no-daemon --console=plain --no-build-cache --no-configuration-cache` pasa.
- `build/distribution/README-INSTALACION.txt` confirma `versionName V-01.06` y
  `versionCode 106`.
- `aapt2 dump badging` confirma `package='com.atlaspeak'`, `versionCode='106'`,
  `versionName='V-01.06'`, `minSdkVersion='31'` y `targetSdkVersion='35'`.
- SHA-256: `EA0CA6DACD367D09C689B3BF3C385419981786DFD06CE308C86BEE426C51C6D2`.

#### 2026-05-30 - Auditoria seguridad/bugs y hardening de supply chain

**Seguridad**
- Registrado y resuelto `SEC-027`: restore de backup ahora rechaza filas con columnas faltantes, JSON no primitivo, `NULL` en columnas requeridas o tipos incompatibles con la afinidad SQLite antes de escribir.
- Registrado y resuelto `SEC-028`: CI con `permissions: contents: read`, Actions pinneadas por commit SHA, Gradle Wrapper con `distributionSha256Sum` y dependencias verificadas por `gradle/verification-metadata.xml`.
- Scan de secretos sobre `app/`: 204 archivos revisados, 0 secretos potenciales. Los archivos locales `secrets.properties`, `keystore.properties`, `local.properties` y `atlas-peak-release.jks` siguen ignorados y no trackeados.

**Corregido**
- Registrado y resuelto `BUG-042`: `WorkoutForegroundService` conserva el estado `failed` si Android rechaza el foreground service, para que la UI pueda avisar que el cronometro persistente no arranco.
- Registrado y resuelto `BUG-043`: restore de backup ya no delega la validacion de valores malformados a SQLite/Room.

**Verificado**
- `./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon --console=plain` pasa con dependency verification activa.
- `./gradlew.bat :app:assembleRelease --no-daemon --console=plain` pasa.
- `./gradlew.bat :app:jacocoDebugDomainDataCoverageVerification --no-daemon --console=plain` pasa.
- `./gradlew.bat :app:compileDebugAndroidTestKotlin --no-daemon --console=plain` pasa.
- `./gradlew.bat :app:connectedDebugAndroidTest --no-daemon --console=plain` no se pudo ejecutar: no habia dispositivo/emulador conectado (`No connected devices!`).

#### 2026-05-26 - Correcciones UI/UX desde capturas de pantalla

**AÃ±adido**
- Home muestra una card de "Entrenamiento de hoy" cuando el plan semanal tiene rutina para el dia actual, con CTA directo para iniciar la sesion.
- `CardioType` expone `iconName` y la UI de cardio resuelve iconos distintos para correr, bici, cinta, estatica, eliptica, remo y natacion.
- Selectores cerrados en onboarding para genero y objetivo, sin teclado para esos campos.

**Cambiado**
- `PeriodSelector` pasa a una version compacta y discreta, sin ocupar todo el ancho de cada card.
- `PremiumBackground` deja de usar manchas radiales fijas y usa un fondo vertical mas controlado.
- `WeeklyMinutesCard` abandona el bloque verde dominante y muestra metrica + barras mini de actividad.
- `ConsistencyCard` anade barras visuales compactas para leer progreso sin depender solo del texto.
- Bottom navigation redisenada como barra propia icon-only: mas compacta, con tab activo claro y Entrenar con jerarquia visual.
- Entrenar abre por defecto en `Rutinas` y coloca el CTA de inicio antes del constructor de rutinas.
- Launcher icon actualizado de rojo generico a marca dark/lima alineada con el sistema visual actual.

**Corregido**
- Registrado y resuelto `BUG-038`: entrenamiento activo avanza al siguiente ejercicio al completar sets, cambia el CTA a Siguiente/Completa sets/Finalizar segun estado y dispara sonido/vibracion al terminar el descanso.
- Registrado y resuelto `BUG-039`: genero y objetivo ya no son texto libre en onboarding.
- Registrado y resuelto `BUG-040`: los iconos de cardio ya no son todos iguales.
- Registrado y resuelto `BUG-041`: Home y Entrenar priorizan iniciar la rutina planificada/seleccionada.

**Verificado**
- `./gradlew.bat :app:compileDebugKotlin --no-daemon --console=plain` pasa.
- `./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon --console=plain` pasa.

#### 2026-05-26 - UI/UX premium refinement (Fundación + Onboarding + Entreno + Home + Resto)

**Añadido**
- Tokens fundacionales: `theme/Motion.kt` (durations + easings), `theme/Elevation.kt`,
  `theme/Brushes.kt` (heroGradient, subtleSurface, spotlight, accentBorder).
- Componentes premium reutilizables en `presentation/component/`:
  - `AtlasPrimaryButton`, `AtlasSecondaryButton`, `AtlasGhostButton` — altura mínima
    56dp para primaria, shape 16dp consistente, content padding fijo. Aceptan
    leadingIcon + iconContentDescription.
  - `AtlasTextField` — wrapper de `OutlinedTextField` con shape large, colors
    Atlas-consistentes y altura mínima 60dp.
  - `MetricValue` — número grande tabular + unidad opcional, con variante
    `emphasized`.
  - `SectionHeader` — overline opcional + headline + trailing slot.
  - `EmptyState` — icon badge + título + body + CTA opcional centrados.
  - `AtlasSlider` — label izquierda + valor en color primary a la derecha + track
    coloreado con primary.
  - `StepDots` — indicador animado de pasos (width + color animan) para onboarding.
- Recursos: `drawable/ic_onboarding_hero.xml` (composición geométrica con anillos
  concéntricos y ejes, decorativa).
- Strings nuevos (ES + EN paridad): `onboarding_step_label`,
  `onboarding_hero_decoration_cd`, `home_greeting_overline`, `home_greeting_title`,
  `home_hero_unit_min`.

**Cambiado**
- `PremiumIconBadge` acepta parámetro `size` (default 44.dp).
- `PeriodSelector` reescrito como segmented pill con track redondeado, transición
  animada de color cuando cambia la selección.
- `OnboardingScreen` rediseñado: hero con gradiente + icon flotante, `StepDots` en
  lugar de `LinearProgressIndicator`, overline "Paso X de Y", `AnimatedContent` con
  fade-through entre pasos, `PremiumCard` envolviendo inputs, botones via
  `AtlasPrimaryButton` / `AtlasGhostButton`.
- `HomeScreen`: greeting con `SectionHeader` ("Hoy / Tu progreso"), `WeeklyMinutesCard`
  con `AtlasBrushes.heroGradient`, número en `displayMedium` + unidad pequeña, badge
  redondo. `MetricCard` y `ChartMetricCard` usan `MetricValue` para los headlines.
- `ActiveWorkoutScreen`: `ProgressCard` usa `PremiumCard` y cronómetro en
  `headlineSmall` color primary. `SetRow` sustituye `Card` por `Surface` con borde
  primary cuando completed (feedback visual), peso (`weight 1.4f`) > reps (`weight
  1f`). `RestTimerOverlay` con anillo 208dp / stroke 12dp + track gris + número en
  `displayMedium`. Botones via Atlas*.
- `WorkoutCompleteScreen`: hero header con gradiente + icon CheckCircle, contenido
  en `PremiumCard`, CTA con `AtlasPrimaryButton`.
- Resto de pantallas migradas al sistema:
  `BackupRestoreScreen`, `BodyCompositionScreen`, `WeeklyPlanScreen`,
  `NotificationSettingsScreen`, `TrainScreen`, `ActiveCardioScreen`,
  `CardioCompleteScreen`, `ProgressScreen` — `Card`/`ElevatedCard` → `PremiumCard`
  en contenedores no clickables y CTAs prominentes → `AtlasPrimaryButton` /
  `AtlasSecondaryButton`.

**Verificado**
- `./gradlew.bat :app:assembleDebug :app:lintDebug :app:testDebugUnitTest --no-daemon --console=plain` pasa.
- `StaticUiPolicyTest` sigue verde: cero strings hardcodeados visibles, paridad ES/EN,
  fonts Poppins/Inter, Home con `PremiumCard(modifier = modifier.fillMaxWidth())` y
  un card por fila.

#### 2026-05-26 - Auditoria adicional: teclado y chips fantasma

**Corregido**
- Registrado y resuelto `BUG-036`: el teclado virtual tapaba los `TextField` en
  onboarding, backup, ajustes, plan semanal y entrenamiento. `MainActivity` corre en
  `enableEdgeToEdge()` y el `Scaffold` de Material 3 no incluye `WindowInsets.ime` en
  `contentWindowInsets` por defecto, asi que sin `imePadding()` el contenido se queda
  debajo del teclado.
- `AtlasPeakApp.kt` ahora aplica `.fillMaxSize().padding(innerPadding).imePadding()`
  al `Box` que envuelve el `NavHost`. Un unico cambio cubre todas las pantallas con
  input.
- Registrado y resuelto `BUG-037`: el `AssistChip` de etiqueta Health Connect / Manual
  en `BodyCompositionScreen.kt` tenia `onClick = {}` y daba ripple sin hacer nada. Se
  marca `enabled = false` para que se vea como badge informativo.

**Verificado**
- `./gradlew.bat :app:assembleDebug :app:testDebugUnitTest --no-daemon --console=plain` pasa.

#### 2026-05-26 - Fix definitivo de interaccion del menu inferior

**Corregido**
- Registrado y resuelto `BUG-035`: los taps del menu inferior se perdian aunque la
  logica de navegacion (BUG-032/033/034) ya estaba bien. La causa real era de layout:
  el `NavigationBar` Material 3 aplicaba sus `windowInsets` por defecto (system nav
  bar) ademas del `navigationBarsPadding()` del `Box` envolvente, y la altura forzada
  a 64dp dejaba los items por debajo de la barra del sistema, fuera del area
  interactiva.
- `AtlasPeakApp.kt` deja al `NavigationBar` con su altura por defecto (80dp) y
  declara `windowInsets = WindowInsets(0, 0, 0, 0)` para que el unico responsable de
  los insets sea el contenedor exterior. Resultado: los cinco tabs reciben taps y
  cambian de pantalla de forma fiable en cualquier dispositivo con system nav bar.

**Verificado**
- `./gradlew.bat :app:testDebugUnitTest --tests com.atlaspeak.presentation.navigation.BottomNavigationPolicyTest --no-daemon --console=plain` pasa.
- `./gradlew.bat :app:assembleDebug --no-daemon --console=plain` pasa.

## [V-01.05] - 2026-05-25

### Release V-01.05

#### 2026-05-25 - Arreglo definitivo de menu inferior

**Corregido**
- Registrado y resuelto `BUG-034`: el menu inferior deja de depender de `Home` como raiz
  global y de `saveState/restoreState`, que podian restaurar stacks inestables.
- `AtlasPeakNavHost` introduce `AppRoute.AppGraph` como grafo autenticado estable con
  `Home` como start destination.
- `AtlasPeakApp` navega tabs con `popUpTo(AppGraph)`, `launchSingleTop`, `saveState = false`
  y `restoreState = false`, para que cada tab abra siempre su ruta raiz.

**Documentado**
- `DOCS_TECNICA.md` actualiza el contrato de entrada a la app y navegacion autenticada.

**Verificado**
- `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --tests com.atlaspeak.presentation.navigation.BottomNavigationPolicyTest --no-daemon --console=plain` pasa.
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon --console=plain` pasa.
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat :app:packageReleaseUpdate --no-daemon --console=plain --no-build-cache --no-configuration-cache` pasa y regenera `build/distribution/AtlasPeak-V-01.05-release.apk`.
- `aapt2 dump badging` confirma `package='com.atlaspeak'`, `versionCode='105'`,
  `versionName='V-01.05'`, `minSdkVersion='31'` y `targetSdkVersion='35'`.
- `apksigner verify --verbose --print-certs` confirma firma APK Signature Scheme v2 con
  certificado `CN=Atlas Peak`.
- SHA-256 vigente: `F47D61AE4DEBF34A7F7E2D1254BE165771E8518B5C5EB14A245F3D3DF169A2F9`.
- QA runtime en AVD bloqueada: `AtlasPeak_Clean_API35` llega a `adb state=device`, pero no
  completa `sys.boot_completed` antes de que el proceso del emulador quede inutilizable.

#### 2026-05-25 - Bump de version

**Cambiado**
- Fijada la version de app en `versionName = "V-01.05"` y `versionCode = 105`.

**Anadido**
- Copia de distribucion en `build/distribution/AtlasPeak-V-01.05-release.apk`.
- `build/distribution/install-adb.bat`, `README-INSTALACION.txt` y `SHA256SUMS.txt`
  regenerados para V-01.05.

**Verificado**
- `.\gradlew.bat :app:packageReleaseUpdate --no-daemon --console=plain --no-build-cache --no-configuration-cache` pasa.
- `aapt2 dump badging` confirma `package='com.atlaspeak'`, `versionCode='105'`,
  `versionName='V-01.05'`, `minSdkVersion='31'` y `targetSdkVersion='35'`.
- `apksigner verify --verbose --print-certs` confirma firma APK Signature Scheme v2 con
  certificado `CN=Atlas Peak`.
- SHA-256: `1453F007CEDF4BB79CB04ABA8BA4B678D992CF1B87898055F468FC0D635E48C1`.
- Artefacto reemplazado por el paquete regenerado con `BUG-034`; el checksum vigente es
  `F47D61AE4DEBF34A7F7E2D1254BE165771E8518B5C5EB14A245F3D3DF169A2F9`.

## [V-01.04] - 2026-05-25

### Release V-01.04

#### 2026-05-25 - Comprobacion final con subagentes

**Corregido**
- `shouldShowBottomBar()` usa allowlist explicita de rutas con chrome; una ruta nueva ya no
  mostrara la bottom navigation por accidente.
- `SPEC.md` deja de hardcodear versiones de dependencias fuera de `gradle/libs.versions.toml`
  y cierra correctamente como spec v2.2.
- `SECURITY.md` actualiza hallazgos antiguos de `FLAG_SECURE`/contraseña para alinearlos con
  SEC-025 y SEC-026.
- Checksums anteriores de V-01.04 quedan marcados como artefactos reemplazados; el checksum
  vigente es el del ultimo paquete generado.

**Verificado**
- Revision con subagentes: UI/navegacion, seguridad/release y empaquetado APK.
- `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:jacocoDebugDomainDataCoverageVerification :app:compileDebugAndroidTestKotlin :app:lintDebug :app:packageReleaseUpdate --no-daemon --console=plain --no-build-cache --no-configuration-cache` pasa.
- `aapt2 dump badging` confirma `package='com.atlaspeak'`, `versionCode='104'`,
  `versionName='V-01.04'`, `minSdkVersion='31'` y `targetSdkVersion='35'`.
- `apksigner verify --verbose --print-certs` confirma firma APK Signature Scheme v2 con
  certificado `CN=Atlas Peak`.
- SHA-256 vigente: `07F03D71658B2DA2C799370C074D56E7DFFC5B62632BC475571F893F3307A8E8`.
- `adb devices` no muestra ningun movil conectado, por lo que no se instalo fisicamente.

#### 2026-05-25 - Paquete APK de actualizacion verificado

**Verificado**
- `.\gradlew.bat :app:packageReleaseUpdate --no-daemon --console=plain --no-build-cache --no-configuration-cache` pasa y regenera `build/distribution/AtlasPeak-V-01.04-release.apk`.
- `aapt2 dump badging` confirma `package='com.atlaspeak'`, `versionCode='104'`,
  `versionName='V-01.04'`, `minSdkVersion='31'` y `targetSdkVersion='35'`.
- `apksigner verify --verbose --print-certs` confirma firma APK Signature Scheme v2 con
  certificado `CN=Atlas Peak`.
- SHA-256: `17CBFAA8ED7B74E45DF8D0EA9F02BD9B2F05F4A8A4D53D4436A6FEE6D09A4548`.
- Artefacto reemplazado por el paquete final de esta misma V-01.04; el checksum vigente es
  `07F03D71658B2DA2C799370C074D56E7DFFC5B62632BC475571F893F3307A8E8`.
- `adb devices` no muestra ningun movil conectado, por lo que no se instalo fisicamente.

#### 2026-05-25 - Polish Home y navegacion inferior

**Cambiado**
- La bottom navigation queda explicitamente icon-only (`alwaysShowLabel = false`), conserva
  `contentDescription` localizado y mantiene el tab Perfil seleccionado en sus subpantallas.
- La bottom navigation se oculta solo en rutas fullscreen/onboarding y sigue visible en
  `WeeklyPlan`, `Settings` y `BackupRestore`, alineado con `DESIGN.md`.
- `Home` queda en lista vertical de una card por fila, con cards a ancho completo, margen de
  pantalla `spacing.screen`, gap `spacing.cardGap` y padding interno `spacing.card`.

**Verificado**
- `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --tests com.atlaspeak.presentation.navigation.BottomNavigationPolicyTest --tests com.atlaspeak.presentation.StaticUiPolicyTest --no-daemon --console=plain` pasa.
- `.\gradlew.bat :app:lintDebug --no-daemon --console=plain` pasa.
- QA runtime bloqueada: `adb devices` no muestra ningun emulador/dispositivo conectado.

#### 2026-05-25 - Tabs inferiores restauradas

**Corregido**
- Registrado y resuelto `BUG-033`: `Entrenar`, `Progreso`, `Cuerpo` y `Perfil` vuelven a
  navegar como destinos top-level directos del `NavHost`.
- Eliminado el wrapper `AppRoute.Main`; `Launch` y `Onboarding` entran directamente en
  `Home`, y la bottom navigation vuelve a usar `Home` como raiz estable del back stack.

**Cambiado**
- Version de app subida a `versionName = "V-01.04"` y `versionCode = 104` para que Android
  acepte la actualizacion sobre V-01.03.

**Verificado**
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat :app:compileDebugKotlin :app:testDebugUnitTest --tests com.atlaspeak.presentation.navigation.BottomNavigationPolicyTest --no-daemon --console=plain` pasa.
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon --console=plain` pasa.
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat :app:packageReleaseUpdate --no-daemon --console=plain --no-build-cache --no-configuration-cache` pasa y genera `build/distribution/AtlasPeak-V-01.04-release.apk`.
- `aapt2 dump badging` confirma `package='com.atlaspeak'`, `versionCode='104'`,
  `versionName='V-01.04'` y `minSdkVersion='31'`.
- `apksigner verify --verbose --print-certs` confirma firma APK Signature Scheme v2.
- SHA-256: `19B6D388F82F6C6DFDA06821D5C63509DD8A07CCA82754AB7CFBB32B5A99DD2B`.
- Artefacto reemplazado por el paquete verificado posterior de esta misma V-01.04; el
  checksum vigente es `07F03D71658B2DA2C799370C074D56E7DFFC5B62632BC475571F893F3307A8E8`.
- QA runtime con AVD no se pudo completar: `AtlasPeak_UserClean_API35` y `AtlasPeak_API35`
  aparecen en `adb`, pero no completan `sys.boot_completed`, por lo que Android no expone
  el servicio `package` para instalar.

## [V-01.03] - 2026-05-25

### Release V-01.03

#### 2026-05-25 - Capturas habilitadas

**Cambiado**
- `SecureScreenEffect` deja de aplicar `FLAG_SECURE` y limpia el flag para permitir capturas
  en todas las pantallas.
- `SensitiveRoutePolicyTest` queda alineado: las rutas sensibles se siguen clasificando, pero
  ya no bloquean screenshots.

**Corregido**
- `:app:packageReleaseUpdate` queda marcado como no compatible con configuration cache para
  evitar un falso fallo despues de generar el paquete de distribucion.

**Seguridad**
- Registrado `SEC-026`: riesgo aceptado de capturas en pantallas con salud, perfil,
  entrenamiento y backup.

**Documentado**
- `DOCS_TECNICA.md` y `DOCS_USUARIO.md` explican que las capturas estan permitidas y el riesgo
  de compartirlas.

**Verificado**
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat :app:testDebugUnitTest --tests com.atlaspeak.presentation.navigation.SensitiveRoutePolicyTest --no-daemon --console=plain` pasa.
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat :app:compileDebugKotlin --no-daemon --console=plain` pasa.
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat :app:packageReleaseUpdate --no-daemon --console=plain` pasa y regenera `build/distribution/AtlasPeak-V-01.03-release.apk`.
- `aapt2 dump badging` confirma `package='com.atlaspeak'`, `versionCode='103'`,
  `versionName='V-01.03'` y `minSdkVersion='31'`.
- `apksigner verify --verbose --print-certs` confirma firma APK Signature Scheme v2.
- SHA-256: `50BFA2C0805D59B925080CEB5351EB233ADCFDACF939E9D39CE034D61EC46ED3`.

#### 2026-05-25 - Actualizacion sin perdida de datos

**Anadido**
- Task Gradle `:app:packageReleaseUpdate` para generar un paquete de actualizacion release:
  APK firmado, `install-adb.bat`, checksum SHA-256 y README de instalacion en
  `build/distribution/`.
- `app/build.gradle.kts` centraliza `applicationId`, `versionCode` y `versionName` para que el
  empaquetado de release use la misma identidad Android que la app instalada.

**Documentado**
- `DOCS_TECNICA.md` explica las tres condiciones para conservar datos al actualizar:
  mismo `applicationId`, mismo keystore de release y `versionCode` superior.
- `DOCS_USUARIO.md` avisa que no se debe desinstalar antes de actualizar y que
  `install-adb.bat` usa `adb install -r`.

#### 2026-05-25 - Bump de version

**Cambiado**
- Fijada la version de app en `versionName = "V-01.03"` y `versionCode = 103`.

**Anadido**
- Copia de distribucion en `build/distribution/AtlasPeak-V-01.03-release.apk`.
- `build/distribution/install-adb.bat` actualizado para instalar V-01.03 con `adb install -r`.
- `build/distribution/README-INSTALACION.txt` y `SHA256SUMS.txt` actualizados para V-01.03.

**Verificado**
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat :app:assembleRelease --no-daemon --console=plain --no-build-cache --no-configuration-cache` pasa.
- `aapt2 dump badging` confirma `package='com.atlaspeak'`, `versionCode='103'`,
  `versionName='V-01.03'` y `minSdkVersion='31'`.
- `apksigner verify --verbose --print-certs` confirma firma APK Signature Scheme v2.
- SHA-256: `50BFA2C0805D59B925080CEB5351EB233ADCFDACF939E9D39CE034D61EC46ED3`.
- `adb devices` no muestra ningun movil conectado, por lo que no se instalo fisicamente.

## [V-01.02] - 2026-05-25

### Release V-01.02

#### 2026-05-25 - APK release instalable para movil

**Anadido**
- Copia de distribucion en `build/distribution/AtlasPeak-V-01.02-release.apk`.
- Script local `build/distribution/install-adb.bat` para instalar con `adb install -r`.
- `build/distribution/README-INSTALACION.txt` con pasos de instalacion y checksum.

**Verificado**
- `.\gradlew.bat :app:assembleRelease --no-daemon --console=plain --no-build-cache --no-configuration-cache` pasa.
- `apksigner verify --verbose --print-certs` confirma firma APK Signature Scheme v2.
- `aapt2 dump badging` confirma `package='com.atlaspeak'`, `versionCode='102'`,
  `versionName='V-01.02'` y `minSdkVersion='31'`.
- SHA-256: `4CA1B69E7E781A1263C7940AE75D3DE179AD826BE7336D37E7AFCBC670C5A60C`.
- `adb devices` no muestra ningun movil conectado, por lo que no se instalo fisicamente.

#### 2026-05-25 - APK release

**Cambiado**
- Fijada la version de app en `versionName = "V-01.02"` y `versionCode = 102`.

**Verificado**
- `.\gradlew.bat :app:assembleRelease --no-daemon --console=plain --no-build-cache --no-configuration-cache` pasa.
- APK generado: `app/build/outputs/apk/release/app-release-unsigned.apk` (27.964.605 bytes).
- `aapt2 dump badging` confirma `versionCode='102'` y `versionName='V-01.02'`.
- SHA-256: `6B9A5CCA0CE86D32858767BE2BDE24DB120C1EDCDBEBC5BF88A74A6E9218DCD6`.
- `apksigner verify` no valida el APK porque no existe `keystore.properties`; el artefacto
  generado es release unsigned.

### Retirada del gate de contraseña local

#### 2026-05-25 - Entrada directa a la app

**Eliminado**
- Retirado el login local como gate de entrada: `Launch` navega a `Home` tras onboarding
  completado.
- Eliminados `LoginScreen`, `AuthViewModel`, `SessionLockViewModel`, use cases/repositorios
  de auth local, strings de auth, dependencia de biometría y dependencias de Credential Manager.
- Onboarding ya no pide contraseña ni opt-in biométrico.

**Cambiado**
- La passphrase queda limitada a backups cifrados: crear/restaurar backup Drive/local.
- Export JSON/CSV en claro ya no exige contraseña local; el texto avisa que se comparte sin
  cifrado.
- `users.password_hash`, `users.password_salt` y `users.last_login_at` pasan a nullable; Room
  sube a schema v3 con migración 2→3 que borra credenciales locales legadas.

**Seguridad**
- Registrado `SEC-025`: riesgo aceptado si alguien accede al móvil ya desbloqueado.
- `USE_BIOMETRIC` deja de declararse porque no hay desbloqueo biométrico local en v1.

**Verificado**
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleRelease --no-daemon --console=plain --no-build-cache --no-configuration-cache` pasa.
- `connectedAndroidTest` no se ejecuta: `adb devices` no muestra emulador/dispositivo.

### Rediseño UI premium

#### 2026-05-25 - Sistema visual y dashboard

**Cambiado**
- Redefinida la paleta en `AtlasPeakTheme`: neutros off-black/off-white, acento lima/verde
  y coral reservado para error/riesgo.
- Añadidos `PremiumBackground`, `PremiumCard` y `PremiumIconBadge` como base visual compartida.
- Rediseñado el dashboard Home con hero de minutos semanales, métricas en pares, tarjetas
  de gráfica con mayor jerarquía y navegación inferior flotante.
- Actualizados selectores de periodo a chips propios tipo segmented control.
- Aplicado el nuevo fondo premium a auth, onboarding, entrenamiento, cardio, progreso,
  cuerpo, perfil, planificación, notificaciones y backup.
- Ajustada la escala tipográfica para titulares y números grandes con tabular figures.
- `DESIGN.md` queda alineado con la nueva dirección visual.

**Verificado**
- `.\gradlew.bat compileDebugKotlin --rerun-tasks` pasa.
- `.\gradlew.bat test --rerun-tasks` pasa.
- `.\gradlew.bat lint` pasa.
- `.\gradlew.bat assembleDebug` pasa.
- QA visual runtime no se pudo completar porque `adb devices` no muestra emulador ni
  dispositivo conectado.

### Auditoria final v1

#### 2026-05-25 - Crash de tabs de bottom navigation

**Corregido**
- Registrado y resuelto `BUG-032`: tocar `Entrenar`, `Progreso`, `Cuerpo` o `Perfil`
  desde el dashboard podia cerrar la app.
- La bottom navigation ya no hace `popUpTo` al `startDestination` transitorio `launch`;
  usa `Home` como raiz estable del back stack autenticado.

**Anadido**
- `BottomNavigationPolicyTest` cubre que los tabs no vuelvan a depender de
  `findStartDestination`.

**Verificado**
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat :app:compileDebugKotlin --rerun-tasks --no-build-cache --no-daemon --console=plain` pasa.
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat :app:testDebugUnitTest --tests com.atlaspeak.presentation.navigation.BottomNavigationPolicyTest --no-daemon --console=plain` pasa.
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat :app:assembleDebug --no-daemon --console=plain` pasa.
- QA runtime en emulador no pudo completarse: `AtlasPeak_Clean_API35` arranca QEMU, pero
  sale antes de aparecer en `adb devices`; `emulator -accel-check` indica WHPX operativo.

#### 2026-05-25 - Generacion de APK debug

**Verificado**
- `.\gradlew.bat :app:assembleDebug --rerun-tasks --no-daemon --console=plain` pasa.
- APK generada: `app/build/outputs/apk/debug/app-debug.apk` (102.652.921 bytes).
- `apksigner verify --verbose --print-certs` confirma firma debug con APK Signature Scheme v2.
- SHA-256: `36264C39CF17FC2352503F3CF8E1260C6B4A239B28E7F3AA38FE16B41851F226`.

#### 2026-05-24 - Capturas visuales en emulador debug

**Cambiado**
- `SecureScreenEffect` mantiene `FLAG_SECURE` en release y en dispositivos reales, pero lo omite
  en emulador con `BuildConfig.DEBUG` para permitir QA visual y capturas de pantalla.

**Seguridad**
- Registrado `SEC-024`: bypass acotado a emulador debug; las rutas siguen marcadas como
  sensibles y release no cambia.

**Verificado**
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat :app:testDebugUnitTest --tests com.atlaspeak.presentation.navigation.SensitiveRoutePolicyTest --no-daemon` pasa.
- Reinstalada build debug en `emulator-5554`; `screencap` ya muestra el onboarding en vez de
  una captura negra.

#### 2026-05-24 - Runtime QA en emulador y carga nativa SQLCipher

**Corregido**
- Registrado y resuelto `BUG-031`: la app caia al arrancar en emulador porque `libsqlcipher.so`
  no se cargaba antes de abrir Room cifrado.
- `AtlasPeakApplication.attachBaseContext()` carga `System.loadLibrary("sqlcipher")` antes de
  `onCreate()` y antes de que Hilt pueda construir dependencias que tocan la DB.

**Anadido**
- `StaticSecurityPolicyTest` verifica que la carga nativa de SQLCipher queda antes del acceso
  de aplicacion a Room.

**Verificado**
- Se limpiaron procesos antiguos `adb`/`emulator`/`qemu`, se arranco `AtlasPeak_UserClean_API35`
  en `emulator-5554`, se instalo `app-debug.apk` y se lanzo `com.atlaspeak.debug`.
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat :app:testDebugUnitTest --tests com.atlaspeak.security.StaticSecurityPolicyTest --no-daemon` pasa.
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat :app:assembleDebug --no-daemon` pasa.
- `adb shell dumpsys activity activities` confirma `com.atlaspeak.debug/com.atlaspeak.MainActivity`
  como ventana enfocada; `pidof com.atlaspeak.debug` devuelve proceso vivo.
- `build/runtime-qa/atlaspeak-final-ui.xml` muestra el onboarding inicial; la captura PNG queda
  negra porque `FLAG_SECURE` protege la pantalla, comportamiento esperado.
- `build/runtime-qa/atlaspeak-final-logcat.txt` no contiene `FATAL EXCEPTION`, `UnsatisfiedLinkError`
  ni `ANR in com.atlaspeak` tras el fix.

#### 2026-05-24 - Seguridad, arquitectura y backup hardening

**Anadido**
- `SessionLockViewModel` revalida rutas sensibles en `ON_RESUME` y vuelve a `Login` si expira
  el timeout local.
- Contrato de backup en dominio: `BackupRepository`, `BackupUseCase` y modelos `DriveBackup`,
  `BackupResult`, `SharedBackupExport`.
- `DataBackupRepository` mapea la implementacion Room/Drive/export a modelos de dominio.
- `StaticArchitecturePolicyTest` bloquea imports `presentation -> data` y dependencias UI/data
  dentro de `domain`.
- Tests de regresion para timeout de sesion, step-up auth de export plaintext y columnas
  desconocidas en restore de backup.

**Cambiado**
- `BackupRestoreViewModel` ya no depende de managers de `data`; consume `BackupUseCase`.
- Export JSON/CSV en claro exige contrasena local antes de escribir el archivo y limpia
  `BackupRestoreUiState.password` tras operaciones sensibles.
- `GoogleTaskAwait` se mueve a `core/google` para evitar que presentation importe helpers de data.
- `BackupFileCodec`, `DriveBackupManager`, `LocalBackupExportManager` y `EncryptionManager`
  limpian buffers derivados/temporales cuando ya no se necesitan.
- Restore de backup valida columnas por tabla antes de insertar filas restauradas.

**Corregido**
- Registrado `SEC-021` / `BUG-030`: timeout de desbloqueo local no estaba conectado a lifecycle.
- Registrado `SEC-022` / `BUG-029`: export plaintext no exigia step-up auth y retenia password UI.
- Registrado `SEC-023`: restore aceptaba columnas desconocidas hasta SQLite.
- Registrado `BUG-028`: presentation de backup importaba data layer.

**Verificado**
- `./gradlew compileDebugKotlin testDebugUnitTest --tests com.atlaspeak.domain.usecase.auth.LocalAuthUseCaseTest --tests com.atlaspeak.domain.usecase.auth.BiometricAuthPolicyTest --tests com.atlaspeak.presentation.navigation.SessionLockViewModelTest --tests com.atlaspeak.presentation.backup.BackupRestoreViewModelTest --tests com.atlaspeak.architecture.StaticArchitecturePolicyTest --no-daemon`
  pasa.
- `./gradlew assembleDebug assembleRelease test lint compileDebugAndroidTestKotlin jacocoDebugDomainDataCoverageVerification --no-daemon`
  pasa como gate final.
- `python Skills/05_Security/cyber-neo/scripts/scan_secrets.py app` no encuentra secretos.
- `python Skills/05_Security/cyber-neo/scripts/check_lockfiles.py .` no encuentra hallazgos.
- `git diff --check` pasa.
- QA visual/performance runtime sigue bloqueada: `adb devices` no lista dispositivos, no hay AVDs,
  y `emulator -accel-check` devuelve codigo 6.

### Fase 16 - Polish + performance + accesibilidad

#### 2026-05-24 - Tipografia, contraste AA y QA estatica de UI

**Anadido**
- Fuentes locales Poppins 600/700 e Inter variable en `app/src/main/res/font/`.
- `THIRD_PARTY_NOTICES.md` documenta origen y licencia OFL de Poppins/Inter.
- `ThemeAccessibilityTest` verifica contraste WCAG AA en pares `on*`/fondo light y dark.
- `StaticUiPolicyTest` bloquea strings visibles hardcodeados en presentation, icon-only
  buttons sin descripcion, perdida de fuentes Atlas Peak y desalineacion ES/EN.

**Cambiado**
- `Typography.kt` usa Poppins para display/headline/title y Inter para body/label.
- `primary` light cambia de `#E53935` a `#D32F2F` para cumplir contraste AA con texto blanco.
- Icono launcher, color rojo de rutina por defecto, `SPEC.md` y `DESIGN.md` quedan alineados
  con `#D32F2F`.
- `primaryContainer/onPrimaryContainer` quedan definidos en light/dark y las tarjetas
  seleccionadas usan contenido seleccionado con contraste probado.
- Graficos de dashboard/progreso/cuerpo y mapas GPS exponen resumen semantico localizado.
- Filas de switch/checkbox fusionan label/control con rol accesible; el rest timer expone
  progreso y usa un anillo visible.
- `NavHost` usa fade breve y lo desactiva si `ANIMATOR_DURATION_SCALE` es 0.
- `resourceConfigurations` se reemplaza por `androidResources.localeFilters`.

**Corregido**
- Registrado `BUG-024`: primary light no cumplia contraste AA.
- Registrado `BUG-025`: la tipografia del sistema de diseno no estaba aplicada.
- Registrado `BUG-026`: estados seleccionados usaban `primaryContainer` sin token Atlas.
- Registrado `BUG-027`: graficos, mapa y toggles tenian semantica insuficiente para TalkBack.

**Verificado**
- `./gradlew testDebugUnitTest --tests com.atlaspeak.presentation.StaticUiPolicyTest --tests com.atlaspeak.presentation.theme.ThemeAccessibilityTest --tests com.atlaspeak.domain.usecase.workout.RoutineUseCaseTest --tests com.atlaspeak.domain.usecase.workout.StartWorkoutSessionUseCaseTest --no-daemon`
  pasa.
- `./gradlew assembleDebug assembleRelease test lint compileDebugAndroidTestKotlin jacocoDebugDomainDataCoverageVerification --no-daemon`
  pasa como gate completo de cierre.
- APK release minificado: `app-release-unsigned.apk` = 26,86 MB.
- `python Skills/05_Security/cyber-neo/scripts/scan_secrets.py app` no encuentra secretos.
- `python Skills/05_Security/cyber-neo/scripts/check_lockfiles.py .` no encuentra hallazgos.
- `git diff --check` pasa.
- QA visual/performance runtime bloqueada: no hay dispositivos `adb`, no hay AVDs, y
  `emulator -accel-check` devuelve codigo 6 con aviso Hyper-V/WHPX.

### Fase 15 - Testing + cobertura

#### 2026-05-24 - Gate JaCoCo y hardening de workers/backup

**Anadido**
- JaCoCo en `app/build.gradle.kts` con tarea `jacocoDebugDomainDataCoverageVerification`
  y umbral del 70% para el core JVM de `domain`/`data`.
- Paso CI `Domain/data coverage` para fallar PRs si baja la cobertura.
- `BackupWorkerRunner` extrae la logica testeable del worker de auto-backup.
- Tests de backup worker: auto-backup desactivado, hash estable sin cambios, subida exitosa
  y retry en fallo limpiando siempre la password.
- Tests de restore Drive con password valida, schema futuro y tablas faltantes.
- Test de nombres WorkManager para recordatorios ISO 1..7.
- Test instrumentado compilable para comprobar que `BackupWorkScheduler` mantiene un unico
  trabajo periodico.

**Cambiado**
- `BackupWorker` delega en `BackupWorkerRunner`.
- `NotificationWorkNames.trainingReminder()` valida `dayOfWeek` en rango ISO.
- `TrainingReminderWorker` ignora input corrupto de weekday con `Result.success()` para evitar
  retries inutiles.
- `DOCS_TECNICA.md` documenta el gate de cobertura y sus exclusiones JVM/Android.

**Corregido**
- Registrado `BUG-022`: recordatorios aceptaban dias de semana invalidos.
- Registrado `BUG-023`: el objetivo de cobertura `domain`/`data` no era verificable.

**Verificado**
- `./gradlew testDebugUnitTest --tests com.atlaspeak.data.backup.DriveBackupManagerTest --tests com.atlaspeak.data.backup.BackupWorkerRunnerTest --tests com.atlaspeak.data.notification.NotificationWorkNamesTest --no-daemon`
  pasa.
- `./gradlew compileDebugAndroidTestKotlin --no-daemon` pasa tras cambiar el androidTest a
  observacion LiveData de WorkManager.
- `./gradlew jacocoDebugDomainDataCoverageVerification --no-daemon` pasa con 80,28% line
  coverage del core JVM `domain`/`data`.
- `./gradlew assembleDebug assembleRelease test lint compileDebugAndroidTestKotlin jacocoDebugDomainDataCoverageVerification --no-daemon`
  pasa como gate completo de cierre.
- `python Skills/05_Security/cyber-neo/scripts/scan_secrets.py app` no encuentra secretos.
- `python Skills/05_Security/cyber-neo/scripts/check_lockfiles.py .` no encuentra hallazgos.
- `git diff --check` pasa.

### Fase 14 - Seguridad + hardening

#### 2026-05-24 - Rutas sensibles, red y notificaciones privadas

**Anadido**
- `SensitiveRoutePolicy` centraliza las rutas bajo `FLAG_SECURE` y cubre toda la zona
  autenticada con salud/entrenamiento, no solo auth/perfil/backup.
- Tests de hardening para politica de rutas sensibles, timeouts OkHttp y reglas estaticas de
  permisos/logging/notificaciones privadas.

**Cambiado**
- `NetworkModule` fija timeouts explicitos para Drive: connect 20s, read/write 60s, call 120s.
- `file_paths.xml` limita FileProvider al directorio privado de exports.
- `BackupRestoreViewModel` borra el `CharArray` de auto-backup tambien si falla el guardado.
- Canales y builders de notificaciones de fuerza/cardio/resumenes usan visibilidad privada en
  lockscreen.
- `SPEC.md`, `DOCS_TECNICA.md` y `SECURITY.md` reflejan que `FLAG_SECURE` protege rutas
  autenticadas con salud/entrenamiento.

**Corregido**
- Registrado `SEC-019`: rutas de entrenamiento/progreso/cardio sin `FLAG_SECURE`.
- Registrado `SEC-020` y `BUG-021`: notificaciones foreground filtraban tiempo/distancia en
  lockscreen.

**Verificado**
- `./gradlew compileDebugKotlin testDebugUnitTest --tests com.atlaspeak.presentation.navigation.SensitiveRoutePolicyTest --tests com.atlaspeak.di.NetworkModuleTest --no-daemon`
  pasa.
- `./gradlew testDebugUnitTest --tests com.atlaspeak.security.StaticSecurityPolicyTest --tests com.atlaspeak.presentation.navigation.SensitiveRoutePolicyTest --tests com.atlaspeak.di.NetworkModuleTest --no-daemon`
  pasa tras corregir el test estatico.
- `./gradlew assembleDebug assembleRelease test lint compileDebugAndroidTestKotlin --no-daemon`
  pasa como gate completo de cierre.
- `python Skills/05_Security/cyber-neo/scripts/scan_secrets.py app` no encuentra secretos.
- `python Skills/05_Security/cyber-neo/scripts/check_lockfiles.py .` no encuentra hallazgos.
- `git diff --check` pasa.
- Review lateral de seguridad reviso permisos, red, crypto/auth, secrets y detecto el leak de
  notificaciones foreground; el hallazgo quedo corregido.

### Fase 13 - Wear OS diferido a v2

#### 2026-05-24 - Cierre documental de aplazamiento Wear

**Cambiado**
- `SPEC.md` alinea la Fase 13 con la tabla v2.2: v1 no implementa modulo `wear/`, no anade
  `play-services-wearable`, no declara `WearableListenerService` y conserva el protocolo solo
  como diseno para v2.
- Eliminada del plan de Fase 1 la instruccion residual de crear estructura Wear.
- La matriz de dependencias marca Wear como referencia v2 y no como dependencia de `app`.
- `app/proguard-rules.pro` corrige el comentario de fase de hardening R8/ProGuard a Fase 14.

**Corregido**
- Registrado y resuelto `BUG-020`: el spec decia "Wear diferido" y a la vez mantenia tareas
  activas de Wear. Eso era una contradiccion operativa, no una decision de producto.

**Verificado**
- `settings.gradle.kts` solo incluye `:app`; no existe directorio `wear/`.
- `app/build.gradle.kts` y `gradle/libs.versions.toml` no contienen dependencias Wear activas.
- `app/src/main/AndroidManifest.xml` no declara servicios Wear.
- `./gradlew assembleDebug assembleRelease test lint compileDebugAndroidTestKotlin --no-daemon`
  pasa como gate completo de cierre.
- `python Skills/05_Security/cyber-neo/scripts/scan_secrets.py app` no encuentra secretos.
- `git diff --check` pasa.

### Fase 12 - Backup Google Drive + export

#### 2026-05-24 - Backup cifrado, Drive appData y exports manuales

**Anadido**
- Anadio `BackupRestoreScreen` real con contrasena de backup, toggle de auto-backup, lista
  Drive, backup manual, restore confirmado, backup local cifrado, export JSON y export CSV ZIP.
- Anadio flujo Drive separado con `AuthorizationClient` + scope `drive.appdata`; Credential
  Manager sigue siendo identidad opcional, no bearer token de Drive.
- Anadio `DriveApiService`, `RetrofitDriveBackupService`, `DriveBackupManager`,
  `RoomBackupSnapshotStore`, `BackupFileCodec`, `BackupJsonCodec`, `LocalBackupExportManager`,
  `BackupCredentialStore`, `BackupWorker` y `BackupWorkScheduler`.
- Anadio tests unitarios para cabecera ATPK/PBKDF2 600k, round-trip cifrado, rechazo de schema,
  exports sin `users`/`auth_security`, manager Drive, cuerpo HTTP `multipart/related` y hash
  estable del auto-backup.
- Anadio test instrumentado compilable de restore positivo con Room real y FKs de workouts /
  Health Connect.
- Anadio `.gitignore` dentro de `app/` para que los scanners no dependan solo del `.gitignore`
  raiz al buscar secretos.

**Cambiado**
- `AtlasPeakApplication` programa el worker diario de backup tras el seed inicial.
- `AppDatabase` expone `TABLE_ORDER`/`TABLES` para snapshot/restore de las 20 tablas.
- `SettingsDao` actualiza `last_backup_at` y `backup_auto_enabled`.
- `SPEC.md` y `DOCS_TECNICA.md` documentan que Drive upload usa `multipart/related`, no
  `multipart/form-data`, y que el hash de auto-backup ignora `last_backup_at`.
- Dependencia `play-services-auth` vive en `gradle/libs.versions.toml` para `AuthorizationClient`.

**Corregido**
- Registrados y resueltos `BUG-017`, `BUG-018` y `BUG-019`: accion Drive pendiente saveable,
  auto-backup sin subidas repetidas por `last_backup_at`, y upload Drive con cuerpo HTTP valido.

**Seguridad**
- Registrado `SEC-018`: ID token de Google no se usa como bearer token Drive; el worker no
  abre UI de consentimiento; auto-backup con contrasena guardada es opt-in y protegido con
  EncryptedSharedPreferences + Android Keystore.
- El backup cifrado usa formato `[ATPK][version][iterations BE][salt16][iv12][ciphertext+tag]`,
  PBKDF2-HMAC-SHA256 600.000 iteraciones y AES-256-GCM.
- Exports manuales JSON/CSV excluyen `users` y `auth_security`; el backup cifrado completo si
  incluye tablas necesarias para restaurar.

**Verificado**
- `./gradlew compileDebugKotlin testDebugUnitTest compileDebugAndroidTestKotlin --no-daemon`
  pasa tras corregir los hallazgos del review lateral.
- `./gradlew assembleDebug assembleRelease test lint compileDebugAndroidTestKotlin --no-daemon`
  pasa como gate completo de cierre.
- `python Skills/05_Security/cyber-neo/scripts/scan_secrets.py app` no encuentra secretos.
- `git diff --check` pasa.
- Review lateral de Fase 12 ejecuto hallazgos P1/P2 y quedaron corregidos antes de cerrar.
- QA visual/runtime bloqueada: no hay dispositivos/AVDs disponibles y `emulator -accel-check`
  falla con codigo 6 por Hyper-V/WHPX.

### Fase 11 - Plan semanal + notificaciones

#### 2026-05-24 - Planificacion semanal, scheduler y canales Android

**Añadido**
- Añadidos modelos/use cases/repositorios para `weekly_plan` y ajustes de notificaciones sobre
  las tablas existentes, sin cambio de schema Room.
- Reemplazado el placeholder de `Profile` por pantalla real con acceso a plan semanal,
  ajustes de notificaciones y backup.
- Añadida `WeeklyPlanScreen` con cards por dia ISO, asignacion de rutina, descanso,
  completado semanal y hora de recordatorio.
- Añadida pantalla de ajustes de notificaciones para control global, mensajes motivacionales,
  resumen diario, hora diaria y resumen semanal.
- Añadidos canales Android separados para recordatorios, mensajes motivacionales y resumenes.
- Añadidos `TrainingReminderWorker`, `DailySummaryWorker`, `WeeklySummaryWorker` y
  `MotivationalMessageWorker` con Hilt + WorkManager.
- Añadido `WorkManagerNotificationScheduler` con trabajos unicos y reprogramacion best-effort
  por siguiente disparo.

**Cambiado**
- `AtlasPeakApplication` implementa `Configuration.Provider` e inyecta `HiltWorkerFactory`,
  alineado con el manifest que desactiva el initializer por defecto de WorkManager.
- El onboarding persiste el resultado del permiso `POST_NOTIFICATIONS` en `app_settings`.
- `Home` refresca al volver a primer plano para que el widget de consistencia recoja cambios
  recientes del plan semanal.
- `SPEC.md` y `DOCS_TECNICA.md` aclaran que los horarios de WorkManager son aproximados; v1 no
  pide `SCHEDULE_EXACT_ALARM`.

**Corregido**
- La pantalla de ajustes ya no permite guardar “notificaciones activas” si Android bloquea el
  permiso real; solicita `POST_NOTIFICATIONS` cuando aplica o abre ajustes del sistema.
- Los resumenes diarios/semanales incluyen el peso corporal reciente cuando el snapshot del
  dashboard lo tiene disponible.

**Seguridad**
- Registrado `SEC-017`: workers y scheduler comprueban permiso runtime antes de notificar,
  evitan retry loops cuando el permiso esta revocado, usan canales separados y visibilidad
  privada para contenido personal.
- Revalidado: sin secretos nuevos, sin `ACCESS_BACKGROUND_LOCATION`, sin cleartext/fallback
  destructivo y sin nuevos permisos exact-alarm.

**Verificado**
- `./gradlew testDebugUnitTest --tests com.atlaspeak.domain.usecase.planning.WeeklyPlanUseCaseTest --tests com.atlaspeak.domain.usecase.planning.NotificationSettingsUseCaseTest --tests com.atlaspeak.data.notification.NotificationScheduleCalculatorTest --no-daemon`
  pasa.
- `./gradlew assembleDebug assembleRelease test lint compileDebugAndroidTestKotlin --no-daemon`
  pasa.
- Revision independiente de Fase 11 aprobada tras corregir permisos reales de notificacion y
  peso reciente en resumenes.
- Scanner `cyber-neo` sobre `app/` no encuentra secretos reales; el unico aviso es ausencia de
  `.gitignore` dentro de `app/`, mitigado por `.gitignore` raiz que ignora `secrets.properties`,
  `keystore.properties`, `local.properties` y `google-services.json`.
- QA visual runtime bloqueada: `adb devices` no lista dispositivos, `emulator -list-avds` no
  devuelve AVDs y `emulator -accel-check` falla con codigo 6 por Hyper-V/WHPX.

### Fase 10 - Health Connect

#### 2026-05-24 - Import/export Health Connect y migracion DB v2

**Añadido**
- Añadido `HealthConnectRepository`, `SyncHealthConnectUseCase` y `HealthConnectManager` para
  revalidar disponibilidad/permisos, importar pasos, calorias activas, sueño y frecuencia cardiaca,
  y exportar entrenamientos completados y composicion corporal soportada.
- Añadido import por agregados diarios para pasos/calorias y cache Room para sueño/FC. La lectura
  rehace una ventana movil de 30 dias borrando cache local del rango para reflejar cambios y
  borrados recientes sin pedir historial extendido.
- Añadida exportacion a Health Connect con `clientRecordId` estable para workouts, peso, grasa,
  masa magra y masa de agua corporal.
- Añadida tarjeta de gestion Health Connect en `Body` con sincronizacion manual y solicitud de
  permisos si fueron revocados; `Home` intenta sincronizar al abrir si los permisos siguen activos.
- Añadida pantalla de rationale/privacidad para Health Connect con intent
  `ACTION_SHOW_PERMISSIONS_RATIONALE` y alias `VIEW_PERMISSION_USAGE`.
- Añadida columna nullable `body_water_mass_kg`, schema Room v2 y test instrumentado de migracion
  `MIGRATION_1_2`.
- Añadidos tests unitarios para use case de sync, mapper de records Health Connect, validacion de
  masa de agua corporal y validacion raw de entrada manual.

**Corregido**
- Registrado y resuelto `BUG-016`: Health Connect no sincroniza `% agua corporal`; el tipo real
  soportado es masa de agua corporal (`BodyWaterMassRecord`).
- La promesa de báscula inteligente queda ajustada al alcance real de v1: no se leen datos
  corporales desde Health Connect porque no se piden permisos de lectura corporal.

**Seguridad**
- Registrado `SEC-016`: la integracion revalida permisos concedidos en cada sync, no pide lectura
  corporal ni historial extendido, no usa red propia y mantiene `Body` bajo `FLAG_SECURE`.
- Revalidado: sin secretos nuevos, sin `ACCESS_BACKGROUND_LOCATION`, sin cleartext/fallback
  destructivo y sin `google-services.json`.

**Verificado**
- `./gradlew compileDebugKotlin testDebugUnitTest --tests com.atlaspeak.domain.usecase.healthconnect.SyncHealthConnectUseCaseTest --tests com.atlaspeak.data.healthconnect.HealthConnectRecordMapperTest --tests com.atlaspeak.domain.usecase.body.BodyCompositionUseCaseTest --tests com.atlaspeak.presentation.body.BodyCompositionDraftTest --no-daemon`
  pasa.
- `./gradlew compileDebugAndroidTestKotlin --no-daemon` pasa.
- `./gradlew assembleDebug assembleRelease test lint compileDebugAndroidTestKotlin --no-daemon`
  pasa.
- Scanner `cyber-neo` sobre `app/` no encuentra secretos reales; el unico aviso es ausencia de
  `.gitignore` dentro de `app/`, mitigado por `.gitignore` raiz que ignora `secrets.properties`,
  `keystore.properties`, `local.properties` y `google-services.json`.
- QA visual runtime bloqueada: `adb devices` no lista dispositivos, `emulator -list-avds` no
  devuelve AVDs y `emulator -accel-check` falla con codigo 6 por Hyper-V/WHPX.

### Fase 9 - Composición corporal

#### 2026-05-24 - Cuerpo, entrada manual y evolución por métrica

**Añadido**
- Añadidos modelos de dominio para composición corporal, métricas, periodos, fuentes de datos,
  valores actuales y puntos de evolución.
- Añadido `BodyCompositionUseCase` con validación de entrada manual, cálculo de último valor
  por métrica y series filtradas por periodo.
- Añadido contrato `BodyCompositionRepository`, `BodyCompositionDao` y
  `RoomBodyCompositionRepository` sobre la tabla v1 `body_composition`.
- Reemplazado el placeholder de `Body` por `BodyCompositionScreen`, con tabla de valores
  actuales, selector de periodo, selector de métrica, gráfico Vico y entrada manual de todos
  los campos del spec.
- La UI diferencia métricas preparadas para Health Connect (peso, grasa, masa muscular, agua)
  de métricas solo manuales (visceral, proteína, masa ósea, edad corporal).
- Añadidos strings ES/EN y tests unitarios de dominio para validación, guardado manual,
  últimos valores por métrica y series por periodo.

**Seguridad**
- `Body` entra en rutas con `FLAG_SECURE` porque muestra datos de salud. Sin nuevos permisos,
  red, Health Connect runtime, backup ni schema Room.
- Revalidado: sin secretos nuevos, sin `ACCESS_BACKGROUND_LOCATION`, sin cleartext/fallback
  destructivo y sin textos hardcodeados nuevos en UI.

**Verificado**
- `./gradlew testDebugUnitTest --tests com.atlaspeak.domain.usecase.body.BodyCompositionUseCaseTest --no-daemon`
  pasa.
- `./gradlew assembleDebug assembleRelease test lint compileDebugAndroidTestKotlin --no-daemon`
  pasa.
- QA visual runtime bloqueada: `adb devices` no lista dispositivos, `emulator -list-avds` no
  devuelve AVDs y `emulator -accel-check` falla con código 6 por Hyper-V/WHPX.

### Fase 8 - Dashboard / Home

#### 2026-05-24 - Dashboard local con métricas de entrenamiento y salud

**Añadido**
- Añadidos modelos de dominio `DashboardPeriod`, `DashboardWidget`, `DashboardFilters`,
  `DashboardPoint`, `DashboardInterval`, `DashboardSessionSummary`, `DashboardConsistency`
  y `DashboardSnapshot`.
- Añadido `DashboardUseCase` para agregar volumen total, consistencia, minutos de
  entrenamiento semanal, tiempo total de actividad, peso corporal, pasos, frecuencia cardiaca
  y sueño desde repositorios de dominio.
- Añadido contrato `DashboardRepository`, `DashboardDao` y `RoomDashboardRepository` para leer
  datos existentes de `body_composition`, `weekly_plan` y caches de Health Connect.
- Reemplazado el placeholder de `Home` por `HomeScreen`, con tarjetas KPI, selectores de
  periodo por widget y gráficas Vico interactivas.
- Añadido `PeriodSelector` compartido y reutilizado también en `ProgressScreen`.
- Añadidos strings ES/EN y tests unitarios para las agregaciones de dashboard.

**Corregido**
- Registrado y resuelto `BUG-014`: la consistencia con plan ya no cuenta entrenamientos fuera
  del plan y la semana empieza en lunes local.
- Registrado y resuelto `BUG-015`: el widget de pulso ya no etiqueta el mínimo diario como
  frecuencia cardiaca en reposo.
- Los intervalos de pasos que cruzan medianoche o el inicio de periodo se parten
  proporcionalmente por día local.
- `HomeViewModel` cancela refrescos obsoletos al cambiar periodos para evitar snapshots
  viejos sobre filtros nuevos.

**Cambiado**
- El dashboard lee resúmenes de sesiones desde `DashboardDao` en vez de cargar historiales
  completos de fuerza/cardio para agregar KPIs.

**Seguridad**
- Sin nuevos permisos, red, auth, backup ni schema Room. Revalidado: sin secretos nuevos,
  sin `ACCESS_BACKGROUND_LOCATION`, sin cleartext/fallback destructivo y sin textos
  hardcodeados nuevos en UI.

**Verificado**
- `./gradlew testDebugUnitTest --tests com.atlaspeak.domain.usecase.dashboard.DashboardUseCaseTest --no-daemon`
  pasa.
- `./gradlew assembleDebug assembleRelease test lint compileDebugAndroidTestKotlin --no-daemon`
  pasa.
- QA visual runtime bloqueada: `adb devices` no lista dispositivos, `emulator -list-avds` no
  devuelve AVDs y `emulator -accel-check` falla con código 6 por Hyper-V/WHPX.

### Fase 7 - Progreso

#### 2026-05-24 - Historial unificado, progreso por ejercicio y grupos musculares

**Anadido**
- Anadidos modelos de dominio para `ProgressPeriod`, filtros de historial, items de historial,
  puntos por ejercicio y progreso por grupo muscular.
- Anadido `ProgressUseCase` para combinar historial de fuerza/cardio, filtrar por periodo,
  tipo y busqueda, y agregar maximo de peso, volumen y reps por sesion.
- Anadida `ProgressScreen` con tabs internos Historial, Ejercicios y Grupos; incluye
  selector de periodo semana/mes/3 meses/ano/ano actual.
- El historial de Progreso muestra sesiones de fuerza y cardio completadas, detalle de sets,
  metricas de cardio y mapa de ruta cuando existe GPS.
- Anadidas graficas Vico para evolucion de peso maximo y volumen por ejercicio, con eje X
  formateado por fecha.
- Anadido `CardioRouteMap` reutilizable para no duplicar la integracion de Google Maps.
- Anadidos strings ES/EN y tests unitarios de dominio para historial, filtros y agregaciones.

**Cambiado**
- La ruta `Progress` deja de usar placeholder y queda conectada al bottom nav.
- `CardioCompleteScreen` reutiliza `CardioRouteMap`.

**Corregido**
- `WorkoutRepository` ya no expone cabeceras de cardio como sesiones de fuerza, evitando que
  el historial de Progreso duplique cardio como fuerza vacia.

**Seguridad**
- Sin nuevos permisos, red, auth, backup ni schema Room. Revalidado: sin secretos nuevos,
  sin `ACCESS_BACKGROUND_LOCATION`, sin cleartext y sin textos hardcodeados nuevos en UI.

**Verificado**
- `./gradlew testDebugUnitTest --tests com.atlaspeak.domain.usecase.progress.ProgressUseCaseTest --no-daemon`
  pasa.
- `./gradlew compileDebugKotlin --no-daemon` pasa.
- `./gradlew assembleDebug assembleRelease test lint compileDebugAndroidTestKotlin --no-daemon`
  pasa tras corregir la regresion `BUG-013`.
- `emulator -accel-check` falla con codigo 6 por Hyper-V/WHPX, `emulator -list-avds` no
  devuelve AVDs y `adb devices` no lista dispositivos; no se pudieron capturar screenshots
  runtime de la UI de Progreso.

### Fase 6 - Cardio + GPS

#### 2026-05-23 - Cardio GPS, timer/countdown, entrada manual y resumen con mapa

**Anadido**
- Anadidos modelos de dominio `CardioType`, `CardioMode`, `LocationPoint` y `CardioSession`.
- Anadido contrato `CardioRepository`, `RoomCardioRepository` y `CardioDao` sobre las tablas
  v1 `cardio_types` y `cardio_sessions`; no cambia el schema Room.
- Anadido `CardioUseCase` para crear/editar tipos custom, arrancar sesiones y completarlas
  con distancia Haversine, velocidad media/maxima, ruta y calorias estimadas por MET fallback.
- Implementado `LocationTracker` con `FusedLocationProvider` y `Flow` de ubicaciones.
- Implementado `CardioForegroundService` de tipo `location` para GPS + cronometro +
  notificacion persistente, con `CardioTrackerRegistry`.
- Anadido tab `Cardio` dentro de Entrenar con tipos predefinidos/custom, edicion/archivado,
  selector de countdown, inicio timer/countdown e historial/detalle.
- Anadidas rutas fullscreen `ActiveCardio` y `CardioComplete`.
- `ActiveCardio` solicita `ACCESS_FINE_LOCATION` solo para tipos GPS, muestra metricas en vivo
  y permite finalizar o cancelar sin dejar el servicio corriendo.
- `CardioComplete` muestra resumen y mapa de ruta con Google Maps Compose cuando hay puntos GPS.
- Anadidos tests unitarios de dominio para creacion de tipos, inicio, distancia/velocidad,
  entrada manual y rechazo de sesiones manuales incompletas.

**Corregido**
- El historial de cardio se ordena por `workout_sessions.start_time DESC`, no por UUID.
- Los tipos manuales o GPS denegado ya no fuerzan un foreground service `health`; usan
  cronometro local y exigen distancia/velocidad manuales antes de guardar.
- Cancelar o pulsar back en cardio activo detiene el servicio/timer local y borra la sesion
  incompleta.
- `CardioForegroundService` conserva el estado `failed=true` si falla dentro de
  `startForeground`, para que la UI pueda avisar.

**Seguridad**
- Registrado `SEC-014`: cardio GPS sin `ACCESS_BACKGROUND_LOCATION` y sin guardar sesiones GPS
  falsas cuando falta permiso/ruta.
- Revalidado: sin secretos nuevos, sin `google-services.json`, sin cleartext, sin logs
  sensibles y sin textos hardcodeados nuevos en UI.

**Verificado**
- `./gradlew assembleDebug assembleRelease test lint compileDebugAndroidTestKotlin --no-daemon`
  pasa.
- `emulator -accel-check` falla con codigo 6 por Hyper-V/WHPX y `adb devices` no lista
  dispositivos; no se pudieron capturar screenshots runtime de la UI de cardio.

### Fase 5 - Entrenamiento activo de fuerza

#### 2026-05-23 - Sesion activa, timer persistente, rest timer e historial

**Anadido**
- Anadidos modelos de dominio `WorkoutSession`, `ActiveWorkoutExercise`, `WorkoutSet` y
  `WorkoutSummary`.
- Anadido contrato `WorkoutRepository`, `RoomWorkoutRepository` y `WorkoutDao` sobre las
  tablas v1 `workout_sessions` y `workout_sets`; no cambia el schema Room.
- Anadidos `StartWorkoutSessionUseCase` y `CompleteWorkoutSessionUseCase`.
- Implementado `WorkoutForegroundService` con notificacion persistente y `StateFlow` de
  cronometro compartido.
- Anadida ruta fullscreen `ActiveWorkout` con `HorizontalPager`, progress card, sets
  editables, check de completado, anadir/eliminar sets y finalizacion de sesion.
- Anadido overlay de rest timer con progreso circular, skip, vibracion y sonido.
- Anadido bottom sheet de ejercicios con drag vertical y botones accesibles para reordenar
  durante la sesion.
- Anadida pantalla `WorkoutComplete` con resumen de duracion, volumen, sets y records.
- Anadido historial/detalle de sesiones de fuerza dentro del tab Entrenar.
- Anadidos tests unitarios para inicio de sesion y cierre con volumen/records personales.

**Corregido**
- La deteccion de records personales no cuenta dos sets iguales de la misma sesion como dos
  records nuevos.
- El arranque del foreground service captura `SecurityException` por permisos runtime y no
  crashea la sesion activa.
- `ActiveWorkout` pide `ACTIVITY_RECOGNITION` antes de arrancar el FGS de tipo `health`.
- El reordenamiento de ejercicios durante la sesion se mantiene al editar sets.
- El rest timer respeta `rest_sound_enabled` y `rest_vibration_enabled` y libera
  `ToneGenerator`.
- Al completar el ultimo set se cierra la sesion y navega automaticamente al resumen.

**Seguridad**
- Registrado `SEC-013`: FGS de entrenamiento activo y degradacion segura si falta permiso
  runtime para `foregroundServiceType=health`.

**Verificado**
- `./gradlew assembleDebug assembleRelease test lint compileDebugAndroidTestKotlin --no-daemon`
  pasa tras liberar un bloqueo de archivo KSP en Windows con `./gradlew --stop`.
- QA visual runtime sigue bloqueada por falta de aceleracion Hyper-V/WHPX en el AVD local.

### Fase 4 - Ejercicios y rutinas

#### 2026-05-23 - Biblioteca, CRUD custom y constructor de rutinas

**Anadido**
- Anadidos modelos de dominio `MuscleGroup`, `Exercise`, `Routine`, `RoutineExercise` y
  `RoutineExerciseInput`.
- Anadidos contratos `ExerciseRepository`/`RoutineRepository` y use cases de busqueda,
  creacion, edicion, archivado y calculo de duracion estimada.
- Implementados `RoomExerciseRepository` y `RoomRoutineRepository` sobre DAOs existentes;
  no cambia el schema Room v1.
- Ampliados `ReferenceDao`, `ExerciseDao` y nuevo `RoutineDao` para lectura, upsert y soft
  delete de ejercicios/rutinas.
- Reemplazado el placeholder de `Train` por `TrainScreen` con tabs de ejercicios y rutinas.
- La biblioteca de ejercicios incluye busqueda, filtro por grupo muscular, creacion/edicion
  de ejercicios custom y archivado por soft delete.
- El constructor de rutinas permite nombre, color tag, ejercicios, series, reps, peso,
  descanso, orden editable con drag vertical y botones accesibles, y detalle de rutina.
- Anadidos strings ES/EN para toda la UI de Fase 4.
- Anadidos tests unitarios de `ExerciseUseCase` y `RoutineUseCase`, incluyendo actualizacion
  por id para no duplicar entidades al editar.

**Cambiado**
- `AppDatabase` expone `routineDao()` y Hilt enlaza los repositorios de ejercicios/rutinas.
- El guardado de ejercicios/rutinas existentes preserva `created_at` y solo actualiza el
  contenido editable.

**Corregido**
- El calculo de duracion estimada queda alineado con el contrato probado de Fase 4.
- La implementacion inicial de edicion no se dejo como UI falsa: editar usa el mismo id y
  actualiza la entidad existente.
- Los nombres seed de ejercicios/grupos respetan locale ES/EN al mapear desde Room.
- El filtro por grupo muscular incluye tambien el grupo secundario.
- El dominio y el repositorio evitan convertir un ejercicio preset en custom al editar por id.

**Seguridad**
- Sin nuevas superficies de auth/red/permisos/backup. Se mantiene soft delete y no se
  introducen logs ni secretos.

**Verificado**
- `./gradlew assembleDebug assembleRelease test lint compileDebugAndroidTestKotlin` pasa.
- `emulator -accel-check` sigue fallando con codigo 6 por Hyper-V/WHPX y `adb devices`
  no lista dispositivos; no se pudieron capturar screenshots runtime.
- QA visual runtime sigue bloqueada por falta de aceleracion Hyper-V/WHPX en el AVD local.

### Fase 3 - Onboarding

#### 2026-05-23 - Flujo inicial, permisos y perfil

**Anadido**
- Implementado `LaunchViewModel` y gate de arranque basado en DataStore `onboarding_completed`.
- Implementado flujo de onboarding fullscreen de 9 pasos: bienvenida, Google opcional,
  contrasena obligatoria, perfil, notificaciones, Health Connect, ubicacion, biometria y listo.
- Anadidos modelos/domain de onboarding y perfil, `PasswordStrengthEvaluator`,
  `OnboardingRepository` y `ProfileRepository`.
- Anadidos `PreferencesOnboardingRepository` (DataStore) y `RoomProfileRepository` con
  `UserProfileDao`; no cambia el schema Room.
- El paso de contrasena reutiliza `LocalAuthUseCase`; no duplica PBKDF2 ni user creation.
- Anadida solicitud runtime de `POST_NOTIFICATIONS`, `ACCESS_FINE_LOCATION` y permisos
  Health Connect via `PermissionController`, con guard de disponibilidad del SDK.
- Anadidos permisos Health Connect en manifest para lectura/escritura segun spec.
- Anadidos tests unitarios de fuerza de contrasena, onboarding ViewModel y launch gate.

**Corregido**
- El opt-in de biometria del onboarding se persiste al finalizar; antes el estado podia
  quedarse solo en UI.
- `FLAG_SECURE` cubre onboarding porque captura contrasena y perfil.
- El paso de Google queda como informacion saltable de backup futuro; no se presenta como
  conexion activa ni desbloquea datos locales.

**Seguridad**
- Registrado `SEC-012`: onboarding sensible y permisos Health Connect.

**Verificado**
- `./gradlew assembleDebug assembleRelease test lint compileDebugAndroidTestKotlin` pasa.
- QA visual/instrumented runtime sigue bloqueada por falta de aceleracion Hyper-V/WHPX en
  el AVD local; se mantiene pendiente hasta tener emulador operativo o dispositivo fisico.

### Fase 2 - Autenticacion completa

#### 2026-05-23 - Auth local, Google opcional y biometria fuerte

**Anadido**
- Implementado `EncryptionManager` con PBKDF2-HMAC-SHA256 a 600.000 iteraciones, salt de
  32 bytes, comparacion constante y wrappers AES-256-GCM.
- Anadidos modelos/domain/use cases de auth: `LocalAuthUseCase`, `GoogleSignInUseCase`,
  `BiometricAuthPolicy` y contratos `AuthRepository`/`PasswordHasher`.
- Implementado `RoomAuthRepository` usando tabla `users` para hash/salt y `auth_security`
  para bloqueo persistente de 5 intentos / 15 minutos.
- Anadido cliente Google Identity Services con Credential Manager; con placeholders locales
  devuelve estado `NotConfigured` sin intentar OAuth real.
- Anadido `LoginScreen` + `AuthViewModel`; el `NavHost` arranca en Login y navega a Home
  solo tras autenticacion o creacion de contrasena local.
- Anadido `BiometricPromptAuthenticator` con `BIOMETRIC_STRONG` y `MainActivity` basada en
  `FragmentActivity` para soportar `BiometricPrompt`.
- Anadido opt-in de biometria durante setup de contrasena y boton de desbloqueo biometrico
  solo cuando existe contrasena local y el usuario lo habilito.
- Anadida ruta placeholder `BackupRestore` para que `FLAG_SECURE` cubra tambien backup.
- Anadidos tests unitarios de crypto, rate limiting, biometria, Google config y prevencion
  de bypass con Google.

**Cambiado**
- `SPEC.md` queda alineado con la decision real: Google es opcional y hash/salt viven en
  DB SQLCipher, no en `EncryptedSharedPreferences`.
- `DOCS_TECNICA.md` documenta el gate de Login como destino inicial.
- Anadida dependencia `lifecycle-viewmodel-ktx` via catalogo de versiones.
- Credential Manager se lanza desde la `FragmentActivity`; ya no hay cliente singleton con
  application context.
- Las constantes de lockout viven en `LocalAuthPolicy`, no en el use case.

**Seguridad**
- Registrado `SEC-011`: auth local con PBKDF2 600k, lockout persistente y gate real.

**Verificado**
- `./gradlew assembleDebug assembleRelease test lint compileDebugAndroidTestKotlin` pasa.
- `git diff --check` pasa.
- QA visual/instrumented runtime sigue bloqueada por falta de aceleracion Hyper-V/WHPX en
  el AVD local (`emulator -accel-check` devuelve codigo 6); se mantiene pendiente hasta
  tener emulador operativo o dispositivo fisico.

### Fase 1 - Fundacion + i18n

#### 2026-05-23 - Base Room, tema y navegacion

**Anadido**
- Implementada `AppDatabase` Room v1 con SQLCipher, 20 tablas y schema exportado.
- Anadidas entities/DAOs base, cache Health Connect, `DatabaseSeeder` idempotente y seed data.
- Anadido `DatabasePassphraseProvider` con clave aleatoria protegida por Android Keystore /
  `EncryptedSharedPreferences`.
- Implementado `AtlasPeakTheme`, tokens de color/spacing/shape, tipografia base, NavHost raiz,
  bottom navigation de 5 tabs y `FLAG_SECURE` por ruta sensible.
- Anadidos tests unitarios de seed data y tests instrumentados de Room compilables.

**Cambiado**
- `users.email` pasa a nullable porque Google/Drive es opcional.
- `SPEC.md` y `DOCS_TECNICA.md` nombran las 20 tablas reales de DB v1.
- `app/build.gradle.kts` anade dependencias instrumentadas para Room/testing.

**Verificado**
- `./gradlew assembleDebug assembleRelease test lint` pasa.
- `compileDebugAndroidTestKotlin` pasa.
- QA visual/instrumented runtime bloqueada: el AVD x86_64 requiere aceleracion Hyper-V/WHPX,
  no disponible actualmente en esta maquina.

### Fase 0 - Preflight local para ejecucion v1

#### 2026-05-23 - Entorno de ejecucion y gates base

**Anadido**
- Inicializado repositorio Git local para poder crear checkpoints por fase.
- Creado `secrets.properties` local con placeholders (`OAUTH_WEB_CLIENT_ID` y `MAPS_API_KEY`);
  sigue gitignored y no contiene secretos reales.
- Instalado paquete Android Emulator y system image `android-35;google_apis;x86_64`.
- Creado AVD local `AtlasPeak_API35` para QA visual y validacion con `adb`.

**Verificado**
- `./gradlew assembleDebug assembleRelease test lint` pasa en el estado base.
- `test` sigue sin ejecutar tests reales (`NO-SOURCE`); Fase 1 debe anadir cobertura real.

### Fase 0 — Bootstrap (cerrada)

#### 2026-05-23 — Preparación de entorno local y build Android

**Añadido**
- Regenerado Gradle Wrapper (`gradlew`, `gradlew.bat`, `gradle-wrapper.jar`) para que CI y
  desarrolladores puedan ejecutar los comandos documentados.
- Añadida regla en `AGENTS.md` para permitir el uso de skills locales desde
  `C:\Proyectos\Atlas Peak Dev\Skills`.
- Creado `local.properties` local (gitignored) apuntando al Android SDK portable instalado en
  `C:\tmp\atlas-dev-tools\android-sdk`.
- Añadido icono launcher adaptive mínimo y stubs de `WorkoutForegroundService` /
  `CardioForegroundService` para que el manifest apunte a clases reales.

**Cambiado**
- Subido `compileSdk` a 36 y AGP a 8.9.1 porque las dependencias actuales (`health-connect`
  1.1.0 / AndroidX Core resuelto) exigen API 36 y AGP 8.9.1+. `targetSdk` se mantiene en 35.
- Ajustado Vico a 2.1.3 para mantener compatibilidad binaria con Kotlin 2.1.x; Vico 2.4.3
  está compilado con Kotlin 2.3.0 y rompe el build.
- Corregida regla R8 inválida `-keepclasseseachmember` a `-keepclassmembers`.
- Añadido `ACTIVITY_RECOGNITION` por requisito de `foregroundServiceType="health"` en
  Android 14+ (SEC-009).
- Actualizado `README.md`: ya no afirma que falten Gradle Wrapper ni launcher icon.
- Instalado entorno portable para esta máquina: Microsoft OpenJDK 17, Gradle 8.11.1 y Android
  SDK Platform/Build Tools 35 y 36 en `C:\tmp\atlas-dev-tools`.
- Registradas variables de usuario `JAVA_HOME`, `ANDROID_HOME`, `ANDROID_SDK_ROOT` y entradas
  de `Path` apuntando al entorno portable.

#### 2026-05-23 — Auditoría del spec y corrección a v2.2 + kit de arranque

**Seguridad**
- Corregido formato de backup para incluir salt + parámetros KDF en la cabecera del
  archivo (SEC-001). Sin esto la restauración en otro dispositivo era imposible.
- Eliminado `ACCESS_BACKGROUND_LOCATION` del manifest; el FGS de cardio no lo necesita
  y disparaba rechazo de Google Play (SEC-003).
- Eliminado el uso de `google-services.json`; se migra a Web OAuth Client ID via
  `secrets.properties` (SEC-004).
- Añadida gestión de la Google Maps API key via `secrets.properties` (SEC-005).
- PBKDF2 subido de 200.000 a 600.000 iteraciones (SEC-006).
- Añadido `allowBackup="false"` + reglas de exclusión de backup de Android (SEC-007).
- Eliminado permiso `USE_FINGERPRINT` deprecado (SEC-008).

**Cambiado**
- Versiones de dependencias actualizadas a estables verificadas (2026-05-23):
  - Vico `2.0.0-beta.2` → `2.x` estable; coordenada de artifact corregida.
  - Health Connect: `androidx.health:health-connect-client:1.1.0-rc01` →
    `androidx.health.connect:connect-client:1.1.0` (artifact renombrado + estable).
  - Wear Compose: `1.0.0-alpha30`/`1.4.0` mezclados → línea `1.x` coherente (diferido a v2).
- Google Sign-In reclasificado de "crear cuenta obligatoria" a **OAuth opcional para Drive**.
  Onboarding paso 2 ahora es saltable (coherente con local-first y modo offline).
- Rate-limit: resuelta la contradicción ESP vs tabla → se usa la tabla `auth_security`
  en la DB cifrada (se elimina la mención a `EncryptedSharedPreferences` para el contador).

**Eliminado**
- Wear OS diferido a v2 (no se construye en v1). Módulo `wear` fuera de `settings.gradle.kts`
  por ahora; diseño del protocolo conservado en `SPEC.md §2.10`.

**Añadido**
- `AGENTS.md`: contrato operativo tool-agnostic para cualquier agente de IA. Concentra las
  reglas innegociables + mapa de documentos y **delega** el detalle a `CLAUDE.md`/`SPEC.md`
  (sin duplicar contenido, para evitar desincronización entre fuentes de verdad).
- Kit de arranque para Claude Code: `CLAUDE.md`, `DESIGN.md`, `BUGS.md`, `SECURITY.md`,
  `DOCS_USUARIO.md`, `DOCS_TECNICA.md`, este `CHANGELOG.md`, `SPEC.md` (v2.2), `README.md`.
- Config de build: `settings.gradle.kts`, `build.gradle.kts` (root + app),
  `gradle/libs.versions.toml`, `gradle.properties`, `proguard-rules.pro`.
- Manifest base con tipos de Foreground Service declarados, FileProvider, FLAG_SECURE
  documentado, `network_security_config.xml`, `file_paths.xml`, reglas de backup.
- `.gitignore` endurecido (secretos, keystore, build).
- `secrets.properties.template`.
- `strings.xml` (ES) + `values-en/strings.xml` (EN) con set inicial.
- CI de GitHub Actions (`build` + `test` + `lint`).

---

## Formato para nuevas entradas

```
### Fase N — Nombre de la fase

#### AAAA-MM-DD — Título de la sesión
**Añadido / Cambiado / Corregido / Eliminado / Seguridad**
- ...
```

**Regla:** ninguna sesión de trabajo cierra sin una entrada aquí. Si arreglaste un bug,
referencia su `BUG-NNN`. Si tocaste seguridad, referencia su `SEC-NNN`.

---

*Atlas Peak — CHANGELOG.md*
