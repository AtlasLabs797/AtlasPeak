package com.atlaspeak.domain.usecase.auth

import com.atlaspeak.domain.model.auth.AuthSecurityState
import com.atlaspeak.domain.model.auth.LocalAuthPolicy
import com.atlaspeak.domain.model.auth.LocalAuthResult
import com.atlaspeak.domain.model.auth.LocalUser
import com.atlaspeak.domain.repository.AuthRepository
import com.atlaspeak.domain.security.PasswordHash
import com.atlaspeak.domain.security.PasswordHasher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocalAuthUseCaseTest {
    private val repository = FakeAuthRepository()
    private val passwordHasher = FakePasswordHasher()
    private var nowMillis = 1_000_000L
    private val useCase = LocalAuthUseCase(
        authRepository = repository,
        passwordHasher = passwordHasher,
        nowMillis = { nowMillis },
    )

    @Test
    fun `setPassword stores a PBKDF2 hash and enables successful local auth`() = runTest {
        assertFalse(useCase.isPasswordConfigured())

        val setupResult = useCase.setPassword("correct horse".toCharArray())
        val loginResult = useCase.authenticate("correct horse".toCharArray())

        assertEquals(LocalAuthResult.Success, setupResult)
        assertEquals(LocalAuthResult.Success, loginResult)
        assertTrue(useCase.isPasswordConfigured())
        assertEquals("hash:correct horse", repository.localUser?.passwordHash)
        assertEquals("salt:correct horse", repository.localUser?.passwordSalt)
        assertEquals(0, repository.securityState.failedAttempts)
        assertEquals(nowMillis, repository.localUser?.lastLoginAt)
    }

    @Test
    fun `wrong password increments failures and locks for fifteen minutes after five attempts`() = runTest {
        useCase.setPassword("correct horse".toCharArray())

        repeat(4) { attempt ->
            val result = useCase.authenticate("wrong horse".toCharArray())
            assertInstanceOf(LocalAuthResult.InvalidCredentials::class.java, result)
            assertEquals(attempt + 1, repository.securityState.failedAttempts)
        }

        val locked = useCase.authenticate("wrong horse".toCharArray())

        assertInstanceOf(LocalAuthResult.Locked::class.java, locked)
        assertEquals(5, repository.securityState.failedAttempts)
        assertEquals(nowMillis + LocalAuthPolicy.LOCKOUT_MILLIS, repository.securityState.lockedUntilMillis)
    }

    @Test
    fun `expired lock resets and correct password succeeds`() = runTest {
        useCase.setPassword("correct horse".toCharArray())
        repeat(5) { useCase.authenticate("wrong horse".toCharArray()) }

        nowMillis += LocalAuthPolicy.LOCKOUT_MILLIS + 1
        val result = useCase.authenticate("correct horse".toCharArray())

        assertEquals(LocalAuthResult.Success, result)
        assertEquals(0, repository.securityState.failedAttempts)
        assertEquals(null, repository.securityState.lockedUntilMillis)
        assertEquals(nowMillis, repository.localUser?.lastLoginAt)
    }

    @Test
    fun `biometric unlock cannot work before local password setup`() = runTest {
        assertEquals(LocalAuthResult.PasswordNotConfigured, useCase.recordBiometricUnlock())

        useCase.setPassword("correct horse".toCharArray())
        assertEquals(LocalAuthResult.Success, useCase.recordBiometricUnlock())
        assertEquals(nowMillis, repository.localUser?.lastLoginAt)
    }

    @Test
    fun `session unlock is required when timeout has elapsed`() = runTest {
        useCase.setPassword("correct horse".toCharArray())
        useCase.authenticate("correct horse".toCharArray())

        nowMillis += 5 * 60_000L

        assertTrue(useCase.shouldRequireSessionUnlock())
    }

    @Test
    fun `session unlock is skipped before timeout`() = runTest {
        useCase.setPassword("correct horse".toCharArray())
        useCase.authenticate("correct horse".toCharArray())

        nowMillis += 5 * 60_000L - 1

        assertFalse(useCase.shouldRequireSessionUnlock())
    }

    @Test
    fun `short passwords are rejected before hashing`() = runTest {
        val result = useCase.setPassword("short".toCharArray())

        assertEquals(LocalAuthResult.WeakPassword, result)
        assertEquals(null, repository.localUser)
        assertNotEquals("hash:short", repository.localUser?.passwordHash)
    }

    private class FakePasswordHasher : PasswordHasher {
        override suspend fun hashPassword(password: CharArray): PasswordHash {
            val value = password.concatToString()
            return PasswordHash(hashBase64 = "hash:$value", saltBase64 = "salt:$value")
        }

        override suspend fun verifyPassword(password: CharArray, storedHash: PasswordHash): Boolean {
            return storedHash.hashBase64 == "hash:${password.concatToString()}"
        }
    }

    private class FakeAuthRepository : AuthRepository {
        var localUser: LocalUser? = null
        var securityState = AuthSecurityState()
        var biometricsEnabled = false

        override suspend fun getLocalUser(): LocalUser? = localUser

        override suspend fun createOrUpdateLocalPassword(passwordHash: PasswordHash, nowMillis: Long) {
            val existing = localUser
            localUser = LocalUser(
                id = existing?.id ?: "local-user",
                googleId = existing?.googleId,
                email = existing?.email,
                passwordHash = passwordHash.hashBase64,
                passwordSalt = passwordHash.saltBase64,
                createdAt = existing?.createdAt ?: nowMillis,
                lastLoginAt = existing?.lastLoginAt,
            )
        }

        override suspend fun markLogin(nowMillis: Long) {
            localUser = localUser?.copy(lastLoginAt = nowMillis)
        }

        override suspend fun isBiometricUnlockEnabled(): Boolean = biometricsEnabled

        override suspend fun getUnlockTimeoutMinutes(): Int = 5

        override suspend fun setBiometricUnlockEnabled(enabled: Boolean) {
            biometricsEnabled = enabled
        }

        override suspend fun getAuthSecurityState(): AuthSecurityState = securityState

        override suspend fun recordFailedPasswordAttempt(nowMillis: Long): AuthSecurityState {
            val failedAttempts = securityState.failedAttempts + 1
            securityState = AuthSecurityState(
                failedAttempts = failedAttempts,
                lockedUntilMillis = if (failedAttempts >= LocalAuthPolicy.MAX_FAILED_ATTEMPTS) {
                    nowMillis + LocalAuthPolicy.LOCKOUT_MILLIS
                } else {
                    null
                },
            )
            return securityState
        }

        override suspend fun resetAuthSecurity() {
            securityState = AuthSecurityState()
        }
    }
}
