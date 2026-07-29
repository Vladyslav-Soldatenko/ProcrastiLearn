package com.procrastilearn.app.domain.usecase

import com.procrastilearn.app.data.local.prefs.TranslationPreferences
import com.procrastilearn.app.data.local.prefs.resolveOpenAiPrompt
import com.procrastilearn.app.data.translation.AiTranslationProvider
import com.procrastilearn.app.data.translation.AiTranslationRequest
import com.procrastilearn.app.domain.model.AiTranslationDirection
import com.procrastilearn.app.domain.model.LanguagePair
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GenerateAiTranslationUseCase
    @Inject
    constructor(
        private val aiTranslationProvider: AiTranslationProvider,
        private val translationPreferences: TranslationPreferences,
        private val ioDispatcher: CoroutineDispatcher,
    ) {
        suspend operator fun invoke(
            word: String,
            direction: AiTranslationDirection,
        ): String =
            withContext(ioDispatcher) {
                val openAiStore = translationPreferences.openAiStore
                val apiKey: String = openAiStore.readOpenAiApiKey().first().orEmpty()
                require(apiKey.isNotBlank()) { "Missing OpenAI API key" }

                val languagePair: LanguagePair =
                    requireNotNull(translationPreferences.languagePreferencesStore.readLanguagePair().first()) {
                        "Missing language pair"
                    }

                val template =
                    when (direction) {
                        AiTranslationDirection.TARGET_TO_NATIVE -> openAiStore.readOpenAiPrompt().first()
                        AiTranslationDirection.NATIVE_TO_TARGET -> openAiStore.readOpenAiReversePrompt().first()
                    }
                val systemPrompt = resolveOpenAiPrompt(template, languagePair.native, languagePair.target)

                val languageReminder =
                    when (direction) {
                        AiTranslationDirection.TARGET_TO_NATIVE ->
                            "The headword above is in ${languagePair.target.englishName}. " +
                                "Write headings, explanations, and usage notes in ${languagePair.native.englishName}, " +
                                "and write every example sentence in ${languagePair.target.englishName}."
                        AiTranslationDirection.NATIVE_TO_TARGET ->
                            "The headword above is in ${languagePair.native.englishName}. " +
                                "Write headings, explanations, and context notes in ${languagePair.native.englishName}, " +
                                "and write every translation candidate and example sentence in ${languagePair.target.englishName}."
                    }
                val userPrompt =
                    """
                    HEADWORD: "$word"

                    Produce ONLY the entry for this headword, in the exact frame and rules above. No extra text.

                    $languageReminder
                    """.trimIndent()

                aiTranslationProvider.translate(
                    AiTranslationRequest(
                        apiKey = apiKey,
                        systemPrompt = systemPrompt,
                        userPrompt = userPrompt,
                    ),
                )
            }
    }
