package com.atlaspeak.presentation.backup

import android.content.Intent
import androidx.fragment.app.FragmentActivity
import com.atlaspeak.BuildConfig
import com.atlaspeak.core.google.awaitResult
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope

class GoogleDriveAuthorizationClient(
    private val activity: FragmentActivity,
) {
    private val client = Identity.getAuthorizationClient(activity)

    suspend fun requestAccess(): DriveAuthorizationResult {
        val builder = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(Scopes.DRIVE_APPFOLDER)))
        if (BuildConfig.OAUTH_WEB_CLIENT_ID.isNotBlank()) {
            builder.requestOfflineAccess(BuildConfig.OAUTH_WEB_CLIENT_ID)
        }
        val request = builder.build()
        return runCatching {
            val result = client.authorize(request).awaitResult()
            if (result.hasResolution()) {
                result.pendingIntent?.intentSender?.let(DriveAuthorizationResult::RequiresResolution)
                    ?: DriveAuthorizationResult.Failed
            } else {
                result.accessToken?.let(DriveAuthorizationResult::Authorized) ?: DriveAuthorizationResult.Failed
            }
        }.getOrDefault(DriveAuthorizationResult.Failed)
    }

    fun complete(data: Intent?): DriveAuthorizationResult {
        return try {
            val result = client.getAuthorizationResultFromIntent(data)
            result.accessToken?.let(DriveAuthorizationResult::Authorized) ?: DriveAuthorizationResult.Failed
        } catch (_: ApiException) {
            DriveAuthorizationResult.Failed
        }
    }
}

sealed interface DriveAuthorizationResult {
    data class Authorized(val accessToken: String) : DriveAuthorizationResult
    data class RequiresResolution(val intentSender: android.content.IntentSender) : DriveAuthorizationResult
    data object Failed : DriveAuthorizationResult
    data object Cancelled : DriveAuthorizationResult
}
