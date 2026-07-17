package com.procrastilearn.app.appfunctions

import androidx.appfunctions.AppFunctionAppUnknownException
import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.service.AppFunction
import com.procrastilearn.app.data.local.prefs.OpenAiPreferencesStore
import com.procrastilearn.app.data.translation.AiTranslationProvider
import com.procrastilearn.app.data.translation.AiTranslationRequest
import com.procrastilearn.app.domain.model.AiTranslationDirection
import com.procrastilearn.app.domain.usecase.AddVocabularyItemUseCase
import com.procrastilearn.app.domain.usecase.GetVocabularyItemByWordUseCase
import com.procrastilearn.app.domain.usecase.OverrideVocabularyItemUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

class VocabularyFunctions
    @Inject
    constructor(
        private val addVocabularyItemUseCase: AddVocabularyItemUseCase,
        private val getVocabularyItemByWordUseCase: GetVocabularyItemByWordUseCase,
        private val overrideVocabularyItemUseCase: OverrideVocabularyItemUseCase,
        private val openAiStore: OpenAiPreferencesStore,
        private val aiTranslationProvider: AiTranslationProvider,
        private val ioDispatcher: CoroutineDispatcher,
    ) {
        /**
         * Add a word to the vocabulary learning list with an AI-generated or manual translation.
         * If the word already exists, its translation is updated and learning progress is reset.
         *
         * @param appFunctionContext The execution context.
         * @param word The word or phrase to add (e.g. "Computer", "Serendipity").
         *   Must not be blank.
         * @param translation Optional manual translation used when AI is not configured.
         *   If null and AI translation is unavailable, the call fails.
         * @return A status message describing the outcome ("Added …" or "Updated …").
         * @throws AppFunctionInvalidArgumentException if the word is blank or no translation
         *   can be resolved. Configure an OpenAI API key in app settings or supply a manual translation.
         * @throws AppFunctionAppUnknownException on database or network failures.
         */
        @Suppress("UnusedParameter")
        @AppFunction(isDescribedByKDoc = true)
        suspend fun addWord(
            appFunctionContext: AppFunctionContext,
            word: String,
            translation: String? = null,
        ): String =
            withContext(ioDispatcher) {
                if (word.isBlank()) {
                    throw AppFunctionInvalidArgumentException("Word must not be blank.")
                }

                val trimmedWord = word.trim()
                val finalTranslation = resolveTranslation(trimmedWord, translation)

                val existingItem =
                    runCatching { getVocabularyItemByWordUseCase(trimmedWord) }
                        .getOrElse { e ->
                            throw AppFunctionAppUnknownException(
                                e.message ?: "Failed to query vocabulary.",
                            )
                        }

                if (existingItem != null) {
                    overrideVocabularyItemUseCase(existingItem, trimmedWord, finalTranslation)
                        .getOrElse { e ->
                            throw AppFunctionAppUnknownException(
                                e.message ?: "Failed to update word.",
                            )
                        }
                    "Updated \"$trimmedWord\" → \"$finalTranslation\" and reset its learning progress."
                } else {
                    addVocabularyItemUseCase(trimmedWord, finalTranslation)
                        .getOrElse { e ->
                            throw AppFunctionAppUnknownException(
                                e.message ?: "Failed to add word.",
                            )
                        }
                    "Added \"$trimmedWord\" → \"$finalTranslation\" to your learning list."
                }
            }

        private suspend fun resolveTranslation(
            word: String,
            manualTranslation: String?,
        ): String {
            val apiKey = openAiStore.readOpenAiApiKey().first()
            val useAi = openAiStore.readUseAiForTranslation().first()
            val direction = openAiStore.readAiTranslationDirection().first()

            if (!apiKey.isNullOrBlank() && useAi) {
                val systemPrompt =
                    when (direction) {
                        AiTranslationDirection.TARGET_TO_NATIVE -> openAiStore.readOpenAiPrompt().first()
                        AiTranslationDirection.NATIVE_TO_TARGET -> openAiStore.readOpenAiReversePrompt().first()
                    }
                val userPrompt =
                    """
                    HEADWORD: "$word"

                    Produce ONLY the entry for this headword, in the exact frame and rules above. No extra text.
                    """.trimIndent()

                val aiTranslation =
                    runCatching {
                        aiTranslationProvider.translate(
                            AiTranslationRequest(
                                apiKey = apiKey,
                                systemPrompt = systemPrompt,
                                userPrompt = userPrompt,
                            ),
                        )
                    }.getOrNull()?.trim()

                if (!aiTranslation.isNullOrBlank()) return aiTranslation
            }

            val fallback = manualTranslation?.trim()
            if (!fallback.isNullOrBlank()) return fallback

            throw AppFunctionInvalidArgumentException(
                "No translation available. Configure an OpenAI API key in settings or provide a manual 'translation' argument.",
            )
        }
    }
