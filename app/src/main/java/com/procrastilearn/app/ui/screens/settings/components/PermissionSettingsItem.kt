package com.procrastilearn.app.ui.screens.settings.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PermissionSettingsItem(
    headline: String,
    supportingText: String,
    isChecked: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(headline) },
        supportingContent = {
            Text(
                supportingText,
                style = MaterialTheme.typography.bodySmall,
            )
        },
        trailingContent = {
            Checkbox(
                checked = isChecked,
                onCheckedChange = null,
            )
        },
        modifier =
            Modifier
                .clickable { onClick() }
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .fillMaxWidth(),
    )
}
