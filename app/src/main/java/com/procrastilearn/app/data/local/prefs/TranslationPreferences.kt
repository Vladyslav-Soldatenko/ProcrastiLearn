package com.procrastilearn.app.data.local.prefs

import javax.inject.Inject

// Bundles the OpenAI and language-pair settings that are always read (and written)
// together to resolve or configure AI translation, shared by every call site that
// needs both: SettingsViewModel, AddWordViewModel, and GenerateAiTranslationUseCase.
class TranslationPreferences
    @Inject
    constructor(
        val openAiStore: OpenAiPreferencesStore,
        val languagePreferencesStore: LanguagePreferencesStore,
    )
