package com.procrastilearn.app.ui.screens.settings.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.procrastilearn.app.R

@Composable
fun OpenAiApiKeySettingsItem(
    apiKey: String?,
    onClick: () -> Unit,
) {
    val supporting =
        if (apiKey.isNullOrBlank()) stringResource(R.string.settings_openai_api_key_not_set)
        else stringResource(R.string.settings_openai_api_key_set)

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_openai_api_key_title)) },
        supportingContent = {
            Text(
                supporting,
                style = MaterialTheme.typography.bodySmall,
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier =
            Modifier
                .clickable { onClick() }
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .fillMaxWidth(),
    )
}

