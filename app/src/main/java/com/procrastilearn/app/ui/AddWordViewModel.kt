package com.procrastilearn.app.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.procrastilearn.app.R
import com.procrastilearn.app.data.connectivity.NetworkConnectivityObserver
import com.procrastilearn.app.data.local.prefs.OpenAiPreferencesStore
import com.procrastilearn.app.data.local.prefs.TranslationPreferences
import com.procrastilearn.app.data.text.ProcessTextEventBus
import com.procrastilearn.app.domain.model.AiTranslationDirection
import com.procrastilearn.app.domain.model.Language
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.domain.usecase.GenerateAiTranslationUseCase
import com.procrastilearn.app.domain.usecase.GetVocabularyItemByWordUseCase
import com.procrastilearn.app.domain.usecase.PendingWordUseCases
import com.procrastilearn.app.domain.usecase.QueuePendingWordUseCase
import com.procrastilearn.app.domain.usecase.VocabularyEntryUseCases
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
@Suppress("LongParameterList") // arity from composing already-decomposed collaborators, not an undecomposed monolith
class AddWordViewModel @Inject
    constructor(
        private val vocabularyEntryUseCases: VocabularyEntryUseCases,
        private val pendingWordUseCases: PendingWordUseCases,
        private val translationPreferences: TranslationPreferences,
        private val generateAiTranslationUseCase: GenerateAiTranslationUseCase,
        private val connectivityObserver: NetworkConnectivityObserver,
        @param:ApplicationContext private val context: Context,
        private val existingWordOverrideCoordinator: ExistingWordOverrideCoordinator,
        private val processTextEventBus: ProcessTextEventBus = ProcessTextEventBus(),
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(AddWordUiState())
        val uiState: StateFlow<AddWordUiState> = _uiState.asStateFlow()

        init {
            // Observe OpenAI key and toggle from preferences
            viewModelScope.launch {
                combine(
                    translationPreferences.openAiStore.readOpenAiApiKey(),
                    translationPreferences.openAiStore.readUseAiForTranslation(),
                    translationPreferences.openAiStore.readAiTranslationDirection(),
                    translationPreferences.languagePreferencesStore.readLanguagePair(),
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
                pendingWordUseCases.observe().collectLatest { pendingWords ->
                    _uiState.value =
                        _uiState.value.copy(
                            pendingWords = pendingWords.map { PendingWordUi(id = it.id, word = it.word) },
                        )
                }
            }

            viewModelScope.launch {
                processTextEventBus.events.collectLatest { text ->
                    if (!text.isNullOrBlank()) {
                        val prefill = resolveProcessTextPrefill(translationPreferences.openAiStore, text)
                        if (prefill != null) {
                            existingWordOverrideCoordinator.resetForNewWord()
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
            existingWordOverrideCoordinator.clearAcknowledgement()
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

        @Suppress("LongMethod")
        fun onAddClick() {
            val currentState = _uiState.value
            if (currentState.word.isBlank()) {
                _uiState.value =
                    _uiState.value.copy(wordError = context.getString(R.string.add_word_error_word_required))
                return
            }

            if (currentState.isAddLaterMode) {
                val pendingMessage = context.getString(R.string.add_word_success_pending)
                viewModelScope.launch {
                    queuePendingWord(
                        pendingWordUseCases.queue,
                        _uiState,
                        currentState.word.trim(),
                        currentState.translationDirection,
                        pendingMessage,
                    )
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
                        setBlankTranslationError(
                            _uiState,
                            context.getString(R.string.add_word_error_translation_required),
                        )
                        return@launch
                    }
                    handleWordSubmission(word = word, translation = typedTranslation.trim(), fromPreview = false)
                    return@launch
                }

                // AI mode: check for a duplicate before spending an AI request.
                when (
                    val preflight =
                        existingWordOverrideCoordinator.checkBeforeAiTranslationRequest(
                            word,
                            currentState.translationDirection,
                        )
                ) {
                    is ExistingWordPreflight.LookupFailed -> {
                        applyLookupFailure(
                            _uiState,
                            preflight.error.message ?: context.getString(R.string.add_word_error_lookup_failed),
                        )
                        return@launch
                    }
                    is ExistingWordPreflight.ConfirmationRequired -> {
                        showExistingWordDialog(_uiState, preflight.word)
                        return@launch
                    }
                    ExistingWordPreflight.NoConflict -> Unit
                }

                val finalTranslation = resolveTranslationForAdd(_uiState, generateAiTranslationUseCase, currentState)

                if (finalTranslation.isBlank()) {
                    setBlankTranslationError(_uiState, context.getString(R.string.add_word_error_translation_required))
                    return@launch
                }

                submitNewVocabularyItem(word, finalTranslation.trim(), fromPreview = false)
            }
        }

        fun onDeletePendingWord(id: Long) {
            viewModelScope.launch { pendingWordUseCases.delete(id) }
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
            existingWordOverrideCoordinator.clearAcknowledgement()
            viewModelScope.launch { translationPreferences.openAiStore.setUseAiForTranslation(checked) }
            _uiState.value =
                _uiState.value.copy(
                    useAiForTranslation = checked,
                    previewContent = null,
                    isPreviewVisible = false,
                )
        }

        fun onTranslationDirectionToggle() {
            existingWordOverrideCoordinator.clearAcknowledgement()
            val current = _uiState.value.translationDirection
            val next =
                if (current == AiTranslationDirection.TARGET_TO_NATIVE) {
                    AiTranslationDirection.NATIVE_TO_TARGET
                } else {
                    AiTranslationDirection.TARGET_TO_NATIVE
                }
            viewModelScope.launch { translationPreferences.openAiStore.setAiTranslationDirection(next) }
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

        @Suppress("LongMethod")
        fun onPreviewClick() {
            val currentState = _uiState.value
            if (currentState.word.isBlank()) {
                _uiState.value =
                    _uiState.value.copy(wordError = context.getString(R.string.add_word_error_word_required))
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
                    lookupExistingItem(vocabularyEntryUseCases.getByWord, _uiState, word, lookupFailedMessage)
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

                val blankTranslationMessage = context.getString(R.string.add_word_error_translation_required)
                runCatching { generateAiTranslationUseCase(word, currentState.translationDirection) }.fold(
                    onSuccess = { translation ->
                        handlePreviewTranslationSuccess(
                            _uiState,
                            word,
                            translation,
                            isStoredTranslation = false,
                            blankTranslationMessage,
                        )
                    },
                    onFailure = { error ->
                        _uiState.value =
                            _uiState.value.copy(
                                isLoading = false,
                                errorMessage =
                                    error.message ?: context.getString(R.string.add_word_error_preview_failed),
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
                        existingWordOverrideCoordinator.acknowledge(preview.word)
                        handlePreviewTranslationSuccess(
                            _uiState,
                            preview.word,
                            translation,
                            isStoredTranslation = false,
                            blankTranslationMessage = context.getString(R.string.add_word_error_translation_required),
                        )
                    },
                    onFailure = { error ->
                        _uiState.value =
                            _uiState.value.copy(
                                isLoading = false,
                                errorMessage =
                                    error.message ?: context.getString(R.string.add_word_error_preview_failed),
                                loadingAction = null,
                                previewContent = null,
                                isPreviewVisible = false,
                            )
                    },
                )
            }
        }

        fun onPreviewCancel() {
            existingWordOverrideCoordinator.resetForNewWord()
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
            val fromPreview = existingWordOverrideCoordinator.cancelPendingOverride()
            val restorePreview = fromPreview && _uiState.value.previewContent != null
            _uiState.value =
                _uiState.value.copy(
                    isExistingWordDialogVisible = false,
                    isExistingWordDialogLoading = false,
                    existingWordDialogWord = null,
                    isPreviewVisible = if (restorePreview) true else _uiState.value.isPreviewVisible,
                )
        }

        fun onExistingWordDialogProceed() {
            if (!existingWordOverrideCoordinator.hasPendingOverride()) return
            viewModelScope.launch {
                _uiState.value =
                    _uiState.value.copy(
                        isExistingWordDialogLoading = true,
                        errorMessage = null,
                        successMessage = null,
                    )

                when (
                    val result =
                        existingWordOverrideCoordinator.proceedWithPendingOverride { _uiState.value.toCardOptions() }
                ) {
                    OverrideProceedResult.NoPendingOverride -> Unit
                    is OverrideProceedResult.TranslationFailed ->
                        closeExistingWordDialogWithError(
                            _uiState,
                            result.error.message ?: context.getString(R.string.add_word_error_translation_failed),
                        )
                    is OverrideProceedResult.OverrideFailed ->
                        closeExistingWordDialogWithError(
                            _uiState,
                            result.error.message ?: context.getString(R.string.add_word_error_update_failed),
                        )
                    OverrideProceedResult.Overridden ->
                        applyExistingWordDialogSuccess(_uiState, context.getString(R.string.add_word_success_updated))
                }
            }
        }

        private suspend fun handleWordSubmission(
            word: String,
            translation: String,
            fromPreview: Boolean,
        ) {
            val direction = _uiState.value.translationDirection
            when (
                val resolution =
                    existingWordOverrideCoordinator.resolveForSubmission(word, translation, direction, fromPreview) {
                        _uiState.value.toCardOptions()
                    }
            ) {
                is SubmissionResolution.LookupFailed ->
                    applyLookupFailure(
                        _uiState,
                        resolution.error.message ?: context.getString(R.string.add_word_error_lookup_failed),
                    )
                is SubmissionResolution.ConfirmationRequired ->
                    showExistingWordDialog(_uiState, resolution.word)
                is SubmissionResolution.Overridden ->
                    applySubmissionSuccess(_uiState, context.getString(R.string.add_word_success_updated))
                is SubmissionResolution.OverrideFailed ->
                    applySubmissionFailure(
                        _uiState,
                        resolution.error.message ?: context.getString(R.string.add_word_error_update_failed),
                        resolution.fromPreview,
                    )
                SubmissionResolution.NoConflict ->
                    submitNewVocabularyItem(word, translation, fromPreview)
            }
        }

        private suspend fun submitNewVocabularyItem(
            word: String,
            translation: String,
            fromPreview: Boolean,
        ) {
            val currentState = _uiState.value
            vocabularyEntryUseCases
                .add(
                    word = word,
                    translation = translation,
                    bidirectional = currentState.bidirectional,
                    backwardPromptOverride = currentState.backwardPromptOverride.ifBlank { null },
                    backwardAnswerOverride = currentState.backwardAnswerOverride.ifBlank { null },
                ).fold(
                    onSuccess = {
                        applySubmissionSuccess(_uiState, context.getString(R.string.add_word_success_added))
                    },
                    onFailure = { error ->
                        applySubmissionFailure(
                            _uiState,
                            error.message ?: context.getString(R.string.add_word_error_add_failed),
                            fromPreview,
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

internal fun AddWordUiState.toCardOptions(): BidirectionalCardOptions =
    BidirectionalCardOptions(
        bidirectional = bidirectional,
        backwardPromptOverride = backwardPromptOverride.ifBlank { null },
        backwardAnswerOverride = backwardAnswerOverride.ifBlank { null },
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
        .onFailure { error -> applyLookupFailure(uiState, error.message ?: failureMessage) }

internal suspend fun resolveTranslationForAdd(
    uiState: MutableStateFlow<AddWordUiState>,
    generateAiTranslationUseCase: GenerateAiTranslationUseCase,
    currentState: AddWordUiState,
): String {
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
        uiState.value = uiState.value.copy(translation = aiTranslation)
        aiTranslation
    } else {
        currentState.translation
    }
}

internal fun handlePreviewTranslationSuccess(
    uiState: MutableStateFlow<AddWordUiState>,
    word: String,
    translation: String,
    isStoredTranslation: Boolean,
    blankTranslationMessage: String,
) {
    val sanitizedTranslation = translation.trim()
    if (sanitizedTranslation.isBlank()) {
        uiState.value =
            uiState.value.copy(
                isLoading = false,
                translationError = blankTranslationMessage,
                loadingAction = null,
                previewContent = null,
                isPreviewVisible = false,
            )
    } else {
        uiState.value =
            uiState.value.copy(
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
