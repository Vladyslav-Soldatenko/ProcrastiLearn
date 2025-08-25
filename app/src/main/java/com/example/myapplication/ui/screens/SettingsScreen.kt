package com.example.myapplication.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.myapplication.R
import com.example.myapplication.utils.isPermissionsGranted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
    var a11yEnabled by remember { mutableStateOf(isPermissionsGranted(ctx)) }

    // Refresh the toggles after returning from system settings
    DisposableEffect(lifecycleOwner) {
        val obs =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    overlayGranted = Settings.canDrawOverlays(ctx)
                    a11yEnabled = isPermissionsGranted(ctx)
                }
            }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
            contentAlignment = Alignment.TopStart,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
            ) {
                // Overlay permission row
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_overlay_headline)) },
                    supportingContent = {
                        Text(
                            stringResource(R.string.settings_overlay_support),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    trailingContent = {
                        // Read-only; user must grant in system UI
                        Checkbox(checked = overlayGranted, onCheckedChange = null)
                    },
                    modifier =
                        Modifier
                            .clickable {
                                val intent =
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${ctx.packageName}"),
                                    )
                                ctx.startActivity(intent)
                            }.padding(horizontal = 8.dp, vertical = 4.dp)
                            .fillMaxWidth(),
                )

                Spacer(Modifier.height(4.dp))

                // Accessibility service row (for “reconsider after Not now”)
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_a11y_headline)) },
                    supportingContent = {
                        Text(
                            stringResource(R.string.settings_a11y_support),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    trailingContent = {
                        Checkbox(checked = a11yEnabled, onCheckedChange = null)
                    },
                    modifier =
                        Modifier
                            .clickable {
                                ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            }.padding(horizontal = 8.dp, vertical = 4.dp)
                            .fillMaxWidth(),
                )
            }
        }
    }
}
