package com.procrastilearn.app.ui.screens.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.procrastilearn.app.R
import kotlin.math.min

private const val SINGLE_LINE_COUNT = 1
private const val MULTI_LINE_DEFAULT_COUNT = 4

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StringInputDialog(
    title: String,
    currentValue: String,
    onValueConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    isPassword: Boolean = true,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) SINGLE_LINE_COUNT else MULTI_LINE_DEFAULT_COUNT,
    helperText: String? = null,
) {
    var textValue by remember { mutableStateOf(currentValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                val keyboardOptions =
                    if (isPassword) {
                        KeyboardOptions(keyboardType = KeyboardType.Password)
                    } else {
                        KeyboardOptions.Default
                    }
                val visualTransformation =
                    if (isPassword) {
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    }
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { newValue ->
                        textValue = newValue
                    },
                    keyboardOptions = keyboardOptions,
                    singleLine = singleLine,
                    minLines = if (singleLine) SINGLE_LINE_COUNT else min(MULTI_LINE_DEFAULT_COUNT, maxLines),
                    maxLines = maxLines,
                    visualTransformation = visualTransformation,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (helperText != null) {
                    Text(
                        text = helperText,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onValueConfirm(textValue)
                },
            ) {
                Text(stringResource(id = R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.action_cancel))
            }
        },
    )
}
