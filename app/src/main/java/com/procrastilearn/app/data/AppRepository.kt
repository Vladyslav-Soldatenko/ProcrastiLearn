package com.procrastilearn.app.data

import com.procrastilearn.app.domain.model.AppInfo

interface AppRepository {
    suspend fun loadLaunchableApps(): List<AppInfo>
}
