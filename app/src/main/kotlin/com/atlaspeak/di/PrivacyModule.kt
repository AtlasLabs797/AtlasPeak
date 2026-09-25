package com.atlaspeak.di

import com.atlaspeak.data.privacy.AppUserDataEraser
import com.atlaspeak.domain.usecase.privacy.UserDataEraser
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class PrivacyModule {
    @Binds
    abstract fun bindUserDataEraser(eraser: AppUserDataEraser): UserDataEraser
}
