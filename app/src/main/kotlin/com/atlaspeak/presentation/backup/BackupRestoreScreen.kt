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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.atlaspeak.domain.model.backup.BackupFailure
import com.atlaspeak.domain.model.backup.DriveBackup
import com.atlaspeak.domain.model.backup.SharedBackupExport
import com.atlaspeak.presentation.component.AtlasPrimaryButton
import com.atlaspeak.presentation.component.AtlasDialog
import com.atlaspeak.presentation.component.AtlasSecondaryButton
import com.atlaspeak.presentation.component.AtlasSwitchRow
import com.atlaspeak.presentation.component.AtlasTextField
import com.atlaspeak.presentation.component.PremiumBackground
import com.atlaspeak.presentation.component.PremiumCard
import com.atlaspeak.presentation.theme.LocalAtlasColors
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
                    DriveAction.Reconnect -> {
                        viewModel.onDriveReauthorized()
                        viewModel.loadDriveBackups(result.accessToken)
                    }
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
        onConfirmPasswordChanged = viewModel::onConfirmPasswordChanged,
        onAutoBackupChanged = viewModel::setAutoBackupEnabled,
        onUpdateAutoBackupPassword = viewModel::updateAutoBackupPassword,
        onCreateLocalBackup = viewModel::createLocalEncryptedBackup,
        onExportJson = viewModel::requestExportJson,
        onExportCsv = viewModel::requestExportCsv,
        onCancelCleartextExport = viewModel::cancelCleartextExport,
        onConfirmCleartextExport = viewModel::confirmCleartextExport,
        onReconnectDrive = { requestDrive(DriveAction.Reconnect) },
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
    onConfirmPasswordChanged: (String) -> Unit,
    onAutoBackupChanged: (Boolean) -> Unit,
    onUpdateAutoBackupPassword: () -> Unit,
    onCreateLocalBackup: () -> Unit,
    onExportJson: () -> Unit,
    onExportCsv: () -> Unit,
    onCancelCleartextExport: () -> Unit,
    onConfirmCleartextExport: () -> Unit,
    onReconnectDrive: () -> Unit,
    onRefreshDrive: () -> Unit,
    onCreateDriveBackup: () -> Unit,
    onConfirmRestore: (String) -> Unit,
    onCancelRestore: () -> Unit,
    onRestoreDriveBackup: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    if (state.pendingCleartextExport != null) {
        AtlasDialog(
            onDismissRequest = onCancelCleartextExport,
            title = stringResource(R.string.backup_cleartext_export_title),
            message = stringResource(R.string.backup_cleartext_export_body),
            confirmButton = {
                AtlasPrimaryButton(
                    onClick = onConfirmCleartextExport,
                    text = stringResource(R.string.backup_cleartext_export_confirm),
                )
            },
            dismissButton = {
                AtlasSecondaryButton(
                    onClick = onCancelCleartextExport,
                    text = stringResource(R.string.action_cancel),
                )
            },
        )
    }
    PremiumBackground(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(spacing.screen),
            verticalArrangement = Arrangement.spacedBy(spacing.cardGap),
        ) {
            item { BackupHeader(onBack) }
            if (state.messageRes != null) {
                item {
                    val atlasColors = LocalAtlasColors.current
                    Text(
                        text = stringResource(state.messageRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = atlasColors.ink2,
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
            // BUG-108: los flags `requiresDriveAuthorization`/`lastError` son
            // estado persistido del ultimo intento de backup automatico. Si
            // el usuario apaga el backup automatico, esos avisos ("Auto-backup
            // detenido / reconecta Drive") ya no aplican y no deben seguir
            // mostrandose.
            if (state.autoBackupEnabled && state.requiresDriveAuthorization) {
                item {
                    DriveAuthorizationWarning(onReconnectDrive = onReconnectDrive)
                }
            } else if (state.autoBackupEnabled && state.lastError != null) {
                item {
                    LastBackupErrorWarning(error = state.lastError)
                }
            }
            item {
                PasswordCard(
                    password = state.password,
                    confirmPassword = state.confirmPassword,
                    autoBackupEnabled = state.autoBackupEnabled,
                    lastBackupAt = state.lastBackupAt,
                    onPasswordChanged = onPasswordChanged,
                    onConfirmPasswordChanged = onConfirmPasswordChanged,
                    onAutoBackupChanged = onAutoBackupChanged,
                    onUpdateAutoBackupPassword = onUpdateAutoBackupPassword,
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
private fun DriveAuthorizationWarning(onReconnectDrive: () -> Unit) {
    val spacing = LocalSpacing.current
    val colors = LocalAtlasColors.current
    PremiumCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.card),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = stringResource(R.string.backup_drive_reconnect_title),
                style = MaterialTheme.typography.titleMedium,
                color = colors.risk,
            )
            Text(
                text = stringResource(R.string.backup_drive_reconnect_body),
                style = MaterialTheme.typography.bodySmall,
                color = colors.ink2,
            )
            AtlasPrimaryButton(
                onClick = onReconnectDrive,
                text = stringResource(R.string.backup_drive_reconnect_action),
            )
        }
    }
}

@Composable
private fun LastBackupErrorWarning(error: BackupFailure) {
    val spacing = LocalSpacing.current
    val colors = LocalAtlasColors.current
    val bodyRes = when (error) {
        BackupFailure.Network -> R.string.backup_last_error_network
        BackupFailure.Crypto -> R.string.backup_last_error_crypto
        BackupFailure.InvalidBackup -> R.string.backup_last_error_invalid
        BackupFailure.NotAuthorized,
        BackupFailure.EmptyPassword,
        BackupFailure.Unknown,
        -> R.string.backup_last_error_unknown
    }
    PremiumCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.card),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(
                text = stringResource(R.string.backup_last_error_title),
                style = MaterialTheme.typography.titleMedium,
                color = colors.warn,
            )
            Text(
                text = stringResource(bodyRes),
                style = MaterialTheme.typography.bodySmall,
                color = colors.ink2,
            )
        }
    }
}

@Composable
private fun BackupHeader(onBack: () -> Unit) {
    val spacing = LocalSpacing.current
    val atlasColors = LocalAtlasColors.current
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
                color = atlasColors.ink,
            )
            Text(
                text = stringResource(R.string.backup_screen_body),
                style = MaterialTheme.typography.bodyMedium,
                color = atlasColors.ink2,
            )
        }
    }
}

@Composable
private fun PasswordCard(
    password: String,
    confirmPassword: String,
    autoBackupEnabled: Boolean,
    lastBackupAt: Long?,
    onPasswordChanged: (String) -> Unit,
    onConfirmPasswordChanged: (String) -> Unit,
    onAutoBackupChanged: (Boolean) -> Unit,
    onUpdateAutoBackupPassword: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val atlasColors = LocalAtlasColors.current
    PremiumCard(modifier = Modifier.fillMaxWidth()) {
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
                    color = atlasColors.ink,
                )
                Text(
                    text = stringResource(R.string.backup_password_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = atlasColors.ink2,
                )
                AtlasTextField(
                    value = password,
                    onValueChange = onPasswordChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.backup_password_label),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardType = KeyboardType.Password,
                )
                AtlasTextField(
                    value = confirmPassword,
                    onValueChange = onConfirmPasswordChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.backup_password_confirm_label),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardType = KeyboardType.Password,
                )
                AtlasSwitchRow(
                    modifier = Modifier.fillMaxWidth(),
                    title = stringResource(R.string.backup_auto_title),
                    subtitle = stringResource(
                        R.string.backup_last_backup,
                        lastBackupAt?.formatTimestamp() ?: stringResource(R.string.backup_last_never),
                    ),
                    checked = autoBackupEnabled,
                    onCheckedChange = onAutoBackupChanged,
                )
                if (autoBackupEnabled) {
                    Text(
                        text = stringResource(R.string.backup_password_change_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = atlasColors.warn,
                    )
                    AtlasSecondaryButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onUpdateAutoBackupPassword,
                        text = stringResource(R.string.backup_password_update_action),
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
    PremiumCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.card),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            SectionTitle(R.string.backup_drive_title, R.string.backup_drive_body)
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                AtlasPrimaryButton(
                    modifier = Modifier.weight(1f),
                    onClick = onCreateDriveBackup,
                    text = stringResource(R.string.backup_drive_create),
                    leadingIcon = Icons.Filled.CloudUpload,
                )
                AtlasSecondaryButton(
                    modifier = Modifier.weight(1f),
                    onClick = onRefreshDrive,
                    text = stringResource(R.string.backup_drive_refresh),
                    leadingIcon = Icons.Filled.CloudDownload,
                )
            }
            if (backups.isEmpty()) {
                Text(
                    text = stringResource(R.string.backup_drive_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalAtlasColors.current.ink3,
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
    val atlasColors = LocalAtlasColors.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text(
            text = backup.name,
            style = MaterialTheme.typography.titleSmall,
            color = atlasColors.ink,
        )
        Text(
            text = stringResource(
                R.string.backup_file_meta,
                backup.createdTimeMillis?.formatTimestamp() ?: stringResource(R.string.backup_last_never),
                backup.sizeBytes.formatSize(),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = atlasColors.ink3,
        )
        if (isPendingRestore) {
            Text(
                text = stringResource(R.string.backup_restore_confirm_body),
                style = MaterialTheme.typography.bodySmall,
                color = atlasColors.risk,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                AtlasPrimaryButton(
                    onClick = { onRestoreDriveBackup(backup.id) },
                    text = stringResource(R.string.backup_restore_confirm),
                )
                AtlasSecondaryButton(
                    onClick = onCancelRestore,
                    text = stringResource(R.string.action_cancel),
                )
            }
        } else {
            AtlasSecondaryButton(
                onClick = { onConfirmRestore(backup.id) },
                text = stringResource(R.string.backup_restore_action),
            )
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
    PremiumCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.card),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            SectionTitle(R.string.backup_export_title, R.string.backup_export_body)
            AtlasPrimaryButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onCreateLocalBackup,
                text = stringResource(R.string.backup_local_create),
                leadingIcon = Icons.Filled.Download,
            )
            AtlasSecondaryButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onExportJson,
                text = stringResource(R.string.backup_export_json),
                leadingIcon = Icons.Filled.UploadFile,
            )
            AtlasSecondaryButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onExportCsv,
                text = stringResource(R.string.backup_export_csv),
                leadingIcon = Icons.Filled.UploadFile,
            )
        }
    }
}

@Composable
private fun SectionTitle(titleRes: Int, bodyRes: Int) {
    val spacing = LocalSpacing.current
    val atlasColors = LocalAtlasColors.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleMedium,
            color = atlasColors.ink,
        )
        Text(
            text = stringResource(bodyRes),
            style = MaterialTheme.typography.bodySmall,
            color = atlasColors.ink2,
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

@Composable
private fun Long?.formatSize(): String {
    val bytes = this ?: return stringResource(R.string.backup_size_unknown)
    val kb = (bytes + 1023L) / 1024L
    return if (kb < 1024L) {
        stringResource(R.string.backup_size_kb, kb)
    } else {
        stringResource(R.string.backup_size_mb, bytes / (1024.0 * 1024.0))
    }
}

private sealed interface DriveAction {
    data object RefreshList : DriveAction
    data object CreateBackup : DriveAction
    data object Reconnect : DriveAction
    data class Restore(val fileId: String) : DriveAction
}

private fun DriveAction.toSavedValue(): String = when (this) {
    DriveAction.CreateBackup -> "create"
    DriveAction.RefreshList -> "refresh"
    DriveAction.Reconnect -> "reconnect"
    is DriveAction.Restore -> "restore:$fileId"
}

private fun String.toDriveAction(): DriveAction? = when {
    this == "create" -> DriveAction.CreateBackup
    this == "refresh" -> DriveAction.RefreshList
    this == "reconnect" -> DriveAction.Reconnect
    startsWith("restore:") -> DriveAction.Restore(removePrefix("restore:"))
    else -> null
}
