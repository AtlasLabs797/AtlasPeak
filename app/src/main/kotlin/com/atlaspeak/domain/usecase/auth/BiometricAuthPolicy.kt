package com.atlaspeak.domain.usecase.auth

import androidx.biometric.BiometricManager
import javax.inject.Inject

class BiometricAuthPolicy @Inject constructor() {
    val allowedAuthenticators: Int = BiometricManager.Authenticators.BIOMETRIC_STRONG

    fun isSupportedTimeoutMinutes(timeoutMinutes: Int): Boolean {
        return timeoutMinutes == TIMEOUT_NEVER || timeoutMinutes in SUPPORTED_TIMEOUTS_MINUTES
    }

    fun shouldPromptForUnlock(
        biometricsEnabled: Boolean,
        passwordConfigured: Boolean,
        timeoutMinutes: Int,
        lastUnlockAtMillis: Long?,
        nowMillis: Long,
    ): Boolean {
        if (!biometricsEnabled) return false
        return shouldRequireUnlock(
            passwordConfigured = passwordConfigured,
            timeoutMinutes = timeoutMinutes,
            lastUnlockAtMillis = lastUnlockAtMillis,
            nowMillis = nowMillis,
        )
    }

    fun shouldRequireUnlock(
        passwordConfigured: Boolean,
        timeoutMinutes: Int,
        lastUnlockAtMillis: Long?,
        nowMillis: Long,
    ): Boolean {
        if (!passwordConfigured || timeoutMinutes == TIMEOUT_NEVER) return false
        if (!isSupportedTimeoutMinutes(timeoutMinutes)) return false
        val lastUnlock = lastUnlockAtMillis ?: return true
        return nowMillis - lastUnlock >= timeoutMinutes * 60_000L
    }

    companion object {
        const val TIMEOUT_NEVER = -1
        private val SUPPORTED_TIMEOUTS_MINUTES = setOf(1, 5, 15)
    }
}
