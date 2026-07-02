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
import com.atlaspeak.data.db.entity.BodyCompositionEntity
import com.atlaspeak.data.db.entity.HcActiveCaloriesRecordEntity
import com.atlaspeak.data.db.entity.HcHeartRateSampleEntity
import com.atlaspeak.data.db.entity.HcSleepSessionEntity
import com.atlaspeak.data.db.entity.HcSleepStageEntity
import com.atlaspeak.data.db.entity.HcStepsRecordEntity
import com.atlaspeak.data.db.entity.HcSyncLogEntity
import com.atlaspeak.domain.model.healthconnect.HealthConnectAvailability
import com.atlaspeak.domain.model.healthconnect.HealthConnectCapability
import com.atlaspeak.domain.model.healthconnect.HealthConnectSyncResult
import com.atlaspeak.domain.repository.HealthConnectRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
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
        val now = System.currentTimeMillis()
        var imported = 0
        var exported = 0
        val completedCapabilities = mutableSetOf<HealthConnectCapability>()
        val skippedCapabilities = mutableSetOf<HealthConnectCapability>()

        suspend fun runIfGranted(
            capability: HealthConnectCapability,
            block: suspend () -> Int,
        ): Int {
            val permissions = REQUIRED_PERMISSIONS_BY_CAPABILITY.getValue(capability)
            return if (grantedPermissions.containsAll(permissions)) {
                completedCapabilities += capability
                block()
            } else {
                skippedCapabilities += capability
                0
            }
        }

        imported += runIfGranted(HealthConnectCapability.Steps) { importSteps(client, now) }
        imported += runIfGranted(HealthConnectCapability.ActiveCalories) { importActiveCalories(client, now) }
        imported += runIfGranted(HealthConnectCapability.Sleep) { importSleep(client, now) }
        imported += runIfGranted(HealthConnectCapability.HeartRate) { importHeartRate(client, now) }
        imported += runIfGranted(HealthConnectCapability.BodyCompositionRead) { importBodyComposition(client, now) }
        exported += runIfGranted(HealthConnectCapability.WorkoutWrite) { exportWorkouts(client, now) }
        exported += runIfGranted(HealthConnectCapability.BodyCompositionWrite) { exportBodyComposition(client, now) }

        return HealthConnectSyncResult(
            availability = HealthConnectAvailability.Available,
            missingPermissions = skippedCapabilities.isNotEmpty(),
            importedRecords = imported,
            exportedRecords = exported,
            completedCapabilities = completedCapabilities,
            skippedCapabilities = skippedCapabilities,
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

    private suspend fun importBodyComposition(client: HealthConnectClient, now: Long): Int {
        val startMillis = readWindowStartMillis(now)
        val start = Instant.ofEpochMilli(startMillis)
        val end = Instant.ofEpochMilli(now)
        val entries = buildList {
            addAll(readWeightRecords(client, start, end, now))
            addAll(readBodyFatRecords(client, start, end, now))
            addAll(readLeanBodyMassRecords(client, start, end, now))
            addAll(readBodyWaterMassRecords(client, start, end, now))
        }
        database.withTransaction {
            ensureSyncLog(BODY_COMP)
            database.bodyCompositionDao().deleteHealthConnectEntriesInWindow(startMillis, now)
            database.bodyCompositionDao().upsert(entries)
            database.healthConnectDao().updateLastReadAt(BODY_COMP, now)
        }
        return entries.size
    }

    private suspend fun readWeightRecords(
        client: HealthConnectClient,
        start: Instant,
        end: Instant,
        importedAt: Long,
    ): List<BodyCompositionEntity> {
        val records = readPagedRecords(client, WeightRecord::class, start, end)
        return records.map { record ->
            record.toBodyEntity("weight", importedAt, weightKg = record.weight.inKilograms)
        }
    }

    private suspend fun readBodyFatRecords(
        client: HealthConnectClient,
        start: Instant,
        end: Instant,
        importedAt: Long,
    ): List<BodyCompositionEntity> {
        val records = readPagedRecords(client, BodyFatRecord::class, start, end)
        return records.map { record ->
            record.toBodyEntity("body_fat", importedAt, bodyFatPercent = record.percentage.value)
        }
    }

    private suspend fun readLeanBodyMassRecords(
        client: HealthConnectClient,
        start: Instant,
        end: Instant,
        importedAt: Long,
    ): List<BodyCompositionEntity> {
        val records = readPagedRecords(client, LeanBodyMassRecord::class, start, end)
        return records.map { record ->
            record.toBodyEntity("lean_mass", importedAt, muscleMassKg = record.mass.inKilograms)
        }
    }

    private suspend fun readBodyWaterMassRecords(
        client: HealthConnectClient,
        start: Instant,
        end: Instant,
        importedAt: Long,
    ): List<BodyCompositionEntity> {
        val records = readPagedRecords(client, BodyWaterMassRecord::class, start, end)
        return records.map { record ->
            record.toBodyEntity("body_water_mass", importedAt, bodyWaterMassKg = record.mass.inKilograms)
        }
    }

    private suspend fun <T : androidx.health.connect.client.records.Record> readPagedRecords(
        client: HealthConnectClient,
        recordType: kotlin.reflect.KClass<T>,
        start: Instant,
        end: Instant,
    ): List<T> {
        val records = mutableListOf<T>()
        var pageToken: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = recordType,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    ascendingOrder = true,
                    pageSize = PAGE_SIZE,
                    pageToken = pageToken,
                ),
            )
            records += response.records
            pageToken = response.pageToken
        } while (!pageToken.isNullOrBlank())
        return records
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

    private fun WeightRecord.toBodyEntity(
        metric: String,
        importedAt: Long,
        weightKg: Double,
    ): BodyCompositionEntity = bodyEntity(
        metric = metric,
        providerId = providerRecordId(metric, time.toEpochMilli()),
        measuredAt = time.toEpochMilli(),
        sourcePackage = metadata.dataOrigin.packageName,
        createdAt = importedAt,
        weightKg = weightKg,
    )

    private fun BodyFatRecord.toBodyEntity(
        metric: String,
        importedAt: Long,
        bodyFatPercent: Double,
    ): BodyCompositionEntity = bodyEntity(
        metric = metric,
        providerId = providerRecordId(metric, time.toEpochMilli()),
        measuredAt = time.toEpochMilli(),
        sourcePackage = metadata.dataOrigin.packageName,
        createdAt = importedAt,
        bodyFatPercent = bodyFatPercent,
    )

    private fun LeanBodyMassRecord.toBodyEntity(
        metric: String,
        importedAt: Long,
        muscleMassKg: Double,
    ): BodyCompositionEntity = bodyEntity(
        metric = metric,
        providerId = providerRecordId(metric, time.toEpochMilli()),
        measuredAt = time.toEpochMilli(),
        sourcePackage = metadata.dataOrigin.packageName,
        createdAt = importedAt,
        muscleMassKg = muscleMassKg,
    )

    private fun BodyWaterMassRecord.toBodyEntity(
        metric: String,
        importedAt: Long,
        bodyWaterMassKg: Double,
    ): BodyCompositionEntity = bodyEntity(
        metric = metric,
        providerId = providerRecordId(metric, time.toEpochMilli()),
        measuredAt = time.toEpochMilli(),
        sourcePackage = metadata.dataOrigin.packageName,
        createdAt = importedAt,
        bodyWaterMassKg = bodyWaterMassKg,
    )

    private fun bodyEntity(
        metric: String,
        providerId: String,
        measuredAt: Long,
        sourcePackage: String,
        createdAt: Long,
        weightKg: Double? = null,
        bodyFatPercent: Double? = null,
        muscleMassKg: Double? = null,
        bodyWaterMassKg: Double? = null,
    ) = BodyCompositionEntity(
        id = "hc_body:$metric:$providerId",
        measuredAt = measuredAt,
        weightKg = weightKg?.takeIf { it > 0.0 },
        bodyFatPercent = bodyFatPercent?.takeIf { it in 0.0..100.0 },
        muscleMassKg = muscleMassKg?.takeIf { it > 0.0 },
        waterPercent = null,
        bodyWaterMassKg = bodyWaterMassKg?.takeIf { it > 0.0 },
        visceralFatLevel = null,
        proteinPercent = null,
        boneMassKg = null,
        bodyAge = null,
        source = SOURCE_HEALTH_CONNECT,
        syncedToHc = true,
        createdAt = createdAt,
    )

    private fun SleepSessionRecord.localId(dataType: String): String = "$dataType:${providerRecordId(dataType)}"

    private fun SleepSessionRecord.providerRecordId(dataType: String): String {
        return metadata.id.ifBlank { "$dataType:${startTime.toEpochMilli()}:${endTime.toEpochMilli()}" }
    }

    private fun HeartRateRecord.providerRecordId(dataType: String): String {
        return metadata.id.ifBlank { "$dataType:${startTime.toEpochMilli()}:${endTime.toEpochMilli()}" }
    }

    private fun androidx.health.connect.client.records.Record.providerRecordId(dataType: String, timeMillis: Long): String {
        return metadata.id.ifBlank { "$dataType:${metadata.dataOrigin.packageName}:$timeMillis" }
    }

    private fun zoneOffsetString(instant: Instant): String {
        val offsetSeconds = ZoneId.systemDefault().rules.getOffset(instant).totalSeconds
        return ZoneOffset.ofTotalSeconds(offsetSeconds).id
    }

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
        const val SOURCE_HEALTH_CONNECT = "HEALTH_CONNECT"
        const val HEALTH_CONNECT_READ_WINDOW_MS = 30L * 24L * 60L * 60L * 1000L

        val REQUIRED_PERMISSIONS_BY_CAPABILITY = mapOf(
            HealthConnectCapability.Steps to setOf(HealthPermission.getReadPermission(StepsRecord::class)),
            HealthConnectCapability.ActiveCalories to setOf(HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)),
            HealthConnectCapability.Sleep to setOf(HealthPermission.getReadPermission(SleepSessionRecord::class)),
            HealthConnectCapability.HeartRate to setOf(HealthPermission.getReadPermission(HeartRateRecord::class)),
            HealthConnectCapability.BodyCompositionRead to setOf(
                HealthPermission.getReadPermission(WeightRecord::class),
                HealthPermission.getReadPermission(BodyFatRecord::class),
                HealthPermission.getReadPermission(LeanBodyMassRecord::class),
                HealthPermission.getReadPermission(BodyWaterMassRecord::class),
            ),
            HealthConnectCapability.WorkoutWrite to setOf(HealthPermission.getWritePermission(ExerciseSessionRecord::class)),
            HealthConnectCapability.BodyCompositionWrite to setOf(
                HealthPermission.getWritePermission(WeightRecord::class),
                HealthPermission.getWritePermission(BodyFatRecord::class),
                HealthPermission.getWritePermission(LeanBodyMassRecord::class),
                HealthPermission.getWritePermission(BodyWaterMassRecord::class),
            ),
        )

        val REQUIRED_PERMISSIONS = REQUIRED_PERMISSIONS_BY_CAPABILITY.values.flatten().toSet()
    }
}
