package com.procrastilearn.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.procrastilearn.app.R
import com.procrastilearn.app.ui.AddWordLoadingAction
import com.procrastilearn.app.ui.AddWordPreviewContent
import com.procrastilearn.app.ui.AddWordViewModel
import com.procrastilearn.app.ui.PendingWordUi
import com.procrastilearn.app.domain.model.AiTranslationDirection
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
        onDeletePendingWord = viewModel::onDeletePendingWord,
        onWordChange = viewModel::onWordChange,
        onTranslationChange = viewModel::onTranslationChange,
        onUseAiToggle = viewModel::onUseAiToggle,
        onTranslationDirectionToggle = viewModel::onTranslationDirectionToggle,
        onPreviewClick = viewModel::onPreviewClick,
        onPreviewCancel = viewModel::onPreviewCancel,
        onPreviewConfirmAdd = viewModel::onPreviewConfirmAdd,
        onAddClick = viewModel::onAddClick,
        onExistingWordDialogCancel = viewModel::onExistingWordDialogCancel,
        onExistingWordDialogProceed = viewModel::onExistingWordDialogProceed,
    )
}

@Suppress("LongParameterList")
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
    onAddClick: () -> Unit,
    onExistingWordDialogCancel: () -> Unit,
    onExistingWordDialogProceed: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .padding(16.dp),
    ) {
        IconButton(
            onClick = onNavigateToList,
            modifier = Modifier.align(Alignment.TopEnd).zIndex(2f),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Default.List,
                contentDescription = stringResource(R.string.add_word_view_list),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // Title
            Text(
                text = stringResource(R.string.add_word_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.add_word_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(40.dp))

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

            Spacer(modifier = Modifier.height(32.dp))

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
                isConfirmLoading = isLoading && loadingAction == AddWordLoadingAction.PREVIEW_CONFIRM,
                onCancel = onPreviewCancel,
                onConfirm = onPreviewConfirmAdd,
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

@Composable
private fun WordInputCard(
    word: String,
    wordError: String?,
    isLoading: Boolean,
    openAiAvailable: Boolean,
    useAiForTranslation: Boolean,
    translationDirection: AiTranslationDirection,
    nativeLanguageCode: String,
    targetLanguageCode: String,
    onWordChange: (String) -> Unit,
    onUseAiToggle: (Boolean) -> Unit,
    onTranslationDirectionToggle: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            if (openAiAvailable) {
                AiToggleRow(
                    useAiForTranslation = useAiForTranslation,
                    translationDirection = translationDirection,
                    nativeLanguageCode = nativeLanguageCode,
                    targetLanguageCode = targetLanguageCode,
                    onUseAiToggle = onUseAiToggle,
                    onTranslationDirectionToggle = onTranslationDirectionToggle,
                )
            }
            val disableWordInput = isLoading && openAiAvailable && useAiForTranslation
            OutlinedTextField(
                value = word,
                onValueChange = onWordChange,
                label = { Text(stringResource(R.string.add_word_label_word)) },
                placeholder = { Text(stringResource(R.string.add_word_placeholder_word)) },
                isError = wordError != null,
                supportingText = wordError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !disableWordInput,
                readOnly = disableWordInput,
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
            )
        }
    }
}

@Composable
private fun AiToggleRow(
    useAiForTranslation: Boolean,
    translationDirection: AiTranslationDirection,
    nativeLanguageCode: String,
    targetLanguageCode: String,
    onUseAiToggle: (Boolean) -> Unit,
    onTranslationDirectionToggle: () -> Unit,
) {
    if (useAiForTranslation) {
        TranslationDirectionRow(
            direction = translationDirection,
            nativeLanguageCode = nativeLanguageCode,
            targetLanguageCode = targetLanguageCode,
            onToggle = onTranslationDirectionToggle,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        Checkbox(
            checked = useAiForTranslation,
            onCheckedChange = { onUseAiToggle(it) },
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.add_word_use_ai_toggle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun TranslationInputCard(
    translation: String,
    translationError: String?,
    useAiForTranslation: Boolean,
    openAiAvailable: Boolean,
    onTranslationChange: (String) -> Unit,
) {
    // Hidden when AI is used and key is present
    if (useAiForTranslation && openAiAvailable) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            OutlinedTextField(
                value = translation,
                onValueChange = onTranslationChange,
                label = { Text(stringResource(R.string.add_word_label_translation)) },
                placeholder = { Text(stringResource(R.string.add_word_placeholder_translation)) },
                isError = translationError != null,
                supportingText = translationError?.let { { Text(it) } },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 240.dp),
                singleLine = false,
                minLines = 4,
                maxLines = 8,
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                    ),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
            )
        }
    }
}

@Composable
private fun ActionButtonsRow(
    openAiAvailable: Boolean,
    useAiForTranslation: Boolean,
    isLoading: Boolean,
    isOnline: Boolean,
    loadingAction: AddWordLoadingAction?,
    isAddLaterMode: Boolean,
    onPreviewClick: () -> Unit,
    onAddClick: () -> Unit,
) {
    val showPreviewButton = openAiAvailable && useAiForTranslation
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (showPreviewButton) Arrangement.spacedBy(12.dp) else Arrangement.Start,
    ) {
        if (showPreviewButton) {
            Button(
                onClick = onPreviewClick,
                modifier =
                    Modifier
                        .weight(1f)
                        .height(56.dp),
                enabled = !isLoading && isOnline,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                elevation =
                    ButtonDefaults.buttonElevation(
                        defaultElevation = 2.dp,
                        pressedElevation = 4.dp,
                    ),
            ) {
                if (isLoading && loadingAction == AddWordLoadingAction.PREVIEW) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.add_word_button_preview),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }

        val addModifier =
            if (showPreviewButton) {
                Modifier
                    .weight(1f)
                    .height(56.dp)
            } else {
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            }

        Button(
            onClick = onAddClick,
            modifier = addModifier,
            enabled = !isLoading,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            elevation =
                ButtonDefaults.buttonElevation(
                    defaultElevation = 4.dp,
                    pressedElevation = 8.dp,
                ),
        ) {
            if (isLoading && loadingAction == AddWordLoadingAction.ADD) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.add_word_icon_add),
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text =
                        stringResource(
                            if (isAddLaterMode) {
                                R.string.add_word_button_add_later
                            } else {
                                R.string.add_word_button_add
                            },
                        ),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun SuccessOverlayCard(successMessage: String?) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "✓",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = successMessage ?: stringResource(R.string.add_word_success_default),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun ErrorMessageCard(errorMessage: String?) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
    ) {
        Text(
            text = errorMessage ?: "",
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun PendingWordsSection(
    pendingWords: List<PendingWordUi>,
    onDeletePendingWord: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.add_word_pending_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                pendingWords.forEachIndexed { index, pendingWord ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = pendingWord.word,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { onDeletePendingWord(pendingWord.id) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.add_word_pending_delete),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    if (index != pendingWords.lastIndex) {
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
internal fun TranslationDirectionRow(
    direction: AiTranslationDirection,
    nativeLanguageCode: String,
    targetLanguageCode: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isNativeToTarget = direction == AiTranslationDirection.NATIVE_TO_TARGET
    val startLabel = if (isNativeToTarget) nativeLanguageCode else targetLanguageCode
    val endLabel = if (isNativeToTarget) targetLanguageCode else nativeLanguageCode
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = startLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(onClick = onToggle) {
            Icon(
                imageVector = Icons.AutoMirrored.Default.ArrowForward,
                contentDescription = stringResource(R.string.add_word_toggle_direction),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = endLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
