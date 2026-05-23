package com.atlaspeak.domain.usecase.auth

import androidx.biometric.BiometricManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BiometricAuthPolicyTest {
    private val policy = BiometricAuthPolicy()

    @Test
    fun `biometric policy only allows strong authenticators`() {
        assertEquals(
            BiometricManager.Authenticators.BIOMETRIC_STRONG,
            policy.allowedAuthenticators,
        )
    }

    @Test
    fun `biometric unlock requires local password and enabled setting`() {
        assertFalse(
            policy.shouldPromptForUnlock(
                biometricsEnabled = false,
                passwordConfigured = true,
                timeoutMinutes = 5,
                lastUnlockAtMillis = null,
                nowMillis = 1_000,
            ),
        )
        assertFalse(
            policy.shouldPromptForUnlock(
                biometricsEnabled = true,
                passwordConfigured = false,
                timeoutMinutes = 5,
                lastUnlockAtMillis = null,
                nowMillis = 1_000,
            ),
        )
        assertTrue(
            policy.shouldPromptForUnlock(
                biometricsEnabled = true,
                passwordConfigured = true,
                timeoutMinutes = 5,
                lastUnlockAtMillis = null,
                nowMillis = 1_000,
            ),
        )
    }

    @Test
    fun `biometric timeout supports one five fifteen minutes and never`() {
        assertFalse(policy.isSupportedTimeoutMinutes(2))
        assertTrue(policy.isSupportedTimeoutMinutes(1))
        assertTrue(policy.isSupportedTimeoutMinutes(5))
        assertTrue(policy.isSupportedTimeoutMinutes(15))
        assertTrue(policy.isSupportedTimeoutMinutes(BiometricAuthPolicy.TIMEOUT_NEVER))

        assertFalse(
            policy.shouldPromptForUnlock(
                biometricsEnabled = true,
                passwordConfigured = true,
                timeoutMinutes = BiometricAuthPolicy.TIMEOUT_NEVER,
                lastUnlockAtMillis = 1_000,
                nowMillis = Long.MAX_VALUE,
            ),
        )
        assertFalse(
            policy.shouldPromptForUnlock(
                biometricsEnabled = true,
                passwordConfigured = true,
                timeoutMinutes = 5,
                lastUnlockAtMillis = 1_000,
                nowMillis = 1_000 + 5 * 60_000 - 1,
            ),
        )
        assertTrue(
            policy.shouldPromptForUnlock(
                biometricsEnabled = true,
                passwordConfigured = true,
                timeoutMinutes = 5,
                lastUnlockAtMillis = 1_000,
                nowMillis = 1_000 + 5 * 60_000,
            ),
        )
    }
}
