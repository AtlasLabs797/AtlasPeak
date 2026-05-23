package com.atlaspeak.di

import com.atlaspeak.data.repository.PreferencesOnboardingRepository
import com.atlaspeak.data.repository.RoomAuthRepository
import com.atlaspeak.data.repository.RoomExerciseRepository
import com.atlaspeak.data.repository.RoomProfileRepository
import com.atlaspeak.data.repository.RoomRoutineRepository
import com.atlaspeak.data.security.EncryptionManager
import com.atlaspeak.domain.repository.AuthRepository
import com.atlaspeak.domain.repository.ExerciseRepository
import com.atlaspeak.domain.repository.OnboardingRepository
import com.atlaspeak.domain.repository.ProfileRepository
import com.atlaspeak.domain.repository.RoutineRepository
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
    abstract fun bindPasswordHasher(encryptionManager: EncryptionManager): PasswordHasher

    @Binds
    abstract fun bindOnboardingRepository(repository: PreferencesOnboardingRepository): OnboardingRepository

    @Binds
    abstract fun bindProfileRepository(repository: RoomProfileRepository): ProfileRepository

    @Binds
    abstract fun bindExerciseRepository(repository: RoomExerciseRepository): ExerciseRepository

    @Binds
    abstract fun bindRoutineRepository(repository: RoomRoutineRepository): RoutineRepository
}
