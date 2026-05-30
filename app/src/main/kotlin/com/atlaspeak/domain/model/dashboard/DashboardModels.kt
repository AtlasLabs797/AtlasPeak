package com.atlaspeak.domain.model.dashboard

enum class DashboardPeriod {
    Week,
    Month,
    ThreeMonths,
    Year,
    YearToDate,
}

enum class DashboardWidget {
    TotalVolume,
    Consistency,
    BodyWeight,
    DailySteps,
    HeartRate,
    Sleep,
    TotalActivity,
}

data class DashboardFilters(
    val totalVolumePeriod: DashboardPeriod = DashboardPeriod.Week,
    val consistencyPeriod: DashboardPeriod = DashboardPeriod.Week,
    val bodyWeightPeriod: DashboardPeriod = DashboardPeriod.Month,
    val dailyStepsPeriod: DashboardPeriod = DashboardPeriod.Week,
    val heartRatePeriod: DashboardPeriod = DashboardPeriod.Week,
    val sleepPeriod: DashboardPeriod = DashboardPeriod.Week,
    val totalActivityPeriod: DashboardPeriod = DashboardPeriod.Week,
)

data class DashboardPoint(
    val timestamp: Long,
    val value: Double,
)

data class DashboardInterval(
    val startTime: Long,
    val endTime: Long,
    val value: Double,
)

data class DashboardSessionSummary(
    val startTime: Long,
    val durationSeconds: Int,
    val totalVolumeKg: Double?,
)

data class DashboardConsistency(
    val activeDays: Int,
    val targetDays: Int,
    val usesWeeklyPlan: Boolean,
)

data class DashboardSnapshot(
    val weeklyTrainingMinutes: Int,
    val totalVolumeKg: Double,
    val consistency: DashboardConsistency,
    val bodyWeightPoints: List<DashboardPoint>,
    val dailySteps: List<DashboardPoint>,
    val heartRate: List<DashboardPoint>,
    val averageSleepHours: Double?,
    val totalActivitySeconds: Int,
)
