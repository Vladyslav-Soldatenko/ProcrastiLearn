package com.procrastilearn.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.procrastilearn.app.data.connectivity.NetworkConnectivityObserver
import com.procrastilearn.app.data.local.prefs.OpenAiPreferencesStore
import com.procrastilearn.app.domain.model.AiTranslationDirection
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.domain.usecase.AddVocabularyItemUseCase
import com.procrastilearn.app.domain.usecase.DeletePendingWordUseCase
import com.procrastilearn.app.domain.usecase.GenerateAiTranslationUseCase
import com.procrastilearn.app.domain.usecase.GetVocabularyItemByWordUseCase
import com.procrastilearn.app.domain.usecase.ObservePendingWordsUseCase
import com.procrastilearn.app.domain.usecase.OverrideVocabularyItemUseCase
import com.procrastilearn.app.domain.usecase.QueuePendingWordUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
@Suppress("LongParameterList")
class AddWordViewModel @Inject
    constructor(
        private val addVocabularyItemUseCase: AddVocabularyItemUseCase,
        private val getVocabularyItemByWordUseCase: GetVocabularyItemByWordUseCase,
        private val overrideVocabularyItemUseCase: OverrideVocabularyItemUseCase,
        private val prefs: OpenAiPreferencesStore,
        private val generateAiTranslationUseCase: GenerateAiTranslationUseCase,
        private val queuePendingWordUseCase: QueuePendingWordUseCase,
        private val observePendingWordsUseCase: ObservePendingWordsUseCase,
        private val deletePendingWordUseCase: DeletePendingWordUseCase,
        private val connectivityObserver: NetworkConnectivityObserver,
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
                    prefs.readAiTranslationDirection(),
                ) { key: String?, useAi: Boolean, direction: AiTranslationDirection ->
                    Triple(!key.isNullOrBlank(), useAi, direction)
                }.collectLatest { (hasKey, useAi, direction) ->
                    _uiState.value =
                        _uiState.value.copy(
                            openAiAvailable = hasKey,
                            useAiForTranslation = useAi,
                            translationDirection = direction,
                        )
                }
            }

            viewModelScope.launch {
                connectivityObserver.observe().collectLatest { online ->
                    _uiState.value = _uiState.value.copy(isOnline = online)
                }
            }

            viewModelScope.launch {
                observePendingWordsUseCase().collectLatest { pendingWords ->
                    _uiState.value =
                        _uiState.value.copy(
                            pendingWords = pendingWords.map { PendingWordUi(id = it.id, word = it.word) },
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

            if (currentState.isAddLaterMode) {
                queuePendingWord(currentState.word.trim(), currentState.translationDirection)
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

                val finalTranslation = resolveTranslationForAdd(currentState)

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

        private suspend fun resolveTranslationForAdd(currentState: AddWordUiState): String {
            val aiTranslation: String? =
                if (currentState.aiModeActive) {
                    runCatching {
                        generateAiTranslationUseCase(
                            currentState.word,
                            currentState.translationDirection,
                        )
                    }.getOrNull()
                } else {
                    null
                }

            return if (!aiTranslation.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(translation = aiTranslation)
                aiTranslation
            } else {
                currentState.translation
            }
        }

        private fun queuePendingWord(
            word: String,
            direction: AiTranslationDirection,
        ) {
            viewModelScope.launch {
                queuePendingWordUseCase(word, direction)
                _uiState.value =
                    _uiState.value.copy(
                        word = "",
                        translation = "",
                        wordError = null,
                        translationError = null,
                        errorMessage = null,
                        isLoading = false,
                        loadingAction = null,
                        isSuccess = true,
                        successMessage = PENDING_QUEUED_MESSAGE,
                    )
            }
        }

        fun onDeletePendingWord(id: Long) {
            viewModelScope.launch { deletePendingWordUseCase(id) }
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
        if (!checked && _uiState.value.translationDirection == AiTranslationDirection.RU_TO_EN) {
            return
        }
        viewModelScope.launch { prefs.setUseAiForTranslation(checked) }
        _uiState.value =
            _uiState.value.copy(
                useAiForTranslation = checked,
                previewContent = null,
                isPreviewVisible = false,
            )
    }

    fun onTranslationDirectionToggle() {
        val current = _uiState.value.translationDirection
        val next =
            if (current == AiTranslationDirection.EN_TO_RU) {
                AiTranslationDirection.RU_TO_EN
            } else {
                AiTranslationDirection.EN_TO_RU
            }
        viewModelScope.launch {
            prefs.setAiTranslationDirection(next)
            if (next == AiTranslationDirection.RU_TO_EN) {
                prefs.setUseAiForTranslation(true)
            }
        }
        _uiState.value =
            _uiState.value.copy(
                translationDirection = next,
                useAiForTranslation = next == AiTranslationDirection.RU_TO_EN || _uiState.value.useAiForTranslation,
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
            if (!currentState.aiModeActive) return
            if (!currentState.isOnline) return

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
                    generateAiTranslationUseCase(
                        currentState.word.trim(),
                        currentState.translationDirection,
                    )
                }.fold(
                    onSuccess = { translation -> handlePreviewTranslationSuccess(currentState.word, translation) },
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

        private fun handlePreviewTranslationSuccess(
            word: String,
            translation: String,
        ) {
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
                                word = word.trim(),
                                translation = sanitizedTranslation,
                            ),
                        isPreviewVisible = true,
                        loadingAction = null,
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
                        handleExistingItemLookupFailure(error)
                        return
                    }

            if (existingItem != null) {
                promptExistingWordOverride(existingItem, word, translation, fromPreview)
                return
            }

            submitNewVocabularyItem(word, translation, fromPreview)
        }

        private fun handleExistingItemLookupFailure(error: Throwable) {
            _uiState.value =
                _uiState.value.copy(
                    isLoading = false,
                    loadingAction = null,
                    errorMessage = error.message ?: "Failed to check existing words",
                    isExistingWordDialogVisible = false,
                    isExistingWordDialogLoading = false,
                )
        }

        private fun promptExistingWordOverride(
            existingItem: VocabularyItem,
            word: String,
            translation: String,
            fromPreview: Boolean,
        ) {
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
        }

        private suspend fun submitNewVocabularyItem(
            word: String,
            translation: String,
            fromPreview: Boolean,
        ) {
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
            private const val PENDING_QUEUED_MESSAGE = "Saved. Translation will be generated once you're back online."
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
    val translationDirection: AiTranslationDirection = AiTranslationDirection.EN_TO_RU,
    val previewContent: AddWordPreviewContent? = null,
    val isPreviewVisible: Boolean = false,
    val isExistingWordDialogVisible: Boolean = false,
    val existingWordDialogWord: String? = null,
    val isExistingWordDialogLoading: Boolean = false,
    val loadingAction: AddWordLoadingAction? = null,
    val isOnline: Boolean = true,
    val pendingWords: List<PendingWordUi> = emptyList(),
) {
    val aiModeActive: Boolean get() = openAiAvailable && useAiForTranslation
    val isAddLaterMode: Boolean get() = aiModeActive && !isOnline
}

data class AddWordPreviewContent(
    val word: String,
    val translation: String,
)

data class PendingWordUi(
    val id: Long,
    val word: String,
)

enum class AddWordLoadingAction {
    ADD,
    PREVIEW,
    PREVIEW_CONFIRM,
}
