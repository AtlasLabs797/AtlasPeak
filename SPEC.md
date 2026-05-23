# ATLAS PEAK
## Especificación Técnica Completa — v2.2
**Fecha:** Mayo 2026 | **Estado:** Ready for Development | **Plataforma:** Android 12+

> **v2.2** aplica una auditoría de seguridad y dependencias sobre v2.1. Cambios clave:
> formato de backup corregido (incluye salt — antes era irrestaurable), eliminado
> `ACCESS_BACKGROUND_LOCATION`, eliminado `google-services.json`, añadida gestión de Maps
> API key, PBKDF2 a 600k, versiones de librerías actualizadas a estables, Google Sign-In
> reclasificado como opcional, y **Wear OS diferido a v2**. Ver "REGISTRO DE CORRECCIONES
> (v2.1 → v2.2)" más abajo y `SECURITY.md`.

---

## REGISTRO DE CORRECCIONES (v2.1 → v2.2)

| # | Problema en v2.1 | Corrección en v2.2 | Ref |
|---|------------------|--------------------|-----|
| A | **Backup irrestaurable**: formato `[IV][ciphertext]` sin salt; PBKDF2 necesita salt y en un dispositivo nuevo no existe | Formato nuevo con cabecera `[magic][versión][iteraciones][salt][IV][ciphertext+tag]`; el salt viaja en el archivo | SEC-001 |
| B | `ACCESS_BACKGROUND_LOCATION` innecesario y causa rechazo en Play | Eliminado; el FGS de cardio (tipo `location`, arrancado con app visible) basta | SEC-003 |
| C | `google-services.json` (artefacto Firebase, con API key) mandado al repo | Eliminado; se usa Web OAuth Client ID via `secrets.properties` | SEC-004 |
| D | Google Maps API key sin gestionar | Gestionada via `secrets.properties` + `manifestPlaceholders`, gitignored | SEC-005 |
| E | PBKDF2 200k etiquetado "fuerte" | Subido a 600.000 iter (OWASP) | SEC-006 |
| F | `allowBackup` sin definir → DB cifrada podría ir al backup de Android | `allowBackup="false"` + reglas de exclusión | SEC-007 |
| G | `USE_FINGERPRINT` deprecado (minSdk 31) | Eliminado; `USE_BIOMETRIC` cubre todo | SEC-008 |
| H | Versiones desactualizadas (Vico beta, Health Connect RC con artifact viejo, Wear alpha mezclado) | Actualizadas a estables verificadas; HC con artifact renombrado `androidx.health.connect:connect-client` | — |
| I | Google Sign-In como "cuenta obligatoria" pese a no haber backend; fuerza internet en app local-first | Reclasificado: OAuth **opcional** solo para Drive; onboarding paso 2 saltable | — |
| J | Contradicción: rate-limit en `EncryptedSharedPreferences` (§2.12) vs tabla `auth_security` | Unificado: contador en tabla `auth_security` (DB cifrada) | — |
| K | Wear OS en v1 (4 semanas, máxima complejidad, mínimo valor core) | **Diferido a v2**; diseño del protocolo conservado | — |
| L | Calorías "desde GPS" (el GPS no da calorías; falta el peso, que está en `body_composition`, no en perfil) | Documentada dependencia cross-table y orden de estimación | — |
| M | `exportSchema=true` sin `room.schemaLocation` | Configurado en `app/build.gradle.kts`, schemas versionados en `app/schemas/` | — |

---

## REGISTRO DE CORRECCIONES (v1.0 → v2.1)

| # | Problema original | Corrección aplicada |
|---|-------------------|---------------------|
| 1 | `play-services-drive` deprecado desde 2019 | Reemplazado por Google Drive REST API v3 via Retrofit directo |
| 2 | Google API Client library (pesada, conflictos OkHttp) | Eliminada — Drive se consume con Retrofit+OkHttp directamente |
| 3 | Wear OS dependency incorrecta (`androidx.wear:wear`) | Reemplazada por `play-services-wearable` + `wear.compose` |
| 4 | Mockito en proyecto 100% Kotlin | Reemplazado por MockK |
| 5 | "E2E encryption con Keystore" en Health Connect | Corregido: Keystore es para almacenamiento local, no tránsito a HC |
| 6 | Rate limiting aplicado a Google Sign-In | Aclarado: solo aplica a contraseña local |
| 7 | Health Connect full integration como feature premium | Eliminado de premium — es core gratuito |
| 8 | Báscula "integrada directamente" | Corregido: integración indirecta vía app báscula → Health Connect |
| 9 | Datos de báscula inexistentes en Health Connect | Documentados correctamente (4 tipos soportados, resto solo manual) |
| 10 | Google Sign-In deprecated (GMS Auth) | Reemplazado por Google Identity Services (Credential Manager API) |
| 11 | Sin onboarding flow | Añadido como fase de desarrollo y pantallas |
| 12 | Sin perfil de usuario | Añadido: nombre, edad, altura, género, objetivo |
| 13 | Planificación semanal sin tab asignado | Asignada a Tab Perfil como sub-pantalla |
| 14 | Retrofit sin propósito claro | Aclarado: exclusivamente para Drive REST API v3 |
| 15 | Timeline 30 meses injustificado | Recalculado: 36 semanas full-time (~9 meses) |
| 16 | Sin política de conflictos de sync | Definida: dato más reciente tiene prioridad |
| 17 | Sin modelo de feature flags | Añadida arquitectura de flags desde día 1 |
| 18 | "Analytics local" sin definición | Eliminado — zero tracking, sin sistema de analytics |
| 19 | **PBKDF2 iterations insuficientes (100k)** | Actualizado a 200.000 iter. PBKDF2-HMAC-SHA256 |
| 20 | **Sin Foreground Service para entrenamiento activo** | Añadido `WorkoutForegroundService` y `CardioForegroundService` |
| 21 | **Clave backup ligada al dispositivo (Keystore)** | Clave de backup derivada de contraseña local — restaurable en nuevo dispositivo |
| 22 | **Sin network_security_config.xml** | Añadida configuración: solo HTTPS, sin cleartext |
| 23 | **i18n en Fase 16 (demasiado tarde)** | Movida a Fase 1 — strings.xml desde el inicio |
| 24 | **Sin FLAG_SECURE en pantallas sensibles** | Añadido en pantallas de auth, perfil, backup |
| 25 | **Biometría sin especificar nivel** | Definido: `BIOMETRIC_STRONG` (Clase 3) obligatorio |
| 26 | **Sin WearableListenerService en manifest** | Añadido para recibir mensajes del reloj |
| 27 | **Export a almacenamiento público** | Corregido: export a almacenamiento privado + share via ShareSheet |
| 28 | **Sin estrategia de Room migrations** | Añadida — `fallbackToDestructiveMigration()` prohibido en producción |
| 29 | **Permisos Health Connect no listados** | Listados explícitamente (lectura y escritura) |
| 30 | **Sin estrategia de crash reporting** | Android Vitals via Play Console (automático, sin SDK) |
| 31 | **Permisos AndroidManifest incompletos** | Lista completa añadida |

---

## 1. DESCRIPCIÓN DEL PROYECTO

**Atlas Peak** es una aplicación nativa Android de gestión de entrenamientos personales. Filosofía **local-first**: todos los datos residen en el dispositivo, sin backend propio ni servidor de Atlas Peak. El único cloud involucrado es Google Drive para backup cifrado, controlado completamente por el usuario.

La app cubre el ciclo completo del entrenamiento: planificar rutinas, ejecutar sesiones de fuerza o cardio, monitorear composición corporal, visualizar progreso mediante gráficos y sincronizar con el ecosistema de salud del dispositivo (Health Connect, Wear OS).

**Distribución:** APK de desarrollo personal → publicación en Google Play Store cuando esté completa.  
**Monetización:** v1 completamente gratuita. Arquitectura preparada para features premium en versiones futuras mediante feature flags, sin billing library todavía.  
**Idiomas:** Español e Inglés (internacionalización completa desde el día 1).  
**Privacy-first:** zero tracking externo, sin Firebase, sin Crashlytics, sin ningún SDK de telemetría de terceros.

---

## 2. CARACTERÍSTICAS DE LA APLICACIÓN

### 2.1 GESTIÓN DE RUTINAS

- CRUD completo de rutinas personalizadas
- Biblioteca de ejercicios: ejercicios preset (seed data al instalar) + ejercicios custom creados por el usuario
- Cada ejercicio define: nombre, grupo muscular principal, grupo muscular secundario (opcional), descripción
- **Grupos musculares disponibles:** Pecho, Espalda, Piernas, Hombros, Bíceps, Tríceps, Core, Glúteos, Gemelos, Cuerpo completo
- Por ejercicio dentro de una rutina: número de series, repeticiones, peso objetivo (nullable para ejercicios con peso corporal), descanso en segundos, posición en la rutina
- Reordenamiento de ejercicios en rutina con drag-and-drop
- Color tag por rutina para diferenciación visual rápida
- Duración estimada calculada automáticamente

### 2.2 ENTRENAMIENTO ACTIVO (FUERZA)

- Pantalla exclusiva durante el entrenamiento (oculta bottom navigation)
- **`WorkoutForegroundService`** arranca al iniciar la sesión: mantiene el cronómetro activo cuando la app está en background y muestra notificación persistente con tiempo transcurrido
- `HorizontalPager`: un ejercicio por página, swipe lateral para navegar
- **Progress card superior:** `X / N ejercicios` + cronómetro de sesión en tiempo real
- Por cada ejercicio:
  - Lista de sets con reps planificadas, peso, checkbox de completado
  - Peso editable por set individualmente durante el entrenamiento
  - Botón "+ Set" y "– Set" para añadir o eliminar sets sobre la marcha
- Al marcar un set como completado → overlay de rest timer:
  - Anillo de progreso circular con cuenta regresiva
  - Botón "Skip" para saltarse el descanso
  - Sonido + vibración o solo vibración (configurable en ajustes)
  - Timer enviado al reloj Wear OS simultáneamente
- `BottomSheet` colapsable y draggable: lista de todos los ejercicios de la rutina + drag-and-drop para reordenar en tiempo real durante el entrenamiento
- Al finalizar el último set del último ejercicio: pantalla de resumen (volumen total, duración, sets completados, nuevo récord detectado si aplica)
- Las sesiones son de tipo **FUERZA** exclusivamente — no hay modo mixto fuerza+cardio

### 2.3 CARDIO

- Tipos de cardio: predefinidos (Running exterior, Ciclismo exterior, Cinta, Bici estática, Elíptica, Remo, Natación) + tipos custom creados por el usuario
- Al crear tipo custom: definir si usa GPS o no
- Pantalla exclusiva durante cardio (oculta bottom navigation)
- **`CardioForegroundService`** para mantener GPS activo y cronómetro en background con notificación persistente
- **Dos modos de sesión:**
  - **Timer:** cuenta hacia adelante (el usuario para cuando quiere)
  - **Countdown:** cuenta atrás desde duración objetivo definida
- **Con GPS activado:**
  - Tracking de ruta en tiempo real via `FusedLocationProvider`
  - Métricas en vivo: distancia recorrida, velocidad actual, velocidad media
  - Al finalizar: mapa de ruta completa (Google Maps Compose)
- **Sin GPS (máquinas de gimnasio):**
  - Entrada manual de distancia y velocidad
  - Sin mapa
- Calorías quemadas: calculadas desde GPS si disponible; si no, desde Health Connect; si no, estimación por duración y tipo de ejercicio
- Las sesiones de cardio son **independientes** de las de fuerza — no hay rutinas mixtas

### 2.4 HISTORIAL Y PROGRESO

- Lista cronológica de todas las sesiones completadas (fuerza y cardio, identificadas por tipo)
- Búsqueda y filtro en historial por tipo, rutina, fecha
- Al clicar en una sesión de fuerza: detalle completo con todos los ejercicios, sets, pesos, duración y volumen total
- Al clicar en una sesión de cardio: detalle con duración, distancia, velocidad, mapa (si GPS)
- **Vista por ejercicio:** gráfico de evolución histórica del peso máximo y volumen total por sesión
- **Vista por grupo muscular:** todos los ejercicios del grupo con sus gráficos individuales
- Gráficos Vico Charts con:
  - Eje Y izquierdo: peso (kg)
  - Eje X: fecha
  - Eje Y derecho opcional: repeticiones
  - Selector de período: semana / mes / 3 meses / año / año hasta hoy

### 2.5 DASHBOARD (HOME)

Panel con scroll vertical. Todos los widgets tienen selector de período individual (semana / mes / 3 meses / año / año hasta hoy):

- **Volumen total:** kg totales levantados en el período seleccionado
- **Consistencia:** días entrenados vs días planificados (si hay plan semanal activo) o vs total de días del período (si no hay plan)
- **Evolución de peso corporal:** gráfico de línea temporal
- **Pasos diarios:** desde Health Connect, gráfico de barras
- **Frecuencia cardíaca en reposo:** desde Health Connect, gráfico de línea
- **Horas de sueño:** promedio del período, desde Health Connect
- **Tiempo total de actividad:** suma de duración de todas las sesiones
- **Minutos de entrenamiento esta semana:** widget destacado en la parte superior
- Todos los gráficos son interactivos (touch para ver valor exacto en punto)

### 2.6 COMPOSICIÓN CORPORAL

**Datos disponibles y sincronizables con Health Connect:**

| Métrica | Health Connect | Solo manual |
|---------|---------------|-------------|
| Peso corporal | ✅ | ✅ |
| % Grasa corporal | ✅ | ✅ |
| Masa muscular (kg) | ✅ | ✅ |
| % Agua corporal | ✅ | ✅ |
| Grasa visceral | ❌ | ✅ |
| % Proteína | ❌ | ✅ |
| Masa ósea (kg) | ❌ | ✅ |
| Edad corporal | ❌ | ✅ |

> **Importante sobre báscula inteligente (Xiaomi / Renpho):** La integración es **indirecta**. La báscula sincroniza datos con su app propietaria (Zepp Life, Renpho App). Esa app escribe en Health Connect. Atlas Peak lee desde Health Connect. Atlas Peak no controla si la app de la báscula tiene soporte de Health Connect — esto debe verificarse con el dispositivo específico del usuario.

- Pantalla principal: tabla de valores actuales + gráficos de evolución por métrica (scroll vertical)
- Entrada manual disponible para todos los campos en cualquier momento
- Los datos introducidos en Atlas Peak se exportan a Health Connect (solo los 4 tipos soportados)
- **Política de conflicto:** el dato con timestamp más reciente tiene prioridad, independientemente de la fuente

### 2.7 PLANIFICACIÓN SEMANAL

- Configurar días de entrenamiento de la semana (Lunes a Domingo)
- Asignar una rutina específica a cada día de entrenamiento
- Marcar días explícitamente como descanso
- Cards visuales por día: nombre de rutina + checkbox de completado del día
- La semana empieza en lunes
- Configurar hora de notificación de recordatorio por día (independiente por día)
- Si no hay plan configurado: el widget de consistencia en el dashboard muestra días activos vs total de días del período
- **Ubicación en navegación:** Tab "Perfil" → sub-pantalla "Planificación"

### 2.8 NOTIFICACIONES

- **Canal 1 — Recordatorios de entrenamiento:** muestra nombre de rutina del día + hora configurada en el plan semanal. Ejemplo: "Pecho + Tríceps a las 18:00"
- **Canal 2 — Mensajes motivacionales:** mensajes aleatorios predefinidos, activables/desactivables en ajustes
- **Canal 3 — Resúmenes:**
  - Resumen diario: 8:30 AM por defecto, hora configurable por usuario
  - Resumen semanal: lunes a las 8:30 AM
  - Contenido: volumen del período, consistencia, peso corporal si hay datos recientes
- Todos los canales configurables individualmente en ajustes
- `WorkManager` para programación y ejecución en background
- En Android 13+: solicitar permiso `POST_NOTIFICATIONS` en onboarding

### 2.9 SINCRONIZACIÓN HEALTH CONNECT

- Sincronización automática al abrir la app (si permisos concedidos)
- Primera sincronización: flujo explícito de concesión de permisos en onboarding
- Si se revocan permisos: pantalla informativa que guía al usuario a re-concederlos

**Permisos de LECTURA requeridos:**
```
READ_STEPS
READ_ACTIVE_CALORIES_BURNED
READ_SLEEP
READ_HEART_RATE
```

**Permisos de ESCRITURA requeridos:**
```
WRITE_EXERCISE
WRITE_WEIGHT
WRITE_BODY_FAT
WRITE_LEAN_BODY_MASS
WRITE_BODY_WATER_MASS
```

- **Import desde Health Connect:** pasos diarios, calorías activas, sueño, frecuencia cardíaca
- **Export a Health Connect:** sesiones de entrenamiento completadas, peso, grasa, masa muscular, agua
- `HcSyncLog`: tabla que registra el último timestamp de lectura y escritura por tipo de dato
- **Política de conflicto:** dato con timestamp más reciente gana — Atlas Peak no sobreescribe si el dato local es más reciente

### 2.10 SMARTWATCH (WEAR OS)

- Módulo `wear/` como APK separado empaquetado dentro del APK principal
- Comunicación bidireccional via **Wearable Data Layer API**

**Funcionalidades en el reloj:**
- Rest timer con anillo de progreso circular (Wear Compose `CircularProgressIndicator`)
- Seconds countdown en el centro del anillo
- Información del siguiente set: "Siguiente: 8 reps × 80 kg"
- Botón "✓ Set completado" → envía confirmación al teléfono
- Botón "⏭ Skip descanso" → envía acción al teléfono
- Vibración háptica al llegar a 0 en el descanso

**Protocolo de comunicación:**

```
Teléfono → Reloj (DataClient — estado persistente):
  Path: /workout/state
  {
    "active": boolean,
    "exercise_name": string,
    "rest_remaining_sec": int,
    "next_set_reps": int,
    "next_set_weight_kg": float,
    "session_elapsed_sec": int
  }

Reloj → Teléfono (MessageClient — eventos puntuales):
  Path: /workout/action
  { "action": "complete_set", "weight_kg": float, "reps": int }
  { "action": "skip_rest" }
  { "action": "pause_workout" }
```

- `WearableListenerService` registrado en el manifest del teléfono para recibir mensajes del reloj

### 2.11 PERFIL DE USUARIO

- Nombre de display
- Edad (años)
- Altura (cm)
- Género: Hombre / Mujer / Otro / Prefiero no decir
- Objetivo: Perder grasa / Ganar músculo / Mantener peso / Mejorar resistencia
- Foto de perfil (opcional, almacenada localmente)
- El perfil informa estimaciones de calorías pero no bloquea ninguna funcionalidad
- Editable en cualquier momento desde Tab Perfil

### 2.12 AUTENTICACIÓN Y SEGURIDAD

- **Google Identity Services (Credential Manager API)** como método principal de sign-in
- **Contraseña local obligatoria** — configurada durante onboarding, sirve como acceso de respaldo si se pierde la cuenta Google
- **Hashing de contraseña:** PBKDF2-HMAC-SHA256 con **600.000 iteraciones** (OWASP) + salt aleatorio de 32 bytes, ejecutado en coroutine (no en main thread)
- Contraseña y salt almacenados en `EncryptedSharedPreferences`
- **Biometría:** `BiometricPrompt` con `BIOMETRIC_STRONG` (Clase 3 — huella dactilar segura, reconocimiento facial 3D). Nivel `BIOMETRIC_WEAK` explícitamente rechazado
- Biometría es opcional y configurable; solo sirve para desbloquear la app, no como autenticación nueva
- Biometría se solicita al volver al foreground después del timeout configurado (1 / 5 / 15 minutos / nunca)
- **Rate limiting:** exclusivamente sobre contraseña local — 5 intentos fallidos → bloqueo 15 minutos. El contador se almacena en la tabla `auth_security` (DB cifrada con SQLCipher). *(v2.2: unificado — antes el spec mencionaba también `EncryptedSharedPreferences`, lo que contradecía la tabla.)*
- **DB local:** encriptada con SQLCipher. La clave de cifrado se genera aleatoriamente, se almacena cifrada en Android Keystore (nunca en texto plano)
- `FLAG_SECURE` activo en: `LoginActivity`, `BiometricPromptScreen`, `BackupRestoreScreen`, `ProfileScreen`

**Escenario de pérdida total de acceso:**
Si el usuario pierde acceso a Google **y** olvida la contraseña local → los datos del dispositivo son inaccesibles. El backup en Drive está cifrado con una clave derivada de la contraseña local (no ligada al dispositivo) — si el usuario recuerda la contraseña puede restaurar en un dispositivo nuevo. Este escenario debe advertirse al usuario durante la configuración de contraseña.

### 2.13 BACKUP Y RECUPERACIÓN

- Backup a **Google Drive App Data folder** (carpeta privada, invisible para el usuario, solo accesible por Atlas Peak)
- Acceso via **Drive REST API v3** con OAuth2 — scope: `https://www.googleapis.com/auth/drive.appdata`
- Retrofit + OkHttp como cliente HTTP (consistente con el resto del stack)
- **Proceso de backup:**
  1. Serializar toda la DB a JSON (Kotlinx Serialization)
  2. Derivar clave de 256 bits con PBKDF2-HMAC-SHA256 (600k iter) desde la contraseña local + un salt **propio del backup** (16 bytes aleatorios, generado por backup)
  3. Cifrar JSON con AES-256-GCM + IV aleatorio (12 bytes)
  4. **Formato del archivo (v2.2 — corregido):**
     `[magic "ATPK" (4B)] [versión (1B)] [iteraciones (4B big-endian)] [salt (16B)] [IV (12B)] [ciphertext + tag GCM]`
     El salt y las iteraciones viajan **dentro del archivo** (no son secretos). Sin esto, la
     restauración en un dispositivo nuevo era imposible (no se podía derivar la clave). Ver SEC-001.
  5. Upload a Drive App Data folder
- `WorkManager`: backup automático cada 24h si hay cambios desde el último backup
- Máximo **5 backups históricos** en Drive — el más antiguo se elimina al crear el sexto
- **Proceso de restauración:**
  1. Listar backups disponibles con fecha y tamaño
  2. Seleccionar backup
  3. Download + descifrar con contraseña local
  4. Transacción Room completa: DROP + INSERT de todos los datos
- **Export manual:** JSON (estructura completa exportable) o CSV (un archivo por tipo: sesiones, sets, cardio, composición corporal)
- Export guarda en almacenamiento privado de la app, luego comparte via Android `ShareSheet` — el usuario elige dónde enviarlo (Drive, email, etc.)
- Export incluye: rutinas, ejercicios, sesiones, sets, cardio, composición corporal, plan semanal

### 2.14 ONBOARDING (PRIMER LANZAMIENTO)

Flujo lineal; solo la contraseña de respaldo (paso 3) es obligatoria:

1. **Bienvenida:** propuesta de valor en una pantalla, logo, CTA "Empezar"
2. **Conectar Google (OPCIONAL, saltable):** Google Identity Services solo para habilitar el backup en Drive. *(v2.2: ya NO es obligatorio ni "crea cuenta" — no hay backend. La app funciona 100% offline. Se puede conectar más tarde desde Ajustes.)*
3. **Contraseña de respaldo:** crear contraseña local (obligatorio, no salteable, con confirmación y medidor de fortaleza)
4. **Perfil básico:** nombre, edad, altura, género, objetivo (salteable)
5. **Permisos de notificaciones:** explicación + solicitud (salteable con advertencia)
6. **Health Connect:** explicación de qué datos se leen/escriben + solicitud de permisos (salteable)
7. **Ubicación para cardio GPS:** solicitud de permiso `ACCESS_FINE_LOCATION` (salteable)
8. **Biometría:** activar opcionalmente (salteable)
9. **Listo:** pantalla de confirmación → Home

### 2.15 PRIVACIDAD

- Almacenamiento local exclusivo (Room + SQLCipher)
- Sin backend propio de Atlas Peak
- Único cloud: Google Drive App Data folder (privado, controlado por el usuario)
- **Zero tracking externo:** sin Firebase Analytics, sin Crashlytics, sin Amplitude, sin ningún SDK de telemetría de terceros
- Crash reporting: **Android Vitals** via Google Play Console — automático, sin SDK adicional, sin código extra
- Privacy Policy pública requerida antes de publicar en Play Store (especialmente obligatoria para aprobación de Health Connect)
- Portabilidad total: export completo en cualquier momento

---

## 3. ESPECIFICACIONES TÉCNICAS

### 3.1 STACK TECNOLÓGICO

```
Lenguaje:                   Kotlin
IDE:                        Android Studio Ladybug 2024.2.1+
Min SDK:                    API 31 (Android 12)
Target SDK:                 API 35 (Android 15)
Compile SDK:                API 36
JDK:                        17+

Framework UI:               Jetpack Compose + Material 3
Base de Datos:              Room + SQLCipher (cifrado transparente)
ORM:                        Room Persistence Library
Inyección de Dependencias:  Hilt
State Management:           ViewModel + StateFlow + collectAsStateWithLifecycle
Gráficos:                   Vico Charts (com.patrykandpatrick.vico)
HTTP Client:                Retrofit + OkHttp [exclusivamente para Drive REST API v3]
Serialización:              Kotlinx Serialization
Cifrado:                    Android Keystore + EncryptedSharedPreferences + SQLCipher + AES-256-GCM
Autenticación:              Google Identity Services — Credential Manager API
Health Connect:             androidx.health.connect:connect-client (estable)
Location:                   Google Play Services Location (FusedLocationProvider)
Maps:                       Google Maps Compose
Background jobs:            WorkManager
Foreground Services:        WorkoutForegroundService, CardioForegroundService
Wear OS comunicación:       Wearable Data Layer API (DataClient + MessageClient)
Wear OS UI:                 Wear Compose Material 3
Versioning:                 Git + GitHub
CI/CD:                      GitHub Actions
Testing:                    JUnit5 + MockK + Compose Testing + Room in-memory
Crash reporting:            Android Vitals (Google Play Console, sin SDK)
```

### 3.2 DEPENDENCIAS COMPLETAS (build.gradle.kts — módulo app)

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlinx.serialization)
}

dependencies {

    // ── Compose BOM ─────────────────────────────────────────────────────────
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // ── Lifecycle + ViewModel ────────────────────────────────────────────────
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")  // para ForegroundService

    // ── Navigation ───────────────────────────────────────────────────────────
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // ── Room + SQLCipher ─────────────────────────────────────────────────────
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    // SQLCipher: cifrado transparente sobre SQLite
    implementation("net.zetetic:sqlcipher-android:4.5.7")
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")

    // ── Hilt ─────────────────────────────────────────────────────────────────
    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-compiler:2.52")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // ── Health Connect (v2.2: artifact renombrado + estable) ─────────────────
    // El grupo cambió de androidx.health a androidx.health.connect y ya hay 1.1.0 estable.
    implementation("androidx.health.connect:connect-client:1.1.0")

    // ── Vico Charts (v2.2: estable 2.x; antes era 2.0.0-beta.2) ──────────────
    implementation("com.patrykandpatrick.vico:compose-m3:2.4.3")

    // ── Cifrado ──────────────────────────────────────────────────────────────
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    // EncryptedSharedPreferences + acceso al Keystore

    // ── Google Identity Services (Sign-In moderno) ────────────────────────────
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")

    // ── Google Drive REST API v3 (via Retrofit, sin Google API Client library) ─
    // Se consume directamente con Retrofit + bearer token OAuth2
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    // OkHttp interceptor para añadir Authorization: Bearer {token} automáticamente

    // ── Kotlinx Serialization ────────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // ── WorkManager ──────────────────────────────────────────────────────────
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // ── Location + Maps ──────────────────────────────────────────────────────
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.maps.android:maps-compose:6.1.2")
    implementation("com.google.android.gms:play-services-maps:19.0.0")

    // ── Wear OS Data Layer ────────────────────────────────────────────────────
    implementation("com.google.android.gms:play-services-wearable:18.2.0")

    // ── Splash Screen API ────────────────────────────────────────────────────
    implementation("androidx.core:core-splashscreen:1.0.1")

    // ── DataStore (ajustes y preferencias) ───────────────────────────────────
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // ── Coroutines ────────────────────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // ── Testing ──────────────────────────────────────────────────────────────
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("androidx.room:room-testing:2.6.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("app.cash.turbine:turbine:1.2.0")  // testing de Flows
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("io.mockk:mockk-android:1.13.13")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.work:work-testing:2.10.0")
}
```

### 3.3 DEPENDENCIAS MÓDULO WEAR (wear/build.gradle.kts)

```kotlin
dependencies {
    implementation("androidx.wear.compose:compose-material3:1.0.0-alpha30")
    implementation("androidx.wear.compose:compose-navigation:1.4.0")
    implementation("androidx.wear.compose:compose-foundation:1.4.0")
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Hilt en Wear
    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-compiler:2.52")
}
```

### 3.4 PERMISOS AndroidManifest.xml

```xml
<!-- Localización para cardio GPS -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

<!-- v2.2: ACCESS_BACKGROUND_LOCATION ELIMINADO.
     El CardioForegroundService (foregroundServiceType=location), arrancado mientras la app
     está visible, accede a ubicación con la UI en background SIN este permiso. Pedirlo
     dispara revisión manual de Google Play y es causa frecuente de rechazo. Ver SEC-003. -->

<!-- Notificaciones (Android 13+) -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- Foreground Services -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_HEALTH" />
<uses-permission android:name="android.permission.ACTIVITY_RECOGNITION" />

<!-- Internet (Google Sign-In opcional, Drive backup) -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<!-- Biometría (v2.2: USE_FINGERPRINT eliminado — deprecado desde API 28, minSdk es 31) -->
<uses-permission android:name="android.permission.USE_BIOMETRIC" />

<!-- Vibración (rest timer) -->
<uses-permission android:name="android.permission.VIBRATE" />

<!-- Wear OS: diferido a v2, no se declara en v1 -->

<!-- Health Connect (declarar visibilidad del paquete) -->
<queries>
    <package android:name="com.google.android.apps.healthdata" />
</queries>

<!-- v2.2: la Google Maps API key se inyecta via manifestPlaceholders desde
     secrets.properties (NUNCA hardcodeada). En el <application>:
     <meta-data android:name="com.google.android.geo.API_KEY"
                android:value="${MAPS_API_KEY}" />
     Ver SEC-005 y secrets.properties.template. -->
```

### 3.5 NETWORK SECURITY CONFIG

Archivo `res/xml/network_security_config.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <!-- Solo HTTPS en producción, cero cleartext -->
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>
    <!-- Permitir cleartext solo en debug para herramientas de red -->
    <debug-overrides>
        <trust-anchors>
            <certificates src="system" />
            <certificates src="user" />
        </trust-anchors>
    </debug-overrides>
</network-security-config>
```

Referenciado en `AndroidManifest.xml`:
```xml
<application
    android:networkSecurityConfig="@xml/network_security_config"
    ...>
```

---

## 4. BASE DE DATOS COMPLETA (Room Entities)

### Convención: todos los timestamps son `Long` (Unix epoch milisegundos)

```sql
────────────────────────────────────────────────────────────
AUTENTICACIÓN Y PERFIL
────────────────────────────────────────────────────────────

users
  id                TEXT    PK          -- UUID generado localmente
  google_id         TEXT                -- nullable, id de cuenta Google
  email             TEXT                -- nullable; Google/Drive es opcional
  password_hash     TEXT    NOT NULL    -- PBKDF2-HMAC-SHA256, 600k iter
  password_salt     TEXT    NOT NULL    -- 32 bytes aleatorios, base64
  created_at        INTEGER NOT NULL
  last_login_at     INTEGER

user_profile
  id                TEXT    PK
  user_id           TEXT    NOT NULL    FK → users.id
  display_name      TEXT
  age               INTEGER             -- nullable
  height_cm         REAL                -- nullable
  gender            TEXT                -- MALE/FEMALE/OTHER/PREFER_NOT
  goal_type         TEXT                -- LOSE_FAT/BUILD_MUSCLE/MAINTAIN/ENDURANCE
  photo_uri         TEXT                -- nullable, URI local
  updated_at        INTEGER NOT NULL

────────────────────────────────────────────────────────────
EJERCICIOS Y RUTINAS
────────────────────────────────────────────────────────────

muscle_groups       (seed data, solo lectura)
  id                INTEGER PK
  name_es           TEXT    NOT NULL
  name_en           TEXT    NOT NULL
  icon_name         TEXT    NOT NULL

exercises
  id                TEXT    PK          -- UUID
  name_es           TEXT    NOT NULL
  name_en           TEXT    NOT NULL
  muscle_group_id   INTEGER NOT NULL    FK → muscle_groups.id
  secondary_muscle_group_id INTEGER     FK → muscle_groups.id, nullable
  description_es    TEXT
  description_en    TEXT
  is_preset         INTEGER NOT NULL    -- 1 = viene con la app
  is_archived       INTEGER NOT NULL DEFAULT 0  -- soft delete
  created_at        INTEGER NOT NULL

routines
  id                TEXT    PK
  name              TEXT    NOT NULL
  description       TEXT
  color_tag         TEXT                -- hex ej: "#E53935", nullable
  estimated_duration_min INTEGER        -- calculado, nullable
  created_at        INTEGER NOT NULL
  updated_at        INTEGER NOT NULL
  is_archived       INTEGER NOT NULL DEFAULT 0

routine_exercises
  id                TEXT    PK
  routine_id        TEXT    NOT NULL    FK → routines.id
  exercise_id       TEXT    NOT NULL    FK → exercises.id
  sets              INTEGER NOT NULL
  reps              INTEGER NOT NULL
  weight_kg         REAL                -- nullable (peso corporal)
  rest_seconds      INTEGER NOT NULL DEFAULT 90
  order_index       INTEGER NOT NULL
  notes             TEXT

────────────────────────────────────────────────────────────
SESIONES DE ENTRENAMIENTO
────────────────────────────────────────────────────────────

workout_sessions
  id                TEXT    PK
  routine_id        TEXT                FK → routines.id, nullable (sesión ad-hoc)
  type              TEXT    NOT NULL    -- STRENGTH / CARDIO
  start_time        INTEGER NOT NULL
  end_time          INTEGER             -- nullable hasta completar
  duration_seconds  INTEGER
  notes             TEXT
  completed         INTEGER NOT NULL DEFAULT 0
  calories_burned   INTEGER
  total_volume_kg   REAL                -- pre-calculado para performance en dashboard

workout_sets
  id                TEXT    PK
  session_id        TEXT    NOT NULL    FK → workout_sessions.id
  exercise_id       TEXT    NOT NULL    FK → exercises.id
  set_number        INTEGER NOT NULL
  planned_reps      INTEGER NOT NULL
  actual_reps       INTEGER             -- nullable hasta completar
  weight_kg         REAL
  completed         INTEGER NOT NULL DEFAULT 0
  completed_at      INTEGER
  source            TEXT    NOT NULL DEFAULT 'PHONE'  -- PHONE / WATCH
  is_personal_record INTEGER NOT NULL DEFAULT 0

────────────────────────────────────────────────────────────
CARDIO
────────────────────────────────────────────────────────────

cardio_types
  id                TEXT    PK
  name_es           TEXT    NOT NULL
  name_en           TEXT    NOT NULL
  has_gps           INTEGER NOT NULL
  icon_name         TEXT    NOT NULL
  is_preset         INTEGER NOT NULL DEFAULT 0
  is_archived       INTEGER NOT NULL DEFAULT 0

cardio_sessions
  id                TEXT    PK
  session_id        TEXT    NOT NULL    FK → workout_sessions.id
  cardio_type_id    TEXT    NOT NULL    FK → cardio_types.id
  mode              TEXT    NOT NULL    -- TIMER / COUNTDOWN
  target_duration_sec INTEGER NOT NULL
  actual_duration_sec INTEGER
  distance_km       REAL
  avg_speed_kmh     REAL
  max_speed_kmh     REAL
  calories_burned   INTEGER
  has_gps           INTEGER NOT NULL DEFAULT 0
  route_polyline_json TEXT             -- JSON encoded, nullable
  source            TEXT    NOT NULL   -- MANUAL / GPS / HEALTH_CONNECT

────────────────────────────────────────────────────────────
COMPOSICIÓN CORPORAL
────────────────────────────────────────────────────────────

body_composition
  id                TEXT    PK
  measured_at       INTEGER NOT NULL    -- timestamp de la medición real
  weight_kg         REAL
  body_fat_percent  REAL
  muscle_mass_kg    REAL
  water_percent     REAL
  -- Solo entrada manual, no disponibles en Health Connect:
  visceral_fat_level INTEGER
  protein_percent   REAL
  bone_mass_kg      REAL
  body_age          INTEGER
  source            TEXT    NOT NULL   -- MANUAL / HEALTH_CONNECT / SCALE_APP
  synced_to_hc      INTEGER NOT NULL DEFAULT 0
  created_at        INTEGER NOT NULL

────────────────────────────────────────────────────────────
PLANIFICACIÓN SEMANAL
────────────────────────────────────────────────────────────

weekly_plan
  id                TEXT    PK
  day_of_week       INTEGER NOT NULL    -- 1=Lunes ... 7=Domingo
  routine_id        TEXT                FK → routines.id, nullable
  is_rest_day       INTEGER NOT NULL DEFAULT 0
  notification_enabled INTEGER NOT NULL DEFAULT 1
  notification_time TEXT                -- "HH:mm", nullable

────────────────────────────────────────────────────────────
SINCRONIZACIÓN Y CONFIGURACIÓN
────────────────────────────────────────────────────────────

hc_sync_log
  id                TEXT    PK
  data_type         TEXT    NOT NULL    -- STEPS/CALORIES/SLEEP/HEART_RATE/WORKOUTS/BODY_COMP
  last_read_at      INTEGER
  last_write_at     INTEGER

app_settings        (tabla singleton — siempre id = 1)
  id                INTEGER PK DEFAULT 1
  notifications_enabled INTEGER DEFAULT 1
  motivational_messages INTEGER DEFAULT 1
  daily_summary_enabled INTEGER DEFAULT 1
  daily_summary_time TEXT    DEFAULT '08:30'
  weekly_summary_enabled INTEGER DEFAULT 1
  biometrics_enabled INTEGER DEFAULT 0
  biometric_timeout_min INTEGER DEFAULT 5     -- 1/5/15/-1(nunca)
  theme             TEXT    DEFAULT 'SYSTEM'  -- SYSTEM/LIGHT/DARK
  language          TEXT    DEFAULT 'SYSTEM'  -- SYSTEM/ES/EN
  rest_sound_enabled INTEGER DEFAULT 1
  rest_vibration_enabled INTEGER DEFAULT 1
  last_backup_at    INTEGER
  backup_auto_enabled INTEGER DEFAULT 1

auth_security       (tabla singleton — siempre id = 1)
  id                INTEGER PK DEFAULT 1
  failed_attempts   INTEGER DEFAULT 0
  locked_until      INTEGER             -- nullable, timestamp de fin de bloqueo

hc_steps_records
  id                TEXT    PK
  hc_record_id      TEXT    NOT NULL UNIQUE
  source_package    TEXT    NOT NULL
  last_modified_at  INTEGER NOT NULL
  recording_method  INTEGER
  imported_at       INTEGER NOT NULL
  start_time        INTEGER NOT NULL
  end_time          INTEGER NOT NULL
  start_zone_offset TEXT
  end_zone_offset   TEXT
  count             INTEGER NOT NULL

hc_active_calories_records
  id                TEXT    PK
  hc_record_id      TEXT    NOT NULL UNIQUE
  source_package    TEXT    NOT NULL
  last_modified_at  INTEGER NOT NULL
  recording_method  INTEGER
  imported_at       INTEGER NOT NULL
  start_time        INTEGER NOT NULL
  end_time          INTEGER NOT NULL
  start_zone_offset TEXT
  end_zone_offset   TEXT
  kilocalories      REAL    NOT NULL

hc_sleep_sessions
  id                TEXT    PK
  hc_record_id      TEXT    NOT NULL UNIQUE
  source_package    TEXT    NOT NULL
  last_modified_at  INTEGER NOT NULL
  recording_method  INTEGER
  imported_at       INTEGER NOT NULL
  start_time        INTEGER NOT NULL
  end_time          INTEGER NOT NULL
  start_zone_offset TEXT
  end_zone_offset   TEXT
  title             TEXT
  notes             TEXT

hc_sleep_stages
  id                TEXT    PK
  sleep_session_id  TEXT    NOT NULL FK -> hc_sleep_sessions.id
  hc_record_id      TEXT    NOT NULL UNIQUE
  source_package    TEXT    NOT NULL
  last_modified_at  INTEGER NOT NULL
  recording_method  INTEGER
  imported_at       INTEGER NOT NULL
  start_time        INTEGER NOT NULL
  end_time          INTEGER NOT NULL
  stage_type        INTEGER NOT NULL

hc_heart_rate_samples
  id                TEXT    PK
  hc_record_id      TEXT    NOT NULL
  source_package    TEXT    NOT NULL
  last_modified_at  INTEGER NOT NULL
  recording_method  INTEGER
  imported_at       INTEGER NOT NULL
  sampled_at        INTEGER NOT NULL
  bpm               INTEGER NOT NULL
  UNIQUE(hc_record_id, sampled_at)
```

### Estrategia de Migraciones Room

```kotlin
// AppDatabase.kt
@Database(
    entities = [...],
    version = 1,
    exportSchema = true  // genera JSON de schema para tracking de cambios
)
abstract class AppDatabase : RoomDatabase() {

    companion object {
        // NUNCA usar fallbackToDestructiveMigration() en producción
        // Cada cambio de schema requiere una Migration explícita:
        // val MIGRATION_1_2 = object : Migration(1, 2) {
        //     override fun migrate(db: SupportSQLiteDatabase) {
        //         db.execSQL("ALTER TABLE workout_sets ADD COLUMN is_personal_record INTEGER NOT NULL DEFAULT 0")
        //     }
        // }
    }
}
```

---

## 5. ARQUITECTURA

### 5.1 Estructura de Módulos

```
atlas-peak/
├── app/                                     ← módulo principal (teléfono)
│   └── src/main/kotlin/com/atlaspeak/
│       ├── data/
│       │   ├── db/
│       │   │   ├── AppDatabase.kt           ← SQLCipher + Room
│       │   │   ├── dao/                     ← 1 DAO por entidad principal
│       │   │   └── entity/                  ← Room entities
│       │   ├── repository/                  ← implementaciones de interfaces
│       │   ├── healthconnect/
│       │   │   └── HealthConnectManager.kt  ← import + export HC
│       │   ├── drive/
│       │   │   ├── DriveApiService.kt       ← Retrofit interface para Drive REST v3
│       │   │   └── DriveBackupManager.kt    ← serializar, cifrar, upload/download
│       │   ├── location/
│       │   │   └── LocationTracker.kt       ← FusedLocationProvider, Flow de ubicaciones
│       │   ├── wear/
│       │   │   └── WearableDataManager.kt   ← DataClient + MessageClient
│       │   └── security/
│       │       └── EncryptionManager.kt     ← Keystore, AES-256-GCM, PBKDF2
│       │
│       ├── domain/
│       │   ├── model/                       ← data classes sin anotaciones Room/Retrofit
│       │   ├── repository/                  ← interfaces (contratos)
│       │   └── usecase/
│       │       ├── auth/
│       │       │   ├── GoogleSignInUseCase.kt
│       │       │   ├── LocalAuthUseCase.kt
│       │       │   └── BiometricAuthUseCase.kt
│       │       ├── workout/
│       │       │   ├── CreateRoutineUseCase.kt
│       │       │   ├── StartWorkoutSessionUseCase.kt
│       │       │   ├── RecordSetUseCase.kt
│       │       │   └── CompleteWorkoutSessionUseCase.kt
│       │       ├── cardio/
│       │       │   ├── StartCardioSessionUseCase.kt
│       │       │   └── CompleteCardioSessionUseCase.kt
│       │       ├── progress/
│       │       │   ├── GetWorkoutHistoryUseCase.kt
│       │       │   └── GetExerciseProgressUseCase.kt
│       │       ├── body/
│       │       │   ├── RecordBodyCompositionUseCase.kt
│       │       │   └── GetBodyEvolutionUseCase.kt
│       │       ├── healthconnect/
│       │       │   └── SyncHealthConnectUseCase.kt
│       │       ├── backup/
│       │       │   ├── BackupToDriveUseCase.kt
│       │       │   └── RestoreFromDriveUseCase.kt
│       │       └── plan/
│       │           └── GetWeeklyPlanUseCase.kt
│       │
│       ├── presentation/
│       │   ├── screen/
│       │   │   ├── auth/            ← LoginScreen, SplashScreen
│       │   │   ├── onboarding/      ← 9 pantallas del flujo inicial
│       │   │   ├── home/            ← HomeScreen (dashboard)
│       │   │   ├── workout/         ← Rutinas, ActiveWorkout, Historial
│       │   │   ├── cardio/          ← Tipos cardio, ActiveCardio
│       │   │   ├── progress/        ← Progreso, detalle por ejercicio/grupo
│       │   │   ├── body/            ← Composición corporal
│       │   │   ├── plan/            ← Planificación semanal
│       │   │   └── profile/         ← Perfil, Ajustes, Backup, Export
│       │   ├── component/           ← Composables reutilizables (charts, cards, timers...)
│       │   ├── viewmodel/           ← 1 ViewModel por feature
│       │   └── theme/
│       │       ├── Theme.kt
│       │       ├── Color.kt         ← Material 3, accent rojo
│       │       ├── Typography.kt    ← Poppins (títulos) + Inter (body)
│       │       └── Shape.kt         ← 16dp universal
│       │
│       ├── service/
│       │   ├── WorkoutForegroundService.kt  ← cronómetro sesión fuerza en background
│       │   └── CardioForegroundService.kt   ← GPS + cronómetro cardio en background
│       │
│       ├── worker/
│       │   ├── BackupWorker.kt
│       │   ├── DailySummaryWorker.kt
│       │   └── WeeklySummaryWorker.kt
│       │
│       ├── di/                      ← Hilt modules (Database, Network, Repository...)
│       ├── feature/
│       │   └── FeatureFlags.kt      ← flags para futuro premium
│       ├── util/                    ← extensiones, formatters, constantes
│       └── MainActivity.kt
│
├── wear/                                    ← módulo Wear OS (APK separado)
│   └── src/main/kotlin/com/atlaspeak/wear/
│       ├── screen/
│       │   ├── WearRestTimerScreen.kt
│       │   └── WearWorkoutStatusScreen.kt
│       ├── viewmodel/
│       │   └── WearWorkoutViewModel.kt
│       ├── service/
│       │   └── WearMessageListenerService.kt  ← recibe mensajes del teléfono
│       └── WearMainActivity.kt
│
├── build.gradle.kts
└── settings.gradle.kts
```

### 5.2 Patrón de Capas

```
Presentation ←→ Domain ←→ Data
     │               │          │
  Compose UI     Use Cases    Room DB
  ViewModels     Repository   Health Connect
  Navigation     Interfaces   Drive REST API
                              Location
                              Wear Data Layer
```

**Regla de oro:** las capas solo dependen hacia adentro. La capa `presentation` nunca importa Room entities directamente — trabaja con domain models.

### 5.3 Foreground Services

**WorkoutForegroundService**
- Se inicia con `startForegroundService()` al comenzar sesión de fuerza
- Notificación persistente: "Entrenamiento activo ⏱ 23:45" (actualiza cada segundo)
- Expone `StateFlow<WorkoutTimerState>` al que el ViewModel se suscribe via `bindService()`
- Se destruye al completar o abandonar la sesión
- Requiere permiso `FOREGROUND_SERVICE_HEALTH`

**CardioForegroundService**
- Se inicia al comenzar sesión de cardio
- Recibe updates de `LocationTracker` via `FusedLocationProvider`
- Notificación persistente: "Corriendo 🏃 3.2 km | 28:14"
- Expone `StateFlow<CardioSessionState>` (ubicaciones, distancia, velocidad, tiempo)
- Requiere permisos `FOREGROUND_SERVICE_LOCATION` + `ACCESS_FINE_LOCATION`

### 5.4 Feature Flags

```kotlin
// feature/FeatureFlags.kt
object FeatureFlags {
    // v1: todo gratuito. En el futuro, leer desde DataStore o servidor de flags.
    val ADVANCED_ANALYTICS    get() = true
    val AUTO_MONTHLY_EXPORT   get() = true
    val UNLIMITED_ROUTINES    get() = true
    val AI_COACHING           get() = false   // reservado, no implementado en v1
}
```

Cada pantalla con feature potencialmente premium comprueba el flag antes de renderizar contenido restringido. Cambiar billing en el futuro = cambiar la fuente de los flags, sin tocar UI.

---

## 6. DISEÑO

### 6.1 Tipografía

| Uso | Fuente | Pesos |
|-----|--------|-------|
| Títulos, headings | **Poppins** | 600 Semi-bold, 700 Bold |
| Cuerpo, labels, datos | **Inter** | 400 Regular, 500 Medium |

### 6.2 Paleta de Colores (Material 3)

| Token | Rol | Valor aproximado |
|-------|-----|-----------------|
| `primary` | Accent principal | Rojo `#E53935` |
| `onPrimary` | Texto sobre rojo | Blanco |
| `surface` | Fondo de cards | Neutro oscuro / claro |
| `background` | Fondo base | Casi negro / casi blanco |
| `error` | Errores | Rojo claro (distinto del primary) |

Dark Mode y Light Mode: automáticos siguiendo configuración del sistema Android. Contraste mínimo WCAG AA en todos los textos.

### 6.3 Espaciado y Componentes

- **Border radius universal:** 16dp (cards, botones, textfields, bottom sheets)
- **Padding estándar:** 16dp | Compacto: 8dp | Amplio: 24dp
- **Gaps:** 8dp / 12dp / 16dp / 24dp
- **Animaciones:** 200–350ms, Material Motion easing curves

### 6.4 Navegación — 5 Tabs + Pantallas Completas

```
Bottom Navigation (siempre visible excepto en pantallas fullscreen):

Tab 1: HOME           → HomeScreen
Tab 2: ENTRENAR       → TrainHomeScreen
                        ├── RoutineListScreen
                        ├── RoutineDetailScreen
                        ├── CreateEditRoutineScreen
                        ├── ExerciseLibraryScreen
                        ├── CreateEditExerciseScreen
                        ├── CardioListScreen
                        └── CreateEditCardioTypeScreen

                        [FULLSCREEN — ocultan bottom nav:]
                        ├── ActiveWorkoutScreen
                        ├── WorkoutCompleteScreen
                        ├── ActiveCardioScreen
                        └── CardioCompleteScreen

Tab 3: PROGRESO       → ProgressHomeScreen (tabs internos: Historial / Ejercicios / Grupos)
                        ├── SessionDetailScreen
                        ├── ExerciseProgressScreen
                        └── MuscleGroupProgressScreen

Tab 4: CUERPO         → BodyCompositionScreen
                        └── ManualBodyEntryScreen

Tab 5: PERFIL         → ProfileScreen
                        ├── EditProfileScreen
                        ├── WeeklyPlanScreen
                        ├── SettingsScreen
                        ├── BackupRestoreScreen
                        └── ExportScreen

Flujo Auth (fuera del NavHost principal):
  SplashScreen
  └── LoginScreen
      └── OnboardingFlow (solo primer lanzamiento, 9 pasos)
```

**Total: 27 pantallas.**

---

## 7. SEGURIDAD

### 7.1 Autenticación

| Capa | Tecnología | Notas |
|------|------------|-------|
| Sign-In principal | Google Identity Services (Credential Manager) | Reemplaza GMS Auth (deprecated) |
| Contraseña respaldo | PBKDF2-HMAC-SHA256, 600.000 iter, salt 32B | Ejecución en `Dispatchers.IO` |
| Almacenamiento | `EncryptedSharedPreferences` | Backed by Android Keystore |
| Biometría | `BiometricPrompt` clase `BIOMETRIC_STRONG` | Solo desbloqueo, no auth nueva |
| Rate limiting | 5 intentos → lock 15 min | Solo contraseña local |
| Pantallas sensibles | `FLAG_SECURE` | Login, Perfil, Backup, Biometría |

### 7.2 Cifrado Local

| Qué | Cómo |
|-----|------|
| Base de datos | SQLCipher, clave AES-256 en Android Keystore |
| Preferencias y credenciales | `EncryptedSharedPreferences` (AES-256-SIV + AES-256-GCM) |
| Clave SQLCipher | Generada en primer lanzamiento, nunca sale del Keystore |
| Datos en memoria | Domain models sin campos sensibles; credenciales no pasan por ViewModel |

### 7.3 Backup Cifrado (Google Drive)

```
Proceso de cifrado de backup (v2.2 — corregido):
1. salt_backup = 16 bytes aleatorios (por backup); iteraciones = 600.000
2. Contraseña local + salt_backup → PBKDF2-HMAC-SHA256 → clave maestra de 256 bits
3. JSON completo de la DB → cifrado con AES-256-GCM + IV aleatorio (12 bytes)
4. Formato del archivo:
   [magic "ATPK" (4B)] [versión (1B)] [iteraciones (4B BE)] [salt_backup (16B)] [IV (12B)] [ciphertext + tag GCM (16B)]
5. Upload a Drive App Data folder: atlas_peak_backup_{timestamp}.enc

Por qué la cabecera:
- El salt y las iteraciones NO son secretos; deben acompañar al ciphertext para poder
  derivar la misma clave en cualquier dispositivo.
- En v2.1 el formato era [IV][ciphertext] sin salt → en un dispositivo nuevo no había forma
  de derivar la clave → la feature "restaurar con tu contraseña" estaba rota. (SEC-001)

Ventaja clave (ahora sí funciona):
- La clave NO está ligada al dispositivo físico.
- Si el usuario instala en un nuevo dispositivo y recuerda su contraseña local,
  puede restaurar el backup correctamente (lee la cabecera, deriva la clave, descifra).
- Google solo ve datos binarios cifrados, nunca el contenido.

Aviso: cambiar la contraseña local invalida los backups previos (estaban cifrados con la
clave derivada de la contraseña antigua). La app advierte de esto y ofrece crear un backup
nuevo. (SEC-002)
```

### 7.4 Red

- `network_security_config.xml`: cleartext prohibido en producción
- Todas las llamadas a Drive REST API usan `Authorization: Bearer {token}`
- Token OAuth2 de Google almacenado en `EncryptedSharedPreferences`
- `OkHttp logging interceptor` desactivado en builds release (BuildConfig.DEBUG)

### 7.5 Export de Datos

- Los archivos exportados se crean en `context.filesDir` (almacenamiento privado de la app)
- Se comparten via `FileProvider` + Android `ShareSheet`
- El archivo no queda accesible a otras apps directamente
- `FileProvider` configurado en manifest con `android:exported="false"`

### 7.6 Puntos Débiles Conocidos y Aceptados

| Limitación | Por qué se acepta |
|------------|-------------------|
| Rate limiting reseteable borrando datos de app | La protección real es el hash fuerte de contraseña |
| Backup no restaurable si se olvida contraseña Y pierde cuenta Google | Documentado y advertido en UI. No hay solución sin comprometer seguridad |
| FLAG_SECURE impide screenshots en pantallas sensibles pero no en todas | Solo pantallas con datos personales críticos |

---

## 8. INSTALACIÓN Y CONFIGURACIÓN DEL ENTORNO

### 8.1 Requisitos del Sistema de Desarrollo

| Requisito | Mínimo | Recomendado |
|-----------|--------|-------------|
| OS | Windows 10 / macOS 12 / Ubuntu 22.04 | macOS 14 o Ubuntu 24.04 |
| RAM | 8 GB | 16 GB |
| Disco libre | 20 GB | 40 GB (emuladores ocupan mucho) |
| CPU | 4 cores | 8 cores (compilación Gradle) |

### 8.2 Software Requerido

**1. Java Development Kit 17+**
```
Descargar: https://adoptium.net (Temurin — gratuito)
Verificar: java --version
```

**2. Android Studio Ladybug 2024.2.1+**
```
Descargar: https://developer.android.com/studio
Incluye: Gradle, Android SDK, emulador
```

**3. Android SDK (desde Android Studio > SDK Manager)**
```
SDK Platforms: API 31, API 34, API 35, API 36
SDK Tools:
  - Android SDK Build-Tools 35.0.0
  - Android Emulator
  - Android SDK Platform-Tools
  - Google Play services
  - Intel x86 Emulator Accelerator (HAXM) — o AMD equivalente
```

**4. Git 2.40+**
```
Descargar: https://git-scm.com
```

### 8.3 Cuentas y Servicios Externos

**Google Developer Account** (único pago: $25 USD, único pago para siempre)
- Google Play Console: https://play.google.com/console
- Google Cloud Console: https://console.cloud.google.com

**En Google Cloud Console, habilitar:**
- Google Identity Services / OAuth (pantalla de consentimiento + credencial OAuth 2.0)
- Google Drive API v3
- Maps SDK for Android

**Credenciales necesarias (v2.2 — NO usar `google-services.json`):**
```
1. OAuth 2.0 Client ID (tipo "Web application")  → para Credential Manager + Drive
   - Registrar también el SHA-1 de tu keystore (debug y release) como cliente Android.
2. Maps SDK for Android API key                   → restringida por package name + SHA-1.

Ambos valores se colocan en secrets.properties (LOCAL, gitignored), nunca en el repo:
   OAUTH_WEB_CLIENT_ID=xxxxxxxx.apps.googleusercontent.com
   MAPS_API_KEY=AIzaSy...

Ver secrets.properties.template. Razón del cambio: google-services.json es un artefacto de
Firebase/GMS que ni se usa aquí y contiene una API key que acabaría en el repo. (SEC-004)
```

**GitHub Account**
- Repositorio privado durante desarrollo
- GitHub Actions para CI/CD

### 8.4 Pasos de Instalación Inicial

```bash
# 1. Clonar repositorio
git clone https://github.com/tuusuario/atlas-peak.git
cd atlas-peak

# 2. Crear secrets.properties desde la plantilla y rellenar valores
cp secrets.properties.template secrets.properties
# editar secrets.properties con OAUTH_WEB_CLIENT_ID y MAPS_API_KEY reales

# 3. Crear local.properties (si no existe)
echo "sdk.dir=/Users/TU_USUARIO/Library/Android/sdk" > local.properties
# En Windows: sdk.dir=C\:\\Users\\TU_USUARIO\\AppData\\Local\\Android\\Sdk

# 4. Sincronizar dependencias
./gradlew build
# Windows: gradlew.bat build

# 5. Crear emulador (Android Studio > Device Manager)
# API 31+ con Google Play Services (necesario para Health Connect y Maps)

# 6. Ejecutar
# Android Studio > Run > Run 'app'
```

### 8.5 Firma del APK (signing)

```bash
# Crear keystore (hacer UNA VEZ, guardar en lugar seguro)
keytool -genkey -v \
  -keystore atlas-peak-release.jks \
  -keyalg RSA -keysize 2048 \
  -validity 10000 \
  -alias atlas-peak

# IMPORTANTE: nunca subir secretos al repositorio
# .gitignore debe incluir (ver el .gitignore del proyecto):
echo "*.jks" >> .gitignore
echo "keystore.properties" >> .gitignore
echo "secrets.properties" >> .gitignore
echo "google-services.json" >> .gitignore
echo "local.properties" >> .gitignore
```

`keystore.properties` (local, no en repo):
```properties
storeFile=../atlas-peak-release.jks
storePassword=TU_PASSWORD
keyAlias=atlas-peak
keyPassword=TU_PASSWORD
```

### 8.6 CI/CD con GitHub Actions

```yaml
# .github/workflows/ci.yml
name: CI

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Build debug
        run: ./gradlew assembleDebug
      - name: Run unit tests
        run: ./gradlew test
      - name: Lint
        run: ./gradlew lint
```

---

## 9. PLAN DE EJECUCIÓN — 36 SEMANAS

### Resumen de Fases

| Fase | Contenido | Semanas | Acum. |
|------|-----------|---------|-------|
| 1 | Fundación + Internacionalización | 1 | 1 |
| 2 | Autenticación completa | 2 | 3 |
| 3 | Onboarding | 1 | 4 |
| 4 | Ejercicios y Rutinas | 2 | 6 |
| 5 | Entrenamiento activo (fuerza) + ForegroundService | 3 | 9 |
| 6 | Cardio + GPS + ForegroundService | 3 | 12 |
| 7 | Historial y progreso | 2 | 14 |
| 8 | Dashboard | 2 | 16 |
| 9 | Composición corporal | 1.5 | 17.5 |
| 10 | Health Connect (import + export) | 3 | 20.5 |
| 11 | Planificación semanal + Notificaciones | 2 | 22.5 |
| 12 | Backup Google Drive + Export | 2 | 24.5 |
| 13 | ~~Wear OS~~ **(DIFERIDO A v2 — ver nota)** | — | — |
| 14 | Seguridad + Hardening | 1 | 29.5 |
| 15 | Testing | 3 | 32.5 |
| 16 | Polish + Performance + Accesibilidad | 2 | 34.5 |
| 17 | Play Store + Privacy Policy + Release | 1.5 | 36 |

> **Full-time (~8h/día):** ~9 meses  
> **Part-time (~3h/día):** ~24 meses  
> El timeline original de 30 meses no tiene base para un desarrollador full-time.

> **v2.2 — Wear OS diferido a v2.** Las 4 semanas de Wear OS (la pieza de mayor complejidad
> técnica y menor valor core) se sacan de v1. Construye el teléfono, publícalo, valídalo, y
> añade el reloj después. El diseño del protocolo teléfono↔reloj (§2.10) se conserva intacto.
> Sin Wear, v1 son ~32 semanas full-time. Reactivar = añadir el módulo `wear` a
> `settings.gradle.kts` y seguir el plan original de esa fase.

---

### Detalle por Fase

**FASE 1 — Fundación + i18n (1 semana)**
- Crear proyecto Android con todos los plugins (Hilt, KSP, Compose, Serialization)
- Configurar `build.gradle.kts` con todas las dependencias definidas en este spec
- Configurar `network_security_config.xml`
- Configurar `proguard-rules.pro` para Room, Hilt, Retrofit, Kotlinx Serialization
- Implementar sistema de tema Material 3 (colores rojo/neutro, dark/light mode automático)
- Configurar tipografía Poppins + Inter via Google Fonts o assets locales
- Inicializar `strings.xml` para ES y EN — todas las claves desde el primer día
- Implementar `AppDatabase.kt` con SQLCipher, todas las entidades y DAOs
- Seed data: grupos musculares, ejercicios preset, tipos de cardio preset
- `SplashScreen` con `core-splashscreen`
- `FeatureFlags.kt`
- Módulo Wear OS: estructura básica del proyecto

**FASE 2 — Autenticación completa (2 semanas)**
- Google Identity Services Sign-In (Credential Manager API)
- Contraseña local: PBKDF2-HMAC-SHA256 (600k iter), salt, hash en `EncryptedSharedPreferences`, contador de intentos en tabla auth_security
- `BiometricPrompt` con `BIOMETRIC_STRONG` + lógica de timeout configurable
- Rate limiting en contraseña local (5 intentos, 15 min lock, `EncryptedSharedPreferences`)
- `LoginScreen` con `FLAG_SECURE`
- `AuthViewModel` + `LocalAuthUseCase` + `GoogleSignInUseCase`
- `EncryptionManager.kt`: wrappers de Keystore, AES-256-GCM, PBKDF2

**FASE 3 — Onboarding (1 semana)**
- Flujo de 9 pasos completo
- Solicitud de permisos: notificaciones, Health Connect, ubicación
- Pantalla de configuración de contraseña local con medidor de fortaleza
- Setup de perfil básico (nombre, edad, altura, género, objetivo)
- `UserProfile` entity + DAO + repositorio
- Persistir flag `onboarding_completed` en DataStore

**FASE 4 — Ejercicios y Rutinas (2 semanas)**
- `ExerciseLibraryScreen`: lista de ejercicios con búsqueda y filtro por grupo muscular
- `CreateEditExerciseScreen`: CRUD de ejercicios custom
- `RoutineListScreen`: lista de rutinas con color tags
- `RoutineDetailScreen`: vista de ejercicios de la rutina
- `CreateEditRoutineScreen`: crear/editar rutina con drag-and-drop de ejercicios
- Cálculo de duración estimada de rutina

**FASE 5 — Entrenamiento Activo Fuerza (3 semanas)**
- `WorkoutForegroundService`: cronómetro persistente + notificación
- `ActiveWorkoutScreen`: `HorizontalPager` + sets + pesos editables + progress card
- Rest timer overlay con anillo de progreso + sonido/vibración
- `BottomSheet` de ejercicios con drag-and-drop en tiempo real
- Detección de personal records
- `WorkoutCompleteScreen`: resumen post-sesión
- `WorkoutHistoryScreen`: lista de sesiones pasadas
- `SessionDetailScreen`: detalle completo

**FASE 6 — Cardio + GPS (3 semanas)**
- `CardioForegroundService`: GPS + cronómetro persistente + notificación
- `CardioListScreen` + `CreateEditCardioTypeScreen`
- `ActiveCardioScreen`: modo Timer y Countdown, métricas en tiempo real
- `LocationTracker.kt`: `FusedLocationProvider`, Flow de coordenadas
- Mapa de ruta al finalizar (Google Maps Compose)
- Entrada manual para cardio sin GPS
- `CardioCompleteScreen`: resumen

**FASE 7 — Historial y Progreso (2 semanas)**
- `ProgressHomeScreen` con 3 tabs internos: Historial / Por Ejercicio / Por Grupo Muscular
- Integración Vico Charts: gráficos de evolución de peso y volumen
- Selectores de período (semana / mes / 3 meses / año / año hasta hoy)
- Buscador en historial con filtros
- `ExerciseProgressScreen` + `MuscleGroupProgressScreen`

**FASE 8 — Dashboard (2 semanas)**
- `HomeScreen` con todos los widgets
- Cada widget con selector de período independiente
- Gráficos Vico interactivos (touch para valor exacto)
- Widgets: volumen, consistencia, peso corporal, pasos, FC, sueño, tiempo actividad, minutos semana
- Lógica de consistencia: con plan vs sin plan

**FASE 9 — Composición Corporal (1.5 semanas)**
- `BodyCompositionScreen`: tabla de valores actuales + gráficos de evolución
- `ManualBodyEntryScreen`: entrada de todos los campos
- Diferenciación visual de qué datos son sincronizables con HC y cuáles son solo manuales

**FASE 10 — Health Connect (3 semanas)**
- `HealthConnectManager.kt`: toda la lógica de permisos, lectura y escritura
- Import: pasos, calorías, sueño, frecuencia cardíaca → Room
- Export: workout sessions → HC ExerciseSession records
- Export: weight, body fat, lean body mass, water → HC records correspondientes
- `HcSyncLog`: registro de timestamps de sync
- Política de conflictos: timestamp más reciente gana
- Pantalla de gestión de permisos HC (si se revocan)
- Integración con Dashboard (pasos, FC, sueño vienen de HC)

**FASE 11 — Plan Semanal + Notificaciones (2 semanas)**
- `WeeklyPlanScreen`: cards por día, asignación de rutinas, hora de notificación por día
- `DailySummaryWorker` + `WeeklySummaryWorker` + `TrainingReminderWorker`
- Canales de notificación (3 canales separados)
- `NotificationHelper` con templates de mensajes motivacionales
- Integración con Dashboard: widget de consistencia usa datos del plan

**FASE 12 — Backup + Export (2 semanas)**
- `DriveApiService.kt`: Retrofit interface para Drive REST API v3
- `DriveBackupManager.kt`: serialize → encrypt (AES-256-GCM, clave derivada de password) → upload
- `BackupRestoreScreen`: listar backups, crear manual, restaurar
- `BackupWorker`: backup automático diario si hay cambios
- Export JSON completo + CSV por tipo
- `ExportScreen` con opciones y Share Sheet

**FASE 13 — Wear OS (4 semanas)**
- Semana 1: `WearableDataManager.kt` en módulo phone — DataClient + MessageClient
- Semana 2: `WearMainActivity` + navegación Wear Compose
- Semana 3: `WearRestTimerScreen` + `WearMessageListenerService` (phone recibe mensajes del reloj)
- Semana 4: integración completa con ActiveWorkout — phone → watch sync, watch → phone actions

**FASE 14 — Seguridad + Hardening (1 semana)**
- Revisar ProGuard/R8 rules (Room, Hilt, Retrofit, Kotlinx Serialization, SQLCipher)
- Verificar que no hay logs sensibles en builds release
- `OkHttp logging interceptor` solo en debug
- Revisión completa de permisos en manifest (eliminar cualquier permiso no usado)
- Test de `FLAG_SECURE` en todas las pantallas marcadas
- Verificar que el keystore de release está correctamente configurado y NO en el repo

**FASE 15 — Testing (3 semanas)**
- **Unit tests (MockK):** todos los Use Cases, ViewModels, EncryptionManager, lógica de conflictos HC
- **Integración (Room in-memory):** DAOs, repositorios, migraciones
- **Flows (Turbine):** StateFlows de ViewModels, emissions de LocationTracker
- **Compose UI tests:** pantallas críticas (ActiveWorkout, Login, Onboarding)
- **WorkManager tests:** workers con `work-testing`
- Target mínimo: **70% cobertura en capas domain y data**

**FASE 16 — Polish + Performance + Accesibilidad (2 semanas)**
- Auditoría de animaciones: transiciones entre pantallas, micro-animaciones en botones/checkboxes
- Performance: `LazyColumn` profiling, eliminar recomposiciones innecesarias, `remember` y `derivedStateOf` donde aplique
- Accesibilidad: contraste WCAG AA verificado en light y dark mode, `contentDescription` en todos los iconos sin texto, soporte TalkBack básico
- Revisión completa de strings ES/EN — verificar que ningún texto está hardcodeado
- Tamaño de APK: verificar ProGuard reduce correctamente

**FASE 17 — Play Store + Release (1.5 semanas)**
- Redactar Privacy Policy completa y publicarla en URL accesible
- Completar `store listing`: descripción, screenshots (teléfono + tablet + Wear), icon
- Enviar solicitud de verificación a Google para uso de Health Connect (proceso puede tomar 1-4 semanas — iniciar al principio de esta fase)
- Generar AAB firmado con release keystore
- Subir a Play Store Internal Testing → revisar → Producción

---

## 10. POST-MVP (VERSIONES FUTURAS)

| Feature | Notas |
|---------|-------|
| **AI coaching premium** | Análisis de progreso, sugerencias de peso/volumen, detección de estancamiento |
| **Auto-export mensual** | Ya hay flag preparado. Añadir `MonthlyExportWorker` y activar flag |
| **Custom icons para ejercicios** | Reemplazar Material Icons con iconografía propia |
| **Social features** | Compartir logros, retos con amigos, comunidad — requiere backend propio |
| **iOS** | Solo viable migrando a KMP (Kotlin Multiplatform) o Flutter — decisión para v3+ |
| **Apple Health / Garmin** | Expansión de integración de salud |
| **Billing Library** | Cuando se active premium: `com.android.billingclient:billing-ktx` |

---

## 11. DECISIONES DE DISEÑO RELEVANTES

| Decisión | Alternativa descartada | Razón |
|----------|------------------------|-------|
| SQLCipher para cifrado DB | EncryptedRoom (androidx.security) | EncryptedRoom está deprecated desde 2023 |
| Retrofit directo para Drive | Google API Client library | API Client trae deps conflictivas con OkHttp y pesa ~5MB extra |
| MockK para testing | Mockito | Mockito es Java; MockK es Kotlin-native, mejor integración con coroutines |
| Clave backup derivada de password | Clave ligada al Keystore del dispositivo | Permite restaurar en nuevo dispositivo si se recuerda la contraseña |
| ForegroundService para workout timer | ViewModel con CountDownTimer | ViewModel se destruye cuando la app pasa a background |
| i18n desde Fase 1 | Internacionalizar en Fase final | Añadirlo al final obliga a revisar las 27 pantallas una por una |
| Argon2id descartado | PBKDF2-HMAC-SHA256 | Argon2 requiere librería nativa (NDK); PBKDF2 con 600k iter (OWASP) es suficiente para uso local |
| BIOMETRIC_STRONG obligatorio | BIOMETRIC_WEAK | BIOMETRIC_WEAK acepta reconocimiento facial 2D (inseguro) |

---

*Fin del documento — ATLAS PEAK Spec v2.1 — Mayo 2026*
