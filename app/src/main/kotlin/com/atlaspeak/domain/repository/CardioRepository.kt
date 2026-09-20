package com.atlaspeak.domain.repository

import com.atlaspeak.domain.model.cardio.CardioSession
import com.atlaspeak.domain.model.cardio.CardioType

interface CardioRepository {
    suspend fun cardioTypes(includeArchived: Boolean = false): List<CardioType>
    suspend fun upsertCustomType(type: CardioType)
    suspend fun archiveType(id: String)
    suspend fun createSession(session: CardioSession): CardioSession
    suspend fun session(id: String): CardioSession?
    suspend fun sessions(): List<CardioSession>
    suspend fun findActiveSession(): CardioSession?
    suspend fun updateSession(session: CardioSession)
    suspend fun deleteSession(id: String)
}
