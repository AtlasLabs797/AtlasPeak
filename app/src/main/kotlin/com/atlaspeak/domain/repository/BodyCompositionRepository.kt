package com.atlaspeak.domain.repository

import com.atlaspeak.domain.model.body.BodyCompositionEntry

interface BodyCompositionRepository {
    suspend fun entries(): List<BodyCompositionEntry>
    suspend fun upsert(entry: BodyCompositionEntry)
}
