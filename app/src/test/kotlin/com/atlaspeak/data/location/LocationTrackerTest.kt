package com.atlaspeak.data.location

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocationTrackerTest {
    @Test
    fun `location is accepted when accuracy and age are reasonable`() {
        val now = 1_800_000_000_000L

        assertTrue(
            isUsableForCardioTracking(
                hasAccuracy = true,
                accuracyMeters = 12f,
                locationTimeMillis = now - 1_000,
                nowMillis = now,
            ),
        )
    }

    @Test
    fun `location is rejected when accuracy is too poor`() {
        val now = 1_800_000_000_000L

        assertFalse(
            isUsableForCardioTracking(
                hasAccuracy = true,
                accuracyMeters = 45f,
                locationTimeMillis = now - 1_000,
                nowMillis = now,
            ),
        )
    }

    @Test
    fun `location is rejected when it is stale or from the future`() {
        val now = 1_800_000_000_000L

        assertFalse(
            isUsableForCardioTracking(
                hasAccuracy = true,
                accuracyMeters = 10f,
                locationTimeMillis = now - 11_000,
                nowMillis = now,
            ),
        )
        assertFalse(
            isUsableForCardioTracking(
                hasAccuracy = true,
                accuracyMeters = 10f,
                locationTimeMillis = now + 1,
                nowMillis = now,
            ),
        )
    }

    @Test
    fun `location without accuracy is rejected`() {
        val now = 1_800_000_000_000L

        assertFalse(
            isUsableForCardioTracking(
                hasAccuracy = false,
                accuracyMeters = 0f,
                locationTimeMillis = now,
                nowMillis = now,
            ),
        )
    }
}
