package com.atlaspeak.presentation.navigation

import com.atlaspeak.domain.model.auth.AuthSecurityState
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionLockViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private var nowMillis = 1_000_000L

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `sensitive route locks after configured timeout`() = runTest {
        val repository = FakeAuthRepository(lastLoginAt = 1_000_000L)
        val viewModel = SessionLockViewModel(
            LocalAuthUseCase(repository, NoopPasswordHasher(), { nowMillis }),
        )

        nowMillis += 5 * 60_000L
        viewModel.evaluate(AppRoute.Home.route)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.lockRequired)
    }

    @Test
    fun `login and launch routes never trigger session lock`() = runTest {
        val repository = FakeAuthRepository(lastLoginAt = 1L)
        val viewModel = SessionLockViewModel(
            LocalAuthUseCase(repository, NoopPasswordHasher(), { Long.MAX_VALUE }),
        )

        viewModel.evaluate(AppRoute.Login.route)
        assertFalse(viewModel.state.value.lockRequired)
        viewModel.evaluate(AppRoute.Launch.route)
        assertFalse(viewModel.state.value.lockRequired)
    }

    private class NoopPasswordHasher : PasswordHasher {
        override suspend fun hashPassword(password: CharArray): PasswordHash =
            PasswordHash(hashBase64 = "", saltBase64 = "")

        override suspend fun verifyPassword(password: CharArray, storedHash: PasswordHash): Boolean = true
    }

    private class FakeAuthRepository(
        private val lastLoginAt: Long?,
    ) : AuthRepository {
        override suspend fun getLocalUser(): LocalUser = LocalUser(
            id = "local-user",
            googleId = null,
            email = null,
            passwordHash = "hash",
            passwordSalt = "salt",
            createdAt = 1L,
            lastLoginAt = lastLoginAt,
        )

        override suspend fun createOrUpdateLocalPassword(passwordHash: PasswordHash, nowMillis: Long) = Unit

        override suspend fun markLogin(nowMillis: Long) = Unit

        override suspend fun isBiometricUnlockEnabled(): Boolean = true

        override suspend fun getUnlockTimeoutMinutes(): Int = 5

        override suspend fun setBiometricUnlockEnabled(enabled: Boolean) = Unit

        override suspend fun getAuthSecurityState(): AuthSecurityState = AuthSecurityState()

        override suspend fun recordFailedPasswordAttempt(nowMillis: Long): AuthSecurityState = AuthSecurityState()

        override suspend fun resetAuthSecurity() = Unit
    }
}
