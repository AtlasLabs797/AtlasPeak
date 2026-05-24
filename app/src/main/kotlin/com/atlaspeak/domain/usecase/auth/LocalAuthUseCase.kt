package com.atlaspeak.domain.usecase.auth

import com.atlaspeak.domain.model.auth.LocalAuthResult
import com.atlaspeak.domain.model.auth.LocalAuthPolicy
import com.atlaspeak.domain.repository.AuthRepository
import com.atlaspeak.domain.security.PasswordHash
import com.atlaspeak.domain.security.PasswordHasher
import javax.inject.Inject
import kotlin.math.max

class LocalAuthUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val passwordHasher: PasswordHasher,
    private val biometricAuthPolicy: BiometricAuthPolicy,
) {
    private var nowMillis: () -> Long = { System.currentTimeMillis() }

    constructor(
        authRepository: AuthRepository,
        passwordHasher: PasswordHasher,
    ) : this(authRepository, passwordHasher, BiometricAuthPolicy())

    constructor(
        authRepository: AuthRepository,
        passwordHasher: PasswordHasher,
        nowMillis: () -> Long,
    ) : this(authRepository, passwordHasher, BiometricAuthPolicy()) {
        this.nowMillis = nowMillis
    }

    suspend fun isPasswordConfigured(): Boolean = authRepository.getLocalUser() != null

    suspend fun isBiometricUnlockEnabled(): Boolean = authRepository.isBiometricUnlockEnabled()

    suspend fun shouldRequireSessionUnlock(): Boolean {
        val user = authRepository.getLocalUser() ?: return false
        return biometricAuthPolicy.shouldRequireUnlock(
            passwordConfigured = true,
            timeoutMinutes = authRepository.getUnlockTimeoutMinutes(),
            lastUnlockAtMillis = user.lastLoginAt,
            nowMillis = nowMillis(),
        )
    }

    suspend fun setBiometricUnlockEnabled(enabled: Boolean) {
        authRepository.setBiometricUnlockEnabled(enabled)
    }

    suspend fun setPassword(password: CharArray): LocalAuthResult {
        return try {
            if (password.size < LocalAuthPolicy.MIN_PASSWORD_LENGTH) return LocalAuthResult.WeakPassword
            val hash = passwordHasher.hashPassword(password)
            authRepository.createOrUpdateLocalPassword(hash, nowMillis())
            authRepository.resetAuthSecurity()
            LocalAuthResult.Success
        } finally {
            password.fill(CLEARED_CHAR)
        }
    }

    suspend fun authenticate(password: CharArray): LocalAuthResult {
        return try {
            val user = authRepository.getLocalUser() ?: return LocalAuthResult.PasswordNotConfigured
            val now = nowMillis()
            val securityState = authRepository.getAuthSecurityState()
            val lockedUntil = securityState.lockedUntilMillis

            if (lockedUntil != null) {
                if (now < lockedUntil) return LocalAuthResult.Locked(lockedUntil)
                authRepository.resetAuthSecurity()
            }

            val verified = passwordHasher.verifyPassword(
                password = password,
                storedHash = PasswordHash(
                    hashBase64 = user.passwordHash,
                    saltBase64 = user.passwordSalt,
                ),
            )

            if (verified) {
                authRepository.resetAuthSecurity()
                authRepository.markLogin(now)
                LocalAuthResult.Success
            } else {
                val updatedState = authRepository.recordFailedPasswordAttempt(now)
                val updatedLockedUntil = updatedState.lockedUntilMillis
                if (updatedLockedUntil != null && now < updatedLockedUntil) {
                    LocalAuthResult.Locked(updatedLockedUntil)
                } else {
                    LocalAuthResult.InvalidCredentials(
                        attemptsRemaining = max(
                            0,
                            LocalAuthPolicy.MAX_FAILED_ATTEMPTS - updatedState.failedAttempts,
                        ),
                    )
                }
            }
        } finally {
            password.fill(CLEARED_CHAR)
        }
    }

    suspend fun recordBiometricUnlock(): LocalAuthResult {
        authRepository.getLocalUser() ?: return LocalAuthResult.PasswordNotConfigured
        authRepository.resetAuthSecurity()
        authRepository.markLogin(nowMillis())
        return LocalAuthResult.Success
    }

    companion object {
        private const val CLEARED_CHAR = '\u0000'
    }
}
