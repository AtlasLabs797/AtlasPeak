package com.atlaspeak.domain.usecase.body

import com.atlaspeak.domain.model.body.BodyCompositionEntry
import com.atlaspeak.domain.model.body.BodyCompositionInput
import com.atlaspeak.domain.model.body.BodyCompositionPeriod
import com.atlaspeak.domain.model.body.BodyCompositionSource
import com.atlaspeak.domain.model.body.BodyMetric
import com.atlaspeak.domain.repository.BodyCompositionRepository
import com.atlaspeak.presentation.body.BodyCompositionDraft
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BodyCompositionUseCaseTest {
    private val repository = FakeBodyCompositionRepository()
    private val now = 1_700_000_000_000L
    private val useCase = BodyCompositionUseCase(
        repository = repository,
        now = { now },
        idProvider = { "entry-${repository.saved.size + 1}" },
    )

    @Test
    fun `record rejects empty and invalid entries`() = runTest {
        assertFalse(useCase.record(BodyCompositionInput(measuredAt = now)))
        assertFalse(useCase.record(BodyCompositionInput(measuredAt = now, weightKg = -1.0)))
        assertFalse(useCase.record(BodyCompositionInput(measuredAt = now, bodyFatPercent = 101.0)))
    }

    @Test
    fun `record saves sanitized manual entry`() = runTest {
        val saved = useCase.record(
            BodyCompositionInput(
                measuredAt = now,
                weightKg = 82.4,
                bodyFatPercent = 18.2,
                visceralFatLevel = 7,
            ),
        )

        assertTrue(saved)
        assertEquals(1, repository.saved.size)
        assertEquals("entry-1", repository.saved.single().id)
        assertEquals(BodyCompositionSource.Manual, repository.saved.single().source)
        assertEquals(false, repository.saved.single().syncedToHealthConnect)
    }

    @Test
    fun `draft raw validation rejects unparseable fields before partial save`() {
        val draft = BodyCompositionDraft(
            weightKg = "80",
            bodyFatPercent = "abc",
        )

        assertFalse(draft.isValidRaw())
    }

    @Test
    fun `draft raw validation accepts comma decimals`() {
        val draft = BodyCompositionDraft(weightKg = "80,5")

        assertTrue(draft.isValidRaw())
    }

    @Test
    fun `snapshot returns latest value per metric by timestamp`() = runTest {
        repository.entries = listOf(
            entry(id = "old-weight", measuredAt = now - DAYS_10, weightKg = 84.0, bodyFatPercent = 20.0),
            entry(id = "new-fat", measuredAt = now - DAYS_2, bodyFatPercent = 18.0),
            entry(id = "new-weight", measuredAt = now - DAYS_1, weightKg = 82.0),
        )

        val snapshot = useCase.snapshot(BodyCompositionPeriod.Month)

        assertEquals(82.0, snapshot.latestValue(BodyMetric.Weight)?.value)
        assertEquals(18.0, snapshot.latestValue(BodyMetric.BodyFat)?.value)
    }

    @Test
    fun `snapshot filters evolution points by period and metric`() = runTest {
        repository.entries = listOf(
            entry(id = "outside", measuredAt = now - DAYS_40, weightKg = 90.0),
            entry(id = "inside-1", measuredAt = now - DAYS_2, weightKg = 84.0, muscleMassKg = 64.0),
            entry(id = "inside-2", measuredAt = now - DAYS_1, weightKg = 83.0),
        )

        val snapshot = useCase.snapshot(BodyCompositionPeriod.Month)

        assertEquals(listOf(84.0, 83.0), snapshot.seriesFor(BodyMetric.Weight).map { it.value })
        assertEquals(listOf(64.0), snapshot.seriesFor(BodyMetric.MuscleMass).map { it.value })
    }

    private fun entry(
        id: String,
        measuredAt: Long,
        weightKg: Double? = null,
        bodyFatPercent: Double? = null,
        muscleMassKg: Double? = null,
    ) = BodyCompositionEntry(
        id = id,
        measuredAt = measuredAt,
        weightKg = weightKg,
        bodyFatPercent = bodyFatPercent,
        muscleMassKg = muscleMassKg,
        waterPercent = null,
        visceralFatLevel = null,
        proteinPercent = null,
        boneMassKg = null,
        bodyAge = null,
        source = BodyCompositionSource.Manual,
        syncedToHealthConnect = false,
        createdAt = measuredAt,
    )

    private class FakeBodyCompositionRepository : BodyCompositionRepository {
        var entries = emptyList<BodyCompositionEntry>()
        val saved = mutableListOf<BodyCompositionEntry>()

        override suspend fun entries(): List<BodyCompositionEntry> = entries

        override suspend fun upsert(entry: BodyCompositionEntry) {
            saved += entry
            entries = entries.filterNot { it.id == entry.id } + entry
        }
    }

    private companion object {
        const val HOUR = 60L * 60L * 1_000L
        const val DAYS_1 = 24L * HOUR
        const val DAYS_2 = 2L * DAYS_1
        const val DAYS_10 = 10L * DAYS_1
        const val DAYS_40 = 40L * DAYS_1
    }
}
