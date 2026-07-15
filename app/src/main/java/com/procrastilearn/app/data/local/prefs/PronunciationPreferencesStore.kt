package com.procrastilearn.app.data.local.prefs

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PronunciationPreferencesStore
    @Inject
    constructor(
        studyPreferences: StudyPreferencesDataStore,
    ) {
        private val ds = studyPreferences.ds

        private object K {
            val PRONUNCIATION_ENABLED = booleanPreferencesKey("pronunciation_enabled")
        }

        fun readEnabled(): Flow<Boolean> = ds.data.map { p -> p[K.PRONUNCIATION_ENABLED] ?: false }

        suspend fun setEnabled(enabled: Boolean) {
            ds.edit { it[K.PRONUNCIATION_ENABLED] = enabled }
        }
    }
