package com.example.myapplication.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.myapplication.R

@Composable
fun ProminentA11yDisclosureScreen(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onPrivacyPolicy: () -> Unit,
) {
    val scroll = rememberScrollState()
    var checked by rememberSaveable { mutableStateOf(false) }

    Scaffold { inner ->
        Column(
            modifier =
                Modifier
                    .padding(inner)
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(scroll),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(stringResource(R.string.a11y_disclosure_title), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.a11y_disclosure_description))
                Spacer(Modifier.height(12.dp))

                Text(
                    stringResource(R.string.a11y_disclosure_section_access),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(stringResource(R.string.a11y_disclosure_access_details))
                Spacer(Modifier.height(12.dp))

                Text(
                    stringResource(R.string.a11y_disclosure_section_usage),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(stringResource(R.string.a11y_disclosure_usage_details))
                Spacer(Modifier.height(16.dp))
            }
            Column {
                RowWithCheckbox(
                    checked = checked,
                    onCheckedChange = { checked = it },
                    text = stringResource(R.string.a11y_disclosure_checkbox),
                )
                Spacer(Modifier.height(12.dp))
                Column {
                    Button(
                        enabled = checked,
                        onClick = onAccept,
                        modifier = Modifier.fillMaxSize(fraction = 1f),
                    ) { Text(stringResource(R.string.a11y_disclosure_accept)) }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onDecline, modifier = Modifier.fillMaxSize(fraction = 1f)) {
                        Text(stringResource(R.string.a11y_disclosure_decline))
                    }
                    TextButton(onClick = onPrivacyPolicy, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text(stringResource(R.string.a11y_disclosure_privacy))
                    }
                }
            }
        }
    }
}

@Composable
private fun RowWithCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    text: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(Modifier.height(0.dp)) // spacing handled in parent
        Text(text, modifier = Modifier.padding(start = 8.dp))
    }
}


@Preview
@Composable
private fun ProminentA11yDisclosureScreenPreview(){
    ProminentA11yDisclosureScreen({},{},{})
}
