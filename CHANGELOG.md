# CHANGELOG.md — Registro de cambios

> Trazabilidad de todo lo que se hace en el proyecto. Cada sesión de trabajo añade una
> entrada. Formato basado en [Keep a Changelog](https://keepachangelog.com/es/).
> Orden: más reciente arriba.
>
> Tipos: `Añadido` · `Cambiado` · `Corregido` · `Eliminado` · `Seguridad` · `Deprecado`

---

## [Unreleased]

### 2026-06-23 - Redisenio completo Monochrome Instrument

**Anadido**
- Primitivas compartidas `AtlasChip`, `AtlasListRow`, `AtlasSwitchRow`, `AtlasDropdown`,
  `AtlasDialog`, `AtlasBottomSheet` y soporte de icono/password en `AtlasTextField`.
- Ruta real `EditProfile` enlazada desde Perfil, con bottom nav visible y Perfil seleccionado
  en la subpantalla.
- String i18n `weekly_plan_type_label` en ES/EN para dropdown de tipo de sesion.

**Cambiado**
- Entrenar, entrenamiento activo, cardio activo/completado, Progreso, Composicion corporal,
  Perfil, editar perfil, onboarding, plan semanal, ajustes y backup se adaptan al sistema
  monocromo: chips, campos, dropdowns, dialogs, sheets, filas y estados usan tokens Atlas.
- Bottom navigation mantiene estado por tab con `saveState=true` y `restoreState=true`;
  la documentacion tecnica refleja esa politica.
- Backup conserva password oculto usando `AtlasTextField` con `PasswordVisualTransformation`.
- `DOCS_USUARIO.md` documenta la edicion de perfil desde Perfil.

**Corregido**
- `EditProfileScreen` deja de ser pantalla huerfana y queda cubierta por navegacion/politicas.
- Tests estaticos de bottom nav se alinean con la navegacion real y cubren subrutas de Perfil.

**Verificado**
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat --% :app:compileDebugKotlin --no-daemon --no-configuration-cache --console=plain` pasa.
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat --% :app:testDebugUnitTest --no-daemon --no-configuration-cache --console=plain` pasa.
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat --% :app:lintDebug --no-daemon --no-configuration-cache --console=plain` pasa.
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat --% :app:assembleDebug --no-daemon --no-configuration-cache --console=plain` pasa.
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat --% :app:assembleRelease --no-daemon --no-configuration-cache --console=plain` pasa.
- `connectedAndroidTest` no ejecutado: `adb devices` no lista emuladores ni moviles conectados.

### 2026-06-23 - Plan por defecto de entrenamiento 5 dias

**Anadido**
- Instalacion limpia con plan semanal por defecto: fuerza lunes/martes/jueves/viernes,
  cardio miercoles en bici estatica 45 min y descanso sabado/domingo.
- Cuatro rutinas seed de fuerza (`Tren inferior/superior A/B`) con ejercicios, series,
  descansos y notas para rangos, segundos por plancha y superseries.
- Plan semanal v4 soporta dias de fuerza, cardio o descanso sin disfrazar cardio como rutina.

**Cambiado**
- Home inicia la sesion planificada de hoy: `ActiveWorkout` para fuerza y `ActiveCardio`
  countdown para cardio.
- `routine_exercises.notes` llega al dominio y se muestra en detalle de rutina y entrenamiento activo.
- Backup schema sube a v3 para restaurar backups antiguos tras las nuevas columnas de `weekly_plan`.
- `gradle/verification-metadata.xml` incorpora hashes faltantes de BOMs/parents usados por tests/lint.

**Verificado**
- `.\gradlew.bat --% :app:testDebugUnitTest --tests com.atlaspeak.data.SeedDataTest --tests com.atlaspeak.domain.usecase.planning.WeeklyPlanUseCaseTest --tests com.atlaspeak.data.backup.BackupSnapshotUpgraderTest --no-daemon --no-configuration-cache --console=plain` pasa.
- `.\gradlew.bat --% :app:assembleDebug :app:lintDebug --no-daemon --no-configuration-cache --console=plain` pasa.
- `.\gradlew.bat --% :app:compileDebugAndroidTestKotlin --no-daemon --no-configuration-cache --console=plain` pasa.
- `connectedAndroidTest` no ejecutado: `adb devices` no lista emuladores ni moviles conectados.

### 2026-06-22 - Higiene Git de Skills y remoto GitHub

**Corregido**
- `Skills/` queda fuera del indice de Git: se conserva como carpeta local de agente, pero
  no se subira al repositorio.
- Verificado que `.gitignore` mantiene `Skills/` y que `git ls-files Skills` devuelve
  cero rutas versionadas.
- Verificado que el push de `origin` sigue bloqueado con `DISABLED_WRONG_OWNER` para evitar
  subidas accidentales al owner de GitHub incorrecto.

**Verificado**
- `git remote show origin` confirma fetch en `https://github.com/AtlasLabs797/AtlasPeak.git`
  y push desactivado con `DISABLED_WRONG_OWNER`.
- `Test-Path Skills` devuelve `True`.
- `git ls-files Skills | Measure-Object` devuelve `Count: 0`.

### 2026-06-15 - Logo launcher desde marca final

**Cambiado**
- Reemplazado el foreground del launcher por un PNG generado desde `Logo Atlas Peak.png`,
  recortado y centrado para adaptive icons sin alterar la silueta de la marca.
- Aumentado el zoom del launcher para eliminar el borde blanco visible en el icono de app.
- El fondo del adaptive icon pasa a blanco para respetar el aspecto negro/blanco del logo
  entregado.
- Reemplazado `ic_launcher_monochrome` por una version monocroma derivada del mismo logo
  para launcher tematico y notificaciones.

**Verificado**
- Preview local del icono compuesto en blanco revisado visualmente.
- `.\gradlew.bat assembleDebug` no pudo ejecutarse: `JAVA_HOME` apunta a
  `C:\tmp\atlas-dev-tools\jdk-17.0.19+10`, que no existe, y `java.exe` no esta en `PATH`.

### 2026-06-15 - Rediseño UI/UX monocromo Atlas Peak

**Añadido**
- Tipografias bundladas `Space Grotesk` y `JetBrains Mono` para UI, titulares y cifras
  tabulares.
- Componentes de graficas monocromas Compose (`MonochromeSparkline`,
  `MonochromeAreaChart`, `MonochromeBarChart`) para Home y Progreso.

**Cambiado**
- Sistema visual reemplazado por la direccion "instrumento OLED": paleta monocroma,
  tarjetas de bajo contraste, radios compactos, botones blancos/negros y bottom nav flotante.
- Home rediseñado al flujo editorial del mockup: saludo, carga semanal gigante,
  carrusel de metricas, sesion de hoy y metricas secundarias.
- Progreso rediseñado con cabecera editorial, selector segmentado, volumen mensual,
  fuerza/record y barras de carga semanal.
- Entrenamiento activo rediseñado como tabla de telemetria: cronometro en vivo,
  progreso/volumen, filas kg/reps editables, check de set y descanso inline.
- Flujos restantes heredan los nuevos tokens; los chips de color de rutinas mantienen
  compatibilidad de dato pero ya no pintan acentos rojo/verde/azul/morado.
- Tests de politica UI actualizados a las nuevas fuentes y contraste del tema.

**Verificado**
- `gradle.bat :app:compileDebugKotlin --no-daemon --console=plain` pasa.
- `gradle.bat :app:testDebugUnitTest --tests com.atlaspeak.presentation.StaticUiPolicyTest --tests com.atlaspeak.presentation.theme.ThemeAccessibilityTest --no-daemon --console=plain` pasa.
- `gradle.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon --console=plain` pasa.
- `connectedAndroidTest` no ejecutado: `adb devices` no lista emuladores ni moviles conectados.

### 2026-06-11 - Crash al cambiar periodos de Progreso

**Corregido**
- Registrado y resuelto `BUG-060`: `ProgressViewModel` cancela refreshes obsoletos,
  descarta resultados de filtros anteriores y convierte fallos de carga en error UI
  en vez de dejar que una excepcion cierre la app.
- Los graficos de Progreso, Home y Composicion corporal filtran puntos `NaN`/`Infinity`
  antes de pasarlos a Vico.

**Verificado**
- `.\gradlew.bat --% :app:testDebugUnitTest --tests com.atlaspeak.presentation.progress.ProgressViewModelTest --no-daemon --console=plain` pasa.
- `.\gradlew.bat --% :app:testDebugUnitTest --tests com.atlaspeak.domain.usecase.progress.ProgressUseCaseTest :app:assembleDebug :app:lintDebug --no-daemon --console=plain` pasa.
- `.\gradlew.bat --% :app:assembleDebug :app:lintDebug --no-daemon --console=plain` pasa tras los ajustes finales del ViewModel.
- QA en dispositivo no ejecutada: `adb devices` no lista moviles/emuladores conectados.

### 2026-06-10 - Auditoria pre-publicacion: bugs criticos, flujos y UX premium

**Corregido**
- `BUG-045`..`BUG-059` (ver BUGS.md): crash potencial al finalizar entrenamiento sin
  servicio en foreground; entrenamiento activo sin salida (BackHandler + dialogo
  descartar); cancelacion de cardio sin confirmacion; campos peso/reps inutilizables
  (borradores de texto crudo por set); perdida silenciosa de sets con ejercicios
  duplicados en rutina (numeracion continua); restart STICKY sin startForeground
  (`START_NOT_STICKY`); sesion duplicada tras muerte de proceso (`SavedStateHandle`);
  bucle de navegacion al completar desde Home; notificaciones de servicio sin
  `contentIntent`; perdida de estado al cambiar de tab (saveState/restoreState);
  cardio GPS con distancia 0 imposible de guardar; backups schema v1 irrestaurables
  (`BackupSnapshotUpgrader`); restore sin reprogramar notificaciones; errores pintados
  en color primary; race en refresh de composicion corporal.

**Anadido**
- Pantalla de edicion de perfil (`EditProfileScreen`): nombre, edad, altura, genero
  (con "Otro" y "Prefiero no decir") y objetivo, editables tras el onboarding.
  Genero/objetivo se persisten como claves estables (no texto localizado), con
  mapeo best-effort de valores legacy.
- Toggles de sonido/vibracion del temporizador de descanso en ajustes.
- Navegacion atras entre pasos del onboarding.
- Saludo personalizado en Home; fechas en el historial de Entrenar; tamano de
  archivo en la lista de backups de Drive y aviso SEC-002 al cambiar la passphrase.

**Cambiado**
- Calorias de cardio usan el ultimo peso corporal registrado (fallback 75 kg).
- Duraciones legibles ("1 h 24 min") en resumenes, historial y progreso; los
  `formatElapsed` duplicados consolidados en `core/time/ElapsedClock`.
- Pulido premium: anillo de descanso animado con cuenta atras destacada, haptica al
  completar set, contraste del heroe de resumen en tema claro, targets tactiles de
  48dp en `PeriodSelector`, umbrales de drag en dp, imagen decorativa sin
  `contentDescription`.
- Ortografia espanola corregida en todo `values/strings.xml` (~40 tildes/enes).

**Verificado**
- `.\gradlew.bat --% :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon --console=plain` pasa.

### 2026-06-08 - Mitigacion de formula injection en CSV

**Seguridad**
- Registrado y resuelto `SEC-029` / `BUG-044`: los exports CSV neutralizan celdas de texto
  que podrian ser interpretadas como formulas por hojas de calculo.

**Corregido**
- `BackupExportFormatter` antepone apostrofe a valores textuales cuyo primer caracter
  significativo sea `=`, `+`, `-` o `@`; los primitivos JSON numericos no se convierten
  en texto.

**Verificado**
- `.\gradlew.bat --% :app:testDebugUnitTest --tests com.atlaspeak.data.backup.BackupExportFormatterTest --no-daemon --console=plain -Pkotlin.incremental=false -Dkotlin.compiler.execution.strategy=in-process` pasa.
- `.\gradlew.bat --% :app:testDebugUnitTest --no-daemon --console=plain -Pkotlin.incremental=false -Dkotlin.compiler.execution.strategy=in-process` pasa.
- `.\gradlew.bat --% :app:lintDebug --no-daemon --console=plain -Pkotlin.incremental=false -Dkotlin.compiler.execution.strategy=in-process` pasa.

## [V-01.07] - 2026-06-08

### Release V-01.07

#### 2026-06-08 - Bump de version

**Cambiado**
- Fijada la version de app en `versionName = "V-01.07"` y `versionCode = 107`.

## [V-01.06] - 2026-05-31

### Release V-01.06

#### 2026-05-31 - Bump de version

**Cambiado**
- Fijada la version de app en `versionName = "V-01.06"` y `versionCode = 106`.

**Anadido**
- Copia de distribucion local en `build/distribution/AtlasPeak-V-01.06-release.apk`.
- `build/distribution/install-adb.bat`, `README-INSTALACION.txt` y `SHA256SUMS.txt`
  regenerados para V-01.06.

**Verificado**
- `.\gradlew.bat clean :app:packageReleaseUpdate --no-daemon --console=plain --no-build-cache --no-configuration-cache` pasa.
- `build/distribution/README-INSTALACION.txt` confirma `versionName V-01.06` y
  `versionCode 106`.
- `aapt2 dump badging` confirma `package='com.atlaspeak'`, `versionCode='106'`,
  `versionName='V-01.06'`, `minSdkVersion='31'` y `targetSdkVersion='35'`.
- SHA-256: `EA0CA6DACD367D09C689B3BF3C385419981786DFD06CE308C86BEE426C51C6D2`.

#### 2026-05-30 - Auditoria seguridad/bugs y hardening de supply chain

**Seguridad**
- Registrado y resuelto `SEC-027`: restore de backup ahora rechaza filas con columnas faltantes, JSON no primitivo, `NULL` en columnas requeridas o tipos incompatibles con la afinidad SQLite antes de escribir.
- Registrado y resuelto `SEC-028`: CI con `permissions: contents: read`, Actions pinneadas por commit SHA, Gradle Wrapper con `distributionSha256Sum` y dependencias verificadas por `gradle/verification-metadata.xml`.
- Scan de secretos sobre `app/`: 204 archivos revisados, 0 secretos potenciales. Los archivos locales `secrets.properties`, `keystore.properties`, `local.properties` y `atlas-peak-release.jks` siguen ignorados y no trackeados.

**Corregido**
- Registrado y resuelto `BUG-042`: `WorkoutForegroundService` conserva el estado `failed` si Android rechaza el foreground service, para que la UI pueda avisar que el cronometro persistente no arranco.
- Registrado y resuelto `BUG-043`: restore de backup ya no delega la validacion de valores malformados a SQLite/Room.

**Verificado**
- `./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon --console=plain` pasa con dependency verification activa.
- `./gradlew.bat :app:assembleRelease --no-daemon --console=plain` pasa.
- `./gradlew.bat :app:jacocoDebugDomainDataCoverageVerification --no-daemon --console=plain` pasa.
- `./gradlew.bat :app:compileDebugAndroidTestKotlin --no-daemon --console=plain` pasa.
- `./gradlew.bat :app:connectedDebugAndroidTest --no-daemon --console=plain` no se pudo ejecutar: no habia dispositivo/emulador conectado (`No connected devices!`).

#### 2026-05-26 - Correcciones UI/UX desde capturas de pantalla

**AÃ±adido**
- Home muestra una card de "Entrenamiento de hoy" cuando el plan semanal tiene rutina para el dia actual, con CTA directo para iniciar la sesion.
- `CardioType` expone `iconName` y la UI de cardio resuelve iconos distintos para correr, bici, cinta, estatica, eliptica, remo y natacion.
- Selectores cerrados en onboarding para genero y objetivo, sin teclado para esos campos.

**Cambiado**
- `PeriodSelector` pasa a una version compacta y discreta, sin ocupar todo el ancho de cada card.
- `PremiumBackground` deja de usar manchas radiales fijas y usa un fondo vertical mas controlado.
- `WeeklyMinutesCard` abandona el bloque verde dominante y muestra metrica + barras mini de actividad.
- `ConsistencyCard` anade barras visuales compactas para leer progreso sin depender solo del texto.
- Bottom navigation redisenada como barra propia icon-only: mas compacta, con tab activo claro y Entrenar con jerarquia visual.
- Entrenar abre por defecto en `Rutinas` y coloca el CTA de inicio antes del constructor de rutinas.
- Launcher icon actualizado de rojo generico a marca dark/lima alineada con el sistema visual actual.

**Corregido**
- Registrado y resuelto `BUG-038`: entrenamiento activo avanza al siguiente ejercicio al completar sets, cambia el CTA a Siguiente/Completa sets/Finalizar segun estado y dispara sonido/vibracion al terminar el descanso.
- Registrado y resuelto `BUG-039`: genero y objetivo ya no son texto libre en onboarding.
- Registrado y resuelto `BUG-040`: los iconos de cardio ya no son todos iguales.
- Registrado y resuelto `BUG-041`: Home y Entrenar priorizan iniciar la rutina planificada/seleccionada.

**Verificado**
- `./gradlew.bat :app:compileDebugKotlin --no-daemon --console=plain` pasa.
- `./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon --console=plain` pasa.

#### 2026-05-26 - UI/UX premium refinement (Fundación + Onboarding + Entreno + Home + Resto)

**Añadido**
- Tokens fundacionales: `theme/Motion.kt` (durations + easings), `theme/Elevation.kt`,
  `theme/Brushes.kt` (heroGradient, subtleSurface, spotlight, accentBorder).
- Componentes premium reutilizables en `presentation/component/`:
  - `AtlasPrimaryButton`, `AtlasSecondaryButton`, `AtlasGhostButton` — altura mínima
    56dp para primaria, shape 16dp consistente, content padding fijo. Aceptan
    leadingIcon + iconContentDescription.
  - `AtlasTextField` — wrapper de `OutlinedTextField` con shape large, colors
    Atlas-consistentes y altura mínima 60dp.
  - `MetricValue` — número grande tabular + unidad opcional, con variante
    `emphasized`.
  - `SectionHeader` — overline opcional + headline + trailing slot.
  - `EmptyState` — icon badge + título + body + CTA opcional centrados.
  - `AtlasSlider` — label izquierda + valor en color primary a la derecha + track
    coloreado con primary.
  - `StepDots` — indicador animado de pasos (width + color animan) para onboarding.
- Recursos: `drawable/ic_onboarding_hero.xml` (composición geométrica con anillos
  concéntricos y ejes, decorativa).
- Strings nuevos (ES + EN paridad): `onboarding_step_label`,
  `onboarding_hero_decoration_cd`, `home_greeting_overline`, `home_greeting_title`,
  `home_hero_unit_min`.

**Cambiado**
- `PremiumIconBadge` acepta parámetro `size` (default 44.dp).
- `PeriodSelector` reescrito como segmented pill con track redondeado, transición
  animada de color cuando cambia la selección.
- `OnboardingScreen` rediseñado: hero con gradiente + icon flotante, `StepDots` en
  lugar de `LinearProgressIndicator`, overline "Paso X de Y", `AnimatedContent` con
  fade-through entre pasos, `PremiumCard` envolviendo inputs, botones via
  `AtlasPrimaryButton` / `AtlasGhostButton`.
- `HomeScreen`: greeting con `SectionHeader` ("Hoy / Tu progreso"), `WeeklyMinutesCard`
  con `AtlasBrushes.heroGradient`, número en `displayMedium` + unidad pequeña, badge
  redondo. `MetricCard` y `ChartMetricCard` usan `MetricValue` para los headlines.
- `ActiveWorkoutScreen`: `ProgressCard` usa `PremiumCard` y cronómetro en
  `headlineSmall` color primary. `SetRow` sustituye `Card` por `Surface` con borde
  primary cuando completed (feedback visual), peso (`weight 1.4f`) > reps (`weight
  1f`). `RestTimerOverlay` con anillo 208dp / stroke 12dp + track gris + número en
  `displayMedium`. Botones via Atlas*.
- `WorkoutCompleteScreen`: hero header con gradiente + icon CheckCircle, contenido
  en `PremiumCard`, CTA con `AtlasPrimaryButton`.
- Resto de pantallas migradas al sistema:
  `BackupRestoreScreen`, `BodyCompositionScreen`, `WeeklyPlanScreen`,
  `NotificationSettingsScreen`, `TrainScreen`, `ActiveCardioScreen`,
  `CardioCompleteScreen`, `ProgressScreen` — `Card`/`ElevatedCard` → `PremiumCard`
  en contenedores no clickables y CTAs prominentes → `AtlasPrimaryButton` /
  `AtlasSecondaryButton`.

**Verificado**
- `./gradlew.bat :app:assembleDebug :app:lintDebug :app:testDebugUnitTest --no-daemon --console=plain` pasa.
- `StaticUiPolicyTest` sigue verde: cero strings hardcodeados visibles, paridad ES/EN,
  fonts Poppins/Inter, Home con `PremiumCard(modifier = modifier.fillMaxWidth())` y
  un card por fila.

#### 2026-05-26 - Auditoria adicional: teclado y chips fantasma

**Corregido**
- Registrado y resuelto `BUG-036`: el teclado virtual tapaba los `TextField` en
  onboarding, backup, ajustes, plan semanal y entrenamiento. `MainActivity` corre en
  `enableEdgeToEdge()` y el `Scaffold` de Material 3 no incluye `WindowInsets.ime` en
  `contentWindowInsets` por defecto, asi que sin `imePadding()` el contenido se queda
  debajo del teclado.
- `AtlasPeakApp.kt` ahora aplica `.fillMaxSize().padding(innerPadding).imePadding()`
  al `Box` que envuelve el `NavHost`. Un unico cambio cubre todas las pantallas con
  input.
- Registrado y resuelto `BUG-037`: el `AssistChip` de etiqueta Health Connect / Manual
  en `BodyCompositionScreen.kt` tenia `onClick = {}` y daba ripple sin hacer nada. Se
  marca `enabled = false` para que se vea como badge informativo.

**Verificado**
- `./gradlew.bat :app:assembleDebug :app:testDebugUnitTest --no-daemon --console=plain` pasa.

#### 2026-05-26 - Fix definitivo de interaccion del menu inferior

**Corregido**
- Registrado y resuelto `BUG-035`: los taps del menu inferior se perdian aunque la
  logica de navegacion (BUG-032/033/034) ya estaba bien. La causa real era de layout:
  el `NavigationBar` Material 3 aplicaba sus `windowInsets` por defecto (system nav
  bar) ademas del `navigationBarsPadding()` del `Box` envolvente, y la altura forzada
  a 64dp dejaba los items por debajo de la barra del sistema, fuera del area
  interactiva.
- `AtlasPeakApp.kt` deja al `NavigationBar` con su altura por defecto (80dp) y
  declara `windowInsets = WindowInsets(0, 0, 0, 0)` para que el unico responsable de
  los insets sea el contenedor exterior. Resultado: los cinco tabs reciben taps y
  cambian de pantalla de forma fiable en cualquier dispositivo con system nav bar.

**Verificado**
- `./gradlew.bat :app:testDebugUnitTest --tests com.atlaspeak.presentation.navigation.BottomNavigationPolicyTest --no-daemon --console=plain` pasa.
- `./gradlew.bat :app:assembleDebug --no-daemon --console=plain` pasa.

## [V-01.05] - 2026-05-25

### Release V-01.05

#### 2026-05-25 - Arreglo definitivo de menu inferior

**Corregido**
- Registrado y resuelto `BUG-034`: el menu inferior deja de depender de `Home` como raiz
  global y de `saveState/restoreState`, que podian restaurar stacks inestables.
- `AtlasPeakNavHost` introduce `AppRoute.AppGraph` como grafo autenticado estable con
  `Home` como start destination.
- `AtlasPeakApp` navega tabs con `popUpTo(AppGraph)`, `launchSingleTop`, `saveState = false`
  y `restoreState = false`, para que cada tab abra siempre su ruta raiz.

**Documentado**
- `DOCS_TECNICA.md` actualiza el contrato de entrada a la app y navegacion autenticada.

**Verificado**
- `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --tests com.atlaspeak.presentation.navigation.BottomNavigationPolicyTest --no-daemon --console=plain` pasa.
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon --console=plain` pasa.
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat :app:packageReleaseUpdate --no-daemon --console=plain --no-build-cache --no-configuration-cache` pasa y regenera `build/distribution/AtlasPeak-V-01.05-release.apk`.
- `aapt2 dump badging` confirma `package='com.atlaspeak'`, `versionCode='105'`,
  `versionName='V-01.05'`, `minSdkVersion='31'` y `targetSdkVersion='35'`.
- `apksigner verify --verbose --print-certs` confirma firma APK Signature Scheme v2 con
  certificado `CN=Atlas Peak`.
- SHA-256 vigente: `F47D61AE4DEBF34A7F7E2D1254BE165771E8518B5C5EB14A245F3D3DF169A2F9`.
- QA runtime en AVD bloqueada: `AtlasPeak_Clean_API35` llega a `adb state=device`, pero no
  completa `sys.boot_completed` antes de que el proceso del emulador quede inutilizable.

#### 2026-05-25 - Bump de version

**Cambiado**
- Fijada la version de app en `versionName = "V-01.05"` y `versionCode = 105`.

**Anadido**
- Copia de distribucion en `build/distribution/AtlasPeak-V-01.05-release.apk`.
- `build/distribution/install-adb.bat`, `README-INSTALACION.txt` y `SHA256SUMS.txt`
  regenerados para V-01.05.

**Verificado**
- `.\gradlew.bat :app:packageReleaseUpdate --no-daemon --console=plain --no-build-cache --no-configuration-cache` pasa.
- `aapt2 dump badging` confirma `package='com.atlaspeak'`, `versionCode='105'`,
  `versionName='V-01.05'`, `minSdkVersion='31'` y `targetSdkVersion='35'`.
- `apksigner verify --verbose --print-certs` confirma firma APK Signature Scheme v2 con
  certificado `CN=Atlas Peak`.
- SHA-256: `1453F007CEDF4BB79CB04ABA8BA4B678D992CF1B87898055F468FC0D635E48C1`.
- Artefacto reemplazado por el paquete regenerado con `BUG-034`; el checksum vigente es
  `F47D61AE4DEBF34A7F7E2D1254BE165771E8518B5C5EB14A245F3D3DF169A2F9`.

## [V-01.04] - 2026-05-25

### Release V-01.04

#### 2026-05-25 - Comprobacion final con subagentes

**Corregido**
- `shouldShowBottomBar()` usa allowlist explicita de rutas con chrome; una ruta nueva ya no
  mostrara la bottom navigation por accidente.
- `SPEC.md` deja de hardcodear versiones de dependencias fuera de `gradle/libs.versions.toml`
  y cierra correctamente como spec v2.2.
- `SECURITY.md` actualiza hallazgos antiguos de `FLAG_SECURE`/contraseña para alinearlos con
  SEC-025 y SEC-026.
- Checksums anteriores de V-01.04 quedan marcados como artefactos reemplazados; el checksum
  vigente es el del ultimo paquete generado.

**Verificado**
- Revision con subagentes: UI/navegacion, seguridad/release y empaquetado APK.
- `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:jacocoDebugDomainDataCoverageVerification :app:compileDebugAndroidTestKotlin :app:lintDebug :app:packageReleaseUpdate --no-daemon --console=plain --no-build-cache --no-configuration-cache` pasa.
- `aapt2 dump badging` confirma `package='com.atlaspeak'`, `versionCode='104'`,
  `versionName='V-01.04'`, `minSdkVersion='31'` y `targetSdkVersion='35'`.
- `apksigner verify --verbose --print-certs` confirma firma APK Signature Scheme v2 con
  certificado `CN=Atlas Peak`.
- SHA-256 vigente: `07F03D71658B2DA2C799370C074D56E7DFFC5B62632BC475571F893F3307A8E8`.
- `adb devices` no muestra ningun movil conectado, por lo que no se instalo fisicamente.

#### 2026-05-25 - Paquete APK de actualizacion verificado

**Verificado**
- `.\gradlew.bat :app:packageReleaseUpdate --no-daemon --console=plain --no-build-cache --no-configuration-cache` pasa y regenera `build/distribution/AtlasPeak-V-01.04-release.apk`.
- `aapt2 dump badging` confirma `package='com.atlaspeak'`, `versionCode='104'`,
  `versionName='V-01.04'`, `minSdkVersion='31'` y `targetSdkVersion='35'`.
- `apksigner verify --verbose --print-certs` confirma firma APK Signature Scheme v2 con
  certificado `CN=Atlas Peak`.
- SHA-256: `17CBFAA8ED7B74E45DF8D0EA9F02BD9B2F05F4A8A4D53D4436A6FEE6D09A4548`.
- Artefacto reemplazado por el paquete final de esta misma V-01.04; el checksum vigente es
  `07F03D71658B2DA2C799370C074D56E7DFFC5B62632BC475571F893F3307A8E8`.
- `adb devices` no muestra ningun movil conectado, por lo que no se instalo fisicamente.

#### 2026-05-25 - Polish Home y navegacion inferior

**Cambiado**
- La bottom navigation queda explicitamente icon-only (`alwaysShowLabel = false`), conserva
  `contentDescription` localizado y mantiene el tab Perfil seleccionado en sus subpantallas.
- La bottom navigation se oculta solo en rutas fullscreen/onboarding y sigue visible en
  `WeeklyPlan`, `Settings` y `BackupRestore`, alineado con `DESIGN.md`.
- `Home` queda en lista vertical de una card por fila, con cards a ancho completo, margen de
  pantalla `spacing.screen`, gap `spacing.cardGap` y padding interno `spacing.card`.

**Verificado**
- `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --tests com.atlaspeak.presentation.navigation.BottomNavigationPolicyTest --tests com.atlaspeak.presentation.StaticUiPolicyTest --no-daemon --console=plain` pasa.
- `.\gradlew.bat :app:lintDebug --no-daemon --console=plain` pasa.
- QA runtime bloqueada: `adb devices` no muestra ningun emulador/dispositivo conectado.

#### 2026-05-25 - Tabs inferiores restauradas

**Corregido**
- Registrado y resuelto `BUG-033`: `Entrenar`, `Progreso`, `Cuerpo` y `Perfil` vuelven a
  navegar como destinos top-level directos del `NavHost`.
- Eliminado el wrapper `AppRoute.Main`; `Launch` y `Onboarding` entran directamente en
  `Home`, y la bottom navigation vuelve a usar `Home` como raiz estable del back stack.

**Cambiado**
- Version de app subida a `versionName = "V-01.04"` y `versionCode = 104` para que Android
  acepte la actualizacion sobre V-01.03.

**Verificado**
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat :app:compileDebugKotlin :app:testDebugUnitTest --tests com.atlaspeak.presentation.navigation.BottomNavigationPolicyTest --no-daemon --console=plain` pasa.
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon --console=plain` pasa.
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat :app:packageReleaseUpdate --no-daemon --console=plain --no-build-cache --no-configuration-cache` pasa y genera `build/distribution/AtlasPeak-V-01.04-release.apk`.
- `aapt2 dump badging` confirma `package='com.atlaspeak'`, `versionCode='104'`,
  `versionName='V-01.04'` y `minSdkVersion='31'`.
- `apksigner verify --verbose --print-certs` confirma firma APK Signature Scheme v2.
- SHA-256: `19B6D388F82F6C6DFDA06821D5C63509DD8A07CCA82754AB7CFBB32B5A99DD2B`.
- Artefacto reemplazado por el paquete verificado posterior de esta misma V-01.04; el
  checksum vigente es `07F03D71658B2DA2C799370C074D56E7DFFC5B62632BC475571F893F3307A8E8`.
- QA runtime con AVD no se pudo completar: `AtlasPeak_UserClean_API35` y `AtlasPeak_API35`
  aparecen en `adb`, pero no completan `sys.boot_completed`, por lo que Android no expone
  el servicio `package` para instalar.

## [V-01.03] - 2026-05-25

### Release V-01.03

#### 2026-05-25 - Capturas habilitadas

**Cambiado**
- `SecureScreenEffect` deja de aplicar `FLAG_SECURE` y limpia el flag para permitir capturas
  en todas las pantallas.
- `SensitiveRoutePolicyTest` queda alineado: las rutas sensibles se siguen clasificando, pero
  ya no bloquean screenshots.

**Corregido**
- `:app:packageReleaseUpdate` queda marcado como no compatible con configuration cache para
  evitar un falso fallo despues de generar el paquete de distribucion.

**Seguridad**
- Registrado `SEC-026`: riesgo aceptado de capturas en pantallas con salud, perfil,
  entrenamiento y backup.

**Documentado**
- `DOCS_TECNICA.md` y `DOCS_USUARIO.md` explican que las capturas estan permitidas y el riesgo
  de compartirlas.

**Verificado**
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat :app:testDebugUnitTest --tests com.atlaspeak.presentation.navigation.SensitiveRoutePolicyTest --no-daemon --console=plain` pasa.
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat :app:compileDebugKotlin --no-daemon --console=plain` pasa.
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat :app:packageReleaseUpdate --no-daemon --console=plain` pasa y regenera `build/distribution/AtlasPeak-V-01.03-release.apk`.
- `aapt2 dump badging` confirma `package='com.atlaspeak'`, `versionCode='103'`,
  `versionName='V-01.03'` y `minSdkVersion='31'`.
- `apksigner verify --verbose --print-certs` confirma firma APK Signature Scheme v2.
- SHA-256: `50BFA2C0805D59B925080CEB5351EB233ADCFDACF939E9D39CE034D61EC46ED3`.

#### 2026-05-25 - Actualizacion sin perdida de datos

**Anadido**
- Task Gradle `:app:packageReleaseUpdate` para generar un paquete de actualizacion release:
  APK firmado, `install-adb.bat`, checksum SHA-256 y README de instalacion en
  `build/distribution/`.
- `app/build.gradle.kts` centraliza `applicationId`, `versionCode` y `versionName` para que el
  empaquetado de release use la misma identidad Android que la app instalada.

**Documentado**
- `DOCS_TECNICA.md` explica las tres condiciones para conservar datos al actualizar:
  mismo `applicationId`, mismo keystore de release y `versionCode` superior.
- `DOCS_USUARIO.md` avisa que no se debe desinstalar antes de actualizar y que
  `install-adb.bat` usa `adb install -r`.

#### 2026-05-25 - Bump de version

**Cambiado**
- Fijada la version de app en `versionName = "V-01.03"` y `versionCode = 103`.

**Anadido**
- Copia de distribucion en `build/distribution/AtlasPeak-V-01.03-release.apk`.
- `build/distribution/install-adb.bat` actualizado para instalar V-01.03 con `adb install -r`.
- `build/distribution/README-INSTALACION.txt` y `SHA256SUMS.txt` actualizados para V-01.03.

**Verificado**
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat :app:assembleRelease --no-daemon --console=plain --no-build-cache --no-configuration-cache` pasa.
- `aapt2 dump badging` confirma `package='com.atlaspeak'`, `versionCode='103'`,
  `versionName='V-01.03'` y `minSdkVersion='31'`.
- `apksigner verify --verbose --print-certs` confirma firma APK Signature Scheme v2.
- SHA-256: `50BFA2C0805D59B925080CEB5351EB233ADCFDACF939E9D39CE034D61EC46ED3`.
- `adb devices` no muestra ningun movil conectado, por lo que no se instalo fisicamente.

## [V-01.02] - 2026-05-25

### Release V-01.02

#### 2026-05-25 - APK release instalable para movil

**Anadido**
- Copia de distribucion en `build/distribution/AtlasPeak-V-01.02-release.apk`.
- Script local `build/distribution/install-adb.bat` para instalar con `adb install -r`.
- `build/distribution/README-INSTALACION.txt` con pasos de instalacion y checksum.

**Verificado**
- `.\gradlew.bat :app:assembleRelease --no-daemon --console=plain --no-build-cache --no-configuration-cache` pasa.
- `apksigner verify --verbose --print-certs` confirma firma APK Signature Scheme v2.
- `aapt2 dump badging` confirma `package='com.atlaspeak'`, `versionCode='102'`,
  `versionName='V-01.02'` y `minSdkVersion='31'`.
- SHA-256: `4CA1B69E7E781A1263C7940AE75D3DE179AD826BE7336D37E7AFCBC670C5A60C`.
- `adb devices` no muestra ningun movil conectado, por lo que no se instalo fisicamente.

#### 2026-05-25 - APK release

**Cambiado**
- Fijada la version de app en `versionName = "V-01.02"` y `versionCode = 102`.

**Verificado**
- `.\gradlew.bat :app:assembleRelease --no-daemon --console=plain --no-build-cache --no-configuration-cache` pasa.
- APK generado: `app/build/outputs/apk/release/app-release-unsigned.apk` (27.964.605 bytes).
- `aapt2 dump badging` confirma `versionCode='102'` y `versionName='V-01.02'`.
- SHA-256: `6B9A5CCA0CE86D32858767BE2BDE24DB120C1EDCDBEBC5BF88A74A6E9218DCD6`.
- `apksigner verify` no valida el APK porque no existe `keystore.properties`; el artefacto
  generado es release unsigned.

### Retirada del gate de contraseña local

#### 2026-05-25 - Entrada directa a la app

**Eliminado**
- Retirado el login local como gate de entrada: `Launch` navega a `Home` tras onboarding
  completado.
- Eliminados `LoginScreen`, `AuthViewModel`, `SessionLockViewModel`, use cases/repositorios
  de auth local, strings de auth, dependencia de biometría y dependencias de Credential Manager.
- Onboarding ya no pide contraseña ni opt-in biométrico.

**Cambiado**
- La passphrase queda limitada a backups cifrados: crear/restaurar backup Drive/local.
- Export JSON/CSV en claro ya no exige contraseña local; el texto avisa que se comparte sin
  cifrado.
- `users.password_hash`, `users.password_salt` y `users.last_login_at` pasan a nullable; Room
  sube a schema v3 con migración 2→3 que borra credenciales locales legadas.

**Seguridad**
- Registrado `SEC-025`: riesgo aceptado si alguien accede al móvil ya desbloqueado.
- `USE_BIOMETRIC` deja de declararse porque no hay desbloqueo biométrico local en v1.

**Verificado**
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleRelease --no-daemon --console=plain --no-build-cache --no-configuration-cache` pasa.
- `connectedAndroidTest` no se ejecuta: `adb devices` no muestra emulador/dispositivo.

### Rediseño UI premium

#### 2026-05-25 - Sistema visual y dashboard

**Cambiado**
- Redefinida la paleta en `AtlasPeakTheme`: neutros off-black/off-white, acento lima/verde
  y coral reservado para error/riesgo.
- Añadidos `PremiumBackground`, `PremiumCard` y `PremiumIconBadge` como base visual compartida.
- Rediseñado el dashboard Home con hero de minutos semanales, métricas en pares, tarjetas
  de gráfica con mayor jerarquía y navegación inferior flotante.
- Actualizados selectores de periodo a chips propios tipo segmented control.
- Aplicado el nuevo fondo premium a auth, onboarding, entrenamiento, cardio, progreso,
  cuerpo, perfil, planificación, notificaciones y backup.
- Ajustada la escala tipográfica para titulares y números grandes con tabular figures.
- `DESIGN.md` queda alineado con la nueva dirección visual.

**Verificado**
- `.\gradlew.bat compileDebugKotlin --rerun-tasks` pasa.
- `.\gradlew.bat test --rerun-tasks` pasa.
- `.\gradlew.bat lint` pasa.
- `.\gradlew.bat assembleDebug` pasa.
- QA visual runtime no se pudo completar porque `adb devices` no muestra emulador ni
  dispositivo conectado.

### Auditoria final v1

#### 2026-05-25 - Crash de tabs de bottom navigation

**Corregido**
- Registrado y resuelto `BUG-032`: tocar `Entrenar`, `Progreso`, `Cuerpo` o `Perfil`
  desde el dashboard podia cerrar la app.
- La bottom navigation ya no hace `popUpTo` al `startDestination` transitorio `launch`;
  usa `Home` como raiz estable del back stack autenticado.

**Anadido**
- `BottomNavigationPolicyTest` cubre que los tabs no vuelvan a depender de
  `findStartDestination`.

**Verificado**
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat :app:compileDebugKotlin --rerun-tasks --no-build-cache --no-daemon --console=plain` pasa.
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat :app:testDebugUnitTest --tests com.atlaspeak.presentation.navigation.BottomNavigationPolicyTest --no-daemon --console=plain` pasa.
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat :app:assembleDebug --no-daemon --console=plain` pasa.
- QA runtime en emulador no pudo completarse: `AtlasPeak_Clean_API35` arranca QEMU, pero
  sale antes de aparecer en `adb devices`; `emulator -accel-check` indica WHPX operativo.

#### 2026-05-25 - Generacion de APK debug

**Verificado**
- `.\gradlew.bat :app:assembleDebug --rerun-tasks --no-daemon --console=plain` pasa.
- APK generada: `app/build/outputs/apk/debug/app-debug.apk` (102.652.921 bytes).
- `apksigner verify --verbose --print-certs` confirma firma debug con APK Signature Scheme v2.
- SHA-256: `36264C39CF17FC2352503F3CF8E1260C6B4A239B28E7F3AA38FE16B41851F226`.

#### 2026-05-24 - Capturas visuales en emulador debug

**Cambiado**
- `SecureScreenEffect` mantiene `FLAG_SECURE` en release y en dispositivos reales, pero lo omite
  en emulador con `BuildConfig.DEBUG` para permitir QA visual y capturas de pantalla.

**Seguridad**
- Registrado `SEC-024`: bypass acotado a emulador debug; las rutas siguen marcadas como
  sensibles y release no cambia.

**Verificado**
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat :app:testDebugUnitTest --tests com.atlaspeak.presentation.navigation.SensitiveRoutePolicyTest --no-daemon` pasa.
- Reinstalada build debug en `emulator-5554`; `screencap` ya muestra el onboarding en vez de
  una captura negra.

#### 2026-05-24 - Runtime QA en emulador y carga nativa SQLCipher

**Corregido**
- Registrado y resuelto `BUG-031`: la app caia al arrancar en emulador porque `libsqlcipher.so`
  no se cargaba antes de abrir Room cifrado.
- `AtlasPeakApplication.attachBaseContext()` carga `System.loadLibrary("sqlcipher")` antes de
  `onCreate()` y antes de que Hilt pueda construir dependencias que tocan la DB.

**Anadido**
- `StaticSecurityPolicyTest` verifica que la carga nativa de SQLCipher queda antes del acceso
  de aplicacion a Room.

**Verificado**
- Se limpiaron procesos antiguos `adb`/`emulator`/`qemu`, se arranco `AtlasPeak_UserClean_API35`
  en `emulator-5554`, se instalo `app-debug.apk` y se lanzo `com.atlaspeak.debug`.
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat :app:testDebugUnitTest --tests com.atlaspeak.security.StaticSecurityPolicyTest --no-daemon` pasa.
- `C:\tmp\atlas-dev-tools\gradle-8.11.1\bin\gradle.bat :app:assembleDebug --no-daemon` pasa.
- `adb shell dumpsys activity activities` confirma `com.atlaspeak.debug/com.atlaspeak.MainActivity`
  como ventana enfocada; `pidof com.atlaspeak.debug` devuelve proceso vivo.
- `build/runtime-qa/atlaspeak-final-ui.xml` muestra el onboarding inicial; la captura PNG queda
  negra porque `FLAG_SECURE` protege la pantalla, comportamiento esperado.
- `build/runtime-qa/atlaspeak-final-logcat.txt` no contiene `FATAL EXCEPTION`, `UnsatisfiedLinkError`
  ni `ANR in com.atlaspeak` tras el fix.

#### 2026-05-24 - Seguridad, arquitectura y backup hardening

**Anadido**
- `SessionLockViewModel` revalida rutas sensibles en `ON_RESUME` y vuelve a `Login` si expira
  el timeout local.
- Contrato de backup en dominio: `BackupRepository`, `BackupUseCase` y modelos `DriveBackup`,
  `BackupResult`, `SharedBackupExport`.
- `DataBackupRepository` mapea la implementacion Room/Drive/export a modelos de dominio.
- `StaticArchitecturePolicyTest` bloquea imports `presentation -> data` y dependencias UI/data
  dentro de `domain`.
- Tests de regresion para timeout de sesion, step-up auth de export plaintext y columnas
  desconocidas en restore de backup.

**Cambiado**
- `BackupRestoreViewModel` ya no depende de managers de `data`; consume `BackupUseCase`.
- Export JSON/CSV en claro exige contrasena local antes de escribir el archivo y limpia
  `BackupRestoreUiState.password` tras operaciones sensibles.
- `GoogleTaskAwait` se mueve a `core/google` para evitar que presentation importe helpers de data.
- `BackupFileCodec`, `DriveBackupManager`, `LocalBackupExportManager` y `EncryptionManager`
  limpian buffers derivados/temporales cuando ya no se necesitan.
- Restore de backup valida columnas por tabla antes de insertar filas restauradas.

**Corregido**
- Registrado `SEC-021` / `BUG-030`: timeout de desbloqueo local no estaba conectado a lifecycle.
- Registrado `SEC-022` / `BUG-029`: export plaintext no exigia step-up auth y retenia password UI.
- Registrado `SEC-023`: restore aceptaba columnas desconocidas hasta SQLite.
- Registrado `BUG-028`: presentation de backup importaba data layer.

**Verificado**
- `./gradlew compileDebugKotlin testDebugUnitTest --tests com.atlaspeak.domain.usecase.auth.LocalAuthUseCaseTest --tests com.atlaspeak.domain.usecase.auth.BiometricAuthPolicyTest --tests com.atlaspeak.presentation.navigation.SessionLockViewModelTest --tests com.atlaspeak.presentation.backup.BackupRestoreViewModelTest --tests com.atlaspeak.architecture.StaticArchitecturePolicyTest --no-daemon`
  pasa.
- `./gradlew assembleDebug assembleRelease test lint compileDebugAndroidTestKotlin jacocoDebugDomainDataCoverageVerification --no-daemon`
  pasa como gate final.
- `python Skills/05_Security/cyber-neo/scripts/scan_secrets.py app` no encuentra secretos.
- `python Skills/05_Security/cyber-neo/scripts/check_lockfiles.py .` no encuentra hallazgos.
- `git diff --check` pasa.
- QA visual/performance runtime sigue bloqueada: `adb devices` no lista dispositivos, no hay AVDs,
  y `emulator -accel-check` devuelve codigo 6.

### Fase 16 - Polish + performance + accesibilidad

#### 2026-05-24 - Tipografia, contraste AA y QA estatica de UI

**Anadido**
- Fuentes locales Poppins 600/700 e Inter variable en `app/src/main/res/font/`.
- `THIRD_PARTY_NOTICES.md` documenta origen y licencia OFL de Poppins/Inter.
- `ThemeAccessibilityTest` verifica contraste WCAG AA en pares `on*`/fondo light y dark.
- `StaticUiPolicyTest` bloquea strings visibles hardcodeados en presentation, icon-only
  buttons sin descripcion, perdida de fuentes Atlas Peak y desalineacion ES/EN.

**Cambiado**
- `Typography.kt` usa Poppins para display/headline/title y Inter para body/label.
- `primary` light cambia de `#E53935` a `#D32F2F` para cumplir contraste AA con texto blanco.
- Icono launcher, color rojo de rutina por defecto, `SPEC.md` y `DESIGN.md` quedan alineados
  con `#D32F2F`.
- `primaryContainer/onPrimaryContainer` quedan definidos en light/dark y las tarjetas
  seleccionadas usan contenido seleccionado con contraste probado.
- Graficos de dashboard/progreso/cuerpo y mapas GPS exponen resumen semantico localizado.
- Filas de switch/checkbox fusionan label/control con rol accesible; el rest timer expone
  progreso y usa un anillo visible.
- `NavHost` usa fade breve y lo desactiva si `ANIMATOR_DURATION_SCALE` es 0.
- `resourceConfigurations` se reemplaza por `androidResources.localeFilters`.

**Corregido**
- Registrado `BUG-024`: primary light no cumplia contraste AA.
- Registrado `BUG-025`: la tipografia del sistema de diseno no estaba aplicada.
- Registrado `BUG-026`: estados seleccionados usaban `primaryContainer` sin token Atlas.
- Registrado `BUG-027`: graficos, mapa y toggles tenian semantica insuficiente para TalkBack.

**Verificado**
- `./gradlew testDebugUnitTest --tests com.atlaspeak.presentation.StaticUiPolicyTest --tests com.atlaspeak.presentation.theme.ThemeAccessibilityTest --tests com.atlaspeak.domain.usecase.workout.RoutineUseCaseTest --tests com.atlaspeak.domain.usecase.workout.StartWorkoutSessionUseCaseTest --no-daemon`
  pasa.
- `./gradlew assembleDebug assembleRelease test lint compileDebugAndroidTestKotlin jacocoDebugDomainDataCoverageVerification --no-daemon`
  pasa como gate completo de cierre.
- APK release minificado: `app-release-unsigned.apk` = 26,86 MB.
- `python Skills/05_Security/cyber-neo/scripts/scan_secrets.py app` no encuentra secretos.
- `python Skills/05_Security/cyber-neo/scripts/check_lockfiles.py .` no encuentra hallazgos.
- `git diff --check` pasa.
- QA visual/performance runtime bloqueada: no hay dispositivos `adb`, no hay AVDs, y
  `emulator -accel-check` devuelve codigo 6 con aviso Hyper-V/WHPX.

### Fase 15 - Testing + cobertura

#### 2026-05-24 - Gate JaCoCo y hardening de workers/backup

**Anadido**
- JaCoCo en `app/build.gradle.kts` con tarea `jacocoDebugDomainDataCoverageVerification`
  y umbral del 70% para el core JVM de `domain`/`data`.
- Paso CI `Domain/data coverage` para fallar PRs si baja la cobertura.
- `BackupWorkerRunner` extrae la logica testeable del worker de auto-backup.
- Tests de backup worker: auto-backup desactivado, hash estable sin cambios, subida exitosa
  y retry en fallo limpiando siempre la password.
- Tests de restore Drive con password valida, schema futuro y tablas faltantes.
- Test de nombres WorkManager para recordatorios ISO 1..7.
- Test instrumentado compilable para comprobar que `BackupWorkScheduler` mantiene un unico
  trabajo periodico.

**Cambiado**
- `BackupWorker` delega en `BackupWorkerRunner`.
- `NotificationWorkNames.trainingReminder()` valida `dayOfWeek` en rango ISO.
- `TrainingReminderWorker` ignora input corrupto de weekday con `Result.success()` para evitar
  retries inutiles.
- `DOCS_TECNICA.md` documenta el gate de cobertura y sus exclusiones JVM/Android.

**Corregido**
- Registrado `BUG-022`: recordatorios aceptaban dias de semana invalidos.
- Registrado `BUG-023`: el objetivo de cobertura `domain`/`data` no era verificable.

**Verificado**
- `./gradlew testDebugUnitTest --tests com.atlaspeak.data.backup.DriveBackupManagerTest --tests com.atlaspeak.data.backup.BackupWorkerRunnerTest --tests com.atlaspeak.data.notification.NotificationWorkNamesTest --no-daemon`
  pasa.
- `./gradlew compileDebugAndroidTestKotlin --no-daemon` pasa tras cambiar el androidTest a
  observacion LiveData de WorkManager.
- `./gradlew jacocoDebugDomainDataCoverageVerification --no-daemon` pasa con 80,28% line
  coverage del core JVM `domain`/`data`.
- `./gradlew assembleDebug assembleRelease test lint compileDebugAndroidTestKotlin jacocoDebugDomainDataCoverageVerification --no-daemon`
  pasa como gate completo de cierre.
- `python Skills/05_Security/cyber-neo/scripts/scan_secrets.py app` no encuentra secretos.
- `python Skills/05_Security/cyber-neo/scripts/check_lockfiles.py .` no encuentra hallazgos.
- `git diff --check` pasa.

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
