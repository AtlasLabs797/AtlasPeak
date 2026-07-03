# Atlas Peak — Sistema de diseño "Monochrome Instrument"

> Especificación de implementación. Reinvención total: OLED puro, **el blanco es el único acento**, la jerarquía nace del **contraste + retículas de líneas finas**, nunca del color. Los números se leen como un panel de telemetría. Mobile-first (390 × 844), dark-first con tema claro por intercambio de tokens.

---

## 1. Principios

1. **Monocromo absoluto.** Cinco grados de gris + tinta blanca. El color se reserva **exclusivamente** para riesgo/destructivo.
2. **La señal es brillo, no tono.** Un dato positivo o activo se destaca aumentando luminancia/peso, no cambiando de color.
3. **Separación por contraste y hairline.** Sin sombras pesadas; las superficies se distinguen por borde de 1px y fondo.
4. **Telemetría.** Toda cifra usa fuente mono con figuras tabulares: no hay reflujo al actualizarse en vivo.
5. **Movimiento sólo funcional.** Cronómetros, conteos, trazos de gráfica, pulso "en vivo". Nada decorativo.

---

## 2. Tokens — Color

### 2.1 Tema oscuro (por defecto)

```css
:root {
  /* Grounds & surfaces */
  --ground:        #0A0A0B; /* fondo de app / OLED */
  --surface:       #0E0E10; /* card base */
  --surface-2:     #131316; /* card elevada / gradiente top */
  --surface-3:     #1A1A1F; /* hero / sheet top */

  /* Texto */
  --ink:           #FAFAFA; /* primario + acento */
  --ink-2:         #9D9DA6; /* secundario */
  --ink-3:         #67676E; /* terciario / overlines / captions */
  --ink-4:         #5E5E66; /* iconos inactivos */
  --on-accent:     #0A0A0B; /* texto/icono sobre relleno blanco */

  /* Hairlines (borde por contraste) */
  --line-1:        rgba(255,255,255,0.08);
  --line-2:        rgba(255,255,255,0.10);
  --line-3:        rgba(255,255,255,0.12);
  --line-strong:   rgba(255,255,255,0.16);

  /* Fills sutiles (estados) */
  --fill-soft:     rgba(255,255,255,0.05); /* track segmentado / fondo inactivo */
  --fill-active:   rgba(255,255,255,0.10); /* tab activa (pill) / hover */

  /* Datos */
  --spark:         rgba(255,255,255,0.55); /* sparklines secundarias */
  --grid:          rgba(255,255,255,0.07); /* líneas guía de gráfica */
  --ring-track:    rgba(255,255,255,0.09); /* track de anillo */

  /* Semántico — ÚNICO color del sistema, sólo riesgo/destructivo */
  --risk:          #FF5A4D;
}
```

### 2.2 Tema claro (intercambio de tokens)

```css
[data-theme="light"] {
  --ground:        #FAFAF8;
  --surface:       #FFFFFF;
  --surface-2:     #F4F3EF;
  --surface-3:     #EFEEE9;

  --ink:           #0A0A0B; /* primario + acento (botones negros) */
  --ink-2:         #5A5A60;
  --ink-3:         #8A8A90;
  --ink-4:         #A8A8AE;
  --on-accent:     #FAFAFA;

  --line-1:        rgba(10,10,11,0.08);
  --line-2:        rgba(10,10,11,0.12);
  --line-3:        rgba(10,10,11,0.16);
  --line-strong:   rgba(10,10,11,0.22);

  --fill-soft:     rgba(10,10,11,0.04);
  --fill-active:   rgba(10,10,11,0.08);

  --spark:         rgba(10,10,11,0.55);
  --grid:          rgba(10,10,11,0.08);
  --ring-track:    rgba(10,10,11,0.10);

  --risk:          #E0473B;
}
```

> El acento es siempre `--ink`: en oscuro el relleno de acción es **blanco con texto negro**; en claro es **negro con texto blanco**. Nunca se introduce un color de marca.

---

## 3. Tokens — Tipografía

```css
:root {
  --font-ui:   "Space Grotesk", system-ui, sans-serif; /* UI, títulos, copy */
  --font-data: "JetBrains Mono", ui-monospace, monospace; /* TODA cifra/métrica */
  --font-icon: "Material Symbols Outlined";

  /* pesos */
  --w-reg: 400; --w-med: 500; --w-semi: 600; --w-bold: 700;
}
/* Las cifras mono ya son tabulares; forzar si hace falta: */
.data { font-variant-numeric: tabular-nums; font-feature-settings: "tnum" 1; }
```

### Escala de tipo

| Rol | Familia / peso | Tamaño | Tracking | Uso |
|---|---|---|---|---|
| Display editorial | data / 700 | **104px** | −5px | número-héroe de la pantalla principal |
| Display L | data / 700 | 60px | −3px | métrica héroe de stats |
| Display M | data / 700 | 52px | −2px | carga/volumen destacado |
| Métrica | data / 700 | 23–38px | −0.5…−1.5px | valores en tarjetas |
| Dato en línea | data / 500 | 16–18px | 0 | filas de tabla (kg, reps) |
| H1 pantalla | ui / 600 | 30px | −0.8px | "Tu evolución" |
| H2 | ui / 600 | 21–22px | −0.4px | nombre de sesión |
| Título | ui / 600 | 17–18px | −0.3px | título de tarjeta/fila |
| Body | ui / 400 | 15px | 0 | texto de apoyo |
| Body sm | ui / 400 | 13px | 0 | metadatos |
| Overline / label | data / 500 | 10–11px | **1.5–2px**, UPPER | etiquetas de sección, ej. `CARGA SEMANAL · MIN` |
| Caption / eje | data / 500 | 9–10px | 1px | ejes de gráfica, días |

**Regla de oro:** texto en `--font-ui`, **cualquier número** en `--font-data`. Las unidades (`kg`, `min`, `bpm`) van en `--ink-3`, tamaño pequeño, alineadas a la base del número.

---

## 4. Tokens — Espaciado, radio, sombra

```css
:root {
  /* Espaciado (escala 4) */
  --s-1: 4px; --s-2: 8px; --s-3: 12px; --s-4: 16px; --s-5: 20px; --s-6: 24px; --s-8: 32px;

  --pad-screen: 24px;   /* 22–26 según pantalla */
  --pad-card:   16px;   /* 14–18 */
  --gap-stack:  16px;   /* separación vertical entre bloques */

  /* Radio */
  --r-card:   22px;     /* tarjetas, sheets (20–24) */
  --r-btn:    16px;     /* botones (16–18) */
  --r-tile:   18px;     /* tiles, inputs, segmentos */
  --r-badge:  13px;     /* icon-badge cuadrado */
  --r-pill:   999px;    /* chips, nav flotante, dots, barras */
  --r-frame:  46px;     /* bisel del dispositivo */

  /* Elevación — plana. Sombra sólo en flotantes. */
  --elev-none:   none;
  --elev-nav:    0 8px 30px rgba(0,0,0,0.45);
  --elev-cta:    0 16px 40px -12px rgba(255,255,255,0.25); /* glow del botón blanco */
  --elev-device: 0 50px 90px -30px rgba(0,0,0,0.85);

  --touch-min:   44px;  /* objetivo táctil mínimo */
}
```

---

## 5. Tokens — Motion

```css
:root {
  --dur-instant: 80ms; --dur-fast: 140ms; --dur-medium: 220ms; --dur-slow: 320ms;
  --ease-standard:   cubic-bezier(.4,0,.2,1);
  --ease-decelerate: cubic-bezier(.05,.7,.1,1);   /* entradas, trazo de anillo */
  --ease-emphasized: cubic-bezier(.2,0,0,1);       /* selectores, tabs */
}

/* Pulso "EN VIVO" */
@keyframes apPulse { 0%,100%{opacity:1;transform:scale(1)} 50%{opacity:.35;transform:scale(.82)} }
/* Trazo de anillo / sparkline (animar stroke-dashoffset de len→off) */
@keyframes apDraw  { from{stroke-dashoffset:var(--len)} to{stroke-dashoffset:var(--off)} }

@media (prefers-reduced-motion: reduce){
  :root{ --dur-instant:0ms; --dur-fast:0ms; --dur-medium:0ms; --dur-slow:0ms; }
}
```

**Inventario de movimiento permitido:** pulso del punto "en vivo" · cuenta del cronómetro de sesión · cuenta atrás + vaciado del anillo de descanso · trazo de gráfica al entrar · press de botón `scale(.97)`. Todo lo demás es estático.

> Nota de implementación: si dibujas el anillo con `apDraw`, deja el estado **final** como valor base de `stroke-dashoffset` (no uses `fill-mode: both` con un `from` vacío) para que el anillo nunca aparezca vacío en el primer frame.

---

## 6. Componentes

### 6.1 Botón
- **Primario:** relleno `--ink`, texto `--on-accent`, `--r-btn`, alto 52–58px, `--font-ui` 600, icono inicial opcional (Material Symbols, `FILL 1`). Sombra `--elev-cta` sólo si es el CTA flotante inferior. Press → `transform: scale(.97)` (`--dur-instant`).
- **Secundario:** fondo transparente, borde `1px --line-strong`, texto `--ink`.
- **Fantasma / icono:** sin fondo ni borde, texto `--ink-2`.

```html
<button style="height:52px;border:none;border-radius:16px;background:var(--ink);
  color:var(--on-accent);font:600 16px var(--font-ui);display:inline-flex;
  align-items:center;justify-content:center;gap:8px;">
  <span class="ms" style="font-variation-settings:'FILL' 1;">play_arrow</span>Iniciar sesión
</button>
```

### 6.2 Tarjeta / Superficie
- Fondo `--surface` (o gradiente `linear-gradient(160deg, var(--surface-3), #0C0C0E)` para héroe), borde `1px --line-2/3`, `--r-card`, padding `--pad-card`, sombra `--elev-none`.

### 6.3 Icon-badge (acción)
- Cuadrado 44–46px, `--r-badge`, fondo `--ink`, icono `--on-accent` (`FILL 1`). Variante suave: fondo `--fill-active`, icono `--ink-2`.

### 6.4 Overline + Métrica
```html
<div style="font:500 11px var(--font-data);letter-spacing:2px;color:var(--ink-3);">VOLUMEN TOTAL · MES</div>
<div style="display:flex;align-items:flex-end;gap:8px;">
  <span class="data" style="font:700 60px var(--font-data);letter-spacing:-3px;">34 120</span>
  <span style="font-size:15px;color:var(--ink-3);margin-bottom:6px;">kg</span>
</div>
```

### 6.5 Delta chip
- Pill hairline (`1px --line-strong`), `--font-data` 12px, con icono `arrow_upward`/`arrow_downward`. **Sin color**: la dirección la da la flecha. (Sólo un dato dañino usaría `--risk`.)

### 6.6 Sparkline
- `<path>` blanco `stroke-width:1.6–2.2`, `stroke-linecap/linejoin:round`. Relleno de área opcional con gradiente vertical `rgba(255,255,255,0.16) → 0`. Punto final = `<circle r=3.5 fill=--ink>`.

### 6.7 Gráfica grande (área + línea)
- `viewBox` con `preserveAspectRatio="none"`. Capas: línea guía media `--grid` (dash `2 5`), área (gradiente), línea (`--ink` 2.2px), punto final. Ejes con captions mono (`SEM 1…4`).

### 6.8 Anillo / gauge radial
- Dos `<circle>` concéntricos: track `--ring-track` + progreso `--ink`, `stroke-linecap:round`, `stroke-dasharray = 2πr`, `transform: rotate(-90deg)` para iniciar arriba. Centro: número `--font-data` 700 + label mono. Anillo secundario interior a `rgba(255,255,255,0.45)`.
- `offset = circ · (1 − porcentaje)`.

### 6.9 Gráfica de barras
- Fila flex, barras `flex:1`, `border-radius:6px 6px 0 0`, altura = `%` del máximo. Inactivas `rgba(255,255,255,0.22)`; **pico/seleccionada** `--ink`. Etiquetas de día mono debajo (activa en `--ink`, resto `--ink-3`).

### 6.10 Control segmentado (periodo)
- Track `--fill-soft` + borde `--line-1`, `--r-tile`, padding 4px. Segmento activo: fondo `--ink`, texto `--on-accent`, `--font-data` 700; inactivos `--ink-2`.

### 6.11 Chip / tag
- Pill, borde `1px --line-strong`, `--font-data` 10–11px UPPER, icono inicial opcional (`trophy`, `sync`).

### 6.12 Tabla de series (telemetría)
- Grid `38px 1fr 1fr 46px` → `SET · KG · REPS · ✓`. Cabecera mono `--ink-3`. Filas separadas por `--line-1`.
- **Serie completada:** valores `--ink`, check = cuadro `--ink` 26px `r8` con `check` negro `FILL 1`.
- **Serie activa:** fondo `--fill-active`, `box-shadow: inset 3px 0 0 var(--ink)` (barra izquierda), valores 700 más grandes, check = cuadro vacío con borde `1.5px rgba(255,255,255,.35)`.
- Fila "Añadir serie": `add` + texto `--ink-2`.

### 6.13 Navegación inferior (2 variantes)
- **Barra acoplada:** `left/right:14px; bottom:14px`, alto 64, `--r-card`, fondo `rgba(14,14,16,.86)` + `backdrop-filter: blur(18px)`, borde `--line-2`. Tab activa = cuadro `--fill-active` `r14` con icono `--ink` (`FILL 1`); resto `--ink-4`.
- **Pill flotante:** centrada, alto 60, `--r-pill`, mismo blur/borde. Tab activa = **círculo blanco** 44px con icono negro (`FILL 1`).
- 5 tabs **sólo icono** (`home/space_dashboard`, `exercise`, `monitoring`, `cardiology`, `person`) con `aria-label`.

### 6.14 Cronómetro "en vivo"
- Pill hairline: punto 7px `--ink` con `animation: apPulse 1.6s infinite` + tiempo `--font-data` 500 15px.

### 6.15 Barra de progreso (hairline)
- Track `--fill-active` 3px `--r-pill`; relleno `--ink`.

### 6.16 Chrome de dispositivo
- Bisel 390×844, `--r-frame`, fondo `--ground`, borde `--line-3`, `--elev-device`.
- **Status bar** 50px: hora `--font-data` 14px + iconos `signal_cellular_alt / wifi / battery_full`.
- **Home indicator:** 130×4px `--r-pill`, `rgba(255,255,255,.2)`, centrado abajo.

---

## 7. Iconografía
- **Material Symbols Outlined**, eje `wght 400` base; usar `FILL 1` para estados activos/acción. Tamaños: 17–18 (status), 20–24 (UI), 26+ (acción). Cargar con `display:block` para evitar texto-ligadura durante la carga.

---

## 8. Anatomía de pantallas

### 8.1 Inicio — Editorial *(pantalla principal elegida)*
Aire generoso, **una** métrica dominante.
1. Header: overline fecha + saludo (`H2` a dos líneas) + icono notificaciones.
2. Héroe: overline `CARGA SEMANAL` → número **104px** + unidad + delta `vs. semana previa` + sparkline ancha.
3. Carrusel horizontal de tarjetas de métrica (Volumen / Pasos / FC) — peek de la siguiente.
4. Tarjeta de sesión de hoy (gradiente) con CTA blanco a ancho completo.
5. Nav: **pill flotante**, tab `home` activa.

### 8.2 Progreso — Stats
Orientada a gráfica y evolución (scroll vertical interno).
1. Header `Tu evolución` + icono `tune`.
2. Control segmentado de periodo (Sem/Mes/3M/Año).
3. Héroe **Volumen** (60px) + gráfica de área + ejes `SEM 1…4`.
4. **Evolución de fuerza** (1RM press de banca) con chip `RÉCORD` + línea ascendente.
5. **Carga semanal** en barras (pico en blanco) + días.
6. Lista **Récords recientes**: icono + nombre/fecha + valor mono + delta con flecha.
7. Nav: tab `monitoring` activa.

### 8.3 Sesión en vivo
1. App-bar: `expand_more` + `EN CURSO` / nombre + **cronómetro en vivo** (pill con pulso).
2. Progreso hairline `EJERCICIO 1/6` + volumen acumulado.
3. Tarjeta de ejercicio + **tabla de series** (telemetría) con fila activa marcada.
4. Panel de **descanso**: anillo de cuenta atrás en vivo + "Siguiente serie" + `+15s` / `Saltar`.
5. CTA inferior flotante **Completar serie** (con glow).

### Pantallas pendientes (mismo lenguaje)
**Cuerpo** (composición corporal: rejilla de tiles + gráfica de peso) · **Perfil** (avatar, ajustes en filas hairline, switch de tema). Reutilizan tarjeta, overline+métrica, filas con `chevron_right` y nav.

---

## 9. Checklist de implementación
- [ ] Cargar fuentes: Space Grotesk (400–700), JetBrains Mono (400–700), Material Symbols Outlined.
- [ ] Declarar tokens de `:root` y `[data-theme="light"]`; alternar tema por atributo.
- [ ] **Toda cifra** con `--font-data` + `tabular-nums`.
- [ ] Ningún color salvo `--risk` para destructivo; positivo = brillo + flecha.
- [ ] Bordes hairline en vez de sombras; sombra sólo en nav flotante, CTA y dispositivo.
- [ ] Objetivos táctiles ≥ 44px; nav inferior sólo icono con `aria-label`.
- [ ] Respetar `prefers-reduced-motion`.
- [ ] Anillos/sparklines: estado final como base del `stroke-dashoffset`.

---
*Atlas Peak · Monochrome Instrument — v1. Referencia visual viva: `Atlas Peak Redesign.dc.html`.*
