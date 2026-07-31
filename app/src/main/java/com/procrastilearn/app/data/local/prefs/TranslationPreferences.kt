package com.procrastilearn.app.data.local.prefs

import javax.inject.Inject

class TranslationPreferences
    @Inject
    constructor(
        val openAiStore: OpenAiPreferencesStore,
        val languagePreferencesStore: LanguagePreferencesStore,
    )
