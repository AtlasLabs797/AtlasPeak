package com.atlaspeak.domain.usecase.recovery

/**
 * Borra la base de datos cifrada local y la re-siembra desde cero, para cuando el Keystore/
 * keyset esta corrupto y la DB no se puede abrir en este dispositivo (ver
 * `presentation/recovery/RecoveryViewModel`). Interfaz en `domain` para que la presentation
 * layer no dependa de `data` directamente (StaticArchitecturePolicyTest); la implementacion
 * real vive en `data.security` porque toca Room/Keystore.
 */
interface DatabaseRecoveryUseCase {
    /** @return `true` si el reseteo (borrado + resembrado) se completo correctamente. */
    suspend fun resetLocalData(): Boolean
}
