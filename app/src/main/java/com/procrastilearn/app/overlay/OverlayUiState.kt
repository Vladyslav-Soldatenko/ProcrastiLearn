package com.procrastilearn.app.overlay

import com.procrastilearn.app.domain.model.VocabularyItem

data class OverlayUiState(
    val vocabularyItem: VocabularyItem? = null,
    val showAnswer: Boolean = false,
    val unlocked: Boolean = false,
    val isLoading: Boolean = false,
)
