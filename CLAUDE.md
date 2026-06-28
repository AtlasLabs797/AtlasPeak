# CLAUDE.md — Instrucciones maestras para Claude Code

> Este archivo es la fuente de verdad operativa del proyecto **Atlas Peak**.
> Léelo entero antes de tocar una sola línea. Si algo aquí contradice un mensaje
> puntual del usuario, **gana el mensaje del usuario**, pero avísale del conflicto.

---

## 0. Qué es Atlas Peak (en una frase)

App Android nativa, **local-first**, de gestión de entrenamientos de fuerza y cardio,
con sincronización con Health Connect, backup cifrado en Google Drive y **cero tracking**.
Sin backend propio. Todos los datos viven en el dispositivo.

La especificación funcional completa está en **`SPEC.md`** (v2.2, ya corregida).
Este archivo (`CLAUDE.md`) define **cómo** se construye. `SPEC.md` define **qué** se construye.

---

## 1. Reglas de oro innegociables

1. **Una fase cada vez.** No empieces la fase N+1 hasta que la fase N esté completa,
   compilando, con tests verdes y registrada en `CHANGELOG.md`. El usuario te dará
   las fases de una en una.
2. **Nada de secretos en el repo. Jamás.** Ni API keys, ni keystore, ni
   `secrets.properties`, ni `google-services.json`. Si vas a escribir un secreto,
   párate y usa el mecanismo de `secrets.properties` (ver §6). Si detectas un secreto
   commiteado, es un incidente: regístralo en `SECURITY.md` y avisa.
3. **Arquitectura limpia, dependencias hacia adentro.** `presentation → domain → data`.
   La capa `presentation` **nunca** importa entities de Room ni clases de Retrofit.
   Trabaja con domain models.
4. **i18n desde el primer string.** Cero texto hardcodeado en UI. Todo va en
   `strings.xml` (ES) y `values-en/strings.xml` (EN). Si escribes `Text("Guardar")`,
   está mal. Es `Text(stringResource(R.string.action_save))`.
5. **Migraciones Room explícitas.** `fallbackToDestructiveMigration()` está
   **PROHIBIDO** en cualquier build que no sea un test. Cada cambio de schema = una
   `Migration` escrita a mano + bump de versión + test de migración.
6. **Registra todo.** Cada sesión de trabajo actualiza `CHANGELOG.md`. Cada bug que
   arregles va a `BUGS.md`. Cada hallazgo de seguridad va a `SECURITY.md`.
7. **Si dudas, pregunta.** Una pregunta concreta cada vez. No inventes requisitos.

---

## 2. Stack técnico (resumen — detalle en SPEC.md §3)

| Capa | Tecnología |
|------|-----------|
| Lenguaje | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Min/Target/Compile SDK | 31 / 35 / 36 |
| JDK | 17 |
| DB | Room + SQLCipher (cifrado transparente) |
| DI | Hilt |
| Estado | ViewModel + StateFlow + `collectAsStateWithLifecycle` |
| Gráficos | Vico (estable 2.x) |
| HTTP | Retrofit + OkHttp — **exclusivamente** para Drive REST API v3 |
| Serialización | Kotlinx Serialization |
| Cifrado | Android Keystore + EncryptedSharedPreferences + SQLCipher + AES-256-GCM |
| Auth/Backup | Sin login de app; Google Drive OAuth opcional + passphrase de backup |
| Health | `androidx.health.connect:connect-client` |
| Location | FusedLocationProvider |
| Maps | Google Maps Compose |
| Background | WorkManager + Foreground Services |
| Testing | JUnit5 + MockK + Turbine + Room in-memory + Compose Testing |

**Las versiones exactas viven SOLO en `gradle/libs.versions.toml`.** No pongas versiones
hardcodeadas en `build.gradle.kts`. Si necesitas subir una versión, edita el catálogo
y deja constancia en `CHANGELOG.md`.

---

## 3. Plan de fases (orden estricto)

Timeline orientativo full-time. Wear OS **diferido a v2** (ver §9).

| Fase | Contenido | Estado |
|------|-----------|--------|
| 0 | Bootstrap: proyecto compila, CI verde, docs creados | ⬜ |
| 1 | Fundación + i18n + tema + DB + seed data | ⬜ |
| 2 | Seguridad local + Google/backup opcional (sin contraseña de entrada) | ⬜ |
| 3 | Onboarding | ⬜ |
| 4 | Ejercicios y rutinas | ⬜ |
| 5 | Entrenamiento activo (fuerza) + WorkoutForegroundService | ⬜ |
| 6 | Cardio + GPS + CardioForegroundService | ⬜ |
| 7 | Historial y progreso | ⬜ |
| 8 | Dashboard | ⬜ |
| 9 | Composición corporal | ⬜ |
| 10 | Health Connect (import + export) | ⬜ |
| 11 | Plan semanal + notificaciones | ⬜ |
| 12 | Backup Drive + export | ⬜ |
| 13 | Seguridad + hardening | ⬜ |
| 14 | Testing (≥70% cobertura domain+data) | ⬜ |
| 15 | Polish + performance + accesibilidad | ⬜ |
| 16 | Play Store + privacy policy + release | ⬜ |
| (v2) | Wear OS | ⬜ aplazado |

Marca cada casilla cuando la fase cierre. El detalle de tareas por fase está en
`SPEC.md §9`.

---

## 4. Definición de "Hecho" (Definition of Done) por fase

Una fase NO está hecha hasta que:

- [ ] Compila en debug **y** release (`./gradlew assembleDebug assembleRelease`).
- [ ] `./gradlew test` pasa (unit) y `./gradlew lint` no añade warnings nuevos.
- [ ] Cero strings hardcodeados nuevos (revisa con `./gradlew lint`, regla `HardcodedText`).
- [ ] Si tocó schema: hay `Migration` + test de migración.
- [ ] Si tocó seguridad/permisos/red: anotado en `SECURITY.md`.
- [ ] `CHANGELOG.md` actualizado con qué se hizo.
- [ ] `DOCS_TECNICA.md` actualizado si cambió arquitectura/contratos.
- [ ] `DOCS_USUARIO.md` actualizado si cambió algo visible para el usuario.

---

## 5. Convenciones de código

- **Paquetes:** `com.atlaspeak.<capa>.<feature>` (`com.atlaspeak.domain.usecase.workout`).
- **Naming:**
  - Entities Room → sufijo `Entity` (`WorkoutSessionEntity`).
  - Domain models → sin sufijo (`WorkoutSession`).
  - DTOs de red → sufijo `Dto`.
  - UseCases → verbo + `UseCase` (`StartWorkoutSessionUseCase`), un `invoke()` público.
  - ViewModels → `XxxViewModel`, exponen `StateFlow<XxxUiState>`.
  - Composables de pantalla → `XxxScreen`; reutilizables → nombre descriptivo sin `Screen`.
- **Estado UI:** un `data class XxxUiState` por pantalla. Nada de exponer flows sueltos.
- **Coroutines:** trabajo pesado (PBKDF2, cifrado, IO) **siempre** en `Dispatchers.IO`.
  Nunca bloquees el main thread.
- **Errores:** sealed `Result`/`Either` en domain; no lances excepciones a través de capas.
- **Timestamps:** `Long`, Unix epoch **milisegundos**, UTC. Conversión a local solo en UI.
- **IDs:** `UUID` string generado localmente (salvo tablas seed con `INTEGER PK`).
- **Comentarios:** en español o inglés, pero **explica el porqué, no el qué**.

---

## 6. Manejo de secretos (LEER ANTES DE TOCAR AUTH/MAPS/DRIVE)

Hay tres secretos de configuración. **Ninguno** va al repo.

| Secreto | Para qué | Dónde vive |
|---------|----------|-----------|
| `MAPS_API_KEY` | Google Maps Compose (cardio GPS) | `secrets.properties` (local) |
| `OAUTH_WEB_CLIENT_ID` | Drive OAuth (`AuthorizationClient`) | `secrets.properties` (local) |
| Release keystore + passwords | Firmar el AAB | Fuera del repo, via `ATLAS_PEAK_KEYSTORE_PROPERTIES` |

- Plantilla versionada: **`secrets.properties.template`**. El usuario la copia a
  `secrets.properties` y rellena valores reales.
- `secrets.properties`, `keystore.properties`, `*.jks`, `local.properties` y
  `google-services.json` están en `.gitignore`. **Verifica que siguen ahí** antes de
  cualquier commit que toque config.
- El keystore de release no vive en la raiz del repo. Usar, por ejemplo,
  `%USERPROFILE%\.atlaspeak\release\keystore.properties` y apuntarlo con
  `ATLAS_PEAK_KEYSTORE_PROPERTIES`.
- En Gradle, lee `secrets.properties` y expón los valores via `manifestPlaceholders`
  (para la Maps key) y `BuildConfig` (para el OAuth client id). NO los escribas inline.
- **NO uses `google-services.json` ni el plugin `com.google.gms.google-services`.**
  Drive REST con `AuthorizationClient` no lo necesita. Si lo ves aparecer, bórralo.

---

## 7. Seguridad — checklist permanente

Aplica en CADA fase, no solo en la 13:

- [ ] ¿Hay algún `Log.d/e/i` con datos sensibles (email, token, password, ubicación)?
      Fuera. El logging interceptor de OkHttp **solo** en `BuildConfig.DEBUG`.
- [ ] ¿Pantalla con datos personales críticos? → las capturas siguen permitidas por SEC-026.
      No reintroduzcas `FLAG_SECURE` sin decision explicita de producto.
- [ ] ¿Nueva llamada de red? → solo HTTPS, pasa por `network_security_config.xml`.
- [ ] ¿Tocaste el backup? → el archivo cifrado **debe** llevar cabecera
      `[versión(1B)][iter(4B)][salt(16B)][IV(12B)][ciphertext+tag]`. Sin salt en el
      archivo, la restauración en otro dispositivo es imposible (ver `SECURITY.md` SEC-001).
- [ ] ¿Cambió la passphrase de backup? → los backups previos requieren la passphrase antigua (ver SEC-002).
- [ ] PBKDF2-HMAC-SHA256 con **600.000** iteraciones (no 200k) para backups cifrados.
- [ ] No reintroduzcas contraseña/biometría para entrar sin decisión explícita de producto (SEC-025).
- [ ] `allowBackup="false"` en el manifest (la DB cifrada no debe ir al backup de Android).
- [ ] Revisa que no pediste `ACCESS_BACKGROUND_LOCATION` (no se usa, dispara rechazo de Play).

---

## 8. Comandos frecuentes

```bash
./gradlew assembleDebug          # build debug
./gradlew assembleRelease        # build release (requiere ATLAS_PEAK_KEYSTORE_PROPERTIES o fallback local)
./gradlew test                   # unit tests (JVM)
./gradlew connectedAndroidTest   # tests instrumentados (requiere emulador/dispositivo)
./gradlew lint                   # análisis estático; mira reporte en app/build/reports/lint
./gradlew :app:dependencies      # árbol de dependencias (debug de conflictos)
gradle wrapper --gradle-version 8.x   # regenerar wrapper si falta el jar
```

---

## 9. Por qué Wear OS está aplazado (contexto de decisión)

El spec original metía Wear OS en v1 (4 semanas, ~11% del proyecto). Decisión revisada:
**no construir Wear hasta que el teléfono esté en producción y validado.** Razones:
es la pieza de mayor complejidad técnica (Data Layer API, módulo aparte, otro ciclo de
testing) y menor valor core. El protocolo de comunicación teléfono↔reloj ya está
diseñado en `SPEC.md §2.10` para cuando se retome. Para reactivarlo: añadir el módulo
`wear` a `settings.gradle.kts` y seguir el plan v2.

Si el usuario decide hacer Wear en v1, no pasa nada: el diseño está listo, solo cambia
el orden y el timeline.

---

## 10. Qué hacer al empezar CADA sesión

1. Lee el último bloque de `CHANGELOG.md` para saber dónde quedaste.
2. Mira la tabla de fases (§3): ¿qué fase está abierta?
3. Confirma con el usuario la tarea concreta de hoy.
4. Trabaja. Compila a menudo. No acumules cambios sin compilar.
5. Al cerrar: actualiza `CHANGELOG.md`, marca casillas, registra bugs/seguridad si aplica.

---

*Atlas Peak — CLAUDE.md — alineado con SPEC.md v2.2*
