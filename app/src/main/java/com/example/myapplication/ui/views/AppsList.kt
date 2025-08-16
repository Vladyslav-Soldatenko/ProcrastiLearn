package com.example.myapplication.ui.views

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.myapplication.domain.model.AppInfo

@Composable
fun AppsList(
    apps: List<AppInfo>,
    selectedKeys: Set<String>,
    onToggle: (AppInfo) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(apps, key = { it.packageName + it.activityName }) { app ->
            val key = "${app.packageName}/${app.activityName}"
            AppRow(
                app = app,
                checked = selectedKeys.contains(key),
                onCheckedChange = { onToggle(app) }
            )
        }
    }
}

@Composable
private fun AppRow(
    app: AppInfo,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = app.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    }
}
