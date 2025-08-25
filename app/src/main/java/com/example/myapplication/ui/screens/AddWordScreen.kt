package com.example.myapplication.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import com.example.myapplication.R
import com.example.myapplication.ui.AddWordViewModel
import kotlinx.coroutines.delay

@Suppress("MagicNumber", "LongMethod")
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

    Box(
        modifier =
            Modifier
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
                    OutlinedTextField(
                        value = uiState.word,
                        onValueChange = viewModel::onWordChange,
                        label = { Text(stringResource(R.string.add_word_label_word)) },
                        placeholder = { Text(stringResource(R.string.add_word_placeholder_word)) },
                        isError = uiState.wordError != null,
                        supportingText = uiState.wordError?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Translation Input Card
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
                        value = uiState.translation,
                        onValueChange = viewModel::onTranslationChange,
                        label = { Text(stringResource(R.string.add_word_label_translation)) },
                        placeholder = { Text(stringResource(R.string.add_word_placeholder_translation)) },
                        isError = uiState.translationError != null,
                        supportingText = uiState.translationError?.let { { Text(it) } },
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

            Spacer(modifier = Modifier.height(32.dp))

            // Add Button
            Button(
                onClick = viewModel::onAddClick,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                enabled = !uiState.isLoading,
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
                if (uiState.isLoading) {
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

            // Error Message
            AnimatedVisibility(
                visible = uiState.errorMessage != null,
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
                        text = uiState.errorMessage ?: "",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // Success Message Overlay
        AnimatedVisibility(
            visible = uiState.isSuccess,
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
                        text = uiState.successMessage ?: stringResource(R.string.add_word_success_default),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}
