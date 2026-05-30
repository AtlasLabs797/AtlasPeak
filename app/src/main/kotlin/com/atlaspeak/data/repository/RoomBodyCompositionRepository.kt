package com.atlaspeak.data.repository

import com.atlaspeak.data.db.AppDatabase
import com.atlaspeak.data.db.entity.BodyCompositionEntity
import com.atlaspeak.domain.model.body.BodyCompositionEntry
import com.atlaspeak.domain.model.body.BodyCompositionSource
import com.atlaspeak.domain.repository.BodyCompositionRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomBodyCompositionRepository @Inject constructor(
    private val database: AppDatabase,
) : BodyCompositionRepository {
    override suspend fun entries(): List<BodyCompositionEntry> {
        return database.bodyCompositionDao().getEntries().map { it.toDomain() }
    }

    override suspend fun upsert(entry: BodyCompositionEntry) {
        database.bodyCompositionDao().upsert(entry.toEntity())
    }

    private fun BodyCompositionEntity.toDomain() = BodyCompositionEntry(
        id = id,
        measuredAt = measuredAt,
        weightKg = weightKg,
        bodyFatPercent = bodyFatPercent,
        muscleMassKg = muscleMassKg,
        waterPercent = waterPercent,
        bodyWaterMassKg = bodyWaterMassKg,
        visceralFatLevel = visceralFatLevel,
        proteinPercent = proteinPercent,
        boneMassKg = boneMassKg,
        bodyAge = bodyAge,
        source = when (source) {
            SOURCE_HEALTH_CONNECT -> BodyCompositionSource.HealthConnect
            SOURCE_SCALE_APP -> BodyCompositionSource.ScaleApp
            else -> BodyCompositionSource.Manual
        },
        syncedToHealthConnect = syncedToHc,
        createdAt = createdAt,
    )

    private fun BodyCompositionEntry.toEntity() = BodyCompositionEntity(
        id = id,
        measuredAt = measuredAt,
        weightKg = weightKg,
        bodyFatPercent = bodyFatPercent,
        muscleMassKg = muscleMassKg,
        waterPercent = waterPercent,
        bodyWaterMassKg = bodyWaterMassKg,
        visceralFatLevel = visceralFatLevel,
        proteinPercent = proteinPercent,
        boneMassKg = boneMassKg,
        bodyAge = bodyAge,
        source = when (source) {
            BodyCompositionSource.Manual -> SOURCE_MANUAL
            BodyCompositionSource.HealthConnect -> SOURCE_HEALTH_CONNECT
            BodyCompositionSource.ScaleApp -> SOURCE_SCALE_APP
        },
        syncedToHc = syncedToHealthConnect,
        createdAt = createdAt,
    )

    private companion object {
        const val SOURCE_MANUAL = "MANUAL"
        const val SOURCE_HEALTH_CONNECT = "HEALTH_CONNECT"
        const val SOURCE_SCALE_APP = "SCALE_APP"
    }
}
