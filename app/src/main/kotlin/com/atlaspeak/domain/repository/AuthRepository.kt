package com.atlaspeak.domain.repository

import com.atlaspeak.domain.model.auth.AuthSecurityState
import com.atlaspeak.domain.model.auth.LocalUser
import com.atlaspeak.domain.security.PasswordHash

interface AuthRepository {
    suspend fun getLocalUser(): LocalUser?
    suspend fun createOrUpdateLocalPassword(passwordHash: PasswordHash, nowMillis: Long)
    suspend fun markLogin(nowMillis: Long)
    suspend fun isBiometricUnlockEnabled(): Boolean
    suspend fun setBiometricUnlockEnabled(enabled: Boolean)
    suspend fun getAuthSecurityState(): AuthSecurityState
    suspend fun recordFailedPasswordAttempt(nowMillis: Long): AuthSecurityState
    suspend fun resetAuthSecurity()
}
