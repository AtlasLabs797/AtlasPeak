package com.atlaspeak.domain.repository

import com.atlaspeak.domain.model.workout.Routine

interface RoutineRepository {
    suspend fun routines(includeArchived: Boolean = false): List<Routine>
    suspend fun routine(id: String): Routine?
    suspend fun upsertRoutine(routine: Routine)
    suspend fun archiveRoutine(id: String)
}
