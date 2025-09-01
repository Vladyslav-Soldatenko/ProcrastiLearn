package com.procrastilearn.app.ui.screens.settings.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.procrastilearn.app.R
import com.procrastilearn.app.domain.model.MixMode
import com.procrastilearn.app.ui.theme.MyApplicationTheme

@Composable
fun MixModeDialog(
    currentMode: MixMode,
    onModeSelected: (MixMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_study_mode_title)) },
        text = {
            Column {
                MixMode.entries.forEach { mode ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onModeSelected(mode) }
                                .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = mode == currentMode,
                            onClick = { onModeSelected(mode) },
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text =
                                    when (mode) {
                                        MixMode.MIX -> stringResource(R.string.settings_study_mode_mixed)
                                        MixMode.REVIEWS_FIRST ->
                                            stringResource(R.string.settings_study_mode_reviews_first)
                                        MixMode.NEW_FIRST -> stringResource(R.string.settings_study_mode_new_first)
                                    },
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text =
                                    when (mode) {
                                        MixMode.MIX -> stringResource(R.string.settings_study_mode_mixed_desc)
                                        MixMode.REVIEWS_FIRST ->
                                            stringResource(R.string.settings_study_mode_reviews_first_desc)
                                        MixMode.NEW_FIRST -> stringResource(R.string.settings_study_mode_new_first_desc)
                                    },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
fun MixModeDialogPreview() {
    MyApplicationTheme {
        MixModeDialog(
            currentMode = MixMode.MIX,
            onModeSelected = {},
            onDismiss = {},
        )
    }
}
