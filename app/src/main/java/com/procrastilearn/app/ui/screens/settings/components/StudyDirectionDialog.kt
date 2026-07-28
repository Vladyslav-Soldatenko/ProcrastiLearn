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
import com.procrastilearn.app.domain.model.StudyDirectionMode
import com.procrastilearn.app.ui.theme.MyApplicationTheme

@Composable
fun StudyDirectionDialog(
    currentMode: StudyDirectionMode,
    onModeSelected: (StudyDirectionMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_review_direction_title)) },
        text = {
            Column {
                StudyDirectionMode.entries.forEach { mode ->
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
                                        StudyDirectionMode.FORWARD -> stringResource(R.string.settings_review_direction_forward)
                                        StudyDirectionMode.BACKWARD -> stringResource(R.string.settings_review_direction_backward)
                                        StudyDirectionMode.BIDIRECTIONAL ->
                                            stringResource(R.string.settings_review_direction_bidirectional)
                                    },
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text =
                                    when (mode) {
                                        StudyDirectionMode.FORWARD ->
                                            stringResource(R.string.settings_review_direction_forward_desc)
                                        StudyDirectionMode.BACKWARD ->
                                            stringResource(R.string.settings_review_direction_backward_desc)
                                        StudyDirectionMode.BIDIRECTIONAL ->
                                            stringResource(R.string.settings_review_direction_bidirectional_desc)
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
fun StudyDirectionDialogPreview() {
    MyApplicationTheme {
        StudyDirectionDialog(
            currentMode = StudyDirectionMode.FORWARD,
            onModeSelected = {},
            onDismiss = {},
        )
    }
}
