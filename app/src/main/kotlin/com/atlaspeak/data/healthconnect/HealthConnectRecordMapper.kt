package com.atlaspeak.data.healthconnect

import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyWaterMassRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import com.atlaspeak.data.db.dao.HealthConnectWorkoutExportRow
import com.atlaspeak.data.db.entity.BodyCompositionEntity
import java.time.Instant
import java.time.ZoneId
import androidx.health.connect.client.records.Record as HealthRecord

internal data class HealthConnectRecordExport(
    val record: HealthRecord,
    val clientRecordId: String,
    val localId: String? = null,
)

internal object HealthConnectRecordMapper {
    fun bodyRecords(entries: List<BodyCompositionEntity>): List<HealthConnectRecordExport> {
        return entries.flatMap { entry ->
            val time = Instant.ofEpochMilli(entry.measuredAt)
            val zoneOffset = zoneOffsetAt(time)
            buildList {
                entry.weightKg?.takeIf { it > 0.0 }?.let { weight ->
                    val clientId = clientRecordId("body:weight", entry.id)
                    add(
                        HealthConnectRecordExport(
                            record = WeightRecord(
                                time,
                                zoneOffset,
                                Mass.kilograms(weight),
                                Metadata.manualEntry(clientId, entry.createdAt),
                            ),
                            clientRecordId = clientId,
                            localId = entry.id,
                        ),
                    )
                }
                entry.bodyFatPercent?.takeIf { it in 0.0..100.0 }?.let { bodyFat ->
                    val clientId = clientRecordId("body:fat", entry.id)
                    add(
                        HealthConnectRecordExport(
                            record = BodyFatRecord(
                                time,
                                zoneOffset,
                                Percentage(bodyFat),
                                Metadata.manualEntry(clientId, entry.createdAt),
                            ),
                            clientRecordId = clientId,
                            localId = entry.id,
                        ),
                    )
                }
                entry.muscleMassKg?.takeIf { it > 0.0 }?.let { leanMass ->
                    val clientId = clientRecordId("body:lean_mass", entry.id)
                    add(
                        HealthConnectRecordExport(
                            record = LeanBodyMassRecord(
                                time,
                                zoneOffset,
                                Mass.kilograms(leanMass),
                                Metadata.manualEntry(clientId, entry.createdAt),
                            ),
                            clientRecordId = clientId,
                            localId = entry.id,
                        ),
                    )
                }
                entry.bodyWaterMassKg?.takeIf { it > 0.0 }?.let { bodyWater ->
                    val clientId = clientRecordId("body:water_mass", entry.id)
                    add(
                        HealthConnectRecordExport(
                            record = BodyWaterMassRecord(
                                time,
                                zoneOffset,
                                Mass.kilograms(bodyWater),
                                Metadata.manualEntry(clientId, entry.createdAt),
                            ),
                            clientRecordId = clientId,
                            localId = entry.id,
                        ),
                    )
                }
            }
        }
    }

    fun workoutRecords(rows: List<HealthConnectWorkoutExportRow>): List<HealthConnectRecordExport> {
        return rows.mapNotNull { row ->
            val endTimeMillis = row.endTime ?: return@mapNotNull null
            if (endTimeMillis <= row.startTime) return@mapNotNull null
            val start = Instant.ofEpochMilli(row.startTime)
            val end = Instant.ofEpochMilli(endTimeMillis)
            val clientId = clientRecordId("workout", row.id)
            HealthConnectRecordExport(
                record = ExerciseSessionRecord(
                    start,
                    zoneOffsetAt(start),
                    end,
                    zoneOffsetAt(end),
                    Metadata.manualEntry(clientId, endTimeMillis),
                    row.exerciseType(),
                    null,
                    row.notes?.takeIf { it.isNotBlank() },
                ),
                clientRecordId = clientId,
            )
        }
    }

    private fun HealthConnectWorkoutExportRow.exerciseType(): Int {
        if (type == TYPE_STRENGTH) return ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING
        return when (cardioTypeId) {
            "cardio_running_outdoor" -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING
            "cardio_treadmill" -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL
            "cardio_cycling_outdoor" -> ExerciseSessionRecord.EXERCISE_TYPE_BIKING
            "cardio_static_bike" -> ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY
            "cardio_elliptical" -> ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL
            "cardio_rowing" -> ExerciseSessionRecord.EXERCISE_TYPE_ROWING
            "cardio_swimming" -> ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL
            else -> ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT
        }
    }

    private fun clientRecordId(type: String, localId: String): String = "atlaspeak:$type:$localId"

    private fun zoneOffsetAt(time: Instant) = ZoneId.systemDefault().rules.getOffset(time)

    private const val TYPE_STRENGTH = "STRENGTH"
}
