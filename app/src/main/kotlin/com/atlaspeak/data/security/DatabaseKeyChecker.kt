package com.atlaspeak.data.security

import com.atlaspeak.data.db.AppDatabase
import com.atlaspeak.domain.usecase.security.DatabaseKeyCheckResult
import com.atlaspeak.domain.usecase.security.DatabaseKeyChecker
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Si el Keystore/keyset esta corrupto (`DatabaseKeyUnavailableException`, ver
 * `DatabasePassphraseProvider`) o SQLCipher no logra abrir la DB con la clave obtenida, se
 * reporta [DatabaseKeyCheckResult.KeyUnavailable] en vez de dejar que la excepcion suba y la
 * app quede en crash-loop; la UI enruta a Recovery.
 */
class RoomDatabaseKeyChecker @Inject constructor(
    private val database: AppDatabase,
) : DatabaseKeyChecker {
    override suspend fun check(): DatabaseKeyCheckResult = withContext(Dispatchers.IO) {
        try {
            // Fuerza la apertura real (LazyPassphraseOpenHelperFactory difiere justo esto).
            database.openHelper.writableDatabase
            DatabaseKeyCheckResult.Ok
        } catch (error: Exception) {
            DatabaseKeyCheckResult.KeyUnavailable
        }
    }
}
