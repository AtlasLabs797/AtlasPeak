# Design Skills Catalog

## 1) Alcance

Este documento resume las skills de diseño que instalaste en `C:\Proyectos\Skills\Diseno`, qué hace cada una, qué comandos tienes y cómo funciona cada comando.

## 2) Inventario de Skills (por paquete)

### 2.1 `emilkowalski/skill`

- `emil-design-eng`: filosofía de Emil Kowalski para pulido visual, composición de componentes y animación con criterio.

### 2.2 `pbakaus/impeccable`

- `adapt`: adapta UI a breakpoints/dispositivos/contextos.
- `animate`: añade motion y microinteracciones con intención.
- `audit`: auditoría técnica UI (a11y, performance, theming, responsive, anti-patrones).
- `bolder`: sube impacto visual sin romper usabilidad.
- `clarify`: mejora microcopy, labels, errores e instrucciones UX.
- `colorize`: añade color estratégico a UI apagadas.
- `critique`: evaluación UX estructurada con hallazgos accionables.
- `delight`: añade personalidad y momentos memorables.
- `distill`: simplifica y elimina ruido visual.
- `harden`: endurece UI para producción (edge cases, vacíos, errores, overflow, i18n).
- `impeccable`: skill principal para construir interfaces de alta calidad.
- `layout`: corrige jerarquía, espaciado y ritmo visual.
- `optimize`: optimiza rendimiento visual (carga/render/animaciones).
- `overdrive`: implementaciones visuales ambiciosas (shaders, scroll, física).
- `polish`: pasada final de acabado y consistencia.
- `quieter`: baja agresividad visual manteniendo calidad.
- `shape`: define estrategia UX/UI antes de codificar.
- `typeset`: mejora tipografía (jerarquía, legibilidad, escala).

### 2.3 `Leonxlnx/taste-skill`

- `design-taste-frontend`: arquitectura UI/UX con reglas estrictas de calidad visual y técnica.
- `full-output-enforcement`: evita respuestas truncadas; fuerza salidas completas.
- `gpt-taste`: dirección visual/motion avanzada con fuerte control de estructura.
- `high-end-visual-design`: guía de diseño “agencia premium”.
- `industrial-brutalist-ui`: estilo brutalista industrial (editorial/dashboards densos).
- `minimalist-ui`: estilo editorial minimalista sin efectos pesados.
- `redesign-existing-projects`: rediseña productos existentes a nivel premium sin romper funcionalidad.
- `stitch-design-taste`: genera `DESIGN.md` para reglas de diseño semánticas.

### 2.4 `nextlevelbuilder/ui-ux-pro-max-skill`

- `ui-ux-pro-max`: base de decisiones UI/UX (estilos, color, tipografía, UX patterns).
- `ckm-banner-design`: diseño de banners social/ads/web/print.
- `ckm-brand`: estrategia de marca, voz, consistencia y activos.
- `ckm-design`: skill integral (branding + diseño + presentaciones + assets).
- `ckm-design-system`: tokens y especificación de componentes.
- `ckm-slides`: presentaciones HTML estratégicas.
- `ckm-ui-styling`: estilado UI con enfoque accesible (componentes + theming).

## 3) Comandos Disponibles

## 3.1 Gestión de instalación de skills

- `npx skills add <owner/repo>`
  - Para qué sirve: descargar e instalar skills de un repo.
  - Cómo funciona: clona/fetch del repo, detecta skills y las instala en carpetas de agentes.

- `npx skills add <owner/repo> -y`
  - Para qué sirve: lo mismo, sin prompts interactivos.
  - Cómo funciona: instala automáticamente con confirmaciones implícitas.

- `npx skills add <owner/repo> -g`
  - Para qué sirve: instalación global (usuario).
  - Cómo funciona: escribe en rutas globales del agente, no solo en el proyecto actual.

## 3.2 CLI de UI Pro Max (`uipro`)

- `uipro init --ai <platform>`
  - Para qué sirve: instala la skill para un asistente concreto (`codex`, `claude`, `cursor`, etc.).
  - Cómo funciona: detecta plataforma, genera/copía archivos de skill y confirma carpetas creadas.

- `uipro init --ai <platform> --global`
  - Para qué sirve: instalación global de esa plataforma.
  - Cómo funciona: usa directorio home en vez de `cwd`.

- `uipro init --offline`
  - Para qué sirve: instalar sin descarga remota.
  - Cómo funciona: usa assets empaquetados locales.

- `uipro update`
  - Para qué sirve: actualizar a la última versión.
  - Cómo funciona: consulta release más reciente y relanza flujo de instalación.

- `uipro versions`
  - Para qué sirve: listar versiones publicadas.
  - Cómo funciona: consulta releases y marca la más reciente.

- `uipro uninstall`
  - Para qué sirve: desinstalar skill.
  - Cómo funciona: detecta carpeta(s) del agente y elimina `skills/ui-ux-pro-max`.

## 3.3 CLI de Impeccable

- `npx impeccable skills help`
  - Para qué sirve: ver ayuda/comandos disponibles.
  - Cómo funciona: imprime guía y lista de comandos del paquete.

- `npx impeccable skills install`
  - Para qué sirve: instalar skills de Impeccable.
  - Cómo funciona: usa `npx skills add pbakaus/impeccable` y configura prefijos si aplica.

- `npx impeccable skills update`
  - Para qué sirve: actualizar skills instaladas.
  - Cómo funciona: compara versión y reemplaza skills con la versión más nueva.

- `npx impeccable skills check`
  - Para qué sirve: comprobar si estás actualizado.
  - Cómo funciona: compara hash/versión local contra bundle remoto.

## 4) “Comandos” de uso dentro del asistente

Estas skills no son comandos shell directos; se activan por intención en tu prompt.

- Uso general: pedir explícitamente la skill por nombre, por ejemplo:
  - `Usa la skill impeccable para rediseñar este dashboard`
  - `Aplica typeset para mejorar legibilidad`
  - `Pasa audit y devuelve hallazgos P0-P3`

- Caso especial `impeccable`:
  - `impeccable craft`: diseño + implementación.
  - `impeccable teach`: define contexto de diseño del proyecto.
  - `impeccable extract`: extrae componentes/tokens reutilizables.

## 5) Nota de seguridad

`ui-ux-pro-max` fue endurecida localmente para modo “solo guía” (sin instalación/ejecución de comandos del sistema) en:

- `C:\Proyectos\Skills\Diseno\ui-ux-pro-max-skill\.agents\skills\ui-ux-pro-max\SKILL.md`

## 6) 21st SDK

Instalado en:

- `C:\Proyectos\Atlas Balance Dev\Skills\Diseno\21st-sdk`

Para que sirve:

- SDK para construir, desplegar e incrustar agentes de IA en una app.
- Incluye paquetes para definir agentes, UI React/Next.js, cliente Node, CLI y cliente Python.
- No es una skill de diseno. Es tooling de agentes; esta aqui como referencia/local install para el proyecto.

Paquetes principales:

- `packages/agent`: `@21st-sdk/agent`
- `packages/react`: `@21st-sdk/react`
- `packages/nextjs`: `@21st-sdk/nextjs`
- `packages/node`: `@21st-sdk/node`
- `packages/cli`: `@21st-sdk/cli`
- `packages/python-sdk`: `21st-sdk`

Comandos utiles desde la carpeta `21st-sdk`:

- `corepack pnpm install`
  - Instala dependencias con `pnpm@9.15.4`, que es el gestor declarado por el repo.

- `corepack pnpm --filter @21st-sdk/agent build`
- `corepack pnpm --filter @21st-sdk/node build`
- `corepack pnpm --filter @21st-sdk/react build`
- `corepack pnpm --filter @21st-sdk/nextjs build`
- `corepack pnpm --filter @21st-sdk/cli build`
  - Compilan los paquetes en orden.

Notas locales:

- `corepack pnpm build` falla en esta maquina porque Turbo no encuentra el binario global `pnpm`.
- `corepack enable` no se pudo aplicar sin permisos de administrador en `C:\Program Files\nodejs`.
- Se agrego `@types/node` como devDependency local de `packages/react` porque el build de tipos usa `require`.
