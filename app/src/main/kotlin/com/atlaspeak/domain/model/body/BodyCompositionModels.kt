package com.atlaspeak.domain.model.body

enum class BodyCompositionPeriod {
    Week,
    Month,
    ThreeMonths,
    Year,
    YearToDate,
}

enum class BodyCompositionSource {
    Manual,
    HealthConnect,
    ScaleApp,
}

enum class BodyMetric {
    Weight,
    BodyFat,
    MuscleMass,
    Water,
    VisceralFat,
    Protein,
    BoneMass,
    BodyAge,
}

data class BodyCompositionInput(
    val measuredAt: Long,
    val weightKg: Double? = null,
    val bodyFatPercent: Double? = null,
    val muscleMassKg: Double? = null,
    val waterPercent: Double? = null,
    val visceralFatLevel: Int? = null,
    val proteinPercent: Double? = null,
    val boneMassKg: Double? = null,
    val bodyAge: Int? = null,
)

data class BodyCompositionEntry(
    val id: String,
    val measuredAt: Long,
    val weightKg: Double?,
    val bodyFatPercent: Double?,
    val muscleMassKg: Double?,
    val waterPercent: Double?,
    val visceralFatLevel: Int?,
    val proteinPercent: Double?,
    val boneMassKg: Double?,
    val bodyAge: Int?,
    val source: BodyCompositionSource,
    val syncedToHealthConnect: Boolean,
    val createdAt: Long,
)

data class BodyMetricPoint(
    val metric: BodyMetric,
    val timestamp: Long,
    val value: Double,
    val source: BodyCompositionSource,
)

data class BodyMetricValue(
    val metric: BodyMetric,
    val timestamp: Long,
    val value: Double,
    val source: BodyCompositionSource,
    val syncedToHealthConnect: Boolean,
)

data class BodyCompositionSnapshot(
    val latestValues: List<BodyMetricValue>,
    val series: List<BodyMetricPoint>,
    val entries: List<BodyCompositionEntry>,
) {
    fun latestValue(metric: BodyMetric): BodyMetricValue? = latestValues.firstOrNull { it.metric == metric }

    fun seriesFor(metric: BodyMetric): List<BodyMetricPoint> = series.filter { it.metric == metric }
}
