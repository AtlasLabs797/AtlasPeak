package com.atlaspeak.domain.repository

import com.atlaspeak.domain.model.workout.WorkoutSession
import com.atlaspeak.domain.model.workout.WorkoutSet

interface WorkoutRepository {
    suspend fun createSession(session: WorkoutSession): WorkoutSession
    suspend fun session(id: String): WorkoutSession?
    suspend fun sessions(): List<WorkoutSession>
    suspend fun findActiveSession(): WorkoutSession?
    suspend fun findActiveOrCreateSession(session: WorkoutSession): WorkoutSession {
        return findActiveSession() ?: createSession(session)
    }
    suspend fun deleteSession(id: String)
    suspend fun upsertSet(set: WorkoutSet)
    suspend fun deleteSet(id: String)
    suspend fun maxCompletedWeightBefore(exerciseId: String, before: Long): Double?
    suspend fun updateSessionCompletion(
        sessionId: String,
        endTime: Long,
        durationSeconds: Int,
        totalVolumeKg: Double,
    )
}
