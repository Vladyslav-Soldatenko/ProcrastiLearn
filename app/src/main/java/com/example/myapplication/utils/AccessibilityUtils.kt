package com.example.myapplication.utils

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import com.example.myapplication.service.OverlayAccessibilityService

fun isPermissionsGranted(context: Context): Boolean {
    val expected = ComponentName(context, OverlayAccessibilityService::class.java).flattenToString()
    val enabled =
        Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false

    val isA11yOn =
        Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0,
        ) == 1

    if (!isA11yOn) return false
    return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
}
