package com.procrastilearn.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.models.ChatModel
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.procrastilearn.app.data.local.prefs.DayCountersStore
import com.procrastilearn.app.domain.usecase.AddVocabularyItemUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class AddWordViewModel @Inject
    constructor(
        private val addVocabularyItemUseCase: AddVocabularyItemUseCase,
        private val prefs: DayCountersStore,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(AddWordUiState())
        val uiState: StateFlow<AddWordUiState> = _uiState.asStateFlow()

        init {
            // Observe OpenAI key and toggle from preferences
            viewModelScope.launch {
                combine(
                    prefs.readOpenAiApiKey(),
                    prefs.readUseAiForTranslation(),
                ) { key: String?, useAi: Boolean ->
                    Pair(!key.isNullOrBlank(), useAi)
                }.collectLatest { (hasKey, useAi) ->
                    _uiState.value =
                        _uiState.value.copy(
                            openAiAvailable = hasKey,
                            useAiForTranslation = useAi,
                        )
                }
            }
        }

        fun onWordChange(word: String) {
            _uiState.value =
                _uiState.value.copy(
                    word = word,
                    wordError = null,
                )
        }

        fun onTranslationChange(translation: String) {
            _uiState.value =
                _uiState.value.copy(
                    translation = translation,
                    translationError = null,
                )
        }

        fun onAddClick() {
            val currentState = _uiState.value
            if (currentState.word.isBlank()) {
                _uiState.value = _uiState.value.copy(wordError = "Please enter a word")
                return
            }

            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, successMessage = null)

                val aiTranslation: String? =
                    if (currentState.openAiAvailable && currentState.useAiForTranslation) {
                        runCatching { requestAiTranslation(currentState.word) }
                            .getOrNull()
                    } else {
                        null
                    }

                val finalTranslation =
                    when {
                        !aiTranslation.isNullOrBlank() -> {
                            _uiState.value = _uiState.value.copy(translation = aiTranslation)
                            aiTranslation
                        }
                        else -> {
                            currentState.translation
                        }
                    }

                if (finalTranslation.isBlank()) {
                    _uiState.value =
                        _uiState.value.copy(
                            isLoading = false,
                            translationError = "Please enter a translation",
                        )
                    return@launch
                }

                addVocabularyItemUseCase(
                    word = currentState.word.trim(),
                    translation = finalTranslation.trim(),
                ).fold(
                    onSuccess = {
                        // Preserve prefs-driven flags and clear only transient fields
                        _uiState.value =
                            _uiState.value.copy(
                                isLoading = false,
                                errorMessage = null,
                                wordError = null,
                                translationError = null,
                                word = "",
                                translation = "",
                                isSuccess = true,
                                successMessage = "Word added successfully!",
                            )
                    },
                    onFailure = { error ->
                        _uiState.value =
                            _uiState.value.copy(
                                isLoading = false,
                                errorMessage = error.message ?: "Failed to add word",
                            )
                    },
                )
            }
        }

        fun resetSuccess() {
            // Do not reset prefs-driven flags; only clear success UI state
            _uiState.value =
                _uiState.value.copy(
                    isSuccess = false,
                    successMessage = null,
                    errorMessage = null,
                )
        }

        fun onUseAiToggle(checked: Boolean) {
            viewModelScope.launch { prefs.setUseAiForTranslation(checked) }
            _uiState.value = _uiState.value.copy(useAiForTranslation = checked)
        }


        private suspend fun requestAiTranslation(word: String): String =
            withContext(Dispatchers.IO) {
                val apiKey: String = prefs.readOpenAiApiKey().first().orEmpty()
                require(apiKey.isNotBlank()) { "Missing OpenAI API key" }

                val client: OpenAIClient =
                    OpenAIOkHttpClient
                        .builder()
                        .apiKey(apiKey)
                        .build()

                val systemPrompt =
                    """
            ROLE: You are an EN→RU lexicographer for Russian-speaking learners of English.

            GOAL: For a single English headword, produce a compact, accurate entry in EXACTLY this format:
            Transcription: /.../

            Russian translation: <comma-separated common synonyms; group senses with semicolons>

            Explanation in English:
              <1–5 concise sentences, neutral dictionary style, no markdown except italics where needed. Should mention all common meanings of the word>

            Examples:
            1. <short sentence with the headword, sense A>
            2. <short sentence with the headword, sense B>
            [Optional more examples only if they covers a distinct sense]

            HARD RULES:
            - Use exact section headings as shown (capitalization, punctuation).
            - IPA only between slashes; no brackets or respelling (e.g., /bɪˈnaɪn/).
            - Russian: no transliteration; give common synonyms, comma-separated; separate sense groups with semicolons.
            - Keep the explanation 1–5 sentences; informative, not verbose.
            - Examples: 2–5 total, different senses when possible.
            - No extra commentary, notes, links, or code fences.

            EXAMPLES (follow exactly):

            EXAMPLE 1
            Transcription: /snaʊt/

            Russian translation: рыло, морда; пятачок (у свиньи); нос (разг.)

            Explanation in English:
              *Snout* is an animal’s protruding nose or muzzle; informally, it can refer to a person’s nose; it may also denote a projecting nozzle or spout.

            Examples:
            1. The pig rooted in the soil with its snout.
            2. The kettle’s snout was dented and leaked.

            EXAMPLE 2
            Transcription: /bɪˈnaɪn/

            Russian translation: доброкачественный; благоприятный; доброжелательный, мягкий

            Explanation in English:
              *Benign* means gentle or not harmful; in medicine, it describes a noncancerous condition or tumor; it can also mean mild or favorable (e.g., climate).

            Examples:
            1. The biopsy confirmed a benign tumor.
            2. They enjoyed the region’s benign climate.
            """.trimIndent()
                val userPrompt =
                    """
            HEADWORD: "$word"

            Produce ONLY the entry for this headword, in the exact frame and rules above. No extra text.
            """.trimIndent()

                val params =
                    ChatCompletionCreateParams
                        .builder()
                        .model(ChatModel.CHATGPT_4O_LATEST)
                        .addSystemMessage(systemPrompt)
                        .addUserMessage(userPrompt)
                        .temperature(0.5)
                        .maxCompletionTokens(1000)
                        .build()

                val completion = client.chat().completions().create(params)
                val text =
                    completion
                        .choices()
                        .firstOrNull()
                        ?.message()
                        ?.content()
                        ?.orElse("")
                        ?.trim()
                        .orEmpty()

                if (text.isBlank()) error("OpenAI returned an empty response")
                text
            }
    }

data class AddWordUiState(
    val word: String = "",
    val translation: String = "",
    val wordError: String? = null,
    val translationError: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false,
    val successMessage: String? = null,
    val openAiAvailable: Boolean = false,
    val useAiForTranslation: Boolean = false,
)
