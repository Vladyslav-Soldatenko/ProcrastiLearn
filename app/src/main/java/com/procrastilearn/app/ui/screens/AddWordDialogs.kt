package com.procrastilearn.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.procrastilearn.app.R
import com.procrastilearn.app.overlay.theme.OverlayTheme
import com.procrastilearn.app.overlay.theme.OverlayThemeTokens
import com.procrastilearn.app.ui.AddWordPreviewContent
import com.procrastilearn.app.ui.theme.MyApplicationTheme

@Composable
internal fun AddWordPreviewDialog(
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
internal fun ExistingWordDialog(
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
