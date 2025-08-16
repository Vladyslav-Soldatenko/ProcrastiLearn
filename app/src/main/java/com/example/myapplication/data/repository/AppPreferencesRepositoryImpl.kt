package com.example.myapplication.data.repository

import com.example.myapplication.domain.repository.AppPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppPreferencesRepositoryImpl @Inject constructor() : AppPreferencesRepository {
    private val _blockedApps = MutableStateFlow(
        setOf(
            "com.android.vending", // Play Store for testing
            // "com.zhiliaoapp.musically" // TikTok
        )
    )

    override fun getBlockedApps(): Flow<Set<String>> {
        return _blockedApps.asStateFlow()
    }

    override suspend fun addBlockedApp(packageName: String) {
        _blockedApps.value = _blockedApps.value + packageName
    }

    override suspend fun removeBlockedApp(packageName: String) {
        _blockedApps.value = _blockedApps.value - packageName
    }

    override suspend fun setBlockedApps(packageNames: Set<String>) {
        _blockedApps.value = packageNames
    }

    override suspend fun isAppBlocked(packageName: String): Boolean {
        return _blockedApps.first().contains(packageName)
    }
}