package com.procrastilearn.app.ui.screens.settings.components

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri

fun openOverlaySettings(context: Context) {
    val intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            "package:${context.packageName}".toUri(),
        )
    context.startActivity(intent)
}

fun openAccessibilitySettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
}
