package com.atlaspeak.domain.repository

import kotlinx.coroutines.flow.Flow

interface OnboardingRepository {
    val onboardingCompleted: Flow<Boolean>
    suspend fun setOnboardingCompleted(completed: Boolean)
}
