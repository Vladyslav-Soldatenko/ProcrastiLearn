package com.example.myapplication.data.repository

import android.util.Log
import com.example.myapplication.data.local.PreferencesDataStore
import com.example.myapplication.domain.repository.AppPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppPreferencesRepositoryImpl @Inject constructor(
    private val preferencesDataStore: PreferencesDataStore
) : AppPreferencesRepository {

    override fun getBlockedApps(): Flow<Set<String>> {
        return preferencesDataStore.blockedApps
    }

    override suspend fun addBlockedApp(packageName: String) {
        preferencesDataStore.addBlockedApp(packageName)
    }

    override suspend fun removeBlockedApp(packageName: String) {
        preferencesDataStore.removeBlockedApp(packageName)
    }

    override suspend fun setBlockedApps(packageNames: Set<String>) {
        preferencesDataStore.setBlockedApps(packageNames)
    }

    override suspend fun isAppBlocked(packageName: String): Boolean {
        return preferencesDataStore.blockedApps.first().contains(packageName)
    }

    override suspend fun toggleApp(packageName: String) {
        return preferencesDataStore.toggleApp(packageName)
    }
}