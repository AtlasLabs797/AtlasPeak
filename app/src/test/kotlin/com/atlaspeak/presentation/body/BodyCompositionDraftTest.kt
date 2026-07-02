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
}
