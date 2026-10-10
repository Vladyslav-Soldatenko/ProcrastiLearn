package com.procrastilearn.app.overlay

import com.procrastilearn.app.domain.model.VocabularyItem

data class OverlayUiState(
    val vocabularyItem: VocabularyItem? = null,
    val showAnswer: Boolean = false,
    val completion: GateCompletion? = null,
    val isLoading: Boolean = false,
    val ratingDelaySeconds: Int = 0,
    val ratingLockSecondsRemaining: Int = 0,
    val requiredCards: Int = 1,
    val completedCards: Int = 0,
    val isSavingRating: Boolean = false,
    val hasRatingSaveError: Boolean = false,
    val hasNextCardLoadError: Boolean = false,
)

val OverlayUiState.unlocked: Boolean
    get() = completion != null
