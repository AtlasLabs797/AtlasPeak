package com.atlaspeak.di

import com.atlaspeak.data.repository.PreferencesOnboardingRepository
import com.atlaspeak.data.backup.BackupSnapshotStore
import com.atlaspeak.data.backup.DataBackupRepository
import com.atlaspeak.data.backup.RoomBackupSnapshotStore
import com.atlaspeak.data.backup.DriveBackupService
import com.atlaspeak.data.drive.RetrofitDriveBackupService
import com.atlaspeak.data.drive.DriveAccessTokenProvider
import com.atlaspeak.data.drive.GoogleDriveAccessTokenProvider
import com.atlaspeak.data.healthconnect.HealthConnectManager
import com.atlaspeak.data.notification.WorkManagerNotificationScheduler
import com.atlaspeak.data.repository.RoomAppSettingsRepository
import com.atlaspeak.data.repository.RoomBodyCompositionRepository
import com.atlaspeak.data.repository.RoomCardioRepository
import com.atlaspeak.data.repository.RoomDashboardRepository
import com.atlaspeak.data.repository.RoomExerciseRepository
import com.atlaspeak.data.repository.RoomNotificationSettingsRepository
import com.atlaspeak.data.repository.RoomProfileRepository
import com.atlaspeak.data.repository.RoomRoutineRepository
import com.atlaspeak.data.repository.RoomWeeklyPlanRepository
import com.atlaspeak.data.repository.RoomWorkoutSettingsRepository
import com.atlaspeak.data.repository.RoomWorkoutRepository
import com.atlaspeak.domain.repository.BackupRepository
import com.atlaspeak.domain.repository.BodyCompositionRepository
import com.atlaspeak.domain.repository.CardioRepository
import com.atlaspeak.domain.repository.DashboardRepository
import com.atlaspeak.domain.repository.ExerciseRepository
import com.atlaspeak.domain.repository.HealthConnectRepository
import com.atlaspeak.domain.repository.NotificationScheduler
import com.atlaspeak.domain.repository.NotificationSettingsRepository
import com.atlaspeak.domain.repository.OnboardingRepository
import com.atlaspeak.domain.repository.ProfileRepository
import com.atlaspeak.domain.repository.RoutineRepository
import com.atlaspeak.domain.repository.WeeklyPlanRepository
import com.atlaspeak.domain.repository.WorkoutRepository
import com.atlaspeak.domain.repository.WorkoutSettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.atlaspeak.domain.repository.AppSettingsRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {
    @Binds
    abstract fun bindCardioRepository(repository: RoomCardioRepository): CardioRepository

    @Binds
    abstract fun bindAppSettingsRepository(repository: RoomAppSettingsRepository): AppSettingsRepository

    @Binds
    abstract fun bindBodyCompositionRepository(repository: RoomBodyCompositionRepository): BodyCompositionRepository

    @Binds
    abstract fun bindDashboardRepository(repository: RoomDashboardRepository): DashboardRepository

    @Binds
    abstract fun bindHealthConnectRepository(repository: HealthConnectManager): HealthConnectRepository

    @Binds
    abstract fun bindWeeklyPlanRepository(repository: RoomWeeklyPlanRepository): WeeklyPlanRepository

    @Binds
    abstract fun bindNotificationSettingsRepository(repository: RoomNotificationSettingsRepository): NotificationSettingsRepository

    @Binds
    abstract fun bindNotificationScheduler(scheduler: WorkManagerNotificationScheduler): NotificationScheduler

    @Binds
    abstract fun bindBackupSnapshotStore(store: RoomBackupSnapshotStore): BackupSnapshotStore

    @Binds
    abstract fun bindDriveBackupService(service: RetrofitDriveBackupService): DriveBackupService

    @Binds
    abstract fun bindDriveAccessTokenProvider(provider: GoogleDriveAccessTokenProvider): DriveAccessTokenProvider

    @Binds
    abstract fun bindBackupRepository(repository: DataBackupRepository): BackupRepository

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
