package com.atlaspeak.data.db.seed

import androidx.room.withTransaction
import com.atlaspeak.data.db.AppDatabase
import com.atlaspeak.data.db.entity.AppSettingsEntity
import com.atlaspeak.data.db.entity.AuthSecurityEntity
import javax.inject.Inject

class DatabaseSeeder @Inject constructor(
    private val database: AppDatabase,
) {
    suspend fun seed() {
        database.withTransaction {
            database.referenceDao().insertMuscleGroups(SeedData.muscleGroups)
            database.exerciseDao().insertExercises(SeedData.exercises)
            database.cardioDao().insertCardioTypes(SeedData.cardioTypes)
            database.settingsDao().insertSettings(AppSettingsEntity())
            database.authSecurityDao().insertAuthSecurity(AuthSecurityEntity())
            database.healthConnectDao().insertSyncLogs(SeedData.hcSyncLogs)
        }
    }
}
