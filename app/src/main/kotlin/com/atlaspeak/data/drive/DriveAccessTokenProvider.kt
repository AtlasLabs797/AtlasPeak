package com.atlaspeak.data.drive

interface DriveAccessTokenProvider {
    suspend fun silentAccessToken(): DriveAccessTokenResult
}

sealed interface DriveAccessTokenResult {
    data class Granted(val accessToken: String) : DriveAccessTokenResult
    data object MissingAuthorization : DriveAccessTokenResult
    data object Failed : DriveAccessTokenResult
}
