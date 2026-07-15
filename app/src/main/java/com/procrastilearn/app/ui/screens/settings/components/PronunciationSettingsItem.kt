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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.procrastilearn.app.R

@Composable
fun PronunciationSettingsItem(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_pronunciation_title)) },
        supportingContent = {
            Text(
                stringResource(R.string.settings_pronunciation_desc),
                style = MaterialTheme.typography.bodySmall,
            )
        },
        trailingContent = {
            Checkbox(
                checked = isEnabled,
                onCheckedChange = null,
                modifier =
                    Modifier
                        .testTag("pronunciation_settings_checkbox")
                        .semantics {
                            role = Role.Checkbox
                            this[SemanticsProperties.ToggleableState] =
                                if (isEnabled) {
                                    ToggleableState.On
                                } else {
                                    ToggleableState.Off
                                }
                        },
            )
        },
        modifier =
            Modifier
                .clickable { onToggle(!isEnabled) }
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .fillMaxWidth(),
    )
}
