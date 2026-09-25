package com.atlaspeak.presentation.body

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BodyCompositionDraftTest {
    @Test
    fun `raw validation rejects unparseable fields before partial save`() {
        val draft = BodyCompositionDraft(
            weightKg = "80",
            bodyFatPercent = "abc",
        )

        assertFalse(draft.isValidRaw())
    }

    @Test
    fun `raw validation accepts comma decimals`() {
        val draft = BodyCompositionDraft(weightKg = "80,5")

        assertTrue(draft.isValidRaw())
    }

    @Test
    fun `raw validation rejects impossible body composition ranges`() {
        assertFalse(BodyCompositionDraft(bodyFatPercent = "101").isValidRaw())
        assertFalse(BodyCompositionDraft(waterPercent = "-1").isValidRaw())
        assertFalse(BodyCompositionDraft(bodyAge = "180").isValidRaw())
        assertFalse(BodyCompositionDraft(weightKg = "0").isValidRaw())
    }

    @Test
    fun `raw validation routes through the shared BodyCompositionValidation ranges`() {
        // BUG-099 (Fase 10 P2): antes isValidRaw() aceptaba bodyAge hasta 130
        // y muscle/water/bone mass hasta 500, divergiendo de
        // BodyCompositionUseCase (120, 250, 250, 20). Ahora ambos comparten
        // BodyCompositionValidation, asi que estos valores fuera del rango
        // compartido deben rechazarse.
        assertFalse(BodyCompositionDraft(bodyAge = "125").isValidRaw())
        assertTrue(BodyCompositionDraft(bodyAge = "120").isValidRaw())
        assertFalse(BodyCompositionDraft(muscleMassKg = "300").isValidRaw())
        assertTrue(BodyCompositionDraft(muscleMassKg = "250").isValidRaw())
        assertFalse(BodyCompositionDraft(bodyWaterMassKg = "300").isValidRaw())
        assertFalse(BodyCompositionDraft(boneMassKg = "25").isValidRaw())
        assertTrue(BodyCompositionDraft(boneMassKg = "20").isValidRaw())
    }
}
