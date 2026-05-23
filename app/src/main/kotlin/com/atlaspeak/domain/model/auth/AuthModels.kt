package com.atlaspeak.domain.model.auth

data class LocalUser(
    val id: String,
    val googleId: String?,
    val email: String?,
    val passwordHash: String,
    val passwordSalt: String,
    val createdAt: Long,
    val lastLoginAt: Long?,
)

data class AuthSecurityState(
    val failedAttempts: Int = 0,
    val lockedUntilMillis: Long? = null,
)

sealed interface LocalAuthResult {
    data object Success : LocalAuthResult
    data object PasswordNotConfigured : LocalAuthResult
    data object WeakPassword : LocalAuthResult
    data class InvalidCredentials(val attemptsRemaining: Int) : LocalAuthResult
    data class Locked(val lockedUntilMillis: Long) : LocalAuthResult
}

data class GoogleAccount(
    val idToken: String,
    val googleId: String?,
    val email: String?,
    val displayName: String?,
)

sealed interface GoogleSignInResult {
    data class Success(
        val idToken: String,
        val googleId: String?,
        val email: String?,
        val displayName: String?,
    ) : GoogleSignInResult

    data object Cancelled : GoogleSignInResult
    data object NotConfigured : GoogleSignInResult
    data object Failed : GoogleSignInResult
}
