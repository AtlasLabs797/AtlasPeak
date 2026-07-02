package com.atlaspeak.data.backup

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Almacén seguro de la passphrase de auto-backup (SEC-034).
 *
 * Antes: el password se guardaba como String en EncryptedSharedPreferences. Eso
 * significa que en memoria JVM vivía como String inmutable (copias múltiples hasta
 * GC), y el valor "cifrado" en disco era esencialmente el password en claro envuelto
 * en otra capa de cifrado derivable del propio dispositivo. Si alguien extraía
 * SharedPreferences o hacía un dump de memoria, recuperaba la passphrase.
 *
 * Ahora: derivamos una clave AES-256-GCM **dentro del Android Keystore** (hardware-
 * backed cuando el dispositivo lo soporta). El password se cifra con esa clave y
 * solo el ciphertext (IV + tag incluidos) se persiste en SharedPreferences plano.
 * Para descifrar hace falta la clave, que nunca sale del Keystore.
 *
 * Riesgo residual documentado: si el atacante compromete el Keystore del dispositivo
 * (root + keystore software, o exploit de hardware), puede descifrar. Es un modelo
 * estándar en apps Android; no es perfecto pero es lo mejor sin reintroducir
 * autenticación biométrica que rompía el flujo "auto-backup silencioso".
 */
@Singleton
class BackupCredentialStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun saveAutoBackupPassword(password: CharArray) = withContext(Dispatchers.IO) {
        require(password.isNotEmpty()) { "Password must not be empty" }
        val plaintext = password.toUtf8Bytes()
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
            val ciphertext = cipher.doFinal(plaintext)
            val payload = Base64.encodeToString(cipher.iv + ciphertext, Base64.NO_WRAP)
            preferences().edit { putString(KEY_AUTO_BACKUP_CREDENTIAL, payload) }
        } finally {
            plaintext.fill(0)
            password.fill('\u0000')
        }
    }

    suspend fun autoBackupPassword(): CharArray? = withContext(Dispatchers.IO) {
        val payload = preferences().getString(KEY_AUTO_BACKUP_CREDENTIAL, null) ?: return@withContext null
        runCatching {
            val combined = Base64.decode(payload, Base64.NO_WRAP)
            require(combined.size > IV_BYTES) { "Stored credential payload is malformed" }
            val iv = combined.copyOfRange(0, IV_BYTES)
            val ciphertext = combined.copyOfRange(IV_BYTES, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
            }
            val plaintext = cipher.doFinal(ciphertext)
            plaintext.toCharArrayUtf8().also {
                // Limpiamos el buffer intermedio de plaintext (mejor esfuerzo).
                plaintext.fill(0)
            }
        }.getOrElse {
            // Si falla (Keystore wiped, payload corrupto), limpiamos para forzar
            // al usuario a re-introducir la passphrase en lugar de seguir con
            // auto-backup silenciosamente roto.
            preferences().edit { remove(KEY_AUTO_BACKUP_CREDENTIAL) }
            null
        }
    }

    suspend fun clearAutoBackupPassword() = withContext(Dispatchers.IO) {
        preferences().edit { remove(KEY_AUTO_BACKUP_CREDENTIAL) }
    }

    suspend fun lastSnapshotHash(): String? = withContext(Dispatchers.IO) {
        preferences().getString(KEY_LAST_SNAPSHOT_HASH, null)
    }

    suspend fun saveLastSnapshotHash(hash: String) = withContext(Dispatchers.IO) {
        preferences().edit { putString(KEY_LAST_SNAPSHOT_HASH, hash) }
    }

    private fun preferences(): SharedPreferences =
        context.getSharedPreferences(SECURE_PREFS, Context.MODE_PRIVATE)

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

    private fun CharArray.toUtf8Bytes(): ByteArray {
        val encoder = Charsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val scratch = ByteBuffer.allocate((size * encoder.maxBytesPerChar()).toInt().coerceAtLeast(1))
        return try {
            encoder.encode(CharBuffer.wrap(this), scratch, true)
            encoder.flush(scratch)
            scratch.array().copyOf(scratch.position())
        } finally {
            scratch.array().fill(0)
        }
    }

    private fun ByteArray.toCharArrayUtf8(): CharArray {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val scratch = CharBuffer.allocate(size.coerceAtLeast(1))
        return try {
            decoder.decode(ByteBuffer.wrap(this), scratch, true)
            decoder.flush(scratch)
            scratch.array().copyOf(scratch.position())
        } finally {
            scratch.array().fill('\u0000')
        }
    }

    private companion object {
        const val SECURE_PREFS = "atlas_peak_backup_secure"
        const val KEY_AUTO_BACKUP_CREDENTIAL = "auto_backup_credential"
        const val KEY_LAST_SNAPSHOT_HASH = "last_snapshot_hash"

        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "atlas_peak_backup_credential_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val KEY_SIZE_BITS = 256
    }
}
