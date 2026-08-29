package com.procrastilearn.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
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
import com.procrastilearn.app.R
import com.procrastilearn.app.domain.model.SearchScope
import com.procrastilearn.app.ui.theme.MyApplicationTheme

@Composable
internal fun WordListSearchScopeDialog(
    currentScope: SearchScope,
    onApply: (SearchScope) -> Unit,
    onDismiss: () -> Unit,
) {
    var draftScope by remember { mutableStateOf(currentScope) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.word_list_search_scope_title)) },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Checkbox(
                        checked = draftScope.matchWord,
                        onCheckedChange = { checked -> draftScope = draftScope.copy(matchWord = checked) },
                        modifier = Modifier.testTag("word_list_search_scope_word_checkbox"),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.word_list_search_scope_option_word))
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Checkbox(
                        checked = draftScope.matchTranslation,
                        onCheckedChange = { checked -> draftScope = draftScope.copy(matchTranslation = checked) },
                        modifier = Modifier.testTag("word_list_search_scope_translation_checkbox"),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.word_list_search_scope_option_translation))
                }
                if (!draftScope.isValid) {
                    Text(
                        text = stringResource(R.string.word_list_search_scope_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("word_list_search_scope_error_text"),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onApply(draftScope) },
                enabled = draftScope.isValid,
            ) {
                Text(stringResource(R.string.action_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun WordListSearchScopeDialogPreview() {
    MyApplicationTheme {
        WordListSearchScopeDialog(currentScope = SearchScope(), onApply = {}, onDismiss = {})
    }
}
