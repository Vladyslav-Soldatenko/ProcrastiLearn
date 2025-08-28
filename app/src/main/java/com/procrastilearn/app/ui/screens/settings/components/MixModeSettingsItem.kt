package com.procrastilearn.app.ui.screens.settings.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.procrastilearn.app.domain.model.MixMode

@Composable
fun MixModeSettingsItem(
    mixMode: MixMode,
    onClick: () -> Unit,
) {
    val modeText =
        when (mixMode) {
            MixMode.MIX -> "Mixed"
            MixMode.REVIEWS_FIRST -> "Reviews First"
            MixMode.NEW_FIRST -> "New First"
        }

    ListItem(
        headlineContent = { Text("Study Mode") },
        supportingContent = {
            Text(
                modeText,
                style = MaterialTheme.typography.bodySmall,
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
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
