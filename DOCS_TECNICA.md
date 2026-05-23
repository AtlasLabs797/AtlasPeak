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

---

## 2. Módulos

- **`app/`** — módulo principal (teléfono). Todo v1 vive aquí.
- **`wear/`** — *aplazado a v2*. No está en `settings.gradle.kts` todavía. El protocolo de
  comunicación está diseñado (ver `SPEC.md §2.10`) para reactivarlo sin rediseño.

---

## 3. Estructura de paquetes (`com.atlaspeak`)

```
data/
  db/            AppDatabase (SQLCipher), dao/, entity/
  repository/    implementaciones de las interfaces de domain
  healthconnect/ HealthConnectManager (permisos, import, export)
  drive/         DriveApiService (Retrofit), DriveBackupManager (serializar/cifrar/subir)
  location/      LocationTracker (FusedLocationProvider → Flow<LatLng>)
  security/      EncryptionManager (Keystore, AES-256-GCM, PBKDF2)
domain/
  model/         data classes puras (sin anotaciones)
  repository/    interfaces (contratos)
  usecase/       auth/ workout/ cardio/ progress/ body/ healthconnect/ backup/ plan/
presentation/
  screen/        auth/ onboarding/ home/ workout/ cardio/ progress/ body/ plan/ profile/
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

- **Login:** contraseña local con PBKDF2-HMAC-SHA256, **600.000 iter**, salt de 32 bytes.
  Hash y salt en la DB SQLCipher. Ejecución en `Dispatchers.IO`.
- **Biometría:** `BiometricPrompt` clase `BIOMETRIC_STRONG`. Solo desbloqueo, no auth nueva.
  Timeout configurable (1/5/15/nunca). Re-pide al volver a foreground tras el timeout.
- **Rate limiting:** 5 intentos → bloqueo 15 min. Contador en tabla `auth_security` (DB cifrada).
- **`FLAG_SECURE`** en Login, Biometría, Perfil, Backup.
- **Red:** solo HTTPS (`network_security_config.xml`, sin cleartext). Drive con
  `Authorization: Bearer {token}`; token OAuth en `EncryptedSharedPreferences`.
- **Backup cifrado** (formato corregido, SEC-001):
  ```
  [magic "ATPK" (4B)] [versión (1B)] [iteraciones (4B BE)] [salt (16B)] [IV (12B)] [ciphertext+tag GCM]
  ```
  Clave = PBKDF2(contraseña, salt-del-archivo, iteraciones-del-archivo). El salt viaja en el
  archivo (no es secreto) → permite restaurar en otro dispositivo. AES-256-GCM (tag de 16B
  incluido por el proveedor JCE).
- **Secretos:** `MAPS_API_KEY` y `OAUTH_WEB_CLIENT_ID` en `secrets.properties` (gitignored),
  inyectados via `manifestPlaceholders` y `BuildConfig`. **Sin `google-services.json`.**

---

## 6. Autenticación y Google (aclaración importante)

No hay backend, así que "iniciar sesión" no autentica contra ningún servidor de Atlas Peak.
El gate real es la **contraseña local**. Google Identity Services (Credential Manager) sirve
**solo** para obtener el token OAuth con scope `drive.appdata` y poder hacer backup. Por eso
Google es **opcional** y el onboarding lo permite saltar; la app funciona 100% offline sin él.

---

## 7. Foreground Services

- **WorkoutForegroundService** (`foregroundServiceType=health`): cronómetro de sesión de
  fuerza persistente + notificación. Expone `StateFlow<WorkoutTimerState>`; el ViewModel se
  suscribe via `bindService()`. Requiere `FOREGROUND_SERVICE_HEALTH` y
  `ACTIVITY_RECOGNITION` en Android 14+. Se destruye al completar/abandonar.
- **CardioForegroundService** (`foregroundServiceType=location`): recibe ubicaciones de
  `LocationTracker`, mantiene cronómetro + notificación, expone `StateFlow<CardioSessionState>`.
  **No** requiere `ACCESS_BACKGROUND_LOCATION` porque se inicia con la app visible.
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
- `hc_sync_log` registra último read/write por tipo. **Conflicto:** gana el timestamp más
  reciente; no se sobreescribe lo local si es más nuevo.
- Báscula inteligente: integración **indirecta** (app de la báscula → HC → Atlas Peak).

---

## 9. Backup / Drive

- `DriveApiService` (Retrofit) habla con Drive REST API v3, scope `drive.appdata` (carpeta
  privada de la app, invisible al usuario).
- `DriveBackupManager`: serializa la DB a JSON (Kotlinx) → cifra (formato §5) → sube.
- `BackupWorker` (WorkManager): backup diario si hubo cambios desde el último.
- Máximo **5 backups**; al crear el sexto se borra el más antiguo.
- Restore: listar → descargar → leer cabecera → derivar clave → descifrar → transacción Room
  (reemplazo completo de datos).

---

## 10. Cálculo de calorías (cross-table, ojo)

El GPS aporta **distancia/velocidad**, no calorías. La estimación necesita el **peso** del
usuario, que NO está en `user_profile` sino en el último registro de `body_composition`.
Orden de preferencia: (1) Health Connect si hay dato; (2) estimación por MET × peso ×
duración; (3) fallback por tipo de ejercicio y duración. Documentar la fórmula MET usada
en el código.

---

## 11. Estado, concurrencia y errores

- UI: un `data class XxxUiState` por pantalla; ViewModel expone `StateFlow`. La UI usa
  `collectAsStateWithLifecycle`.
- IO/cripto/PBKDF2: siempre `Dispatchers.IO`. Nunca en main thread.
- Errores: tipo `Result` sellado en domain; no se propagan excepciones entre capas.

---

## 12. Testing

- **Unit (MockK):** UseCases, ViewModels, `EncryptionManager`, lógica de conflictos HC.
- **Integración (Room in-memory):** DAOs, repos, migraciones.
- **Flows (Turbine):** StateFlows de ViewModels, emisiones de `LocationTracker`.
- **Compose UI:** pantallas críticas (ActiveWorkout, Login, Onboarding).
- **WorkManager:** workers con `work-testing`.
- Objetivo: **≥70%** cobertura en `domain` y `data`.

---

## 13. Build, CI y release

- Versiones **solo** en `gradle/libs.versions.toml` (version catalog).
- CI (GitHub Actions): `assembleDebug` + `test` + `lint` en cada push/PR.
- Release: AAB firmado con keystore local (`keystore.properties`, fuera del repo). R8/ProGuard
  activo (reglas para Room, Hilt, Retrofit, Kotlinx Serialization, SQLCipher).
- Crash reporting: **Android Vitals** (Play Console), sin SDK.

---

## 14. Feature flags

`feature/FeatureFlags.kt`. v1 todo gratuito. Las pantallas con features potencialmente
premium consultan el flag antes de renderizar contenido restringido. Cambiar a premium en el
futuro = cambiar la fuente de los flags, sin tocar UI.

---

*Atlas Peak — DOCS_TECNICA.md — alineado con SPEC.md v2.2*
