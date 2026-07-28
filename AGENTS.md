# AGENTS.md — Instrucciones maestras de Atlas Peak

> **Fuente única de verdad operativa del proyecto.** La leen todos los agentes: Claude Code,
> Codex y OpenCode. `CLAUDE.md` no contiene reglas propias: solo redirige aquí.
>
> Léelo entero antes de tocar una línea. Si algo aquí contradice un mensaje puntual del
> usuario, **gana el mensaje del usuario**, pero avísale del conflicto.
>
> `AGENTS.md` define **cómo** se construye. `SPEC.md` define **qué** se construye.
> `DESIGN.md` define **cómo se ve**. No dupliques contenido entre ellos: si una regla cambia,
> cámbiala en su sitio.

---

## 0. Qué es Atlas Peak (en una frase)

App Android nativa (Kotlin + Compose), **local-first**, de gestión de entrenamientos de fuerza
y cardio, con Health Connect, backup cifrado opcional en Google Drive y **cero tracking**.
Sin backend propio. Todos los datos viven en el dispositivo.

---

## 1. Mapa de documentos (léelos en este orden)

| Archivo | Para qué |
|---------|----------|
| **`AGENTS.md`** (este) | Cómo se trabaja: reglas, fases, convenciones, DoD. **Empieza aquí.** |
| **`SPEC.md`** | Especificación funcional y técnica (v2.2). El "qué". Plan de fases detallado en §9. |
| **`DESIGN.md`** | Sistema de diseño "Monochrome Instrument". **Vinculante para toda UI.** |
| **`SECURITY.md`** | Modelo de amenazas + log de hallazgos (SEC-xxx). Léelo antes de tocar auth/red/backup. |
| **`DOCS_TECNICA.md`** | Arquitectura y contratos internos. |
| **`DOCS_USUARIO.md`** | Guía para el usuario final. |
| **`BUGS.md`** | Log de bugs (BUG-xxx), causa raíz y prevención. |
| **`CHANGELOG.md`** | Trazabilidad de todo. **Lee el último bloque al empezar cada sesión.** |
| **`REVIEW_REPORT.md`** | Hallazgos de las revisiones integrales. |

---

## 2. Cómo trabajar — los cuatro principios

Estos principios están por encima de la velocidad. Sesgan hacia **cautela sobre rapidez**;
para tareas triviales (un typo, un one-liner obvio) aplica sentido común, no el ritual completo.

### 2.1 Piensa antes de programar

**No asumas. No escondas la confusión. Saca los trade-offs a la luz.**

- Declara tus suposiciones de forma explícita. Si dudas, **pregunta** — una pregunta concreta
  cada vez, no una batería.
- Si hay varias interpretaciones posibles, preséntalas. No elijas en silencio.
- Si existe un enfoque más simple, dilo. Discrepa cuando toque.
- Si algo no está claro, **para**. Nombra qué te confunde y pregunta.
- No inventes requisitos. Lo que no está en `SPEC.md` ni lo pidió el usuario, no existe.

### 2.2 Simplicidad primero

**El mínimo código que resuelve el problema. Nada especulativo.**

- Ninguna funcionalidad más allá de lo pedido.
- Ninguna abstracción para código de un solo uso (nada de `Strategy` para un cálculo único).
- Ninguna "flexibilidad" o "configurabilidad" que nadie pidió.
- Ningún manejo de errores para escenarios imposibles.
- Si escribes 200 líneas y podrían ser 50, reescríbelo.

**El test:** ¿un ingeniero senior diría que esto está sobreingenierizado? Si sí, simplifica.
El problema de la complejidad prematura no es que esté "mal escrita" — es el **momento**:
añade patrones antes de necesitarlos, y eso cuesta comprensión, bugs y tiempo.
Se refactoriza cuando la complejidad aparece de verdad, no antes.

### 2.3 Cambios quirúrgicos

**Toca solo lo imprescindible. Limpia solo tu propio desorden.**

Al editar código existente:
- No "mejores" código, comentarios ni formato adyacentes.
- No refactorices lo que no está roto.
- Respeta el estilo existente aunque tú lo harías de otra forma (comillas, ausencia de type
  hints, patrones de retorno, espaciado).
- Si ves código muerto no relacionado, **menciónalo — no lo borres**.

Cuando tus cambios dejan huérfanos:
- Elimina imports/variables/funciones que **tus** cambios dejaron sin uso.
- No elimines código muerto preexistente salvo que te lo pidan.

**El test:** cada línea cambiada debe poder trazarse directamente a lo que pidió el usuario.

### 2.4 Ejecución orientada a objetivos

**Define criterios de éxito. Itera hasta verificarlos.**

Convierte tareas imperativas en objetivos verificables:

| En vez de… | Conviértelo en… |
|-----------|-----------------|
| "Añade validación" | "Escribe tests para entradas inválidas y haz que pasen" |
| "Arregla el bug" | "Escribe un test que lo reproduzca y haz que pase" |
| "Refactoriza X" | "Asegura que los tests pasan antes y después" |

Para tareas multi-paso, enuncia un plan breve:

```
1. [Paso] → verifica: [comprobación]
2. [Paso] → verifica: [comprobación]
3. [Paso] → verifica: [comprobación]
```

Criterios fuertes te dejan iterar solo. Criterios débiles ("haz que funcione") obligan a
preguntar constantemente. Cada paso debe ser verificable e integrable por separado.

### 2.5 Anti-patrones (resumen)

| Principio | Anti-patrón típico | Corrección |
|-----------|-------------------|-----------|
| Piensa antes | Asume en silencio el alcance, los campos o el formato | Lista las suposiciones y pregunta |
| Simplicidad | Jerarquía de interfaces para un solo caso de uso | Una función hasta que la complejidad exista de verdad |
| Quirúrgico | Reformatea y añade docs mientras arregla un bug | Cambia solo las líneas que arreglan el fallo reportado |
| Objetivos | "Reviso y mejoro el código" | "Test que reproduce X → hacerlo pasar → sin regresiones" |

**Señales de que esto funciona:** diffs sin cambios innecesarios, menos reescrituras por
sobreingeniería, y las preguntas de aclaración llegan **antes** de implementar, no después.

---

## 3. Reglas de oro innegociables

1. **Una fase / una tarea cada vez.** No abras la siguiente hasta cerrar la actual (compila,
   tests verdes, registrada en `CHANGELOG.md`). El usuario da el trabajo de uno en uno.
2. **Cero secretos en el repo. Jamás.** Ni API keys, ni keystore, ni `secrets.properties`, ni
   `google-services.json`. Si vas a escribir un secreto, párate y usa el mecanismo de §8.
   Si detectas un secreto commiteado es un **incidente**: regístralo en `SECURITY.md` y avisa.
3. **Arquitectura limpia, dependencias hacia adentro:** `presentation → domain → data`.
   `presentation` **nunca** importa entities de Room ni clases de Retrofit; trabaja con
   domain models.
4. **i18n desde el primer string.** Cero texto hardcodeado en UI. Todo en `strings.xml` (ES) y
   `values-en/strings.xml` (EN). `Text("Guardar")` está mal;
   `Text(stringResource(R.string.action_save))` está bien.
5. **Migraciones Room explícitas.** `fallbackToDestructiveMigration()` **PROHIBIDO** fuera de
   tests. Cada cambio de schema = `Migration` escrita a mano + bump de versión + test de
   migración.
6. **Las versiones de dependencias viven SOLO en `gradle/libs.versions.toml`.** Nada de
   versiones hardcodeadas en `build.gradle.kts`. Si subes una, deja constancia en `CHANGELOG.md`.
7. **La seguridad aplica en cada fase**, no solo en la de hardening. Checklist en §9.
8. **Registra todo.** Cada sesión actualiza `CHANGELOG.md`. Bugs → `BUGS.md`. Seguridad →
   `SECURITY.md`. **Un bug no se cierra sin causa raíz + prevención.**
9. **Si dudas, pregunta.** Una pregunta concreta cada vez.

---

## 4. Estado del proyecto y plan de fases

El plan original de v1 (numeración de `SPEC.md §9`) está **cerrado hasta la fase 16**. El
trabajo actual es iterativo: versiones `V-01.0x`, corrección de bugs, auditorías y UX.
Consulta siempre el último bloque de `CHANGELOG.md` para el estado real.

| Fase | Contenido | Estado |
|------|-----------|--------|
| 0 | Bootstrap: proyecto compila, CI verde, docs creados | ✅ |
| 1 | Fundación + i18n + tema + DB + seed data | ✅ |
| 2 | Seguridad local + Google/backup opcional (sin contraseña de entrada) | ✅ |
| 3 | Onboarding | ✅ |
| 4 | Ejercicios y rutinas | ✅ |
| 5 | Entrenamiento activo (fuerza) + `WorkoutForegroundService` | ✅ |
| 6 | Cardio + GPS + `CardioForegroundService` | ✅ |
| 7 | Historial y progreso | ✅ |
| 8 | Dashboard | ✅ |
| 9 | Composición corporal | ✅ |
| 10 | Health Connect (import + export) | ✅ |
| 11 | Plan semanal + notificaciones | ✅ |
| 12 | Backup Drive + export | ✅ |
| 13 | ~~Wear OS~~ — **diferido a v2** (ver §13) | ⏸ |
| 14 | Seguridad + hardening | ✅ |
| 15 | Testing (≥70% cobertura domain+data, verificada en CI) | ✅ |
| 16 | Polish + performance + accesibilidad | ✅ |
| 17 | Play Store + privacy policy + release público | ⬜ abierta |
| (v2) | Wear OS | ⬜ aplazado |

Marca la casilla cuando una fase cierre. El detalle de tareas por fase está en `SPEC.md §9`.

---

## 5. Definición de "Hecho" (Definition of Done)

Ninguna fase, bug o cambio está hecho hasta que:

- [ ] Compila en debug **y** release (`./gradlew assembleDebug assembleRelease`).
- [ ] `./gradlew test` pasa y `./gradlew lint` no añade warnings nuevos.
- [ ] Cero strings hardcodeados nuevos (regla `HardcodedText` de lint).
- [ ] La cobertura de domain+data sigue en verde
      (`./gradlew jacocoDebugDomainDataCoverageVerification`, umbral 70%).
- [ ] Si tocó schema: hay `Migration` + test de migración.
- [ ] Si tocó seguridad/permisos/red: anotado en `SECURITY.md`.
- [ ] Si arregló un bug: entrada en `BUGS.md` con causa raíz **y** prevención.
- [ ] `CHANGELOG.md` actualizado con qué se hizo y qué se verificó.
- [ ] `DOCS_TECNICA.md` actualizado si cambió arquitectura/contratos.
- [ ] `DOCS_USUARIO.md` actualizado si cambió algo visible para el usuario.

---

## 6. Stack técnico (resumen — detalle en `SPEC.md §3`)

| Capa | Tecnología |
|------|-----------|
| Lenguaje | Kotlin |
| UI | Jetpack Compose + Material 3 (estilo: `DESIGN.md`) |
| Min / Target / Compile SDK | 31 / 35 / 36 |
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
| Testing | JUnit5 + fakes escritos a mano + Room in-memory + `kotlinx-coroutines-test` + JaCoCo |

**Wear OS diferido a v2** (ver §13). Sin librerías de mocking automático: los dobles de prueba
se escriben a mano.

---

## 7. Convenciones de código

- **Paquetes:** `com.atlaspeak.<capa>.<feature>` (p. ej. `com.atlaspeak.domain.usecase.workout`).
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
- **Lógica compartida:** si UI y persistencia necesitan el mismo cálculo, extrae un helper de
  dominio y úsalo en ambos sitios (evita divergencias como la de `BUG-088`).

---

## 8. Secretos (LEER ANTES DE TOCAR AUTH / MAPS / DRIVE)

Hay tres secretos de configuración. **Ninguno** va al repo.

| Secreto | Para qué | Dónde vive |
|---------|----------|-----------|
| `MAPS_API_KEY` | Google Maps Compose (cardio GPS) | `secrets.properties` (local) |
| `OAUTH_WEB_CLIENT_ID` | Drive OAuth (`AuthorizationClient`) | `secrets.properties` (local) |
| Release keystore + passwords | Firmar el AAB | Fuera del repo, via `ATLAS_PEAK_KEYSTORE_PROPERTIES` |

- Plantillas versionadas: **`secrets.properties.template`** y **`keystore.properties.template`**.
  Se copian y se rellenan en local.
- `secrets.properties`, `keystore.properties`, `*.jks`, `*.keystore`, `local.properties` y
  `google-services.json` están en `.gitignore`. **Verifica que siguen ahí** antes de cualquier
  commit que toque configuración.
- El keystore de release **no** vive en la raíz del repo. Usa, por ejemplo,
  `%USERPROFILE%\.atlaspeak\release\keystore.properties` y apúntalo con
  `ATLAS_PEAK_KEYSTORE_PROPERTIES`.
- En Gradle, lee `secrets.properties` y expón los valores via `manifestPlaceholders` (Maps key)
  y `BuildConfig` (OAuth client id). NO los escribas inline.
- **NO uses `google-services.json` ni el plugin `com.google.gms.google-services`.** Drive REST
  con `AuthorizationClient` no lo necesita. Si aparece, bórralo.
- En CI (`.github/workflows/ci.yml`) `secrets.properties` se genera con placeholders: el build
  compila sin valores reales. No introduzcas dependencias que rompan eso.

---

## 9. Seguridad — checklist permanente

Aplica en **cada** cambio, no solo en la fase de hardening:

- [ ] ¿Algún `Log.d/e/i` con datos sensibles (email, token, password, ubicación)? Fuera.
      El logging interceptor de OkHttp **solo** en `BuildConfig.DEBUG`.
- [ ] ¿Nueva llamada de red? Solo HTTPS, y pasa por `network_security_config.xml`.
- [ ] ¿Tocaste el backup? El archivo cifrado **debe** llevar cabecera
      `[versión(1B)][iter(4B)][salt(16B)][IV(12B)][ciphertext+tag]`. Sin salt en el archivo, la
      restauración en otro dispositivo es imposible (`SECURITY.md` SEC-001).
- [ ] PBKDF2-HMAC-SHA256 con **600.000** iteraciones (no 200k) para backups cifrados.
- [ ] ¿Cambió la passphrase de backup? Los backups previos requieren la antigua (SEC-002).
- [ ] No reintroduzcas contraseña ni biometría para entrar en la app sin decisión explícita de
      producto (SEC-025).
- [ ] Las capturas de pantalla siguen permitidas (SEC-026). No reintroduzcas `FLAG_SECURE` sin
      decisión explícita de producto.
- [ ] `allowBackup="false"` en el manifest (la DB cifrada no debe ir al backup de Android).
- [ ] No pidas `ACCESS_BACKGROUND_LOCATION` (no se usa y dispara rechazo de Play).
- [ ] Datos de usuario exportados a CSV: mantén la mitigación de formula injection.

### Higiene antimalware (entorno de desarrollo)

Este ordenador tiene antivirus/antimalware activo. Trabaja de forma limpia: comandos legibles,
herramientas estándar del proyecto, sin ofuscación. **Prohibido por defecto:** evasión,
ofuscación, ejecución desde `%TEMP%`, descargas de código remoto, AMSI bypass, encoded commands,
persistencia oculta, desactivar defensas y añadir exclusiones de antivirus como "solución".
Nada de escaneos agresivos de red, de tocar credenciales del sistema ni de descargar/ejecutar
binarios injustificados. Evita procesos ocultos salvo servidores o herramientas de desarrollo
claramente necesarios y explicados. **Si una defensa bloquea algo, para y avisa; no lo esquives.**

---

## 10. Comandos

```bash
cp secrets.properties.template secrets.properties   # primera vez; rellenar valores

./gradlew assembleDebug          # build debug
./gradlew assembleRelease        # build release (requiere ATLAS_PEAK_KEYSTORE_PROPERTIES o fallback local)
./gradlew test                   # unit tests JVM (JUnit5)
./gradlew lint                   # análisis estático; reporte en app/build/reports/lint
./gradlew jacocoDebugDomainDataCoverageVerification   # cobertura domain+data (falla bajo 70%)
./gradlew connectedAndroidTest   # tests instrumentados (requiere emulador/dispositivo)
./gradlew :app:dependencies      # árbol de dependencias (debug de conflictos)
gradle wrapper --gradle-version 8.x   # regenerar wrapper si falta el jar
```

En Windows usa `.\gradlew.bat <tarea>`. CI (`.github/workflows/ci.yml`) ejecuta, en este orden:
`assembleDebug` → `test` → `jacocoDebugDomainDataCoverageVerification` → `lint`.

---

## 11. Skills locales

La biblioteca de skills es **local y externa al repositorio**. Vive en la máquina de trabajo en
`C:\Proyectos\Atlas Peak Dev\Skills`. Úsalas cuando encajen con la tarea, empezando por su
`README.md` y por `00_START_HERE`.

- **Nunca las commitees.** `Skills/` está en `.gitignore` precisamente para eso. Si alguna vez
  vuelves a ver rutas `Skills/...` en `git status` o en `git ls-files`, sácalas del índice
  (`git rm -r --cached Skills`) antes de commitear: `.gitignore` no excluye lo que ya está
  trackeado, así que un `git add -A` descuidado las mete de nuevo.
- Abre **solo** la carpeta de la fase actual; meterlas todas en el prompt es ruido caro.
- Antes de aplicar una skill, lee su `SKILL.md` y resuelve las rutas relativas desde la carpeta
  de esa skill.
- **No copies las reglas de una skill aquí.** Si una skill contradice este documento, manda este
  documento.

---

## 12. Rutina de cada sesión

1. Lee el último bloque de `CHANGELOG.md` para saber dónde se quedó el trabajo.
2. Mira la tabla de fases (§4): ¿qué está abierto?
3. Confirma con el usuario la tarea concreta de hoy y sus criterios de éxito (§2.4).
4. Trabaja. Compila a menudo. No acumules cambios sin compilar.
5. Al cerrar: recorre la DoD (§5), actualiza `CHANGELOG.md`, marca casillas y registra
   bugs/seguridad si aplica.

---

## 13. Por qué Wear OS está aplazado (contexto de decisión)

El spec original metía Wear OS en v1 (4 semanas, ~11% del proyecto). Decisión revisada: **no
construir Wear hasta que el teléfono esté en producción y validado.** Razones: es la pieza de
mayor complejidad técnica (Data Layer API, módulo aparte, otro ciclo de testing) y menor valor
core. El protocolo de comunicación teléfono↔reloj ya está diseñado en `SPEC.md §2.10` para
cuando se retome. Para reactivarlo: añadir el módulo `wear` a `settings.gradle.kts` y seguir el
plan v2.

Si el usuario decide hacer Wear antes, no pasa nada: el diseño está listo, solo cambia el orden.

---

*Atlas Peak — AGENTS.md — instrucciones únicas para Claude Code, Codex y OpenCode.
Alineado con SPEC.md v2.2.*
