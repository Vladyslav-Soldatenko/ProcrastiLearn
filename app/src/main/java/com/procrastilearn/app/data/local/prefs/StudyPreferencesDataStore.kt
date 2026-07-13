package com.procrastilearn.app.data.local.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StudyPreferencesDataStore
    @Inject
    constructor(
        @ApplicationContext ctx: Context,
    ) {
        val ds: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                produceFile = { ctx.dataStoreFile("study_prefs.preferences_pb") },
            )
    }
