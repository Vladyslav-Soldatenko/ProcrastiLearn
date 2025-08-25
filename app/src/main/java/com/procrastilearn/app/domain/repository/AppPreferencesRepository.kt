package com.procrastilearn.app.domain.repository

import kotlinx.coroutines.flow.Flow

interface AppPreferencesRepository {
    fun getBlockedApps(): Flow<Set<String>>

    suspend fun addBlockedApp(packageName: String)

    suspend fun removeBlockedApp(packageName: String)

    suspend fun setBlockedApps(packageNames: Set<String>)

    suspend fun isAppBlocked(packageName: String): Boolean

    suspend fun toggleApp(packageName: String)
}
