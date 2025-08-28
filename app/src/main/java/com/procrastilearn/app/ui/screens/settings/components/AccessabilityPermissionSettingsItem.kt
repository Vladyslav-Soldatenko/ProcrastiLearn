package com.procrastilearn.app.ui.screens.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.procrastilearn.app.R

@Composable
fun AccessibilityPermissionItem(
    isEnabled: Boolean,
    onClick: () -> Unit,
) {
    PermissionSettingsItem(
        headline = stringResource(R.string.settings_a11y_headline),
        supportingText = stringResource(R.string.settings_a11y_support),
        isChecked = isEnabled,
        onClick = onClick,
    )
}
