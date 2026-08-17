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
import com.procrastilearn.app.domain.model.NewCardOrder
import com.procrastilearn.app.ui.theme.MyApplicationTheme

@Composable
fun NewCardOrderDialog(
    currentOrder: NewCardOrder,
    onOrderSelect: (NewCardOrder) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_new_card_order_title)) },
        text = {
            Column {
                NewCardOrder.entries.forEach { order ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOrderSelect(order) }
                                .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = order == currentOrder,
                            onClick = { onOrderSelect(order) },
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text =
                                    when (order) {
                                        NewCardOrder.SEQUENTIAL ->
                                            stringResource(R.string.settings_new_card_order_sequential)
                                        NewCardOrder.RANDOM ->
                                            stringResource(R.string.settings_new_card_order_random)
                                    },
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text =
                                    when (order) {
                                        NewCardOrder.SEQUENTIAL ->
                                            stringResource(R.string.settings_new_card_order_sequential_desc)
                                        NewCardOrder.RANDOM ->
                                            stringResource(R.string.settings_new_card_order_random_desc)
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
private fun NewCardOrderDialogPreview() {
    MyApplicationTheme {
        NewCardOrderDialog(
            currentOrder = NewCardOrder.SEQUENTIAL,
            onOrderSelect = {},
            onDismiss = {},
        )
    }
}
