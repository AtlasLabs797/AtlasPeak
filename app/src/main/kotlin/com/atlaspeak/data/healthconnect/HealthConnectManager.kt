package com.atlaspeak.data.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyWaterMassRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.room.withTransaction
import com.atlaspeak.data.db.AppDatabase
import com.atlaspeak.data.db.entity.HcActiveCaloriesRecordEntity
import com.atlaspeak.data.db.entity.HcHeartRateSampleEntity
import com.atlaspeak.data.db.entity.HcSleepSessionEntity
import com.atlaspeak.data.db.entity.HcSleepStageEntity
import com.atlaspeak.data.db.entity.HcStepsRecordEntity
import com.atlaspeak.data.db.entity.HcSyncLogEntity
import com.atlaspeak.domain.model.healthconnect.HealthConnectAvailability
import com.atlaspeak.domain.model.healthconnect.HealthConnectSyncResult
import com.atlaspeak.domain.repository.HealthConnectRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthConnectManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
) : HealthConnectRepository {
    override fun requiredPermissions(): Set<String> = REQUIRED_PERMISSIONS

    override suspend fun availability(): HealthConnectAvailability {
        return when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.Available
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectAvailability.UpdateRequired
            else -> HealthConnectAvailability.Unavailable
        }
    }

    override suspend fun sync(): HealthConnectSyncResult {
        val availability = availability()
        if (availability != HealthConnectAvailability.Available) {
            return HealthConnectSyncResult(availability = availability)
        }

        val client = HealthConnectClient.getOrCreate(context)
        val grantedPermissions = client.permissionController.getGrantedPermissions()
        if (!grantedPermissions.containsAll(REQUIRED_PERMISSIONS)) {
            return HealthConnectSyncResult(
                availability = HealthConnectAvailability.Available,
                missingPermissions = true,
            )
        }

        val now = System.currentTimeMillis()
        val imported = importSteps(client, now) +
            importActiveCalories(client, now) +
            importSleep(client, now) +
            importHeartRate(client, now)
        val exported = exportWorkouts(client, now) + exportBodyComposition(client, now)
        return HealthConnectSyncResult(
            availability = HealthConnectAvailability.Available,
            importedRecords = imported,
            exportedRecords = exported,
        )
    }

    private suspend fun importSteps(client: HealthConnectClient, now: Long): Int {
        val startMillis = readWindowStartMillis(now)
        val records = aggregateDaily(startMillis, now) { start, end ->
            val steps = client.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                ),
            )[StepsRecord.COUNT_TOTAL] ?: 0L
            if (steps <= 0L) {
                null
            } else {
                val startMillis = start.toEpochMilli()
                val endMillis = end.toEpochMilli()
                HcStepsRecordEntity(
                    id = "aggregate:steps:$startMillis:$endMillis",
                    hcRecordId = "aggregate:steps:$startMillis:$endMillis",
                    sourcePackage = AGGREGATE_SOURCE,
                    lastModifiedAt = now,
                    recordingMethod = null,
                    importedAt = now,
                    startTime = startMillis,
                    endTime = endMillis,
                    startZoneOffset = zoneOffsetString(start),
                    endZoneOffset = zoneOffsetString(end),
                    count = steps,
                )
            }
        }
        database.withTransaction {
            ensureSyncLog(STEPS)
            database.healthConnectDao().deleteAggregateStepsInWindow(startMillis, now)
            database.healthConnectDao().upsertSteps(records)
            database.healthConnectDao().updateLastReadAt(STEPS, now)
        }
        return records.size
    }

    private suspend fun importActiveCalories(client: HealthConnectClient, now: Long): Int {
        val startMillis = readWindowStartMillis(now)
        val records = aggregateDaily(startMillis, now) { start, end ->
            val energy: Energy? = client.aggregate(
                AggregateRequest(
                    metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                ),
            )[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]
            val kilocalories = energy?.inKilocalories ?: 0.0
            if (kilocalories <= 0.0) {
                null
            } else {
                val startMillis = start.toEpochMilli()
                val endMillis = end.toEpochMilli()
                HcActiveCaloriesRecordEntity(
                    id = "aggregate:active_calories:$startMillis:$endMillis",
                    hcRecordId = "aggregate:active_calories:$startMillis:$endMillis",
                    sourcePackage = AGGREGATE_SOURCE,
                    lastModifiedAt = now,
                    recordingMethod = null,
                    importedAt = now,
                    startTime = startMillis,
                    endTime = endMillis,
                    startZoneOffset = zoneOffsetString(start),
                    endZoneOffset = zoneOffsetString(end),
                    kilocalories = kilocalories,
                )
            }
        }
        database.withTransaction {
            ensureSyncLog(ACTIVE_CALORIES)
            database.healthConnectDao().deleteAggregateActiveCaloriesInWindow(startMillis, now)
            database.healthConnectDao().upsertActiveCalories(records)
            database.healthConnectDao().updateLastReadAt(ACTIVE_CALORIES, now)
        }
        return records.size
    }

    private suspend fun importSleep(client: HealthConnectClient, now: Long): Int {
        val startMillis = readWindowStartMillis(now)
        val start = Instant.ofEpochMilli(startMillis)
        val end = Instant.ofEpochMilli(now)
        val sessions = mutableListOf<SleepSessionRecord>()
        var pageToken: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    ascendingOrder = true,
                    pageSize = PAGE_SIZE,
                    pageToken = pageToken,
                ),
            )
            sessions += response.records
            pageToken = response.pageToken
        } while (!pageToken.isNullOrBlank())

        val sessionEntities = sessions.map { it.toEntity(now) }
        val stageEntities = sessions.flatMap { session ->
            val sessionId = session.localId(SLEEP)
            session.stages.map { stage -> stage.toEntity(session, sessionId, now) }
        }
        database.withTransaction {
            ensureSyncLog(SLEEP)
            database.healthConnectDao().deleteSleepSessionsInWindow(startMillis, now)
            database.healthConnectDao().upsertSleepSessions(sessionEntities)
            database.healthConnectDao().upsertSleepStages(stageEntities)
            database.healthConnectDao().updateLastReadAt(SLEEP, now)
        }
        return sessionEntities.size + stageEntities.size
    }

    private suspend fun importHeartRate(client: HealthConnectClient, now: Long): Int {
        val startMillis = readWindowStartMillis(now)
        val start = Instant.ofEpochMilli(startMillis)
        val end = Instant.ofEpochMilli(now)
        val heartRecords = mutableListOf<HeartRateRecord>()
        var pageToken: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    ascendingOrder = true,
                    pageSize = PAGE_SIZE,
                    pageToken = pageToken,
                ),
            )
            heartRecords += response.records
            pageToken = response.pageToken
        } while (!pageToken.isNullOrBlank())

        val samples = heartRecords.flatMap { record ->
            record.samples.map { sample -> sample.toEntity(record, now) }
        }
        database.withTransaction {
            ensureSyncLog(HEART_RATE)
            database.healthConnectDao().deleteHeartRateSamplesInWindow(startMillis, now)
            database.healthConnectDao().upsertHeartRateSamples(samples)
            database.healthConnectDao().updateLastReadAt(HEART_RATE, now)
        }
        return samples.size
    }

    private suspend fun exportWorkouts(client: HealthConnectClient, now: Long): Int {
        val lastWriteAt = database.healthConnectDao().getSyncLog(WORKOUTS)?.lastWriteAt
        val exports = HealthConnectRecordMapper.workoutRecords(
            database.healthConnectDao().getCompletedWorkoutExportRows(lastWriteAt),
        )
        if (exports.isNotEmpty()) {
            client.insertRecords(exports.map { it.record })
        }
        database.withTransaction {
            ensureSyncLog(WORKOUTS)
            database.healthConnectDao().updateLastWriteAt(WORKOUTS, now)
        }
        return exports.size
    }

    private suspend fun exportBodyComposition(client: HealthConnectClient, now: Long): Int {
        val entries = database.bodyCompositionDao().getUnsyncedHealthConnectEntries()
        val exports = HealthConnectRecordMapper.bodyRecords(entries)
        if (exports.isNotEmpty()) {
            client.insertRecords(exports.map { it.record })
        }
        database.withTransaction {
            ensureSyncLog(BODY_COMP)
            if (exports.isNotEmpty()) {
                database.bodyCompositionDao().markSyncedToHealthConnect(exports.mapNotNull { it.localId }.distinct())
            }
            database.healthConnectDao().updateLastWriteAt(BODY_COMP, now)
        }
        return exports.size
    }

    private fun readWindowStartMillis(now: Long): Long = now - HEALTH_CONNECT_READ_WINDOW_MS

    private suspend fun ensureSyncLog(dataType: String) {
        val id = "hc_sync_${dataType.lowercase()}"
        database.healthConnectDao().insertSyncLog(HcSyncLogEntity(id = id, dataType = dataType))
    }

    private suspend fun <T> aggregateDaily(
        startMillis: Long,
        endMillis: Long,
        block: suspend (Instant, Instant) -> T?,
    ): List<T> {
        if (startMillis >= endMillis) return emptyList()
        val zone = ZoneId.systemDefault()
        var date = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate()
        val endDate = Instant.ofEpochMilli(endMillis).atZone(zone).toLocalDate()
        return buildList {
            while (!date.isAfter(endDate)) {
                val dayStart = date.atStartOfDay(zone).toInstant()
                val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()
                val start = maxInstant(dayStart, Instant.ofEpochMilli(startMillis))
                val end = minInstant(dayEnd, Instant.ofEpochMilli(endMillis))
                if (start < end) block(start, end)?.let(::add)
                date = date.plusDays(1)
            }
        }
    }

    private fun SleepSessionRecord.toEntity(importedAt: Long): HcSleepSessionEntity {
        return HcSleepSessionEntity(
            id = localId(SLEEP),
            hcRecordId = providerRecordId(SLEEP),
            sourcePackage = metadata.dataOrigin.packageName,
            lastModifiedAt = metadata.lastModifiedTime.toEpochMilli(),
            recordingMethod = metadata.recordingMethod,
            importedAt = importedAt,
            startTime = startTime.toEpochMilli(),
            endTime = endTime.toEpochMilli(),
            startZoneOffset = startZoneOffset?.id,
            endZoneOffset = endZoneOffset?.id,
            title = title,
            notes = notes,
        )
    }

    private fun SleepSessionRecord.Stage.toEntity(
        session: SleepSessionRecord,
        sessionId: String,
        importedAt: Long,
    ): HcSleepStageEntity {
        val providerId = "${session.providerRecordId(SLEEP)}:${startTime.toEpochMilli()}:${endTime.toEpochMilli()}:$stage"
        return HcSleepStageEntity(
            id = "sleep_stage:$providerId",
            sleepSessionId = sessionId,
            hcRecordId = providerId,
            sourcePackage = session.metadata.dataOrigin.packageName,
            lastModifiedAt = session.metadata.lastModifiedTime.toEpochMilli(),
            recordingMethod = session.metadata.recordingMethod,
            importedAt = importedAt,
            startTime = startTime.toEpochMilli(),
            endTime = endTime.toEpochMilli(),
            stageType = stage,
        )
    }

    private fun HeartRateRecord.Sample.toEntity(record: HeartRateRecord, importedAt: Long): HcHeartRateSampleEntity {
        val providerId = record.providerRecordId(HEART_RATE)
        return HcHeartRateSampleEntity(
            id = "heart_rate:$providerId:${time.toEpochMilli()}",
            hcRecordId = providerId,
            sourcePackage = record.metadata.dataOrigin.packageName,
            lastModifiedAt = record.metadata.lastModifiedTime.toEpochMilli(),
            recordingMethod = record.metadata.recordingMethod,
            importedAt = importedAt,
            sampledAt = time.toEpochMilli(),
            bpm = beatsPerMinute,
        )
    }

    private fun SleepSessionRecord.localId(dataType: String): String = "$dataType:${providerRecordId(dataType)}"

    private fun SleepSessionRecord.providerRecordId(dataType: String): String {
        return metadata.id.ifBlank { "$dataType:${startTime.toEpochMilli()}:${endTime.toEpochMilli()}" }
    }

    private fun HeartRateRecord.providerRecordId(dataType: String): String {
        return metadata.id.ifBlank { "$dataType:${startTime.toEpochMilli()}:${endTime.toEpochMilli()}" }
    }

    private fun zoneOffsetString(instant: Instant): String = ZoneId.systemDefault().rules.getOffset(instant).id

    private fun maxInstant(a: Instant, b: Instant): Instant = if (a >= b) a else b

    private fun minInstant(a: Instant, b: Instant): Instant = if (a <= b) a else b

    private companion object {
        const val STEPS = "STEPS"
        const val ACTIVE_CALORIES = "ACTIVE_CALORIES"
        const val SLEEP = "SLEEP"
        const val HEART_RATE = "HEART_RATE"
        const val WORKOUTS = "WORKOUTS"
        const val BODY_COMP = "BODY_COMP"
        const val PAGE_SIZE = 1000
        const val AGGREGATE_SOURCE = "health_connect_aggregate"
        const val HEALTH_CONNECT_READ_WINDOW_MS = 30L * 24L * 60L * 60L * 1000L

        val REQUIRED_PERMISSIONS = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getWritePermission(ExerciseSessionRecord::class),
            HealthPermission.getWritePermission(WeightRecord::class),
            HealthPermission.getWritePermission(BodyFatRecord::class),
            HealthPermission.getWritePermission(LeanBodyMassRecord::class),
            HealthPermission.getWritePermission(BodyWaterMassRecord::class),
        )
    }
}
