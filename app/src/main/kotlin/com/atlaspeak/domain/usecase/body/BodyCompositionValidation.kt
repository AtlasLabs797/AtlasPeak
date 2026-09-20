package com.atlaspeak.domain.usecase.body

/**
 * Limites compartidos para composicion corporal. BUG-099 (Fase 10 P2).
 *
 * Antes `BodyCompositionDraft` (UI) y `BodyCompositionUseCase.BodyCompositionInput`
 * (dominio) usaban rangos divergentes:
 *   - muscleMassKg: 0..500 en la VM, 0..250 en el use case
 *   - bodyWaterMassKg: 0..500 en la VM, 0..250 en el use case
 *   - boneMassKg: 0..500 en la VM, 0..20 en el use case
 *   - bodyAge: 1..130 en la VM, 1..120 en el use case
 *
 * Aqui fijamos una sola fuente de verdad y dejamos ambas capas usarla.
 */
object BodyCompositionValidation {
    const val MAX_WEIGHT_KG: Double = 500.0
    const val MIN_WEIGHT_KG: Double = 1.0
    const val MAX_PERCENT: Double = 100.0
    const val MIN_PERCENT: Double = 0.0
    const val MAX_MUSCLE_MASS_KG: Double = 250.0
    const val MIN_MUSCLE_MASS_KG: Double = 0.0
    const val MAX_BODY_WATER_MASS_KG: Double = 250.0
    const val MIN_BODY_WATER_MASS_KG: Double = 0.0
    const val MAX_BONE_MASS_KG: Double = 20.0
    const val MIN_BONE_MASS_KG: Double = 0.0
    const val MIN_VISCERAL_FAT: Int = 1
    const val MAX_VISCERAL_FAT: Int = 100
    const val MIN_BODY_AGE: Int = 1
    const val MAX_BODY_AGE: Int = 120

    fun weightIsValid(value: Double?): Boolean =
        value != null && value in MIN_WEIGHT_KG..MAX_WEIGHT_KG

    fun percentIsValid(value: Double?): Boolean =
        value != null && value in MIN_PERCENT..MAX_PERCENT

    fun muscleMassIsValid(value: Double?): Boolean =
        value != null && value in MIN_MUSCLE_MASS_KG..MAX_MUSCLE_MASS_KG

    fun bodyWaterMassIsValid(value: Double?): Boolean =
        value != null && value in MIN_BODY_WATER_MASS_KG..MAX_BODY_WATER_MASS_KG

    fun boneMassIsValid(value: Double?): Boolean =
        value != null && value in MIN_BONE_MASS_KG..MAX_BONE_MASS_KG

    fun visceralFatIsValid(value: Int?): Boolean =
        value != null && value in MIN_VISCERAL_FAT..MAX_VISCERAL_FAT

    fun bodyAgeIsValid(value: Int?): Boolean =
        value != null && value in MIN_BODY_AGE..MAX_BODY_AGE
}