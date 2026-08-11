package com.procrastilearn.app.ui.screens

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.procrastilearn.app.R

@Composable
internal fun WordListSelectionMenu(
    expanded: Boolean,
    allDisplayedSelected: Boolean,
    canSelectAll: Boolean,
    canEnableBidirectional: Boolean,
    canDisableBidirectional: Boolean,
    canDelete: Boolean,
    onDismissRequest: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onEnableBidirectional: () -> Unit,
    onDisableBidirectional: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
    ) {
        DropdownMenuItem(
            text = {
                Text(
                    text =
                        stringResource(
                            if (allDisplayedSelected) {
                                R.string.action_deselect_all
                            } else {
                                R.string.action_select_all
                            },
                        ),
                )
            },
            enabled = canSelectAll,
            onClick = onToggleSelectAll,
        )
        DropdownMenuItem(
            text = { Text(text = stringResource(R.string.word_list_bulk_bidirectional_enable)) },
            enabled = canEnableBidirectional,
            onClick = onEnableBidirectional,
        )
        DropdownMenuItem(
            text = { Text(text = stringResource(R.string.word_list_bulk_bidirectional_disable)) },
            enabled = canDisableBidirectional,
            onClick = onDisableBidirectional,
        )
        DropdownMenuItem(
            text = { Text(text = stringResource(R.string.action_delete)) },
            enabled = canDelete,
            onClick = onDelete,
        )
    }
}

@Composable
internal fun ForwardOnlyConfirmDialog(
    count: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.word_list_bulk_forward_only_confirm_title))
        },
        text = {
            Text(
                text = pluralStringResource(R.plurals.word_list_bulk_forward_only_confirm_message, count, count),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.action_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}
