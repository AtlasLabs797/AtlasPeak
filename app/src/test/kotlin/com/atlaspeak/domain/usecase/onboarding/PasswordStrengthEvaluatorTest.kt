package com.atlaspeak.domain.usecase.onboarding

import com.atlaspeak.domain.model.onboarding.PasswordStrength
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PasswordStrengthEvaluatorTest {
    private val evaluator = PasswordStrengthEvaluator()

    @Test
    fun `password strength rejects short passwords`() {
        assertEquals(PasswordStrength.Weak, evaluator.evaluate("short"))
    }

    @Test
    fun `password strength rewards length and character diversity`() {
        assertEquals(PasswordStrength.Medium, evaluator.evaluate("longenough"))
        assertEquals(PasswordStrength.Strong, evaluator.evaluate("LongEnough42!"))
    }
}
