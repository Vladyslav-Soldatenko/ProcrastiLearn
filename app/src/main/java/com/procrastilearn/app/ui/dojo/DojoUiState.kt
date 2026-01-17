package com.procrastilearn.app.ui.dojo

import com.procrastilearn.app.domain.model.VocabularyItem

data class DojoUiState(
    // Flashcard state (compatible with OverlayUiState)
    val vocabularyItem: VocabularyItem? = null,
    val showAnswer: Boolean = false,
    val isLoading: Boolean = false,
    // Stats state
    val newQuotaRemaining: Int = 0,
    val pendingReviewCount: Int = 0,
) {
    val hasNoWords: Boolean get() = vocabularyItem == null && !isLoading
}
