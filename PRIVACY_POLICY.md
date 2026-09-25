# Política de privacidad de Atlas Peak

> **Fecha efectiva:** [FECHA_EFECTIVA]
> **Responsable del tratamiento:** [RESPONSABLE]
> **Contacto:** [CONTACTO]
>
> Este documento debe publicarse en una URL pública y enlazarse desde la ficha
> de Google Play (Play Console) y desde el flujo de verificación de Health
> Connect. Rellena los placeholders entre corchetes antes de publicarlo.

Este documento tiene dos versiones completas: [español](#versión-en-español) e
[English](#english-version). Ambas describen la misma app y el mismo
tratamiento de datos.

---

## Versión en español

### 1. Quiénes somos

Atlas Peak es una aplicación Android de entrenamiento de fuerza y cardio,
desarrollada por [RESPONSABLE]. Puedes contactarnos en [CONTACTO] para
cualquier pregunta sobre esta política o sobre tus datos.

### 2. Qué datos trata la app y dónde se guardan

Atlas Peak **no tiene servidor propio ni sistema de cuentas de usuario**. No
existe ningún backend de Atlas Peak al que se envíen tus datos. Todo lo que
registras en la app —perfil, rutinas, entrenamientos de fuerza, sesiones de
cardio, composición corporal, plan semanal y ajustes— se guarda **únicamente
en tu dispositivo**, en una base de datos cifrada con SQLCipher (AES-256). La
clave de cifrado se genera en tu dispositivo y se guarda en el almacenamiento
privado de la app; nunca sale de él salvo como parte de un backup cifrado que
tú decidas subir (ver sección 5).

### 3. Cero tracking, cero anuncios, cero SDKs de terceros

Atlas Peak no incluye analítica de terceros, publicidad ni SDKs de rastreo o
medición (no usa Firebase, Crashlytics, Google Analytics ni herramientas
similares). No compartimos tus datos con terceros con fines comerciales o
publicitarios. Las únicas integraciones externas de la app son, si tú las
activas de forma explícita:

- **Health Connect** (ver sección 4): es una capa local del sistema Android,
  no una conexión de red externa.
- **Google Drive** (ver sección 5): solo para el backup opcional que tú
  actives.

### 4. Health Connect

Si concedes permiso, Atlas Peak **lee** de Health Connect: pasos, calorías
activas, sueño, frecuencia cardíaca, peso, grasa corporal, masa magra y agua
corporal. Atlas Peak **escribe** en Health Connect: las sesiones de ejercicio
que completas en la app, y el peso, grasa corporal, masa magra y agua
corporal que introduces manualmente.

Estos datos se usan exclusivamente para mostrarte tu progreso y el dashboard
dentro de la propia app. No salen del dispositivo salvo que tú actives un
backup o generes un export manual (secciones 5 y 6). Puedes revocar el
acceso de Atlas Peak a Health Connect en cualquier momento desde la app
Health Connect de tu dispositivo.

### 5. Ubicación (GPS) durante el cardio

Atlas Peak solicita el permiso de ubicación precisa (GPS) **únicamente**
para trazar la ruta, la distancia y la velocidad durante una **sesión de
cardio activa y en primer plano**. La app **no solicita ni usa ubicación en
segundo plano** (`ACCESS_BACKGROUND_LOCATION`): en cuanto finalizas o sales
de la sesión de cardio, deja de acceder a tu ubicación. La ruta registrada se
guarda localmente, cifrada junto con el resto de la base de datos.

### 6. Backup opcional en Google Drive

Si activas el backup, Atlas Peak sube una copia de tu base de datos a la
**carpeta privada de datos de la app en tu Google Drive** (`appDataFolder`):
un espacio que solo Atlas Peak puede leer o escribir, invisible en tu Drive
normal y que no cuenta contra tu cuota de almacenamiento visible.

El cifrado se realiza **en tu dispositivo, antes de subir nada**, con una
contraseña que tú eliges (PBKDF2-HMAC-SHA256, 600.000 iteraciones, AES-256).
Google nunca ve el contenido en claro. Esa contraseña —o, si activas el
backup automático, su copia cifrada localmente con Android Keystore— se
guarda **solo en tu dispositivo**, nunca en Drive ni en ningún servidor de
Atlas Peak (que no existe).

### 7. Exportar tus datos manualmente

Puedes generar en cualquier momento un export local de tus datos, en dos
formatos:

- **Cifrado (.enc):** protegido con la contraseña que elijas en ese momento.
- **En claro (JSON o CSV):** sin cifrar.

Estos archivos se generan localmente en el almacenamiento privado de la app
y **solo se comparten si tú, explícitamente, eliges una app o servicio de
destino** en el selector de "Compartir" de Android.

### 8. Cómo borrar tus datos

Tienes varias formas de borrar tus datos, en cualquier momento:

1. **Desde la app:** Perfil → "Borrar todos mis datos". Borra de forma
   irreversible todo lo guardado en el dispositivo (rutinas, entrenamientos,
   cardio, composición corporal, ajustes) y, si lo indicas, también tus
   backups en Google Drive (siempre que Drive esté autorizado en ese
   momento).
2. **Desinstalando la app:** elimina la base de datos local del dispositivo.
3. **Revocando el acceso a Health Connect:** desde la app Health Connect de
   tu dispositivo.
4. **Revocando el acceso a Google Drive:** desde los ajustes de tu cuenta de
   Google ([myaccount.google.com/permissions](https://myaccount.google.com/permissions)),
   lo que también te permite borrar manualmente los backups si el borrado
   automático desde la app no fue posible.

### 9. Cambios a esta política

Si esta política cambia de forma relevante, actualizaremos la fecha efectiva
al inicio de este documento y, cuando aplique, lo indicaremos dentro de la
app.

### 10. Contacto

Para cualquier pregunta sobre esta política o sobre tus datos, escribe a
[CONTACTO].

---

## English version

### 1. Who we are

Atlas Peak is an Android app for strength and cardio training, developed by
[CONTROLLER]. You can reach us at [CONTACT] with any question about this
policy or your data.

### 2. What data the app handles and where it is stored

Atlas Peak **has no server of its own and no user account system**. There is
no Atlas Peak backend that your data is sent to. Everything you log in the
app — profile, routines, strength workouts, cardio sessions, body
composition, weekly plan and settings — is stored **only on your device**,
in a database encrypted with SQLCipher (AES-256). The encryption key is
generated on your device and kept in the app's private storage; it never
leaves it except as part of an encrypted backup you choose to upload (see
section 5).

### 3. No tracking, no ads, no third-party SDKs

Atlas Peak includes no third-party analytics, advertising, or tracking/
measurement SDKs (no Firebase, Crashlytics, Google Analytics or similar
tools). We do not share your data with third parties for commercial or
advertising purposes. The app's only external integrations are, if you
explicitly turn them on:

- **Health Connect** (see section 4): a local layer of the Android system,
  not an external network connection.
- **Google Drive** (see section 5): only for the optional backup you enable.

### 4. Health Connect

If you grant permission, Atlas Peak **reads** from Health Connect: steps,
active calories, sleep, heart rate, weight, body fat, lean body mass and
body water mass. Atlas Peak **writes** to Health Connect: exercise sessions
you complete in the app, and the weight, body fat, lean body mass and body
water mass you enter manually.

This data is used only to show your progress and dashboard inside the app
itself. It never leaves the device unless you turn on backup or generate a
manual export (sections 5 and 6). You can revoke Atlas Peak's access to
Health Connect at any time from the Health Connect app on your device.

### 5. Location (GPS) during cardio

Atlas Peak requests precise location (GPS) permission **only** to trace the
route, distance and speed during an **active, foreground cardio session**.
The app **never requests or uses background location**
(`ACCESS_BACKGROUND_LOCATION`): as soon as you finish or leave the cardio
session, it stops accessing your location. The recorded route is stored
locally, encrypted along with the rest of the database.

### 6. Optional Google Drive backup

If you turn on backup, Atlas Peak uploads a copy of your database to the
**app's private data folder in your Google Drive** (`appDataFolder`): a
space only Atlas Peak can read or write, hidden from your regular Drive view
and not counted against your visible storage quota.

Encryption happens **on your device, before anything is uploaded**, with a
password you choose (PBKDF2-HMAC-SHA256, 600,000 iterations, AES-256).
Google never sees the content in plain form. That password — or, if you
turn on automatic backup, its locally Android Keystore-encrypted copy — is
stored **only on your device**, never in Drive or on any Atlas Peak server
(none exists).

### 7. Manually exporting your data

You can generate a local export of your data at any time, in two formats:

- **Encrypted (.enc):** protected with a password you choose at that moment.
- **Plain text (JSON or CSV):** unencrypted.

These files are generated locally in the app's private storage and are
**only shared if you explicitly pick** an app or service from Android's
share sheet.

### 8. How to delete your data

You have several ways to delete your data, at any time:

1. **In the app:** Profile → "Delete all my data". This irreversibly deletes
   everything stored on the device (routines, workouts, cardio, body
   composition, settings) and, if you choose to, your Google Drive backups
   as well (as long as Drive is authorized at that moment).
2. **Uninstalling the app:** removes the local database from the device.
3. **Revoking Health Connect access:** from the Health Connect app on your
   device.
4. **Revoking Google Drive access:** from your Google account settings
   ([myaccount.google.com/permissions](https://myaccount.google.com/permissions)),
   which also lets you manually delete backups if in-app deletion could not
   reach Drive.

### 9. Changes to this policy

If this policy changes in a meaningful way, we will update the effective
date at the top of this document and, where applicable, note it inside the
app.

### 10. Contact

For any question about this policy or your data, write to [CONTACT].
