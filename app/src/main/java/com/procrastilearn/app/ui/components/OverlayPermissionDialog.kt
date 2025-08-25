package com.procrastilearn.app.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.procrastilearn.app.R

@Composable
fun OverlayPermissionDialog(
    onOpenSettings: () -> Unit,
    onSkip: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { /* keep modal */ },
        title = { Text(stringResource(R.string.overlay_permission_title)) },
        text = {
            Text(stringResource(R.string.overlay_permission_message))
        },
        confirmButton = {
            Button(onClick = onOpenSettings) {
                Text(stringResource(R.string.overlay_permission_open_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onSkip) {
                Text(stringResource(R.string.overlay_permission_not_now))
            }
        },
    )
}

@Preview
@Composable
private fun OverlayPermissionDialogPreview() {
    OverlayPermissionDialog(onOpenSettings = {}, onSkip = { })
}
