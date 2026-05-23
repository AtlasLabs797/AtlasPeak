package com.atlaspeak.domain.security

data class PasswordHash(
    val hashBase64: String,
    val saltBase64: String,
)

interface PasswordHasher {
    suspend fun hashPassword(password: CharArray): PasswordHash
    suspend fun verifyPassword(password: CharArray, storedHash: PasswordHash): Boolean
}
