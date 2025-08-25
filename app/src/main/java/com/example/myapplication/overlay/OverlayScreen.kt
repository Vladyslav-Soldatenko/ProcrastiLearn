package com.example.myapplication.overlay

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.example.myapplication.domain.model.VocabularyItem
import com.example.myapplication.overlay.components.LearningCard
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
@Composable
private fun OverlayScreen(
    uiState: OverlayUiState,
    onToggleShowAnswer: () -> Unit,
    onDifficultySelected: (Rating) -> Unit,
) {
    val backgroundGradient =
        Brush.verticalGradient(
            colors = listOf(Color(0xFF0F172A), Color(0xFF111827)),
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

private val sampleWord =
    VocabularyItem(
        id = 1L,
        word = "impetuous",
        translation = "пылкий; буйний",
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
                vocabularyItem = sampleWord,
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
    name = "OverlayScreen states",
    showSystemUi = false,
    device = Devices.PIXEL_7,
    backgroundColor = 0xFF0F172A,
    showBackground = true,
)
@Composable
private fun OverlayScreenPreview(
    @PreviewParameter(OverlayUiStateProvider::class) state: OverlayUiState,
) {
    OverlayScreen(
        uiState = state,
        onToggleShowAnswer = {},
        onDifficultySelected = {},
    )
}
