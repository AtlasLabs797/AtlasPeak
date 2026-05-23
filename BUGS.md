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
