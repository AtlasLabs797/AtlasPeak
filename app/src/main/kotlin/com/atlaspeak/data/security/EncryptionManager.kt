package com.atlaspeak.data.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class EncryptionManager @Inject constructor() {
    private val secureRandom = SecureRandom()

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
