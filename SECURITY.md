# SECURITY.md — Registro de seguridad

> Log de problemas de seguridad detectados y su resolución, más el modelo de amenazas y
> los checklists permanentes. Cada hallazgo deja rastro aquí.
>
> Estados: `🔴 Abierto` · `🟡 Mitigando` · `🟢 Resuelto` · `🔵 Aceptado (riesgo conocido)`

---

## 1. Modelo de amenazas (resumen)

Atlas Peak es **local-first** sin backend propio. Las superficies de ataque reales son:

1. **Acceso físico al dispositivo desbloqueado** → mitigado con gate de contraseña +
   biometría + timeout + `FLAG_SECURE`.
2. **Extracción de la DB del dispositivo** → mitigado con SQLCipher (clave en Keystore).
3. **Backup en Google Drive comprometido** (cuenta Google hackeada / Google interno) →
   mitigado con AES-256-GCM y clave derivada de contraseña (Google solo ve binario cifrado).
   **Aquí el atacante puede hacer fuerza bruta offline** → por eso PBKDF2 con 600k iter.
4. **Secretos en el repositorio** (Maps key, OAuth id, keystore) → mitigado con
   `.gitignore` + `secrets.properties`. Riesgo humano, vigilancia continua.
5. **Tráfico de red** (solo Drive) → solo HTTPS, sin cleartext, Bearer token.

Fuera de alcance: no hay servidor que atacar, no hay multiusuario, no hay PII en tránsito
salvo lo que el propio Google maneja en su OAuth.

---

## 2. Hallazgos de la revisión inicial (spec v2.1 → v2.2)

Estos se detectaron al auditar el spec **antes** de escribir código. Los fixes están
reflejados en `SPEC.md v2.2`, `CLAUDE.md §6-7`, el manifest y el catálogo de versiones.

### SEC-010 — DB SQLCipher y clave local protegida
- **Estado:** 🟢 Resuelto
- **Fecha:** 2026-05-23
- **Severidad:** Alta
- **Síntoma:** Fase 1 necesitaba abrir Room con SQLCipher sin persistir una clave de DB en texto plano.
- **Causa raíz:** el bootstrap solo declaraba SQLCipher como dependencia; no existía ciclo de vida de clave.
- **Solución:** `DatabasePassphraseProvider` genera 32 bytes aleatorios con `SecureRandom`, los guarda codificados en `EncryptedSharedPreferences` protegido por Android Keystore, y entrega la passphrase a `SupportOpenHelperFactory`.
- **Prevención:** no usar `fallbackToDestructiveMigration()` fuera de tests; revisar este flujo en Fase 13 junto con backup/restore.

### SEC-011 — Auth local con PBKDF2 y rate-limit persistente
- **Estado:** 🟢 Resuelto
- **Fecha:** 2026-05-23
- **Severidad:** Alta
- **Síntoma:** Fase 2 necesitaba un gate local real; dejar el `NavHost` arrancando en `Home` convertía auth en teatro.
- **Causa raíz:** Fase 1 solo tenía rutas placeholder y la tabla `auth_security`, sin DAO/repositorio/use case de autenticación.
- **Solución:** `LoginScreen` es el destino inicial, `LocalAuthUseCase` usa PBKDF2-HMAC-SHA256 con 600.000 iteraciones y salt de 32 bytes, comparación constante, y bloqueo persistente de 5 intentos/15 min en `auth_security`. Google Identity no desbloquea datos locales; solo informa de conexión opcional. Biometría requiere contraseña local previa y opt-in guardado.
- **Prevención:** constantes únicas en `EncryptionManager`/`LocalAuthPolicy`, tests unitarios de crypto/rate-limit/bypass de Google y `FLAG_SECURE` por ruta sensible.

### SEC-012 — Onboarding sensible y permisos Health Connect
- **Estado:** 🟢 Resuelto
- **Fecha:** 2026-05-23
- **Severidad:** Media
- **Síntoma:** Fase 3 añade captura de contraseña/perfil y permisos Health Connect durante onboarding.
- **Causa raíz:** onboarding pasó de placeholder a flujo sensible; además el manifest necesitaba declarar permisos Health Connect antes de pedirlos.
- **Solución:** `FLAG_SECURE` cubre `Onboarding`, se declaran permisos `android.permission.health.*` del spec, y el request de Health Connect comprueba disponibilidad del SDK antes de lanzar el contrato.
- **Prevención:** revisión de rutas sensibles cada vez que una pantalla capture contraseña, perfil, backup o permisos de salud.

### SEC-013 — Foreground service de fuerza puede fallar por permiso runtime
- **Estado:** 🟢 Resuelto
- **Fecha:** 2026-05-23
- **Severidad:** Media
- **Síntoma:** Fase 5 activa `WorkoutForegroundService` con `foregroundServiceType=health`. En Android 14+ el tipo health puede requerir permisos runtime; si el usuario los salta, `startForeground` puede lanzar `SecurityException`.
- **Causa raíz:** el manifest declara permisos, pero la concesión runtime depende del usuario y del dispositivo.
- **Solución:** `ActiveWorkout` solicita `ACTIVITY_RECOGNITION` antes de arrancar el servicio. `ActiveWorkoutViewModel` y `WorkoutForegroundService` capturan `SecurityException`; la sesión activa no crashea y muestra un aviso si el cronómetro persistente no arranca.
- **Prevención:** gate de Fase 5 incluye revisión de permisos FGS; Fase 13 debe validar flujo en dispositivo real Android 14/15.

### SEC-014 - Cardio GPS no debe pedir ubicacion de fondo ni fingir ruta
- **Estado:** Resuelto
- **Fecha:** 2026-05-23
- **Severidad:** Media
- **Sintoma:** Fase 6 arranca un `CardioForegroundService`; si el usuario deniega ubicacion, la app no puede guardar una sesion como GPS real sin puntos.
- **Causa raiz:** el permiso de ubicacion es runtime y el FGS de tipo `location` no puede asumirse concedido.
- **Solucion:** `ActiveCardio` solicita `ACCESS_FINE_LOCATION` solo para tipos con GPS. Si se deniega o el tipo es manual, la sesion usa cronometro local y exige distancia/velocidad manuales antes de guardarse. `ACCESS_BACKGROUND_LOCATION` sigue ausente.
- **Prevencion:** Fase 13 debe validar el flujo en Android 14/15 real: GPS concedido, GPS denegado y cardio manual.

### SEC-015 - Composicion corporal muestra datos de salud sensibles
- **Estado:** Resuelto
- **Fecha:** 2026-05-24
- **Severidad:** Media
- **Sintoma:** Fase 9 activa la pantalla `Body` con peso, grasa, masa muscular y edad corporal; sin proteccion, esos datos podrian aparecer en screenshots o vista de recientes.
- **Causa raiz:** `Body` era placeholder y no estaba incluido en las rutas sensibles con `FLAG_SECURE`.
- **Solucion:** `AppRoute.Body.route` se anade a `secureRoutes`; no se introducen nuevos permisos, red ni logs de valores corporales.
- **Prevencion:** toda pantalla que muestre salud, backup, perfil, auth o permisos sensibles debe revisarse contra `SecureScreenEffect`.

### SEC-001 — Backup no restaurable: falta el salt en el archivo cifrado
- **Estado:** 🟢 Resuelto (en diseño)
- **Fecha:** 2026-05-23
- **Severidad:** Crítica
- **Síntoma:** El spec v2.1 define el formato de backup como `[IV(12B)][ciphertext]` y la
  clave como PBKDF2(contraseña). PBKDF2 requiere salt. En un dispositivo nuevo el salt no
  existe (vivía en la DB que se intenta restaurar). Resultado: **imposible derivar la clave
  y descifrar el backup en otro dispositivo** — justo la promesa estrella de la feature.
- **Causa raíz:** formato de archivo sin cabecera de parámetros KDF.
- **Solución:** nuevo formato del archivo de backup:
  `[magic(4B)="ATPK"][versión(1B)][iteraciones(4B, big-endian)][salt(16B)][IV(12B)][ciphertext+tag GCM]`.
  El salt y las iteraciones del backup son **independientes** del salt de login y viajan
  dentro del archivo (no son secretos). En restore: leer cabecera → derivar clave con esos
  params → descifrar.
- **Prevención:** test de round-trip "cifrar en dispositivo A, descifrar en dispositivo B
  (salt distinto en login)" obligatorio en Fase 12. Documentado en `CLAUDE.md §7`.

### SEC-002 — Cambio de contraseña invalida backups anteriores (sin avisar)
- **Estado:** 🔵 Aceptado + mitigado con UX
- **Fecha:** 2026-05-23
- **Severidad:** Alta
- **Síntoma:** la clave del backup deriva de la contraseña. Si el usuario cambia la
  contraseña, los backups antiguos quedan cifrados con la clave vieja → indescifrables con
  la nueva.
- **Causa raíz:** acoplamiento contraseña↔clave de backup (es también la ventaja: portabilidad).
- **Solución/mitigación:** al cambiar contraseña, la app (a) avisa de que los backups
  previos requerirán la contraseña antigua para restaurarse, y (b) ofrece crear un backup
  nuevo inmediatamente con la nueva contraseña. No se borran los antiguos automáticamente.
- **Prevención:** flujo de cambio de contraseña en Fase 2/12 incluye este aviso explícito.

### SEC-003 — `ACCESS_BACKGROUND_LOCATION` solicitado innecesariamente
- **Estado:** 🟢 Resuelto
- **Fecha:** 2026-05-23
- **Severidad:** Alta (riesgo de rechazo en Play + sobre-permisos)
- **Síntoma:** el manifest del spec pedía `ACCESS_BACKGROUND_LOCATION`.
- **Causa raíz:** suposición de que el tracking en background lo necesita.
- **Solución:** **eliminado.** El `CardioForegroundService` con `foregroundServiceType=location`,
  arrancado mientras la app está visible, accede a ubicación con la UI en segundo plano sin
  ese permiso. Pedirlo dispara revisión manual de Google Play y es causa frecuente de rechazo
  en apps de fitness.
- **Prevención:** regla en `CLAUDE.md §7`; revisión de manifest en Fase 13.

### SEC-004 — `google-services.json` y confusión de credenciales
- **Estado:** 🟢 Resuelto
- **Fecha:** 2026-05-23
- **Severidad:** Media
- **Síntoma:** el spec mandaba colocar `google-services.json` en `app/`. Es artefacto de
  Firebase/GMS plugin, no presente en los plugins del proyecto, y **contiene una API key**
  que acabaría en el repo.
- **Causa raíz:** confusión entre Firebase y Google Identity Services / Drive REST.
- **Solución:** **no se usa `google-services.json`.** Credential Manager + Drive REST solo
  necesitan el **Web OAuth Client ID** (no secreto, pero gestionado via `secrets.properties`
  para no esparcirlo). `google-services.json` añadido al `.gitignore` por si acaso.
- **Prevención:** documentado en `CLAUDE.md §6`.

### SEC-005 — Maps API key sin gestión (config faltante + riesgo de exposición)
- **Estado:** 🟢 Resuelto (mecanismo listo; valor lo pone el usuario)
- **Fecha:** 2026-05-23
- **Severidad:** Media
- **Síntoma:** Maps Compose exige una API key en el manifest; el spec no la mencionaba.
- **Causa raíz:** omisión.
- **Solución:** la key se lee de `secrets.properties` (`MAPS_API_KEY`) y se inyecta via
  `manifestPlaceholders`. `secrets.properties` está en `.gitignore`. Restringir la key en
  Google Cloud Console (por SHA-1 + package name) antes de release.
- **Prevención:** plantilla `secrets.properties.template`; regla en `CLAUDE.md §6`.

### SEC-006 — Iteraciones PBKDF2 bajas para amenaza offline
- **Estado:** 🟢 Resuelto
- **Fecha:** 2026-05-23
- **Severidad:** Media
- **Síntoma:** spec usaba 200.000 iteraciones y lo etiquetaba "suficiente/fuerte".
- **Causa raíz:** referencia desactualizada. Para el backup (atacable offline) las
  iteraciones son la defensa principal.
- **Solución:** **600.000 iteraciones** PBKDF2-HMAC-SHA256 (alineado con OWASP).
- **Prevención:** constante única en `EncryptionManager`; no hardcodear en otros sitios.

### SEC-007 — `allowBackup` no definido (la DB cifrada no debe ir al backup de Android)
- **Estado:** 🟢 Resuelto
- **Fecha:** 2026-05-23
- **Severidad:** Media
- **Síntoma:** sin `android:allowBackup="false"`, Android podría incluir la DB SQLCipher y
  las prefs cifradas en su backup automático, con claves ligadas a un Keystore que no
  migra → datos inservibles o expuestos.
- **Solución:** `allowBackup="false"` + `dataExtractionRules`/`fullBackupContent` que
  excluyen DB y prefs. El backup de datos lo gestiona la app (Drive cifrado), no Android.
- **Prevención:** en el manifest base; revisión Fase 13.

### SEC-008 — `USE_FINGERPRINT` deprecado
- **Estado:** 🟢 Resuelto
- **Fecha:** 2026-05-23
- **Severidad:** Baja
- **Síntoma:** permiso deprecado desde API 28; con minSdk 31 es peso muerto.
- **Solución:** eliminado. `USE_BIOMETRIC` cubre todo.

### SEC-009 — `foregroundServiceType="health"` sin permiso asociado
- **Estado:** 🟢 Resuelto
- **Fecha:** 2026-05-23
- **Severidad:** Media
- **Síntoma:** el manifest declaraba `WorkoutForegroundService` con
  `foregroundServiceType="health"` y `FOREGROUND_SERVICE_HEALTH`, pero faltaba un permiso
  asociado como `ACTIVITY_RECOGNITION`. En Android 14+ esto puede lanzar `SecurityException`
  al arrancar el foreground service.
- **Causa raíz:** bootstrap incompleto del manifest: se añadió el tipo de FGS, no el permiso
  de actividad requerido por la plataforma.
- **Solución:** añadido `android.permission.ACTIVITY_RECOGNITION` y documentado junto al
  contrato del `WorkoutForegroundService`.
- **Prevención:** lint queda como gate obligatorio de Fase 0 para detectar permisos FGS
  incompletos.

---

## 3. Checklist de seguridad por release (repetir antes de cada subida a Play)

- [ ] Cero secretos en el repo (`git log -p` y `git grep` por "key", "secret", "password").
- [ ] `secrets.properties`, `keystore.properties`, `*.jks`, `local.properties`,
      `google-services.json` siguen en `.gitignore`.
- [ ] OkHttp logging interceptor desactivado en release (`BuildConfig.DEBUG`).
- [ ] Sin `Log.*` con datos sensibles en builds release (ProGuard/R8 los retira; verificar).
- [ ] `FLAG_SECURE` activo en Login, Biometría, Perfil, Backup.
- [ ] `network_security_config.xml`: `cleartextTrafficPermitted="false"` en producción.
- [ ] Maps API key restringida (SHA-1 + package) en Cloud Console.
- [ ] Permisos del manifest = solo los usados (sin `ACCESS_BACKGROUND_LOCATION`).
- [ ] Test de round-trip de backup entre "dispositivos" (salts distintos) pasa.
- [ ] Biometría = `BIOMETRIC_STRONG`.
- [ ] PBKDF2 = 600.000 iteraciones.

---

## 4. Riesgos conocidos y aceptados

| ID | Limitación | Por qué se acepta |
|----|------------|-------------------|
| SEC-002 | Cambiar contraseña invalida backups previos | Es el coste de la portabilidad sin backend; mitigado con aviso UX |
| RA-01 | Rate-limit reseteable borrando datos de la app | La defensa real es el hash fuerte; sin servidor no hay alternativa |
| RA-02 | Pérdida de contraseña **y** cuenta Google → datos irrecuperables | Documentado y advertido en onboarding; no hay solución sin comprometer el cifrado |
| RA-03 | `FLAG_SECURE` solo en pantallas críticas, no en todas | Coste/beneficio; el resto no muestra datos sensibles |

---

## Formato para nuevos hallazgos

```
### SEC-NNN — Título
- **Estado:** 🔴 / 🟡 / 🟢 / 🔵
- **Fecha:** AAAA-MM-DD
- **Severidad:** Crítica / Alta / Media / Baja
- **Síntoma:** ...
- **Causa raíz:** ...
- **Solución:** ...
- **Prevención:** ...
```

---

*Atlas Peak — SECURITY.md*
