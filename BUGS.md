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
