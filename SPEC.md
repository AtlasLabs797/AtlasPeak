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
| G | `USE_FINGERPRINT` deprecado (minSdk 31) | Eliminado; sin gate local tampoco se declara `USE_BIOMETRIC` | SEC-008 |
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
| 3 | Wear OS dependency incorrecta (`androidx.wear:wear`) | Diferida a v2; si se reactiva, usar `play-services-wearable` + `wear.compose` |
| 4 | Mockito en proyecto 100% Kotlin | Descartado; los tests actuales usan fakes manuales y `kotlinx-coroutines-test` |
| 5 | "E2E encryption con Keystore" en Health Connect | Corregido: Keystore es para almacenamiento local, no tránsito a HC |
| 6 | Rate limiting aplicado a Google Sign-In | Obsoleto: no hay contraseña de entrada en v1 |
| 7 | Health Connect full integration como feature premium | Eliminado de premium — es core gratuito |
| 8 | Báscula "integrada directamente" | Corregido: integración indirecta vía app báscula → Health Connect |
| 9 | Datos de báscula inexistentes en Health Connect | Documentados correctamente (4 tipos soportados, resto solo manual) |
| 10 | Google Sign-In deprecated (GMS Auth) | Obsoleto: no hay login de app; Drive usa `AuthorizationClient` |
| 11 | Sin onboarding flow | Añadido como fase de desarrollo y pantallas |
| 12 | Sin perfil de usuario | Añadido: nombre, edad, altura, género, objetivo |
| 13 | Planificación semanal sin tab asignado | Asignada a Tab Perfil como sub-pantalla |
| 14 | Retrofit sin propósito claro | Aclarado: exclusivamente para Drive REST API v3 |
| 15 | Timeline 30 meses injustificado | Recalculado: 36 semanas full-time (~9 meses) |
| 16 | Sin política de conflictos de sync | Definida: dato más reciente tiene prioridad |
| 17 | Sin modelo de feature flags | Flags premium diferidos hasta que haya billing o features restringidas reales |
| 18 | "Analytics local" sin definición | Eliminado — zero tracking, sin sistema de analytics |
| 19 | **PBKDF2 iterations insuficientes (100k)** | Actualizado a 200.000 iter. PBKDF2-HMAC-SHA256 |
| 20 | **Sin Foreground Service para entrenamiento activo** | Añadido `WorkoutForegroundService` y `CardioForegroundService` |
| 21 | **Clave backup ligada al dispositivo (Keystore)** | Clave de backup derivada de passphrase de backup — restaurable en nuevo dispositivo |
| 22 | **Sin network_security_config.xml** | Añadida configuración: solo HTTPS, sin cleartext |
| 23 | **i18n en Fase 16 (demasiado tarde)** | Movida a Fase 1 — strings.xml desde el inicio |
| 24 | **Sin FLAG_SECURE en pantallas sensibles** | Obsoleto por SEC-026: las capturas quedan permitidas en toda la app |
| 25 | **Biometría sin especificar nivel** | Obsoleto: sin gate local, biometría no se usa en v1 |
| 26 | **Sin WearableListenerService en manifest** | Conservado en el diseño v2; no se declara en v1 |
| 27 | **Export a almacenamiento público** | Corregido: export a almacenamiento privado + share via ShareSheet |
| 28 | **Sin estrategia de Room migrations** | Añadida — `fallbackToDestructiveMigration()` prohibido en producción |
| 29 | **Permisos Health Connect no listados** | Listados explícitamente (lectura y escritura) |
| 30 | **Sin estrategia de crash reporting** | Android Vitals via Play Console (automático, sin SDK) |
| 31 | **Permisos AndroidManifest incompletos** | Lista completa añadida |

---

## 1. DESCRIPCIÓN DEL PROYECTO

**Atlas Peak** es una aplicación nativa Android de gestión de entrenamientos personales. Filosofía **local-first**: todos los datos residen en el dispositivo, sin backend propio ni servidor de Atlas Peak. El único cloud involucrado es Google Drive para backup cifrado, controlado completamente por el usuario.

La app cubre el ciclo completo del entrenamiento: planificar rutinas, ejecutar sesiones de fuerza o cardio, monitorear composición corporal, visualizar progreso mediante gráficos y sincronizar con el ecosistema de salud del dispositivo (Health Connect en v1; Wear OS diferido a v2).

**Distribución:** APK de desarrollo personal → publicación en Google Play Store cuando esté completa.  
**Monetización:** v1 completamente gratuita. Las features premium quedan diferidas a versiones futuras; sin feature flags activos ni billing library todavía.
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
  - Timer enviado al reloj Wear OS simultáneamente (v2; fuera de v1)
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
| % Agua corporal | ❌ | ✅ |
| Masa de agua corporal (kg) | ✅ | ✅ |
| Grasa visceral | ❌ | ✅ |
| % Proteína | ❌ | ✅ |
| Masa ósea (kg) | ❌ | ✅ |
| Edad corporal | ❌ | ✅ |

> **Importante sobre báscula inteligente (Xiaomi / Renpho):** La integración es **indirecta**. La báscula sincroniza datos con su app propietaria (Zepp Life, Renpho App) y esa app escribe en Health Connect. Atlas Peak puede leer peso, grasa corporal, masa magra y masa de agua corporal desde Health Connect si el usuario concede esos permisos. `% Agua corporal`, grasa visceral, proteína, masa ósea y edad corporal siguen siendo manuales.

- Pantalla principal: tabla de valores actuales + gráficos de evolución por métrica (scroll vertical)
- Entrada manual disponible para todos los campos en cualquier momento
- Los datos introducidos en Atlas Peak se exportan a Health Connect (solo los 4 tipos soportados:
  peso, grasa corporal, masa muscular y masa de agua corporal)
- **Política de conflicto:** el dato con timestamp más reciente tiene prioridad, independientemente de la fuente

### 2.7 PLANIFICACIÓN SEMANAL

- Configurar días de entrenamiento de la semana (Lunes a Domingo)
- Cada día puede tener cero, una o varias sesiones planificadas.
- Una sesión puede ser fuerza (rutina) o cardio (tipo + duración objetivo).
- Se permite fuerza + cardio el mismo día y doble sesión del mismo tipo.
- Marcar días explícitamente como descanso eliminando sesiones del día.
- Cards visuales por día: lista de sesiones + checkbox de completado por sesión.
- La semana empieza en lunes.
- Configurar hora de notificación de recordatorio por sesión.
- Si no hay plan configurado: el widget de consistencia en el dashboard muestra días activos vs total de días del período.
- **Ubicación en navegación:** Tab "Perfil" → sub-pantalla "Planificación"

### 2.8 NOTIFICACIONES

- **Canal 1 — Recordatorios de entrenamiento:** muestra nombre de rutina del día + hora configurada en el plan semanal. Ejemplo: "Pecho + Tríceps sobre las 18:00"
- **Canal 2 — Mensajes motivacionales:** mensajes aleatorios predefinidos, activables/desactivables en ajustes
- **Canal 3 — Resúmenes:**
  - Resumen diario: 8:30 AM por defecto, hora configurable por usuario
  - Resumen semanal: lunes a las 8:30 AM
  - Contenido: volumen del período, consistencia, peso corporal si hay datos recientes
- Todos los canales configurables individualmente en ajustes
- `WorkManager` para programación y ejecución en background. Estos avisos son **best-effort**: Android puede ajustar la hora exacta por batería, Doze o cuotas del sistema. No se pide `SCHEDULE_EXACT_ALARM` en v1.
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
READ_WEIGHT
READ_BODY_FAT
READ_LEAN_BODY_MASS
READ_BODY_WATER_MASS
```

**Permisos de ESCRITURA requeridos:**
```
WRITE_EXERCISE
WRITE_WEIGHT
WRITE_BODY_FAT
WRITE_LEAN_BODY_MASS
WRITE_BODY_WATER_MASS
```

- **Import desde Health Connect:** pasos diarios, calorías activas, sueño, frecuencia cardíaca,
  peso, grasa corporal, masa magra y masa de agua corporal.
- **Export a Health Connect:** sesiones de entrenamiento completadas, peso, grasa, masa muscular,
  masa de agua corporal
- La sincronización es parcial por capacidad: si falta un permiso de sueño, por ejemplo, no se
  bloquea la importación de pasos ni la exportación de entrenamientos.
- `HcSyncLog`: tabla que registra el último timestamp de lectura y escritura por tipo de dato
- **Política de conflicto:** dato con timestamp más reciente gana — Atlas Peak no sobreescribe si el dato local es más reciente

### 2.10 SMARTWATCH (WEAR OS) - DIFERIDO A V2

> Fuera del alcance de v1. Esta seccion conserva el diseno del protocolo para reactivarlo en
> v2 sin redisenar la app de telefono. En v1 no existe modulo `wear/`, no hay dependencia
> `play-services-wearable`, no se declara `WearableListenerService` y Fase 13 solo verifica
> que el aplazamiento sigue limpio.

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

- `WearableListenerService` se registrara en v2; no se declara en el manifest v1.

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

- **Sin contraseña para entrar en la app.** Tras completar onboarding, `Launch` navega directo a `Home`.
- **Google Drive OAuth opcional** mediante Google Identity `AuthorizationClient`; la app no depende de backend ni de cuenta Google.
- **DB local:** encriptada con SQLCipher. La clave de cifrado se genera aleatoriamente, se almacena cifrada en Android Keystore (nunca en texto plano).
- **Contraseña/passphrase solo para backups cifrados:** se introduce en la pantalla de Backup al crear/restaurar copias y deriva la clave AES-256-GCM con PBKDF2-HMAC-SHA256 600.000 iteraciones.
- **Sin biometría de desbloqueo local** en v1 tras retirar el gate de entrada. No hay permiso, dependencia ni flujo visible de desbloqueo.
- Capturas de pantalla permitidas en toda la app por decision de producto (SEC-026).
- **Riesgo aceptado:** si alguien usa el móvil ya desbloqueado, puede abrir Atlas Peak y ver/exportar datos. La defensa pasa a ser el bloqueo del dispositivo.

**Escenario de pérdida de backup:**
Si el usuario olvida la passphrase usada para cifrar un backup, esa copia no se puede restaurar. Google Drive solo guarda binario cifrado; Atlas Peak no tiene backend ni recuperación de contraseña.

### 2.13 BACKUP Y RECUPERACIÓN

- Backup a **Google Drive App Data folder** (carpeta privada, invisible para el usuario, solo accesible por Atlas Peak)
- Acceso via **Drive REST API v3** con OAuth2 — scope: `https://www.googleapis.com/auth/drive.appdata`
- El permiso Drive se obtiene con Google Identity `AuthorizationClient`; no hay ID token de login de app que reutilizar como bearer token.
- Retrofit + OkHttp como cliente HTTP (consistente con el resto del stack)
- **Proceso de backup:**
  1. Serializar toda la DB a JSON (Kotlinx Serialization)
  2. Derivar clave de 256 bits con PBKDF2-HMAC-SHA256 (600k iter) desde la passphrase de backup + un salt **propio del backup** (16 bytes aleatorios, generado por backup)
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
  3. Download + descifrar con passphrase de backup
  4. Decodificar JSON y aplicar `BackupSnapshotUpgrader` al schema actual.
  5. Transacción Room completa: DROP + INSERT de todos los datos
- **Export manual:** JSON (estructura completa exportable) o CSV (un archivo por tipo: sesiones, sets, cardio, composición corporal)
- Export guarda en almacenamiento privado de la app, luego comparte via Android `ShareSheet` — el usuario elige dónde enviarlo (Drive, email, etc.)
- Export incluye: rutinas, ejercicios, sesiones, sets, cardio, composición corporal, plan semanal
- Export JSON/CSV en claro no exige contraseña tras retirar el gate local; la UI muestra una
  confirmación explícita porque contiene datos deportivos/personales sensibles.
- Export CSV neutraliza celdas textuales con prefijo de formula de hoja de calculo (`=`, `+`, `-`, `@`) anteponiendo apostrofe en la salida.
- Los exports temporales se limpian automáticamente para reducir exposición residual.

### 2.14 ONBOARDING (PRIMER LANZAMIENTO)

Flujo lineal; todos los pasos salvo la pantalla final son saltables:

1. **Bienvenida:** propuesta de valor en una pantalla, logo, CTA "Empezar"
2. **Conectar Google (OPCIONAL, saltable):** Google Identity solo para habilitar el backup en Drive. *(v2.2: ya NO es obligatorio ni "crea cuenta" — no hay backend. La app funciona 100% offline. Se puede conectar más tarde desde Backup.)*
3. **Perfil básico:** nombre, edad, altura, género, objetivo (salteable)
4. **Permisos de notificaciones:** explicación + solicitud (salteable con advertencia)
5. **Health Connect:** explicación de qué datos se leen/escriben + solicitud de permisos (salteable)
6. **Ubicación para cardio GPS:** solicitud de permiso `ACCESS_FINE_LOCATION` (salteable)
7. **Listo:** pantalla de confirmación → Home

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
Autenticación:              Sin login de app; Google Identity AuthorizationClient solo para Drive
Health Connect:             androidx.health.connect:connect-client (estable)
Location:                   Google Play Services Location (FusedLocationProvider)
Maps:                       Google Maps Compose
Background jobs:            WorkManager
Foreground Services:        WorkoutForegroundService, CardioForegroundService
Wear OS comunicación:       Diferido a v2 (DataClient + MessageClient)
Wear OS UI:                 Diferido a v2 (Wear Compose)
Versioning:                 Git + GitHub
CI/CD:                      GitHub Actions
Testing:                    JUnit5 + fakes manuales + Room in-memory + kotlinx-coroutines-test
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
    alias(libs.plugins.kotlin.serialization)
}

dependencies {

    // Las versiones viven solo en gradle/libs.versions.toml.
    // ── Compose BOM ─────────────────────────────────────────────────────────
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // ── Lifecycle + ViewModel ────────────────────────────────────────────────
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)  // para ForegroundService

    // ── Navigation ───────────────────────────────────────────────────────────
    implementation(libs.androidx.navigation.compose)

    // ── Room + SQLCipher ─────────────────────────────────────────────────────
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    // SQLCipher: cifrado transparente sobre SQLite
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite.ktx)

    // ── Hilt ─────────────────────────────────────────────────────────────────
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // ── Health Connect (v2.2: artifact renombrado + estable) ─────────────────
    // El grupo cambió de androidx.health a androidx.health.connect y ya hay 1.1.0 estable.
    implementation(libs.androidx.health.connect.client)

    // ── Vico Charts (v2.2: estable 2.x; antes era 2.0.0-beta.2) ──────────────
    implementation(libs.vico.compose.m3)

    // ── Cifrado ──────────────────────────────────────────────────────────────
    implementation(libs.androidx.security.crypto)
    // EncryptedSharedPreferences + acceso al Keystore

    // ── Google Drive OAuth ───────────────────────────────────────────────────
    implementation(libs.play.services.auth) // AuthorizationClient para Drive

    // ── Google Drive REST API v3 (via Retrofit, sin Google API Client library) ─
    // Se consume directamente con Retrofit + bearer token OAuth2
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    // DriveApiService envia Authorization: Bearer {access_token} obtenido por AuthorizationClient

    // ── Kotlinx Serialization ────────────────────────────────────────────────
    implementation(libs.kotlinx.serialization.json)

    // ── WorkManager ──────────────────────────────────────────────────────────
    implementation(libs.androidx.work.runtime.ktx)

    // ── Location + Maps ──────────────────────────────────────────────────────
    implementation(libs.play.services.location)
    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)

    // Wear OS queda diferido a v2: no incluir play-services-wearable en v1.

    // ── Splash Screen API ────────────────────────────────────────────────────
    implementation(libs.androidx.core.splashscreen)

    // ── DataStore (ajustes y preferencias) ───────────────────────────────────
    implementation(libs.androidx.datastore.preferences)

    // ── Coroutines ────────────────────────────────────────────────────────────
    implementation(libs.kotlinx.coroutines.android)

    // ── Testing ──────────────────────────────────────────────────────────────
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.work.testing)
}
```

### 3.3 DEPENDENCIAS MÓDULO WEAR (v2, diferido)

No se incluyen en v1. Mantener este bloque solo como referencia cuando se reactive el modulo
`wear/` en v2. Las dependencias Wear deben añadirse primero a `gradle/libs.versions.toml` y
despues consumirse como aliases `libs.*`; no hardcodear versiones aqui ni en Gradle.

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

<!-- Internet (Google Drive backup opcional) -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

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
  password_hash     TEXT                -- nullable; legado de auth local, no usado para entrar
  password_salt     TEXT                -- nullable; legado de auth local, no usado para entrar
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
  color_tag         TEXT                -- hex ej: "#D32F2F", nullable
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
  body_water_mass_kg REAL
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
  order_index       INTEGER NOT NULL    -- orden de sesión dentro del día
  routine_id        TEXT                FK → routines.id, nullable
  is_rest_day       INTEGER NOT NULL DEFAULT 0
  notification_enabled INTEGER NOT NULL DEFAULT 1
  notification_time TEXT                -- "HH:mm", nullable
  type              TEXT    NOT NULL DEFAULT 'STRENGTH' -- STRENGTH / CARDIO
  cardio_type_id    TEXT                FK → cardio_types.id, nullable
  cardio_target_duration_sec INTEGER    nullable

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
│       │   ├── wear/                        ← v2, no existe en v1
│       │   │   └── WearableDataManager.kt   ← DataClient + MessageClient
│       │   └── security/
│       │       └── EncryptionManager.kt     ← Keystore, AES-256-GCM, PBKDF2
│       │
│       ├── domain/
│       │   ├── model/                       ← data classes sin anotaciones Room/Retrofit
│       │   ├── repository/                  ← interfaces (contratos)
│       │   └── usecase/
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
│       │   │   ├── auth/            ← legado local no enrutable en v1
│       │   │   ├── onboarding/      ← 7 pantallas del flujo inicial
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
│       ├── util/                    ← extensiones, formatters, constantes
│       └── MainActivity.kt
│
├── wear/                                    ← v2, módulo Wear OS (APK separado; no existe en v1)
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
                              Wear Data Layer (v2)
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

### 5.4 Features premium diferidas

v1 no tiene features premium, billing ni clase `FeatureFlags`. No mantener flags muertos "por si acaso":
cuando exista una feature restringida real, se añadirá el mecanismo junto con su fuente de verdad
(DataStore, billing o backend futuro) y sus tests.

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
| `primary` | Accent principal | Rojo `#D32F2F` |
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

Flujo inicial:
  SplashScreen
  └── Launch
      ├── OnboardingFlow (solo primer lanzamiento, 7 pasos)
      └── Home (si onboarding ya esta completado)
```

**Total: 27 pantallas.**

---

## 7. SEGURIDAD

### 7.1 Autenticación

| Capa | Tecnología | Notas |
|------|------------|-------|
| Google Drive opcional | Google Identity `AuthorizationClient` | Solo pide scope `drive.appdata` desde Backup |
| Entrada a la app | Sin contraseña local | `Launch` navega a `Home` tras onboarding |
| Passphrase backup | PBKDF2-HMAC-SHA256, 600.000 iter, salt 16B en archivo | Solo cifra/restaura backups |
| Biometría | No usada en v1 para desbloqueo | Sin gate local no aporta UX |
| Pantallas sensibles | Capturas permitidas | `FLAG_SECURE` desactivado por SEC-026; rutas sensibles siguen clasificadas para auditoria |

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
2. Passphrase de backup + salt_backup → PBKDF2-HMAC-SHA256 → clave maestra de 256 bits
3. JSON completo de la DB → cifrado con AES-256-GCM + IV aleatorio (12 bytes)
4. Formato del archivo:
   [magic "ATPK" (4B)] [versión (1B)] [iteraciones (4B BE)] [salt_backup (16B)] [IV (12B)] [ciphertext + tag GCM (16B)]
5. Upload a Drive App Data folder: atlas_peak_backup_{timestamp}.enc
   - Drive REST `uploadType=multipart` usa `multipart/related`: metadata JSON primero, binario
     cifrado despues. No usar `multipart/form-data`.

Por qué la cabecera:
- El salt y las iteraciones NO son secretos; deben acompañar al ciphertext para poder
  derivar la misma clave en cualquier dispositivo.
- En v2.1 el formato era [IV][ciphertext] sin salt → en un dispositivo nuevo no había forma
  de derivar la clave → la feature "restaurar con tu passphrase" estaba rota. (SEC-001)

Ventaja clave (ahora sí funciona):
- La clave NO está ligada al dispositivo físico.
- Si el usuario instala en un nuevo dispositivo y recuerda la passphrase de backup,
  puede restaurar el backup correctamente (lee la cabecera, deriva la clave, descifra).
- Google solo ve datos binarios cifrados, nunca el contenido.

Aviso: cambiar la passphrase de backup no re-cifra backups previos; esas copias siguen
requiriendo la passphrase con la que se crearon. La app debe advertirlo si se ofrece guardar
una nueva passphrase para backup automático. (SEC-002)

Backup automatico: como no hay backend ni refresh server-side, la app solo puede cifrar en
segundo plano si el usuario acepta guardar la contraseña de backup cifrada en el dispositivo
(`EncryptedSharedPreferences` + Android Keystore). Si no hay grant silencioso de Drive o no
hay contraseña guardada, `BackupWorker` termina sin lanzar UI de consentimiento. (SEC-018)
El detector de cambios ignora `app_settings.last_backup_at` para no crear backups repetidos por
la propia marca de exito del backup anterior.
```

### 7.4 Red

- `network_security_config.xml`: cleartext prohibido en producción
- Todas las llamadas a Drive REST API usan `Authorization: Bearer {access_token}`
- El `access_token` sale de `AuthorizationClient` con scope `drive.appdata`; no hay login de
  app ni ID token que reutilizar contra Drive.
- `OkHttp logging interceptor` desactivado en builds release (BuildConfig.DEBUG)

### 7.5 Export de Datos

- Los archivos exportados se crean en `context.filesDir` (almacenamiento privado de la app)
- Se comparten via `FileProvider` + Android `ShareSheet`
- El archivo no queda accesible a otras apps directamente
- `FileProvider` configurado en manifest con `android:exported="false"`
- El backup cifrado contiene las tablas necesarias para restaurar (`users` incluido). El
  export manual JSON/CSV, al no estar cifrado, excluye `users` y `auth_security`.
- El export manual en claro no exige reautenticacion local tras retirar el gate de entrada.
- El export CSV neutraliza celdas textuales con prefijo de formula de hoja de calculo
  (`=`, `+`, `-`, `@`) anteponiendo apostrofe en la salida.

### 7.6 Puntos Débiles Conocidos y Aceptados

| Limitación | Por qué se acepta |
|------------|-------------------|
| Sin contraseña para abrir la app | Decisión de producto: fricción cero; se confía en el bloqueo del dispositivo |
| Backup no restaurable si se olvida la passphrase | Documentado y advertido en UI. No hay solución sin comprometer cifrado |
| Capturas permitidas en rutas sensibles | Decision de producto (SEC-026); facilita uso personal/QA, pero el usuario puede exponer datos si comparte capturas |

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
1. OAuth 2.0 Client ID (tipo "Web application")  → para Drive OAuth
   - Registrar también el SHA-1 de tu keystore (debug y release) como cliente Android.
2. Maps SDK for Android API key                   → restringida por package name + SHA-1.

Ambos valores se colocan en secrets.properties (LOCAL, gitignored), nunca en el repo:
   OAUTH_WEB_CLIENT_ID=xxxxxxxx.apps.googleusercontent.com
   MAPS_API_KEY=TU_MAPS_API_KEY

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
# Crear keystore (hacer UNA VEZ, guardar fuera del repo)
mkdir -p "$HOME/.atlaspeak/release"
keytool -genkey -v \
  -keystore "$HOME/.atlaspeak/release/atlas-peak-release.jks" \
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

`keystore.properties` (local, no en repo; recomendado fuera del árbol del proyecto):
```properties
storeFile=atlas-peak-release.jks
storePassword=TU_PASSWORD
keyAlias=atlas-peak
keyPassword=TU_PASSWORD
```

Gradle busca primero `ATLAS_PEAK_KEYSTORE_PROPERTIES` y, como compatibilidad local, después
`keystore.properties` en la raíz. Para release:

```bash
export ATLAS_PEAK_KEYSTORE_PROPERTIES="$HOME/.atlaspeak/release/keystore.properties"
./gradlew assembleRelease
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
- Wear OS queda fuera de v1; se conserva solo el diseño en `SPEC.md §2.10`.

**FASE 2 — Seguridad local y Google opcional (2 semanas)**
- Google Identity `AuthorizationClient` opcional para Drive, sin cuenta obligatoria.
- Sin `LoginScreen` ni contraseña para entrar en la app.
- SQLCipher + Android Keystore para cifrado local transparente.
- Passphrase de backup con PBKDF2-HMAC-SHA256 (600k iter) solo en flujos de Backup.
- `EncryptionManager.kt`: wrappers de Keystore, AES-256-GCM, PBKDF2.

**FASE 3 — Onboarding (1 semana)**
- Flujo de 7 pasos completo
- Solicitud de permisos: notificaciones, Health Connect, ubicación
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
- Import: pasos, calorías, sueño, frecuencia cardíaca, peso, grasa corporal, masa magra y
  masa de agua corporal → Room
- Sync parcial por capacidad: un permiso denegado no bloquea el resto.
- Export: workout sessions → HC ExerciseSession records
- Export: weight, body fat, lean body mass, water → HC records correspondientes
- `HcSyncLog`: registro de timestamps de sync
- Política de conflictos: timestamp más reciente gana
- Pantalla de gestión de permisos HC (si se revocan)
- Integración con Dashboard (pasos, FC, sueño vienen de HC)

**FASE 11 — Plan Semanal + Notificaciones (2 semanas)**
- `WeeklyPlanScreen`: cards por día, lista de sesiones fuerza/cardio, hora de notificación
  por sesión
- `DailySummaryWorker` + `WeeklySummaryWorker` + `TrainingReminderWorker`
- Canales de notificación (3 canales separados)
- `NotificationHelper` con templates de mensajes motivacionales
- Integración con Dashboard: widget de consistencia usa datos del plan

**FASE 12 — Backup + Export (2 semanas)**
- `DriveApiService.kt`: Retrofit interface para Drive REST API v3
- `AuthorizationClient` con scope `drive.appdata`; no usar ID token como token Drive
- `DriveBackupManager.kt`: serialize → encrypt (AES-256-GCM, clave derivada de passphrase) → upload
- `BackupRestoreScreen`: listar backups, crear manual, restaurar
- `BackupWorker`: backup automático diario si hay cambios
- Export JSON completo sin auth secrets + CSV ZIP por tipo, sin step-up local, con aviso
  explícito de datos en claro
- `BackupRestoreScreen` incluye opciones de export y Share Sheet

**FASE 13 — Wear OS diferido a v2**
- No implementar modulo `wear/` en v1.
- No añadir `play-services-wearable` ni clases `WearableDataManager`/`WearableListenerService`.
- Cierre de fase v1: verificar que `settings.gradle.kts` solo incluye `:app`, que no hay
  dependencias/codigo Wear activo y que la documentacion no contradice el aplazamiento.

**FASE 14 — Seguridad + Hardening (1 semana)**
- Revisar ProGuard/R8 rules (Room, Hilt, Retrofit, Kotlinx Serialization, SQLCipher)
- Verificar que no hay logs sensibles en builds release
- `OkHttp logging interceptor` solo en debug
- Revisión completa de permisos en manifest (eliminar cualquier permiso no usado)
- Test de politica de capturas: `FLAG_SECURE` no debe reintroducirse sin decision explicita
- Verificar que el keystore de release está correctamente configurado y NO en el repo

**FASE 15 — Testing (3 semanas)**
- **Unit tests:** todos los Use Cases, ViewModels, EncryptionManager, lógica de conflictos HC
- **Integración (Room in-memory):** DAOs, repositorios, migraciones
- **Flows/corrutinas:** StateFlows de ViewModels, emissions de LocationTracker con fakes manuales y `kotlinx-coroutines-test`
- **Compose UI tests:** pantallas críticas (ActiveWorkout, Onboarding, Backup)
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
- Completar `store listing`: descripción, screenshots (teléfono + tablet; Wear solo si v2 se reactiva), icon
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
| Fakes manuales en tests actuales | MockK/Turbine sin uso | Mantiene el grafo de dependencias pequeño; reintroducir librerías solo cuando aporten valor real |
| Clave backup derivada de passphrase | Clave ligada al Keystore del dispositivo | Permite restaurar en nuevo dispositivo si se recuerda la passphrase |
| ForegroundService para workout timer | ViewModel con CountDownTimer | ViewModel se destruye cuando la app pasa a background |
| i18n desde Fase 1 | Internacionalizar en Fase final | Añadirlo al final obliga a revisar las 27 pantallas una por una |
| Argon2id descartado | PBKDF2-HMAC-SHA256 | Argon2 requiere librería nativa (NDK); PBKDF2 con 600k iter (OWASP) es suficiente para uso local |
| Biometría local descartada en v1 | Biometría débil/fuerte | Sin gate local no aporta UX; reabrirla exige decisión de producto |

---

*Fin del documento — ATLAS PEAK Spec v2.2 — Mayo 2026*
