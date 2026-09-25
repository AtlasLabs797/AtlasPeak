package com.atlaspeak.data.db.seed

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.atlaspeak.data.db.AppDatabase
import com.atlaspeak.data.db.entity.AuthSecurityEntity
import com.atlaspeak.data.db.entity.UserEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SEC-025 / SEC-036 (auditoria P2): instalaciones anteriores al retiro del login local
 * pueden conservar PII en `users`/`auth_security`. `DatabaseSeeder.seed()` debe sanearla
 * en cada arranque sin romper el resto del seed ni la idempotencia.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseSeederInstrumentedTest {
    private lateinit var database: AppDatabase
    private lateinit var seeder: DatabaseSeeder

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        seeder = DatabaseSeeder(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun seedSanitizesLegacyLoginPiiIdempotently() = runTest {
        database.userDao().upsertUser(
            UserEntity(
                id = "user-legacy",
                googleId = "google-1",
                email = "athlete@example.com",
                passwordHash = "hash",
                passwordSalt = "salt",
                createdAt = 1_700_000_000_000,
                lastLoginAt = 1_700_000_500_000,
            ),
        )
        database.authSecurityDao().insertAuthSecurity(AuthSecurityEntity(failedAttempts = 3, lockedUntil = 1_700_000_600_000))

        seeder.seed()

        val sanitized = database.userDao().getLocalUser()
        assertEquals("user-legacy", sanitized?.id)
        assertNull(sanitized?.googleId)
        assertNull(sanitized?.email)
        assertNull(sanitized?.passwordHash)
        assertNull(sanitized?.passwordSalt)
        assertNull(sanitized?.lastLoginAt)
        val authSecurity = database.authSecurityDao().getAuthSecurity()
        assertEquals(0, authSecurity?.failedAttempts)
        assertNull(authSecurity?.lockedUntil)

        // Idempotente: repetir el seed (arranque normal siguiente) no falla ni cambia nada mas.
        seeder.seed()
        val secondPass = database.userDao().getLocalUser()
        assertNull(secondPass?.googleId)
    }
}
