package com.example.myapplication.overlay

import com.example.myapplication.domain.model.VocabularyItem

data class OverlayUiState(
    val vocabularyItem: VocabularyItem? = null,
    val showAnswer: Boolean = false,
    val unlocked: Boolean = false,
    val isLoading: Boolean = false,
)
