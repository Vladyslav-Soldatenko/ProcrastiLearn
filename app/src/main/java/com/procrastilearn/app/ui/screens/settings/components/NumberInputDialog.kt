package com.procrastilearn.app.ui.screens.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import com.procrastilearn.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumberInputDialog(
    title: String,
    currentValue: Int,
    onValueConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
    minValue: Int = 0,
    maxValue: Int = Int.MAX_VALUE,
    description: String? = null,
    validateValue: (Int) -> String? = { null },
    saveError: String? = null,
    isSaving: Boolean = false,
) {
    var textValue by remember { mutableStateOf(currentValue.toString()) }
    val currentInput = textValue.toIntOrNull()
    val validationError = currentInput?.let(validateValue)
    val error = validationError ?: saveError

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(title)
                description?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        text = {
            OutlinedTextField(
                value = textValue,
                enabled = !isSaving,
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                onValueChange = { newValue ->
                    if (newValue.all { it.isDigit() }) {
                        textValue = newValue
                    }
                },
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                    ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled =
                    !isSaving && currentInput != null && currentInput in minValue..maxValue && validationError == null,
                onClick = {
                    textValue.toIntOrNull()?.let { value ->
                        if (!isSaving && value in minValue..maxValue && validateValue(value) == null) {
                            onValueConfirm(value)
                        }
                    }
                },
            ) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
