package com.atlaspeak.data.repository

import androidx.room.withTransaction
import com.atlaspeak.data.db.AppDatabase
import com.atlaspeak.data.db.entity.AppSettingsEntity
import com.atlaspeak.data.db.entity.AuthSecurityEntity
import com.atlaspeak.data.db.entity.UserEntity
import com.atlaspeak.domain.model.auth.AuthSecurityState
import com.atlaspeak.domain.model.auth.LocalAuthPolicy
import com.atlaspeak.domain.model.auth.LocalUser
import com.atlaspeak.domain.repository.AuthRepository
import com.atlaspeak.domain.security.PasswordHash
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomAuthRepository @Inject constructor(
    private val database: AppDatabase,
) : AuthRepository {
    override suspend fun getLocalUser(): LocalUser? {
        return database.userDao().getLocalUser()?.toDomain()
    }

    override suspend fun createOrUpdateLocalPassword(passwordHash: PasswordHash, nowMillis: Long) {
        database.withTransaction {
            val existing = database.userDao().getLocalUser()
            database.userDao().upsertUser(
                UserEntity(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    googleId = existing?.googleId,
                    email = existing?.email,
                    passwordHash = passwordHash.hashBase64,
                    passwordSalt = passwordHash.saltBase64,
                    createdAt = existing?.createdAt ?: nowMillis,
                    lastLoginAt = existing?.lastLoginAt,
                ),
            )
        }
    }

    override suspend fun markLogin(nowMillis: Long) {
        database.userDao().getLocalUser()?.let { user ->
            database.userDao().updateLastLoginAt(user.id, nowMillis)
        }
    }

    override suspend fun isBiometricUnlockEnabled(): Boolean {
        return database.settingsDao().getSettings()?.biometricsEnabled == true
    }

    override suspend fun getUnlockTimeoutMinutes(): Int {
        return database.settingsDao().getSettings()?.biometricTimeoutMin ?: DEFAULT_UNLOCK_TIMEOUT_MINUTES
    }

    override suspend fun setBiometricUnlockEnabled(enabled: Boolean) {
        database.withTransaction {
            database.settingsDao().insertSettings(AppSettingsEntity())
            database.settingsDao().updateBiometricsEnabled(enabled)
        }
    }

    override suspend fun getAuthSecurityState(): AuthSecurityState {
        return database.authSecurityDao().getAuthSecurity()?.toDomain() ?: AuthSecurityState()
    }

    override suspend fun recordFailedPasswordAttempt(nowMillis: Long): AuthSecurityState {
        return database.withTransaction {
            val current = database.authSecurityDao().getAuthSecurity() ?: AuthSecurityEntity()
            val failedAttempts = current.failedAttempts + 1
            val updated = AuthSecurityEntity(
                id = 1,
                failedAttempts = failedAttempts,
                lockedUntil = if (failedAttempts >= LocalAuthPolicy.MAX_FAILED_ATTEMPTS) {
                    nowMillis + LocalAuthPolicy.LOCKOUT_MILLIS
                } else {
                    null
                },
            )
            database.authSecurityDao().upsertAuthSecurity(updated)
            updated.toDomain()
        }
    }

    override suspend fun resetAuthSecurity() {
        database.authSecurityDao().upsertAuthSecurity(AuthSecurityEntity())
    }

    private fun UserEntity.toDomain() = LocalUser(
        id = id,
        googleId = googleId,
        email = email,
        passwordHash = passwordHash,
        passwordSalt = passwordSalt,
        createdAt = createdAt,
        lastLoginAt = lastLoginAt,
    )

    private fun AuthSecurityEntity.toDomain() = AuthSecurityState(
        failedAttempts = failedAttempts,
        lockedUntilMillis = lockedUntil,
    )

    private companion object {
        const val DEFAULT_UNLOCK_TIMEOUT_MINUTES = 5
    }
}
