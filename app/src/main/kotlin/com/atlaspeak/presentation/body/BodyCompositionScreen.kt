package com.atlaspeak.presentation.body

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atlaspeak.R
import com.atlaspeak.domain.model.body.BodyCompositionPeriod
import com.atlaspeak.domain.model.body.BodyCompositionSnapshot
import com.atlaspeak.domain.model.body.BodyCompositionSource
import com.atlaspeak.domain.model.body.BodyMetric
import com.atlaspeak.domain.model.body.BodyMetricPoint
import com.atlaspeak.domain.model.body.BodyMetricValue
import com.atlaspeak.presentation.component.AtlasPrimaryButton
import com.atlaspeak.presentation.component.AtlasSecondaryButton
import com.atlaspeak.presentation.component.AtlasTextField
import com.atlaspeak.presentation.component.MonochromeAreaChart
import com.atlaspeak.presentation.component.PeriodSelector
import com.atlaspeak.presentation.component.PeriodSelectorItem
import com.atlaspeak.presentation.component.PremiumBackground
import com.atlaspeak.presentation.component.PremiumCard
import com.atlaspeak.presentation.theme.LocalAtlasColors
import com.atlaspeak.presentation.theme.LocalSpacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun BodyCompositionRoute(
    viewModel: BodyCompositionViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    val healthConnectLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) {
        viewModel.syncHealthConnect()
    }
    BodyCompositionScreen(
        state = state,
        onPeriodSelected = viewModel::selectPeriod,
        onMetricSelected = viewModel::selectMetric,
        onToggleEntryForm = viewModel::toggleEntryForm,
        onDraftChanged = viewModel::updateDraft,
        onSaveDraft = viewModel::saveDraft,
        onRetry = viewModel::refresh,
        onHealthConnectSync = viewModel::syncHealthConnect,
        onRequestHealthConnectPermissions = {
            if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
                healthConnectLauncher.launch(state.healthConnectPermissions)
            } else {
                viewModel.markHealthConnectUnavailable()
            }
        },
    )
}

@Composable
fun BodyCompositionScreen(
    state: BodyCompositionUiState,
    onPeriodSelected: (BodyCompositionPeriod) -> Unit,
    onMetricSelected: (BodyMetric) -> Unit,
    onToggleEntryForm: () -> Unit,
    onDraftChanged: (BodyMetric, String) -> Unit,
    onSaveDraft: () -> Unit,
    onRetry: () -> Unit,
    onHealthConnectSync: () -> Unit,
    onRequestHealthConnectPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    PremiumBackground(modifier = modifier.fillMaxSize()) {
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.snapshot == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(spacing.screen),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(state.messageRes ?: R.string.error_generic),
                    style = MaterialTheme.typography.bodyLarge,
                    color = LocalAtlasColors.current.ink,
                )
                AtlasPrimaryButton(
                    onClick = onRetry,
                    text = stringResource(R.string.action_retry),
                )
            }
        } else {
            BodyCompositionContent(
                state = state,
                snapshot = state.snapshot,
                onPeriodSelected = onPeriodSelected,
                onMetricSelected = onMetricSelected,
                onToggleEntryForm = onToggleEntryForm,
                onDraftChanged = onDraftChanged,
                onSaveDraft = onSaveDraft,
                onHealthConnectSync = onHealthConnectSync,
                onRequestHealthConnectPermissions = onRequestHealthConnectPermissions,
                modifier = Modifier.padding(top = spacing.screen),
            )
        }
    }
}

@Composable
private fun BodyCompositionContent(
    state: BodyCompositionUiState,
    snapshot: BodyCompositionSnapshot,
    onPeriodSelected: (BodyCompositionPeriod) -> Unit,
    onMetricSelected: (BodyMetric) -> Unit,
    onToggleEntryForm: () -> Unit,
    onDraftChanged: (BodyMetric, String) -> Unit,
    onSaveDraft: () -> Unit,
    onHealthConnectSync: () -> Unit,
    onRequestHealthConnectPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val atlasColors = LocalAtlasColors.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = spacing.screen, vertical = spacing.screen),
        verticalArrangement = Arrangement.spacedBy(spacing.cardGap),
    ) {
        item {
            HeaderRow(onToggleEntryForm)
        }
        item {
            HealthConnectCard(
                isSyncing = state.isHealthConnectSyncing,
                onSync = onHealthConnectSync,
                onRequestPermissions = onRequestHealthConnectPermissions,
            )
        }
        state.messageRes?.let { message ->
            item {
                Text(
                    text = stringResource(message),
                    style = MaterialTheme.typography.bodySmall,
                    color = bodyMessageColor(message),
                )
            }
        }
        if (state.isEntryFormVisible) {
            item {
                ManualBodyEntryCard(
                    draft = state.draft,
                    onDraftChanged = onDraftChanged,
                    onSaveDraft = onSaveDraft,
                    onCancel = onToggleEntryForm,
                )
            }
        }
        if (snapshot.latestValues.isEmpty()) {
            item {
                EmptyBodyCard(onToggleEntryForm)
            }
        } else {
            item {
                LatestValuesTable(snapshot.latestValues)
            }
            item {
                BodyPeriodSelector(
                    selectedPeriod = state.selectedPeriod,
                    onPeriodSelected = onPeriodSelected,
                )
            }
            item {
                BodyMetricTabs(
                    selectedMetric = state.selectedMetric,
                    onMetricSelected = onMetricSelected,
                )
            }
            item {
                BodyMetricChartCard(
                    metric = state.selectedMetric,
                    points = snapshot.seriesFor(state.selectedMetric),
                )
            }
        }
    }
}

@Composable
private fun HealthConnectCard(
    isSyncing: Boolean,
    onSync: () -> Unit,
    onRequestPermissions: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val atlasColors = LocalAtlasColors.current
    PremiumCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.card),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = atlasColors.ink2,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.body_health_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = atlasColors.ink,
                    )
                    Text(
                        text = stringResource(if (isSyncing) R.string.body_health_syncing else R.string.body_health_status_ready),
                        style = MaterialTheme.typography.bodySmall,
                        color = atlasColors.ink2,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                AtlasPrimaryButton(
                    modifier = Modifier.weight(1f),
                    onClick = onSync,
                    enabled = !isSyncing,
                    text = stringResource(R.string.body_health_sync),
                    leadingIcon = Icons.Filled.Sync,
                )
                AtlasSecondaryButton(
                    modifier = Modifier.weight(1f),
                    onClick = onRequestPermissions,
                    enabled = !isSyncing,
                    text = stringResource(R.string.body_health_permissions),
                    leadingIcon = Icons.Filled.Settings,
                )
            }
        }
    }
}

@Composable
private fun HeaderRow(onAddClick: () -> Unit) {
    val spacing = LocalSpacing.current
    val atlasColors = LocalAtlasColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            Text(
                text = stringResource(R.string.screen_body_title),
                style = MaterialTheme.typography.headlineMedium,
                color = atlasColors.ink,
            )
            Text(
                text = stringResource(R.string.body_screen_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = atlasColors.ink2,
            )
        }
        AtlasPrimaryButton(
            onClick = onAddClick,
            text = stringResource(R.string.body_add_entry),
            leadingIcon = Icons.Filled.Add,
        )
    }
}

@Composable
private fun EmptyBodyCard(onAddClick: () -> Unit) {
    val spacing = LocalSpacing.current
    val atlasColors = LocalAtlasColors.current
    PremiumCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.card),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Icon(
                imageVector = Icons.Filled.Scale,
                contentDescription = null,
                tint = atlasColors.ink2,
            )
            Text(
                text = stringResource(R.string.body_empty_title),
                style = MaterialTheme.typography.titleMedium,
                color = atlasColors.ink,
            )
            Text(
                text = stringResource(R.string.body_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = atlasColors.ink2,
            )
            AtlasPrimaryButton(
                onClick = onAddClick,
                text = stringResource(R.string.body_add_entry),
                leadingIcon = Icons.Filled.Add,
            )
        }
    }
}

@Composable
private fun LatestValuesTable(values: List<BodyMetricValue>) {
    val spacing = LocalSpacing.current
    val atlasColors = LocalAtlasColors.current
    PremiumCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.card),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = stringResource(R.string.body_latest_values_title),
                style = MaterialTheme.typography.titleMedium,
                color = atlasColors.ink,
            )
            values.forEachIndexed { index, value ->
                if (index > 0) {
                    HorizontalDivider(color = atlasColors.line1)
                }
                LatestValueRow(value)
            }
        }
    }
}

@Composable
private fun LatestValueRow(value: BodyMetricValue) {
    val spacing = LocalSpacing.current
    val atlasColors = LocalAtlasColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Icon(
            imageVector = Icons.Filled.MonitorWeight,
            contentDescription = null,
            tint = atlasColors.ink2,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(value.metric.labelRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = atlasColors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value.timestamp.formatDate(),
                style = MaterialTheme.typography.bodySmall,
                color = atlasColors.ink3,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = value.value.formattedValue(value.metric),
                style = MaterialTheme.typography.titleMedium,
                color = atlasColors.ink,
            )
            SourceBadge(metric = value.metric)
        }
    }
}

@Composable
private fun SourceBadge(metric: BodyMetric) {
    val spacing = LocalSpacing.current
    val atlasColors = LocalAtlasColors.current
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = atlasColors.fillSoft,
        contentColor = atlasColors.ink3,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, atlasColors.line1),
    ) {
        Text(
            text = stringResource(
                if (metric.isHealthConnectSyncable()) {
                    R.string.body_badge_hc_ready
                } else {
                    R.string.body_badge_manual_only
                },
            ).uppercase(),
            modifier = Modifier.padding(horizontal = spacing.xs, vertical = spacing.xxs),
            style = MaterialTheme.typography.labelSmall,
            color = atlasColors.ink3,
        )
    }
}

@Composable
private fun ManualBodyEntryCard(
    draft: BodyCompositionDraft,
    onDraftChanged: (BodyMetric, String) -> Unit,
    onSaveDraft: () -> Unit,
    onCancel: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val atlasColors = LocalAtlasColors.current
    PremiumCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.card),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = stringResource(R.string.body_manual_entry_title),
                style = MaterialTheme.typography.titleMedium,
                color = atlasColors.ink,
            )
            BodyMetric.entries.forEach { metric ->
                AtlasTextField(
                    value = draft.value(metric),
                    onValueChange = { onDraftChanged(metric, it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(metric.labelRes()),
                    supportingText = stringResource(
                        if (metric.isHealthConnectSyncable()) {
                            R.string.body_badge_hc_ready
                        } else {
                            R.string.body_badge_manual_only
                        },
                    ),
                    keyboardType = KeyboardType.Decimal,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm, Alignment.End),
            ) {
                AtlasSecondaryButton(
                    onClick = onCancel,
                    text = stringResource(R.string.action_cancel),
                )
                AtlasPrimaryButton(
                    onClick = onSaveDraft,
                    text = stringResource(R.string.action_save),
                )
            }
        }
    }
}

@Composable
private fun BodyPeriodSelector(
    selectedPeriod: BodyCompositionPeriod,
    onPeriodSelected: (BodyCompositionPeriod) -> Unit,
) {
    PeriodSelector(
        items = BodyCompositionPeriod.entries.map { PeriodSelectorItem(it, it.labelRes()) },
        selected = selectedPeriod,
        onSelected = onPeriodSelected,
    )
}

@Composable
private fun BodyMetricTabs(
    selectedMetric: BodyMetric,
    onMetricSelected: (BodyMetric) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.xs)) {
        items(BodyMetric.entries, key = { it.name }) { metric ->
            MetricSelectorChip(
                selected = selectedMetric == metric,
                onClick = { onMetricSelected(metric) },
                label = stringResource(metric.labelRes()),
            )
        }
    }
}

@Composable
private fun MetricSelectorChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val atlasColors = LocalAtlasColors.current
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) atlasColors.ink else atlasColors.fillSoft,
        contentColor = if (selected) atlasColors.onAccent else atlasColors.ink2,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, if (selected) atlasColors.ink else atlasColors.line1),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
            style = if (selected) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun BodyMetricChartCard(metric: BodyMetric, points: List<BodyMetricPoint>) {
    val spacing = LocalSpacing.current
    val atlasColors = LocalAtlasColors.current
    PremiumCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.card),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = stringResource(metric.labelRes()),
                style = MaterialTheme.typography.titleMedium,
                color = atlasColors.ink,
            )
            if (points.isEmpty()) {
                Text(
                    text = stringResource(R.string.body_chart_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = atlasColors.ink2,
                )
            } else {
                BodyLineChart(
                    points = points,
                    metricLabel = stringResource(metric.labelRes()),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                )
            }
        }
    }
}

@Composable
private fun BodyLineChart(
    points: List<BodyMetricPoint>,
    metricLabel: String,
    modifier: Modifier = Modifier,
) {
    val atlasColors = LocalAtlasColors.current
    val validPoints = points.filter { it.value.isFinite() }
    if (validPoints.isEmpty()) {
        Text(
            text = stringResource(R.string.body_chart_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = atlasColors.ink2,
        )
        return
    }
    val chartDescription = stringResource(
        R.string.body_chart_summary,
        metricLabel,
        validPoints.size,
        validPoints.first().timestamp.formatDate(),
        validPoints.last().timestamp.formatDate(),
        validPoints.last().value.formattedValue(validPoints.last().metric),
    )
    MonochromeAreaChart(
        values = validPoints.map { it.value.toFloat() },
        modifier = modifier.semantics { contentDescription = chartDescription },
    )
}

@Composable
private fun bodyMessageColor(@StringRes messageRes: Int) = when (messageRes) {
    R.string.body_entry_saved,
    R.string.body_health_sync_success,
    -> LocalAtlasColors.current.ink
    else -> LocalAtlasColors.current.risk
}

@StringRes
private fun BodyCompositionPeriod.labelRes(): Int = when (this) {
    BodyCompositionPeriod.Week -> R.string.progress_period_week
    BodyCompositionPeriod.Month -> R.string.progress_period_month
    BodyCompositionPeriod.ThreeMonths -> R.string.progress_period_three_months
    BodyCompositionPeriod.Year -> R.string.progress_period_year
    BodyCompositionPeriod.YearToDate -> R.string.progress_period_ytd
}

@StringRes
private fun BodyMetric.labelRes(): Int = when (this) {
    BodyMetric.Weight -> R.string.body_metric_weight
    BodyMetric.BodyFat -> R.string.body_metric_body_fat
    BodyMetric.MuscleMass -> R.string.body_metric_muscle_mass
    BodyMetric.Water -> R.string.body_metric_water
    BodyMetric.BodyWaterMass -> R.string.body_metric_body_water_mass
    BodyMetric.VisceralFat -> R.string.body_metric_visceral_fat
    BodyMetric.Protein -> R.string.body_metric_protein
    BodyMetric.BoneMass -> R.string.body_metric_bone_mass
    BodyMetric.BodyAge -> R.string.body_metric_body_age
}

private fun BodyMetric.isHealthConnectSyncable(): Boolean = when (this) {
    BodyMetric.Weight,
    BodyMetric.BodyFat,
    BodyMetric.MuscleMass,
    BodyMetric.BodyWaterMass -> true
    BodyMetric.Water -> false
    BodyMetric.VisceralFat,
    BodyMetric.Protein,
    BodyMetric.BoneMass,
    BodyMetric.BodyAge -> false
}

@Composable
private fun Double.formattedValue(metric: BodyMetric): String {
    return when (metric) {
        BodyMetric.Weight,
        BodyMetric.MuscleMass,
        BodyMetric.BodyWaterMass,
        BodyMetric.BoneMass -> stringResource(R.string.body_value_kg, this)
        BodyMetric.BodyFat,
        BodyMetric.Water,
        BodyMetric.Protein -> stringResource(R.string.body_value_percent, this)
        BodyMetric.VisceralFat,
        BodyMetric.BodyAge -> stringResource(R.string.body_value_integer, roundToInt())
    }
}

private fun Long.formatDate(): String {
    val formatter = DateTimeFormatter.ofPattern("dd MMM", Locale.getDefault())
    return Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(formatter)
}
