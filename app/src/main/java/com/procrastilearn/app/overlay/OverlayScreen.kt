package com.procrastilearn.app.overlay

import android.content.res.Configuration
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.procrastilearn.app.R
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.overlay.components.LearningCard
import com.procrastilearn.app.overlay.theme.OverlayTheme
import com.procrastilearn.app.overlay.theme.OverlayThemeTokens
import io.github.openspacedrepetition.Rating

@Composable
@Suppress("ParameterNaming")
fun OverlayScreen(
    onGateCompleted: (GateCompletion) -> Unit,
    viewModel: OverlayViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentOnGateCompleted by rememberUpdatedState(onGateCompleted)

    // Initial load
    LaunchedEffect(Unit) {
        viewModel.onOverlayOpened()
    }

    // When unlocked, tell the service to remove the overlay
    LaunchedEffect(uiState.completion) {
        uiState.completion?.let(currentOnGateCompleted)
    }

    OverlayScreen(
        uiState = uiState,
        onToggleShowAnswer = viewModel::onToggleShowAnswer,
        onDifficultySelect = viewModel::onDifficultySelected,
        onRetryNextCard = viewModel::retryNextCard,
    )
}

@Suppress("MagicNumber")
@VisibleForTesting
@Composable
internal fun OverlayScreen(
    uiState: OverlayUiState,
    onToggleShowAnswer: () -> Unit,
    onDifficultySelect: (Rating) -> Unit,
    onRetryNextCard: () -> Unit = {},
) {
    OverlayTheme {
        val backgroundGradient =
            Brush.verticalGradient(
                colors =
                    listOf(
                        OverlayThemeTokens.colors.backgroundGradientStart,
                        OverlayThemeTokens.colors.backgroundGradientEnd,
                    ),
            )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(backgroundGradient),
            contentAlignment = Alignment.Center,
        ) {
            if (uiState.requiredCards > 1) {
                Column(
                    modifier = Modifier.fillMaxSize().safeDrawingPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text =
                            stringResource(
                                R.string.overlay_gate_progress,
                                uiState.completedCards,
                                uiState.requiredCards,
                            ),
                        color = OverlayThemeTokens.colors.titleColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier =
                            Modifier
                                .padding(top = 8.dp, bottom = 8.dp)
                                .testTag("gate_card_progress"),
                    )
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        LearningCard(
                            state = uiState,
                            onToggleShowAnswer = onToggleShowAnswer,
                            onDifficultySelect = onDifficultySelect,
                            onRetryNextCard = onRetryNextCard,
                            ratingLockSecondsRemaining = uiState.ratingLockSecondsRemaining,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            } else {
                LearningCard(
                    state = uiState,
                    onToggleShowAnswer = onToggleShowAnswer,
                    onDifficultySelect = onDifficultySelect,
                    onRetryNextCard = onRetryNextCard,
                    ratingLockSecondsRemaining = uiState.ratingLockSecondsRemaining,
                    modifier = Modifier.safeDrawingPadding(),
                )
            }
        }
    }
}

private val sampleWord =
    VocabularyItem(
        id = 1L,
        word = "impetuous",
        translation = "пылкий; буйний",
        isNew = false,
    )

internal class OverlayUiStateProvider : PreviewParameterProvider<OverlayUiState> {
    override val values: Sequence<OverlayUiState> =
        sequenceOf(
            OverlayUiState(
                vocabularyItem = sampleWord,
                showAnswer = false,
                isLoading = false,
            ),
            OverlayUiState(
                vocabularyItem = sampleWord.copy(isNew = true),
                showAnswer = true,
                isLoading = false,
            ),
            OverlayUiState(
                vocabularyItem = sampleWord,
                showAnswer = true,
                isLoading = false,
                ratingDelaySeconds = 5,
                ratingLockSecondsRemaining = 3,
            ),
            OverlayUiState(
                vocabularyItem = null,
                showAnswer = false,
                isLoading = true,
            ),
        )
}

@Preview(
    name = "OverlayScreen • Light",
    showSystemUi = false,
    device = Devices.PIXEL_7,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_NO,
)
@Preview(
    name = "OverlayScreen • Dark",
    showSystemUi = false,
    device = Devices.PIXEL_7,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun OverlayScreenPreview(
    @PreviewParameter(OverlayUiStateProvider::class) state: OverlayUiState,
) {
    com.procrastilearn.app.ui.theme.MyApplicationTheme {
        OverlayScreen(
            uiState = state,
            onToggleShowAnswer = {},
            onDifficultySelect = {},
        )
    }
}
