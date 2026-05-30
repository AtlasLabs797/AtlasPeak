package com.atlaspeak.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.atlaspeak.domain.repository.OnboardingRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.onboardingDataStore by preferencesDataStore(name = "onboarding")

@Singleton
class PreferencesOnboardingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : OnboardingRepository {
    override val onboardingCompleted: Flow<Boolean> = context.onboardingDataStore.data
        .map { preferences -> preferences[ONBOARDING_COMPLETED] == true }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        context.onboardingDataStore.edit { preferences ->
            preferences[ONBOARDING_COMPLETED] = completed
        }
    }

    private companion object {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }
}
