package com.procrastilearn.app.data.local.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_preferences",
)

@Singleton
class PreferencesDataStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val dataStore = context.dataStore

        companion object {
            // Keys for stored values - like localStorage keys
            val BLOCKED_APPS_KEY = stringSetPreferencesKey("blocked_apps")
            val PROCRASTILEARN_ENABLED_KEY = booleanPreferencesKey("procrastilearn_enabled")
        }

        // Observe blocked apps
        val blockedApps: Flow<Set<String>> =
            dataStore.data
                .map { preferences ->
                    preferences[BLOCKED_APPS_KEY] ?: emptySet()
                }

        val isProcrastilearnEnabled: Flow<Boolean> =
            dataStore.data
                .map { preferences ->
                    preferences[PROCRASTILEARN_ENABLED_KEY] ?: true
                }

        // Save blocked apps
        suspend fun setBlockedApps(apps: Set<String>) {
            dataStore.edit { preferences ->
                preferences[BLOCKED_APPS_KEY] = apps
            }
        }

        // Add single app
        suspend fun addBlockedApp(packageName: String) {
            dataStore.edit { preferences ->
                val current = preferences[BLOCKED_APPS_KEY] ?: emptySet()
                preferences[BLOCKED_APPS_KEY] = current + packageName
            }
        }

        // Remove single app
        suspend fun removeBlockedApp(packageName: String) {
            dataStore.edit { preferences ->
                val current = preferences[BLOCKED_APPS_KEY] ?: emptySet()
                preferences[BLOCKED_APPS_KEY] = current - packageName
            }
        }

        suspend fun toggleApp(appKey: String) {
            context.dataStore.edit { preferences ->
                val current = preferences[BLOCKED_APPS_KEY] ?: emptySet()
                preferences[BLOCKED_APPS_KEY] =
                    if (appKey in current) {
                        current - appKey
                    } else {
                        current + appKey
                    }
            }
        }

        suspend fun setProcrastilearnEnabled(enabled: Boolean) {
            dataStore.edit { preferences ->
                preferences[PROCRASTILEARN_ENABLED_KEY] = enabled
            }
        }
    }
