package com.atlaspeak.data.security

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EncryptionManagerTest {
    private val encryptionManager = EncryptionManager()

    @Test
    fun `password hashing uses the required PBKDF2 parameters`() {
        assertEquals(600_000, EncryptionManager.PASSWORD_ITERATIONS)
        assertEquals(32, EncryptionManager.PASSWORD_SALT_BYTES)
        assertEquals(256, EncryptionManager.PASSWORD_KEY_BITS)
        assertEquals("PBKDF2WithHmacSHA256", EncryptionManager.PASSWORD_ALGORITHM)
    }

    @Test
    fun `aes gcm decrypts only with the original payload`() = runTest {
        val key = ByteArray(32) { it.toByte() }
        val plaintext = "backup payload".encodeToByteArray()

        val encrypted = encryptionManager.encryptAesGcm(plaintext, key)
        val decrypted = encryptionManager.decryptAesGcm(encrypted, key)

        assertArrayEquals(plaintext, decrypted)
    }
}
