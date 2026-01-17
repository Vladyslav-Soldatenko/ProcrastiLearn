package com.procrastilearn.app.ui.dojo

import android.content.res.Configuration
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.procrastilearn.app.R
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.overlay.OverlayUiState
import com.procrastilearn.app.overlay.components.LearningCard
import com.procrastilearn.app.overlay.theme.OverlayTheme
import com.procrastilearn.app.ui.dojo.components.DojoStatsHeader
import com.procrastilearn.app.ui.theme.MyApplicationTheme
import io.github.openspacedrepetition.Rating

@Composable
fun DojoScreen(viewModel: DojoViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    DojoScreen(uiState, viewModel::onToggleShowAnswer, viewModel::onDifficultySelected)
}

@VisibleForTesting
@Composable
internal fun DojoScreen(
    uiState: DojoUiState,
    onToggleShowAnswer: () -> Unit,
    onDifficultySelected: (Rating) -> Unit,
) {
    MyApplicationTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Stats header always visible
                DojoStatsHeader(
                    newQuotaRemaining = uiState.newQuotaRemaining,
                    pendingReviewCount = uiState.pendingReviewCount,
                )

                // Content area
                when {
                    uiState.isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    uiState.hasNoWords -> {
                        EmptyState(modifier = Modifier.fillMaxWidth().weight(1f))
                    }
                    else -> {
                        // Show LearningCard by converting DojoUiState to OverlayUiState
                        val overlayState =
                            OverlayUiState(
                                vocabularyItem = uiState.vocabularyItem,
                                showAnswer = uiState.showAnswer,
                                unlocked = false,
                                isLoading = false,
                            )
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = true)
                                    .padding(horizontal = 12.dp, vertical = 0.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            OverlayTheme {
                                LearningCard(
                                    state = overlayState,
                                    onToggleShowAnswer = onToggleShowAnswer,
                                    onDifficultySelected = onDifficultySelected,
                                    showTranslationButtonHeight = 56.dp,
                                    addNavigationBarsPadding = false,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.dojo_empty_title),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.dojo_empty_message),
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

private val sampleWord =
    VocabularyItem(
        id = 1L,
        word = "serendipity",
        translation = "счастливая случайность",
        isNew = true,
    )

private class DojoUiStateProvider : PreviewParameterProvider<DojoUiState> {
    override val values: Sequence<DojoUiState> =
        sequenceOf(
            // Loading state
            DojoUiState(
                vocabularyItem = null,
                showAnswer = false,
                isLoading = true,
                newQuotaRemaining = 15,
                pendingReviewCount = 10,
            ),
            // Flashcard with answer hidden
            DojoUiState(
                vocabularyItem = sampleWord,
                showAnswer = false,
                isLoading = false,
                newQuotaRemaining = 17,
                pendingReviewCount = 10,
            ),
            // Flashcard with answer shown
            DojoUiState(
                vocabularyItem = sampleWord,
                showAnswer = true,
                isLoading = false,
                newQuotaRemaining = 17,
                pendingReviewCount = 10,
            ),
            // Empty state
            DojoUiState(
                vocabularyItem = null,
                showAnswer = false,
                isLoading = false,
                newQuotaRemaining = 0,
                pendingReviewCount = 0,
            ),
        )
}

@Preview(
    name = "DojoScreen • Light",
    showSystemUi = true,
    device = Devices.PIXEL_7,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_NO,
)
@Preview(
    name = "DojoScreen • Dark",
    showSystemUi = true,
    device = Devices.PIXEL_7,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun DojoScreenPreview(
    @PreviewParameter(DojoUiStateProvider::class) state: DojoUiState,
) {
    MyApplicationTheme {
        DojoScreen(
            uiState = state,
            onToggleShowAnswer = {},
            onDifficultySelected = {},
        )
    }
}
