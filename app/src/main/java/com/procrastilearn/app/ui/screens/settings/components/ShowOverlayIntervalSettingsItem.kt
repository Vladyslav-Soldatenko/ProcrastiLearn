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
import androidx.compose.ui.unit.dp

@Composable
fun ShowOverlayIntervalSettingsItem(
    value: Int,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text("Show overlay each X minutes while blocked app is open") },
        supportingContent = {
            Text(
                "Each $value minutes",
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
