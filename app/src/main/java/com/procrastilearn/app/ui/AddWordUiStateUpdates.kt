package com.procrastilearn.app.ui

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Shared [AddWordUiState] mutation shapes for the different ways the add-word flow can end,
 * split out from [AddWordViewModel] so that file stays under detekt's top-level function
 * threshold once the existing-word-conflict handling moved into [ExistingWordOverrideCoordinator].
 */
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

// Shared by the "brand new word" add and the "user already acknowledged this override" submit -
// both end the add-word flow the same way, differing only in which success string is shown.
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

// The existing-word dialog's own success shape differs from applySubmissionSuccess: it doesn't
// touch wordError/translationError, since nothing in the dialog flow can set them.
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
