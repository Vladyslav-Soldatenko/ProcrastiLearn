package com.example.myapplication.overlay

import com.example.myapplication.domain.model.VocabularyItem

data class OverlayUiState(
    val vocabularyItem: VocabularyItem = VocabularyItem(id=0, "Cat", "кот"),
    val input: String = "",
    val showAnswer: Boolean = false,
    val error: Boolean = false,
    val errorTick: Int = 0,
    val unlocked: Boolean = false,
    val isLoading: Boolean = false  // Added for better UX
) {
    val isInputEmpty: Boolean get() = input.isBlank()
}