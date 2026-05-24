# CHANGELOG.md — Registro de cambios

> Trazabilidad de todo lo que se hace en el proyecto. Cada sesión de trabajo añade una
> entrada. Formato basado en [Keep a Changelog](https://keepachangelog.com/es/).
> Orden: más reciente arriba.
>
> Tipos: `Añadido` · `Cambiado` · `Corregido` · `Eliminado` · `Seguridad` · `Deprecado`

---

## [No publicado]

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
