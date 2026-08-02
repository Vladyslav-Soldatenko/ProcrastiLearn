package com.procrastilearn.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.procrastilearn.app.R
import com.procrastilearn.app.domain.model.AiTranslationDirection

@Composable
internal fun WordInputCard(
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
internal fun AiToggleRow(
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
internal fun TranslationInputCard(
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
internal fun BidirectionalOptionSection(
    visible: Boolean,
    bidirectional: Boolean,
    isCustomizing: Boolean,
    backwardPromptOverride: String,
    backwardAnswerOverride: String,
    onBidirectionalToggle: (Boolean) -> Unit,
    onCustomizeToggle: () -> Unit,
    onBackwardPromptOverrideChange: (String) -> Unit,
    onBackwardAnswerOverrideChange: (String) -> Unit,
) {
    if (!visible) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Checkbox(
                    checked = bidirectional,
                    onCheckedChange = onBidirectionalToggle,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.add_word_bidirectional_toggle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }

            if (bidirectional) {
                TextButton(onClick = onCustomizeToggle) {
                    Text(
                        text =
                            stringResource(
                                if (isCustomizing) {
                                    R.string.add_word_customize_backward_hide
                                } else {
                                    R.string.add_word_customize_backward_show
                                },
                            ),
                    )
                }

                AnimatedVisibility(visible = isCustomizing) {
                    Column {
                        OutlinedTextField(
                            value = backwardPromptOverride,
                            onValueChange = onBackwardPromptOverrideChange,
                            label = { Text(stringResource(R.string.add_word_backward_prompt_label)) },
                            placeholder = { Text(stringResource(R.string.add_word_backward_prompt_placeholder)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = backwardAnswerOverride,
                            onValueChange = onBackwardAnswerOverrideChange,
                            label = { Text(stringResource(R.string.add_word_backward_answer_label)) },
                            placeholder = { Text(stringResource(R.string.add_word_backward_answer_placeholder)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
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
