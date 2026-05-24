package com.atlaspeak.data.backup

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class LocalBackupExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val snapshotStore: BackupSnapshotStore,
    private val backupJsonCodec: BackupJsonCodec,
    private val backupFileCodec: BackupFileCodec,
    private val backupExportFormatter: BackupExportFormatter,
) {
    suspend fun writeEncryptedBackup(password: CharArray): SharedExportFile = withContext(Dispatchers.IO) {
        require(password.isNotEmpty()) { "Password required" }
        val snapshot = snapshotStore.snapshot()
        val encrypted = backupFileCodec.encrypt(backupJsonCodec.encode(snapshot).encodeToByteArray(), password)
        val file = exportFile("atlas_peak_backup_${timestamp()}.enc")
        file.writeBytes(encrypted)
        file.toSharedFile("application/octet-stream")
    }

    suspend fun writeManualJson(): SharedExportFile = withContext(Dispatchers.IO) {
        val snapshot = backupExportFormatter.manualJson(snapshotStore.snapshot())
        val file = exportFile("atlas_peak_export_${timestamp()}.json")
        file.writeText(backupJsonCodec.encode(snapshot), Charsets.UTF_8)
        file.toSharedFile("application/json")
    }

    suspend fun writeCsvZip(): SharedExportFile = withContext(Dispatchers.IO) {
        val csvFiles = backupExportFormatter.csvFiles(snapshotStore.snapshot())
        val file = exportFile("atlas_peak_csv_${timestamp()}.zip")
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            csvFiles.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        file.toSharedFile("application/zip")
    }

    private fun exportFile(fileName: String): File {
        val directory = File(context.filesDir, EXPORT_DIRECTORY).apply { mkdirs() }
        return File(directory, fileName)
    }

    private fun File.toSharedFile(mimeType: String): SharedExportFile {
        return SharedExportFile(
            uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", this),
            mimeType = mimeType,
            fileName = name,
        )
    }

    private fun timestamp(): String {
        val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return formatter.format(Date())
    }

    companion object {
        private const val EXPORT_DIRECTORY = "exports"
    }
}

data class SharedExportFile(
    val uri: Uri,
    val mimeType: String,
    val fileName: String,
)
