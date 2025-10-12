package com.procrastilearn.app.overlay

import android.content.res.Configuration
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.overlay.components.LearningCard
import com.procrastilearn.app.overlay.theme.OverlayTheme
import com.procrastilearn.app.overlay.theme.OverlayThemeTokens
import io.github.openspacedrepetition.Rating

@Composable
fun OverlayScreen(
    onUnlock: () -> Unit,
    viewModel: OverlayViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()

    // Initial load
    LaunchedEffect(Unit) {
        viewModel.onOverlayOpened()
    }

    // When unlocked, tell the service to remove the overlay
    LaunchedEffect(uiState.unlocked) {
        if (uiState.unlocked) onUnlock()
    }

    OverlayScreen(uiState, viewModel::onToggleShowAnswer, viewModel::onDifficultySelected)
}

@Suppress("MagicNumber")
@VisibleForTesting
@Composable
internal fun OverlayScreen(
    uiState: OverlayUiState,
    onToggleShowAnswer: () -> Unit,
    onDifficultySelected: (Rating) -> Unit,
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
            LearningCard(
                state = uiState,
                onToggleShowAnswer = onToggleShowAnswer,
                onDifficultySelected = onDifficultySelected,
            )
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

private class OverlayUiStateProvider : PreviewParameterProvider<OverlayUiState> {
    override val values: Sequence<OverlayUiState> =
        sequenceOf(
            OverlayUiState(
                vocabularyItem = sampleWord,
                showAnswer = false,
                unlocked = false,
                isLoading = false,
            ),
            OverlayUiState(
                vocabularyItem = sampleWord.copy(isNew = true),
                showAnswer = true,
                unlocked = false,
                isLoading = false,
            ),
            OverlayUiState(
                vocabularyItem = null,
                showAnswer = false,
                unlocked = false,
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
            onDifficultySelected = {},
        )
    }
}
