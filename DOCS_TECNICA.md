# DOCS_TECNICA.md — Documentación técnica de Atlas Peak

> Documentación para **desarrolladores/técnicos**. Arquitectura, contratos, decisiones.
> Se actualiza cuando cambian estructura, capas o contratos. El detalle funcional vive en
> `SPEC.md`; aquí está el "cómo está construido por dentro".

---

## 1. Visión de alto nivel

App Android nativa, **local-first**, sin backend. Kotlin + Jetpack Compose + Material 3.
Arquitectura limpia en tres capas con dependencias hacia adentro:

```
presentation  →  domain  →  data
  Compose         UseCases    Room + SQLCipher
  ViewModels      Repos(int)  Health Connect
  Navigation      Models      Drive REST (Retrofit)
                              Location / (Wear: v2)
```

Regla dura: `presentation` no conoce Room ni Retrofit. Mapea siempre a **domain models**.

Tema/UI: los tokens viven en `presentation/theme/`. Las fuentes se empaquetan localmente en
`app/src/main/res/font/` (Poppins 600/700 e Inter variable) para cumplir `DESIGN.md` sin
descargas runtime. El origen/licencia esta en `THIRD_PARTY_NOTICES.md`.

---

## 2. Módulos

- **`app/`** — módulo principal (teléfono). Todo v1 vive aquí.
- **`wear/`** — *aplazado a v2*. No está en `settings.gradle.kts` todavía. El protocolo de
  comunicación está diseñado (ver `SPEC.md §2.10`) para reactivarlo sin rediseño.

---

## 3. Estructura de paquetes (`com.atlaspeak`)

```
data/
  backup/       Snapshot Room, codec ATPK, export local, DriveBackupManager, BackupWorker,
                DataBackupRepository
  db/            AppDatabase (SQLCipher), dao/, entity/
  repository/    implementaciones de las interfaces de domain
  healthconnect/ HealthConnectManager (permisos, import, export)
  drive/         DriveApiService (Retrofit), AuthorizationClient/Drive token provider
  location/      LocationTracker (FusedLocationProvider → Flow<LatLng>)
  security/      EncryptionManager (Keystore, AES-256-GCM, PBKDF2)
domain/
  model/         data classes puras (sin anotaciones)
  repository/    interfaces (contratos)
  usecase/       auth/ workout/ cardio/ progress/ body/ healthconnect/ backup/ plan/
presentation/
  auth/          legado de auth local no enrutable en v1
  onboarding/    OnboardingScreen, OnboardingViewModel
  navigation/    Launch gate, NavHost, bottom navigation, secure route effect, session lock
  workout/       TrainScreen, TrainViewModel (biblioteca de ejercicios, rutinas y cardio)
  cardio/        ActiveCardioScreen, CardioCompleteScreen y ViewModels
  progress/      ProgressScreen, ProgressViewModel (historial y graficas)
  screen/        home/ workout/ cardio/ body/ plan/ profile/
  component/     composables reutilizables (MetricCard, PeriodSelector, Chart, RestTimer…)
  viewmodel/     1 ViewModel por feature; expone StateFlow<UiState>
  theme/         Theme, Color, Typography, Shape, Spacing
service/         WorkoutForegroundService, CardioForegroundService
worker/          BackupWorker, DailySummaryWorker, WeeklySummaryWorker, TrainingReminderWorker
di/              módulos Hilt (Database, Network, Repository, Security…)
feature/         FeatureFlags
util/            extensiones, formatters, constantes
MainActivity.kt
```

---

## 4. Persistencia

- **Room + SQLCipher** (`net.zetetic:sqlcipher-android`). Cifrado transparente.
- **Clave de la DB:** generada aleatoriamente en el primer arranque. Se protege con una
  clave del **Android Keystore** (wrap/unwrap), nunca se guarda en texto plano. La clave del
  Keystore no se exporta del dispositivo.
- **`exportSchema = true`** con `room.schemaLocation` configurado en `app/build.gradle.kts`
  (ksp arg). Los JSON de schema se versionan en `app/schemas/` para trackear cambios.
- **Migraciones:** explícitas, una `Migration(n, n+1)` por cambio. `fallbackToDestructiveMigration()`
  prohibido fuera de tests. Cada migración tiene su test (Room `MigrationTestHelper`).

### Modelo de datos (resumen — schema completo en `SPEC.md §4`)

20 tablas. Núcleo: `users`, `user_profile`, `muscle_groups` (seed), `exercises`,
`routines`, `routine_exercises`, `workout_sessions`, `workout_sets`, `cardio_types` (seed),
`cardio_sessions`, `body_composition`, `weekly_plan`, `hc_sync_log`, `app_settings`
(singleton), `auth_security` (singleton), `hc_steps_records`,
`hc_active_calories_records`, `hc_sleep_sessions`, `hc_sleep_stages` y
`hc_heart_rate_samples`.

Notas de integridad:
- Timestamps `Long` epoch ms UTC.
- IDs `UUID` string (salvo seeds con `INTEGER PK`).
- Soft delete via `is_archived` en `exercises`, `routines`, `cardio_types`. No DELETE físico.
- `total_volume_kg` en `workout_sessions` está **pre-calculado** (rendimiento de dashboard);
  recalcular si se editan sets de una sesión pasada.
- `is_personal_record` en `workout_sets` es dato **derivado** denormalizado: si se borra/edita
  una sesión con PR, hay que recalcular los PRs afectados (no se auto-promueve el siguiente).

---

## 5. Seguridad (detalle en `SECURITY.md`)

- **Entrada a la app:** no hay contraseña local ni pantalla de login enrutable. `AtlasPeakNavHost`
  arranca en `Launch`; si `onboarding_completed=false` navega a `Onboarding`, y si ya está
  completado navega al grafo autenticado `AppGraph`, cuyo start destination es `Home`.
  La bottom navigation hace `popUpTo(AppGraph)` sin `saveState/restoreState`: tocar un tab
  abre siempre su ruta raíz (`Home`, `Train`, `Progress`, `Body` o `Profile`), no una
  subpantalla restaurada.
- **Google:** no hay login Google para entrar. Drive usa `AuthorizationClient` con scope
  `drive.appdata`; un ID token Google no es un bearer token valido para Drive y nunca
  desbloquea la DB local.
- **Biometría:** no se usa para desbloqueo en v1 porque no hay gate local.
- **Capturas:** permitidas en toda la app por decision de producto (SEC-026). `SecureScreenEffect`
  limpia `FLAG_SECURE`; `SensitiveRoutePolicy` solo clasifica rutas sensibles para auditoria.
- **Red:** solo HTTPS (`network_security_config.xml`, sin cleartext). Drive REST usa
  `Authorization: Bearer {access_token}` obtenido por `AuthorizationClient`; Atlas Peak no
  reutiliza ID tokens como credenciales Drive.
- **Backup cifrado** (formato corregido, SEC-001):
  ```
  [magic "ATPK" (4B)] [versión (1B)] [iteraciones (4B BE)] [salt (16B)] [IV (12B)] [ciphertext+tag GCM]
  ```
  Clave = PBKDF2(passphrase de backup, salt-del-archivo, iteraciones-del-archivo). El salt viaja en el
  archivo (no es secreto) → permite restaurar en otro dispositivo. AES-256-GCM (tag de 16B
  incluido por el proveedor JCE).
- **Backup automatico:** opt-in. Para cifrar sin pedir contrasena cada dia, la contrasena de
  backup se guarda cifrada en `EncryptedSharedPreferences` protegido por Keystore. Si no hay
  grant silencioso de Drive o contrasena guardada, el worker termina sin lanzar UI.
- **Export manual:** JSON/CSV sin cifrar no exige contraseña tras retirar el gate local; excluye
  `users` y `auth_security` para no compartir restos de auth legada.
  El CSV neutraliza celdas textuales que puedan interpretarse como formulas en hojas de
  calculo (`=`, `+`, `-`, `@`) anteponiendo apostrofe en la salida.
  El restore valida tablas y columnas contra el schema actual antes de insertar datos.
- **Secretos:** `MAPS_API_KEY` y `OAUTH_WEB_CLIENT_ID` en `secrets.properties` (gitignored),
  inyectados via `manifestPlaceholders` y `BuildConfig`. **Sin `google-services.json`.**

---

## 6. Autenticación y Google (aclaración importante)

No hay backend, así que Atlas Peak no tiene inicio de sesión propio. Google Identity
`AuthorizationClient` sirve **solo** para obtener el token OAuth con scope `drive.appdata` y
poder hacer backup. Por eso Google es **opcional** y el onboarding lo permite saltar; la app
funciona 100% offline sin él. La obtencion de permiso Drive sucede en la pantalla de Backup.

## 6.1 Onboarding

- Flujo fullscreen de 7 pasos: bienvenida, Google opcional, perfil, notificaciones,
  Health Connect, ubicación y listo.
- `onboarding_completed` vive en DataStore (`PreferencesOnboardingRepository`), no en Room.
- `ProfileRepository` mapea `UserProfile` domain a `user_profile`; si el perfil se salta no
  se crea una fila falsa.
- Permisos solicitados solo desde su paso: `POST_NOTIFICATIONS` en Android 13+, Health Connect
  con `PermissionController.createRequestPermissionResultContract()`, y ubicación fina para
  cardio GPS. Sin `ACCESS_BACKGROUND_LOCATION`.

## 6.2 Ejercicios y rutinas

Fase 4 activa el tab `Train` con `TrainScreen`/`TrainViewModel`, sin saltarse capas:

- `presentation.workout` solo habla con `ExerciseUseCase` y `RoutineUseCase`.
- `domain.model.workout` contiene `MuscleGroup`, `Exercise`, `Routine` y entradas de rutina.
- `domain.repository` define `ExerciseRepository` y `RoutineRepository`.
- `RoomExerciseRepository` y `RoomRoutineRepository` mapean entidades Room a modelos de dominio.
- `ReferenceDao`, `ExerciseDao` y `RoutineDao` exponen lectura, upsert y soft delete.

La biblioteca permite buscar por nombre, filtrar por grupo muscular, crear/editar ejercicios
custom y archivarlos. Los presets nunca se eliminan desde UI; los custom usan
`is_archived = true`.

Las rutinas permiten crear/editar nombre, color tag, ejercicios, series, reps, peso objetivo,
descanso y orden. El orden se puede cambiar con drag vertical sobre cada ejercicio y tambien
con controles subir/bajar accesibles. El guardado preserva `created_at` cuando se actualiza
una rutina o ejercicio existente.

La duracion estimada se calcula en dominio con un minuto base por serie mas el descanso
configurado por serie, acumulado y truncado a minutos enteros. No cambia schema Room en esta
fase porque las tablas `exercises`, `routines` y `routine_exercises` ya estaban en v1.

## 6.3 Cardio

Fase 6 activa cardio dentro del tab `Train` y las pantallas fullscreen:

- `domain.model.cardio` contiene `CardioType`, `CardioMode`, `LocationPoint` y
  `CardioSession`. `CardioType.iconName` viene de `cardio_types.icon_name` para que la UI
  represente cada tipo con iconografia especifica y no con un icono generico.
- `CardioUseCase` crea tipos custom, arranca sesiones timer/countdown y completa sesiones
  calculando distancia Haversine, velocidad media/maxima, ruta y calorias estimadas.
- `RoomCardioRepository` mapea `cardio_types`/`cardio_sessions` y usa `workout_sessions`
  como cabecera comun de sesiones. El historial se ordena por `workout_sessions.start_time`,
  no por UUID.
- `TrainScreen` muestra tipos predefinidos/custom con iconos por tipo, edicion/archivado de
  custom, selector de minutos para countdown e historial/detalle de cardio.
- `ActiveCardioScreen` pide `ACCESS_FINE_LOCATION` solo si el tipo usa GPS; si no hay GPS o
  se deniega ubicacion, permite introducir distancia y velocidad media manuales.
- `CardioCompleteScreen` muestra resumen y, si hay ruta, un mapa con Google Maps Compose.

La estimacion de calorias en Fase 6 usa fallback MET por tipo y peso fijo de 75 kg porque
la integracion real con `body_composition` llega en Fase 8 y Health Connect en Fase 10.

## 6.4 Progreso

Fase 7 reemplaza el placeholder de `Progress` por una pantalla real con tres tabs internos:

- **Historial:** combina sesiones completadas de fuerza (`WorkoutRepository`) y cardio
  (`CardioRepository`), ordena por `startTime DESC`, filtra por periodo/tipo y busca por
  rutina, tipo de cardio o nombre de ejercicio.
- **Ejercicios:** agrega sets completados por sesion y ejercicio, con maximo de peso,
  volumen por sesion, volumen total y graficas Vico de peso/volumen.
- **Grupos musculares:** agrupa los mismos puntos por grupo principal y secundario del
  ejercicio, mostrando cada ejercicio con su grafica individual de volumen.

`ProgressUseCase` vive en dominio y solo depende de repositorios de dominio; no importa Room,
entities ni Compose. `ProgressViewModel` recalcula graficas solo cuando cambia el periodo y
refresca el historial de forma independiente al cambiar busqueda/tipo para evitar trabajo
innecesario en pantallas con mucho historial.

`WorkoutRepository` expone solo sesiones de fuerza: aunque `workout_sessions` es cabecera
comun para fuerza/cardio, `RoomWorkoutRepository` filtra `type = 'STRENGTH'`. Cardio se lee
exclusivamente por `CardioRepository`, evitando duplicados en historiales unificados.

La ruta GPS de cardio se renderiza con `CardioRouteMap`, componente reutilizado por
`CardioCompleteScreen` y el detalle de historial de Progreso. No se anaden permisos,
networking ni schema Room en esta fase.

## 6.5 Dashboard / Home

Fase 8 reemplaza el placeholder de `Home` por un dashboard local-first:

- `domain.model.dashboard` contiene periodos, widgets, filtros individuales y el snapshot
  agregado que consume la UI.
- `DashboardUseCase` agrega datos desde `DashboardRepository`; dominio no importa Room ni
  Compose y no carga historiales completos de entrenamiento.
- `DashboardRepository` expone solo lecturas ya existentes: plan semanal, peso corporal,
  pasos, muestras de frecuencia cardiaca y sueño.
- `RoomDashboardRepository` usa `DashboardDao` sobre las tablas v1 ya creadas en Fase 1, por
  lo que no hay migracion ni bump de schema.
- `HomeScreen` muestra la sesion planificada para hoy cuando existe: rutina de fuerza o
  cardio. El CTA arranca `ActiveWorkout` para fuerza o `ActiveCardio` en countdown para
  cardio planificado.
- `HomeScreen` muestra minutos de entrenamiento esta semana, volumen, consistencia, tiempo
  total de actividad, peso corporal, pasos diarios, frecuencia cardiaca y sueño con periodos
  independientes por widget.
- `HomeViewModel` combina `DashboardUseCase` con `WeeklyPlanUseCase` para resolver la sesion
  planificada de hoy sin filtrar entities Room en presentation.
- `PeriodSelector` queda como componente compartido para Dashboard y Progreso, en variante
  compacta para no ocupar todo el ancho de las cards.

El widget de frecuencia cardiaca muestra el minimo diario de las muestras importadas en
`hc_heart_rate_samples`; no se etiqueta como frecuencia en reposo hasta que Fase 10 anada una
cache dedicada para `RestingHeartRateRecord`. Los pasos se parten proporcionalmente por dia
local cuando un intervalo cruza medianoche o el inicio del periodo.

## 6.6 Composicion corporal

Fase 9 reemplaza el placeholder de `Body` por una pantalla real sobre la tabla v1
`body_composition`:

- `domain.model.body` contiene entradas, metricas, fuentes (`Manual`, `HealthConnect`,
  `ScaleApp`), periodos, ultimos valores y puntos de evolucion.
- `BodyCompositionUseCase` valida entrada manual, guarda con `source = Manual` y
  `syncedToHealthConnect = false`, calcula el ultimo valor por metrica y genera series por
  periodo.
- `BodyCompositionRepository` abstrae Room; `RoomBodyCompositionRepository` mapea
  `BodyCompositionEntity` sin exponer entities a presentation.
- `BodyCompositionScreen` muestra tabla de valores actuales, selector de periodo, selector de
  metrica, grafica Vico y formulario manual con todos los campos del spec.
- Las metricas preparadas para Health Connect son peso, grasa corporal, masa muscular y masa de
  agua corporal. `% agua corporal` queda manual: Health Connect expone `BodyWaterMassRecord`
  como masa, no como porcentaje.
  Grasa visceral, proteina, masa osea y edad corporal son solo manuales hasta nueva decision
  de producto.

La pantalla muestra datos de salud, pero las capturas estan permitidas por SEC-026. Desde
Fase 10 la tarjeta Health Connect permite sincronizar y volver a pedir permisos si fueron
revocados.

---

## 7. Foreground Services

- **WorkoutForegroundService** (`foregroundServiceType=health`): cronómetro de sesión de
  fuerza persistente + notificación. Expone `StateFlow<WorkoutTimerState>`; el ViewModel se
  suscribe via `bindService()`. Requiere `FOREGROUND_SERVICE_HEALTH` y
  `ACTIVITY_RECOGNITION` en Android 14+. En la implementacion actual el estado se publica via
  `WorkoutTimerRegistry`, `ActiveWorkout` solicita `ACTIVITY_RECOGNITION` antes de arrancarlo,
  y el servicio se detiene al completar la sesion. Si `startForeground` falla por permisos
  runtime, la pantalla activa sigue funcionando y muestra un aviso.
- **CardioForegroundService** (`foregroundServiceType=location`): mantiene cronometro GPS
  + notificacion y expone `StateFlow<CardioTrackerState>`. Si la sesion usa GPS y hay
  `ACCESS_FINE_LOCATION`, arranca como tipo `location` y recibe ubicaciones de
  `LocationTracker`. Si el tipo es manual o el usuario deniega ubicacion, `ActiveCardio`
  degrada a cronometro local y exige distancia/velocidad manuales antes de guardar. **No**
  requiere `ACCESS_BACKGROUND_LOCATION` porque se inicia con la app visible.
- Android 14/15: los `foregroundServiceType` se declaran en el `<service>` del manifest y se
  respetan las restricciones de lanzamiento de FGS desde background.

---

## 8. Health Connect

- Artifact: **`androidx.health.connect:connect-client`** (estable). En Android 14+ es módulo
  del framework (sin setup); en 13 y anteriores se apoya en la app Health Connect.
- **Lectura:** `READ_STEPS`, `READ_ACTIVE_CALORIES_BURNED`, `READ_SLEEP`, `READ_HEART_RATE`.
- **Escritura:** `WRITE_EXERCISE`, `WRITE_WEIGHT`, `WRITE_BODY_FAT`, `WRITE_LEAN_BODY_MASS`,
  `WRITE_BODY_WATER_MASS`.
- `HealthConnectManager` centraliza permisos, import (→ Room) y export (→ HC records).
- Importa pasos diarios y calorias activas mediante agregados diarios para evitar doble conteo
  por origen; importa sueño y frecuencia cardiaca como records crudos paginados.
- Exporta sesiones completadas como `ExerciseSessionRecord`; exporta peso, grasa corporal,
  masa magra y masa de agua corporal con `clientRecordId` estable `atlaspeak:<tipo>:<id>`.
- Las lecturas rehacen una ventana movil de 30 dias: se borra la cache local del rango y se lee
  de nuevo para reflejar cambios o borrados recientes sin pedir `READ_HEALTH_DATA_HISTORY`.
  No se piden permisos de lectura corporal.
- `hc_sync_log` registra último read/write por tipo. **Conflicto:** gana el timestamp más
  reciente; no se sobreescribe lo local si es más nuevo.
- Báscula inteligente: integración **indirecta**. En v1 Atlas Peak no pide permisos de lectura
  corporal de Health Connect; solo exporta métricas corporales introducidas en la app. Leer peso
  o composición desde apps de báscula requiere ampliar permisos y Play Console.

---

## 9. Plan semanal y notificaciones

- `ProfileScreen` reemplaza el placeholder y enlaza a `WeeklyPlanScreen`, `SettingsScreen`
  de notificaciones y backup. Toda la zona autenticada puede aparecer en capturas porque
  `FLAG_SECURE` esta desactivado por SEC-026.
- `WeeklyPlanUseCase` normaliza siete dias ISO (`1=Lunes ... 7=Domingo`), valida `HH:mm`,
  soporta dias de fuerza, cardio o descanso, convierte descansos en filas sin sesion/
  recordatorio y reprograma notificaciones al guardar cada dia.
- `RoomWeeklyPlanRepository` usa `weekly_plan` v4: `type`, `routine_id`, `cardio_type_id` y
  `cardio_target_duration_sec` permiten planificar fuerza o cardio sin crear rutinas falsas.
  La marca visual de completado sale de `workout_sessions.completed` dentro de la semana local
  actual.
- El seeder inicial crea un plan por defecto de 5 dias: cuatro rutinas de fuerza
  tren inferior/superior y un miercoles de cardio de 45 min en bici estatica; usa IDs estables
  e inserciones `IGNORE` para no sobrescribir planes editados por el usuario.
- `NotificationSettingsUseCase` y `RoomNotificationSettingsRepository` usan `app_settings`
  para el control global, mensajes motivacionales, resumen diario, hora diaria y resumen semanal.
- WorkManager usa Hilt: `AtlasPeakApplication` implementa `Configuration.Provider`, inyecta
  `HiltWorkerFactory` y el manifest mantiene desactivado el initializer por defecto.
- Canales Android separados: `training_reminders`, `motivational_messages` y `summaries`.
  Los canales de foreground services (`active_workout`, `active_cardio`) no se reutilizan.
- Scheduler: `WorkManagerNotificationScheduler` cancela y recrea trabajos unicos con nombres
  estables (`training_reminder_1..7`, `daily_summary`, `weekly_summary`, `motivational_message`).
  Usa `OneTimeWorkRequest` para el siguiente disparo y los workers reprograman al terminar.
- Los horarios son **best-effort**. WorkManager no garantiza una alarma exacta; Atlas Peak no
  solicita `SCHEDULE_EXACT_ALARM` en v1 porque seria friccion innecesaria para recordatorios
  de fitness.
- Antes de notificar se comprueba `POST_NOTIFICATIONS`/`NotificationManagerCompat`. Si el
  permiso esta denegado, el worker termina sin notificar ni entrar en bucles de retry.

---

## 10. Backup / Drive

- `DriveApiService` (Retrofit) habla con Drive REST API v3 sobre `appDataFolder`.
  La subida usa `uploadType=multipart` con cuerpo `multipart/related`: metadata JSON primero,
  binario cifrado despues. `multipart/form-data` no es valido para este endpoint.
- `GoogleDriveAuthorizationClient` solicita `drive.appdata` desde UI con `AuthorizationClient`;
  `GoogleDriveAccessTokenProvider` solo intenta grant silencioso para el worker.
- `RoomBackupSnapshotStore` vuelca/restaura las 20 tablas de Room. Restore borra en orden
  inverso de FK e inserta en orden de schema dentro de una transaccion.
- `BackupSnapshotUpgrader` eleva backups schema v2 a v3 anadiendo las columnas nuevas de
  planificacion cardio en `weekly_plan` con defaults compatibles (`STRENGTH` y `NULL`).
- `DriveBackupManager`: snapshot DB completo -> JSON Kotlinx -> ATPK/AES-256-GCM -> upload.
- `BackupWorker` (WorkManager): diario, con red, solo si auto-backup esta activo, hay token
  Drive silencioso, contrasena guardada y hash estable de snapshot distinto. El hash ignora
  `app_settings.last_backup_at` para no subir un backup diario solo porque el anterior actualizo
  esa marca.
- Maximo **5 backups**; tras subir correctamente se lista y borra el mas antiguo sobrante.
- Restore: listar -> descargar -> leer cabecera -> derivar clave -> descifrar -> validar
  schema/tablas -> transaccion Room (reemplazo completo de datos).
- `LocalBackupExportManager` crea archivos en `filesDir/exports` y los comparte con
  `FileProvider`. El backup local `.enc` es cifrado; JSON/CSV ZIP son exports manuales sin
  auth secrets.

---

## 11. Cálculo de calorías (cross-table, ojo)

El GPS aporta **distancia/velocidad**, no calorías. La estimación necesita el **peso** del
usuario, que NO está en `user_profile` sino en el último registro de `body_composition`.
Orden de preferencia: (1) Health Connect si hay dato; (2) estimación por MET × peso ×
duración; (3) fallback por tipo de ejercicio y duración. Documentar la fórmula MET usada
en el código.

---

## 12. Estado, concurrencia y errores

- UI: un `data class XxxUiState` por pantalla; ViewModel expone `StateFlow`. La UI usa
  `collectAsStateWithLifecycle`.
- IO/cripto/PBKDF2: siempre `Dispatchers.IO`. Nunca en main thread.
- Errores: tipo `Result` sellado en domain; no se propagan excepciones entre capas.

---

## 13. Testing

- **Unit (MockK):** UseCases, ViewModels, `EncryptionManager`, lógica de conflictos HC.
- **Integración (Room in-memory):** DAOs, repos, migraciones.
- **Flows (Turbine):** StateFlows de ViewModels, emisiones de `LocationTracker`.
- **Compose UI:** pantallas críticas (ActiveWorkout, Onboarding, Backup).
- **WorkManager:** workers con `work-testing`.
- Objetivo: **≥70%** cobertura en `domain` y `data`.
- Gate local/CI: `./gradlew jacocoDebugDomainDataCoverageVerification`.
  - Reporte HTML: `app/build/reports/jacoco/jacocoDebugDomainDataReport/html/index.html`.
  - La tarea mide el core testeable en JVM (`domain`, mappers, codecs, use cases y managers
    puros de `data`) y excluye bordes que requieren runtime Android/emulador: Room adapters,
    WorkManager wrappers, Health Connect client, notificaciones Android, FileProvider,
    Credential/Keystore y GPS.
  - Esos bordes quedan cubiertos por tests instrumentados/compilacion `compileDebugAndroidTestKotlin`
    y por `connectedAndroidTest` cuando haya dispositivo o AVD disponible.

---

## 14. Build, CI y release

- Versiones **solo** en `gradle/libs.versions.toml` (version catalog).
- CI (GitHub Actions): `assembleDebug` + `test` + `jacocoDebugDomainDataCoverageVerification` +
  `lint` en cada push/PR.
- Release: AAB firmado con keystore local (`keystore.properties`, fuera del repo). R8/ProGuard
  activo (reglas para Room, Hilt, Retrofit, Kotlinx Serialization, SQLCipher).
- APK de actualizacion local: `.\gradlew.bat :app:packageReleaseUpdate`. Genera
  `build/distribution/AtlasPeak-<versionName>-release.apk`, `install-adb.bat`,
  `SHA256SUMS.txt` y `README-INSTALACION.txt`.
- Para conservar datos al actualizar deben cumplirse tres cosas: mismo `applicationId`
  (`com.atlaspeak`), mismo keystore de release y `versionCode` superior. Una build debug usa
  `com.atlaspeak.debug`; no actualiza la app release.
- Crash reporting: **Android Vitals** (Play Console), sin SDK.

---

## 14. Feature flags

`feature/FeatureFlags.kt`. v1 todo gratuito. Las pantallas con features potencialmente
premium consultan el flag antes de renderizar contenido restringido. Cambiar a premium en el
futuro = cambiar la fuente de los flags, sin tocar UI.

---

*Atlas Peak — DOCS_TECNICA.md — alineado con SPEC.md v2.2*
