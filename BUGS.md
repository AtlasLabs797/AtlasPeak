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
