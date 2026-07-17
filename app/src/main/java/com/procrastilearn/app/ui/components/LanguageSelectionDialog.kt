package com.procrastilearn.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.procrastilearn.app.R
import com.procrastilearn.app.domain.model.Language
import com.procrastilearn.app.ui.theme.MyApplicationTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelectionDialog(
    onConfirm: (native: Language, target: Language) -> Unit,
    initialNativeLanguage: Language? = null,
    initialTargetLanguage: Language? = null,
    onDismiss: (() -> Unit)? = null,
) {
    var nativeLanguage by remember { mutableStateOf(initialNativeLanguage) }
    var targetLanguage by remember { mutableStateOf(initialTargetLanguage) }

    val isDismissable = onDismiss != null
    val canConfirm = nativeLanguage != null && targetLanguage != null && nativeLanguage != targetLanguage

    AlertDialog(
        onDismissRequest = { onDismiss?.invoke() },
        properties =
            DialogProperties(
                dismissOnBackPress = isDismissable,
                dismissOnClickOutside = isDismissable,
            ),
        title = { Text(stringResource(R.string.language_selection_dialog_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.language_selection_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    LanguageDropdownField(
                        label = stringResource(R.string.language_selection_native_label),
                        selected = nativeLanguage,
                        excluding = targetLanguage,
                        onSelected = { nativeLanguage = it },
                        testTag = "language_selection_native_field",
                    )
                    LanguageDropdownField(
                        label = stringResource(R.string.language_selection_target_label),
                        selected = targetLanguage,
                        excluding = nativeLanguage,
                        onSelected = { targetLanguage = it },
                        testTag = "language_selection_target_field",
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = canConfirm,
                onClick = {
                    val native = nativeLanguage
                    val target = targetLanguage
                    if (native != null && target != null && native != target) {
                        onConfirm(native, target)
                    }
                },
            ) {
                Text(stringResource(R.string.action_continue))
            }
        },
        dismissButton =
            onDismiss?.let { dismiss ->
                {
                    TextButton(onClick = dismiss) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageDropdownField(
    label: String,
    selected: Language?,
    excluding: Language?,
    onSelected: (Language) -> Unit,
    testTag: String,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selected?.let { stringResource(it.displayNameRes) }.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier =
                Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
                    .testTag(testTag),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            Language.entries
                .filter { it != excluding }
                .forEach { language ->
                    DropdownMenuItem(
                        text = { Text(stringResource(language.displayNameRes)) },
                        onClick = {
                            onSelected(language)
                            expanded = false
                        },
                    )
                }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LanguageSelectionDialogMandatoryPreview() {
    MyApplicationTheme {
        LanguageSelectionDialog(
            onConfirm = { _, _ -> },
            onDismiss = null,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LanguageSelectionDialogDismissablePreview() {
    MyApplicationTheme {
        LanguageSelectionDialog(
            initialNativeLanguage = Language.ENGLISH,
            initialTargetLanguage = Language.RUSSIAN,
            onConfirm = { _, _ -> },
            onDismiss = {},
        )
    }
}
