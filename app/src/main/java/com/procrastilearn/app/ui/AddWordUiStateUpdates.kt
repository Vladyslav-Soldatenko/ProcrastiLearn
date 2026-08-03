package com.procrastilearn.app.ui

import kotlinx.coroutines.flow.MutableStateFlow

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

internal fun showExistingWordDialog(
    uiState: MutableStateFlow<AddWordUiState>,
    word: String,
) {
    uiState.value =
        uiState.value.copy(
            isLoading = false,
            loadingAction = null,
            errorMessage = null,
            isExistingWordDialogVisible = true,
            existingWordDialogWord = word,
            isExistingWordDialogLoading = false,
            isPreviewVisible = false,
        )
}

internal fun applyLookupFailure(
    uiState: MutableStateFlow<AddWordUiState>,
    message: String,
) {
    uiState.value =
        uiState.value.copy(
            isLoading = false,
            loadingAction = null,
            errorMessage = message,
            isExistingWordDialogVisible = false,
            isExistingWordDialogLoading = false,
        )
}

internal fun applySubmissionSuccess(
    uiState: MutableStateFlow<AddWordUiState>,
    successMessage: String,
) {
    uiState.value =
        uiState.value
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
                successMessage = successMessage,
                loadingAction = null,
                isExistingWordDialogVisible = false,
                isExistingWordDialogLoading = false,
                existingWordDialogWord = null,
            ).withBidirectionalCleared()
}

internal fun applySubmissionFailure(
    uiState: MutableStateFlow<AddWordUiState>,
    message: String,
    fromPreview: Boolean,
) {
    uiState.value =
        uiState.value.copy(
            isLoading = false,
            loadingAction = null,
            errorMessage = message,
            isPreviewVisible = fromPreview && uiState.value.previewContent != null,
        )
}

internal fun applyExistingWordDialogSuccess(
    uiState: MutableStateFlow<AddWordUiState>,
    successMessage: String,
) {
    uiState.value =
        uiState.value
            .copy(
                isExistingWordDialogVisible = false,
                isExistingWordDialogLoading = false,
                existingWordDialogWord = null,
                word = "",
                translation = "",
                previewContent = null,
                isPreviewVisible = false,
                isSuccess = true,
                successMessage = successMessage,
                isLoading = false,
                loadingAction = null,
            ).withBidirectionalCleared()
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
