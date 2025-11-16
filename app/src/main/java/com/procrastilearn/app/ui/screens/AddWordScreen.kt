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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.procrastilearn.app.R
import com.procrastilearn.app.overlay.theme.OverlayTheme
import com.procrastilearn.app.overlay.theme.OverlayThemeTokens
import com.procrastilearn.app.ui.AddWordLoadingAction
import com.procrastilearn.app.ui.AddWordPreviewContent
import com.procrastilearn.app.ui.AddWordViewModel
import com.procrastilearn.app.ui.theme.MyApplicationTheme
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
        previewContent = uiState.previewContent,
        isPreviewVisible = uiState.isPreviewVisible,
        isExistingWordDialogVisible = uiState.isExistingWordDialogVisible,
        existingWordDialogWord = uiState.existingWordDialogWord,
        isExistingWordDialogLoading = uiState.isExistingWordDialogLoading,
        loadingAction = uiState.loadingAction,
        onWordChange = viewModel::onWordChange,
        onTranslationChange = viewModel::onTranslationChange,
        onUseAiToggle = viewModel::onUseAiToggle,
        onPreviewClick = viewModel::onPreviewClick,
        onPreviewCancel = viewModel::onPreviewCancel,
        onPreviewConfirmAdd = viewModel::onPreviewConfirmAdd,
        onAddClick = viewModel::onAddClick,
        onExistingWordDialogCancel = viewModel::onExistingWordDialogCancel,
        onExistingWordDialogProceed = viewModel::onExistingWordDialogProceed,
    )
}

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
            onWordChange = {},
            onTranslationChange = {},
            onUseAiToggle = {},
            onPreviewClick = {},
            onPreviewCancel = {},
            onPreviewConfirmAdd = {},
            onAddClick = {},
            onExistingWordDialogCancel = {},
            onExistingWordDialogProceed = {},
        )
    }
}

@Preview(showBackground = true, name = "Add Word • Preview Dialog")
@Composable
private fun AddWordPreviewDialogContentPreview() {
    MyApplicationTheme {
        AddWordPreviewDialogContent(
            previewContent =
                AddWordPreviewContent(
                    word = "Haus",
                    translation = "House\nHome\nA building serving as human habitation.",
                ),
            isConfirmLoading = false,
            onCancel = {},
            onConfirm = {},
        )
    }
}

@Suppress("LongParameterList", "LongMethod")
@Composable
private fun AddWordContent(
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
    previewContent: AddWordPreviewContent?,
    isPreviewVisible: Boolean,
    isExistingWordDialogVisible: Boolean,
    existingWordDialogWord: String?,
    isExistingWordDialogLoading: Boolean,
    loadingAction: AddWordLoadingAction?,
    onWordChange: (String) -> Unit,
    onTranslationChange: (String) -> Unit,
    onUseAiToggle: (Boolean) -> Unit,
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

            // Word Input Card
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

            Spacer(modifier = Modifier.height(16.dp))

            // Translation Input Card (hidden when AI is used and key is present)
            if (!useAiForTranslation || !openAiAvailable) {
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

            Spacer(modifier = Modifier.height(32.dp))

            // Action Buttons
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
                        enabled = !isLoading,
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
                            text = stringResource(R.string.add_word_button_add),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }

            // Error Message
            AnimatedVisibility(
                visible = errorMessage != null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
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
private fun AddWordPreviewDialog(
    previewContent: AddWordPreviewContent,
    isConfirmLoading: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onCancel) {
        AddWordPreviewDialogContent(
            previewContent = previewContent,
            isConfirmLoading = isConfirmLoading,
            onCancel = onCancel,
            onConfirm = onConfirm,
        )
    }
}

@Composable
private fun AddWordPreviewDialogContent(
    previewContent: AddWordPreviewContent,
    isConfirmLoading: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    OverlayTheme {
        Surface(
            modifier =
                Modifier
                    .widthIn(min = 320.dp, max = 420.dp),
            shape = RoundedCornerShape(24.dp),
            color = OverlayThemeTokens.colors.cardContainer,
            tonalElevation = 12.dp,
        ) {
            Column(
                modifier =
                    Modifier
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.add_word_preview_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = OverlayThemeTokens.colors.helpText,
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = previewContent.word,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OverlayThemeTokens.colors.titleColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(20.dp))
                val scrollState = rememberScrollState()
                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 140.dp, max = 280.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = OverlayThemeTokens.colors.innerCardContainer,
                ) {
                    SelectionContainer {
                        Column(
                            modifier =
                                Modifier
                                    .verticalScroll(scrollState)
                                    .padding(16.dp),
                        ) {
                            Text(
                                text = previewContent.translation,
                                color = OverlayThemeTokens.colors.translationText,
                                fontSize = 18.sp,
                                lineHeight = 24.sp,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        enabled = !isConfirmLoading,
                    ) {
                        Text(text = stringResource(R.string.add_word_preview_cancel))
                    }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        enabled = !isConfirmLoading,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                            ),
                    ) {
                        if (isConfirmLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text(text = stringResource(R.string.add_word_preview_confirm))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExistingWordDialog(
    word: String?,
    isLoading: Boolean,
    onProceed: () -> Unit,
    onCancel: () -> Unit,
) {
    Dialog(onDismissRequest = onCancel) {
        ExistingWordDialogContent(
            word = word,
            isLoading = isLoading,
            onProceed = onProceed,
            onCancel = onCancel,
        )
    }
}

@Composable
private fun ExistingWordDialogContent(
    word: String?,
    isLoading: Boolean,
    onProceed: () -> Unit,
    onCancel: () -> Unit,
) {
    OverlayTheme {
        Surface(
            modifier = Modifier.widthIn(min = 320.dp, max = 420.dp),
            shape = RoundedCornerShape(24.dp),
            color = OverlayThemeTokens.colors.cardContainer,
            tonalElevation = 12.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.add_word_existing_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = OverlayThemeTokens.colors.titleColor,
                    textAlign = TextAlign.Center,
                )
                word?.takeIf { it.isNotBlank() }?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = OverlayThemeTokens.colors.titleColor,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.add_word_existing_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = OverlayThemeTokens.colors.helpText,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading,
                    ) {
                        Text(text = stringResource(R.string.add_word_existing_cancel))
                    }
                    Button(
                        onClick = onProceed,
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                            ),
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text(text = stringResource(R.string.add_word_existing_proceed))
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Existing Word Dialog")
@Composable
private fun ExistingWordDialogPreview() {
    MyApplicationTheme {
        ExistingWordDialogContent(
            word = "Haus",
            isLoading = false,
            onProceed = {},
            onCancel = {},
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
            previewContent = null,
            isPreviewVisible = false,
            isExistingWordDialogVisible = false,
            existingWordDialogWord = null,
            isExistingWordDialogLoading = false,
            loadingAction = null,
            onWordChange = {},
            onTranslationChange = {},
            onUseAiToggle = {},
            onPreviewClick = {},
            onPreviewCancel = {},
            onPreviewConfirmAdd = {},
            onAddClick = {},
            onExistingWordDialogCancel = {},
            onExistingWordDialogProceed = {},
        )
    }
}
