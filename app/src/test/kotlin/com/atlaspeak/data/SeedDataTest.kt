package com.atlaspeak.data

import com.atlaspeak.data.db.seed.SeedData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SeedDataTest {
    @Test
    fun `seed data has stable ids and no duplicate presets`() {
        assertEquals(10, SeedData.muscleGroups.size)
        assertEquals(
            SeedData.muscleGroups.map { it.id }.distinct().size,
            SeedData.muscleGroups.size,
        )
        assertTrue(SeedData.exercises.size >= 20)
        assertEquals(
            SeedData.exercises.map { it.id }.distinct().size,
            SeedData.exercises.size,
        )
        assertEquals(7, SeedData.cardioTypes.size)
        assertEquals(
            SeedData.cardioTypes.map { it.id }.distinct().size,
            SeedData.cardioTypes.size,
        )
    }

    @Test
    fun `sync log seeds cover all health connect data types`() {
        assertEquals(
            setOf("STEPS", "ACTIVE_CALORIES", "SLEEP", "HEART_RATE", "WORKOUTS", "BODY_COMP"),
            SeedData.hcSyncLogs.map { it.dataType }.toSet(),
        )
    }
}
