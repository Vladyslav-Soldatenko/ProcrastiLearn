package com.example.myapplication.overlay

import com.example.myapplication.overlay.components.LearningCard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.presentation.overlay.OverlayViewModel


@Composable
fun OverlayScreen(onUnlock: () -> Unit, viewModel: OverlayViewModel) {
    val uiState by viewModel.uiState.collectAsState()

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
            onInputChanged = viewModel::onInputChanged,
            onSubmit = viewModel::onSubmit,
            onToggleShowAnswer = viewModel::onToggleShowAnswer
        )
    }
}