package com.atlaspeak.domain.usecase.auth

import com.atlaspeak.domain.model.auth.GoogleSignInResult
import javax.inject.Inject

data class GoogleSignInConfig(
    val webClientId: String,
) {
    val isConfigured: Boolean =
        webClientId.isNotBlank() && !webClientId.contains("placeholder", ignoreCase = true)
}

interface GoogleAuthClient {
    suspend fun signIn(): GoogleSignInResult
}

class GoogleSignInUseCase @Inject constructor(
    private val client: GoogleAuthClient,
) {
    suspend operator fun invoke(): GoogleSignInResult = client.signIn()
}
