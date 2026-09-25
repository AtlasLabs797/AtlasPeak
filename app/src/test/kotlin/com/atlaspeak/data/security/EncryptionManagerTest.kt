package com.atlaspeak.data.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EncryptionManagerTest {
    @Test
    fun `password hashing uses the required PBKDF2 parameters`() {
        assertEquals(600_000, EncryptionManager.PASSWORD_ITERATIONS)
        assertEquals(256, EncryptionManager.PASSWORD_KEY_BITS)
        assertEquals("PBKDF2WithHmacSHA256", EncryptionManager.PASSWORD_ALGORITHM)
    }
}
