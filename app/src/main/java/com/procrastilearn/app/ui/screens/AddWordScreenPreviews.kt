package com.procrastilearn.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.procrastilearn.app.domain.model.AiTranslationDirection
import com.procrastilearn.app.ui.AddWordPreviewContent
import com.procrastilearn.app.ui.PendingWordUi
import com.procrastilearn.app.ui.theme.MyApplicationTheme

@Preview(showBackground = true, name = "Add Word • AI Enabled")
@Composable
private fun AddWordContentPreviewAiEnabled() {
    MyApplicationTheme {
        AddWordContent(
            onNavigateToList = {},
            word = "Haus",
            translation = "",
            wordError = null,
            translationError = null,
            isLoading = false,
            errorMessage = null,
            isSuccess = false,
            successMessage = null,
            openAiAvailable = true,
            useAiForTranslation = true,
            translationDirection = AiTranslationDirection.EN_TO_RU,
            previewContent =
                AddWordPreviewContent(
                    word = "Haus",
                    translation = "House\nHome\nBuilding",
                ),
            isPreviewVisible = false,
            isExistingWordDialogVisible = false,
            existingWordDialogWord = null,
            isExistingWordDialogLoading = false,
            loadingAction = null,
            isOnline = true,
            isAddLaterMode = false,
            pendingWords = emptyList(),
            onDeletePendingWord = {},
            onWordChange = {},
            onTranslationChange = {},
            onUseAiToggle = {},
            onTranslationDirectionToggle = {},
            onPreviewClick = {},
            onPreviewCancel = {},
            onPreviewConfirmAdd = {},
            onAddClick = {},
            onExistingWordDialogCancel = {},
            onExistingWordDialogProceed = {},
        )
    }
}

@Preview(showBackground = true, name = "Add Word • Offline (Add later)")
@Composable
private fun AddWordContentPreviewOffline() {
    MyApplicationTheme {
        AddWordContent(
            onNavigateToList = {},
            word = "Baum",
            translation = "",
            wordError = null,
            translationError = null,
            isLoading = false,
            errorMessage = null,
            isSuccess = false,
            successMessage = null,
            openAiAvailable = true,
            useAiForTranslation = true,
            translationDirection = AiTranslationDirection.EN_TO_RU,
            previewContent = null,
            isPreviewVisible = false,
            isExistingWordDialogVisible = false,
            existingWordDialogWord = null,
            isExistingWordDialogLoading = false,
            loadingAction = null,
            isOnline = false,
            isAddLaterMode = true,
            pendingWords =
                listOf(
                    PendingWordUi(id = 1, word = "Haus"),
                    PendingWordUi(id = 2, word = "Auto"),
                    PendingWordUi(id = 3, word = "Fenster"),
                ),
            onDeletePendingWord = {},
            onWordChange = {},
            onTranslationChange = {},
            onUseAiToggle = {},
            onTranslationDirectionToggle = {},
            onPreviewClick = {},
            onPreviewCancel = {},
            onPreviewConfirmAdd = {},
            onAddClick = {},
            onExistingWordDialogCancel = {},
            onExistingWordDialogProceed = {},
        )
    }
}

@Preview(showBackground = true, name = "Pending Words Section")
@Composable
private fun PendingWordsSectionPreview() {
    MyApplicationTheme {
        PendingWordsSection(
            pendingWords =
                listOf(
                    PendingWordUi(id = 1, word = "Haus"),
                    PendingWordUi(id = 2, word = "Auto"),
                ),
            onDeletePendingWord = {},
        )
    }
}

@Preview(showBackground = true, name = "Translation Direction • EN->RU")
@Composable
private fun TranslationDirectionRowPreviewEnRu() {
    MyApplicationTheme {
        TranslationDirectionRow(
            direction = AiTranslationDirection.EN_TO_RU,
            onToggle = {},
        )
    }
}

@Preview(showBackground = true, name = "Translation Direction • RU->EN")
@Composable
private fun TranslationDirectionRowPreviewRuEn() {
    MyApplicationTheme {
        TranslationDirectionRow(
            direction = AiTranslationDirection.RU_TO_EN,
            onToggle = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AddWordContentPreview() {
    MyApplicationTheme {
        AddWordContent(
            onNavigateToList = {},
            word = "example",
            translation = "приклад\nнаочний\nзразок",
            wordError = null,
            translationError = null,
            isLoading = false,
            errorMessage = null,
            isSuccess = false,
            successMessage = "Word added successfully!",
            openAiAvailable = true,
            useAiForTranslation = false,
            translationDirection = AiTranslationDirection.EN_TO_RU,
            previewContent = null,
            isPreviewVisible = false,
            isExistingWordDialogVisible = false,
            existingWordDialogWord = null,
            isExistingWordDialogLoading = false,
            loadingAction = null,
            isOnline = true,
            isAddLaterMode = false,
            pendingWords = emptyList(),
            onDeletePendingWord = {},
            onWordChange = {},
            onTranslationChange = {},
            onUseAiToggle = {},
            onTranslationDirectionToggle = {},
            onPreviewClick = {},
            onPreviewCancel = {},
            onPreviewConfirmAdd = {},
            onAddClick = {},
            onExistingWordDialogCancel = {},
            onExistingWordDialogProceed = {},
        )
    }
}
