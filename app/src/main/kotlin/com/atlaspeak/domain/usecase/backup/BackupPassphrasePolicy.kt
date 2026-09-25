package com.atlaspeak.domain.usecase.backup

import javax.inject.Inject

/**
 * Bloqueo de contraseñas comunes, inyectado desde `data` para que este
 * paquete `domain` no dependa de Android (recursos, IO en disco, etc.).
 * Ver [com.atlaspeak.data.backup.CommonPasswordBlocklist].
 */
fun interface PassphraseBlocklist {
    suspend fun isCommon(passphraseLowercase: String): Boolean
}

/**
 * Política de la contraseña de backup (BUG P0): antes solo se exigía que no
 * estuviera vacía, así un typo en la confirmación hacía irrecuperable el
 * backup y una contraseña de 1-4 caracteres permitía fuerza bruta offline
 * sobre un `.enc` exportado.
 *
 * Sin reglas de complejidad (mayúsculas/símbolos/dígitos): solo longitud
 * mínima y lista de contraseñas comunes (AGENTS.md §2.2 — nada de validación
 * especulativa). Aplica solo a la CREACIÓN de un secreto (backup nuevo,
 * activar auto-backup, cambiar la contraseña de auto-backup); restaurar
 * acepta cualquier contraseña no vacía.
 */
class BackupPassphrasePolicy @Inject constructor(
    private val blocklist: PassphraseBlocklist,
) {
    suspend fun evaluate(passphrase: CharArray, confirmation: CharArray): BackupPassphraseValidation {
        if (passphrase.size < MIN_LENGTH) return BackupPassphraseValidation.TooShort
        // SEC-036: se evita mantener el CharArray como String más de lo
        // imprescindible; el bloqueo lee de un Set<String> (igual que
        // SEC-039 acepta el mismo riesgo residual para el JSON del backup).
        val lowercase = passphrase.concatToString().lowercase()
        if (blocklist.isCommon(lowercase)) return BackupPassphraseValidation.Common
        if (!passphrase.contentEquals(confirmation)) return BackupPassphraseValidation.Mismatch
        return BackupPassphraseValidation.Valid
    }

    companion object {
        const val MIN_LENGTH = 8
    }
}

sealed interface BackupPassphraseValidation {
    data object TooShort : BackupPassphraseValidation
    data object Common : BackupPassphraseValidation
    data object Mismatch : BackupPassphraseValidation
    data object Valid : BackupPassphraseValidation
}
