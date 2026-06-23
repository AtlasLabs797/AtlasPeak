# SECURITY.md — Registro de seguridad

> Log de problemas de seguridad detectados y su resolución, más el modelo de amenazas y
> los checklists permanentes. Cada hallazgo deja rastro aquí.
>
> Estados: `🔴 Abierto` · `🟡 Mitigando` · `🟢 Resuelto` · `🔵 Aceptado (riesgo conocido)`

---

## 1. Modelo de amenazas (resumen)

Atlas Peak es **local-first** sin backend propio. Las superficies de ataque reales son:

Decision vigente (SEC-026): las capturas de pantalla estan permitidas en toda la app.
Esto reduce privacidad frente a screenshots, screen recording y vista de recientes.

1. **Acceso físico al dispositivo desbloqueado** → riesgo aceptado tras retirar el gate de
   contraseña; la defensa real es el bloqueo del dispositivo. Las capturas estan permitidas
   por SEC-026.
2. **Extracción de la DB del dispositivo** → mitigado con SQLCipher (clave en Keystore).
3. **Backup en Google Drive comprometido** (cuenta Google hackeada / Google interno) →
   mitigado con AES-256-GCM y clave derivada de contraseña (Google solo ve binario cifrado).
   **Aquí el atacante puede hacer fuerza bruta offline** → por eso PBKDF2 con 600k iter.
4. **Secretos en el repositorio** (Maps key, OAuth id, keystore) → mitigado con
   `.gitignore` + `secrets.properties`. Riesgo humano, vigilancia continua.
5. **Tráfico de red** (solo Drive) → solo HTTPS, sin cleartext, Bearer token.

Fuera de alcance: no hay servidor que atacar, no hay multiusuario, no hay PII en tránsito
salvo lo que el propio Google maneja en su OAuth.

---

## 2. Hallazgos de la revisión inicial (spec v2.1 → v2.2)

Estos se detectaron al auditar el spec **antes** de escribir código. Los fixes están
reflejados en `SPEC.md v2.2`, `CLAUDE.md §6-7`, el manifest y el catálogo de versiones.

### SEC-029 - Export CSV permitia formula injection en hojas de calculo
- **Estado:** Resuelto
- **Fecha:** 2026-06-08
- **Severidad:** Media
- **Sintoma:** el export manual CSV serializaba texto de tablas exportables, incluido texto importado desde Health Connect como titulo/notas de sueno, sin neutralizar valores que empiezan por `=`, `+`, `-` o `@`. Al abrir el CSV en una hoja de calculo, esas celdas podian evaluarse como formulas.
- **Causa raiz:** `BackupExportFormatter.escapeCsv()` solo escapaba sintaxis CSV (comillas, coma y saltos de linea), pero confundia CSV valido con CSV seguro para hojas de calculo.
- **Solucion:** `BackupExportFormatter` antepone apostrofe a celdas textuales cuyo primer caracter significativo sea prefijo de formula. Los primitivos JSON numericos se mantienen como numeros para no romper exportaciones legitimas.
- **Prevencion:** `BackupExportFormatterTest.csv export neutralizes spreadsheet formulas in text cells` cubre `=`, `+`, `-` y `@` sobre `hc_sleep_sessions`, y comprueba que un numero negativo JSON real no se neutraliza.

### SEC-028 - Hardening de supply chain en CI y Gradle
- **Estado:** Resuelto
- **Fecha:** 2026-05-30
- **Severidad:** Baja
- **Sintoma:** CI usaba `GITHUB_TOKEN` con permisos implicitos, Actions referenciadas por tags mutables y Gradle Wrapper sin checksum de distribucion. Ademas no habia metadata de verificacion de dependencias.
- **Causa raiz:** se confiaba en defaults de GitHub/Gradle y en HTTPS, que no fijan integridad de artefactos ni reducen permisos por si solos.
- **Solucion:** `.github/workflows/ci.yml` declara `permissions: contents: read` y pinnea `actions/checkout`, `actions/setup-java` y `gradle/actions/setup-gradle` a commit SHA. `gradle-wrapper.properties` fija `distributionSha256Sum` para Gradle 8.11.1 y se versiona `gradle/verification-metadata.xml` con SHA-256 de artefactos resueltos.
- **Prevencion:** cualquier cambio de Action, Gradle o dependencia debe actualizar el SHA/checksum/verification metadata junto al cambio.
- **2026-06-23:** agregados checksums SHA-256 faltantes de `compose-bom`, `junit-bom`,
  `kotlinx-coroutines-bom` y `guava-parent` para mantener `dependencyVerification` activo
  en unit tests, lint y compilacion androidTest.

### SEC-030 - Compatibilidad de backups con plan semanal v4
- **Estado:** Resuelto
- **Fecha:** 2026-06-23
- **Severidad:** Media
- **Sintoma:** la migracion Room 3->4 agrega columnas a `weekly_plan` para planificar cardio.
  Sin upgrade del snapshot, un backup antiguo descifrado correctamente seria rechazado por
  columnas faltantes antes del restore.
- **Causa raiz:** el restore valida columnas exactas contra el schema vivo; eso es correcto,
  pero exige que cada migracion de columnas tenga un paso equivalente en `BackupSnapshotUpgrader`.
- **Solucion:** `BackupJsonCodec.CURRENT_SCHEMA_VERSION` sube a 3 y `BackupSnapshotUpgrader`
  rellena `weekly_plan.type = STRENGTH`, `cardio_type_id = NULL` y
  `cardio_target_duration_sec = NULL` para snapshots v2.
- **Prevencion:** `BackupSnapshotUpgraderTest.schema v2 snapshot gains cardio planning columns`.

### SEC-027 - Restore de backup validaba columnas, pero no forma/tipo de valores
- **Estado:** Resuelto
- **Fecha:** 2026-05-30
- **Severidad:** Media
- **Sintoma:** un backup descifrado y con passphrase valida podia contener filas con valores JSON anidados, columnas faltantes o tipos incompatibles con la tabla destino.
- **Causa raiz:** `RoomBackupSnapshotStore.restore()` solo comprobaba set de tablas y columnas desconocidas; los valores restantes se convertian a `ContentValues`, e incluso objetos/arrays JSON podian acabar como string.
- **Solucion:** antes de borrar o insertar, el restore compara cada fila con `PRAGMA table_info`, exige columnas exactas y rechaza `NULL` en columnas requeridas, JSON no primitivo y valores incompatibles con la afinidad SQLite.
- **Prevencion:** tests instrumentados nuevos cubren columnas faltantes, JSON no primitivo y tipos incompatibles; los datos existentes se conservan si la validacion falla.

### SEC-026 - Capturas permitidas en toda la app
- **Estado:** Aceptado (riesgo conocido)
- **Fecha:** 2026-05-25
- **Severidad:** Media
- **Sintoma:** el usuario necesita poder tomar capturas de cualquier pantalla de Atlas Peak.
- **Causa raiz:** `FLAG_SECURE` protegia rutas con salud/entrenamiento/perfil/backup, pero tambien bloqueaba capturas legitimas para uso personal, soporte y QA.
- **Solucion:** `SecureScreenEffect` ya no aplica `WindowManager.LayoutParams.FLAG_SECURE`; limpia el flag y `shouldApplySecureFlag()` devuelve siempre `false`. `SensitiveRoutePolicy` conserva la clasificacion de rutas sensibles, pero no bloquea screenshots.
- **Prevencion:** cualquier reintroduccion de `FLAG_SECURE` debe ser una decision explicita. El riesgo aceptado es que datos de salud, perfil, entrenamiento y backup pueden aparecer en screenshots y vista de recientes.

### SEC-025 - Gate de contraseña local retirado por decisión de producto
- **Estado:** Aceptado (riesgo conocido)
- **Fecha:** 2026-05-25
- **Severidad:** Media
- **Sintoma:** pedir contraseña al entrar convertia una app de uso diario en friccion constante.
- **Causa raiz:** el modelo anterior trataba todos los datos locales como si exigieran step-up permanente, aunque el usuario prefiere confiar en el bloqueo del dispositivo.
- **Solucion:** `Launch` navega directo a `Home` tras onboarding; se elimina `SessionLockViewModel`, el onboarding ya no crea contraseña ni biometria, y los hashes/salts locales pasan a nullable con migracion 2→3 que borra credenciales legadas. La passphrase se mantiene solo para cifrar/restaurar backups.
- **Prevencion:** no reintroducir login local salvo decisión explícita. Cualquier export JSON/CSV queda aceptado como claro; los backups Drive siguen cifrados.

### SEC-024 - Capturas permitidas solo en emulador debug para QA visual
- **Estado:** Obsoleto por SEC-026
- **Fecha:** 2026-05-24
- **Severidad:** Baja
- **Sintoma:** `FLAG_SECURE` protegia correctamente el onboarding y rutas sensibles, pero dejaba las capturas del emulador negras e impedia QA visual automatizada.
- **Causa raiz:** la politica de seguridad no distinguia release/dispositivo real de emulador debug usado como herramienta de validacion.
- **Solucion:** la solucion original limitaba capturas a emulador debug. SEC-026 la reemplaza: las capturas estan permitidas tambien en release y dispositivo real.
- **Prevencion:** `SensitiveRoutePolicyTest` cubre que `FLAG_SECURE` no se aplica.

### SEC-010 — DB SQLCipher y clave local protegida
- **Estado:** 🟢 Resuelto
- **Fecha:** 2026-05-23
- **Severidad:** Alta
- **Síntoma:** Fase 1 necesitaba abrir Room con SQLCipher sin persistir una clave de DB en texto plano.
- **Causa raíz:** el bootstrap solo declaraba SQLCipher como dependencia; no existía ciclo de vida de clave.
- **Solución:** `DatabasePassphraseProvider` genera 32 bytes aleatorios con `SecureRandom`, los guarda codificados en `EncryptedSharedPreferences` protegido por Android Keystore, y entrega la passphrase a `SupportOpenHelperFactory`.
- **Prevención:** no usar `fallbackToDestructiveMigration()` fuera de tests; revisar este flujo en Fase 13 junto con backup/restore.

### SEC-011 — Auth local con PBKDF2 y rate-limit persistente
- **Estado:** Obsoleto por SEC-025
- **Fecha:** 2026-05-23
- **Severidad:** Alta
- **Síntoma:** Fase 2 necesitaba un gate local real; dejar el `NavHost` arrancando en `Home` convertía auth en teatro.
- **Causa raíz:** Fase 1 solo tenía rutas placeholder y la tabla `auth_security`, sin DAO/repositorio/use case de autenticación.
- **Solución:** la solución original fue retirada por SEC-025. Se eliminó el flujo de auth local (`LoginScreen`, `LocalAuthUseCase`, biometría y repositorio de auth); los hashes/salts migran a `NULL` y no hay login local visible.
- **Prevención:** si vuelve el login local, reactivar PBKDF2/rate-limit/biometría como feature explícita, no como requisito implícito.

### SEC-012 — Onboarding sensible y permisos Health Connect
- **Estado:** 🟢 Resuelto / actualizado por SEC-025 y SEC-026
- **Fecha:** 2026-05-23
- **Severidad:** Media
- **Síntoma:** Fase 3 añade captura de perfil y permisos Health Connect durante onboarding. La captura de contraseña quedo obsoleta por SEC-025.
- **Causa raíz:** onboarding pasó de placeholder a flujo sensible; además el manifest necesitaba declarar permisos Health Connect antes de pedirlos.
- **Solución:** se declaran permisos `android.permission.health.*` del spec, y el request de Health Connect comprueba disponibilidad del SDK antes de lanzar el contrato. Tras SEC-026, `Onboarding` sigue clasificada como ruta sensible pero no bloquea screenshots.
- **Prevención:** revisión de rutas sensibles cada vez que una pantalla capture perfil, backup o permisos de salud. No reintroducir `FLAG_SECURE` sin decision explicita.

### SEC-013 — Foreground service de fuerza puede fallar por permiso runtime
- **Estado:** 🟢 Resuelto
- **Fecha:** 2026-05-23
- **Severidad:** Media
- **Síntoma:** Fase 5 activa `WorkoutForegroundService` con `foregroundServiceType=health`. En Android 14+ el tipo health puede requerir permisos runtime; si el usuario los salta, `startForeground` puede lanzar `SecurityException`.
- **Causa raíz:** el manifest declara permisos, pero la concesión runtime depende del usuario y del dispositivo.
- **Solución:** `ActiveWorkout` solicita `ACTIVITY_RECOGNITION` antes de arrancar el servicio. `ActiveWorkoutViewModel` y `WorkoutForegroundService` capturan `SecurityException`; la sesión activa no crashea y muestra un aviso si el cronómetro persistente no arranca.
- **Prevención:** gate de Fase 5 incluye revisión de permisos FGS; Fase 13 debe validar flujo en dispositivo real Android 14/15.

### SEC-014 - Cardio GPS no debe pedir ubicacion de fondo ni fingir ruta
- **Estado:** Resuelto
- **Fecha:** 2026-05-23
- **Severidad:** Media
- **Sintoma:** Fase 6 arranca un `CardioForegroundService`; si el usuario deniega ubicacion, la app no puede guardar una sesion como GPS real sin puntos.
- **Causa raiz:** el permiso de ubicacion es runtime y el FGS de tipo `location` no puede asumirse concedido.
- **Solucion:** `ActiveCardio` solicita `ACCESS_FINE_LOCATION` solo para tipos con GPS. Si se deniega o el tipo es manual, la sesion usa cronometro local y exige distancia/velocidad manuales antes de guardarse. `ACCESS_BACKGROUND_LOCATION` sigue ausente.
- **Prevencion:** Fase 13 debe validar el flujo en Android 14/15 real: GPS concedido, GPS denegado y cardio manual.

### SEC-015 - Composicion corporal muestra datos de salud sensibles
- **Estado:** Aceptado por SEC-026
- **Fecha:** 2026-05-24
- **Severidad:** Media
- **Sintoma:** Fase 9 activa la pantalla `Body` con peso, grasa, masa muscular y edad corporal; esos datos pueden aparecer en screenshots o vista de recientes.
- **Causa raiz:** `Body` era placeholder y no estaba clasificado como ruta sensible; despues SEC-026 cambio la politica y las capturas quedaron permitidas en toda la app.
- **Solucion:** `AppRoute.Body.route` sigue clasificada como sensible para auditoria; `SecureScreenEffect` no aplica `FLAG_SECURE` y no se introducen nuevos permisos, red ni logs de valores corporales.
- **Prevencion:** toda pantalla que muestre salud, backup, perfil o permisos sensibles debe revisarse contra `SensitiveRoutePolicy`; cualquier bloqueo de screenshots requiere decision explicita.

### SEC-016 - Sincronizacion Health Connect con permisos revocables
- **Estado:** Resuelto
- **Fecha:** 2026-05-24
- **Severidad:** Media
- **Sintoma:** Fase 10 activa lectura/escritura de datos de salud del dispositivo; los permisos pueden revocarse fuera de la app y no deben asumirse persistentes.
- **Causa raiz:** Health Connect es una superficie de datos sensible controlada por permisos runtime externos a Atlas Peak.
- **Solucion:** `HealthConnectManager` comprueba disponibilidad y `getGrantedPermissions()` antes de cada sync, rehace una ventana movil de 30 dias sin pedir `READ_HEALTH_DATA_HISTORY`, no solicita lectura corporal, usa solo los permisos declarados en manifest, expone una accion de permisos en `Body` y declara pantalla de rationale/privacidad para Health Connect.
- **Prevencion:** cualquier nuevo tipo de dato Health Connect debe anadir permiso manifest + UI + revision de minimo privilegio antes de leer o escribir.

### SEC-017 - Notificaciones con permiso revocable y horarios best-effort
- **Estado:** Resuelto
- **Fecha:** 2026-05-24
- **Severidad:** Media
- **Sintoma:** Fase 11 programa recordatorios y resumenes; `POST_NOTIFICATIONS` puede denegarse o revocarse, y WorkManager no garantiza alarmas exactas.
- **Causa raiz:** Android 13+ protege notificaciones con permiso runtime y WorkManager ejecuta trabajo diferible sujeto a Doze, bateria y cuotas del sistema.
- **Solucion:** `NotificationPermissionChecker` valida permiso runtime y `NotificationManagerCompat` antes de programar o publicar. Los workers terminan sin notificar si el permiso no existe, sin bucles de retry. Los canales estan separados (`training_reminders`, `motivational_messages`, `summaries`) y el texto de UI/documentacion comunica horarios aproximados. No se solicita `SCHEDULE_EXACT_ALARM`.
- **Prevencion:** cualquier worker nuevo debe comprobar permisos antes de mostrar datos en lockscreen, usar `VISIBILITY_PRIVATE` cuando el contenido sea personal y documentar si el horario es exacto o best-effort.

### SEC-018 - Backup Drive no puede usar ID token ni contrasena efimera
- **Estado:** Resuelto
- **Fecha:** 2026-05-24
- **Severidad:** Alta
- **Sintoma:** Fase 12 necesitaba Drive REST; el cliente Google existente solo devolvia ID token de autenticacion, inutil como `Authorization: Bearer` para Drive. Ademas, un worker diario no puede cifrar backups con PBKDF2 si la app no conserva ninguna credencial de backup.
- **Causa raiz:** se mezclaron autenticacion Google (quien eres) y autorizacion Google (permiso para Drive), y el spec asumia backup automatico sin definir como obtener la contrasena fuera de una accion manual.
- **Solucion:** Drive usa `AuthorizationClient` con scope minimo `drive.appdata`; los tokens de acceso se tratan como efimeros y el worker no lanza UI de consentimiento. El backup automatico solo se activa si el usuario introduce la contrasena y acepta guardarla en `EncryptedSharedPreferences` protegido por Android Keystore. Los exports manuales sin cifrar excluyen `users` y `auth_security`.
- **Prevencion:** tests de manager/HTTP prueban que se sube binario cifrado, no JSON, con `multipart/related` para Drive; revision permanente: ID tokens nunca son bearer tokens de Drive; cualquier export no cifrado debe excluir credenciales, hashes y estado de bloqueo.

### SEC-019 - Rutas autenticadas con salud sin FLAG_SECURE
- **Estado:** Obsoleto por SEC-026
- **Fecha:** 2026-05-24
- **Severidad:** Media
- **Sintoma:** `Home`, `Train`, `Progress`, `ActiveWorkout`, `ActiveCardio` y pantallas de resumen mostraban datos de salud, ubicacion o entrenamiento sin bloqueo de screenshots/vista de recientes.
- **Causa raiz:** el allowlist de `SecureScreenEffect` se quedo en auth/perfil/backup/cuerpo, pero fases posteriores movieron datos sensibles a casi toda la zona autenticada.
- **Solucion:** la solucion original protegia rutas autenticadas con `FLAG_SECURE`. SEC-026 revierte ese bloqueo por decision de producto; las rutas siguen clasificadas, pero las capturas se permiten.
- **Prevencion:** cualquier reintroduccion de `FLAG_SECURE` requiere decision explicita y actualizacion de este documento.

### SEC-020 - Notificaciones foreground filtraban actividad en lockscreen
- **Estado:** Resuelto
- **Fecha:** 2026-05-24
- **Severidad:** Media
- **Sintoma:** las notificaciones persistentes de fuerza/cardio mostraban tiempo y distancia sin `VISIBILITY_PRIVATE`; en lockscreen podian exponer actividad fisica y ruta inferida.
- **Causa raiz:** Fase 11 hizo privados recordatorios/resumenes, pero los foreground services de Fase 5/6 tenian builders/canales propios sin la misma politica.
- **Solucion:** `WorkoutForegroundService` y `CardioForegroundService` usan `setVisibility(NotificationCompat.VISIBILITY_PRIVATE)` y canales con `lockscreenVisibility = Notification.VISIBILITY_PRIVATE`; los canales generales tambien fijan visibilidad privada.
- **Prevencion:** test estatico `all notification builders and channels are private on lockscreen`.

### SEC-021 - Timeout de desbloqueo local no se ejecutaba al volver a foreground
- **Estado:** Aceptado / obsoleto por SEC-025
- **Fecha:** 2026-05-24
- **Severidad:** Alta
- **Sintoma:** `biometric_timeout_min` y la politica de timeout existian, pero ninguna ruta sensible revalidaba el desbloqueo al volver de background.
- **Causa raiz:** el gate de auth solo se ejecutaba en `Launch`; `FLAG_SECURE` bloquea capturas, pero no bloquea a una persona con el dispositivo desbloqueado.
- **Solucion:** la mitigacion original fue reemplazada por SEC-025 y SEC-026: no hay gate local ni timeout de sesion, y las capturas estan permitidas.
- **Prevencion:** no tratar `last_login_at` como control vigente; si vuelve el login local, recuperar tests de timeout antes de exponerlo.

### SEC-022 - Export JSON/CSV en claro sin step-up auth
- **Estado:** Aceptado por SEC-025
- **Fecha:** 2026-05-24
- **Severidad:** Media
- **Sintoma:** una sesion ya abierta podia exportar datos de salud/entrenamiento en claro por ShareSheet sin volver a pedir contrasena.
- **Causa raiz:** el flujo de export manual excluia hashes y lockout, pero no distinguia entre sesion autenticada y accion sensible de exfiltracion.
- **Solucion:** al retirar la contraseña de entrada, el step-up local deja de existir. `BackupRestoreViewModel.exportJson()` y `exportCsv()` escriben exports manuales sin contraseña; la UI avisa que son cleartext. Los backups cifrados siguen usando passphrase.
- **Prevencion:** mantener los exports manuales excluyendo `users` y `auth_security`; no confundirlos con backup cifrado.

### SEC-023 - Restore de backup aceptaba columnas desconocidas hasta SQLite
- **Estado:** Resuelto
- **Fecha:** 2026-05-24
- **Severidad:** Media
- **Sintoma:** el restore validaba el set de tablas, pero no el set de columnas por fila antes de insertar el JSON restaurado.
- **Causa raiz:** se confiaba en SQLite para fallar ante columnas invalidas en vez de rechazar el backup en la frontera de validacion.
- **Solucion:** `RoomBackupSnapshotStore.restore()` obtiene `PRAGMA table_info` por tabla y rechaza filas con columnas desconocidas antes de borrar/insertar datos.
- **Prevencion:** `RoomBackupSnapshotStoreInstrumentedTest.restoreRejectsUnknownColumnsBeforeWriting`.

### SEC-001 — Backup no restaurable: falta el salt en el archivo cifrado
- **Estado:** 🟢 Resuelto (en diseño)
- **Fecha:** 2026-05-23
- **Severidad:** Crítica
- **Síntoma:** El spec v2.1 define el formato de backup como `[IV(12B)][ciphertext]` y la
  clave como PBKDF2(contraseña). PBKDF2 requiere salt. En un dispositivo nuevo el salt no
  existe (vivía en la DB que se intenta restaurar). Resultado: **imposible derivar la clave
  y descifrar el backup en otro dispositivo** — justo la promesa estrella de la feature.
- **Causa raíz:** formato de archivo sin cabecera de parámetros KDF.
- **Solución:** nuevo formato del archivo de backup:
  `[magic(4B)="ATPK"][versión(1B)][iteraciones(4B, big-endian)][salt(16B)][IV(12B)][ciphertext+tag GCM]`.
  El salt y las iteraciones del backup son **independientes** del salt de login y viajan
  dentro del archivo (no son secretos). En restore: leer cabecera → derivar clave con esos
  params → descifrar.
- **Prevención:** test de round-trip "cifrar en dispositivo A, descifrar en dispositivo B
  (salt distinto en login)" obligatorio en Fase 12. Documentado en `CLAUDE.md §7`.

### SEC-002 — Cambio de contraseña invalida backups anteriores (sin avisar)
- **Estado:** 🔵 Aceptado + mitigado con UX
- **Fecha:** 2026-05-23
- **Severidad:** Alta
- **Síntoma:** la clave del backup deriva de la contraseña. Si el usuario cambia la
  contraseña, los backups antiguos quedan cifrados con la clave vieja → indescifrables con
  la nueva.
- **Causa raíz:** acoplamiento contraseña↔clave de backup (es también la ventaja: portabilidad).
- **Solución/mitigación:** al cambiar contraseña, la app (a) avisa de que los backups
  previos requerirán la contraseña antigua para restaurarse, y (b) ofrece crear un backup
  nuevo inmediatamente con la nueva contraseña. No se borran los antiguos automáticamente.
- **Prevención:** flujo de cambio de contraseña en Fase 2/12 incluye este aviso explícito.

### SEC-003 — `ACCESS_BACKGROUND_LOCATION` solicitado innecesariamente
- **Estado:** 🟢 Resuelto
- **Fecha:** 2026-05-23
- **Severidad:** Alta (riesgo de rechazo en Play + sobre-permisos)
- **Síntoma:** el manifest del spec pedía `ACCESS_BACKGROUND_LOCATION`.
- **Causa raíz:** suposición de que el tracking en background lo necesita.
- **Solución:** **eliminado.** El `CardioForegroundService` con `foregroundServiceType=location`,
  arrancado mientras la app está visible, accede a ubicación con la UI en segundo plano sin
  ese permiso. Pedirlo dispara revisión manual de Google Play y es causa frecuente de rechazo
  en apps de fitness.
- **Prevención:** regla en `CLAUDE.md §7`; revisión de manifest en Fase 13.

### SEC-004 — `google-services.json` y confusión de credenciales
- **Estado:** 🟢 Resuelto
- **Fecha:** 2026-05-23
- **Severidad:** Media
- **Síntoma:** el spec mandaba colocar `google-services.json` en `app/`. Es artefacto de
  Firebase/GMS plugin, no presente en los plugins del proyecto, y **contiene una API key**
  que acabaría en el repo.
- **Causa raíz:** confusión entre Firebase y Google Identity Services / Drive REST.
- **Solución:** **no se usa `google-services.json`.** `AuthorizationClient` + Drive REST solo
  necesitan el **Web OAuth Client ID** (no secreto, pero gestionado via `secrets.properties`
  para no esparcirlo). `google-services.json` añadido al `.gitignore` por si acaso.
- **Prevención:** documentado en `CLAUDE.md §6`.

### SEC-005 — Maps API key sin gestión (config faltante + riesgo de exposición)
- **Estado:** 🟢 Resuelto (mecanismo listo; valor lo pone el usuario)
- **Fecha:** 2026-05-23
- **Severidad:** Media
- **Síntoma:** Maps Compose exige una API key en el manifest; el spec no la mencionaba.
- **Causa raíz:** omisión.
- **Solución:** la key se lee de `secrets.properties` (`MAPS_API_KEY`) y se inyecta via
  `manifestPlaceholders`. `secrets.properties` está en `.gitignore`. Restringir la key en
  Google Cloud Console (por SHA-1 + package name) antes de release.
- **Prevención:** plantilla `secrets.properties.template`; regla en `CLAUDE.md §6`.

### SEC-006 — Iteraciones PBKDF2 bajas para amenaza offline
- **Estado:** 🟢 Resuelto
- **Fecha:** 2026-05-23
- **Severidad:** Media
- **Síntoma:** spec usaba 200.000 iteraciones y lo etiquetaba "suficiente/fuerte".
- **Causa raíz:** referencia desactualizada. Para el backup (atacable offline) las
  iteraciones son la defensa principal.
- **Solución:** **600.000 iteraciones** PBKDF2-HMAC-SHA256 (alineado con OWASP).
- **Prevención:** constante única en `EncryptionManager`; no hardcodear en otros sitios.

### SEC-007 — `allowBackup` no definido (la DB cifrada no debe ir al backup de Android)
- **Estado:** 🟢 Resuelto
- **Fecha:** 2026-05-23
- **Severidad:** Media
- **Síntoma:** sin `android:allowBackup="false"`, Android podría incluir la DB SQLCipher y
  las prefs cifradas en su backup automático, con claves ligadas a un Keystore que no
  migra → datos inservibles o expuestos.
- **Solución:** `allowBackup="false"` + `dataExtractionRules`/`fullBackupContent` que
  excluyen DB y prefs. El backup de datos lo gestiona la app (Drive cifrado), no Android.
- **Prevención:** en el manifest base; revisión Fase 13.

### SEC-008 — `USE_FINGERPRINT` deprecado
- **Estado:** 🟢 Resuelto
- **Fecha:** 2026-05-23
- **Severidad:** Baja
- **Síntoma:** permiso deprecado desde API 28; con minSdk 31 es peso muerto.
- **Solución:** eliminado. Tras SEC-025 tampoco se declara `USE_BIOMETRIC` porque no hay desbloqueo biométrico local.

### SEC-009 — `foregroundServiceType="health"` sin permiso asociado
- **Estado:** 🟢 Resuelto
- **Fecha:** 2026-05-23
- **Severidad:** Media
- **Síntoma:** el manifest declaraba `WorkoutForegroundService` con
  `foregroundServiceType="health"` y `FOREGROUND_SERVICE_HEALTH`, pero faltaba un permiso
  asociado como `ACTIVITY_RECOGNITION`. En Android 14+ esto puede lanzar `SecurityException`
  al arrancar el foreground service.
- **Causa raíz:** bootstrap incompleto del manifest: se añadió el tipo de FGS, no el permiso
  de actividad requerido por la plataforma.
- **Solución:** añadido `android.permission.ACTIVITY_RECOGNITION` y documentado junto al
  contrato del `WorkoutForegroundService`.
- **Prevención:** lint queda como gate obligatorio de Fase 0 para detectar permisos FGS
  incompletos.

---

## 3. Checklist de seguridad por release (repetir antes de cada subida a Play)

- [ ] Cero secretos en el repo (`git log -p` y `git grep` por "key", "secret", "password").
- [ ] `secrets.properties`, `keystore.properties`, `*.jks`, `local.properties`,
      `google-services.json` siguen en `.gitignore`.
- [ ] OkHttp logging interceptor desactivado en release (`BuildConfig.DEBUG`).
- [ ] Sin `Log.*` con datos sensibles en builds release (ProGuard/R8 los retira; verificar).
- [ ] Capturas permitidas intencionalmente: no reintroducir `FLAG_SECURE` sin decision explicita.
- [ ] `network_security_config.xml`: `cleartextTrafficPermitted="false"` en producción.
- [ ] Maps API key restringida (SHA-1 + package) en Cloud Console.
- [ ] Permisos del manifest = solo los usados (sin `ACCESS_BACKGROUND_LOCATION`).
- [ ] Test de round-trip de backup entre "dispositivos" (salts distintos) pasa.
- [ ] PBKDF2 = 600.000 iteraciones.

---

## 4. Riesgos conocidos y aceptados

| ID | Limitación | Por qué se acepta |
|----|------------|-------------------|
| RA-05 | Sin contraseña para abrir la app | El usuario prefiere friccion cero; se confia en bloqueo del dispositivo |
| RA-06 | Capturas de pantalla permitidas | Decision de producto; facilita uso personal, soporte y QA, pero puede exponer datos si el usuario comparte capturas |
| SEC-002 | Cambiar contraseña invalida backups previos | Es el coste de la portabilidad sin backend; mitigado con aviso UX |
| RA-01 | Rate-limit reseteable borrando datos de la app | La defensa real es el hash fuerte; sin servidor no hay alternativa |
| RA-02 | Pérdida de contraseña **y** cuenta Google → datos irrecuperables | Documentado y advertido en onboarding; no hay solución sin comprometer el cifrado |
| RA-04 | Backup automatico guarda la contrasena cifrada en el dispositivo | Es el coste de cifrar backups diarios sin servidor ni pedir contrasena cada dia; opt-in explicito y protegido por Keystore |

---

## Formato para nuevos hallazgos

```
### SEC-NNN — Título
- **Estado:** 🔴 / 🟡 / 🟢 / 🔵
- **Fecha:** AAAA-MM-DD
- **Severidad:** Crítica / Alta / Media / Baja
- **Síntoma:** ...
- **Causa raíz:** ...
- **Solución:** ...
- **Prevención:** ...
```

---

*Atlas Peak — SECURITY.md*
