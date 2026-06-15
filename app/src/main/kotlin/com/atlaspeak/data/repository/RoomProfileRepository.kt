package com.atlaspeak.data.repository

import androidx.room.withTransaction
import com.atlaspeak.data.db.AppDatabase
import com.atlaspeak.data.db.entity.UserEntity
import com.atlaspeak.data.db.entity.UserProfileEntity
import com.atlaspeak.domain.model.profile.Gender
import com.atlaspeak.domain.model.profile.Goal
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
        database.withTransaction {
            val now = System.currentTimeMillis()
            val user = database.userDao().getLocalUser() ?: UserEntity(
                id = UUID.randomUUID().toString(),
                createdAt = now,
            ).also { database.userDao().upsertUser(it) }
            val existing = database.userProfileDao().getProfile()
            database.userProfileDao().upsertProfile(
                UserProfileEntity(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    userId = user.id,
                    displayName = profile.displayName,
                    age = profile.age,
                    heightCm = profile.heightCm,
                    gender = profile.gender?.storageValue,
                    goalType = profile.goalType?.storageValue,
                    photoUri = existing?.photoUri,
                    updatedAt = now,
                ),
            )
        }
    }

    private fun UserProfileEntity.toDomain() = UserProfile(
        displayName = displayName,
        age = age,
        heightCm = heightCm,
        gender = Gender.fromStorageValue(gender),
        goalType = Goal.fromStorageValue(goalType),
    )
}
