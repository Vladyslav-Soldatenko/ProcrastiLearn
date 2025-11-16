package com.procrastilearn.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.procrastilearn.app.data.local.prefs.DayCountersStore
import com.procrastilearn.app.data.translation.AiTranslationProvider
import com.procrastilearn.app.data.translation.AiTranslationRequest
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.domain.usecase.AddVocabularyItemUseCase
import com.procrastilearn.app.domain.usecase.GetVocabularyItemByWordUseCase
import com.procrastilearn.app.domain.usecase.OverrideVocabularyItemUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
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
        private val getVocabularyItemByWordUseCase: GetVocabularyItemByWordUseCase,
        private val overrideVocabularyItemUseCase: OverrideVocabularyItemUseCase,
        private val prefs: DayCountersStore,
        private val aiTranslationProvider: AiTranslationProvider,
        private val ioDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(AddWordUiState())
        private var pendingOverride: PendingOverrideSubmission? = null
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
                    previewContent = null,
                    isPreviewVisible = false,
                )
        }

        fun onTranslationChange(translation: String) {
            _uiState.value =
                _uiState.value.copy(
                    translation = translation,
                    translationError = null,
                    previewContent = null,
                    isPreviewVisible = false,
                )
        }

        fun onAddClick() {
            val currentState = _uiState.value
            if (currentState.word.isBlank()) {
                _uiState.value = _uiState.value.copy(wordError = "Please enter a word")
                return
            }

            viewModelScope.launch {
                _uiState.value =
                    _uiState.value.copy(
                        isLoading = true,
                        errorMessage = null,
                        successMessage = null,
                        loadingAction = AddWordLoadingAction.ADD,
                        previewContent = null,
                        isPreviewVisible = false,
                        isExistingWordDialogVisible = false,
                        isExistingWordDialogLoading = false,
                    )

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
                            loadingAction = null,
                        )
                    return@launch
                }

                handleWordSubmission(
                    word = currentState.word.trim(),
                    translation = finalTranslation.trim(),
                    fromPreview = false,
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
                    loadingAction = null,
                )
        }

        fun onUseAiToggle(checked: Boolean) {
            viewModelScope.launch { prefs.setUseAiForTranslation(checked) }
            _uiState.value =
                _uiState.value.copy(
                    useAiForTranslation = checked,
                    previewContent = null,
                    isPreviewVisible = false,
                )
        }

        fun onPreviewClick() {
            val currentState = _uiState.value
            if (currentState.word.isBlank()) {
                _uiState.value = _uiState.value.copy(wordError = "Please enter a word")
                return
            }
            if (!currentState.openAiAvailable || !currentState.useAiForTranslation) return

            viewModelScope.launch {
                _uiState.value =
                    _uiState.value.copy(
                        isLoading = true,
                        errorMessage = null,
                        successMessage = null,
                        translationError = null,
                        loadingAction = AddWordLoadingAction.PREVIEW,
                        previewContent = null,
                        isPreviewVisible = false,
                    )

                runCatching {
                    requestAiTranslation(currentState.word.trim())
                }.fold(
                    onSuccess = { translation ->
                        val sanitizedTranslation = translation.trim()
                        if (sanitizedTranslation.isBlank()) {
                            _uiState.value =
                                _uiState.value.copy(
                                    isLoading = false,
                                    translationError = "Please enter a translation",
                                    loadingAction = null,
                                )
                        } else {
                            _uiState.value =
                                _uiState.value.copy(
                                    isLoading = false,
                                    translation = sanitizedTranslation,
                                    previewContent =
                                        AddWordPreviewContent(
                                            word = currentState.word.trim(),
                                            translation = sanitizedTranslation,
                                        ),
                                    isPreviewVisible = true,
                                    loadingAction = null,
                                )
                        }
                    },
                    onFailure = { error ->
                        _uiState.value =
                            _uiState.value.copy(
                                isLoading = false,
                                errorMessage = error.message ?: "Failed to generate preview",
                                loadingAction = null,
                            )
                    },
                )
            }
        }

        fun onPreviewCancel() {
            pendingOverride = null
            _uiState.value =
                _uiState.value.copy(
                    word = "",
                    translation = "",
                    previewContent = null,
                    isPreviewVisible = false,
                    errorMessage = null,
                    wordError = null,
                    translationError = null,
                    isLoading = false,
                    loadingAction = null,
                    isSuccess = false,
                    successMessage = null,
                    isExistingWordDialogVisible = false,
                    existingWordDialogWord = null,
                    isExistingWordDialogLoading = false,
                )
        }

        fun onExistingWordDialogCancel() {
            pendingOverride = null
            _uiState.value =
                _uiState.value.copy(
                    isExistingWordDialogVisible = false,
                    isExistingWordDialogLoading = false,
                    existingWordDialogWord = null,
                )
        }

        fun onExistingWordDialogProceed() {
            val pending = pendingOverride ?: return
            viewModelScope.launch {
                _uiState.value =
                    _uiState.value.copy(
                        isExistingWordDialogLoading = true,
                        errorMessage = null,
                        successMessage = null,
                    )

                overrideVocabularyItemUseCase(
                    existingItem = pending.existingItem,
                    newWord = pending.word,
                    newTranslation = pending.translation,
                ).fold(
                    onSuccess = {
                        pendingOverride = null
                        _uiState.value =
                            _uiState.value.copy(
                                isExistingWordDialogVisible = false,
                                isExistingWordDialogLoading = false,
                                existingWordDialogWord = null,
                                word = "",
                                translation = "",
                                previewContent = null,
                                isPreviewVisible = false,
                                isSuccess = true,
                                successMessage = OVERRIDE_SUCCESS_MESSAGE,
                                isLoading = false,
                                loadingAction = null,
                            )
                    },
                    onFailure = { error ->
                        pendingOverride = null
                        _uiState.value =
                            _uiState.value.copy(
                                isExistingWordDialogVisible = false,
                                isExistingWordDialogLoading = false,
                                existingWordDialogWord = null,
                                errorMessage = error.message ?: "Failed to update word",
                            )
                    },
                )
            }
        }

        private suspend fun handleWordSubmission(
            word: String,
            translation: String,
            fromPreview: Boolean,
        ) {
            val existingItem =
                runCatching { getVocabularyItemByWordUseCase(word) }
                    .getOrElse { error ->
                        _uiState.value =
                            _uiState.value.copy(
                                isLoading = false,
                                loadingAction = null,
                                errorMessage = error.message ?: "Failed to check existing words",
                                isExistingWordDialogVisible = false,
                                isExistingWordDialogLoading = false,
                            )
                        return
                    }

            if (existingItem != null) {
                pendingOverride =
                    PendingOverrideSubmission(
                        existingItem = existingItem,
                        word = word,
                        translation = translation,
                        fromPreview = fromPreview,
                    )
                _uiState.value =
                    _uiState.value.copy(
                        isLoading = false,
                        loadingAction = null,
                        errorMessage = null,
                        isExistingWordDialogVisible = true,
                        existingWordDialogWord = word,
                        isExistingWordDialogLoading = false,
                    )
                return
            }

            addVocabularyItemUseCase(
                word = word,
                translation = translation,
            ).fold(
                onSuccess = {
                    _uiState.value =
                        _uiState.value.copy(
                            isLoading = false,
                            errorMessage = null,
                            wordError = null,
                            translationError = null,
                            word = "",
                            translation = "",
                            previewContent = null,
                            isPreviewVisible = false,
                            isSuccess = true,
                            successMessage = DEFAULT_ADD_SUCCESS_MESSAGE,
                            loadingAction = null,
                            isExistingWordDialogVisible = false,
                            isExistingWordDialogLoading = false,
                            existingWordDialogWord = null,
                        )
                },
                onFailure = { error ->
                    _uiState.value =
                        _uiState.value.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Failed to add word",
                            loadingAction = null,
                            isPreviewVisible = fromPreview && _uiState.value.previewContent != null,
                        )
                },
            )
        }

        private data class PendingOverrideSubmission(
            val existingItem: VocabularyItem,
            val word: String,
            val translation: String,
            val fromPreview: Boolean,
        )

        companion object {
            private const val DEFAULT_ADD_SUCCESS_MESSAGE = "Word added successfully!"
            private const val OVERRIDE_SUCCESS_MESSAGE = "Word updated and progress reset!"
        }

        fun onPreviewConfirmAdd() {
            val preview = _uiState.value.previewContent ?: return
            viewModelScope.launch {
                _uiState.value =
                    _uiState.value.copy(
                        isLoading = true,
                        errorMessage = null,
                        successMessage = null,
                        loadingAction = AddWordLoadingAction.PREVIEW_CONFIRM,
                        isExistingWordDialogVisible = false,
                        isExistingWordDialogLoading = false,
                    )

                handleWordSubmission(
                    word = preview.word.trim(),
                    translation = preview.translation.trim(),
                    fromPreview = true,
                )
            }
        }

        private suspend fun requestAiTranslation(word: String): String =
            withContext(ioDispatcher) {
                val apiKey: String = prefs.readOpenAiApiKey().first().orEmpty()
                require(apiKey.isNotBlank()) { "Missing OpenAI API key" }

                val systemPrompt = prefs.readOpenAiPrompt().first()
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
    val previewContent: AddWordPreviewContent? = null,
    val isPreviewVisible: Boolean = false,
    val isExistingWordDialogVisible: Boolean = false,
    val existingWordDialogWord: String? = null,
    val isExistingWordDialogLoading: Boolean = false,
    val loadingAction: AddWordLoadingAction? = null,
)

data class AddWordPreviewContent(
    val word: String,
    val translation: String,
)

enum class AddWordLoadingAction {
    ADD,
    PREVIEW,
    PREVIEW_CONFIRM,
}
