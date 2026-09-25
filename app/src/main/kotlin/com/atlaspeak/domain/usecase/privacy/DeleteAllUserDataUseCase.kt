package com.atlaspeak.domain.usecase.privacy

import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/**
 * Borra todos los datos del usuario (GDPR: derecho al olvido / P2 de la auditoria
 * de privacidad). Orquesta el orden seguro: primero cancela trabajo en segundo
 * plano para que nada reescriba datos a medio borrar, despues intenta borrar los
 * backups de Drive (si se pidio) y por ultimo limpia lo local.
 */
class DeleteAllUserDataUseCase @Inject constructor(
    private val userDataEraser: UserDataEraser,
) {
    suspend operator fun invoke(deleteDriveBackups: Boolean): DeleteAllUserDataResult {
        return try {
            userDataEraser.cancelBackgroundWork()
            val driveCleared = if (deleteDriveBackups) userDataEraser.deleteDriveBackups() else true
            userDataEraser.eraseLocalData()
            if (driveCleared) {
                DeleteAllUserDataResult.Success
            } else {
                DeleteAllUserDataResult.PartialSuccess(driveNotDeleted = true)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            DeleteAllUserDataResult.Failed
        }
    }
}

sealed interface DeleteAllUserDataResult {
    data object Success : DeleteAllUserDataResult
    data class PartialSuccess(val driveNotDeleted: Boolean) : DeleteAllUserDataResult
    data object Failed : DeleteAllUserDataResult
}
