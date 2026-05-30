# shadcn/ui - uso correcto en proyectos

Esta carpeta contiene una copia de referencia del repo oficial:

`C:\Proyectos\Atlas Balance Dev\Skills\Diseno\shadcn-ui`

No instales shadcn/ui dentro de `Skills\Diseno` salvo que estes estudiando o actualizando la referencia. Para que una app lo use, shadcn debe inicializarse dentro del frontend real del proyecto, donde esta el `package.json`.

## Donde se instala

Correcto:

```txt
MiProyecto/frontend/
  package.json
  vite.config.ts
  src/
```

Incorrecto:

```txt
Skills/Diseno/
```

La carpeta `Skills\Diseno` es una biblioteca de referencia e instrucciones. No es una app React. Instalar ahi componentes UI no sirve para nada practico.

## Requisitos

El proyecto debe ser una app frontend compatible, normalmente:

- Vite + React
- Next.js
- React Router
- Astro
- Laravel con frontend configurado

Debe tener:

- `package.json`
- TypeScript recomendado
- alias de imports, por ejemplo `@ -> ./src`
- Tailwind CSS instalado o instalable

## Instalacion en un proyecto Vite + React

Entra primero al frontend real:

```powershell
cd "C:\Ruta\Del\Proyecto\frontend"
```

Si PowerShell bloquea `npm.ps1`, usa `npm.cmd`.

Instala Tailwind v4 para Vite si el proyecto aun no lo tiene:

```powershell
npm.cmd install -D tailwindcss @tailwindcss/vite
```

En `vite.config.ts`, agrega el plugin:

```ts
import { defineConfig } from "vite"
import react from "@vitejs/plugin-react"
import tailwindcss from "@tailwindcss/vite"

export default defineConfig({
  plugins: [react(), tailwindcss()],
})
```

En el CSS global que ya importa la app, agrega arriba:

```css
@import "tailwindcss";
```

Inicializa shadcn:

```powershell
npm.cmd exec -- shadcn@latest init -t vite -b radix --preset nova -y --pointer
```

Esto debe crear:

```txt
components.json
src/components/ui/
src/lib/utils.ts
```

## Anadir componentes

Ejemplos:

```powershell
npm.cmd exec -- shadcn@latest add button
npm.cmd exec -- shadcn@latest add card input dialog table badge
```

Uso:

```tsx
import { Button } from "@/components/ui/button"

export function Example() {
  return <Button variant="outline">Guardar</Button>
}
```

## Verificar que funciona

Despues de instalar o anadir componentes:

```powershell
npm.cmd run build
```

Si compila, la instalacion esta viva. Si no compila, no sigas acumulando componentes: arregla primero el error.

## Proyecto Atlas Balance

En este repo, shadcn/ui quedo instalado en:

```txt
C:\Proyectos\Atlas Balance Dev\Atlas Balance\frontend
```

No en:

```txt
C:\Proyectos\Atlas Balance Dev\Skills\Diseno
```

Porque `Skills\Diseno` no es el frontend. Es solo referencia.

## Actualizar la copia local del repo shadcn/ui

La copia local esta en:

```powershell
cd "C:\Proyectos\Atlas Balance Dev\Skills\Diseno\shadcn-ui"
git pull
```

Si fue clonada con `--depth 1`, eso basta para mantener la rama actual al dia en uso normal.

## Regla brutalmente simple

Busca el `package.json` de la app que quieres construir. Entra ahi. Ejecuta shadcn ahi. Todo lo demas es decoracion.
