package com.atlaspeak.presentation.recovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atlaspeak.R
import com.atlaspeak.presentation.component.AtlasDialog
import com.atlaspeak.presentation.component.AtlasPrimaryButton
import com.atlaspeak.presentation.component.AtlasSecondaryButton
import com.atlaspeak.presentation.component.PremiumBackground
import com.atlaspeak.presentation.component.PremiumCard
import com.atlaspeak.presentation.component.PremiumIconBadge
import com.atlaspeak.presentation.theme.LocalSpacing

@Composable
fun RecoveryRoute(
    onResetCompleted: () -> Unit,
    viewModel: RecoveryViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    LaunchedEffect(state.resetCompleted) {
        if (state.resetCompleted) onResetCompleted()
    }
    RecoveryScreen(
        isResetting = state.isResetting,
        resetFailed = state.resetFailed,
        onConfirmReset = viewModel::resetLocalData,
    )
}

@Composable
private fun RecoveryScreen(
    isResetting: Boolean,
    resetFailed: Boolean,
    onConfirmReset: () -> Unit,
) {
    val spacing = LocalSpacing.current
    var showConfirmDialog by remember { mutableStateOf(false) }

    PremiumBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(spacing.screen),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PremiumIconBadge(filled = true) {
                Icon(
                    imageVector = Icons.Filled.Error,
                    contentDescription = null,
                )
            }
            Spacer(Modifier.height(spacing.md))
            Text(
                text = stringResource(R.string.recovery_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(spacing.sm))
            PremiumCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(spacing.card),
            ) {
                Text(
                    text = stringResource(R.string.recovery_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (resetFailed) {
                Spacer(Modifier.height(spacing.sm))
                Text(
                    text = stringResource(R.string.recovery_reset_failed),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(spacing.lg))
            if (isResetting) {
                CircularProgressIndicator()
            } else {
                AtlasPrimaryButton(
                    text = stringResource(R.string.recovery_reset_action),
                    onClick = { showConfirmDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (showConfirmDialog) {
        AtlasDialog(
            title = stringResource(R.string.recovery_confirm_title),
            message = stringResource(R.string.recovery_confirm_body),
            onDismissRequest = { showConfirmDialog = false },
            confirmButton = {
                AtlasPrimaryButton(
                    text = stringResource(R.string.recovery_confirm_action),
                    onClick = {
                        showConfirmDialog = false
                        onConfirmReset()
                    },
                )
            },
            dismissButton = {
                AtlasSecondaryButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = { showConfirmDialog = false },
                )
            },
        )
    }
}
