package com.atlaspeak.domain.model.auth

object LocalAuthPolicy {
    const val MIN_PASSWORD_LENGTH = 8
    const val MAX_FAILED_ATTEMPTS = 5
    const val LOCKOUT_MILLIS = 15 * 60 * 1_000L
}
