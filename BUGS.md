# BUGS.md — Registro de bugs

> Log de bugs detectados y su resolución. Trazabilidad: cada bug arreglado deja rastro
> aquí. **Formato obligatorio** abajo. Orden: más reciente arriba.
>
> Estados: `🔴 Abierto` · `🟡 En progreso` · `🟢 Resuelto` · `⚪ No reproducible / Descartado`

---

## Formato de entrada

```
### BUG-NNN — Título corto
- **Estado:** 🔴 / 🟡 / 🟢 / ⚪
- **Fecha detección:** AAAA-MM-DD
- **Fase:** N
- **Severidad:** Crítica / Alta / Media / Baja
- **Síntoma:** qué se observó (pasos para reproducir si aplica).
- **Causa raíz:** por qué pasaba realmente.
- **Solución:** qué se cambió (archivos / commits).
- **Prevención:** test añadido / regla para que no vuelva.
- **Fecha resolución:** AAAA-MM-DD
```

**Regla:** un bug no se cierra (🟢) sin "Causa raíz" y "Prevención" rellenadas. Apagar el
síntoma sin entender la causa no cuenta como resuelto.

---

## Convención de severidad

| Severidad | Criterio |
|-----------|----------|
| Crítica | Pérdida de datos, crash en flujo principal, fallo de cifrado/seguridad |
| Alta | Funcionalidad core rota, datos incorrectos mostrados |
| Media | Funcionalidad secundaria rota, workaround existe |
| Baja | Cosmético, edge case raro |

---

## Entradas

<!-- Añade nuevas entradas aquí arriba. Ejemplo de plantilla rellenada: -->

### BUG-000 — (plantilla de ejemplo, borrar al primer bug real)
- **Estado:** 🟢 Resuelto
- **Fecha detección:** 2026-05-23
- **Fase:** 0
- **Severidad:** Baja
- **Síntoma:** Entrada de ejemplo para mostrar el formato.
- **Causa raíz:** N/A — es la plantilla.
- **Solución:** N/A.
- **Prevención:** N/A.
- **Fecha resolución:** 2026-05-23

---

*Atlas Peak — BUGS.md*
