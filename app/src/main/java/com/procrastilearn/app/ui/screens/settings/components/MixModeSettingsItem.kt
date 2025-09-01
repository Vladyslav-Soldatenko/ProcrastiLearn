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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.procrastilearn.app.R
import com.procrastilearn.app.domain.model.MixMode

@Composable
fun MixModeSettingsItem(
    mixMode: MixMode,
    onClick: () -> Unit,
) {
    val modeText =
        when (mixMode) {
            MixMode.MIX -> stringResource(R.string.settings_study_mode_mixed)
            MixMode.REVIEWS_FIRST -> stringResource(R.string.settings_study_mode_reviews_first)
            MixMode.NEW_FIRST -> stringResource(R.string.settings_study_mode_new_first)
        }

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_study_mode_title)) },
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
