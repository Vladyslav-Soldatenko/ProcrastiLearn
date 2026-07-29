package com.procrastilearn.app.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.procrastilearn.app.R
import com.procrastilearn.app.data.connectivity.NetworkConnectivityObserver
import com.procrastilearn.app.data.local.prefs.LanguagePreferencesStore
import com.procrastilearn.app.data.local.prefs.OpenAiPreferencesStore
import com.procrastilearn.app.data.text.ProcessTextEventBus
import com.procrastilearn.app.domain.model.AiTranslationDirection
import com.procrastilearn.app.domain.model.Language
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.domain.usecase.AddVocabularyItemUseCase
import com.procrastilearn.app.domain.usecase.DeletePendingWordUseCase
import com.procrastilearn.app.domain.usecase.GenerateAiTranslationUseCase
import com.procrastilearn.app.domain.usecase.GetVocabularyItemByWordUseCase
import com.procrastilearn.app.domain.usecase.ObservePendingWordsUseCase
import com.procrastilearn.app.domain.usecase.OverrideVocabularyItemUseCase
import com.procrastilearn.app.domain.usecase.QueuePendingWordUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
@Suppress("LongParameterList", "LargeClass", "TooManyFunctions")
class AddWordViewModel @Inject
    constructor(
        private val addVocabularyItemUseCase: AddVocabularyItemUseCase,
        private val getVocabularyItemByWordUseCase: GetVocabularyItemByWordUseCase,
        private val overrideVocabularyItemUseCase: OverrideVocabularyItemUseCase,
        private val openAiStore: OpenAiPreferencesStore,
        private val languagePreferencesStore: LanguagePreferencesStore,
        private val generateAiTranslationUseCase: GenerateAiTranslationUseCase,
        private val queuePendingWordUseCase: QueuePendingWordUseCase,
        private val observePendingWordsUseCase: ObservePendingWordsUseCase,
        private val deletePendingWordUseCase: DeletePendingWordUseCase,
        private val connectivityObserver: NetworkConnectivityObserver,
        @ApplicationContext private val context: Context,
        private val processTextEventBus: ProcessTextEventBus = ProcessTextEventBus(),
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(AddWordUiState())
        private var pendingOverride: PendingOverrideSubmission? = null
        private var acknowledgedOverrideWord: String? = null
        val uiState: StateFlow<AddWordUiState> = _uiState.asStateFlow()

        init {
            // Observe OpenAI key and toggle from preferences
            viewModelScope.launch {
                combine(
                    openAiStore.readOpenAiApiKey(),
                    openAiStore.readUseAiForTranslation(),
                    openAiStore.readAiTranslationDirection(),
                    languagePreferencesStore.readLanguagePair(),
                ) { key: String?, useAi: Boolean, direction: AiTranslationDirection, languagePair ->
                    AddWordCombinedPrefs(
                        hasKey = !key.isNullOrBlank(),
                        useAi = useAi,
                        direction = direction,
                        nativeLanguage = languagePair?.native ?: Language.ENGLISH,
                        targetLanguage = languagePair?.target ?: Language.RUSSIAN,
                    )
                }.collectLatest { combined ->
                    val aiModeNowActive = combined.hasKey && combined.useAi
                    val updated =
                        _uiState.value.copy(
                            openAiAvailable = combined.hasKey,
                            useAiForTranslation = combined.useAi,
                            translationDirection = combined.direction,
                            nativeLanguageCode = combined.nativeLanguage.code.uppercase(),
                            targetLanguageCode = combined.targetLanguage.code.uppercase(),
                        )
                    // AI mode hides the bidirectional checkbox entirely, so its state
                    // shouldn't silently linger and get submitted once it flips on.
                    _uiState.value = if (aiModeNowActive) updated.withBidirectionalCleared() else updated
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

            viewModelScope.launch {
                processTextEventBus.events.collectLatest { text ->
                    if (!text.isNullOrBlank()) {
                        val prefill = resolveProcessTextPrefill(openAiStore, text)
                        if (prefill != null) {
                            acknowledgedOverrideWord = null
                            pendingOverride = null
                            _uiState.value =
                                _uiState.value.copy(
                                    word = prefill.word,
                                    translation = "",
                                    wordError = null,
                                    translationError = null,
                                    openAiAvailable = prefill.hasKey,
                                    useAiForTranslation = prefill.useAi,
                                    previewContent = null,
                                    isPreviewVisible = false,
                                    errorMessage = null,
                                    successMessage = null,
                                    isSuccess = false,
                                )
                            if (prefill.hasKey && prefill.useAi && _uiState.value.isOnline) {
                                onPreviewClick()
                            }
                        }
                        processTextEventBus.consume()
                    }
                }
            }
        }

        fun onWordChange(word: String) {
            acknowledgedOverrideWord = null
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
                _uiState.value = _uiState.value.copy(wordError = context.getString(R.string.add_word_error_word_required))
                return
            }

            if (currentState.isAddLaterMode) {
                val pendingMessage = context.getString(R.string.add_word_success_pending)
                viewModelScope.launch {
                    queuePendingWord(queuePendingWordUseCase, _uiState, currentState.word.trim(), currentState.translationDirection, pendingMessage)
                }
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

                val word = currentState.word.trim()

                if (!currentState.aiModeActive) {
                    val typedTranslation = currentState.translation
                    if (typedTranslation.isBlank()) {
                        setBlankTranslationError(_uiState, context.getString(R.string.add_word_error_translation_required))
                        return@launch
                    }
                    handleWordSubmission(word = word, translation = typedTranslation.trim(), fromPreview = false)
                    return@launch
                }

                // AI mode: check for a duplicate before spending an AI request.
                val lookupFailedMessage = context.getString(R.string.add_word_error_lookup_failed)
                val existingItem =
                    lookupExistingItem(getVocabularyItemByWordUseCase, _uiState, word, lookupFailedMessage)
                        .getOrElse { return@launch }

                if (existingItem != null) {
                    promptExistingWordOverride(
                        existingItem = existingItem,
                        word = word,
                        translation = null,
                        direction = currentState.translationDirection,
                        fromPreview = false,
                    )
                    return@launch
                }

                val finalTranslation = resolveTranslationForAdd(currentState)

                if (finalTranslation.isBlank()) {
                    setBlankTranslationError(_uiState, context.getString(R.string.add_word_error_translation_required))
                    return@launch
                }

                submitNewVocabularyItem(word, finalTranslation.trim(), fromPreview = false)
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
        acknowledgedOverrideWord = null
        viewModelScope.launch { openAiStore.setUseAiForTranslation(checked) }
        _uiState.value =
            _uiState.value.copy(
                useAiForTranslation = checked,
                previewContent = null,
                isPreviewVisible = false,
            )
    }

    fun onTranslationDirectionToggle() {
        acknowledgedOverrideWord = null
        val current = _uiState.value.translationDirection
        val next =
            if (current == AiTranslationDirection.TARGET_TO_NATIVE) {
                AiTranslationDirection.NATIVE_TO_TARGET
            } else {
                AiTranslationDirection.TARGET_TO_NATIVE
            }
        viewModelScope.launch { openAiStore.setAiTranslationDirection(next) }
        _uiState.value =
            _uiState.value.copy(
                translationDirection = next,
                previewContent = null,
                isPreviewVisible = false,
            )
    }

        fun onBidirectionalToggle(checked: Boolean) {
            _uiState.value = _uiState.value.withBidirectionalToggle(checked)
        }

        fun onCustomizeBackwardToggle() {
            _uiState.value = _uiState.value.copy(isCustomizingBackward = !_uiState.value.isCustomizingBackward)
        }

        fun onBackwardPromptOverrideChange(value: String) {
            _uiState.value = _uiState.value.copy(backwardPromptOverride = value)
        }

        fun onBackwardAnswerOverrideChange(value: String) {
            _uiState.value = _uiState.value.copy(backwardAnswerOverride = value)
        }

    fun onPreviewClick() {
            val currentState = _uiState.value
            if (currentState.word.isBlank()) {
                _uiState.value = _uiState.value.copy(wordError = context.getString(R.string.add_word_error_word_required))
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

                val word = currentState.word.trim()

                // Check for a duplicate before spending an AI request: if the word is
                // already saved, show its stored translation immediately instead.
                val lookupFailedMessage = context.getString(R.string.add_word_error_lookup_failed)
                val existingItem =
                    lookupExistingItem(getVocabularyItemByWordUseCase, _uiState, word, lookupFailedMessage)
                        .getOrElse { return@launch }

                if (existingItem != null && existingItem.translation.isNotBlank()) {
                    _uiState.value =
                        _uiState.value.copy(
                            isLoading = false,
                            translation = existingItem.translation,
                            previewContent =
                                AddWordPreviewContent(
                                    word = word,
                                    translation = existingItem.translation,
                                    isStoredTranslation = true,
                                ),
                            isPreviewVisible = true,
                            loadingAction = null,
                        )
                    return@launch
                }

                runCatching { generateAiTranslationUseCase(word, currentState.translationDirection) }.fold(
                    onSuccess = { translation ->
                        handlePreviewTranslationSuccess(word, translation, isStoredTranslation = false)
                    },
                    onFailure = { error ->
                        _uiState.value =
                            _uiState.value.copy(
                                isLoading = false,
                                errorMessage = error.message ?: context.getString(R.string.add_word_error_preview_failed),
                                loadingAction = null,
                            )
                    },
                )
            }
        }

        fun onPreviewRegenerate() {
            val currentState = _uiState.value
            val preview = currentState.previewContent ?: return
            if (!currentState.aiModeActive) return
            if (!currentState.isOnline) return

            viewModelScope.launch {
                _uiState.value =
                    _uiState.value.copy(
                        isLoading = true,
                        errorMessage = null,
                        loadingAction = AddWordLoadingAction.PREVIEW_REGENERATE,
                    )

                runCatching {
                    generateAiTranslationUseCase(preview.word, currentState.translationDirection)
                }.fold(
                    onSuccess = { translation ->
                        acknowledgedOverrideWord = preview.word
                        handlePreviewTranslationSuccess(preview.word, translation, isStoredTranslation = false)
                    },
                    onFailure = { error ->
                        _uiState.value =
                            _uiState.value.copy(
                                isLoading = false,
                                errorMessage = error.message ?: context.getString(R.string.add_word_error_preview_failed),
                                loadingAction = null,
                                previewContent = null,
                                isPreviewVisible = false,
                            )
                    },
                )
            }
        }

        private fun handlePreviewTranslationSuccess(
            word: String,
            translation: String,
            isStoredTranslation: Boolean,
        ) {
            val sanitizedTranslation = translation.trim()
            if (sanitizedTranslation.isBlank()) {
                _uiState.value =
                    _uiState.value.copy(
                        isLoading = false,
                        translationError = context.getString(R.string.add_word_error_translation_required),
                        loadingAction = null,
                        previewContent = null,
                        isPreviewVisible = false,
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
                                isStoredTranslation = isStoredTranslation,
                            ),
                        isPreviewVisible = true,
                        loadingAction = null,
                    )
            }
        }

        fun onPreviewCancel() {
            pendingOverride = null
            acknowledgedOverrideWord = null
            _uiState.value =
                _uiState.value
                    .copy(
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
                    ).withBidirectionalCleared()
        }

        fun onExistingWordDialogCancel() {
            val pending = pendingOverride
            pendingOverride = null
            val restorePreview = pending?.fromPreview == true && _uiState.value.previewContent != null
            _uiState.value =
                _uiState.value.copy(
                    isExistingWordDialogVisible = false,
                    isExistingWordDialogLoading = false,
                    existingWordDialogWord = null,
                    isPreviewVisible = if (restorePreview) true else _uiState.value.isPreviewVisible,
                )
        }

        @Suppress("LongMethod")
        fun onExistingWordDialogProceed() {
            val pending = pendingOverride ?: return
            viewModelScope.launch {
                _uiState.value =
                    _uiState.value.copy(
                        isExistingWordDialogLoading = true,
                        errorMessage = null,
                        successMessage = null,
                    )

                val translationFailedMessage = context.getString(R.string.add_word_error_translation_failed)
                val translation =
                    resolvePendingTranslation(
                        generateAiTranslationUseCase,
                        pending.word,
                        pending.translation,
                        pending.direction,
                        translationFailedMessage,
                    ).getOrElse { error ->
                        pendingOverride = null
                        closeExistingWordDialogWithError(_uiState, error.message ?: translationFailedMessage)
                        return@launch
                    }

                val currentState = _uiState.value
                overrideVocabularyItemUseCase(
                    existingItem = pending.existingItem,
                    newWord = pending.word,
                    newTranslation = translation,
                    bidirectional = currentState.bidirectional,
                    backwardPromptOverride = currentState.backwardPromptOverride.ifBlank { null },
                    backwardAnswerOverride = currentState.backwardAnswerOverride.ifBlank { null },
                ).fold(
                    onSuccess = {
                        pendingOverride = null
                        acknowledgedOverrideWord = null
                        _uiState.value =
                            _uiState.value
                                .copy(
                                    isExistingWordDialogVisible = false,
                                    isExistingWordDialogLoading = false,
                                    existingWordDialogWord = null,
                                    word = "",
                                    translation = "",
                                    previewContent = null,
                                    isPreviewVisible = false,
                                    isSuccess = true,
                                    successMessage = context.getString(R.string.add_word_success_updated),
                                    isLoading = false,
                                    loadingAction = null,
                                ).withBidirectionalCleared()
                    },
                    onFailure = { error ->
                        pendingOverride = null
                        closeExistingWordDialogWithError(
                            _uiState,
                            error.message ?: context.getString(R.string.add_word_error_update_failed),
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
            val direction = _uiState.value.translationDirection
            val existingItem =
                lookupExistingItem(
                    getVocabularyItemByWordUseCase,
                    _uiState,
                    word,
                    context.getString(R.string.add_word_error_lookup_failed),
                ).getOrElse { return }

            if (existingItem != null) {
                if (isAcknowledgedOverride(word, acknowledgedOverrideWord)) {
                    submitAcknowledgedOverride(existingItem, word, translation, fromPreview)
                } else {
                    promptExistingWordOverride(existingItem, word, translation, direction, fromPreview)
                }
                return
            }

            submitNewVocabularyItem(word, translation, fromPreview)
        }

        private suspend fun submitAcknowledgedOverride(
            existingItem: VocabularyItem,
            word: String,
            translation: String,
            fromPreview: Boolean,
        ) {
            val currentState = _uiState.value
            overrideVocabularyItemUseCase(
                existingItem = existingItem,
                newWord = word,
                newTranslation = translation,
                bidirectional = currentState.bidirectional,
                backwardPromptOverride = currentState.backwardPromptOverride.ifBlank { null },
                backwardAnswerOverride = currentState.backwardAnswerOverride.ifBlank { null },
            ).fold(
                onSuccess = {
                    acknowledgedOverrideWord = null
                    _uiState.value =
                        _uiState.value
                            .copy(
                                isLoading = false,
                                errorMessage = null,
                                wordError = null,
                                translationError = null,
                                word = "",
                                translation = "",
                                previewContent = null,
                                isPreviewVisible = false,
                                isSuccess = true,
                                successMessage = context.getString(R.string.add_word_success_updated),
                                loadingAction = null,
                                isExistingWordDialogVisible = false,
                                isExistingWordDialogLoading = false,
                                existingWordDialogWord = null,
                            ).withBidirectionalCleared()
                },
                onFailure = { error ->
                    acknowledgedOverrideWord = null
                    _uiState.value =
                        _uiState.value.copy(
                            isLoading = false,
                            loadingAction = null,
                            errorMessage = error.message ?: context.getString(R.string.add_word_error_update_failed),
                            isPreviewVisible = fromPreview && _uiState.value.previewContent != null,
                        )
                },
            )
        }

        private fun promptExistingWordOverride(
            existingItem: VocabularyItem,
            word: String,
            translation: String?,
            direction: AiTranslationDirection,
            fromPreview: Boolean,
        ) {
            pendingOverride =
                PendingOverrideSubmission(
                    existingItem = existingItem,
                    word = word,
                    translation = translation,
                    direction = direction,
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
                    isPreviewVisible = false,
                )
        }

        private suspend fun submitNewVocabularyItem(
            word: String,
            translation: String,
            fromPreview: Boolean,
        ) {
            val currentState = _uiState.value
            addVocabularyItemUseCase(
                word = word,
                translation = translation,
                bidirectional = currentState.bidirectional,
                backwardPromptOverride = currentState.backwardPromptOverride.ifBlank { null },
                backwardAnswerOverride = currentState.backwardAnswerOverride.ifBlank { null },
            ).fold(
                onSuccess = {
                    _uiState.value =
                        _uiState.value
                            .copy(
                                isLoading = false,
                                errorMessage = null,
                                wordError = null,
                                translationError = null,
                                word = "",
                                translation = "",
                                previewContent = null,
                                isPreviewVisible = false,
                                isSuccess = true,
                                successMessage = context.getString(R.string.add_word_success_added),
                                loadingAction = null,
                                isExistingWordDialogVisible = false,
                                isExistingWordDialogLoading = false,
                                existingWordDialogWord = null,
                            ).withBidirectionalCleared()
                },
                onFailure = { error ->
                    _uiState.value =
                        _uiState.value.copy(
                            isLoading = false,
                            errorMessage = error.message ?: context.getString(R.string.add_word_error_add_failed),
                            loadingAction = null,
                            isPreviewVisible = fromPreview && _uiState.value.previewContent != null,
                        )
                },
            )
        }

        private data class AddWordCombinedPrefs(
            val hasKey: Boolean,
            val useAi: Boolean,
            val direction: AiTranslationDirection,
            val nativeLanguage: Language,
            val targetLanguage: Language,
        )

        private data class PendingOverrideSubmission(
            val existingItem: VocabularyItem,
            val word: String,
            val translation: String?,
            val direction: AiTranslationDirection,
            val fromPreview: Boolean,
        )

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
    val translationDirection: AiTranslationDirection = AiTranslationDirection.TARGET_TO_NATIVE,
    val nativeLanguageCode: String = Language.ENGLISH.code.uppercase(),
    val targetLanguageCode: String = Language.RUSSIAN.code.uppercase(),
    val previewContent: AddWordPreviewContent? = null,
    val isPreviewVisible: Boolean = false,
    val isExistingWordDialogVisible: Boolean = false,
    val existingWordDialogWord: String? = null,
    val isExistingWordDialogLoading: Boolean = false,
    val loadingAction: AddWordLoadingAction? = null,
    val isOnline: Boolean = true,
    val pendingWords: List<PendingWordUi> = emptyList(),
    val bidirectional: Boolean = false,
    val isCustomizingBackward: Boolean = false,
    val backwardPromptOverride: String = "",
    val backwardAnswerOverride: String = "",
) {
    val aiModeActive: Boolean get() = openAiAvailable && useAiForTranslation
    val isAddLaterMode: Boolean get() = aiModeActive && !isOnline
    val showBidirectionalOption: Boolean get() = !aiModeActive
}

// The bidirectional checkbox + reverse-card customization fields are reset together in
// several places (unchecking, AI mode turning on, submission success) - centralized here
// instead of repeating the same four-field copy() at each call site.
internal fun AddWordUiState.withBidirectionalCleared(): AddWordUiState =
    copy(
        bidirectional = false,
        isCustomizingBackward = false,
        backwardPromptOverride = "",
        backwardAnswerOverride = "",
    )

internal fun AddWordUiState.withBidirectionalToggle(checked: Boolean): AddWordUiState =
    copy(
        bidirectional = checked,
        isCustomizingBackward = if (checked) isCustomizingBackward else false,
        backwardPromptOverride = if (checked) backwardPromptOverride else "",
        backwardAnswerOverride = if (checked) backwardAnswerOverride else "",
    )

data class AddWordPreviewContent(
    val word: String,
    val translation: String,
    val isStoredTranslation: Boolean = false,
)

data class PendingWordUi(
    val id: Long,
    val word: String,
)

enum class AddWordLoadingAction {
    ADD,
    PREVIEW,
    PREVIEW_CONFIRM,
    PREVIEW_REGENERATE,
}

internal fun isAcknowledgedOverride(
    word: String,
    acknowledgedWord: String?,
): Boolean = acknowledgedWord?.equals(word, ignoreCase = true) == true

internal fun closeExistingWordDialogWithError(
    uiState: MutableStateFlow<AddWordUiState>,
    message: String,
) {
    uiState.value =
        uiState.value.copy(
            isExistingWordDialogVisible = false,
            isExistingWordDialogLoading = false,
            existingWordDialogWord = null,
            errorMessage = message,
        )
}

internal fun setBlankTranslationError(
    uiState: MutableStateFlow<AddWordUiState>,
    message: String,
) {
    uiState.value =
        uiState.value.copy(
            isLoading = false,
            translationError = message,
            loadingAction = null,
        )
}

internal suspend fun queuePendingWord(
    queuePendingWordUseCase: QueuePendingWordUseCase,
    uiState: MutableStateFlow<AddWordUiState>,
    word: String,
    direction: AiTranslationDirection,
    successMessage: String,
) {
    queuePendingWordUseCase(word, direction)
    uiState.value =
        uiState.value.copy(
            word = "",
            translation = "",
            wordError = null,
            translationError = null,
            errorMessage = null,
            isLoading = false,
            loadingAction = null,
            isSuccess = true,
            successMessage = successMessage,
        )
}

internal suspend fun lookupExistingItem(
    getVocabularyItemByWordUseCase: GetVocabularyItemByWordUseCase,
    uiState: MutableStateFlow<AddWordUiState>,
    word: String,
    failureMessage: String,
): Result<VocabularyItem?> =
    runCatching { getVocabularyItemByWordUseCase(word) }
        .onFailure { error ->
            uiState.value =
                uiState.value.copy(
                    isLoading = false,
                    loadingAction = null,
                    errorMessage = error.message ?: failureMessage,
                    isExistingWordDialogVisible = false,
                    isExistingWordDialogLoading = false,
                )
        }

internal suspend fun resolvePendingTranslation(
    generateAiTranslationUseCase: GenerateAiTranslationUseCase,
    word: String,
    translation: String?,
    direction: AiTranslationDirection,
    blankResultMessage: String,
): Result<String> =
    if (translation != null) {
        Result.success(translation)
    } else {
        runCatching { generateAiTranslationUseCase(word, direction) }
            .mapCatching { generated -> generated.trim().ifBlank { error(blankResultMessage) } }
    }

internal data class ProcessTextPrefill(
    val word: String,
    val hasKey: Boolean,
    val useAi: Boolean,
)

internal suspend fun resolveProcessTextPrefill(
    openAiStore: OpenAiPreferencesStore,
    rawText: String,
): ProcessTextPrefill? {
    val word = rawText.trim()
    if (word.isBlank()) return null

    val hasKey = !openAiStore.readOpenAiApiKey().first().isNullOrBlank()
    val useAi = openAiStore.readUseAiForTranslation().first()
    return ProcessTextPrefill(word, hasKey, useAi)
}
