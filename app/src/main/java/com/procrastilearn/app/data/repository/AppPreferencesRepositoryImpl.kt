package com.procrastilearn.app.data.repository

import com.procrastilearn.app.data.local.prefs.PreferencesDataStore
import com.procrastilearn.app.domain.repository.AppPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppPreferencesRepositoryImpl
    @Inject
    constructor(
        private val preferencesDataStore: PreferencesDataStore,
    ) : AppPreferencesRepository {
        override fun getBlockedApps(): Flow<Set<String>> = preferencesDataStore.blockedApps

        override fun isProcrastilearnEnabled(): Flow<Boolean> =
            preferencesDataStore.isProcrastilearnEnabled

        override suspend fun addBlockedApp(packageName: String) {
            preferencesDataStore.addBlockedApp(packageName)
        }

        override suspend fun removeBlockedApp(packageName: String) {
            preferencesDataStore.removeBlockedApp(packageName)
        }

        override suspend fun setBlockedApps(packageNames: Set<String>) {
            preferencesDataStore.setBlockedApps(packageNames)
        }

        override suspend fun isAppBlocked(packageName: String): Boolean =
            preferencesDataStore.blockedApps.first().contains(packageName)

        override suspend fun toggleApp(packageName: String) = preferencesDataStore.toggleApp(packageName)

        override suspend fun setProcrastilearnEnabled(enabled: Boolean) {
            preferencesDataStore.setProcrastilearnEnabled(enabled)
        }
    }
