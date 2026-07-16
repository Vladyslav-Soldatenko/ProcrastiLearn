package com.procrastilearn.app.ui.dojo

import com.procrastilearn.app.domain.model.VocabularyItem
import io.github.openspacedrepetition.Rating

data class DojoUiState(
    // Flashcard state (compatible with OverlayUiState)
    val vocabularyItem: VocabularyItem? = null,
    val showAnswer: Boolean = false,
    val isLoading: Boolean = false,
    // Stats state
    val newQuotaRemaining: Int = 0,
    val pendingReviewCount: Int = 0,
    // Undo state
    val canUndo: Boolean = false,
    val undoEvent: UndoEvent? = null,
) {
    val hasNoWords: Boolean get() = vocabularyItem == null && !isLoading
}

data class UndoEvent(
    val id: Long,
    val word: String,
    val revertedRating: Rating,
)
