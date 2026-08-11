package com.procrastilearn.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.procrastilearn.app.R
import com.procrastilearn.app.ui.AddWordLoadingAction
import com.procrastilearn.app.ui.PendingWordUi
import kotlinx.collections.immutable.ImmutableList

@Composable
internal fun ActionButtonsRow(
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
                    contentDescription = stringResource(R.string.action_add),
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text =
                        stringResource(
                            if (isAddLaterMode) {
                                R.string.add_word_button_add_later
                            } else {
                                R.string.action_add
                            },
                        ),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
internal fun SuccessOverlayCard(successMessage: String?) {
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
internal fun ErrorMessageCard(errorMessage: String?) {
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
    pendingWords: ImmutableList<PendingWordUi>,
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
