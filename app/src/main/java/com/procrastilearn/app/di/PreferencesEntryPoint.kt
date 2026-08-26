package com.procrastilearn.app.di

import com.procrastilearn.app.data.local.prefs.DayCountersStore
import com.procrastilearn.app.data.local.prefs.TranslationPreferences
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PreferencesEntryPoint {
    fun dayCountersStore(): DayCountersStore

    fun translationPreferences(): TranslationPreferences
}
