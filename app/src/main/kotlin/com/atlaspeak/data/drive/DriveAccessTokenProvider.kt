package com.atlaspeak.data.drive

interface DriveAccessTokenProvider {
    suspend fun silentAccessToken(): String?
}
