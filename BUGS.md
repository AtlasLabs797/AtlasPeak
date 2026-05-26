# BUGS.md - Registro de bugs

> Log de bugs detectados y su resolucion. Trazabilidad: cada bug arreglado deja rastro aqui.
> Orden: mas reciente arriba.

---

## Formato de entrada

```
### BUG-NNN - Titulo corto
- **Estado:** Abierto / En progreso / Resuelto / No reproducible
- **Fecha deteccion:** AAAA-MM-DD
- **Fase:** N
- **Severidad:** Critica / Alta / Media / Baja
- **Sintoma:** que se observo (pasos para reproducir si aplica).
- **Causa raiz:** por que pasaba realmente.
- **Solucion:** que se cambio (archivos / commits).
- **Prevencion:** test anadido / regla para que no vuelva.
- **Fecha resolucion:** AAAA-MM-DD
```

---

## Entradas

### BUG-041 - Entrenamiento planificado no era accion principal en Home ni Entrenar
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-26
- **Fase:** Polish UI/UX
- **Severidad:** Media
- **Sintoma:** Inicio mostraba metricas antes que la rutina planificada del dia, y Entrenar abria por defecto la biblioteca/creacion de ejercicios en vez de la accion de iniciar una rutina.
- **Causa raiz:** `HomeScreen` no consultaba el plan semanal y `TrainUiState` arrancaba en `TrainTab.Exercises`; ademas `RoutineContent` mostraba el constructor antes del CTA de inicio.
- **Solucion:** `HomeViewModel` carga la rutina de hoy desde `WeeklyPlanUseCase`, `HomeScreen` muestra una card "Entrenamiento de hoy" con CTA, `TrainUiState` abre en `Routines` y el detalle/inicio de rutina se renderiza antes del builder.
- **Prevencion:** mantener el inicio de sesion de entrenamiento como primer CTA visible cuando exista una rutina planificada o seleccionada.
- **Fecha resolucion:** 2026-05-26

---

### BUG-040 - Iconos de cardio ignoraban el icon_name de seed data
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-26
- **Fase:** Polish UI/UX
- **Severidad:** Baja
- **Sintoma:** todos los tipos de cardio se mostraban con el mismo icono de correr aunque la base de datos seed ya tenia `icon_name` distintos para bici, remo, natacion, etc.
- **Causa raiz:** `CardioType` de dominio no exponia `iconName` y `TrainScreen.CardioTypeCard` hardcodeaba `DirectionsRun`.
- **Solucion:** `CardioType` expone `iconName`, `RoomCardioRepository` lo mapea desde Room y `CardioTypeCard` resuelve iconos Material distintos por tipo.
- **Prevencion:** si un modelo trae metadatos visuales desde seed/schema, la UI no debe reemplazarlos por un icono generico.
- **Fecha resolucion:** 2026-05-26

---

### BUG-039 - Onboarding de perfil usaba texto libre para genero y objetivo
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-26
- **Fase:** Polish UI/UX
- **Severidad:** Media
- **Sintoma:** al tocar Genero u Objetivo aparecia el teclado, no un selector; el usuario podia escribir valores libres inconsistentes.
- **Causa raiz:** `OnboardingScreen` usaba `AtlasTextField` para campos de opcion cerrada.
- **Solucion:** se sustituyen esos campos por selectores con `FilterChip`: Masculino/Femenino y objetivos predefinidos.
- **Prevencion:** campos de taxonomia cerrada se implementan como selector, no como input de texto.
- **Fecha resolucion:** 2026-05-26

---

### BUG-038 - Entrenamiento activo no avanzaba de ejercicio y el descanso acababa en silencio
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-26
- **Fase:** Polish UI/UX
- **Severidad:** Alta
- **Sintoma:** al completar todos los sets de un ejercicio la pantalla no pasaba al siguiente; el CTA seguia diciendo Finalizar y podia cerrar la sesion. Al llegar a cero, el descanso desaparecia sin sonido ni vibracion final.
- **Causa raiz:** `ActiveWorkoutScreen` no sincronizaba el `HorizontalPager` con el estado de sets completados y el feedback sonoro/haptico estaba ligado al inicio del timer, no al final. El boton primario no distinguia "siguiente ejercicio" de "finalizar entrenamiento".
- **Solucion:** el pager avanza al siguiente ejercicio tras acabar/omitir el descanso, el CTA cambia a Siguiente o queda deshabilitado hasta completar sets, y el feedback se dispara cuando el rest timer llega a cero.
- **Prevencion:** el estado completado de un ejercicio debe tener una transicion explicita de pagina y el feedback del descanso debe probarse en el evento de fin, no solo en el de inicio.
- **Fecha resolucion:** 2026-05-26

---

### BUG-037 - AssistChip de badge en composicion corporal sugeria ser interactivo
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-26
- **Fase:** Auditoria final
- **Severidad:** Baja
- **Sintoma:** la lista de metricas de composicion corporal mostraba un `AssistChip` (Health Connect / Manual only) que respondia al tap con ripple pero no hacia nada (`onClick = {}`), confundiendo al usuario sobre si era una accion.
- **Causa raiz:** el chip se uso como etiqueta informativa con `onClick` vacio en `BodyCompositionScreen.kt`. Material da ripple por defecto a cualquier `AssistChip` clickable.
- **Solucion:** se anade `enabled = false` al chip en `BodyCompositionScreen.kt`, asi pierde el ripple y queda claramente como badge informativo.
- **Prevencion:** revisar `Grep` periodico de `onClick = \{\}` para detectar componentes interactivos sin accion.
- **Fecha resolucion:** 2026-05-26

---

### BUG-036 - Teclado virtual tapa los TextField (edge-to-edge sin imePadding)
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-26
- **Fase:** Auditoria final
- **Severidad:** Alta
- **Sintoma:** al tocar un `TextField` (onboarding, backup, ajustes, plan semanal, entrenamiento) el teclado se abria por encima del campo, dejando al usuario escribiendo a ciegas. El borde inferior de la pantalla seguia en su sitio en vez de empujar el contenido hacia arriba.
- **Causa raiz:** `MainActivity` activa `enableEdgeToEdge()` pero el contenedor del `NavHost` en `AtlasPeakApp.kt` no aplicaba `imePadding()` y ninguna pantalla individual lo hacia tampoco. El `Scaffold` de Material 3 no incluye `WindowInsets.ime` en su `contentWindowInsets` por defecto, asi que el contenido quedaba debajo del teclado.
- **Solucion:** el `Box` que envuelve `AtlasPeakNavHost` ahora aplica `.fillMaxSize().padding(innerPadding).imePadding()`. Asi todas las pantallas se empujan hacia arriba cuando aparece el teclado, sin tocar pantalla por pantalla.
- **Prevencion:** el `imePadding()` esta centralizado en el contenedor raiz, asi que cualquier pantalla nueva con inputs hereda el comportamiento automaticamente.
- **Fecha resolucion:** 2026-05-26

---

### BUG-035 - Menu inferior con taps no registrados por doble inset y altura insuficiente
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-26
- **Fase:** Auditoria final
- **Severidad:** Alta
- **Sintoma:** el menu inferior se renderizaba pero al tocar los items no cambiaba de pantalla; los arreglos previos (BUG-032/033/034) corrigieron la logica de navegacion pero el problema persistia a nivel de interaccion.
- **Causa raiz:** el `NavigationBar` Material 3 aplica por defecto `NavigationBarDefaults.windowInsets` (system navigation bars). El contenedor `Box` exterior ya aplicaba `navigationBarsPadding()`, asi que el padding de la barra del sistema se sumaba dos veces. Combinado con `Modifier.height(spacing.minTouchTarget + spacing.md)` (64dp, frente a los 80dp por defecto de Material), el area util quedaba comprimida y los `NavigationBarItem` perdian o desplazaban su touch target detras del system nav bar.
- **Solucion:** en `AtlasPeakApp.kt` se elimina la altura forzada del `NavigationBar` (usa la altura por defecto de Material 3, 80dp) y se le pasa `windowInsets = WindowInsets(0, 0, 0, 0)` para que el unico responsable de los insets sea el `Box` envolvente. Asi cada `NavigationBarItem` recibe su touch target completo y los taps cambian de pestana de forma fiable.
- **Prevencion:** el fix mantiene a `BottomNavigationPolicyTest` verde sin nuevas suposiciones. No hay regresion de logica de navegacion (todas las aserciones siguen pasando).
- **Fecha resolucion:** 2026-05-26

---

### BUG-034 - Menu inferior restauraba stacks inestables
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-25
- **Fase:** Auditoria final
- **Severidad:** Alta
- **Sintoma:** el menu inferior seguia sin comportarse de forma fiable al cambiar entre `Home`, `Entrenar`, `Progreso`, `Cuerpo` y `Perfil`; en especial podia depender de un back stack anterior o volver a subpantallas.
- **Causa raiz:** el arreglo previo uso `Home` como raiz global y mantuvo `saveState/restoreState` en los tabs. Eso es fragil: `Home` es una pantalla, no el contenedor autenticado, y restaurar estado en tabs puede revivir rutas hijas en vez de abrir la raiz del tab.
- **Solucion:** se anade `AppRoute.AppGraph` como grafo autenticado estable con `Home` como start destination. El menu inferior navega con `popUpTo(AppGraph)`, `launchSingleTop`, `saveState = false` y `restoreState = false`; tocar un tab siempre abre su ruta raiz.
- **Prevencion:** `BottomNavigationPolicyTest` ahora exige `AppGraph` como raiz de tabs, prohibe `restoreState = true`/`saveState = true` en el menu inferior y mantiene cubiertas las rutas visibles/ocultas.
- **Fecha resolucion:** 2026-05-25

---

### BUG-033 - Regresion de tabs inferiores tras wrapper de navegacion
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-25
- **Fase:** Auditoria final
- **Severidad:** Alta
- **Sintoma:** `Entrenar`, `Progreso`, `Cuerpo` y `Perfil` seguian sin abrir de forma fiable desde el menu inferior.
- **Causa raiz:** el arreglo anterior volvio a meter las tabs dentro de un grafo wrapper `main` y el menu hizo `popUpTo(main)`. Ese grafo no aportaba nada y dejaba la navegacion dependiendo de un destino intermedio innecesario.
- **Solucion:** se elimina `AppRoute.Main`; `Launch` y `Onboarding` entran directamente en `Home`, y el menu inferior usa `Home` como raiz estable del back stack autenticado.
- **Prevencion:** `BottomNavigationPolicyTest` bloquea el regreso del wrapper `main`, comprueba las cinco rutas de tabs y mantiene prohibido `findStartDestination()` en el menu inferior.
- **Nota 2026-05-25:** supersedido por `BUG-034`. La conclusion correcta no era "sin grafo autenticado", sino "sin grafo wrapper mal usado"; el fix definitivo usa `AppGraph`.
- **Fecha resolucion:** 2026-05-25

---

### BUG-032 - Bottom navigation crasheaba al abrir tabs autenticados
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-25
- **Fase:** Auditoria final
- **Severidad:** Alta
- **Sintoma:** desde el dashboard, tocar `Entrenar`, `Progreso`, `Cuerpo` o `Perfil` cerraba la app.
- **Causa raiz:** la bottom navigation hacia `popUpTo(navController.graph.findStartDestination().id)`, pero el start destination real del grafo es `launch`, un destino transitorio eliminado del back stack despues de login/onboarding.
- **Solucion:** `AtlasPeakApp` usa `popUpTo(AppRoute.Home.route)` como raiz estable de los tabs.
- **Prevencion:** `BottomNavigationPolicyTest` bloquea que los tabs vuelvan a depender de `findStartDestination`.
- **Fecha resolucion:** 2026-05-25

---

### BUG-031 - SQLCipher nativo no se cargaba antes de abrir Room
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-24
- **Fase:** Auditoria final
- **Severidad:** Alta
- **Sintoma:** la app instalada en el emulador arrancaba `MainActivity` y caia con `UnsatisfiedLinkError` en `net.zetetic.database.sqlcipher.SQLiteConnection.nativeOpen`.
- **Causa raiz:** `sqlcipher-android` incluye `libsqlcipher.so`, pero la app no llamaba a `System.loadLibrary("sqlcipher")` antes de que Hilt construyera la DB Room cifrada.
- **Solucion:** `AtlasPeakApplication.attachBaseContext()` carga `sqlcipher` antes de `onCreate()` y de cualquier acceso a Room.
- **Prevencion:** `StaticSecurityPolicyTest` verifica que la carga nativa de SQLCipher ocurre antes del acceso de aplicacion a la DB.
- **Fecha resolucion:** 2026-05-24

---

### BUG-030 - Timeout de desbloqueo local no estaba conectado a lifecycle
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-24
- **Fase:** Auditoria final
- **Severidad:** Alta
- **Sintoma:** al volver a la app desde background, una ruta sensible seguia accesible aunque el timeout hubiera expirado.
- **Causa raiz:** `LaunchViewModel` solo decidia onboarding/login en arranque; no habia observador `ON_RESUME` ni guard central de sesion.
- **Solucion:** `SessionLockViewModel` evalua rutas sensibles en resume y navega a `Login` si `LocalAuthUseCase.shouldRequireSessionUnlock()` lo exige.
- **Prevencion:** `SessionLockViewModelTest` y tests de timeout en `LocalAuthUseCaseTest`.
- **Fecha resolucion:** 2026-05-24

---

### BUG-029 - Export plaintext no exigia reautenticacion y retenia password UI
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-24
- **Fase:** Auditoria final
- **Severidad:** Media
- **Sintoma:** JSON/CSV en claro podian exportarse desde una sesion abierta sin step-up auth; la password escrita seguia en `BackupRestoreUiState`.
- **Causa raiz:** backup/export mezclaba accion sensible con sesion ya autenticada y limpiaba el `CharArray`, pero no el `String` de origen.
- **Solucion:** export JSON/CSV verifica contrasena local antes de escribir; create/restore/export/autobackup limpian el estado `password`.
- **Prevencion:** `BackupRestoreViewModelTest` cubre password incorrecta, export correcto y limpieza de estado.
- **Fecha resolucion:** 2026-05-24

---

### BUG-028 - Presentation de backup importaba data layer
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-24
- **Fase:** Auditoria final
- **Severidad:** Media
- **Sintoma:** `BackupRestoreViewModel` y `BackupRestoreScreen` importaban clases de `com.atlaspeak.data.backup`.
- **Causa raiz:** Fase 12 implemento backup rapido alrededor de managers de data sin contrato de dominio.
- **Solucion:** se anaden modelos/usecase/repositorio de backup en `domain`, `DataBackupRepository` mapea data->domain y `presentation` consume solo dominio.
- **Prevencion:** `StaticArchitecturePolicyTest` falla si `presentation` vuelve a importar `data`, Room, Retrofit u OkHttp.
- **Fecha resolucion:** 2026-05-24

---

### BUG-027 - Graficos, mapa y toggles tenian semantica insuficiente para TalkBack
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-24
- **Fase:** 16
- **Severidad:** Media
- **Sintoma:** charts, mapa GPS y filas de switch/checkbox podian ser visualmente correctos pero pobres para lectores de pantalla.
- **Causa raiz:** Vico/GoogleMap se renderizaban sin resumen semantico y algunos controles dejaban label y switch/checkbox como nodos separados.
- **Solucion:** resumen localizado para charts y ruta GPS; filas de switch/checkbox usan click del row, rol y merge semantico; rest timer expone progreso y texto.
- **Prevencion:** revision lateral de UI obligatoria en Fase 16 y `StaticUiPolicyTest` mantiene i18n/descripcion minima.
- **Fecha resolucion:** 2026-05-24

---

### BUG-026 - Estados seleccionados usaban primaryContainer sin token Atlas
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-24
- **Fase:** 16
- **Severidad:** Media
- **Sintoma:** tarjetas seleccionadas usaban `primaryContainer`, pero el tema no lo definia y caia en los colores baseline de Material.
- **Causa raiz:** el tema solo fijo `primary/onPrimary` y dejo roles contenedor a defaults de Material.
- **Solucion:** se definen `primaryContainer/onPrimaryContainer` light/dark, los textos secundarios seleccionados usan `onPrimaryContainer`, y el test de contraste cubre esos pares.
- **Prevencion:** `ThemeAccessibilityTest` cubre roles contenedor ademas de roles principales.
- **Fecha resolucion:** 2026-05-24

---

### BUG-025 - Tipografia del sistema de diseno no estaba aplicada
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-24
- **Fase:** 16
- **Severidad:** Media
- **Sintoma:** `Typography.kt` usaba `FontFamily.SansSerif` aunque `DESIGN.md` y `SPEC.md` exigen Poppins para titulos/numeros e Inter para cuerpo/labels.
- **Causa raiz:** la fase de fundacion dejo fallback generico en vez de assets locales o Google Fonts.
- **Solucion:** fuentes Poppins 600/700 e Inter variable vendorizadas en `res/font/`, `Typography.kt` usa esos assets y `THIRD_PARTY_NOTICES.md` registra origen/licencia.
- **Prevencion:** `StaticUiPolicyTest` falla si vuelve `FontFamily.SansSerif` o faltan los assets esperados.
- **Fecha resolucion:** 2026-05-24

---

### BUG-024 - Primary light no cumplia contraste AA con texto blanco
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-24
- **Fase:** 16
- **Severidad:** Alta
- **Sintoma:** `onPrimary` blanco sobre `primary #E53935` daba contraste ~4.23:1, por debajo del minimo WCAG AA 4.5:1 para texto normal.
- **Causa raiz:** el rojo de marca se copio como valor visual aproximado sin test de contraste automatizado.
- **Solucion:** primary light cambia a `#D32F2F`; icono, swatch rojo por defecto y specs quedan alineados.
- **Prevencion:** `ThemeAccessibilityTest` verifica pares `on*`/fondo de light y dark contra WCAG AA.
- **Fecha resolucion:** 2026-05-24

---

### BUG-023 - Objetivo de cobertura domain/data no era verificable
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-24
- **Fase:** 15
- **Severidad:** Media
- **Sintoma:** el spec exigia >=70% de cobertura en `domain` y `data`, pero Gradle/CI no tenian tarea de cobertura ni threshold.
- **Causa raiz:** se habian anadido tests por fase, pero no una metrica ejecutable que fallara el build si la cobertura bajaba.
- **Solucion:** JaCoCo en `app/build.gradle.kts`, tarea `jacocoDebugDomainDataCoverageVerification` con umbral 70% y paso CI dedicado.
- **Prevencion:** `DOCS_TECNICA.md` documenta la tarea y CI la ejecuta en cada push/PR.
- **Fecha resolucion:** 2026-05-24

---

### BUG-022 - Recordatorios aceptaban dias de semana invalidos
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-24
- **Fase:** 15
- **Severidad:** Baja
- **Sintoma:** `NotificationWorkNames.trainingReminder()` podia generar nombres como `training_reminder_0` o `training_reminder_8`.
- **Causa raiz:** no habia validacion del rango ISO 1..7 en la fabrica de nombres de WorkManager.
- **Solucion:** `NotificationWorkNames` valida `dayOfWeek in 1..7`; `TrainingReminderWorker` trata input corrupto como `Result.success()` para evitar retries inutiles.
- **Prevencion:** `NotificationWorkNamesTest` cubre dias validos e invalidos.
- **Fecha resolucion:** 2026-05-24

---

### BUG-021 - Notificaciones foreground exponian actividad en lockscreen
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-24
- **Fase:** 14
- **Severidad:** Media
- **Sintoma:** las notificaciones de entrenamiento/cardio podian mostrar tiempo/distancia en pantalla bloqueada.
- **Causa raiz:** los servicios foreground tenian builders y canales propios sin `VISIBILITY_PRIVATE`.
- **Solucion:** visibilidad privada en builders/canales de fuerza, cardio y canales generales de notificacion.
- **Prevencion:** test estatico de builders/canales privados en `StaticSecurityPolicyTest`.
- **Fecha resolucion:** 2026-05-24

---

### BUG-020 - Spec seguia pidiendo Wear OS dentro de v1
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-24
- **Fase:** 13
- **Severidad:** Media
- **Sintoma:** aunque la tabla de fases marcaba Wear OS como diferido a v2, secciones antiguas seguian listando dependencias, modulo y tareas de Wear como si fueran parte de v1.
- **Causa raiz:** se aplazo Wear en v2.2, pero no se limpio todo el texto operativo del spec.
- **Solucion:** `SPEC.md` deja Fase 13 como verificacion de aplazamiento, marca dependencias/protocolo Wear como v2 y elimina la instruccion de crear estructura Wear en Fase 1.
- **Prevencion:** cuando una fase se difiere, limpiar tabla, detalle de fase, dependencias y arbol de modulos en la misma sesion.
- **Fecha resolucion:** 2026-05-24

---

### BUG-019 - Drive upload usaba multipart form-data
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-24
- **Fase:** 12
- **Severidad:** Alta
- **Sintoma:** el backup a Drive compilaba, pero el endpoint `uploadType=multipart` podia rechazar la subida porque el cuerpo era `multipart/form-data`.
- **Causa raiz:** se uso `@Multipart` de Retrofit, pensado para formularios, y se asumio que equivalia al multipart de Drive.
- **Solucion:** `DriveApiService` recibe un `RequestBody` `multipart/related`; `RetrofitDriveBackupService` construye metadata JSON primero y binario cifrado despues.
- **Prevencion:** test unitario `Drive upload body uses multipart related with metadata before encrypted media`.
- **Fecha resolucion:** 2026-05-24

---

### BUG-018 - Auto-backup repetia subidas por last_backup_at
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-24
- **Fase:** 12
- **Severidad:** Media
- **Sintoma:** el worker podia subir un backup diario aunque el usuario no cambiara datos, porque el backup anterior actualizaba `app_settings.last_backup_at`.
- **Causa raiz:** el hash estable incluia una columna que muta como efecto lateral del propio backup.
- **Solucion:** la canonicalizacion del snapshot para hash ignora `last_backup_at` y mantiene el resto de settings.
- **Prevencion:** test unitario `backup change hash canonicalization ignores last backup timestamp only`.
- **Fecha resolucion:** 2026-05-24

---

### BUG-017 - Accion Drive pendiente se perdia al volver del consentimiento
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-24
- **Fase:** 12
- **Severidad:** Media
- **Sintoma:** tras autorizar Drive, la app podia no ejecutar listar/backup/restore si la Activity se recreaba durante el flujo de consentimiento.
- **Causa raiz:** `BackupRestoreRoute` guardaba la accion pendiente en `remember`, que no sobrevive recreacion.
- **Solucion:** la accion pendiente se guarda como clave `rememberSaveable` y se reconstruye al recibir el resultado de AuthorizationClient.
- **Prevencion:** no guardar acciones pendientes de Activity Result en estado no saveable.
- **Fecha resolucion:** 2026-05-24

---

### BUG-016 - Health Connect no soporta porcentaje de agua corporal
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-24
- **Fase:** 10
- **Severidad:** Alta
- **Sintoma:** el spec marcaba `% agua corporal` como sincronizable con Health Connect, pero el API disponible es `BodyWaterMassRecord`, expresado como masa, no porcentaje.
- **Causa raiz:** se mezclo la metrica comun de basculas inteligentes (`water_percent`) con el contrato real de Health Connect.
- **Solucion:** se anade `body_water_mass_kg` con migracion Room v1->v2; `% agua corporal` queda manual y la exportacion Health Connect usa masa de agua corporal.
- **Prevencion:** tests de mapper para `BodyWaterMassRecord` y actualizacion de `SPEC.md`/`DOCS_TECNICA.md` para separar porcentaje vs masa.
- **Fecha resolucion:** 2026-05-24

---

### BUG-015 - Dashboard etiquetaba minimo cardiaco como frecuencia en reposo
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-24
- **Fase:** 8
- **Severidad:** Media
- **Sintoma:** el dashboard llamaba "frecuencia cardiaca en reposo" a un minimo diario calculado desde muestras genericas de pulso.
- **Causa raiz:** la DB v1 tiene `hc_heart_rate_samples`, pero no una cache dedicada para `RestingHeartRateRecord`.
- **Solucion:** el widget queda etiquetado como minimo diario de frecuencia cardiaca hasta que Fase 10 anada la lectura/caché real de reposo.
- **Prevencion:** no usar nombres clinicos o fisiologicos si el dato importado no tiene esa semantica exacta.
- **Fecha resolucion:** 2026-05-24

---

### BUG-014 - Consistencia semanal contaba dias fuera del plan
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-24
- **Fase:** 8
- **Severidad:** Media
- **Sintoma:** la consistencia con plan podia contar entrenamientos hechos en dias no planificados y, ademas, el target semanal podia duplicar un dia al convertir `now - 7 dias` a fechas inclusivas.
- **Causa raiz:** se mezclo una ventana exacta en milisegundos con conteo inclusivo de calendario y el numerador no filtraba por `weekly_plan`.
- **Solucion:** la semana empieza el lunes local, el numerador con plan solo cuenta dias activos planificados y el target usa los dias calendario del periodo.
- **Prevencion:** tests unitarios `snapshot ignores active days outside weekly plan` y `snapshot does not count eight calendar days for weekly plan target`.
- **Fecha resolucion:** 2026-05-24

---

### BUG-013 - Cardio aparecia duplicado como fuerza en Progreso
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-24
- **Fase:** 7
- **Severidad:** Alta
- **Sintoma:** una sesion de cardio completada podia aparecer dos veces en Progreso: como cardio real y como fuerza vacia con `0.0 kg`.
- **Causa raiz:** `workout_sessions` es cabecera comun para fuerza/cardio, pero `WorkoutRepository.sessions()` leia todas las filas sin filtrar `type = 'STRENGTH'`.
- **Solucion:** `WorkoutDao` expone queries especificas de fuerza, `RoomWorkoutRepository` usa solo esas queries y `ProgressUseCase` ignora defensivamente sesiones sin sets completados.
- **Prevencion:** test de regresion en `ProgressUseCaseTest` con una cabecera sin sets para evitar duplicados en historial.
- **Fecha resolucion:** 2026-05-24

### BUG-012 - Fallo interno del FGS cardio podia borrar el estado de error
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-23
- **Fase:** 6
- **Severidad:** Media
- **Sintoma:** si `CardioForegroundService` fallaba dentro de `startForeground`, el servicio podia destruirse y resetear el registry antes de que la UI mostrara el error.
- **Causa raiz:** `onDestroy()` llamaba siempre a `stopTracking()` y este limpiaba `CardioTrackerRegistry`.
- **Solucion:** `stopTracking(clearState)` conserva el estado `failed=true` cuando el servicio muere por fallo interno y solo limpia en parada normal.
- **Prevencion:** revisar errores asincronos de cada foreground service, no solo excepciones en el punto de arranque.
- **Fecha resolucion:** 2026-05-23

### BUG-011 - Cardio GPS denegado podia degradar mal a una sesion sin ruta
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-23
- **Fase:** 6
- **Severidad:** Alta
- **Sintoma:** un tipo de cardio con GPS podia arrancar sin permiso de ubicacion y acabar como sesion sin puntos, confundiendo GPS real con entrada manual.
- **Causa raiz:** la primera implementacion del servicio comprobaba permisos internamente, pero la UI no tenia flujo explicito para denegacion.
- **Solucion:** `ActiveCardio` solicita `ACCESS_FINE_LOCATION`; si se deniega, muestra aviso y habilita distancia/velocidad manual. El repositorio marca `source` como `GPS` solo si existe ruta.
- **Prevencion:** cada permiso opcional debe tener estado de UI y degradacion de datos explicita.
- **Fecha resolucion:** 2026-05-23

### BUG-010 - Historial de cardio ordenado por UUID
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-23
- **Fase:** 6
- **Severidad:** Media
- **Sintoma:** `CardioDao.getCardioSessions()` devolvia sesiones ordenadas por `id DESC`, que no representa cronologia.
- **Causa raiz:** se uso un campo UUID como atajo de orden.
- **Solucion:** la query une `cardio_sessions` con `workout_sessions` y ordena por `workout_sessions.start_time DESC`.
- **Prevencion:** los historiales se ordenan por timestamps, nunca por identificadores aleatorios.
- **Fecha resolucion:** 2026-05-23

### BUG-009 - Ultimo set no navegaba automaticamente al resumen
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-23
- **Fase:** 5
- **Severidad:** Media
- **Sintoma:** completar el ultimo set no cerraba la sesion automaticamente; el usuario tenia que pulsar `Finalizar`.
- **Causa raiz:** `onSetCompleted` solo guardaba el set y arrancaba descanso, sin comprobar si toda la sesion estaba completada.
- **Solucion:** `ActiveWorkoutViewModel` completa la sesion al detectar todos los sets completados y expone `completedSessionId` para navegar al resumen.
- **Prevencion:** revisar flujos automaticos del spec, no solo botones manuales.
- **Fecha resolucion:** 2026-05-23

### BUG-008 - Rest timer ignoraba ajustes y no liberaba audio
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-23
- **Fase:** 5
- **Severidad:** Baja
- **Sintoma:** el descanso siempre vibraba y sonaba, aunque `app_settings` permite desactivar sonido/vibracion; `ToneGenerator` no se liberaba.
- **Causa raiz:** la UI del rest timer no tenia contrato de settings y creaba `ToneGenerator` inline.
- **Solucion:** anadido `WorkoutSettingsRepository`, lectura de `rest_sound_enabled`/`rest_vibration_enabled`, y liberacion de `ToneGenerator`.
- **Prevencion:** cualquier feature que ya tenga settings en schema debe leerlos desde dominio antes de usar defaults.
- **Fecha resolucion:** 2026-05-23

### BUG-007 - Orden de ejercicios durante sesion se perdia al editar sets
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-23
- **Fase:** 5
- **Severidad:** Media
- **Sintoma:** reordenar ejercicios en el bottom sheet se revertia al marcar o editar un set.
- **Causa raiz:** `reloadSession()` reconstruia la sesion desde Room usando el orden de la rutina original.
- **Solucion:** `ActiveWorkoutViewModel` conserva `exerciseOrder` y lo reaplica tras cada reload de sesion.
- **Prevencion:** test/revision de interacciones combinadas: reordenar + editar + recargar.
- **Fecha resolucion:** 2026-05-23

### BUG-006 - Fallo interno del foreground service podia dejar la UI sin aviso
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-23
- **Fase:** 5
- **Severidad:** Alta
- **Sintoma:** si `startForeground()` fallaba dentro de `WorkoutForegroundService`, el servicio se paraba y la pantalla no mostraba aviso.
- **Causa raiz:** el ViewModel solo capturaba excepciones de `startForegroundService()`, no fallos posteriores dentro del servicio.
- **Solucion:** `ActiveWorkout` pide `ACTIVITY_RECOGNITION` antes de arrancar el FGS; el servicio publica estado `failed=true` y el ViewModel lo traduce a mensaje de UI.
- **Prevencion:** revisar errores asincronos de servicios, no solo el punto de llamada.
- **Fecha resolucion:** 2026-05-23

---

### BUG-005 - Records personales duplicados dentro de la misma sesion
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-23
- **Fase:** 5
- **Severidad:** Media
- **Sintoma:** dos sets completados con el mismo peso, ambos por encima del maximo historico, podian contarse como dos records personales.
- **Causa raiz:** `CompleteWorkoutSessionUseCase` comparaba cada set solo contra el maximo previo a la sesion, no contra el maximo ya visto dentro de la sesion actual.
- **Solucion:** el cierre de sesion mantiene `maxSeenByExercise` y solo marca nuevo PR cuando el peso supera ese maximo acumulado.
- **Prevencion:** test unitario `complete session calculates duration volume completed sets and personal records`.
- **Fecha resolucion:** 2026-05-23

---

### BUG-004 - Seed data de ejercicios ignoraba locale EN
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-23
- **Fase:** 4
- **Severidad:** Media
- **Sintoma:** en locale ingles la biblioteca mostraba nombres seed en espanol aunque la DB tenia columnas `name_en`.
- **Causa raiz:** los repositorios de ejercicios/rutinas mapeaban siempre `nameEs`/`descriptionEs`.
- **Solucion:** `RoomExerciseRepository` y `RoomRoutineRepository` seleccionan `nameEn`/`descriptionEn` cuando el locale actual es `en`.
- **Prevencion:** revision de i18n en cada pantalla con seed data o contenido predefinido.
- **Fecha resolucion:** 2026-05-23

### BUG-003 - Reordenamiento de rutinas no tenia drag-and-drop real
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-23
- **Fase:** 4
- **Severidad:** Media
- **Sintoma:** el constructor de rutinas permitia reordenar solo con botones subir/bajar, pero el spec exige drag-and-drop.
- **Causa raiz:** se intento cerrar la fase con una alternativa funcional pero incompleta respecto al spec.
- **Solucion:** `DraftExerciseCard` acepta gesto vertical de drag para mover ejercicios y conserva botones accesibles como fallback.
- **Prevencion:** revision de spec bloqueante antes de cerrar cada fase; no rebajar requisitos funcionales en docs.
- **Fecha resolucion:** 2026-05-23

### BUG-002 - Edicion de rutinas podia convertirse en duplicado
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-23
- **Fase:** 4
- **Severidad:** Media
- **Sintoma:** la primera implementacion de Fase 4 creaba rutinas nuevas sin contrato claro de actualizacion por id; eso habria duplicado una rutina al editarla.
- **Causa raiz:** el use case generaba id por defecto antes de los argumentos de negocio y la UI todavia no tenia modo de edicion real.
- **Solucion:** `createOrUpdateRoutine` y `createOrUpdateCustomExercise` aceptan `id` explicito al final, la UI rellena formularios de edicion y los repositorios preservan `created_at`.
- **Prevencion:** tests unitarios de actualizacion por id en `RoutineUseCaseTest` y `ExerciseUseCaseTest`.
- **Fecha resolucion:** 2026-05-23

---

### BUG-001 - Opt-in de biometria del onboarding no persistia
- **Estado:** Resuelto
- **Fecha deteccion:** 2026-05-23
- **Fase:** 3
- **Severidad:** Media
- **Sintoma:** activar biometria en el paso 8 del onboarding cambiaba solo el estado de UI; el valor no llegaba a `app_settings.biometrics_enabled`.
- **Causa raiz:** `OnboardingViewModel` persistia biometria durante el paso de contrasena, antes de que el usuario pudiera activar la opcion en el paso dedicado.
- **Solucion:** `finish()` llama a `LocalAuthUseCase.setBiometricUnlockEnabled()` con el valor final antes de marcar `onboarding_completed=true`.
- **Prevencion:** test unitario `finish persists biometric opt in after password step`.
- **Fecha resolucion:** 2026-05-23

---

*Atlas Peak - BUGS.md*
