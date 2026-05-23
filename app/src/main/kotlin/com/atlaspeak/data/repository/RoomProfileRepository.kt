package com.atlaspeak.data.repository

import com.atlaspeak.data.db.AppDatabase
import com.atlaspeak.data.db.entity.UserProfileEntity
import com.atlaspeak.domain.model.profile.UserProfile
import com.atlaspeak.domain.repository.ProfileRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomProfileRepository @Inject constructor(
    private val database: AppDatabase,
) : ProfileRepository {
    override suspend fun getProfile(): UserProfile? {
        return database.userProfileDao().getProfile()?.toDomain()
    }

    override suspend fun saveProfile(profile: UserProfile) {
        val user = database.userDao().getLocalUser() ?: return
        val existing = database.userProfileDao().getProfile()
        database.userProfileDao().upsertProfile(
            UserProfileEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                userId = user.id,
                displayName = profile.displayName,
                age = profile.age,
                heightCm = profile.heightCm,
                gender = profile.gender,
                goalType = profile.goalType,
                photoUri = existing?.photoUri,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private fun UserProfileEntity.toDomain() = UserProfile(
        displayName = displayName,
        age = age,
        heightCm = heightCm,
        gender = gender,
        goalType = goalType,
    )
}
