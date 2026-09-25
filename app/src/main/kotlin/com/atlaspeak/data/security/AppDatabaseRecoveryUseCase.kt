package com.atlaspeak.data.security

import android.content.Context
import com.atlaspeak.data.db.AppDatabase
import com.atlaspeak.data.db.seed.DatabaseSeeder
import com.atlaspeak.domain.usecase.recovery.DatabaseRecoveryUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * P1 (auditoria) - recuperacion cuando la base de datos cifrada no abre en este dispositivo
 * (Keystore/keyset corrupto). La unica salida sin backend es borrar los datos locales:
 *  1. Cerrar el helper de Room (no-op si nunca llego a abrir de verdad, ver
 *     `LazyPassphraseOpenHelperFactory`).
 *  2. Borrar el fichero de la base de datos (`Context.deleteDatabase`, incluye -wal/-shm).
 *  3. Borrar la passphrase guardada (nuevo formato, legado y la clave de Keystore).
 *  4. Re-sembrar: abrir la DB genera una passphrase nueva sobre una base vacia (como una
 *     instalacion nueva) y el seed deja los datos de referencia.
 */
class AppDatabaseRecoveryUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val passphraseProvider: DatabasePassphraseProvider,
    private val databaseSeeder: DatabaseSeeder,
) : DatabaseRecoveryUseCase {
    override suspend fun resetLocalData(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            database.close()
            context.deleteDatabase(AppDatabase.DATABASE_NAME)
            passphraseProvider.clearStoredPassphrase()
            // El seed del arranque se salto (la DB no abria): sin el, onboarding/Home
            // quedarian sin ejercicios, rutinas ni ajustes por defecto.
            databaseSeeder.seed()
        }.isSuccess
    }
}
