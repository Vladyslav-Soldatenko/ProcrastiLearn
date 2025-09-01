package com.procrastilearn.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.procrastilearn.app.ui.components.OverlayPermissionDialog
import com.procrastilearn.app.ui.components.ProminentA11yDisclosureScreen
import com.procrastilearn.app.ui.theme.MyApplicationTheme
import com.procrastilearn.app.ui.views.MainScreen
import com.procrastilearn.app.utils.isPermissionsGranted
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.dataStore by preferencesDataStore("app_prefs")
private val KEY_ACCESSIBILITY_SKIPPED = booleanPreferencesKey("accessibility_skipped")
private val KEY_OVERLAY_SKIPPED = booleanPreferencesKey("overlay_skipped")

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Suppress("LongMethod", "CyclomaticComplexMethod")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val ctx = LocalContext.current

                // Load preferences state
                var preferencesLoaded by remember { mutableStateOf(false) }
                var hasSkippedAccessibility by remember { mutableStateOf(false) }
                var hasSkippedOverlay by remember { mutableStateOf(false) }

                // Track dynamic permission state
                var isAccessibilityEnabled by remember { mutableStateOf(isPermissionsGranted(ctx)) }
                var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }

                // Load preferences once
                LaunchedEffect(Unit) {
                    val prefs = ctx.dataStore.data.first()
                    hasSkippedAccessibility = prefs[KEY_ACCESSIBILITY_SKIPPED] ?: false
                    hasSkippedOverlay = prefs[KEY_OVERLAY_SKIPPED] ?: false
                    preferencesLoaded = true
                }

                // Re-check when returning from Settings
                DisposableEffect(Unit) {
                    val observer =
                        LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                val newAccessibilityState = isPermissionsGranted(ctx)
                                val newOverlayState = Settings.canDrawOverlays(ctx)

                                // If user enabled the permission, clear the skip preference
                                if (newAccessibilityState && !isAccessibilityEnabled) {
                                    lifecycleScope.launch {
                                        ctx.dataStore.edit { it[KEY_ACCESSIBILITY_SKIPPED] = false }
                                    }
                                    hasSkippedAccessibility = false
                                }
                                if (newOverlayState && !hasOverlayPermission) {
                                    lifecycleScope.launch {
                                        ctx.dataStore.edit { it[KEY_OVERLAY_SKIPPED] = false }
                                    }
                                    hasSkippedOverlay = false
                                }

                                isAccessibilityEnabled = newAccessibilityState
                                hasOverlayPermission = newOverlayState
                            }
                        }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }

                if (!preferencesLoaded) {
                    // Show loading indicator while preferences are being loaded
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    when {
                        !isAccessibilityEnabled && !hasSkippedAccessibility -> {
                            ProminentA11yDisclosureScreen(
                                onAccept = {
                                    // Take user to Accessibility Settings
                                    openAccessibilitySettings()
                                },
                                onDecline = {
                                    // Save skip preference and proceed with limited mode
                                    lifecycleScope.launch {
                                        ctx.dataStore.edit { it[KEY_ACCESSIBILITY_SKIPPED] = true }
                                    }
                                    hasSkippedAccessibility = true
                                },
                                onPrivacyPolicy = {
                                     startActivity(Intent(Intent.ACTION_VIEW,
                                         "https://gist.github.com/Vladyslav-Soldatenko/adb5953ce000b9e8515d3dcd87773aef".toUri()))
                                },
                            )
                        }

                        !hasOverlayPermission && !hasSkippedOverlay -> {
                            OverlayPermissionDialog(
                                onOpenSettings = {
                                    startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:$packageName"),
                                        ),
                                    )
                                },
                                onSkip = {
                                    // Save skip preference and let users continue
                                    lifecycleScope.launch {
                                        ctx.dataStore.edit { it[KEY_OVERLAY_SKIPPED] = true }
                                    }
                                    hasSkippedOverlay = true
                                },
                            )
                        }

                        else -> {
                            // Your normal app content
                            MainScreen()
                        }
                    }
                }
            }
        }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}
