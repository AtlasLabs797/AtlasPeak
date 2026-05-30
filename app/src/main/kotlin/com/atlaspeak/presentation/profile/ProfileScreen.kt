package com.atlaspeak.presentation.profile

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.atlaspeak.R
import com.atlaspeak.presentation.component.PremiumBackground
import com.atlaspeak.presentation.component.PremiumCard
import com.atlaspeak.presentation.component.PremiumIconBadge
import com.atlaspeak.presentation.theme.LocalSpacing

@Composable
fun ProfileRoute(
    onWeeklyPlan: () -> Unit,
    onSettings: () -> Unit,
    onBackupRestore: () -> Unit,
) {
    ProfileScreen(
        onWeeklyPlan = onWeeklyPlan,
        onSettings = onSettings,
        onBackupRestore = onBackupRestore,
    )
}

@Composable
fun ProfileScreen(
    onWeeklyPlan: () -> Unit,
    onSettings: () -> Unit,
    onBackupRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
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
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = stringResource(R.string.profile_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                ProfileActionCard(
                    titleRes = R.string.profile_weekly_plan,
                    subtitleRes = R.string.profile_weekly_plan_body,
                    icon = Icons.AutoMirrored.Filled.EventNote,
                    onClick = onWeeklyPlan,
                )
            }
            item {
                ProfileActionCard(
                    titleRes = R.string.profile_notification_settings,
                    subtitleRes = R.string.profile_notification_settings_body,
                    icon = Icons.Filled.Notifications,
                    onClick = onSettings,
                )
            }
            item {
                ProfileActionCard(
                    titleRes = R.string.screen_backup_restore_title,
                    subtitleRes = R.string.profile_backup_body,
                    icon = Icons.Filled.Backup,
                    onClick = onBackupRestore,
                )
            }
        }
    }
}

@Composable
private fun ProfileActionCard(
    @StringRes titleRes: Int,
    @StringRes subtitleRes: Int,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val spacing = LocalSpacing.current
    PremiumCard {
        TextButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(spacing.card),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                PremiumIconBadge {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(spacing.xxs),
                ) {
                    Text(
                        text = stringResource(titleRes),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(subtitleRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
