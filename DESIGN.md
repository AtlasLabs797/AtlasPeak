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

Dirección visual actual (2026-05-25): **premium performance lab**. Neutros con tinte
orgánico, fondo off-black/off-white, tarjetas por contraste de superficie y acento vivo
reservado para datos/acciones clave. Nada de UI genérica de gimnasio con rojo por todas
partes: el rojo/coral queda para marca puntual, errores y estados de riesgo.

| Token | Rol | Light (aprox.) | Dark (aprox.) |
|-------|-----|----------------|---------------|
| `primary` | Accent principal / accion clave | `#265C4B` | `#D7FF5F` |
| `onPrimary` | Texto sobre primary | `#FFFFFF` | `#172100` |
| `primaryContainer` | Seleccionados / estado activo suave | `#DFF5B0` | `#314600` |
| `onPrimaryContainer` | Texto sobre selected container | `#17210A` | `#E9FF9A` |
| `secondary` | Acciones secundarias / chips | `#5C6255` | `#C8CBBB` |
| `surface` | Fondo de cards | `#FFFCF4` | `#151812` |
| `surfaceVariant` | Cards secundarias / inputs | `#E9E6DC` | `#23271F` |
| `background` | Fondo base | `#F6F4EE` | `#080A08` |
| `onBackground` / `onSurface` | Texto principal | `#171914` | `#EDEFE5` |
| `outline` | Bordes sutiles | `#C8C5BA` | `#464B40` |
| `error` | Errores | `#BA1A1A` | `#FFB4AB` |

**Regla:** `error` ≠ `primary`. El coral no compite con el acento principal; si aparece,
debe señalar riesgo, error o una accion destructiva.

**Semánticos de progreso** (gráficos, PRs, estados):
- Récord personal / éxito: verde `#22784E` (light) / `#91E6A4` (dark).
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
- Tokens en `theme/Motion.kt` (`AtlasMotion`):
  - `DurationInstant` 80ms · `DurationFast` 140ms · `DurationMedium` 220ms ·
    `DurationSlow` 320ms · `DurationDeliberate` 480ms.
  - `StandardEasing` (`FastOutSlowInEasing`) · `EmphasizedEasing` (0.2, 0, 0, 1) ·
    `DecelerateEasing` (0.05, 0.7, 0.1, 1) · `AccelerateEasing` (0.3, 0, 0.8, 0.15).
- Curvas Material Motion: `StandardEasing` por defecto; `LinearEasing` solo para
  progreso continuo (cronómetros, anillos).
- Transiciones entre pantallas: shared axis / fade-through coherente en todo el NavHost.
- El **rest timer** y los cronómetros de sesión se actualizan a 1Hz visualmente; no animes
  cada milisegundo (gasta batería y recompone de más).
- Respeta "reducir animaciones" del sistema cuando esté activo.

---

## 6. Componentes recurrentes (contratos de UI)

Todos los componentes "atlas" viven en `presentation/component/` y son la fuente única
de cada patrón. **No reimplementar Card/Button/TextField sueltos**: usa los wrappers.

- **AtlasPrimaryButton / AtlasSecondaryButton / AtlasGhostButton:** botones de la app.
  Primary = `Button` con primary container, altura mínima 56dp, shape 16dp. Secondary =
  `OutlinedButton` con borde outline. Ghost = `TextButton` para acciones terciarias
  (saltar, omitir). Todos aceptan `leadingIcon` opcional + `iconContentDescription`.
- **AtlasTextField:** wrapper de `OutlinedTextField` con shape large, colores
  Atlas-consistentes y altura mínima 60dp. Usar siempre que no haya necesidad explícita
  de `visualTransformation` o `leadingIcon` complejos (en esos casos cae a `OutlinedTextField`
  estilizado).
- **AtlasSlider:** label + valor en `titleMedium` color `primary` a la derecha + track
  con primary. Para configuraciones numéricas (offsets, segundos, intensidad).
- **PremiumCard / PremiumIconBadge / PremiumBackground:** contenedor neutro con borde
  outline sutil, badge cuadrado de 44dp por defecto (configurable via `size`), y fondo
  con gradiente radial para todas las rutas raíz.
- **PeriodSelector:** segmented pill animado (`AtlasMotion.DurationMedium` +
  `EmphasizedEasing`). `semana / mes / 3 meses / año / año hasta hoy`. Componente único
  reutilizado en todos los widgets y gráficos. No reimplementar por pantalla.
- **MetricValue:** número grande tabular (`headlineMedium` o `displaySmall` si
  `emphasized = true`) + unidad pequeña en `labelLarge` `onSurfaceVariant`. Usar para
  todo headline numérico de dashboard / progreso / cuerpo.
- **SectionHeader:** overline (`labelMedium` `onSurfaceVariant`) + título
  (`headlineSmall`) + slot trailing opcional. Para abrir secciones de pantalla.
- **EmptyState:** icon badge 72dp + título + body + CTA opcional, centrados.
- **StepDots:** indicador de pasos animado (ancho + color animados con
  `EmphasizedEasing`). Onboarding y wizards.
- **Brushes** (`AtlasBrushes` en `theme/Brushes.kt`): `heroGradient`,
  `subtleSurface`, `spotlight`, `accentBorder`. Para hero cards y fondos premium.
- **Chart (Vico):** eje Y izq = peso (kg), eje X = fecha, eje Y der opcional = reps.
  Interactivo: tap muestra el valor exacto del punto. Estilo coherente (mismo grosor de
  línea, mismos colores semánticos) en toda la app.
- **RestTimerOverlay:** anillo de progreso doble (track gris + progreso primary, stroke
  12dp, diámetro 208dp) + número en `displayMedium` al centro + botón Skip
  `AtlasSecondaryButton`. Mismo componente en teléfono y (futuro) reloj.
- **SetRow:** peso (weight 1.4) > reps (weight 1.0) editables, checkbox de completado,
  borde primary cuando completed. Estado claro completado/pendiente.

---

## 7. Navegación

- **Bottom navigation de 5 tabs**, siempre visible salvo en pantallas fullscreen:
  `HOME · ENTRENAR · PROGRESO · CUERPO · PERFIL`.
- En v1 la bottom navigation es **icon-only**: no muestra nombres bajo los iconos.
  Cada tab conserva `contentDescription` localizado para TalkBack y el estado activo debe
  quedar claro por color/indicador, no por texto visible.
- **Fullscreen (ocultan bottom nav):** `ActiveWorkout`, `WorkoutComplete`,
  `ActiveCardio`, `CardioComplete` y Onboarding.
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
- Soporte TalkBack básico en pantallas críticas (Onboarding, ActiveWorkout, Backup).
- Targets ≥48dp. Texto escalable (no fijar tamaños en px; usar `sp`).
- Estados no comunicados solo por color.

---

## 10. Internacionalización (recordatorio de diseño)

El layout debe sobrevivir a strings más largos en inglés/español. Nada de anchos fijos
calculados para un idioma. Probar ambas locales antes de cerrar cada pantalla.

---

*Atlas Peak — DESIGN.md — v1.0*
