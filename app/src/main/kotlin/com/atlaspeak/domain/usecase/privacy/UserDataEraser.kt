package com.atlaspeak.domain.usecase.privacy

/**
 * Borra todos los datos del usuario (GDPR: derecho al olvido). La implementacion
 * vive en `data/privacy` y cruza varias fuentes (WorkManager, Drive, Room,
 * DataStore/SharedPreferences); el dominio solo ve estas tres operaciones.
 */
interface UserDataEraser {
    /** Cancela todo el trabajo en segundo plano (auto-backup, recordatorios, resumenes). */
    suspend fun cancelBackgroundWork()

    /**
     * Borra los backups en Google Drive si hay un token de acceso disponible de
     * forma silenciosa. Devuelve `true` si no quedan backups en Drive (porque se
     * borraron o porque no habia ninguno), `false` si no se pudo autorizar o
     * borrar (el usuario deberia borrarlos manualmente desde Drive).
     */
    suspend fun deleteDriveBackups(): Boolean

    /** Limpia la base de datos local (re-sembrada), preferencias y exports en disco. */
    suspend fun eraseLocalData()
}
