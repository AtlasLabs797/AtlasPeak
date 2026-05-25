# AGENTS.md

> Contrato operativo para **cualquier agente de IA** que trabaje en este repo (Claude Code,
> y cualquier otro que lea `AGENTS.md`). Este archivo es deliberadamente corto: contiene las
> **reglas innegociables** y el **mapa de documentos**. El detalle (fases, convenciones,
> definición de "hecho") vive en `CLAUDE.md` y `SPEC.md` — **no se duplica aquí** para evitar
> que las dos fuentes se desincronicen. Si algo de detalle falta aquí, está en `CLAUDE.md`.

---

## Proyecto

**Atlas Peak** — app Android nativa (Kotlin + Compose), **local-first**, de gestión de
entrenamientos de fuerza y cardio. Sin backend propio. Datos en el dispositivo. Backup
cifrado opcional en Google Drive. Cero tracking.

---

## Lee esto antes de tocar nada (en orden)

1. **`CLAUDE.md`** — instrucciones operativas completas. **Es la fuente de verdad del "cómo".**
2. **`SPEC.md`** — especificación funcional y técnica (v2.2). Fuente de verdad del "qué".
3. **`DESIGN.md`** — sistema de diseño (vinculante para UI).
4. **`SECURITY.md`** — modelo de amenazas + hallazgos. Léelo antes de tocar auth/red/backup.
5. **`DOCS_TECNICA.md`** — arquitectura y contratos.

Antes de empezar una sesión: lee el último bloque de **`CHANGELOG.md`** para saber dónde se quedó.

---

## Skills locales

Los agentes pueden usar las skills locales en `C:\Proyectos\Atlas Peak Dev\Skills` cuando
encajen con la tarea. Antes de aplicar una skill, lee su `SKILL.md`, resuelve rutas relativas
desde la carpeta de esa skill y no copies sus reglas aquí.

---

## Reglas innegociables (resumen — detalle en `CLAUDE.md §1, §6, §7`)

1. **Una fase cada vez.** No empieces la fase N+1 sin cerrar la N (compila, tests verdes,
   registrada en `CHANGELOG.md`). El humano da las fases de una en una.
2. **Cero secretos en el repo.** Ni API keys, ni keystore, ni `secrets.properties`, ni
   `google-services.json`. Se usan via `secrets.properties` (gitignored). Si ves un secreto
   commiteado: incidente → `SECURITY.md` + avisa. **No uses `google-services.json`.**
3. **Arquitectura hacia adentro:** `presentation → domain → data`. `presentation` nunca
   importa Room entities ni clases Retrofit; trabaja con domain models.
4. **i18n desde el primer string.** Cero texto hardcodeado en UI. Todo en `strings.xml` (ES) +
   `values-en/` (EN).
5. **Migraciones Room explícitas.** `fallbackToDestructiveMigration()` prohibido fuera de tests.
   Cada cambio de schema = `Migration` + bump de versión + test de migración.
6. **Seguridad permanente** (no solo en la fase de hardening): sin contraseña para entrar
   en la app (SEC-025); PBKDF2 **600.000** iter para backups cifrados; `FLAG_SECURE` en
   salud/entrenamiento/Perfil/Backup; solo HTTPS;
   sin `ACCESS_BACKGROUND_LOCATION`; el backup cifrado **debe** llevar el salt en su cabecera
   (formato en `SECURITY.md` SEC-001); logging de OkHttp solo en `BuildConfig.DEBUG`.
7. **Las versiones de dependencias viven SOLO en `gradle/libs.versions.toml`.** No hardcodear
   versiones en `build.gradle.kts`.
8. **Registra todo.** Cada sesión actualiza `CHANGELOG.md`. Bugs → `BUGS.md`. Seguridad →
   `SECURITY.md`. Un bug no se cierra sin causa raíz + prevención.
9. **Si dudas, pregunta.** Una pregunta concreta cada vez. No inventes requisitos.

---

## Stack (resumen)

Kotlin · Jetpack Compose + Material 3 · minSdk 31 / target 35 · JDK 17 · Room + SQLCipher ·
Hilt · ViewModel + StateFlow · Vico (charts) · Retrofit/OkHttp (solo Drive REST v3) ·
Kotlinx Serialization · WorkManager · Health Connect · FusedLocation + Maps Compose ·
JUnit5 + MockK + Turbine. **Wear OS diferido a v2.** Detalle en `SPEC.md §3`.

---

## Comandos

```bash
cp secrets.properties.template secrets.properties   # primera vez; rellenar valores
./gradlew assembleDebug          # build
./gradlew test                   # unit tests (JUnit5)
./gradlew connectedAndroidTest   # tests instrumentados (emulador/dispositivo)
./gradlew lint                   # análisis estático (vigila la regla HardcodedText)
```

---

## Definición de "hecho" (resumen — completo en `CLAUDE.md §4`)

Compila debug+release · `test` y `lint` verdes · sin strings hardcodeados nuevos · migración
+ test si tocó schema · `SECURITY.md` actualizado si tocó seguridad/permisos/red ·
`CHANGELOG.md` actualizado · docs (técnica/usuario) actualizadas si cambió lo relevante.

---

## Relación entre archivos de reglas (para no liarla)

- **`AGENTS.md`** (este) = contrato universal, corto, para cualquier agente.
- **`CLAUDE.md`** = playbook detallado de Claude Code (fases, convenciones, DoD ampliada).
- **No copies contenido entre ambos.** Si una regla cambia, cámbiala en su sitio:
  guardrails de alto nivel aquí; detalle operativo en `CLAUDE.md`; el "qué" en `SPEC.md`.

---

*Atlas Peak — AGENTS.md — alineado con CLAUDE.md y SPEC.md v2.2*
