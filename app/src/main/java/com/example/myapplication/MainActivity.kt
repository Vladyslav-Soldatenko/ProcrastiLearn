package com.example.myapplication

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.myapplication.service.OverlayAccessibilityService
import com.example.myapplication.ui.AppsViewModel
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.ui.views.MainScreen
import com.example.myapplication.utils.AccessibilityUtils
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                // Track both permissions
                var isAccessibilityEnabled by remember { mutableStateOf(false) }
                var hasOverlayPermission by remember { mutableStateOf(false) }

                // Check permissions on lifecycle events
                DisposableEffect(Unit) {
                    val observer =
                        LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                isAccessibilityEnabled =
                                    AccessibilityUtils.isAccessibilityServiceEnabled(
                                        this@MainActivity,
                                        OverlayAccessibilityService::class.java,
                                    )
                                hasOverlayPermission = Settings.canDrawOverlays(this@MainActivity)
                            }
                        }
                    lifecycle.addObserver(observer)

                    // Initial check
                    isAccessibilityEnabled =
                        AccessibilityUtils.isAccessibilityServiceEnabled(
                            this@MainActivity,
                            OverlayAccessibilityService::class.java,
                        )
                    hasOverlayPermission = Settings.canDrawOverlays(this@MainActivity)

                    onDispose {
                        lifecycle.removeObserver(observer)
                    }
                }

                when {
                    // First check accessibility service
                    !isAccessibilityEnabled -> {
                        AlertDialog(
                            onDismissRequest = { /* Don't allow dismiss */ },
                            title = { Text("Enable Accessibility Service") },
                            text = {
                                Text(
                                    "To use this app, you need to enable the Vocabulary Overlay accessibility service in settings.",
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                    },
                                ) {
                                    Text("Open Settings")
                                }
                            },
                        )
                    }

                    // Then check overlay permission
                    !hasOverlayPermission -> {
                        AlertDialog(
                            onDismissRequest = { /* Don't allow dismiss */ },
                            title = { Text("Enable Overlay Permission") },
                            text = {
                                Text("This app needs permission to display overlays on top of other apps.")
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        startActivity(
                                            Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:$packageName"),
                                            ),
                                        )
                                    },
                                ) {
                                    Text("Open Settings")
                                }
                            },
                        )
                    }

                    // Both permissions granted - show main content
                    else -> {
                        val vm: AppsViewModel = hiltViewModel()
                        val state by vm.state.collectAsState()

                        when {
                            state.isLoading -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                            state.error != null -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("Error: ${state.error}")
                                }
                            }
                            else -> {
                                MainScreen()
                            }
                        }
                    }
                }
            }
        }
    }
}
