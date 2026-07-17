package com.procrastilearn.app.data.local.prefs

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.procrastilearn.app.domain.model.AiTranslationDirection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenAiPreferencesStore
    @Inject
    constructor(
        studyPreferences: StudyPreferencesDataStore,
    ) {
        private val ds = studyPreferences.ds

        private object K {
            val OPENAI_API_KEY = stringPreferencesKey("openai_api_key")
            val USE_AI_TRANSLATION = booleanPreferencesKey("use_ai_translation")
            val OPENAI_PROMPT = stringPreferencesKey("openai_prompt")
            val OPENAI_REVERSE_PROMPT = stringPreferencesKey("openai_reverse_prompt")
            val AI_TRANSLATION_DIRECTION = stringPreferencesKey("ai_translation_direction")
        }

        fun readOpenAiApiKey(): Flow<String?> = ds.data.map { p -> p[K.OPENAI_API_KEY] }

        suspend fun setOpenAiApiKey(value: String) {
            ds.edit { it[K.OPENAI_API_KEY] = value }
        }

        fun readOpenAiPrompt(): Flow<String> =
            ds.data.map { p ->
                p[K.OPENAI_PROMPT]
                    ?: OpenAiPromptDefaults.translationPrompt
            }

        suspend fun setOpenAiPrompt(value: String) {
            ds.edit { prefs ->
                val trimmed = value.trim()
                if (trimmed.isEmpty() || trimmed == OpenAiPromptDefaults.translationPrompt) {
                    prefs.remove(K.OPENAI_PROMPT)
                } else {
                    prefs[K.OPENAI_PROMPT] = trimmed
                }
            }
        }

        fun readOpenAiReversePrompt(): Flow<String> =
            ds.data.map { p ->
                p[K.OPENAI_REVERSE_PROMPT]
                    ?: OpenAiPromptDefaults.reverseTranslationPrompt
            }

        suspend fun setOpenAiReversePrompt(value: String) {
            ds.edit { prefs ->
                val trimmed = value.trim()
                if (trimmed.isEmpty() || trimmed == OpenAiPromptDefaults.reverseTranslationPrompt) {
                    prefs.remove(K.OPENAI_REVERSE_PROMPT)
                } else {
                    prefs[K.OPENAI_REVERSE_PROMPT] = trimmed
                }
            }
        }

        fun readAiTranslationDirection(): Flow<AiTranslationDirection> =
            ds.data.map { p ->
                val stored = p[K.AI_TRANSLATION_DIRECTION] ?: AiTranslationDirection.TARGET_TO_NATIVE.name
                runCatching { AiTranslationDirection.valueOf(stored) }.getOrDefault(AiTranslationDirection.TARGET_TO_NATIVE)
            }

        suspend fun setAiTranslationDirection(value: AiTranslationDirection) {
            ds.edit { it[K.AI_TRANSLATION_DIRECTION] = value.name }
        }

        fun readUseAiForTranslation(): Flow<Boolean> = ds.data.map { p -> p[K.USE_AI_TRANSLATION] ?: false }

        suspend fun setUseAiForTranslation(value: Boolean) {
            ds.edit { it[K.USE_AI_TRANSLATION] = value }
        }
    }
