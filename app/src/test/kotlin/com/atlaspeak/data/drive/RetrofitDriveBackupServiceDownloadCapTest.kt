package com.atlaspeak.data.drive

import kotlinx.coroutines.test.runTest
import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RetrofitDriveBackupServiceDownloadCapTest {
    @Test
    fun `downloadBackup rejects a response whose declared Content-Length exceeds the cap`() = runTest {
        val service = RetrofitDriveBackupService(
            FakeDriveApiService(
                downloadResponse = object : ResponseBody() {
                    override fun contentType(): MediaType? = null
                    override fun contentLength(): Long = 100L * 1024L * 1024L
                    override fun source(): BufferedSource = Buffer()
                },
            ),
        )

        val result = runCatching { service.downloadBackup("access-token", "file-1") }

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals("Backup too large", result.exceptionOrNull()?.message)
    }

    @Test
    fun `downloadBackup returns the bytes when within the size cap`() = runTest {
        val payload = "atlas-peak-backup".toByteArray()
        val service = RetrofitDriveBackupService(
            FakeDriveApiService(
                downloadResponse = object : ResponseBody() {
                    override fun contentType(): MediaType? = null
                    override fun contentLength(): Long = payload.size.toLong()
                    override fun source(): BufferedSource = Buffer().apply { write(payload) }
                },
            ),
        )

        val result = service.downloadBackup("access-token", "file-1")

        assertEquals(payload.toList(), result.toList())
    }

    private class FakeDriveApiService(
        private val downloadResponse: ResponseBody,
    ) : DriveApiService {
        override suspend fun uploadBackup(
            authorization: String,
            uploadType: String,
            fields: String,
            body: RequestBody,
        ): DriveFileDto = throw UnsupportedOperationException("not used in this test")

        override suspend fun listBackups(
            authorization: String,
            spaces: String,
            query: String,
            fields: String,
            orderBy: String,
            pageSize: Int,
        ): DriveFileListDto = throw UnsupportedOperationException("not used in this test")

        override suspend fun downloadBackup(authorization: String, fileId: String, alt: String): ResponseBody =
            downloadResponse

        override suspend fun deleteBackup(authorization: String, fileId: String) {
            throw UnsupportedOperationException("not used in this test")
        }
    }
}
