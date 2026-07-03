# Atlas Peak — Informe de revisión integral

> **Alcance:** revisión de código estática (sin ejecutar la app) cubriendo UI/UX, funcionalidad,
> flujos de trabajo, bugs y seguridad. No se ha editado nada.
> **Periodo de revisión:** estado del repo a `2026-07-01` (rama actual, V-01.07 / schema Room v5).
> **Fuentes:** 160+ archivos en `app/src/main/kotlin`, `app/src/androidTest`, `app/src/test`,
> `AndroidManifest.xml`, `app/build.gradle.kts`, `gradle/libs.versions.toml`,
> `res/values/strings.xml` y `res/values-en/strings.xml`.

---

## 0. Resumen ejecutivo

Atlas Peak es una app Android local-first muy bien pensada en su **arquitectura base**, su
**modelo de seguridad pasivo** (SQLCipher + Keystore + PBKDF2 600k + AES-256-GCM, red solo
HTTPS, cero tracking) y su **disciplina operativa** (CHANGELOG, BUGS, SECURITY, schemas
versionados, dependency verification, JaCoCo gate). El estado general es **publicable
previo paso por los issues Críticos y Altos** que se listan abajo.

| Severidad | Cuenta | Bloquean release |
|---|---|---|
| **Crítico** | 4 | Sí — romperían flujos principales o abrirían riesgos |
| **Alto**   | 11 | Sí — fricción seria o regresión de features |
| **Medio**  | ~35 | No bloqueante, pero impactan UX/calidad |
| **Bajo**   | ~25 | Higiene / polish |

**Hallazgos clave** (los 4 que más urge tocar):

1. **`GoogleDriveAccessTokenProvider.silentAccessToken()` no usa `BuildConfig.OAUTH_WEB_CLIENT_ID`**
   y no pide `setRequestedOfflineAccess`. Resultado: el token Drive dura ~1h, el worker
   silencioso falla, `BackupWorker` termina `Result.success()` sin subir nada y el usuario
   cree que tiene backup. **El feature de auto-backup está roto en silencio.**
   `app/src/main/kotlin/com/atlaspeak/data/drive/GoogleDriveAccessTokenProvider.kt:17-25`.

2. **`ActiveCardioScreen` BorHandler borra la sesión sin confirmación.**
   Cualquier back/swipe accidental destruye una sesión de cardio. Los strings
   `cardio_cancel_dialog_*` ya existen pero no se usan. El flujo de Fuerza sí tiene
   `showExitDialog` y `AtlasDialog`; el de Cardio no.
   `app/src/main/kotlin/com/atlaspeak/presentation/cardio/ActiveCardioScreen.kt:83-85`.

3. **`ActiveWorkoutViewModel.availableExercisesForQuickAdd()` ejecuta
   `kotlinx.coroutines.runBlocking` en main thread en cada recomposición.** El comentario
   en el código dice "solo se ejecuta una vez al abrir el picker", pero en `ActiveWorkoutRoute`
   se pasa como argumento del composable y se evalúa en cada recomposición.
   `app/src/main/kotlin/com/atlaspeak/presentation/workout/ActiveWorkoutViewModel.kt:195-203`.

4. **`BackupSnapshotUpgrader` y `MIGRATION_4_5` rellenan `weekly_plan.order_index = 0` para
   todas las filas del mismo día.** Combinado con el nuevo índice único
   `(day_of_week, order_index)`, cualquier backup v3 con 2+ sesiones el mismo día o una DB
   migrada con multi-sesión preexistente hace fallar la restauración con `UNIQUE constraint
   failed` a mitad de transacción, dejando la BD en estado inconsistente.
   `app/src/main/kotlin/com/atlaspeak/data/backup/BackupSnapshotUpgrader.kt:66-71` y
   `app/src/main/kotlin/com/atlaspeak/data/db/AppDatabase.kt:254-281`.

---

## 1. Seguridad — hallazgos Críticos y Altos

### C-SEC-1 · Auto-backup de Drive está roto en silencio
- **Archivo:** `data/drive/GoogleDriveAccessTokenProvider.kt:17-25`
- **Síntoma:** El `AuthorizationRequest` no llama a `setServerClientId(BuildConfig.OAUTH_WEB_CLIENT_ID)`
  ni a `setRequestedOfflineAccess(...)`. `Identity.getAuthorizationClient(...).authorize(...)`
  con un Web Client ID de Drive devuelve tokens de vida corta (~1h). `BackupWorker` (worker
  diario) llama a `silentAccessToken()`, recibe `null`, registra `Result.success()` y nunca
  sube nada. El usuario ve "último backup: nunca" sin causa.
- **Causa raíz:** el `Web OAuth Client ID` está declarado en `BuildConfig` y en el manifest
  (vía `secrets.properties`) pero el `AuthorizationRequest` no lo usa; falta el contrato
  `server_client_id` para `drive.appdata`.
- **Fix recomendado:**
  ```kotlin
  AuthorizationRequest.builder()
      .setRequestedScopes(listOf(Scope(Scopes.DRIVE_APPFOLDER)))
      .setServerClientId(BuildConfig.OAUTH_WEB_CLIENT_ID)
      .setRequestedOfflineAccess(BuildConfig.OAUTH_WEB_CLIENT_ID)
      .build()
  ```
  Y obtener refresh token, persistirlo y canjearlo por access tokens en cada `silentAccessToken()`.

### C-SEC-2 · `silentAccessToken()` traga toda excepción sin diagnóstico
- **Archivo:** `data/drive/GoogleDriveAccessTokenProvider.kt:21-25`
- `runCatching { ... }.getOrNull()` colapsa todo a `null`. El worker no puede distinguir
  "permiso revocado" de "token caducado" de "red caída". Cambiar a sealed
  `SilentGrantResult.{ Missing, Failed(cause), Granted(token) }` para que el worker decida
  reintentar vs. mostrar UI al usuario.

### C-SEC-3 · `LocationTracker` no filtra precisión / frescura / primer fix
- **Archivo:** `data/location/LocationTracker.kt:22-39`
- Acepta todos los fixes (`PRIORITY_HIGH_ACCURACY`, 1 Hz) sin filtrar `location.accuracy`
  (puede ser 50 m+ en ciudad) ni `now - location.time <= 10s` (caché obsoleta). El primer
  fix es casi siempre un `Wi-Fi/cell` cacheado y se convierte en `route[0]`,
  contaminando toda la distancia y la velocidad media. La filtración de velocidad en
  `CardioUseCase.sanitizedRoute` no lo coge porque no hay `previous` para comparar.
- **Fix:** `trySend` solo si `accuracy <= 30f` y `now - location.time <= 10s`. Marcar
  `firstFix = true` en estado y no acumular distancia hasta el 2º fix válido.

### C-SEC-4 · `RoomBackupSnapshotStore.snapshot()` no es transaccional
- **Archivo:** `data/backup/RoomBackupSnapshotStore.kt:23-32`
- Cada `SELECT *` corre en su propia transacción implícita. Si una sesión de entreno se
  completa durante el snapshot, el backup puede contener la fila de `workout_sessions`
  sin sus `workout_sets` (o al revés), produciendo un backup internamente inconsistente
  que al restaurar deja la BD en estado roto. Envolver en
  `database.withTransaction { ... }` para que las 20 tablas se lean en la misma view.

### H-SEC-1 · `BackupSnapshotUpgrader` colisiona con índice único de `weekly_plan`
- **Archivo:** `data/backup/BackupSnapshotUpgrader.kt:66-71`
- `order_index = JsonPrimitive(0)` para todas las filas. Con el nuevo índice
  `UNIQUE(day_of_week, order_index)` cualquier restore con 2+ sesiones/día revienta
  en plena transacción. Usar `ROW_NUMBER() OVER (PARTITION BY day_of_week ORDER BY id)`
  (o equivalente en `JsonElement` mapper) y cubrir con test de restore con backup
  multi-sesión.

### H-SEC-2 · `MIGRATION_4_5` repite el bug a nivel SQL
- **Archivo:** `data/db/AppDatabase.kt:254-281`
- Mismo `order_index = 0` masivo. Una DB con multi-sesión previa (beta / restore) fallará
  la creación del índice único y abortará la migración. Test actual no cubre multi-sesión
  preexistente. Mismo fix con `ROW_NUMBER()`.

### H-SEC-3 · `BackupCredentialStore.saveAutoBackupPassword` materializa la passphrase
  como `String` inmutable
- **Archivo:** `data/backup/BackupCredentialStore.kt:47`
- `password.concatToString().toByteArray(Charsets.UTF_8)` crea un `String` que vive en el
  heap JVM hasta GC y del que no se puede zerar de forma fiable. El propio docstring
  reconoce el riesgo. Pasar a `Cipher` byte-a-byte con un `ByteBuffer` reutilizable y
  rellenar con 0 el array intermedio.

### H-SEC-4 · `DriveBackupManager.createBackup` / `restoreBackup` no zerean `encrypted`
  ni `json` post-IO
- **Archivo:** `data/backup/DriveBackupManager.kt:25-59`
- `payload.fill(0)` y `plaintext.fill(0)` están bien. `encrypted` y el `String` `json`
  intermedio quedan en memoria hasta GC. Para un backup que puede contener PII (peso,
  grasa corporal, email), eso es residuo material.

### H-SEC-5 · `BodyCompositionUseCase` + `HealthConnectManager` duplican mediciones
- **Archivos:** `domain/usecase/body/BodyCompositionUseCase.kt`, `data/healthconnect/HealthConnectManager.kt:243-260`
- El import HC borra solo filas `source = 'HEALTH_CONNECT'` en la ventana y re-inserta.
  Si existe una entrada manual con el mismo `measured_at`, ambas viven y aparecen en el
  chart y en `latestValues(...)` (no se filtra por source). Decidir una política
  (recomendado: `HEALTH_CONNECT` gana si timestamp igual) y reflejar en el `body_composition.snapshot()`
  y en `DashboardRepository.getBodyWeightPoints` (que hoy no filtra por source).

### H-SEC-6 · `BackupWorker` reintenta fallos no transitorios
- **Archivo:** `data/backup/BackupWorkerRunner.kt:28-29`
- `Failed(reason)` se mapea a `Result.retry()` para todas las causas. `InvalidBackup`,
  `EmptyPassword`, `Crypto` (passphrase mala) no se solucionan reintentando: gastan
  slots de WorkManager, retrasan el siguiente run legítimo y diluyen la causa en
  `adb dumpsys jobscheduler`. Solo reintentar en `Network`/`Unknown`.

### H-SEC-7 · `HealthConnectRecordMapper.zoneOffsetString` devuelve ZoneId, no offset
- **Archivo:** `data/healthconnect/HealthConnectManager.kt:537`
- `ZoneId.systemDefault().rules.getOffset(instant).id` devuelve el **id** de zona
  (`"Europe/Madrid"`), no el offset real para ese `instant`. En DST, dos registros del
  mismo usuario pueden tener el mismo string de zona pero offsets distintos. El import
  (línea 132) sí usa el offset correctamente. Unificar a `ZoneOffset.totalSeconds`
  como `Int` o almacenar el offset real.

### H-SEC-8 · Export CSV en claro: `LocalBackupExportManager.writeEncryptedBackup`
  sobreescribe sin avisar
- **Archivo:** `data/backup/LocalBackupExportManager.kt:60-77`
- El cleanup silencioso elimina el export cifrado anterior. El usuario puede haberlo
  enviado por email y perderlo al hacer un segundo export. Mostrar aviso y dejar
  opcionalmente un histórico.

### H-SEC-9 · `MIGRATION_2_3` resetea estado de seguridad
- **Archivo:** `data/db/AppDatabase.kt:107-128, 222`
- `auth_security.failed_attempts = 0`, `locked_until = NULL` en el upgrade 2→3. En
  este modelo sin login el impacto es bajo, pero está documentado como aceptado y no en
  `SECURITY.md`. Mover a riesgo aceptado explícito y enlazar desde
  `SECURITY.md §4` (Riesgos aceptados).

### H-SEC-10 · `secure_random` por defecto en `BackupFileCodec` y `LocalBackupExportManager`
- **Archivo:** `data/backup/BackupFileCodec.kt:17`
- `private val secureRandom: SecureRandom = SecureRandom()` no se inyecta. Tests de
  crypto no pueden ser deterministas. Inyectar vía Hilt y documentar.

### H-SEC-11 · `RoomBodyCompositionRepository` colapsa `source` desconocido a `Manual`
- **Archivo:** `data/repository/RoomBodyCompositionRepository.kt:35-39`
- `else -> BodyCompositionSource.Manual` rompe el tracking cuando se añada un nuevo
  source (Withings, Garmin, etc.). Hacerlo fail-loud con `Unknown` y dejar que la UI
  muestre el origen real.

### H-SEC-12 · `HealthConnectRecordMapper.exerciseType` decidido por string match
- **Archivo:** `data/healthconnect/HealthConnectRecordMapper.kt:116-128`
- Mapea contra nombres en inglés de los tipos preset (`cardio_running_outdoor`...).
  Un tipo custom renombrado o traducido se va a `OTHER_WORKOUT`. Añadir columna
  `hc_exercise_type` en `cardio_types` y seedear los IDs.

---

## 2. Bugs — Críticos y Altos

### C-BUG-1 · BackHandler en Cardio destruye sesión sin diálogo
- **Archivo:** `presentation/cardio/ActiveCardioScreen.kt:83-85`
- `BackHandler { viewModel.cancelCardio() }` borra la sesión inmediatamente. En el
  flujo de Fuerza el `ActiveWorkoutScreen` sí muestra `showExitDialog`. Los strings
  `cardio_cancel_dialog_title/body/keep/discard` existen en `strings.xml` pero nadie
  los referencia. Copiar el patrón de AtlasDialog de ActiveWorkout.

### C-BUG-2 · `availableExercisesForQuickAdd()` corre `runBlocking` en main en cada
  recomposición
- **Archivo:** `presentation/workout/ActiveWorkoutViewModel.kt:195-203`
- En `ActiveWorkoutRoute` se pasa como parámetro de la lambda, así que se evalúa
  en cada recomposición del route. Mover a un `MutableStateFlow` inicializado en
  `init {}` o a un `LaunchedEffect(Unit)` que escribe a un `mutableStateOf`.

### H-BUG-1 · Set-completed check button de 34 dp (< 44 dp mínimo accesible)
- **Archivo:** `presentation/workout/ActiveWorkoutScreen.kt:692-714`
- El checkbox visual es 34 dp, no 44 dp. WCAG/Material pide ≥44 dp. Envolver en
  `Box(Modifier.size(44.dp).clickable { ... })` con `Surface(34.dp)` decorativo dentro.

### H-BUG-2 · Toast en `EditProfileScreen` al guardar
- **Archivo:** `presentation/profile/EditProfileScreen.kt:50-55`
- `Toast.makeText(...).show()` no es accesible y es silencioso en segundo plano
  (Android 11+). Usar `Snackbar` (consistente con el resto de la app) o
  `AtlasStatusMessage`.

### H-BUG-3 · `WeeklyPlanViewModel.setRestDay(true)` borra todas las sesiones del día
- **Archivo:** `presentation/planning/WeeklyPlanViewModel.kt:147-158`
- Un toggle accidental pierde una planificación cuidadosa de fuerza+cardio del día.
  Mostrar `AtlasDialog` de confirmación o mover las sesiones al día siguiente.

### H-BUG-4 · `BackupRestoreViewModel.setAutoBackupEnabled` no permite cambiar passphrase
- **Archivo:** `presentation/backup/BackupRestoreViewModel.kt:56-81`
- Solo permite "set" o "clear". El string `backup_password_change_warning` existe
  pero no se muestra en ningún sitio. El usuario no sabe que los backups anteriores
  requerirán la passphrase antigua (SEC-002). Implementar "cambiar passphrase"
  con aviso explícito y migración de la próxima copia.

### H-BUG-5 · `TrainViewModel.onSearchQueryChanged` re-fetcha Room en cada keystroke
- **Archivo:** `presentation/workout/TrainViewModel.kt:45-53`
- Debounce 250-300 ms con `MutableStateFlow.debounce` o filtrar en memoria sobre la
  lista ya cargada. Flicker perceptible en bibliotecas grandes.

### H-BUG-6 · `HomeViewModel.init` arranca sync y refresh sin cancelarse mutuamente
- **Archivo:** `presentation/home/HomeViewModel.kt:39-42`
- `syncHealthConnect()` y `refresh()` se lanzan en paralelo; un `refresh()` rápido
  puede completar antes que el sync y el resultado del sync llega después pisando el
  state. Usar un único `Job` o `syncJob` con cancelación.

### H-BUG-7 · `LaunchViewModel` no tiene estado de error ni timeout
- **Archivo:** `presentation/navigation/LaunchViewModel.kt:21-26`
- Si la lectura de `onboardingCompleted` falla, el splash se queda "Cargando" para
  siempre. Añadir `LaunchState.Error` y timeout (5 s) con fallback a `Onboarding`.

### H-BUG-8 · `CardioForegroundService` no arranca foreground si no hay GPS → sesión perdida
- **Archivo:** `service/CardioForegroundService.kt:123-127`
- Si el usuario deniega ubicación, el servicio hace `failed=true` y `stopSelf()` sin
  hacer `startForeground()`. Android lo mata en segundos y la sesión de cardio
  (cronómetro + tiempo) se pierde. Arrancar el FGS con tipo `data_sync` o
  `health` (sin requerir location) cuando no haya GPS.

### H-BUG-9 · `HomeScreen` no muestra Retry en error ni permite pull-to-refresh
- **Archivo:** `presentation/home/HomeScreen.kt:96-115, 49-83`
- En error transitorio el usuario solo ve texto rojo sin acción. Sin swipe-to-refresh
  ni botón, no hay forma de forzar recarga. Mismo patrón en `BodyCompositionScreen`
  (línea 111-114) sin retry.

### H-BUG-10 · `MIGRATION_4_5` puede romper datos de multi-sesión preexistentes
- (Repetido desde H-SEC-2 — también es bug de datos.)

### H-BUG-11 · `OnboardingViewModel.finish` no espera el `setOnboardingCompleted`
- **Archivo:** `presentation/onboarding/OnboardingViewModel.kt:89-91`
- `viewModelScope.launch { ... setOnboardingCompleted(true) }` puede ser matado antes
  de persistir si el usuario mata la app al tocar "Empezar". Hacerlo síncrono
  (`runBlocking` o `MutableStateFlow.first()` con timeout) o persistir en
  `onPause` antes de navegar.

---

## 3. Bugs — Medios (resumen, alto impacto UX)

(Lista priorizada, ver `file:line` en cada issue. Cada uno tiene un fix de 1-2 días.)

| # | Severidad | Área | Descripción resumida | Archivo |
|---|---|---|---|---|
| M-BUG-1 | M | Cardio | `ManualDistanceCard` siempre visible, incluso con GPS funcionando | `ActiveCardioScreen.kt:125-130` |
| M-BUG-2 | M | Cardio | `currentSpeedKmh` en servicio es velocidad del **último segmento**, no actual | `CardioForegroundService.kt:60-77` |
| M-BUG-3 | M | Cardio | `startLocalTimerIfNeeded` `while(true)` puede duplicar updates si el servicio recupera | `ActiveCardioViewModel.kt:184-197` |
| M-BUG-4 | M | Strength | `onSetCompleted` pierde `isPersonalRecord` al desmarcar y volver a marcar | `ActiveWorkoutViewModel.kt:69-88` |
| M-BUG-5 | M | Strength | `setRow` checkbox sin `contentDescription` (TalkBack no anuncia "Set 1 completado") | `ActiveWorkoutScreen.kt:1108-1112` |
| M-BUG-6 | M | Strength | `showExerciseSheet` no es `rememberSaveable`; se cierra en rotación | `ActiveWorkoutScreen.kt:247` |
| M-BUG-7 | M | Strength | Rest timer con `delay(1000)` deriva; usar ticks basados en `System.currentTimeMillis()` | `ActiveWorkoutViewModel.kt:373-386` |
| M-BUG-8 | M | Strength | `HorizontalPager` salta automáticamente al completar set; puede desorientar tras swipe manual | `ActiveWorkoutScreen.kt:296-312` |
| M-BUG-9 | M | Routine | `createRoutine` no exige ≥1 ejercicio; permite guardar rutina vacía | `TrainViewModel.kt:176-201` |
| M-BUG-10 | M | Routine | `updateDraftItem` re-emite el draft entero por cada keystroke | `TrainViewModel.kt:160-174` |
| M-BUG-11 | M | Routine | Drag-to-reorder captura el scroll del `LazyColumn`; debería ir con long-press o handle | `TrainScreen.kt:1285-1300` |
| M-BUG-12 | M | Map | `CardioRouteMap` centra en `points.first()` con zoom fijo 15; no encuadra bounds | `CardioRouteMap.kt:34-37` |
| M-BUG-13 | M | Plan | `setRestDay` sin confirmación; pierde planificación | `WeeklyPlanViewModel.kt:147-158` |
| M-BUG-14 | M | Plan | Re-ordenar sesiones en plan no soportado en UI pese a `order_index` en schema | `WeeklyPlanScreen.kt:295-313` |
| M-BUG-15 | M | Body | Sin retry en `BodyCompositionScreen` si falla `refresh()` | `BodyCompositionScreen.kt:111-114` |
| M-BUG-16 | M | Body | Sin validación per-campo (BodyFat > 100, etc.) | `BodyCompositionScreen.kt:432-466` |
| M-BUG-17 | M | Progress | Sin search/filter en listas de Ejercicios / Grupos (lento con 50+ ejercicios) | `ProgressScreen.kt:155-157` |
| M-BUG-18 | M | Progress | `HistoryDetailCard` aparece debajo en el `LazyColumn`; hay que scrollear para verla | `ProgressScreen.kt:441-443` |
| M-BUG-19 | M | Backup | `PasswordCard` se borra tras cada uso; no hay "show/hide", no hay strength meter, no autofill hint | `BackupRestoreScreen.kt:281-333` |
| M-BUG-20 | M | Backup | `DriveAction` serializa `restore:$fileId` con `:` sin escape; brittleness | `BackupRestoreScreen.kt:519-533` |
| M-BUG-21 | M | Home | Carousel de periodos: tap cicla sin pista visual, sin `Role.Button`, no anuncia periodo a TalkBack | `HomeScreen.kt:339-364` |
| M-BUG-22 | M | Home | `Notifications` icon en header con `contentDescription = null`; parece tap-able | `HomeScreen.kt:226-235` |
| M-BUG-23 | M | Onboarding | 3 pasos de permisos tienen body vacío; rationale se ve solo en texto | `OnboardingScreen.kt:166-194` |
| M-BUG-24 | M | Onboarding | En Android 12 (`< TIRAMISU`) se marca notifications granted sin pedir | `OnboardingScreen.kt:103-122` |
| M-BUG-25 | M | Onboarding | `viewModelScope.launch` antes de navegar puede ser matado | `OnboardingViewModel.kt:89-91` |
| M-BUG-26 | M | Nav | `Launch` reusa `PlaceholderScreen` con `state_loading`; mismo look que empty | `AtlasPeakNavHost.kt:66` |
| M-BUG-27 | M | Theme | `ink3` en light `#8A8A90` sobre `#FFFFFF` ≈ 3.4:1 — falla WCAG AA para body | `theme/Color.kt:11-13` |
| M-BUG-28 | M | Theme | `displayLarge` 86 sp + 130% font scale = overflow en hero | `theme/Typography.kt:40-42` |
| M-BUG-29 | M | Nav | `AtlasPeakBottomBar` con `width(282.dp)` fijo; overflow en pantallas estrechas | `AtlasPeakApp.kt:86-130` |
| M-BUG-30 | M | FGS | `WorkoutForegroundService` sin acción "Stop" en la notificación | `WorkoutForegroundService.kt:125-137` |
| M-BUG-31 | M | FGS | `try/catch (_: SecurityException)` no captura `MissingForegroundServiceTypeException` | `WorkoutForegroundService.kt:86-99` |
| M-BUG-32 | M | Cardio | `cancelCardio` borra sin opción "guardar tiempo" | `ActiveCardioViewModel.kt:151-157` |
| M-BUG-33 | M | Cardio | Permiso de location solo pide `FINE`; en `COARSE`-only devices cae a "denegado" | `ActiveCardioScreen.kt:62-72` |
| M-BUG-34 | M | Backup | `HealthPermissionsRationaleActivity` es una Activity entera con su propio task | `HealthPermissionsRationaleActivity.kt:21-63` |
| M-BUG-35 | M | Backup | `BackupWorkScheduler` no cancela reminders de días anteriores al mover una sesión | `WorkManagerNotificationScheduler.kt:73-106` |

---

## 4. Bugs — Bajos (resumen, polish)

| # | Área | Resumen | Archivo |
|---|---|---|---|
| L-BUG-1 | UI | `AtlasListRow` sin `Role.Button` en filas clicables | `component/AtlasPrimitives.kt:117-153` |
| L-BUG-2 | UI | Chevron aparece aunque `onClick == null` (sugiere interactividad) | `AtlasPrimitives.kt:117-153` |
| L-BUG-3 | UI | `AtlasDialog` sin `DialogProperties.usePlatformDefaultWidth=false`; ancho M3 default | `AtlasPrimitives.kt:288-326` |
| L-BUG-4 | UI | `MonochromeBarChart.selectedIndex` recalculado en cada recomposición (no hay tap) | `MonochromeCharts.kt:109-138` |
| L-BUG-5 | UI | `StepDots` sin `contentDescription` decorativo para TalkBack | `StepDots.kt:25-67` |
| L-BUG-6 | UI | `bodyLarge` sin `withTabularNumbers` | `Typography.kt:49-51` |
| L-BUG-7 | i18n | Strings muertos: `onboarding_google_*`, `cardio_cancel_dialog_*`, `theme_system/light/dark` | `values/strings.xml` |
| L-BUG-8 | i18n | `nav_cd_*` ES son fragmentos ("Ir a Inicio"), EN son frases ("Go to Home") | `strings.xml:494-496` |
| L-BUG-9 | i18n | `health_rationale_body` es un párrafo denso; partir en secciones | `strings.xml:475` |
| L-BUG-10 | i18n | `values-en/strings.xml` no audita `state_empty_body`; correr `lint --check MissingTranslation` | `values-en/strings.xml` |
| L-BUG-11 | UI | "Continue" en `WorkoutComplete`/`CardioComplete` sin "Share session" (perdida oportunidad) | `WorkoutCompleteScreen.kt:46-130` |
| L-BUG-12 | UI | `Home` no tiene swipe-to-refresh | `HomeScreen.kt:96-115` |
| L-BUG-13 | UI | Error en `Home` se renderiza como texto sin icono, sin acción, sin auto-dismiss | `HomeScreen.kt:158-166` |
| L-BUG-14 | UI | `BodyMetric` input sin límite de caracteres ni validación inline | `BodyCompositionScreen.kt:432-466` |
| L-BUG-15 | UI | `AddExerciseDialog` (en active workout) sin search | `ActiveWorkoutScreen.kt:949-980` |
| L-BUG-16 | UI | Tab "Hoy" cardio sin "Saltar" affordance (estás atascado) | `ActiveWorkoutScreen.kt:321-348` |
| L-BUG-17 | UI | `setNotificationTime` permite "99:99" sin feedback | `WeeklyPlanViewModel.kt:165` |
| L-BUG-18 | UI | `ThemeMode` declarado pero no usado; el theme siempre es system | `theme/Theme.kt:60-63` |
| L-BUG-19 | UI | `AtlasTextField.keyboardType` no soporta `KeyboardType.Password` realmente | `component/AtlasTextField.kt:18-67` |
| L-BUG-20 | UI | `showExerciseSheet` no sobrevive a rotación | `ActiveWorkoutScreen.kt:247` |
| L-BUG-21 | Nav | `AtlasPeakApp` chip delta 1dp entre tab activa/inactiva (apenas perceptible) | `AtlasPeakApp.kt:122-123` |
| L-BUG-22 | Domain | `CardioUseCase.reasonableMaxSpeedKmh` usa `cardioTypeName.contains("run", ignoreCase)` — frágil | `CardioUseCase.kt:171-196` |
| L-BUG-23 | Domain | `RoutineUseCase.createOrUpdateRoutine` sin validación de sets/reps/rest/weight | `RoutineUseCase.kt:17-50` |
| L-BUG-24 | Domain | `RoutineUseCase` regenera UUIDs de `RoutineExercise` en cada save | `RoutineUseCase.kt:34-46` |
| L-BUG-25 | Domain | `BodyCompositionUseCase.record` sin de-dup ni future-date check | `BodyCompositionUseCase.kt:43-64` |
| L-BUG-26 | Domain | `BackupUseCase` es delegación pura, no aporta valor | `BackupUseCase.kt:6-30` |
| L-BUG-27 | Domain | N+1 lookups en `RoomWorkoutRepository.toDomain` | `RoomWorkoutRepository.kt:67-99` |
| L-BUG-28 | Domain | `WorkoutRepository.sessions()` debería filtrar STRENGTH | `RoomWorkoutRepository.kt:101-112` |
| L-BUG-29 | Domain | `DashboardUseCase.averageHours` calcula avg por sesión, no por día (label miente) | `DashboardUseCase.kt:189-197` |
| L-BUG-30 | Domain | `BodyCompositionUseCase.snapshot` Year=364d, YTD=en-curso; mezcla paradigmas | `BodyCompositionUseCase.kt:138-149` |
| L-BUG-31 | Domain | `WorkoutRepository.maxCompletedWeightBefore` usa `start_time` no completion | `Daos.kt:147-158` |
| L-BUG-32 | Domain | `WorkoutForegroundService`/`CardioForegroundService` `START_NOT_STICKY`; session se pierde si se mata el servicio | `service/*.kt` |
| L-BUG-33 | Build | `BackupFileCodec.secureRandom` no inyectado (no determinismo en tests) | `BackupFileCodec.kt:17` |
| L-BUG-34 | Build | `proguard-rules.pro` no revisado en detalle; confiar en defaults | `app/proguard-rules.pro` |
| L-BUG-35 | Build | `DriveApiService.listBackups` `pageSize=100` cap no documentado | `DriveApiService.kt:31` |
| L-BUG-36 | Build | `DriveApiService.BACKUP_QUERY` permisivo con `contains` (permite archivos foráneos) | `DriveApiService.kt:49` |
| L-BUG-37 | Health | `LocationTracker` entrega en main looper; un HandlerThread sería más eficiente | `LocationTracker.kt:33` |
| L-BUG-38 | Health | `CardioForegroundService` `distanceKm` re-suma full route cada fix (O(n²)) | `CardioForegroundService.kt:65-77` |
| L-BUG-39 | Health | `HealthConnectManager.bodyEntity` silencia 5 campos (visceral, protein, bone, body age, water %) | `HealthConnectManager.kt:496-521` |
| L-BUG-40 | Domain | `NotificationSettingsUseCase.update` sin `Result`; éxito/fallo indistinguibles | `NotificationSettingsUseCase.kt:14-19` |
| L-BUG-41 | Domain | `NotificationScheduleCalculator.nextWeeklyRunAt` con `isAfter` estricto puede añadir 7d extra | `NotificationScheduleCalculator.kt:28-39` |
| L-BUG-42 | Domain | `RoomNotificationSettingsRepository.update` no transaccional | `RoomNotificationSettingsRepository.kt:18-27` |
| L-BUG-43 | Domain | `WeeklyPlanUseCase.completedThisWeek` por `(type, targetId)` no por `orderIndex` (multi-sesión) | `WeeklyPlanUseCase.kt:45-49` |
| L-BUG-44 | Domain | `RoomWeeklyPlanRepository.completedTrainingKeys` depende de `ZoneId.systemDefault()` por fila | `RoomWeeklyPlanRepository.kt:132-134` |

---

## 5. Mejoras de UI/UX (consolidado, no son bugs)

### 5.1 Home / Dashboard
- **"Hoy" prominente cuando hay plan.** El `TodayWorkoutCard` ya existe, pero la versión
  actual es estática. Considerar un swipe horizontal entre "fuerza de hoy" / "cardio de
  hoy" cuando el día tiene varias sesiones.
- **Empty states diferenciados.** El mismo `EmptyState` se usa para "sin datos" y "error";
  parametrizar con un `EmptyStateKind` (Booting, Empty, Error, NoPermission) con icono y
  copy distintos.
- **Pull-to-refresh** en Home y Progreso con `PullToRefreshBox` (Material 3 1.3+).
- **Errores con acción.** Cada `errorMessageRes` debería renderizarse con icono + botón
  "Reintentar" cuando la causa sea transitoria.
- **Carrusel de periodos con chips explícitos** en vez de tap-ciclar; el `MetricTile` con
  `clickable` no comunica que es cíclico.

### 5.2 Entrenamiento (fuerza)
- **Quick-add con búsqueda.** El `AddExerciseDialog` no tiene search; con 50+ ejercicios
  es inutilizable. Añadir un `OutlinedTextField` con `KeyboardType.Text` y filtrar en
  memoria.
- **Stepper de sets/reps/peso en draft de rutina.** El `CompactNumberField` de 60 dp × 4
  por fila come toda la pantalla en "small". Reemplazar con stepper `–/valor/+` o con
  un campo más compacto.
- **Drag-to-reorder con long-press o handle explícito** (actualmente roba scroll).
- **Mostrar suma de volumen en tiempo real** en el `ProgressCard` superior; muchos
  usuarios lo agradecen.
- **Plantillas de sesión** ("empezar desde rutina en blanco", "duplicar sesión anterior").
- **Historial de pesos por ejercicio** en una fila clickable del `DraftExerciseCard` para
  saber qué peso pusiste la última vez.

### 5.3 Cardio
- **Diálogo de confirmación en BackHandler** (ver C-BUG-1) copiando el patrón de Fuerza.
- **Mostrar pace junto a speed** (min/km) — un solo `Text` calculado: `pace = 60 / speed`.
- **Action "Pausar" en notificación** y en la UI, no solo "Finalizar" / "Cancelar".
- **Encajar bounds en `CardioRouteMap`** con `CameraUpdateFactory.newLatLngBounds`.
- **Mapa más alto en landscape / tablet**; el alto fijo 220 dp es muy pequeño.
- **Botón "Comprobar GPS"** si la primera fix tarda (común en interior).
- **Heatmap del ritmo** (Vico / Map polyline color por pace) — nice-to-have v2.

### 5.4 Progreso
- **Búsqueda + filtro por grupo muscular** en "Ejercicios" y "Grupos".
- **`HistoryDetailCard` como bottom-sheet** o como pantalla completa en vez de
  expandirse inline en el `LazyColumn` (UX rota, el usuario scrolla sin contexto).
- **Comparar dos periodos** (semana actual vs semana previa) con chip toggle.
- **PRs recientes con celebración** (confetti, haptic, sonido opcional) — el componente
  `Récord` ya existe pero la animación es mínima.
- **Empty states distintos** para "sin ejercicios" vs "sin sesiones en este periodo".

### 5.5 Composición corporal
- **Formulario con secciones colapsables** (peso, % grasa, hidratación, etc.) y
  validación inline por campo.
- **Indicador "sincronizado con HC"** por celda (no global) reflejando los permisos
  realmente concedidos.
- **Botón "usar último valor"** para campos como water %, visceral, protein.
- **Comparar con mes anterior** en el header.

### 5.6 Plan semanal
- **Reordenar sesiones** dentro del día (drag handle) — el schema ya lo soporta vía
  `order_index`.
- **Confirmar al marcar "Día de descanso"** (ver H-BUG-3).
- **Plantillas de semana** (semana A / semana B) para planes de periodización.
- **Vista "agenda" (mes)** como segunda pestaña.

### 5.7 Onboarding
- **Iconos por paso** en el body de los 3 pasos de permisos, no solo en el hero central.
- **Racionale diferenciada en `< TIRAMISU`** (Android 12): "Notificaciones siempre
  disponibles" en vez de "Solicitar permiso".
- **Indicador de progreso + total** en el overline ("Paso 4 de 7" en vez de solo "Paso 4").
- **`Skip`** visible en cada paso salvo el último (los strings ya existen).

### 5.8 Backup
- **Indicador de fortaleza de la passphrase** (zxcvbn-style, local, no enviar nada).
- **Toggle "mostrar contraseña"** en `AtlasTextField.Password`.
- **Confirmar passphrase** (segundo campo) en setup inicial, no en restore.
- **Diff pre-restore**: mostrar cuántas filas / qué periodo tiene el backup antes de
  pisar la DB local.
- **Progress bar** para restore con bases grandes (CSV ZIP / JSON encode puede tardar).
- **Frecuencia de auto-backup configurable** (24h, semanal, al cerrar sesión).
- **Tarjeta de "último backup: hace X días"** en Home o Perfil.

### 5.9 Sistema / accesibilidad
- **Soporte real de theme switching** (la infra `ThemeMode` está pero el switch no se
  expone). Agregar en Ajustes.
- **Dynamic type**: confirmar que los heroes no overflowean a 130%-200% de font scale.
  Probable fix: `BoxWithConstraints` + `withAutoSize` o `BasicText` con
  `autoSize`/`TextOverflow.Visible`.
- **Contraste AA** en `ink3` light: cambiar de `#8A8A90` a `#6E6E76` (≈ 4.6:1).
- **contentDescription en todos los iconos decorativos** con `null` correcto (no
  strings vacíos que TalkBack anuncia).
- **StepDots decorativos** con `clearAndSetSemantics`.
- **Targets táctiles ≥ 44dp** verificados en todos los icon-buttons (set completed
  checkbox, move up/down, etc.).

### 5.10 Otros
- **Soporte de tablets** (master-detail en Histórico, dashboard en 2 columnas).
- **Theme dinámico Material You** (Android 12+).
- **Multi-idioma**: se ve bien para ES/EN, pero `values/strings.xml:494-496` mezcla
  frases y fragmentos; homogeneizar.

---

## 6. Mejoras de funcionalidad / workflow

### 6.1 Onboarding
- Pantalla de **resumen antes de "Empezar"** (lo que se ha configurado, lo que se ha
  saltado).
- **Reabrir onboarding** desde Perfil → "Repetir tour" (útil tras años sin usar la app).

### 6.2 Entrenamiento
- **Modo "circuito" / "EMOM" / "AMRAP"** dentro de un entrenamiento de fuerza (cronómetro
  global + varios ejercicios en bucle).
- **Notas por set** (RPE,体感, etc.) con un campo opcional inline.
- **Doble sesión del mismo día** ya está soportado por schema (`order_index`); exponer
  UX para añadir segunda sesión.
- **Plantillas de rutinas públicas** (compartidas por archivo `.json` vía Share Sheet).
- **"Comparte rutina"** entre dispositivos propios (codificar rutina a QR/JSON).

### 6.3 Cardio
- **Entrenamiento por zonas de FC** (Z1-Z5) con objetivo de tiempo en zona, derivado de
  HC `RestingHeartRateRecord` o `HeartRateRecord` + edad del perfil.
- **Pausa / resume** ya soportado en el servicio por el `elapsedSeconds`, pero falta
  botón de pausa explícito en la UI.
- **Sesiones "estructuradas"** (intervalos: 5×(1 min rápido / 2 min suave)) con alertas
  sonoras.
- **Importar cardio desde Wear OS / Garmin** vía HC (parcialmente soportado, exponer
  mejor).

### 6.4 Progreso
- **Heatmap anual** tipo GitHub de sesiones por día (ya se tiene la data).
- **Estadísticas de 1RM estimado** (Epley/Brzycki) por ejercicio.
- **Comparar dos ejercicios** lado a lado.
- **Exportar a CSV / imagen** un gráfico concreto (Share Sheet).

### 6.5 Composición corporal
- **Foto de progreso** (opcional, local, cifrada) con timeline.
- **Mediciones programadas** (recordatorio mensual).
- **Objetivos** (perder X kg, mantener grasa < Y%) con badge en dashboard.

### 6.6 Health Connect
- **Sincronización en background** vía Worker (ahora solo al abrir la app).
- **Configurar granularidad de import** (qué métricas traer, qué rango retroactivo).
- **Resolver conflictos** visualmente en una pantalla ("Atlas Peak dijo X, HC dijo Y, elegí Z").

### 6.7 Backup
- **Multi-destino**: Drive + carpeta local + WebDAV.
- **Versionado semántico de backups** (major / minor) y rotación configurable.
- **Restore selectivo** (solo una tabla, un tipo de sesión).
- **Test de restore en sandbox** (verifica el archivo sin tocar la DB actual).

### 6.8 Notificaciones
- **Snooze** en recordatorios (10 min, 1 h, mañana).
- **"Hecho" desde la notificación** (Action) que marque la sesión como completada en el
  plan semanal.
- **Resumen mensual** (al estilo semanal) con foto, peso y PRs.

### 6.9 Dashboard
- **Widget de Home screen** (Glance) con minutos de la semana, plan de hoy, último peso.
- **Tile de "Quick start"** en el launcher shortcut (long-press icon → "Iniciar entreno").
- **Vista de "resumen anual"** tipo Spotify Wrapped.

### 6.10 Multi-perfil / multi-usuario
- El schema tiene `users` y `user_profile` pero no hay UI para multi-perfil. Decisión
  de producto: ¿se mantiene single-user en local-first o se abre la puerta a
  "perfiles" (familiar, segunda cuenta)?

---

## 7. Mejoras de arquitectura / mantenimiento

- **Inyección de `SecureRandom`** en `BackupFileCodec` y `LocalBackupExportManager` (tests
  deterministas).
- **Refactor de `BodyCompositionSource`**: pasar de `when` con `else -> Manual` a sealed
  cerrado (H-SEC-11).
- **Mappers a `Map<exerciseId, name>`** en repositorios para evitar N+1 (M-8 del review
  de domain).
- **`BackupSnapshotUpgrader` con ROW_NUMBER** equivalente en JSON (H-SEC-1).
- **`HC sync` envuelto en `database.withTransaction`** (H-SEC adicional: read+write
  atómico).
- **`Json` instance única** vía Hilt con `explicitNulls=true` para backup y
  `explicitNulls=false` para red; hoy están duplicados (L-16 en review de seguridad).
- **Eliminar `BackupUseCase` delegación pura** o darle lógica real (L-1 review domain).
- **Eliminar código muerto**: `SecureScreenEffect`, `SensitiveRoutePolicy` (post
  SEC-026) o documentar como no-op.
- **`BackupWorkScheduler` con `setRequiresBatteryNotLow(true)`** y backoff (M-4 review
  seguridad).
- **`HealthPermissionsRationaleActivity` → Dialog en `HomeScreen` o `BodyScreen`** en
  vez de Activity separada (M-34).
- **Mover `HealthConnectRecordMapper` de `data` a `domain`** (no toca Room ni Retrofit,
  es pura transformación).
- **Tests instrumentados** para: `WeeklyPlan` con 2+ sesiones/día, restore de backup
  multi-sesión, `LocationTracker` con fixes de baja precisión, `BodyComposition` con
  datos duplicados HC+manual, `BackupSnapshotUpgrader` con `order_index` no-cero.

---

## 8. Resumen de salud por capa

| Capa | Lo que se hace bien | Lo que falta |
|---|---|---|
| **Build / Gradle** | `libs.versions.toml`, secret properties gitignored, keystore fuera del repo, dependency verification, JaCoCo gate, `packageReleaseUpdate` | `proguard-rules.pro` sin revisar; `secureRandom` no inyectado |
| **Manifest / red** | Sin `ACCESS_BACKGROUND_LOCATION`, sin `google-services.json`, network security config sin cleartext, FGS types correctos | — |
| **Seguridad local** | SQLCipher, Keystore, EncryptedSharedPreferences, PBKDF2 600k, AES-256-GCM con salt+IV por archivo, `FLAG_SECURE` desactivado por decisión | `BackupCredentialStore` materializa `String`; `DriveBackupManager` no zerea `encrypted`/`json` |
| **Drive / Backup** | Formato `ATPK` con header, BackupSnapshotUpgrader, MAX_BACKUPS=5, multipart/related correcto | `OAUTH_WEB_CLIENT_ID` no se usa, retry no transitorio, lista de backups con `contains` laxo |
| **Room / Migrations** | Sin `fallbackToDestructiveMigration`, schemas versionados, test de migraciones | MIGRATION_4_5 con `order_index=0` rompe multi-sesión |
| **Domain / UseCases** | Flujos puros, tests en `test/` | N+1 en repos, `when` con `else` colapsa en source desconocido, validación ausente en varios casos |
| **Health Connect** | Sync parcial por capacidad, `clientRecordId` estable, ventana móvil 30d | `zoneOffsetString` mal, manual vs HC no se reconcilia, bodyEntity pierde 5 campos, exerciseType por string match |
| **Presentation / Compose** | Material 3, theme tokens, componentes `Atlas*` reutilizables, BUG-035/036 (imePadding, bottom bar fix) arreglados | Toast en EditProfile, BackHandler en cardio sin diálogo, setRow checkbox 34 dp, drag steal scroll, contraste `ink3` |
| **FGS / Workers** | Tipos correctos, `VISIBILITY_PRIVATE`, canales separados, `START_NOT_STICKY` documentado | Cardio FGS no arranca sin GPS → sesión perdida; no acción "Stop" en notificación |
| **Onboarding** | 7 pasos, todos saltables salvo el último, persistencia en DataStore | Pasos de permisos con body vacío, kill durante `finish()` pierde `setOnboardingCompleted` |
| **Tests** | Unit (MockK), Turbine, JUnit5, room-testing, >70% gate en domain+data | Faltan tests instrumentados de los bugs críticos de este informe |

---

## 9. Recomendación de plan de acción

### Sprint 1 (parche pre-publicación)
1. C-SEC-1: cablear `OAUTH_WEB_CLIENT_ID` + `setRequestedOfflineAccess` y persistir
   refresh token (2-3 días, crítico).
2. C-SEC-3: filtrar `LocationTracker` por `accuracy` + `now - location.time` y marcar
   `firstFix` (1 día).
3. C-SEC-4: envolver `RoomBackupSnapshotStore.snapshot` en `withTransaction` (0.5 día).
4. H-SEC-1 + H-SEC-2: backfill `order_index` con índice por día en `BackupSnapshotUpgrader`
   + `MIGRATION_4_5` + tests (1 día).
5. C-BUG-1: diálogo de confirmación en `BackHandler` de cardio (0.5 día).
6. C-BUG-2: mover `runBlocking` a `MutableStateFlow`/`init` (0.5 día).
7. H-BUG-3: confirmación al marcar día de descanso (0.5 día).
8. H-BUG-4: pantalla de cambio de passphrase con aviso SEC-002 (0.5 día).
9. H-BUG-7: `LaunchViewModel` con estado de error y timeout (0.5 día).
10. H-BUG-8: `CardioForegroundService` arranca también sin GPS con tipo `data_sync` o
    `health` (0.5 día).

### Sprint 2 (UX + polish pre-publicación)
- H-BUG-1 (touch target del checkbox), H-BUG-2 (Toast → Snackbar), M-21 a M-25 (Home,
  Onboarding, Nav), M-27 (contraste `ink3`), M-29 (bottom bar fluid width),
  M-12 (encuadre de mapa), M-9/10/11 (drag-to-reorder con long-press, validación de
  rutina), M-15/16 (retry + validación body).
- Soporte de theme switching real (L-18).
- Eliminar código muerto (L-7) y documentar `SecureScreenEffect` como no-op.
- i18n: ejecutar `lint --check MissingTranslation`, homogeneizar `nav_cd_*`.

### Sprint 3 (hardening + features)
- Health Connect: reconciliación HC vs manual, zona horaria correcta, `exerciseType`
  en `cardio_types`, restauración de los 5 campos de body composition.
- Backup: retry solo en transitorios, `setRequiresBatteryNotLow`, validación de
  `setServerClientId` antes de subir, lista de backups con regex estricto.
- Calorías: caída de 75 kg → pedir peso en onboarding o mostrar aviso cuando se use
  el fallback.
- Notificaciones: acción "Hecho" desde la notificación, snooze, recordatorio de
  medida mensual.
- Tests instrumentados: `WeeklyPlan` multi-sesión, restore multi-sesión,
  `LocationTracker` con fixes ruidosos, `BodyComposition` HC+manual.

### Post-publicación (v1.1 / v1.2)
- Widget de Home screen (Glance).
- Soporte de tablet master-detail.
- Plantillas de rutinas / intercambio vía QR.
- Multi-perfil (decisión de producto primero).
- Sync en background de Health Connect vía Worker.
- Snap-to-route GPS improvement (Kalman / snap to roads).

---

## 10. Lo que la app hace bien (positivo)

- **Arquitectura limpia hacia adentro** (`presentation → domain → data`) con un
  `StaticArchitecturePolicyTest` que la enforce.
- **Seguridad pasiva fuerte**: SQLCipher, Keystore, EncryptedSharedPreferences,
  PBKDF2 600k, AES-256-GCM, `network_security_config.xml` sin cleartext, FGS types
  correctos, sin `ACCESS_BACKGROUND_LOCATION`, sin `google-services.json`, sin
  tracking externo.
- **Backup format auto-descriptivo** (`ATPK` + versión + iter + salt + IV + ciphertext+tag)
  con `BackupSnapshotUpgrader` y validación de columnas/afinidad en restore.
- **CSV injection protection** en `BackupExportFormatter` (apóstrofe en `=+-@`).
- **Dependency verification** con `gradle/verification-metadata.xml` y SHA pins en CI.
- **i18n desde el primer string**, `androidResources.localeFilters` con es+en.
- **Schema exportado** (`app/schemas/.../{1..5}.json`) y migraciones explícitas.
- **`AtlasListRow`, `AtlasDropdown`, `AtlasDialog`, `AtlasTimeField`, `AtlasSlider`**
  son primitivas reutilizables y testeadas.
- **JaCoCo gate del 70%** en `domain+data` (`jacocoDebugDomainDataCoverageVerification`).
- **Notification `VISIBILITY_PRIVATE`** en canales y builders de FGS.
- **CSV export en claro pide confirmación visible** y limpia temporales.
- **PR idempotente** con `maxSeenByExercise` dentro de la sesión.
- **HC `clientRecordId` estable** (`atlaspeak:type:localId`).
- **`NotificationScheduleCalculator` con `ZoneId.systemDefault()`** y validación de
  formato `HH:mm`.

---

*Atlas Peak — Informe de revisión — `2026-07-01`. No se ha editado nada en el repo.*
