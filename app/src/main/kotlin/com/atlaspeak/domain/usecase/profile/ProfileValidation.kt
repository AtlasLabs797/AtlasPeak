package com.atlaspeak.domain.usecase.profile

/**
 * Limites compartidos para los campos del perfil. BUG-099 (Fase 10 P2).
 *
 * Antes `EditProfileViewModel` y `OnboardingViewModel` tenian reglas
 * distintas (el onboarding ni siquiera validaba rangos); `BodyCompositionDraft`
 * y `BodyCompositionUseCase` duplicaban rangos divergentes para grasa, masa
 * muscular, agua y masa osea. Aqui concentramos la verdad y dejamos que la UI
 * siga haciendo validacion temprana, pero reutilizando estos limites.
 */
object ProfileValidation {
    const val MIN_AGE: Int = 10
    const val MAX_AGE: Int = 120
    const val MIN_HEIGHT_CM: Double = 80.0
    const val MAX_HEIGHT_CM: Double = 250.0
    const val MAX_AGE_LENGTH: Int = 3
    const val MAX_HEIGHT_LENGTH: Int = 6

    fun ageIsValid(value: String): Boolean {
        if (value.isBlank()) return false
        val parsed = value.toIntOrNull() ?: return false
        return parsed in MIN_AGE..MAX_AGE
    }

    fun heightIsValid(value: String): Boolean {
        if (value.isBlank()) return false
        val parsed = value.toDoubleOrNull() ?: return false
        return parsed in MIN_HEIGHT_CM..MAX_HEIGHT_CM
    }
}