package com.atlaspeak.presentation.privacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.atlaspeak.R
import com.atlaspeak.presentation.theme.LocalAtlasColors
import com.atlaspeak.presentation.theme.LocalSpacing

/**
 * Contenido de la politica de privacidad (P0 auditoria de privacidad), compartido
 * entre `PrivacyPolicyScreen` (ruta de Perfil) y `HealthPermissionsRationaleActivity`
 * (pantalla de rationale que exige Health Connect).
 */
private data class PrivacySection(val titleRes: Int, val bodyRes: Int)

private val PRIVACY_SECTIONS = listOf(
    PrivacySection(R.string.privacy_section_intro_title, R.string.privacy_section_intro_body),
    PrivacySection(R.string.privacy_section_storage_title, R.string.privacy_section_storage_body),
    PrivacySection(R.string.privacy_section_tracking_title, R.string.privacy_section_tracking_body),
    PrivacySection(R.string.privacy_section_health_title, R.string.privacy_section_health_body),
    PrivacySection(R.string.privacy_section_location_title, R.string.privacy_section_location_body),
    PrivacySection(R.string.privacy_section_backup_title, R.string.privacy_section_backup_body),
    PrivacySection(R.string.privacy_section_export_title, R.string.privacy_section_export_body),
    PrivacySection(R.string.privacy_section_deletion_title, R.string.privacy_section_deletion_body),
    PrivacySection(R.string.privacy_section_contact_title, R.string.privacy_section_contact_body),
)

@Composable
fun PrivacyPolicyContent(modifier: Modifier = Modifier) {
    val spacing = LocalSpacing.current
    val atlasColors = LocalAtlasColors.current
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.cardGap),
    ) {
        item {
            Text(
                text = stringResource(R.string.privacy_policy_updated),
                style = MaterialTheme.typography.bodySmall,
                color = atlasColors.ink3,
            )
        }
        items(PRIVACY_SECTIONS) { section ->
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(
                    text = stringResource(section.titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    color = atlasColors.ink,
                )
                Text(
                    text = stringResource(section.bodyRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = atlasColors.ink2,
                )
            }
        }
    }
}
