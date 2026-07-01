package com.atlaspeak.presentation.planning

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atlaspeak.R
import com.atlaspeak.domain.model.planning.NotificationSettings
import com.atlaspeak.domain.model.settings.AppThemeMode
import com.atlaspeak.presentation.component.AtlasChip
import com.atlaspeak.presentation.component.AtlasPrimaryButton
import com.atlaspeak.presentation.component.AtlasSwitchRow
import com.atlaspeak.presentation.component.AtlasTimeField
import com.atlaspeak.presentation.component.PremiumBackground
import com.atlaspeak.presentation.theme.AppThemeViewModel
import com.atlaspeak.presentation.theme.LocalAtlasColors
import com.atlaspeak.presentation.theme.LocalSpacing

@Composable
fun NotificationSettingsRoute(
    onBack: () -> Unit,
    viewModel: NotificationSettingsViewModel = hiltViewModel(),
    themeViewModel: AppThemeViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val themeMode = themeViewModel.themeMode.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        val canPost = context.canPostNotifications()
        if (canPost) {
            viewModel.setNotificationsEnabled(true)
        } else {
            viewModel.markSystemNotificationsUnavailable()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.reconcileSystemNotificationAvailability(context.canPostNotifications())
    }
    NotificationSettingsScreen(
        state = state,
        themeMode = themeMode,
        onBack = onBack,
        onThemeModeSelected = themeViewModel::setThemeMode,
        onNotificationsEnabledChanged = { enabled ->
            if (!enabled) {
                viewModel.setNotificationsEnabled(false)
            } else if (context.canPostNotifications()) {
                viewModel.setNotificationsEnabled(true)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.markSystemNotificationsUnavailable()
                context.openAppNotificationSettings()
            }
        },
        onMotivationalMessagesChanged = viewModel::setMotivationalMessages,
        onDailySummaryEnabledChanged = viewModel::setDailySummaryEnabled,
        onDailySummaryTimeChanged = viewModel::setDailySummaryTime,
        onWeeklySummaryEnabledChanged = viewModel::setWeeklySummaryEnabled,
        onSave = viewModel::save,
    )
}

private fun Context.canPostNotifications(): Boolean {
    val runtimePermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    return runtimePermissionGranted && NotificationManagerCompat.from(this).areNotificationsEnabled()
}

private fun Context.openAppNotificationSettings() {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

@Composable
fun NotificationSettingsScreen(
    state: NotificationSettingsUiState,
    themeMode: AppThemeMode,
    onBack: () -> Unit,
    onThemeModeSelected: (AppThemeMode) -> Unit,
    onNotificationsEnabledChanged: (Boolean) -> Unit,
    onMotivationalMessagesChanged: (Boolean) -> Unit,
    onDailySummaryEnabledChanged: (Boolean) -> Unit,
    onDailySummaryTimeChanged: (String) -> Unit,
    onWeeklySummaryEnabledChanged: (Boolean) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    PremiumBackground(modifier = modifier.fillMaxSize()) {
        if (state.isLoading) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(spacing.screen),
                verticalArrangement = Arrangement.spacedBy(spacing.cardGap),
            ) {
                item {
                    SettingsHeader(onBack = onBack)
                }
                item {
                    ThemeSettingsControls(
                        selected = themeMode,
                        onSelected = onThemeModeSelected,
                    )
                }
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
                item {
                    NotificationSettingsControls(
                        settings = state.settings,
                        onNotificationsEnabledChanged = onNotificationsEnabledChanged,
                        onMotivationalMessagesChanged = onMotivationalMessagesChanged,
                        onDailySummaryEnabledChanged = onDailySummaryEnabledChanged,
                        onDailySummaryTimeChanged = onDailySummaryTimeChanged,
                        onWeeklySummaryEnabledChanged = onWeeklySummaryEnabledChanged,
                    )
                }
                item {
                    AtlasPrimaryButton(
                        onClick = onSave,
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(R.string.action_save),
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeSettingsControls(
    selected: AppThemeMode,
    onSelected: (AppThemeMode) -> Unit,
) {
    val spacing = LocalSpacing.current
    val atlasColors = LocalAtlasColors.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(
            text = stringResource(R.string.theme_settings_title),
            style = MaterialTheme.typography.titleMedium,
            color = atlasColors.ink,
        )
        Text(
            text = stringResource(R.string.theme_settings_body),
            style = MaterialTheme.typography.bodySmall,
            color = atlasColors.ink2,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            AppThemeMode.entries.forEach { mode ->
                AtlasChip(
                    text = stringResource(mode.labelRes()),
                    selected = selected == mode,
                    onClick = { onSelected(mode) },
                )
            }
        }
    }
}

@StringRes
private fun AppThemeMode.labelRes(): Int {
    return when (this) {
        AppThemeMode.System -> R.string.theme_system
        AppThemeMode.Light -> R.string.theme_light
        AppThemeMode.Dark -> R.string.theme_dark
    }
}

@Composable
private fun SettingsHeader(onBack: () -> Unit) {
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
                text = stringResource(R.string.notification_settings_title),
                style = MaterialTheme.typography.headlineMedium,
                color = atlasColors.ink,
            )
            Text(
                text = stringResource(R.string.notification_settings_body),
                style = MaterialTheme.typography.bodyMedium,
                color = atlasColors.ink2,
            )
        }
    }
}

@Composable
private fun NotificationSettingsControls(
    settings: NotificationSettings,
    onNotificationsEnabledChanged: (Boolean) -> Unit,
    onMotivationalMessagesChanged: (Boolean) -> Unit,
    onDailySummaryEnabledChanged: (Boolean) -> Unit,
    onDailySummaryTimeChanged: (String) -> Unit,
    onWeeklySummaryEnabledChanged: (Boolean) -> Unit,
) {
    val spacing = LocalSpacing.current
    Column(
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        SettingsSwitchRow(
            titleRes = R.string.notification_settings_global,
            bodyRes = R.string.notification_settings_global_body,
            checked = settings.notificationsEnabled,
            onCheckedChange = onNotificationsEnabledChanged,
        )
        SettingsSwitchRow(
            titleRes = R.string.notification_settings_motivation,
            bodyRes = R.string.notification_settings_motivation_body,
            checked = settings.motivationalMessages,
            enabled = settings.notificationsEnabled,
            onCheckedChange = onMotivationalMessagesChanged,
        )
        SettingsSwitchRow(
            titleRes = R.string.notification_settings_daily_summary,
            bodyRes = R.string.notification_settings_daily_summary_body,
            checked = settings.dailySummaryEnabled,
            enabled = settings.notificationsEnabled,
            onCheckedChange = onDailySummaryEnabledChanged,
        )
        AtlasTimeField(
            value = settings.dailySummaryTime,
            onTimeSelected = onDailySummaryTimeChanged,
            modifier = Modifier.fillMaxWidth(),
            enabled = settings.notificationsEnabled && settings.dailySummaryEnabled,
            label = stringResource(R.string.notification_settings_daily_time),
            supportingText = stringResource(R.string.weekly_plan_reminder_best_effort),
        )
        SettingsSwitchRow(
            titleRes = R.string.notification_settings_weekly_summary,
            bodyRes = R.string.notification_settings_weekly_summary_body,
            checked = settings.weeklySummaryEnabled,
            enabled = settings.notificationsEnabled,
            onCheckedChange = onWeeklySummaryEnabledChanged,
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    @StringRes titleRes: Int,
    @StringRes bodyRes: Int,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    AtlasSwitchRow(
        title = stringResource(titleRes),
        subtitle = stringResource(bodyRes),
        checked = checked,
        enabled = enabled,
        onCheckedChange = onCheckedChange,
    )
}
