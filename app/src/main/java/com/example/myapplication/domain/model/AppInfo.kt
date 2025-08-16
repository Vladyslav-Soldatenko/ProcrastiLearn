package com.example.myapplication.domain.model

data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String, // fully-qualified
)
