package com.atlaspeak.presentation.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atlaspeak.R
import com.atlaspeak.presentation.component.AtlasListRow
import com.atlaspeak.presentation.component.AtlasPrimaryButton
import com.atlaspeak.presentation.component.AtlasSecondaryButton
import com.atlaspeak.presentation.component.AtlasStatusMessage
import com.atlaspeak.presentation.component.AtlasStatusTone
import com.atlaspeak.presentation.component.AtlasSwitchRow
import com.atlaspeak.presentation.component.PremiumBackground
import com.atlaspeak.presentation.theme.LocalAtlasColors
import com.atlaspeak.presentation.theme.LocalSpacing

@Composable
fun ProfileRoute(
    onEditProfile: () -> Unit,
    onWeeklyPlan: () -> Unit,
    onSettings: () -> Unit,
    onBackupRestore: () -> Unit,
    onPrivacyPolicy: () -> Unit,
    onDataDeleted: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ProfileEvent.DataDeleted -> {
                    if (event.driveNotDeleted) {
                        Toast.makeText(context, R.string.delete_all_data_partial_success, Toast.LENGTH_LONG).show()
                    }
                    onDataDeleted()
                }
            }
        }
    }
    ProfileScreen(
        state = state,
        onEditProfile = onEditProfile,
        onWeeklyPlan = onWeeklyPlan,
        onSettings = onSettings,
        onBackupRestore = onBackupRestore,
        onPrivacyPolicy = onPrivacyPolicy,
        onDeleteAllDataRequested = viewModel::requestDeleteAllData,
        onDeleteAllDataDismissed = viewModel::cancelDeleteAllData,
        onDeleteDriveBackupsToggled = viewModel::setDeleteDriveBackupsToo,
        onDeleteAllDataConfirmed = viewModel::confirmDeleteAllData,
    )
}

@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onEditProfile: () -> Unit,
    onWeeklyPlan: () -> Unit,
    onSettings: () -> Unit,
    onBackupRestore: () -> Unit,
    onPrivacyPolicy: () -> Unit,
    onDeleteAllDataRequested: () -> Unit,
    onDeleteAllDataDismissed: () -> Unit,
    onDeleteDriveBackupsToggled: (Boolean) -> Unit,
    onDeleteAllDataConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val atlasColors = LocalAtlasColors.current
    PremiumBackground(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(spacing.screen),
            verticalArrangement = Arrangement.spacedBy(spacing.cardGap),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Text(
                        text = stringResource(R.string.screen_profile_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = atlasColors.ink,
                    )
                    Text(
                        text = stringResource(R.string.profile_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = atlasColors.ink2,
                    )
                }
            }
            item {
                ProfileSectionTitle(R.string.profile_section_profile)
            }
            item {
                AtlasListRow(
                    title = state.displayName?.takeIf(String::isNotBlank)
                        ?: stringResource(R.string.profile_edit_no_name),
                    subtitle = stringResource(R.string.profile_edit_body),
                    leadingIcon = Icons.Filled.Edit,
                    onClick = onEditProfile,
                )
            }
            item {
                ProfileSectionTitle(R.string.profile_section_settings)
            }
            item {
                AtlasListRow(
                    title = stringResource(R.string.profile_weekly_plan),
                    subtitle = stringResource(R.string.profile_weekly_plan_body),
                    leadingIcon = Icons.AutoMirrored.Filled.EventNote,
                    onClick = onWeeklyPlan,
                )
            }
            item {
                AtlasListRow(
                    title = stringResource(R.string.profile_notification_settings),
                    subtitle = stringResource(R.string.profile_notification_settings_body),
                    leadingIcon = Icons.Filled.Notifications,
                    onClick = onSettings,
                )
            }
            item {
                ProfileSectionTitle(R.string.profile_section_data)
            }
            item {
                AtlasListRow(
                    title = stringResource(R.string.screen_backup_restore_title),
                    subtitle = stringResource(R.string.profile_backup_body),
                    leadingIcon = Icons.Filled.Backup,
                    onClick = onBackupRestore,
                )
            }
            item {
                AtlasListRow(
                    title = stringResource(R.string.profile_privacy_policy),
                    subtitle = stringResource(R.string.profile_privacy_policy_body),
                    leadingIcon = Icons.Filled.Shield,
                    onClick = onPrivacyPolicy,
                )
            }
            item {
                AtlasListRow(
                    title = stringResource(R.string.profile_delete_all_data),
                    subtitle = stringResource(R.string.profile_delete_all_data_body),
                    leadingIcon = Icons.Filled.DeleteForever,
                    onClick = onDeleteAllDataRequested,
                )
            }
            if (state.deleteAllDataMessageRes != null) {
                item {
                    AtlasStatusMessage(
                        message = stringResource(state.deleteAllDataMessageRes),
                        tone = AtlasStatusTone.Error,
                    )
                }
            }
        }
    }
    if (state.showDeleteAllDataDialog) {
        DeleteAllDataDialog(
            deleteDriveBackupsToo = state.deleteDriveBackupsToo,
            isDeleting = state.isDeletingAllData,
            onDismiss = onDeleteAllDataDismissed,
            onDriveOptionToggled = onDeleteDriveBackupsToggled,
            onConfirm = onDeleteAllDataConfirmed,
        )
    }
}

@Composable
private fun DeleteAllDataDialog(
    deleteDriveBackupsToo: Boolean,
    isDeleting: Boolean,
    onDismiss: () -> Unit,
    onDriveOptionToggled: (Boolean) -> Unit,
    onConfirm: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val atlasColors = LocalAtlasColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = atlasColors.surface3,
        titleContentColor = atlasColors.ink,
        textContentColor = atlasColors.ink2,
        title = {
            Text(text = stringResource(R.string.delete_all_data_dialog_title), style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                Text(
                    text = stringResource(R.string.delete_all_data_dialog_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                AtlasSwitchRow(
                    title = stringResource(R.string.delete_all_data_dialog_drive_option),
                    subtitle = stringResource(R.string.delete_all_data_dialog_drive_hint),
                    checked = deleteDriveBackupsToo,
                    onCheckedChange = onDriveOptionToggled,
                    enabled = !isDeleting,
                )
            }
        },
        confirmButton = {
            if (isDeleting) {
                CircularProgressIndicator()
            } else {
                AtlasPrimaryButton(
                    onClick = onConfirm,
                    text = stringResource(R.string.delete_all_data_dialog_confirm),
                )
            }
        },
        dismissButton = {
            AtlasSecondaryButton(
                onClick = onDismiss,
                text = stringResource(R.string.action_cancel),
            )
        },
    )
}

@Composable
private fun ProfileSectionTitle(@StringRes titleRes: Int) {
    Text(
        text = stringResource(titleRes).uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = LocalAtlasColors.current.ink3,
    )
}
