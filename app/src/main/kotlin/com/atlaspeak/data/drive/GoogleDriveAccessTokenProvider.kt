package com.atlaspeak.data.drive

import android.content.Context
import com.atlaspeak.BuildConfig
import com.atlaspeak.core.google.awaitResult
import com.atlaspeak.data.drive.DriveAccessTokenResult.Failed
import com.atlaspeak.data.drive.DriveAccessTokenResult.Granted
import com.atlaspeak.data.drive.DriveAccessTokenResult.MissingAuthorization
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleDriveAccessTokenProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : DriveAccessTokenProvider {
    override suspend fun silentAccessToken(): DriveAccessTokenResult {
        val builder = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(Scopes.DRIVE_APPFOLDER)))
        if (BuildConfig.OAUTH_WEB_CLIENT_ID.isNotBlank()) {
            builder.requestOfflineAccess(BuildConfig.OAUTH_WEB_CLIENT_ID)
        }
        val request = builder.build()
        return runCatching {
            val result = Identity.getAuthorizationClient(context).authorize(request).awaitResult()
            val accessToken = result.accessToken
            when {
                result.hasResolution() -> MissingAuthorization
                accessToken.isNullOrBlank() -> MissingAuthorization
                else -> Granted(accessToken)
            }
        }.getOrDefault(Failed)
    }
}
