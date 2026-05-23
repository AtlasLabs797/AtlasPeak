package com.atlaspeak.domain.usecase.auth

import com.atlaspeak.domain.model.auth.GoogleSignInResult
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GoogleSignInUseCaseTest {
    @Test
    fun `google config rejects blank and placeholder client ids`() {
        assertFalse(GoogleSignInConfig("").isConfigured)
        assertFalse(GoogleSignInConfig("dev-placeholder.apps.googleusercontent.com").isConfigured)
        assertTrue(GoogleSignInConfig("real-client-id.apps.googleusercontent.com").isConfigured)
    }

    @Test
    fun `google sign in delegates to Credential Manager client`() = runTest {
        val result = GoogleSignInResult.Success(
            idToken = "id-token",
            googleId = "google-id",
            email = "user@example.com",
            displayName = "Atlas User",
        )
        val useCase = GoogleSignInUseCase(client = FakeGoogleAuthClient(result))

        assertEquals(result, useCase())
    }

    private class FakeGoogleAuthClient(
        private val result: GoogleSignInResult,
    ) : GoogleAuthClient {
        override suspend fun signIn(): GoogleSignInResult = result
    }
}
