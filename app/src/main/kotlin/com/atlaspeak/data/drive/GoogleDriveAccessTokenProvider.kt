package com.atlaspeak.data.drive

import android.content.Context
import com.atlaspeak.core.google.awaitResult
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
    override suspend fun silentAccessToken(): String? {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(Scopes.DRIVE_APPFOLDER)))
            .build()
        return runCatching {
            val result = Identity.getAuthorizationClient(context).authorize(request).awaitResult()
            if (result.hasResolution()) null else result.accessToken
        }.getOrNull()
    }
}
