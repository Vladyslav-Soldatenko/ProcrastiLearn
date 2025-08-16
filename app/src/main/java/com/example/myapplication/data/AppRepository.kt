package com.example.myapplication.data

import com.example.myapplication.model.AppInfo

interface AppRepository {
    suspend fun loadLaunchableApps(): List<AppInfo>
}