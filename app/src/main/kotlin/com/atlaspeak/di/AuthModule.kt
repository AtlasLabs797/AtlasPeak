package com.atlaspeak.di

import com.atlaspeak.data.repository.PreferencesOnboardingRepository
import com.atlaspeak.data.repository.RoomAuthRepository
import com.atlaspeak.data.repository.RoomBodyCompositionRepository
import com.atlaspeak.data.repository.RoomCardioRepository
import com.atlaspeak.data.repository.RoomDashboardRepository
import com.atlaspeak.data.repository.RoomExerciseRepository
import com.atlaspeak.data.repository.RoomProfileRepository
import com.atlaspeak.data.repository.RoomRoutineRepository
import com.atlaspeak.data.repository.RoomWorkoutSettingsRepository
import com.atlaspeak.data.repository.RoomWorkoutRepository
import com.atlaspeak.data.security.EncryptionManager
import com.atlaspeak.domain.repository.AuthRepository
import com.atlaspeak.domain.repository.BodyCompositionRepository
import com.atlaspeak.domain.repository.CardioRepository
import com.atlaspeak.domain.repository.DashboardRepository
import com.atlaspeak.domain.repository.ExerciseRepository
import com.atlaspeak.domain.repository.OnboardingRepository
import com.atlaspeak.domain.repository.ProfileRepository
import com.atlaspeak.domain.repository.RoutineRepository
import com.atlaspeak.domain.repository.WorkoutRepository
import com.atlaspeak.domain.repository.WorkoutSettingsRepository
import com.atlaspeak.domain.security.PasswordHasher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {
    @Binds
    abstract fun bindAuthRepository(repository: RoomAuthRepository): AuthRepository

    @Binds
    abstract fun bindCardioRepository(repository: RoomCardioRepository): CardioRepository

    @Binds
    abstract fun bindBodyCompositionRepository(repository: RoomBodyCompositionRepository): BodyCompositionRepository

    @Binds
    abstract fun bindDashboardRepository(repository: RoomDashboardRepository): DashboardRepository

    @Binds
    abstract fun bindPasswordHasher(encryptionManager: EncryptionManager): PasswordHasher

    @Binds
    abstract fun bindOnboardingRepository(repository: PreferencesOnboardingRepository): OnboardingRepository

    @Binds
    abstract fun bindProfileRepository(repository: RoomProfileRepository): ProfileRepository

    @Binds
    abstract fun bindExerciseRepository(repository: RoomExerciseRepository): ExerciseRepository

    @Binds
    abstract fun bindRoutineRepository(repository: RoomRoutineRepository): RoutineRepository

    @Binds
    abstract fun bindWorkoutRepository(repository: RoomWorkoutRepository): WorkoutRepository

    @Binds
    abstract fun bindWorkoutSettingsRepository(repository: RoomWorkoutSettingsRepository): WorkoutSettingsRepository
}
