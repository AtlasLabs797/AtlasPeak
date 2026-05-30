# Atlas Peak

App Android nativa, **local-first**, de gestión de entrenamientos de fuerza y cardio.
Sin backend propio. Datos en el dispositivo. Backup cifrado opcional en Google Drive.
Cero tracking.

---

## Mapa de documentos (léelos en este orden)

| Archivo | Para qué |
|---------|----------|
| **`CLAUDE.md`** | Instrucciones maestras para Claude Code. Reglas, fases, convenciones. **Empieza aquí.** |
| **`SPEC.md`** | Especificación funcional y técnica completa (v2.2, ya corregida). |
| **`DESIGN.md`** | Sistema de diseño (color, tipografía, espaciado, componentes). |
| **`DOCS_TECNICA.md`** | Arquitectura y contratos, para el técnico. |
| **`DOCS_USUARIO.md`** | Guía simple para el usuario final. |
| **`SECURITY.md`** | Modelo de amenazas + log de hallazgos de seguridad. |
| **`BUGS.md`** | Log de bugs y sus soluciones. |
| **`CHANGELOG.md`** | Trazabilidad de todo lo que se hace. |

---

## Cómo arrancar (Fase 0)

Este kit trae la **configuración de build, gobernanza y seguridad** ya corregida, con Gradle
Wrapper e icono launcher mínimo incluidos.

Luego:

```bash
cp secrets.properties.template secrets.properties   # opcional: rellena OAUTH_WEB_CLIENT_ID y MAPS_API_KEY reales
./gradlew assembleDebug
```

En esta máquina se dejó preparado un entorno portable en `C:\tmp\atlas-dev-tools` y
`local.properties` apunta a su Android SDK. También quedaron registradas variables de usuario
`JAVA_HOME`, `ANDROID_HOME`, `ANDROID_SDK_ROOT` y entradas de `Path` para nuevas terminales.
En otra máquina, instala JDK 17 + Android SDK Platform 36 y crea tu propio `local.properties`.

> **No subas secretos.** `secrets.properties`, `keystore.properties`, `*.jks`,
> `google-services.json` y `local.properties` están en `.gitignore`. Mantenlos ahí.

---

## Estado

- **v1:** teléfono. Wear OS **diferido a v2** (decisión documentada en `CLAUDE.md §9`).
- Plan de 16 fases en `SPEC.md §9` / tabla en `CLAUDE.md §3`. Una fase cada vez.

---

*Atlas Peak — kit de arranque generado el 2026-05-23, alineado con SPEC.md v2.2*
