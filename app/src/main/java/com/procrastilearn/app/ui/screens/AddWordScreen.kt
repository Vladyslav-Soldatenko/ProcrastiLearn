package com.procrastilearn.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.procrastilearn.app.R
import com.procrastilearn.app.domain.model.AiTranslationDirection
import com.procrastilearn.app.ui.AddWordLoadingAction
import com.procrastilearn.app.ui.AddWordPreviewContent
import com.procrastilearn.app.ui.AddWordViewModel
import com.procrastilearn.app.ui.PendingWordUi
import kotlinx.coroutines.delay

@Suppress("MagicNumber")
@Composable
fun AddWordScreen(
    viewModel: AddWordViewModel = hiltViewModel(),
    onNavigateToList: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    // Show success message and reset form
    LaunchedEffect(uiState.isSuccess) {
        val delayTime = 2000L
        if (uiState.isSuccess) {
            delay(delayTime) // Show success message for 2 seconds
            viewModel.resetSuccess()
        }
    }

    AddWordContent(
        onNavigateToList = onNavigateToList,
        word = uiState.word,
        translation = uiState.translation,
        wordError = uiState.wordError,
        translationError = uiState.translationError,
        isLoading = uiState.isLoading,
        errorMessage = uiState.errorMessage,
        isSuccess = uiState.isSuccess,
        successMessage = uiState.successMessage,
        openAiAvailable = uiState.openAiAvailable,
        useAiForTranslation = uiState.useAiForTranslation,
        translationDirection = uiState.translationDirection,
        nativeLanguageCode = uiState.nativeLanguageCode,
        targetLanguageCode = uiState.targetLanguageCode,
        previewContent = uiState.previewContent,
        isPreviewVisible = uiState.isPreviewVisible,
        isExistingWordDialogVisible = uiState.isExistingWordDialogVisible,
        existingWordDialogWord = uiState.existingWordDialogWord,
        isExistingWordDialogLoading = uiState.isExistingWordDialogLoading,
        loadingAction = uiState.loadingAction,
        isOnline = uiState.isOnline,
        isAddLaterMode = uiState.isAddLaterMode,
        pendingWords = uiState.pendingWords,
        showBidirectionalOption = uiState.showBidirectionalOption,
        bidirectional = uiState.bidirectional,
        isCustomizingBackward = uiState.isCustomizingBackward,
        backwardPromptOverride = uiState.backwardPromptOverride,
        backwardAnswerOverride = uiState.backwardAnswerOverride,
        onDeletePendingWord = viewModel::onDeletePendingWord,
        onWordChange = viewModel::onWordChange,
        onTranslationChange = viewModel::onTranslationChange,
        onUseAiToggle = viewModel::onUseAiToggle,
        onTranslationDirectionToggle = viewModel::onTranslationDirectionToggle,
        onPreviewClick = viewModel::onPreviewClick,
        onPreviewCancel = viewModel::onPreviewCancel,
        onPreviewConfirmAdd = viewModel::onPreviewConfirmAdd,
        onPreviewRegenerate = viewModel::onPreviewRegenerate,
        onAddClick = viewModel::onAddClick,
        onExistingWordDialogCancel = viewModel::onExistingWordDialogCancel,
        onExistingWordDialogProceed = viewModel::onExistingWordDialogProceed,
        onBidirectionalToggle = viewModel::onBidirectionalToggle,
        onCustomizeBackwardToggle = viewModel::onCustomizeBackwardToggle,
        onBackwardPromptOverrideChange = viewModel::onBackwardPromptOverrideChange,
        onBackwardAnswerOverrideChange = viewModel::onBackwardAnswerOverrideChange,
    )
}

@Composable
internal fun AddWordContent(
    onNavigateToList: () -> Unit,
    word: String,
    translation: String,
    wordError: String?,
    translationError: String?,
    isLoading: Boolean,
    errorMessage: String?,
    isSuccess: Boolean,
    successMessage: String?,
    modifier: Modifier = Modifier,
    openAiAvailable: Boolean,
    useAiForTranslation: Boolean,
    translationDirection: AiTranslationDirection,
    nativeLanguageCode: String,
    targetLanguageCode: String,
    previewContent: AddWordPreviewContent?,
    isPreviewVisible: Boolean,
    isExistingWordDialogVisible: Boolean,
    existingWordDialogWord: String?,
    isExistingWordDialogLoading: Boolean,
    loadingAction: AddWordLoadingAction?,
    isOnline: Boolean,
    isAddLaterMode: Boolean,
    pendingWords: List<PendingWordUi>,
    onDeletePendingWord: (Long) -> Unit,
    onWordChange: (String) -> Unit,
    onTranslationChange: (String) -> Unit,
    onUseAiToggle: (Boolean) -> Unit,
    onTranslationDirectionToggle: () -> Unit,
    onPreviewClick: () -> Unit,
    onPreviewCancel: () -> Unit,
    onPreviewConfirmAdd: () -> Unit,
    onPreviewRegenerate: () -> Unit,
    onAddClick: () -> Unit,
    onExistingWordDialogCancel: () -> Unit,
    onExistingWordDialogProceed: () -> Unit,
    showBidirectionalOption: Boolean = true,
    bidirectional: Boolean = false,
    isCustomizingBackward: Boolean = false,
    backwardPromptOverride: String = "",
    backwardAnswerOverride: String = "",
    onBidirectionalToggle: (Boolean) -> Unit = {},
    onCustomizeBackwardToggle: () -> Unit = {},
    onBackwardPromptOverrideChange: (String) -> Unit = {},
    onBackwardAnswerOverrideChange: (String) -> Unit = {},
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .padding(16.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.add_word_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Center),
                )
                IconButton(
                    onClick = onNavigateToList,
                    modifier = Modifier.align(Alignment.CenterEnd).zIndex(2f),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Default.List,
                        contentDescription = stringResource(R.string.action_view_list),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.add_word_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(24.dp))

            WordInputCard(
                word = word,
                wordError = wordError,
                isLoading = isLoading,
                openAiAvailable = openAiAvailable,
                useAiForTranslation = useAiForTranslation,
                translationDirection = translationDirection,
                nativeLanguageCode = nativeLanguageCode,
                targetLanguageCode = targetLanguageCode,
                onWordChange = onWordChange,
                onUseAiToggle = onUseAiToggle,
                onTranslationDirectionToggle = onTranslationDirectionToggle,
            )

            Spacer(modifier = Modifier.height(16.dp))

            TranslationInputCard(
                translation = translation,
                translationError = translationError,
                useAiForTranslation = useAiForTranslation,
                openAiAvailable = openAiAvailable,
                onTranslationChange = onTranslationChange,
            )

            Spacer(modifier = Modifier.height(16.dp))

            BidirectionalOptionSection(
                visible = showBidirectionalOption,
                bidirectional = bidirectional,
                isCustomizing = isCustomizingBackward,
                backwardPromptOverride = backwardPromptOverride,
                backwardAnswerOverride = backwardAnswerOverride,
                onBidirectionalToggle = onBidirectionalToggle,
                onCustomizeToggle = onCustomizeBackwardToggle,
                onBackwardPromptOverrideChange = onBackwardPromptOverrideChange,
                onBackwardAnswerOverrideChange = onBackwardAnswerOverrideChange,
            )

            Spacer(modifier = Modifier.height(16.dp))

            ActionButtonsRow(
                openAiAvailable = openAiAvailable,
                useAiForTranslation = useAiForTranslation,
                isLoading = isLoading,
                isOnline = isOnline,
                loadingAction = loadingAction,
                isAddLaterMode = isAddLaterMode,
                onPreviewClick = onPreviewClick,
                onAddClick = onAddClick,
            )

            // Pending translations (queued while offline)
            AnimatedVisibility(
                visible = pendingWords.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                PendingWordsSection(
                    pendingWords = pendingWords,
                    onDeletePendingWord = onDeletePendingWord,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                )
            }

            // Error Message
            AnimatedVisibility(
                visible = errorMessage != null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                ErrorMessageCard(errorMessage = errorMessage)
            }
        }

        if (isPreviewVisible && previewContent != null) {
            AddWordPreviewDialog(
                previewContent = previewContent,
                isConfirmLoading =
                    isLoading &&
                        (
                            loadingAction == AddWordLoadingAction.PREVIEW_CONFIRM ||
                                loadingAction == AddWordLoadingAction.PREVIEW_REGENERATE
                        ),
                onCancel = onPreviewCancel,
                onConfirm = if (previewContent.isStoredTranslation) onPreviewRegenerate else onPreviewConfirmAdd,
            )
        }

        // Success Message Overlay
        AnimatedVisibility(
            visible = isSuccess,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            SuccessOverlayCard(successMessage = successMessage)
        }

        if (isExistingWordDialogVisible) {
            ExistingWordDialog(
                word = existingWordDialogWord,
                isLoading = isExistingWordDialogLoading,
                onProceed = onExistingWordDialogProceed,
                onCancel = onExistingWordDialogCancel,
            )
        }
    }
}
