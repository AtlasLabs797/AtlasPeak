package com.atlaspeak.data.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.atlaspeak.data.db.AppDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Passphrase de la base de datos SQLCipher (SEC-010).
 *
 * Antes: se guardaba codificada en EncryptedSharedPreferences (`androidx.security.crypto`,
 * deprecado). Ahora seguimos el mismo patron que `BackupCredentialStore` (SEC-034): una clave
 * AES-256-GCM vive dentro de AndroidKeyStore y cifra la passphrase; solo el IV+ciphertext en
 * Base64 se guarda en SharedPreferences planas.
 *
 * Migracion, evaluada perezosamente la primera vez que se llama a [getPassphrase] tras
 * actualizar:
 *  1. Si ya existe el valor en el nuevo formato -> se usa.
 *  2. Si no, se lee el valor legado de EncryptedSharedPreferences
 *     (`atlas_peak_secure`/`database_passphrase`). Si existe, se migra al nuevo formato y se
 *     borra la entrada legada.
 *  3. Si tampoco hay valor legado:
 *     - si el fichero de la base de datos NO existe todavia (instalacion nueva) -> se genera
 *       una passphrase aleatoria y se guarda.
 *     - si el fichero SI existe -> [DatabaseKeyUnavailableException]: generar una clave nueva
 *       dejaria esa base de datos inaccesible para siempre (perdida de datos silenciosa).
 *  Cualquier fallo al leer/descifrar (Keystore corrupto, keyset legado roto) tambien lanza
 *  [DatabaseKeyUnavailableException] en lugar de generar una clave nueva.
 */
@Singleton
class DatabasePassphraseProvider @Inject constructor(
    @ApplicationContext
    private val context: Context,
) {
    private val secureRandom = SecureRandom()

    fun getPassphrase(): ByteArray {
        val stored = try {
            readStoredPassphrase()
        } catch (error: Exception) {
            throw DatabaseKeyUnavailableException("Unable to decrypt the stored database passphrase", error)
        }
        if (stored != null) return stored

        val legacy = try {
            readLegacyPassphrase()
        } catch (error: Exception) {
            throw DatabaseKeyUnavailableException("Unable to read the legacy database passphrase", error)
        }
        if (legacy != null) {
            try {
                storePassphrase(legacy)
            } catch (error: Exception) {
                throw DatabaseKeyUnavailableException("Unable to migrate the legacy database passphrase", error)
            }
            clearLegacyPreferences()
            return legacy
        }

        if (!databaseFileExists()) {
            val generated = ByteArray(KEY_SIZE_BYTES).also(secureRandom::nextBytes)
            try {
                storePassphrase(generated)
            } catch (error: Exception) {
                throw DatabaseKeyUnavailableException("Unable to store a newly generated database passphrase", error)
            }
            return generated
        }

        throw DatabaseKeyUnavailableException(
            "Database file exists but no passphrase (new or legacy) could be found",
        )
    }

    /** Borra la passphrase guardada (nuevo formato y legado) y la clave de Keystore. Usado por Recovery. */
    fun clearStoredPassphrase() {
        preferences().edit { remove(KEY_DATABASE_PASSPHRASE) }
        clearLegacyPreferences()
        runCatching {
            KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }.deleteEntry(KEY_ALIAS)
        }
    }

    private fun readStoredPassphrase(): ByteArray? {
        val payload = preferences().getString(KEY_DATABASE_PASSPHRASE, null) ?: return null
        val combined = Base64.decode(payload, Base64.NO_WRAP)
        require(combined.size > IV_BYTES) { "Stored database passphrase payload is malformed" }
        val iv = combined.copyOfRange(0, IV_BYTES)
        val ciphertext = combined.copyOfRange(IV_BYTES, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
        }
        return cipher.doFinal(ciphertext)
    }

    private fun storePassphrase(passphrase: ByteArray) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val ciphertext = cipher.doFinal(passphrase)
        val payload = Base64.encodeToString(cipher.iv + ciphertext, Base64.NO_WRAP)
        preferences().edit { putString(KEY_DATABASE_PASSPHRASE, payload) }
    }

    private fun readLegacyPassphrase(): ByteArray? {
        val encoded = legacyPreferences().getString(LEGACY_KEY_DATABASE_PASSPHRASE, null) ?: return null
        return Base64.decode(encoded, Base64.NO_WRAP)
    }

    private fun clearLegacyPreferences() {
        runCatching { legacyPreferences().edit { remove(LEGACY_KEY_DATABASE_PASSPHRASE) } }
    }

    private fun databaseFileExists(): Boolean = context.getDatabasePath(AppDatabase.DATABASE_NAME).exists()

    private fun preferences(): SharedPreferences = context.getSharedPreferences(SECURE_PREFS, Context.MODE_PRIVATE)

    private fun legacyPreferences(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            LEGACY_SECURE_PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private companion object {
        // Nuevo almacen: SharedPreferences planas, el valor guardado ya viene cifrado con la
        // clave de Keystore (ver secretKey()). Nombre distinto del legado para no colisionar.
        const val SECURE_PREFS = "atlas_peak_db_secure"
        const val KEY_DATABASE_PASSPHRASE = "database_passphrase"

        // Almacen legado (EncryptedSharedPreferences / androidx.security.crypto), solo lectura
        // para migrar y luego borrar.
        const val LEGACY_SECURE_PREFS = "atlas_peak_secure"
        const val LEGACY_KEY_DATABASE_PASSPHRASE = "database_passphrase"

        const val KEY_SIZE_BYTES = 32

        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "atlas_peak_database_passphrase_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val KEY_SIZE_BITS = 256
    }
}
