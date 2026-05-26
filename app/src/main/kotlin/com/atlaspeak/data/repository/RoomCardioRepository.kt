package com.atlaspeak.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.atlaspeak.data.db.AppDatabase
import com.atlaspeak.data.db.entity.CardioSessionEntity
import com.atlaspeak.data.db.entity.CardioTypeEntity
import com.atlaspeak.data.db.entity.WorkoutSessionEntity
import com.atlaspeak.domain.model.cardio.CardioMode
import com.atlaspeak.domain.model.cardio.CardioSession
import com.atlaspeak.domain.model.cardio.CardioType
import com.atlaspeak.domain.model.cardio.LocationPoint
import com.atlaspeak.domain.repository.CardioRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class RoomCardioRepository @Inject constructor(
    private val database: AppDatabase,
    @ApplicationContext private val context: Context,
) : CardioRepository {
    override suspend fun cardioTypes(includeArchived: Boolean): List<CardioType> {
        return database.cardioDao().getCardioTypes(includeArchived).map { it.toDomain() }
    }

    override suspend fun upsertCustomType(type: CardioType) {
        val existing = database.cardioDao().getCardioType(type.id)
        if (existing?.isPreset == true) return
        database.cardioDao().upsertCardioType(
            CardioTypeEntity(
                id = type.id,
                nameEs = type.name,
                nameEn = type.name,
                hasGps = type.hasGps,
                iconName = type.iconName,
                isPreset = false,
                isArchived = type.isArchived,
            ),
        )
    }

    override suspend fun archiveType(id: String) {
        database.cardioDao().archiveCustomType(id)
    }

    override suspend fun createSession(session: CardioSession): CardioSession {
        database.withTransaction {
            database.workoutDao().upsertSession(session.toWorkoutSessionEntity())
            database.cardioDao().upsertCardioSession(session.toEntity())
        }
        return session
    }

    override suspend fun session(id: String): CardioSession? {
        val cardio = database.cardioDao().getCardioSession(id) ?: return null
        val workout = database.workoutDao().getSession(cardio.sessionId) ?: return null
        return cardio.toDomain(workout)
    }

    override suspend fun sessions(): List<CardioSession> {
        return database.cardioDao().getCardioSessions().mapNotNull { cardio ->
            val workout = database.workoutDao().getSession(cardio.sessionId) ?: return@mapNotNull null
            cardio.toDomain(workout)
        }
    }

    override suspend fun updateSession(session: CardioSession) {
        database.withTransaction {
            database.workoutDao().upsertSession(session.toWorkoutSessionEntity())
            database.cardioDao().upsertCardioSession(session.toEntity())
        }
    }

    override suspend fun deleteSession(id: String) {
        database.withTransaction {
            database.cardioDao().deleteCardioSession(id)
            database.workoutDao().deleteSession(id)
        }
    }

    private fun CardioTypeEntity.toDomain() = CardioType(
        id = id,
        name = if (isEnglishLocale()) nameEn else nameEs,
        hasGps = hasGps,
        isPreset = isPreset,
        isArchived = isArchived,
        iconName = iconName,
    )

    private suspend fun CardioSessionEntity.toDomain(workout: WorkoutSessionEntity): CardioSession {
        val type = database.cardioDao().getCardioType(cardioTypeId)
        return CardioSession(
            id = id,
            cardioTypeId = cardioTypeId,
            cardioTypeName = type?.let { if (isEnglishLocale()) it.nameEn else it.nameEs }.orEmpty(),
            mode = if (mode == MODE_COUNTDOWN) {
                CardioMode.Countdown(targetDurationSec)
            } else {
                CardioMode.Timer
            },
            startTime = workout.startTime,
            endTime = workout.endTime,
            durationSeconds = workout.durationSeconds,
            distanceKm = distanceKm,
            avgSpeedKmh = avgSpeedKmh,
            maxSpeedKmh = maxSpeedKmh,
            caloriesBurned = caloriesBurned,
            hasGps = hasGps,
            route = routePolylineJson?.let { decodeRoute(it) }.orEmpty(),
            completed = workout.completed,
        )
    }

    private fun CardioSession.toWorkoutSessionEntity() = WorkoutSessionEntity(
        id = id,
        routineId = null,
        type = "CARDIO",
        startTime = startTime,
        endTime = endTime,
        durationSeconds = durationSeconds,
        notes = null,
        completed = completed,
        caloriesBurned = caloriesBurned,
        totalVolumeKg = null,
    )

    private fun CardioSession.toEntity() = CardioSessionEntity(
        id = id,
        sessionId = id,
        cardioTypeId = cardioTypeId,
        mode = if (mode is CardioMode.Countdown) MODE_COUNTDOWN else MODE_TIMER,
        targetDurationSec = (mode as? CardioMode.Countdown)?.targetDurationSeconds ?: 0,
        actualDurationSec = durationSeconds,
        distanceKm = distanceKm,
        avgSpeedKmh = avgSpeedKmh,
        maxSpeedKmh = maxSpeedKmh,
        caloriesBurned = caloriesBurned,
        hasGps = hasGps,
        routePolylineJson = route.takeIf { it.isNotEmpty() }?.let { encodeRoute(it) },
        source = if (route.isNotEmpty()) "GPS" else "MANUAL",
    )

    private fun encodeRoute(route: List<LocationPoint>): String {
        return json.encodeToString(route.map { SerializableLocationPoint(it.latitude, it.longitude, it.timestamp) })
    }

    private fun decodeRoute(value: String): List<LocationPoint> {
        return runCatching {
            json.decodeFromString<List<SerializableLocationPoint>>(value)
                .map { LocationPoint(it.latitude, it.longitude, it.timestamp) }
        }.getOrDefault(emptyList())
    }

    private fun isEnglishLocale(): Boolean {
        return context.resources.configuration.locales[0]?.language == "en"
    }

    @Serializable
    private data class SerializableLocationPoint(
        val latitude: Double,
        val longitude: Double,
        val timestamp: Long,
    )

    private companion object {
        const val MODE_TIMER = "TIMER"
        const val MODE_COUNTDOWN = "COUNTDOWN"
        val json = Json { ignoreUnknownKeys = true }
    }
}
