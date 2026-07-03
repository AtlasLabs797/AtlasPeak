package com.atlaspeak.data.drive

import okio.Buffer
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RetrofitDriveBackupServiceTest {
    @Test
    fun `Drive upload body uses multipart related with metadata before encrypted media`() {
        val body = driveMultipartUploadBody(
            metadataJson = """{"name":"atlas_peak_backup.enc","parents":["appDataFolder"]}""",
            encryptedBytes = byteArrayOf(0x41, 0x54, 0x50, 0x4B),
        )
        val buffer = Buffer()

        body.writeTo(buffer)
        val request = buffer.readUtf8()

        assertTrue(body.contentType().toString().startsWith("multipart/related; boundary="))
        assertTrue(request.contains("Content-Type: application/json; charset=utf-8"))
        assertTrue(request.contains("Content-Type: application/octet-stream"))
        assertFalse(request.contains("form-data"))
        assertTrue(request.indexOf("appDataFolder") < request.indexOf("ATPK"))
    }

    @Test
    fun `Drive backup file name filter only accepts Atlas Peak encrypted backups`() {
        assertTrue("atlas_peak_backup_20260701_143015.enc".isAtlasPeakBackupFileName())

        assertFalse("atlas_peak_backup_20260701.enc".isAtlasPeakBackupFileName())
        assertFalse("other_atlas_peak_backup_20260701_143015.enc".isAtlasPeakBackupFileName())
        assertFalse("atlas_peak_backup_20260701_143015.json".isAtlasPeakBackupFileName())
        assertFalse("atlas_peak_backup_20260701_143015.enc.tmp".isAtlasPeakBackupFileName())
    }
}
