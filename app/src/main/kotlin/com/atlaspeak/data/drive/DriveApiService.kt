package com.atlaspeak.data.drive

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface DriveApiService {
    @POST("upload/drive/v3/files")
    suspend fun uploadBackup(
        @Header("Authorization") authorization: String,
        @Query("uploadType") uploadType: String = "multipart",
        @Query("fields") fields: String = DRIVE_FILE_FIELDS,
        @Body body: RequestBody,
    ): DriveFileDto

    @GET("drive/v3/files")
    suspend fun listBackups(
        @Header("Authorization") authorization: String,
        @Query("spaces") spaces: String = "appDataFolder",
        @Query("q") query: String = BACKUP_QUERY,
        @Query("fields") fields: String = "files($DRIVE_FILE_FIELDS)",
        @Query("orderBy") orderBy: String = "createdTime desc",
        @Query("pageSize") pageSize: Int = 100,
    ): DriveFileListDto

    @GET("drive/v3/files/{fileId}")
    suspend fun downloadBackup(
        @Header("Authorization") authorization: String,
        @Path("fileId") fileId: String,
        @Query("alt") alt: String = "media",
    ): ResponseBody

    @DELETE("drive/v3/files/{fileId}")
    suspend fun deleteBackup(
        @Header("Authorization") authorization: String,
        @Path("fileId") fileId: String,
    )

    companion object {
        const val DRIVE_FILE_FIELDS = "id,name,createdTime,size"
        const val BACKUP_QUERY = "name contains 'atlas_peak_backup_' and name contains '.enc'"
    }
}

@Serializable
data class DriveFileListDto(
    val files: List<DriveFileDto> = emptyList(),
)

@Serializable
data class DriveFileDto(
    val id: String,
    val name: String,
    @SerialName("createdTime") val createdTime: String? = null,
    val size: String? = null,
)
