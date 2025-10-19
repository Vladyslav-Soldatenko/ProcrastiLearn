package com.procrastilearn.app.testing

import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable

/**
 * Utility helpers for constructing [ResolveInfo] instances with predictable behaviour in tests.
 */
object FakeResolveInfo {
    fun create(
        packageName: String,
        className: String,
        label: String = packageName,
        icon: Drawable = ColorDrawable(),
    ): ResolveInfo =
        object : ResolveInfo() {
            init {
                activityInfo =
                    ActivityInfo().apply {
                        applicationInfo =
                            ApplicationInfo().apply {
                                this.packageName = packageName
                                name = packageName
                            }
                        this.packageName = packageName
                        name = className
                    }
            }

            override fun loadLabel(pm: PackageManager): CharSequence = label

            override fun loadIcon(pm: PackageManager): Drawable = icon
        }
}
