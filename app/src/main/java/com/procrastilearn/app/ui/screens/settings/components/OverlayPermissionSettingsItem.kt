package com.procrastilearn.app.ui.screens.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.procrastilearn.app.R

@Composable
fun OverlayPermissionItem(
    isGranted: Boolean,
    onClick: () -> Unit,
) {
    PermissionSettingsItem(
        headline = stringResource(R.string.settings_overlay_headline),
        supportingText = stringResource(R.string.settings_overlay_support),
        isChecked = isGranted,
        onClick = onClick,
    )
}
