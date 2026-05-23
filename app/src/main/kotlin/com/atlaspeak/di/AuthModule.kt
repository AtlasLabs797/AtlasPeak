package com.atlaspeak.di

import com.atlaspeak.data.repository.RoomAuthRepository
import com.atlaspeak.data.security.EncryptionManager
import com.atlaspeak.domain.repository.AuthRepository
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
}
