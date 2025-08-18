package com.example.myapplication.presentation.overlay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.domain.usecase.GetNextVocabularyItemUseCase
import com.example.myapplication.overlay.OverlayUiState
import com.example.myapplication.overlay.components.Difficulty
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OverlayViewModel @Inject constructor(
    private val getNextVocabularyItem: GetNextVocabularyItemUseCase,
    // You might want to add a use case to save difficulty ratings
//     private val saveDifficultyRating: SaveDifficultyRatingUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(OverlayUiState())
    val uiState: StateFlow<OverlayUiState> = _uiState.asStateFlow()

    init {
        loadNewWord()
    }

    fun onToggleShowAnswer() {
        _uiState.update { it.copy(showAnswer = !it.showAnswer) }
    }

    fun onDifficultySelected(difficulty: Difficulty) {
        // Log or save the difficulty rating for spaced repetition algorithm
        viewModelScope.launch {
            // Optional: Save the difficulty rating to database
            // saveDifficultyRating(uiState.value.vocabularyItem.id, difficulty)

            // Log for debugging
            println("Word: ${uiState.value.vocabularyItem.word}, Difficulty: $difficulty")

            // Unlock the overlay
            _uiState.update { it.copy(unlocked = true) }
        }
    }

    private fun loadNewWord() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            getNextVocabularyItem()
                .onSuccess { item ->
                    _uiState.update {
                        it.copy(
                            vocabularyItem = item,
                            isLoading = false,
                            showAnswer = false
                        )
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(isLoading = false)
                    }
                }
        }
    }
}