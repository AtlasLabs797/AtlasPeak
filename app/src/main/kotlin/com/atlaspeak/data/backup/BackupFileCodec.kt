package com.atlaspeak.data.backup

import com.atlaspeak.data.security.EncryptionManager
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BackupFileCodec(
    private val secureRandom: SecureRandom = SecureRandom(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun encrypt(
        plaintext: ByteArray,
        password: CharArray,
    ): ByteArray = withContext(dispatcher) {
        val salt = ByteArray(SALT_BYTES).also(secureRandom::nextBytes)
        val iv = ByteArray(EncryptionManager.AES_GCM_IV_BYTES).also(secureRandom::nextBytes)
        val key = deriveBackupKey(password, salt, ITERATIONS)
        val cipher = Cipher.getInstance(EncryptionManager.AES_GCM_TRANSFORMATION)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, EncryptionManager.AES_ALGORITHM),
            GCMParameterSpec(EncryptionManager.AES_GCM_TAG_BITS, iv),
        )
        val ciphertext = cipher.doFinal(plaintext)

        ByteBuffer.allocate(HEADER_BYTES + ciphertext.size)
            .order(ByteOrder.BIG_ENDIAN)
            .put(MAGIC)
            .put(VERSION.toByte())
            .putInt(ITERATIONS)
            .put(salt)
            .put(iv)
            .put(ciphertext)
            .array()
    }

    suspend fun decrypt(
        encrypted: ByteArray,
        password: CharArray,
    ): ByteArray = withContext(dispatcher) {
        val header = parseHeader(encrypted)
        val ciphertext = encrypted.copyOfRange(HEADER_BYTES, encrypted.size)
        val key = deriveBackupKey(password, header.salt, header.iterations)
        val cipher = Cipher.getInstance(EncryptionManager.AES_GCM_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, EncryptionManager.AES_ALGORITHM),
            GCMParameterSpec(EncryptionManager.AES_GCM_TAG_BITS, header.iv),
        )
        cipher.doFinal(ciphertext)
    }

    fun parseHeader(encrypted: ByteArray): Header {
        require(encrypted.size > HEADER_BYTES) { "Invalid backup file" }
        require(encrypted.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) { "Invalid backup magic" }

        val buffer = ByteBuffer.wrap(encrypted).order(ByteOrder.BIG_ENDIAN)
        val magic = ByteArray(MAGIC.size)
        buffer.get(magic)
        val version = buffer.get().toInt()
        require(version == VERSION) { "Unsupported backup version" }
        val iterations = buffer.int
        require(iterations == ITERATIONS) { "Unsupported backup iterations" }
        val salt = ByteArray(SALT_BYTES).also(buffer::get)
        val iv = ByteArray(EncryptionManager.AES_GCM_IV_BYTES).also(buffer::get)

        return Header(
            version = version,
            iterations = iterations,
            salt = salt,
            iv = iv,
        )
    }

    private fun deriveBackupKey(
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
    ): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, EncryptionManager.PASSWORD_KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(EncryptionManager.PASSWORD_ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    data class Header(
        val version: Int,
        val iterations: Int,
        val salt: ByteArray,
        val iv: ByteArray,
    )

    companion object {
        private val MAGIC = byteArrayOf(0x41, 0x54, 0x50, 0x4B)
        const val VERSION = 1
        const val ITERATIONS = EncryptionManager.PASSWORD_ITERATIONS
        const val SALT_BYTES = 16
        const val HEADER_BYTES = 4 + 1 + 4 + SALT_BYTES + EncryptionManager.AES_GCM_IV_BYTES
    }
}
