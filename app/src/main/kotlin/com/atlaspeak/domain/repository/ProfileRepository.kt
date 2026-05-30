package com.atlaspeak.domain.repository

import com.atlaspeak.domain.model.profile.UserProfile

interface ProfileRepository {
    suspend fun getProfile(): UserProfile?
    suspend fun saveProfile(profile: UserProfile)
}
