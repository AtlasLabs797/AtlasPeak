package com.atlaspeak.presentation.auth

import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.fragment.app.FragmentActivity
import com.atlaspeak.BuildConfig
import com.atlaspeak.domain.model.auth.GoogleSignInResult
import com.atlaspeak.domain.usecase.auth.GoogleAuthClient
import com.atlaspeak.domain.usecase.auth.GoogleSignInConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

class CredentialManagerGoogleAuthClient(
    private val activity: FragmentActivity,
) : GoogleAuthClient {
    private val credentialManager = CredentialManager.create(activity)

    override suspend fun signIn(): GoogleSignInResult {
        val config = GoogleSignInConfig(BuildConfig.OAUTH_WEB_CLIENT_ID)
        if (!config.isConfigured) return GoogleSignInResult.NotConfigured

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(
                GetGoogleIdOption.Builder()
                    .setServerClientId(config.webClientId)
                    .setFilterByAuthorizedAccounts(false)
                    .build(),
            )
            .build()

        return try {
            val response = credentialManager.getCredential(activity, request)
            val credential = GoogleIdTokenCredential.createFrom(response.credential.data)
            GoogleSignInResult.Success(
                idToken = credential.idToken,
                googleId = credential.id,
                email = credential.id,
                displayName = credential.displayName,
            )
        } catch (_: GetCredentialCancellationException) {
            GoogleSignInResult.Cancelled
        } catch (_: GetCredentialException) {
            GoogleSignInResult.Failed
        } catch (_: IllegalArgumentException) {
            GoogleSignInResult.Failed
        }
    }
}
