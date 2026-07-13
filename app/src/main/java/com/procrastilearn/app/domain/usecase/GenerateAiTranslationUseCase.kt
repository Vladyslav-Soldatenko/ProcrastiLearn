package com.procrastilearn.app.domain.usecase

import com.procrastilearn.app.data.local.prefs.OpenAiPreferencesStore
import com.procrastilearn.app.data.translation.AiTranslationProvider
import com.procrastilearn.app.data.translation.AiTranslationRequest
import com.procrastilearn.app.domain.model.AiTranslationDirection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GenerateAiTranslationUseCase
    @Inject
    constructor(
        private val aiTranslationProvider: AiTranslationProvider,
        private val prefs: OpenAiPreferencesStore,
        private val ioDispatcher: CoroutineDispatcher,
    ) {
        suspend operator fun invoke(
            word: String,
            direction: AiTranslationDirection,
        ): String =
            withContext(ioDispatcher) {
                val apiKey: String = prefs.readOpenAiApiKey().first().orEmpty()
                require(apiKey.isNotBlank()) { "Missing OpenAI API key" }

                val systemPrompt =
                    when (direction) {
                        AiTranslationDirection.EN_TO_RU -> prefs.readOpenAiPrompt().first()
                        AiTranslationDirection.RU_TO_EN -> prefs.readOpenAiReversePrompt().first()
                    }
                val userPrompt =
                    """
                    HEADWORD: "$word"

                    Produce ONLY the entry for this headword, in the exact frame and rules above. No extra text.
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
