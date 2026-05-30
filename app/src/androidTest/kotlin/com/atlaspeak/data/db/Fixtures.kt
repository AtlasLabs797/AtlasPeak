package com.atlaspeak.data.db

import com.atlaspeak.data.db.entity.HcSleepSessionEntity
import com.atlaspeak.data.db.entity.HcSleepStageEntity
import com.atlaspeak.data.db.entity.HcStepsRecordEntity

object Fixtures {
    fun stepsRecord(id: String, count: Long) = HcStepsRecordEntity(
        id = id,
        hcRecordId = "hc-steps-1",
        sourcePackage = "com.example.health",
        lastModifiedAt = 1_700_000_000_000 + count,
        recordingMethod = 1,
        importedAt = 1_700_000_010_000,
        startTime = 1_700_000_000_000,
        endTime = 1_700_003_600_000,
        startZoneOffset = "+01:00",
        endZoneOffset = "+01:00",
        count = count,
    )

    fun sleepSession() = HcSleepSessionEntity(
        id = "sleep-1",
        hcRecordId = "hc-sleep-1",
        sourcePackage = "com.example.health",
        lastModifiedAt = 1_700_000_000_000,
        recordingMethod = 1,
        importedAt = 1_700_000_010_000,
        startTime = 1_700_000_000_000,
        endTime = 1_700_028_800_000,
        startZoneOffset = "+01:00",
        endZoneOffset = "+01:00",
        title = "Night sleep",
        notes = null,
    )

    fun sleepStage() = HcSleepStageEntity(
        id = "stage-1",
        sleepSessionId = "sleep-1",
        hcRecordId = "hc-stage-1",
        sourcePackage = "com.example.health",
        lastModifiedAt = 1_700_000_000_000,
        recordingMethod = 1,
        importedAt = 1_700_000_010_000,
        startTime = 1_700_000_000_000,
        endTime = 1_700_003_600_000,
        stageType = 4,
    )
}
