package com.procrastilearn.app.ui.screens.settings.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.procrastilearn.app.R
import com.procrastilearn.app.domain.parser.VocabularyImportOption
import com.procrastilearn.app.ui.theme.MyApplicationTheme

@Composable
fun ImportSettingsItem(
    options: List<VocabularyImportOption>,
    onOptionSelected: (VocabularyImportOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (options.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_import_row)) },
            supportingContent = {
                Text(
                    text = stringResource(R.string.settings_import_row_desc),
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            trailingContent = {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .clickable { expanded = !expanded },
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = stringResource(option.titleResId),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            option.descriptionResId?.let {
                                Text(
                                    text = stringResource(it),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        onOptionSelected(option)
                    },
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ImportSettingsItemPreview() {
    MyApplicationTheme {
        ImportSettingsItem(
            options =
                listOf(
                    VocabularyImportOption(
                        id = "apkg",
                        titleResId = R.string.settings_import_option_anki_apkg,
                        descriptionResId = R.string.settings_import_option_anki_apkg_desc,
                        mimeTypes = listOf("application/apkg"),
                        extensions = setOf("apkg"),
                    ),
                ),
            onOptionSelected = {},
        )
    }
}
