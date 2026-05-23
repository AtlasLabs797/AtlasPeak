# DESIGN.md — Sistema de diseño de Atlas Peak

> Reglas vinculantes de diseño visual y de interacción. Si un mockup o una pantalla
> contradice este archivo, gana este archivo (salvo que el usuario diga lo contrario).
> Todo valor de diseño se implementa via **design tokens** en el tema Compose, nunca
> como literales sueltos repartidos por el código.

---

## 1. Principios

1. **Local-first, sin ruido.** La app es una herramienta personal, no una red social.
   Nada de badges falsos, gamificación agresiva ni dark patterns.
2. **El dato es el protagonista.** Tipografía clara, jerarquía fuerte, gráficos legibles.
   El cromo (bordes, sombras, decoración) se subordina al contenido.
3. **Pulgar primero.** Acciones principales al alcance del pulgar. Lo crítico no vive
   en la esquina superior.
4. **Movimiento con propósito.** Animaciones que comunican (transición de estado,
   continuidad espacial), no que adornan.
5. **Accesible por defecto.** Contraste AA mínimo, targets táctiles ≥48dp,
   `contentDescription` en todo icono sin texto.

---

## 2. Color (Material 3)

Implementar como `ColorScheme` de Material 3 con esquema claro y oscuro. **Dark/Light
automáticos** siguiendo el sistema, con override manual en ajustes (`SYSTEM/LIGHT/DARK`).

| Token | Rol | Light (aprox.) | Dark (aprox.) |
|-------|-----|----------------|---------------|
| `primary` | Accent principal | `#E53935` (rojo) | `#FF6B66` |
| `onPrimary` | Texto sobre primary | `#FFFFFF` | `#3A0907` |
| `secondary` | Acciones secundarias / chips | neutro cálido | neutro cálido |
| `surface` | Fondo de cards | `#FAFAFA` | `#1B1B1B` |
| `surfaceVariant` | Cards secundarias / inputs | `#EFEFEF` | `#262626` |
| `background` | Fondo base | `#FFFFFF` | `#121212` |
| `onBackground` / `onSurface` | Texto principal | `#1A1A1A` | `#ECECEC` |
| `outline` | Bordes sutiles | `#D6D6D6` | `#3A3A3A` |
| `error` | Errores | `#B3261E` | `#F2B8B5` |

**Regla:** `error` ≠ `primary`. El rojo de marca y el rojo de error son distintos para
que un error no se confunda con un acento de UI.

**Semánticos de progreso** (gráficos, PRs, estados):
- Récord personal / éxito: verde `#2E7D32` (light) / `#7FD89B` (dark).
- Neutro / planificado: usar `onSurfaceVariant`.
- Nunca codifiques información **solo** por color (añade icono o texto — daltonismo).

Contraste: todo texto ≥ **4.5:1** sobre su fondo (AA). Verificar en ambos modos.

---

## 3. Tipografía

| Uso | Fuente | Pesos |
|-----|--------|-------|
| Títulos, headings, números destacados | **Poppins** | 600, 700 |
| Cuerpo, labels, datos en tabla | **Inter** | 400, 500 |

Cargar via `androidx.compose.ui.text.googlefonts` (Downloadable Fonts) o assets locales
en `res/font/`. Mapear a la escala tipográfica de Material 3:

- `displayLarge/Medium/Small` → Poppins 700
- `headlineLarge/Medium/Small` → Poppins 600
- `titleLarge/Medium/Small` → Poppins 600
- `bodyLarge/Medium/Small` → Inter 400
- `labelLarge/Medium/Small` → Inter 500

Números de métricas (peso, volumen, distancia): usar variante tabular si está disponible
para que no "bailen" al actualizarse en vivo.

---

## 4. Forma y espaciado

- **Border radius universal: 16dp** (cards, botones, textfields, bottom sheets).
  Excepciones: chips/badges 50% (pill), FAB según Material 3.
- **Escala de espaciado (única fuente):** `4 / 8 / 12 / 16 / 24 / 32 dp`.
  - Padding de pantalla: `16dp`.
  - Padding interno de card: `16dp`.
  - Gap entre cards: `12dp`.
  - Compacto (chips, listas densas): `8dp`.
- **Elevación:** mínima. Cards en `surface` con `outline` sutil antes que sombra dura.
- **Touch target mínimo: 48×48dp** aunque el icono visual sea menor.

Define un `Spacing` object o usa `Dp` constantes en el tema. Prohibido `padding(13.dp)`
inventado pantalla a pantalla.

---

## 5. Movimiento

- Duración estándar: **200–350ms**. Micro-interacciones (checkbox, toggle): 120–180ms.
- Curvas Material Motion: `FastOutSlowInEasing` por defecto; `LinearEasing` solo para
  progreso continuo (cronómetros, anillos).
- Transiciones entre pantallas: shared axis / fade-through coherente en todo el NavHost.
- El **rest timer** y los cronómetros de sesión se actualizan a 1Hz visualmente; no animes
  cada milisegundo (gasta batería y recompone de más).
- Respeta "reducir animaciones" del sistema cuando esté activo.

---

## 6. Componentes recurrentes (contratos de UI)

- **MetricCard:** título (label), valor grande (Poppins), unidad, mini-gráfico opcional,
  selector de período. Usada en Dashboard y Composición Corporal.
- **PeriodSelector:** `semana / mes / 3 meses / año / año hasta hoy`. Componente único
  reutilizado en todos los widgets y gráficos. No reimplementar por pantalla.
- **Chart (Vico):** eje Y izq = peso (kg), eje X = fecha, eje Y der opcional = reps.
  Interactivo: tap muestra el valor exacto del punto. Estilo coherente (mismo grosor de
  línea, mismos colores semánticos) en toda la app.
- **RestTimerOverlay:** anillo de progreso circular + segundos al centro + botón Skip.
  Mismo componente en teléfono y (futuro) reloj.
- **SetRow:** reps planificadas, peso editable, checkbox de completado. Estado claro
  completado/pendiente.
- **EmptyState:** ilustración/icono + título + texto + CTA. Toda lista vacía tiene su
  empty state; nunca una pantalla en blanco.

---

## 7. Navegación

- **Bottom navigation de 5 tabs**, siempre visible salvo en pantallas fullscreen:
  `HOME · ENTRENAR · PROGRESO · CUERPO · PERFIL`.
- **Fullscreen (ocultan bottom nav):** `ActiveWorkout`, `WorkoutComplete`,
  `ActiveCardio`, `CardioComplete`, flujo de Auth y Onboarding.
- Iconografía: Material Icons Extended en v1 (custom icons → post-MVP).
- El tab activo se marca con `primary`; inactivos con `onSurfaceVariant`.

---

## 8. Estados, vacíos y errores

- **Loading:** skeletons o spinner discreto; nunca bloquear toda la pantalla si puedes
  mostrar contenido progresivo.
- **Error:** mensaje claro en lenguaje humano + acción de reintento. Nada de códigos
  crudos al usuario.
- **Vacío:** ver `EmptyState`. Sugiere la primera acción ("Crea tu primera rutina").
- **Destructivo:** borrar/restaurar backup pide confirmación explícita. El borrado real
  de ejercicios/rutinas es **soft delete** (`is_archived`), no DELETE físico.

---

## 9. Accesibilidad (mínimos de release)

- Contraste AA verificado en light y dark.
- `contentDescription` en todos los iconos sin texto.
- Soporte TalkBack básico en pantallas críticas (Login, Onboarding, ActiveWorkout).
- Targets ≥48dp. Texto escalable (no fijar tamaños en px; usar `sp`).
- Estados no comunicados solo por color.

---

## 10. Internacionalización (recordatorio de diseño)

El layout debe sobrevivir a strings más largos en inglés/español. Nada de anchos fijos
calculados para un idioma. Probar ambas locales antes de cerrar cada pantalla.

---

*Atlas Peak — DESIGN.md — v1.0*
