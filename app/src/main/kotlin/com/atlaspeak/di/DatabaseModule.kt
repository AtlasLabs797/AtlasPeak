package com.atlaspeak.di

import android.content.Context
import androidx.room.Room
import com.atlaspeak.data.db.AppDatabase
import com.atlaspeak.data.db.seed.DatabaseSeeder
import com.atlaspeak.data.security.AppDatabaseRecoveryUseCase
import com.atlaspeak.data.security.DatabasePassphraseProvider
import com.atlaspeak.data.security.LazyPassphraseOpenHelperFactory
import com.atlaspeak.data.security.RoomDatabaseKeyChecker
import com.atlaspeak.domain.usecase.recovery.DatabaseRecoveryUseCase
import com.atlaspeak.domain.usecase.security.DatabaseKeyChecker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        passphraseProvider: DatabasePassphraseProvider,
    ): AppDatabase {
        // P1 (auditoria): la lectura de la passphrase (Keystore I/O) se aplaza hasta que Room
        // abra la DB de verdad, fuera del hilo principal. Ver LazyPassphraseOpenHelperFactory.
        return Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .openHelperFactory(LazyPassphraseOpenHelperFactory(passphraseProvider))
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9,
            )
            .build()
    }

    @Provides
    @Singleton
    fun provideDatabaseKeyChecker(database: AppDatabase): DatabaseKeyChecker =
        RoomDatabaseKeyChecker(database)

    @Provides
    @Singleton
    fun provideDatabaseRecoveryUseCase(
        @ApplicationContext context: Context,
        database: AppDatabase,
        passphraseProvider: DatabasePassphraseProvider,
        databaseSeeder: DatabaseSeeder,
    ): DatabaseRecoveryUseCase =
        AppDatabaseRecoveryUseCase(context, database, passphraseProvider, databaseSeeder)
}
