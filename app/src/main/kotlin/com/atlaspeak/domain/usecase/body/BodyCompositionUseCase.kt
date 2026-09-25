package com.atlaspeak.domain.usecase.body

import com.atlaspeak.domain.model.body.BodyCompositionEntry
import com.atlaspeak.domain.model.body.BodyCompositionInput
import com.atlaspeak.domain.model.body.BodyCompositionPeriod
import com.atlaspeak.domain.model.body.BodyCompositionSnapshot
import com.atlaspeak.domain.model.body.BodyCompositionSource
import com.atlaspeak.domain.model.body.BodyMetric
import com.atlaspeak.domain.model.body.BodyMetricPoint
import com.atlaspeak.domain.model.body.BodyMetricValue
import com.atlaspeak.domain.repository.BodyCompositionRepository
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

class BodyCompositionUseCase(
    private val repository: BodyCompositionRepository,
    private val now: () -> Long,
    private val idProvider: () -> String,
) {
    @Inject
    constructor(repository: BodyCompositionRepository) : this(
        repository = repository,
        now = { System.currentTimeMillis() },
        idProvider = { UUID.randomUUID().toString() },
    )

    suspend fun snapshot(period: BodyCompositionPeriod): BodyCompositionSnapshot {
        val nowMillis = now()
        val allEntries = repository.entries().sortedForMetricResolution()
        val periodStart = period.startMillis(nowMillis)
        val periodEntries = allEntries
            .filter { it.measuredAt >= periodStart && it.measuredAt <= nowMillis }
            .sortedBy { it.measuredAt }
        return BodyCompositionSnapshot(
            latestValues = BodyMetric.entries.mapNotNull { metric -> allEntries.latestValue(metric) },
            series = periodEntries.flatMap { it.points() }.resolveMetricConflicts(),
            entries = allEntries,
        )
    }

    suspend fun record(input: BodyCompositionInput): Boolean {
        if (!input.isValid()) return false
        repository.upsert(
            BodyCompositionEntry(
                id = idProvider(),
                measuredAt = input.measuredAt,
                weightKg = input.weightKg,
                bodyFatPercent = input.bodyFatPercent,
                muscleMassKg = input.muscleMassKg,
                waterPercent = input.waterPercent,
                bodyWaterMassKg = input.bodyWaterMassKg,
                visceralFatLevel = input.visceralFatLevel,
                proteinPercent = input.proteinPercent,
                boneMassKg = input.boneMassKg,
                bodyAge = input.bodyAge,
                source = BodyCompositionSource.Manual,
                syncedToHealthConnect = false,
                createdAt = now(),
            ),
        )
        return true
    }

    private fun BodyCompositionInput.isValid(): Boolean {
        // BUG-099 (Fase 10 P2): estos rangos vivian duplicados con literales
        // propios (weight <=500 exclusivo de 0, muscleMass <=250, boneMass
        // <=20, bodyAge <=120) que divergian de `BodyCompositionDraft` en la
        // UI. Ahora ambos pasan por `BodyCompositionValidation`.
        if (measuredAt <= 0L) return false
        val hasMetric = listOf(
            weightKg,
            bodyFatPercent,
            muscleMassKg,
            waterPercent,
            bodyWaterMassKg,
            visceralFatLevel,
            proteinPercent,
            boneMassKg,
            bodyAge,
        ).any { it != null }
        if (!hasMetric) return false
        return weightKg.validOrAbsent(BodyCompositionValidation::weightIsValid) &&
            bodyFatPercent.validOrAbsent(BodyCompositionValidation::percentIsValid) &&
            muscleMassKg.validOrAbsent(BodyCompositionValidation::muscleMassIsValid) &&
            waterPercent.validOrAbsent(BodyCompositionValidation::percentIsValid) &&
            bodyWaterMassKg.validOrAbsent(BodyCompositionValidation::bodyWaterMassIsValid) &&
            visceralFatLevel.validOrAbsent(BodyCompositionValidation::visceralFatIsValid) &&
            proteinPercent.validOrAbsent(BodyCompositionValidation::percentIsValid) &&
            boneMassKg.validOrAbsent(BodyCompositionValidation::boneMassIsValid) &&
            bodyAge.validOrAbsent(BodyCompositionValidation::bodyAgeIsValid)
    }

    private fun <T> T?.validOrAbsent(isValid: (T?) -> Boolean): Boolean = this == null || isValid(this)

    private fun List<BodyCompositionEntry>.latestValue(metric: BodyMetric): BodyMetricValue? {
        return firstNotNullOfOrNull { entry ->
            entry.value(metric)?.let { value ->
                BodyMetricValue(
                    metric = metric,
                    timestamp = entry.measuredAt,
                    value = value,
                    source = entry.source,
                    syncedToHealthConnect = entry.syncedToHealthConnect,
                )
            }
        }
    }

    private fun BodyCompositionEntry.points(): List<BodyMetricPoint> {
        return BodyMetric.entries.mapNotNull { metric ->
            value(metric)?.let { value ->
                BodyMetricPoint(
                    metric = metric,
                    timestamp = measuredAt,
                    value = value,
                    source = source,
                )
            }
        }
    }

    private fun BodyCompositionEntry.value(metric: BodyMetric): Double? {
        return when (metric) {
            BodyMetric.Weight -> weightKg
            BodyMetric.BodyFat -> bodyFatPercent
            BodyMetric.MuscleMass -> muscleMassKg
            BodyMetric.Water -> waterPercent
            BodyMetric.BodyWaterMass -> bodyWaterMassKg
            BodyMetric.VisceralFat -> visceralFatLevel?.toDouble()
            BodyMetric.Protein -> proteinPercent
            BodyMetric.BoneMass -> boneMassKg
            BodyMetric.BodyAge -> bodyAge?.toDouble()
        }
    }

    private fun List<BodyCompositionEntry>.sortedForMetricResolution(): List<BodyCompositionEntry> {
        return sortedWith(
            compareByDescending<BodyCompositionEntry> { it.measuredAt }
                .thenByDescending { it.source.priority },
        )
    }

    private fun List<BodyMetricPoint>.resolveMetricConflicts(): List<BodyMetricPoint> {
        return groupBy { it.metric to it.timestamp }
            .values
            .map { points -> points.maxBy { it.source.priority } }
            .sortedWith(compareBy<BodyMetricPoint> { it.timestamp }.thenBy { it.metric.ordinal })
    }

    private val BodyCompositionSource.priority: Int
        get() = when (this) {
            BodyCompositionSource.HealthConnect -> 2
            BodyCompositionSource.ScaleApp -> 1
            BodyCompositionSource.Manual -> 0
        }

    private fun BodyCompositionPeriod.startMillis(nowMillis: Long): Long {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val startDate = when (this) {
            BodyCompositionPeriod.Week -> today.minusDays((today.dayOfWeek.value - 1).toLong())
            BodyCompositionPeriod.Month -> today.minusDays(29)
            BodyCompositionPeriod.ThreeMonths -> today.minusDays(89)
            BodyCompositionPeriod.Year -> today.minusDays(364)
            BodyCompositionPeriod.YearToDate -> today.withDayOfYear(1)
        }
        return startDate.atStartOfDay(zone).toInstant().toEpochMilli()
    }
}
