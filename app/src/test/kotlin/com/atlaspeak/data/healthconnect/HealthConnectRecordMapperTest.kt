package com.atlaspeak.data.healthconnect

import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyWaterMassRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.WeightRecord
import com.atlaspeak.data.db.dao.HealthConnectWorkoutExportRow
import com.atlaspeak.data.db.entity.BodyCompositionEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HealthConnectRecordMapperTest {
    @Test
    fun `body mapper exports only health connect body record types`() {
        val exports = HealthConnectRecordMapper.bodyRecords(
            listOf(
                BodyCompositionEntity(
                    id = "body-1",
                    measuredAt = 1_700_000_000_000,
                    weightKg = 82.5,
                    bodyFatPercent = 18.0,
                    muscleMassKg = 63.0,
                    waterPercent = 55.0,
                    bodyWaterMassKg = 45.0,
                    visceralFatLevel = 8,
                    proteinPercent = 17.0,
                    boneMassKg = 3.2,
                    bodyAge = 35,
                    source = "MANUAL",
                    syncedToHc = false,
                    createdAt = 1_700_000_000_500,
                ),
            ),
        )

        assertEquals(4, exports.size)
        assertTrue(exports.any { it.record is WeightRecord })
        assertTrue(exports.any { it.record is BodyFatRecord })
        assertTrue(exports.any { it.record is LeanBodyMassRecord })
        assertTrue(exports.any { it.record is BodyWaterMassRecord })
        assertFalse(exports.any { it.clientRecordId.contains("water_percent") })
        assertEquals(setOf("body-1"), exports.mapNotNull { it.localId }.toSet())
    }

    @Test
    fun `workout mapper exports strength and known cardio exercise types`() {
        val exports = HealthConnectRecordMapper.workoutRecords(
            listOf(
                HealthConnectWorkoutExportRow(
                    id = "strength-1",
                    type = "STRENGTH",
                    startTime = 1_700_000_000_000,
                    endTime = 1_700_003_600_000,
                    notes = null,
                    cardioTypeId = null,
                ),
                HealthConnectWorkoutExportRow(
                    id = "run-1",
                    type = "CARDIO",
                    startTime = 1_700_010_000_000,
                    endTime = 1_700_011_800_000,
                    notes = "Intervals",
                    cardioTypeId = "cardio_running_outdoor",
                ),
            ),
        )

        val records = exports.map { it.record as ExerciseSessionRecord }
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, records[0].exerciseType)
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, records[1].exerciseType)
        assertEquals("atlaspeak:workout:strength-1", exports[0].clientRecordId)
    }
}
