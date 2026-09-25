package com.atlaspeak.data.drive

import com.atlaspeak.data.backup.DriveBackupFile
import com.atlaspeak.data.backup.DriveBackupService
import java.io.ByteArrayOutputStream
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody

@Singleton
class RetrofitDriveBackupService @Inject constructor(
    private val api: DriveApiService,
) : DriveBackupService {
    private val json = Json { encodeDefaults = true }

    override suspend fun uploadBackup(
        accessToken: String,
        fileName: String,
        encryptedBytes: ByteArray,
    ): DriveBackupFile {
        val metadata = DriveFileMetadata(name = fileName)
        return api.uploadBackup(
            authorization = accessToken.bearer(),
            body = driveMultipartUploadBody(
                metadataJson = json.encodeToString(metadata),
                encryptedBytes = encryptedBytes,
            ),
        ).toDomain()
    }

    override suspend fun listBackups(accessToken: String): List<DriveBackupFile> {
        return api.listBackups(accessToken.bearer()).files
            .map { it.toDomain() }
            .filter { it.name.isAtlasPeakBackupFileName() }
    }

    override suspend fun downloadBackup(accessToken: String, fileId: String): ByteArray {
        return api.downloadBackup(accessToken.bearer(), fileId).readBounded()
    }

    // P2: sin este limite, un archivo de Drive manipulado o corrupto con un
    // tamano declarado o real enorme podia agotar memoria del dispositivo al
    // leerse entero con `.bytes()`. Se comprueba `Content-Length` cuando esta
    // disponible y ademas se acota la lectura real por si el header falta o
    // miente.
    private fun ResponseBody.readBounded(): ByteArray = use { body ->
        if (body.contentLength() > MAX_BACKUP_BYTES) {
            throw IllegalArgumentException("Backup too large")
        }
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(DOWNLOAD_CHUNK_BYTES)
        var total = 0L
        body.byteStream().use { stream ->
            while (true) {
                val read = stream.read(chunk)
                if (read == -1) break
                total += read
                if (total > MAX_BACKUP_BYTES) {
                    throw IllegalArgumentException("Backup too large")
                }
                buffer.write(chunk, 0, read)
            }
        }
        buffer.toByteArray()
    }

    override suspend fun deleteBackup(accessToken: String, fileId: String) {
        api.deleteBackup(accessToken.bearer(), fileId)
    }

    private fun String.bearer(): String = "Bearer $this"

    private fun DriveFileDto.toDomain(): DriveBackupFile = DriveBackupFile(
        id = id,
        name = name,
        createdTimeMillis = createdTime?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
        sizeBytes = size?.toLongOrNull(),
    )

    @kotlinx.serialization.Serializable
    private data class DriveFileMetadata(
        val name: String,
        val parents: List<String> = listOf("appDataFolder"),
        val mimeType: String = "application/octet-stream",
    )
}

private const val MAX_BACKUP_BYTES = 64L * 1024L * 1024L
private const val DOWNLOAD_CHUNK_BYTES = 64 * 1024

internal fun driveMultipartUploadBody(
    metadataJson: String,
    encryptedBytes: ByteArray,
): RequestBody = MultipartBody.Builder()
    .setType("multipart/related".toMediaType())
    .addPart(metadataJson.toRequestBody(JSON_MEDIA_TYPE))
    .addPart(encryptedBytes.toRequestBody(BACKUP_MEDIA_TYPE))
    .build()

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
private val BACKUP_MEDIA_TYPE = "application/octet-stream".toMediaType()

internal fun String.isAtlasPeakBackupFileName(): Boolean {
    return BACKUP_FILE_NAME_REGEX.matches(this)
}

private val BACKUP_FILE_NAME_REGEX = Regex("^atlas_peak_backup_\\d{8}_\\d{6}\\.enc$")
