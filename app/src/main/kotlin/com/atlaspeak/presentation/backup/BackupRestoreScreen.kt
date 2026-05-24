package com.atlaspeak.presentation.backup

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atlaspeak.R
import com.atlaspeak.domain.model.backup.DriveBackup
import com.atlaspeak.domain.model.backup.SharedBackupExport
import com.atlaspeak.presentation.theme.LocalSpacing
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

@Composable
fun BackupRestoreRoute(
    onBack: () -> Unit,
    viewModel: BackupRestoreViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val scope = rememberCoroutineScope()
    val driveClient = remember(activity) { activity?.let(::GoogleDriveAuthorizationClient) }
    var pendingDriveActionKey by rememberSaveable { mutableStateOf<String?>(null) }

    fun handleAuthorization(result: DriveAuthorizationResult) {
        when (result) {
            is DriveAuthorizationResult.Authorized -> {
                when (val action = pendingDriveActionKey?.toDriveAction()) {
                    DriveAction.CreateBackup -> viewModel.createDriveBackup(result.accessToken)
                    DriveAction.RefreshList -> viewModel.loadDriveBackups(result.accessToken)
                    is DriveAction.Restore -> viewModel.restoreDriveBackup(result.accessToken, action.fileId)
                    null -> Unit
                }
                pendingDriveActionKey = null
            }
            DriveAuthorizationResult.Cancelled,
            DriveAuthorizationResult.Failed,
            -> {
                pendingDriveActionKey = null
                viewModel.onDriveAuthorizationFailed()
            }
            is DriveAuthorizationResult.RequiresResolution -> Unit
        }
    }

    val authorizationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            handleAuthorization(driveClient?.complete(result.data) ?: DriveAuthorizationResult.Failed)
        } else {
            handleAuthorization(DriveAuthorizationResult.Cancelled)
        }
    }

    fun requestDrive(action: DriveAction) {
        val client = driveClient
        if (client == null) {
            viewModel.onDriveAuthorizationFailed()
            return
        }
        pendingDriveActionKey = action.toSavedValue()
        scope.launch {
            when (val result = client.requestAccess()) {
                is DriveAuthorizationResult.RequiresResolution -> {
                    authorizationLauncher.launch(IntentSenderRequest.Builder(result.intentSender).build())
                }
                else -> handleAuthorization(result)
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is BackupRestoreEvent.Share -> context.shareFile(event.file)
            }
        }
    }

    BackupRestoreScreen(
        state = state,
        onBack = onBack,
        onPasswordChanged = viewModel::onPasswordChanged,
        onAutoBackupChanged = viewModel::setAutoBackupEnabled,
        onCreateLocalBackup = viewModel::createLocalEncryptedBackup,
        onExportJson = viewModel::exportJson,
        onExportCsv = viewModel::exportCsv,
        onRefreshDrive = { requestDrive(DriveAction.RefreshList) },
        onCreateDriveBackup = { requestDrive(DriveAction.CreateBackup) },
        onConfirmRestore = viewModel::confirmRestore,
        onCancelRestore = viewModel::cancelRestore,
        onRestoreDriveBackup = { requestDrive(DriveAction.Restore(it)) },
    )
}

@Composable
fun BackupRestoreScreen(
    state: BackupRestoreUiState,
    onBack: () -> Unit,
    onPasswordChanged: (String) -> Unit,
    onAutoBackupChanged: (Boolean) -> Unit,
    onCreateLocalBackup: () -> Unit,
    onExportJson: () -> Unit,
    onExportCsv: () -> Unit,
    onRefreshDrive: () -> Unit,
    onCreateDriveBackup: () -> Unit,
    onConfirmRestore: (String) -> Unit,
    onCancelRestore: () -> Unit,
    onRestoreDriveBackup: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(spacing.screen),
            verticalArrangement = Arrangement.spacedBy(spacing.cardGap),
        ) {
            item { BackupHeader(onBack) }
            if (state.messageRes != null) {
                item {
                    Text(
                        text = stringResource(state.messageRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (state.isLoading) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
            item {
                PasswordCard(
                    password = state.password,
                    autoBackupEnabled = state.autoBackupEnabled,
                    lastBackupAt = state.lastBackupAt,
                    onPasswordChanged = onPasswordChanged,
                    onAutoBackupChanged = onAutoBackupChanged,
                )
            }
            item {
                DriveBackupCard(
                    backups = state.driveBackups,
                    pendingRestoreFileId = state.pendingRestoreFileId,
                    onRefreshDrive = onRefreshDrive,
                    onCreateDriveBackup = onCreateDriveBackup,
                    onConfirmRestore = onConfirmRestore,
                    onCancelRestore = onCancelRestore,
                    onRestoreDriveBackup = onRestoreDriveBackup,
                )
            }
            item {
                ExportCard(
                    onCreateLocalBackup = onCreateLocalBackup,
                    onExportJson = onExportJson,
                    onExportCsv = onExportCsv,
                )
            }
        }
    }
}

@Composable
private fun BackupHeader(onBack: () -> Unit) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(
                text = stringResource(R.string.screen_backup_restore_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.backup_screen_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PasswordCard(
    password: String,
    autoBackupEnabled: Boolean,
    lastBackupAt: Long?,
    onPasswordChanged: (String) -> Unit,
    onAutoBackupChanged: (Boolean) -> Unit,
) {
    val spacing = LocalSpacing.current
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = spacing.minTouchTarget)
                .padding(spacing.card),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Text(
                    text = stringResource(R.string.backup_password_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.backup_password_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.auth_password_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.backup_auto_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(
                                R.string.backup_last_backup,
                                lastBackupAt?.formatTimestamp() ?: stringResource(R.string.backup_last_never),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = autoBackupEnabled,
                        onCheckedChange = onAutoBackupChanged,
                    )
                }
            }
        }
    }
}

@Composable
private fun DriveBackupCard(
    backups: List<DriveBackup>,
    pendingRestoreFileId: String?,
    onRefreshDrive: () -> Unit,
    onCreateDriveBackup: () -> Unit,
    onConfirmRestore: (String) -> Unit,
    onCancelRestore: () -> Unit,
    onRestoreDriveBackup: (String) -> Unit,
) {
    val spacing = LocalSpacing.current
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.card),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            SectionTitle(R.string.backup_drive_title, R.string.backup_drive_body)
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Button(onClick = onCreateDriveBackup, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.CloudUpload, contentDescription = null)
                    Spacer(Modifier.width(spacing.xs))
                    Text(stringResource(R.string.backup_drive_create))
                }
                OutlinedButton(onClick = onRefreshDrive, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.CloudDownload, contentDescription = null)
                    Spacer(Modifier.width(spacing.xs))
                    Text(stringResource(R.string.backup_drive_refresh))
                }
            }
            if (backups.isEmpty()) {
                Text(
                    text = stringResource(R.string.backup_drive_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                backups.forEach { backup ->
                    BackupFileRow(
                        backup = backup,
                        isPendingRestore = pendingRestoreFileId == backup.id,
                        onConfirmRestore = onConfirmRestore,
                        onCancelRestore = onCancelRestore,
                        onRestoreDriveBackup = onRestoreDriveBackup,
                    )
                }
            }
        }
    }
}

@Composable
private fun BackupFileRow(
    backup: DriveBackup,
    isPendingRestore: Boolean,
    onConfirmRestore: (String) -> Unit,
    onCancelRestore: () -> Unit,
    onRestoreDriveBackup: (String) -> Unit,
) {
    val spacing = LocalSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text(
            text = backup.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(
                R.string.backup_file_meta,
                backup.createdTimeMillis?.formatTimestamp() ?: stringResource(R.string.backup_last_never),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (isPendingRestore) {
            Text(
                text = stringResource(R.string.backup_restore_confirm_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Button(onClick = { onRestoreDriveBackup(backup.id) }) {
                    Text(stringResource(R.string.backup_restore_confirm))
                }
                OutlinedButton(onClick = onCancelRestore) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        } else {
            OutlinedButton(onClick = { onConfirmRestore(backup.id) }) {
                Text(stringResource(R.string.backup_restore_action))
            }
        }
    }
}

@Composable
private fun ExportCard(
    onCreateLocalBackup: () -> Unit,
    onExportJson: () -> Unit,
    onExportCsv: () -> Unit,
) {
    val spacing = LocalSpacing.current
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.card),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            SectionTitle(R.string.backup_export_title, R.string.backup_export_body)
            Button(onClick = onCreateLocalBackup, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Download, contentDescription = null)
                Spacer(Modifier.width(spacing.xs))
                Text(stringResource(R.string.backup_local_create))
            }
            OutlinedButton(onClick = onExportJson, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.UploadFile, contentDescription = null)
                Spacer(Modifier.width(spacing.xs))
                Text(stringResource(R.string.backup_export_json))
            }
            OutlinedButton(onClick = onExportCsv, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.UploadFile, contentDescription = null)
                Spacer(Modifier.width(spacing.xs))
                Text(stringResource(R.string.backup_export_csv))
            }
        }
    }
}

@Composable
private fun SectionTitle(titleRes: Int, bodyRes: Int) {
    val spacing = LocalSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(bodyRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun Context.shareFile(file: SharedBackupExport) {
    val sendIntent = Intent(Intent.ACTION_SEND)
        .setType(file.mimeType)
        .putExtra(Intent.EXTRA_STREAM, Uri.parse(file.uri))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    startActivity(Intent.createChooser(sendIntent, getString(R.string.backup_share_title)))
}

private fun Long.formatTimestamp(): String {
    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(this))
}

private sealed interface DriveAction {
    data object RefreshList : DriveAction
    data object CreateBackup : DriveAction
    data class Restore(val fileId: String) : DriveAction
}

private fun DriveAction.toSavedValue(): String = when (this) {
    DriveAction.CreateBackup -> "create"
    DriveAction.RefreshList -> "refresh"
    is DriveAction.Restore -> "restore:$fileId"
}

private fun String.toDriveAction(): DriveAction? = when {
    this == "create" -> DriveAction.CreateBackup
    this == "refresh" -> DriveAction.RefreshList
    startsWith("restore:") -> DriveAction.Restore(removePrefix("restore:"))
    else -> null
}
