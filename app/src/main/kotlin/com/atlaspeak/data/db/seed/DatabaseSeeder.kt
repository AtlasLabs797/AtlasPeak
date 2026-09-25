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
            database.routineDao().insertRoutines(SeedData.defaultRoutines)
            database.routineDao().insertRoutineExercises(SeedData.defaultRoutineExercises)
            database.weeklyPlanDao().insertDefaultDays(SeedData.defaultWeeklyPlan)
            database.settingsDao().insertSettings(AppSettingsEntity())
            database.authSecurityDao().insertAuthSecurity(AuthSecurityEntity())
            database.healthConnectDao().insertSyncLogs(SeedData.hcSyncLogs)
            sanitizeLegacyLoginData()
        }
    }

    /**
     * SEC-025 / SEC-036 (auditoria): instalaciones antiguas (antes del retiro del login local)
     * pueden conservar google_id/email/password_hash/password_salt/last_login_at en `users` y
     * un `auth_security` con intentos fallidos. Esos valores viajan tal cual en los backups de
     * Drive. Idempotente: en una fila ya saneada, el UPDATE no cambia nada.
     */
    private suspend fun sanitizeLegacyLoginData() {
        database.userDao().sanitizeLegacyLoginColumns()
        database.authSecurityDao().resetAuthSecurity()
    }
}
