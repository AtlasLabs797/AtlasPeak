package com.atlaspeak.di

import com.atlaspeak.data.backup.BackupFileCodec
import com.atlaspeak.data.backup.BackupJsonCodec
import com.atlaspeak.data.backup.BackupSnapshotStore
import com.atlaspeak.data.backup.DriveBackupManager
import com.atlaspeak.data.backup.DriveBackupService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BackupModule {
    @Provides
    @Singleton
    fun provideBackupFileCodec(): BackupFileCodec = BackupFileCodec()

    @Provides
    @Singleton
    fun provideDriveBackupManager(
        snapshotStore: BackupSnapshotStore,
        backupJsonCodec: BackupJsonCodec,
        backupFileCodec: BackupFileCodec,
        driveBackupService: DriveBackupService,
    ): DriveBackupManager = DriveBackupManager(
        snapshotStore = snapshotStore,
        backupJsonCodec = backupJsonCodec,
        backupFileCodec = backupFileCodec,
        driveBackupService = driveBackupService,
    )
}
