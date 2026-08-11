package com.procrastilearn.app.data.local.prefs

import javax.inject.Inject

data class TranslationPreferences
    @Inject
    constructor(
        val openAiStore: OpenAiPreferencesStore,
        val languagePreferencesStore: LanguagePreferencesStore,
    )
