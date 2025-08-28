package com.procrastilearn.app.ui.screens.settings.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

fun openOverlaySettings(context: Context) {
    val intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )
    context.startActivity(intent)
}

fun openAccessibilitySettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
}
