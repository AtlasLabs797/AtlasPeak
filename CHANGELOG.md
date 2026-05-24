# CHANGELOG.md — Registro de cambios

> Trazabilidad de todo lo que se hace en el proyecto. Cada sesión de trabajo añade una
> entrada. Formato basado en [Keep a Changelog](https://keepachangelog.com/es/).
> Orden: más reciente arriba.
>
> Tipos: `Añadido` · `Cambiado` · `Corregido` · `Eliminado` · `Seguridad` · `Deprecado`

---

## [No publicado]

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
