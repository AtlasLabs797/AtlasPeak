package com.atlaspeak.presentation.auth

import com.atlaspeak.domain.model.auth.AuthSecurityState
import com.atlaspeak.domain.model.auth.GoogleSignInResult
import com.atlaspeak.domain.model.auth.LocalUser
import com.atlaspeak.domain.repository.AuthRepository
import com.atlaspeak.domain.security.PasswordHash
import com.atlaspeak.domain.security.PasswordHasher
import com.atlaspeak.domain.usecase.auth.LocalAuthUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `google sign in success never authenticates local data`() = runTest {
        val viewModel = AuthViewModel(
            localAuthUseCase = LocalAuthUseCase(FakeAuthRepository(passwordConfigured = true), FakePasswordHasher()),
        )
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onGoogleSignInResult(
            GoogleSignInResult.Success(
                idToken = "id-token",
                googleId = "google-id",
                email = "user@example.com",
                displayName = "Atlas User",
            ),
        )

        assertFalse(viewModel.state.value.isAuthenticated)
        assertEquals(AuthUiMessage.GoogleRequiresLocalPassword, viewModel.state.value.message)
    }

    private class FakePasswordHasher : PasswordHasher {
        override suspend fun hashPassword(password: CharArray): PasswordHash {
            return PasswordHash(hashBase64 = "hash", saltBase64 = "salt")
        }

        override suspend fun verifyPassword(password: CharArray, storedHash: PasswordHash): Boolean = true
    }

    private class FakeAuthRepository(
        passwordConfigured: Boolean,
    ) : AuthRepository {
        private var user = if (passwordConfigured) {
            LocalUser(
                id = "local-user",
                googleId = null,
                email = null,
                passwordHash = "hash",
                passwordSalt = "salt",
                createdAt = 1L,
                lastLoginAt = null,
            )
        } else {
            null
        }

        override suspend fun getLocalUser(): LocalUser? = user

        override suspend fun createOrUpdateLocalPassword(passwordHash: PasswordHash, nowMillis: Long) = Unit

        override suspend fun markLogin(nowMillis: Long) {
            user = user?.copy(lastLoginAt = nowMillis)
        }

        override suspend fun isBiometricUnlockEnabled(): Boolean = false

        override suspend fun setBiometricUnlockEnabled(enabled: Boolean) = Unit

        override suspend fun getAuthSecurityState(): AuthSecurityState = AuthSecurityState()

        override suspend fun recordFailedPasswordAttempt(nowMillis: Long): AuthSecurityState = AuthSecurityState()

        override suspend fun resetAuthSecurity() = Unit
    }
}
