package com.atlaspeak.data.security

import com.atlaspeak.domain.security.PasswordHash
import com.atlaspeak.domain.security.PasswordHasher
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.InvalidKeySpecException
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class EncryptionManager @Inject constructor() : PasswordHasher {
    private val secureRandom = SecureRandom()
    private val base64Encoder = Base64.getEncoder()
    private val base64Decoder = Base64.getDecoder()

    override suspend fun hashPassword(password: CharArray): PasswordHash = withContext(Dispatchers.IO) {
        val salt = ByteArray(PASSWORD_SALT_BYTES).also(secureRandom::nextBytes)
        PasswordHash(
            hashBase64 = base64Encoder.encodeToString(derivePasswordKey(password, salt)),
            saltBase64 = base64Encoder.encodeToString(salt),
        )
    }

    override suspend fun verifyPassword(
        password: CharArray,
        storedHash: PasswordHash,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val salt = base64Decoder.decode(storedHash.saltBase64)
            val expected = base64Decoder.decode(storedHash.hashBase64)
            val actual = derivePasswordKey(password, salt)
            MessageDigest.isEqual(expected, actual)
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: InvalidKeySpecException) {
            false
        }
    }

    suspend fun encryptAesGcm(
        plaintext: ByteArray,
        key: ByteArray,
        aad: ByteArray? = null,
    ): AesGcmPayload = withContext(Dispatchers.IO) {
        val iv = ByteArray(AES_GCM_IV_BYTES).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, AES_ALGORITHM), GCMParameterSpec(AES_GCM_TAG_BITS, iv))
        aad?.let(cipher::updateAAD)
        AesGcmPayload(iv = iv, ciphertext = cipher.doFinal(plaintext))
    }

    suspend fun decryptAesGcm(
        payload: AesGcmPayload,
        key: ByteArray,
        aad: ByteArray? = null,
    ): ByteArray = withContext(Dispatchers.IO) {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, AES_ALGORITHM),
            GCMParameterSpec(AES_GCM_TAG_BITS, payload.iv),
        )
        aad?.let(cipher::updateAAD)
        cipher.doFinal(payload.ciphertext)
    }

    private fun derivePasswordKey(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, PASSWORD_ITERATIONS, PASSWORD_KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(PASSWORD_ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    data class AesGcmPayload(
        val iv: ByteArray,
        val ciphertext: ByteArray,
    )

    companion object {
        const val PASSWORD_ALGORITHM = "PBKDF2WithHmacSHA256"
        const val PASSWORD_ITERATIONS = 600_000
        const val PASSWORD_SALT_BYTES = 32
        const val PASSWORD_KEY_BITS = 256
        const val AES_ALGORITHM = "AES"
        const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        const val AES_GCM_IV_BYTES = 12
        const val AES_GCM_TAG_BITS = 128
    }
}
