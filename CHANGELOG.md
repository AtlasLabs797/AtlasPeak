# CHANGELOG.md — Registro de cambios

> Trazabilidad de todo lo que se hace en el proyecto. Cada sesión de trabajo añade una
> entrada. Formato basado en [Keep a Changelog](https://keepachangelog.com/es/).
> Orden: más reciente arriba.
>
> Tipos: `Añadido` · `Cambiado` · `Corregido` · `Eliminado` · `Seguridad` · `Deprecado`

---

## [No publicado]

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
