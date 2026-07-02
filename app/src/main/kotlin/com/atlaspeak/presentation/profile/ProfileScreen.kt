package com.atlaspeak.presentation.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atlaspeak.R
import com.atlaspeak.presentation.component.AtlasListRow
import com.atlaspeak.presentation.component.PremiumBackground
import com.atlaspeak.presentation.theme.LocalAtlasColors
import com.atlaspeak.presentation.theme.LocalSpacing

@Composable
fun ProfileRoute(
    onEditProfile: () -> Unit,
    onWeeklyPlan: () -> Unit,
    onSettings: () -> Unit,
    onBackupRestore: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    ProfileScreen(
        state = state,
        onEditProfile = onEditProfile,
        onWeeklyPlan = onWeeklyPlan,
        onSettings = onSettings,
        onBackupRestore = onBackupRestore,
    )
}

@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onEditProfile: () -> Unit,
    onWeeklyPlan: () -> Unit,
    onSettings: () -> Unit,
    onBackupRestore: () -> Unit,
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
        }
    }
}

@Composable
private fun ProfileSectionTitle(@StringRes titleRes: Int) {
    Text(
        text = stringResource(titleRes).uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = LocalAtlasColors.current.ink3,
    )
}
