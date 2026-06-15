package com.atlaspeak.presentation.component

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.atlaspeak.presentation.theme.LocalAtlasColors

@Composable
fun AtlasTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
) {
    val atlasColors = LocalAtlasColors.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.heightIn(min = 60.dp),
        enabled = enabled,
        singleLine = singleLine,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        placeholder = placeholder?.let { { Text(it) } },
        supportingText = supportingText?.let { { Text(it) } },
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = MaterialTheme.shapes.large,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = atlasColors.ink,
            unfocusedBorderColor = atlasColors.lineStrong,
            focusedContainerColor = atlasColors.surface,
            unfocusedContainerColor = atlasColors.surface,
            focusedTextColor = atlasColors.ink,
            unfocusedTextColor = atlasColors.ink,
            focusedLabelColor = atlasColors.ink2,
            unfocusedLabelColor = atlasColors.ink3,
            cursorColor = atlasColors.ink,
        ),
    )
}
