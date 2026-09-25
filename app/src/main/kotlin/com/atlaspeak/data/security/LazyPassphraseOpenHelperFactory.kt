package com.atlaspeak.data.security

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Envuelve [SupportOpenHelperFactory] para que abrir la base de datos cifrada (I/O de Keystore
 * + derivacion de clave SQLCipher) nunca ocurra en el hilo que construye el singleton de Room
 * (`DatabaseModule.provideAppDatabase`, que hoy corre en el hilo principal via inyeccion de
 * campos de `AtlasPeakApplication`). Room solo toca `writableDatabase`/`readableDatabase` fuera
 * del hilo principal, asi que aplazar la lectura de la passphrase hasta ese momento libera el
 * arranque.
 *
 * La busqueda de la passphrase es perezosa y NO cachea fallos: si `create(configuration)` de
 * turno lanza (p. ej. `DatabaseKeyUnavailableException`), el siguiente intento (tras un reset
 * de Recovery) vuelve a intentarlo desde cero en lugar de repetir el mismo error para siempre.
 */
class LazyPassphraseOpenHelperFactory(
    private val passphraseProvider: DatabasePassphraseProvider,
) : SupportSQLiteOpenHelper.Factory {

    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
        return LazyOpenHelper(configuration)
    }

    private inner class LazyOpenHelper(
        private val configuration: SupportSQLiteOpenHelper.Configuration,
    ) : SupportSQLiteOpenHelper {
        private val lock = Any()

        @Volatile
        private var delegate: SupportSQLiteOpenHelper? = null
        private var writeAheadLoggingEnabled = false

        override val databaseName: String?
            get() = configuration.name

        override fun setWriteAheadLoggingEnabled(enabled: Boolean) {
            synchronized(lock) {
                writeAheadLoggingEnabled = enabled
                delegate?.setWriteAheadLoggingEnabled(enabled)
            }
        }

        override val writableDatabase: SupportSQLiteDatabase
            get() = ensureDelegate().writableDatabase

        override val readableDatabase: SupportSQLiteDatabase
            get() = ensureDelegate().readableDatabase

        override fun close() {
            synchronized(lock) { delegate?.close() }
        }

        private fun ensureDelegate(): SupportSQLiteOpenHelper {
            delegate?.let { return it }
            synchronized(lock) {
                delegate?.let { return it }
                // getPassphrase() puede lanzar DatabaseKeyUnavailableException; no se asigna
                // `delegate` en ese caso, asi que el proximo intento vuelve a llamarla.
                val passphrase = passphraseProvider.getPassphrase()
                val real = SupportOpenHelperFactory(passphrase).create(configuration)
                real.setWriteAheadLoggingEnabled(writeAheadLoggingEnabled)
                delegate = real
                return real
            }
        }
    }
}
