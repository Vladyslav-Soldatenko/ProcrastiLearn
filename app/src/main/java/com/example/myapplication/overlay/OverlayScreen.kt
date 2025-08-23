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
import com.example.myapplication.overlay.components.LearningCard


@Composable
fun OverlayScreen(onUnlock: () -> Unit, viewModel: OverlayViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    // Initial load
    LaunchedEffect(Unit) {
        viewModel.onOverlayOpened()
    }

    // When unlocked, tell the service to remove the overlay
    LaunchedEffect(uiState.unlocked) {
        if (uiState.unlocked) onUnlock()
    }

    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF0F172A), Color(0xFF111827))
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient),
        contentAlignment = Alignment.Center
    ) {
        LearningCard(
            state = uiState,
            onToggleShowAnswer = viewModel::onToggleShowAnswer,
            onDifficultySelected = viewModel::onDifficultySelected
        )
    }
}