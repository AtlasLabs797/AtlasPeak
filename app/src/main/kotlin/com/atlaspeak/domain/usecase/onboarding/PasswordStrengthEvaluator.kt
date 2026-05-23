package com.atlaspeak.domain.usecase.onboarding

import com.atlaspeak.domain.model.onboarding.PasswordStrength
import javax.inject.Inject

class PasswordStrengthEvaluator @Inject constructor() {
    fun evaluate(password: String): PasswordStrength {
        if (password.length < MIN_LENGTH) return PasswordStrength.Weak

        var score = 0
        if (password.length >= MIN_LENGTH) score += 1
        if (password.length >= STRONG_LENGTH) score += 1
        if (password.any(Char::isLowerCase)) score += 1
        if (password.any(Char::isUpperCase)) score += 1
        if (password.any(Char::isDigit)) score += 1
        if (password.any { !it.isLetterOrDigit() }) score += 1

        return when {
            score >= STRONG_SCORE -> PasswordStrength.Strong
            score >= MEDIUM_SCORE -> PasswordStrength.Medium
            else -> PasswordStrength.Weak
        }
    }

    companion object {
        private const val MIN_LENGTH = 8
        private const val STRONG_LENGTH = 12
        private const val MEDIUM_SCORE = 2
        private const val STRONG_SCORE = 5
    }
}
