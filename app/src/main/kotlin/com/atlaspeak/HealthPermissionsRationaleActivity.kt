package com.atlaspeak

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import com.atlaspeak.presentation.privacy.PrivacyPolicyContent
import com.atlaspeak.presentation.theme.AtlasPeakTheme
import com.atlaspeak.presentation.theme.LocalSpacing

class HealthPermissionsRationaleActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AtlasPeakTheme {
                HealthPermissionsRationaleScreen(onClose = ::finish)
            }
        }
    }
}

@Composable
private fun HealthPermissionsRationaleScreen(onClose: () -> Unit) {
    val spacing = LocalSpacing.current
    var showPrivacyPolicy by remember { mutableStateOf(false) }
    if (showPrivacyPolicy) {
        BackHandler { showPrivacyPolicy = false }
    }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(spacing.screen),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            if (showPrivacyPolicy) {
                PrivacyPolicyContent(modifier = Modifier.fillMaxSize().weight(1f))
                Button(
                    onClick = { showPrivacyPolicy = false },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.action_close))
                }
            } else {
                Text(
                    text = stringResource(R.string.health_rationale_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = stringResource(R.string.health_rationale_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { showPrivacyPolicy = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.action_view_privacy_policy))
                }
                Button(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.action_close))
                }
            }
        }
    }
}
