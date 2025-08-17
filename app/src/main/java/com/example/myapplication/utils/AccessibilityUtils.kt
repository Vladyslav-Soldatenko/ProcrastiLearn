package com.example.myapplication.utils

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.provider.Settings

object AccessibilityUtils {
    fun isAccessibilityServiceEnabled(
        context: Context,
        serviceClass: Class<out AccessibilityService>
    ): Boolean {
        val expectedComponentName = "${context.packageName}/${serviceClass.name}"

        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.split(':').any {
            it.equals(expectedComponentName, ignoreCase = true)
        }
    }
}