package com.atlaspeak.data.security

import com.atlaspeak.domain.security.PasswordHash
import java.util.Base64
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
    fun `hashPassword creates random 32 byte salts and verifies only the correct password`() = runTest {
        val firstHash = encryptionManager.hashPassword("correct horse".toCharArray())
        val secondHash = encryptionManager.hashPassword("correct horse".toCharArray())

        assertEquals(32, Base64.getDecoder().decode(firstHash.saltBase64).size)
        assertEquals(32, Base64.getDecoder().decode(secondHash.saltBase64).size)
        assertNotEquals(firstHash.saltBase64, secondHash.saltBase64)
        assertNotEquals(firstHash.hashBase64, secondHash.hashBase64)
        assertTrue(encryptionManager.verifyPassword("correct horse".toCharArray(), firstHash))
        assertFalse(encryptionManager.verifyPassword("wrong horse".toCharArray(), firstHash))
    }

    @Test
    fun `verifyPassword rejects malformed stored hashes`() = runTest {
        val malformed = PasswordHash(hashBase64 = "not-base64", saltBase64 = "also-not-base64")

        assertFalse(encryptionManager.verifyPassword("correct horse".toCharArray(), malformed))
    }
}
