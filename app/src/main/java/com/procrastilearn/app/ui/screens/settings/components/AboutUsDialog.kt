package com.procrastilearn.app.ui.screens.settings.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.procrastilearn.app.R
import com.procrastilearn.app.ui.theme.MyApplicationTheme

@Composable
fun AboutUsDialog(
    onDismiss: () -> Unit,
    privacyPolicyUrl: String? = null,
) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_about_us_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(text = stringResource(R.string.settings_about_us_intro))

                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.settings_about_us_mission_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(text = stringResource(R.string.settings_about_us_mission_body))

                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.settings_about_us_who_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(text = stringResource(R.string.settings_about_us_who_body))

                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.settings_about_us_values_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(text = stringResource(R.string.settings_about_us_values_simplicity))
                Text(text = stringResource(R.string.settings_about_us_values_growth))

                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.settings_about_us_contact_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(text = stringResource(R.string.settings_about_us_contact_body))

                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.settings_about_us_privacy),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (privacyPolicyUrl != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier =
                        if (privacyPolicyUrl != null) {
                            Modifier.clickable { uriHandler.openUri(privacyPolicyUrl) }
                        } else {
                            Modifier
                        },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_ok))
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
fun AboutUsDialogPreview() {
    MyApplicationTheme {
        AboutUsDialog(
            onDismiss = {},
            privacyPolicyUrl = "https://procrastilearn.app/privacy",
        )
    }
}
