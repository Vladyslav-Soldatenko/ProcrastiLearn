package com.procrastilearn.app.domain.model

import android.graphics.drawable.Drawable

data class AppInfo(
    val label: String,
    val packageName: String,
//    val activityName: String,
    val icon: Drawable? = null,
)
