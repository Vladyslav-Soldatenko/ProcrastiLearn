package com.example.myapplication.overlay


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.domain.model.VocabularyItem
import com.example.myapplication.domain.repository.VocabularyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OverlayViewModel @Inject constructor(
    private val vocabularyRepository: VocabularyRepository
) : ViewModel() {    private val _uiState = MutableStateFlow(OverlayUiState())
    val uiState: StateFlow<OverlayUiState> = _uiState.asStateFlow()

    init {
        loadNewWord()
    }

    fun onInputChanged(newValue: String) {
        _uiState.update { it.copy(input = newValue, error = false) }
    }

    fun onToggleShowAnswer() {
        _uiState.update { it.copy(showAnswer = !it.showAnswer) }
    }

    fun onSubmit() {
        viewModelScope.launch {
            val current = _uiState.value
            val isCorrect = current.input.trim().equals(
                current.vocabularyItem.translation,
                ignoreCase = true
            )

            if (isCorrect) {
                _uiState.update { it.copy(unlocked = true, error = false) }
            } else {
                _uiState.update { it.copy(error = true, errorTick = it.errorTick + 1) }
            }
        }
    }

    private fun loadNewWord() {
        // TODO: Load from repository later
        _uiState.update {
            it.copy(
                vocabularyItem = VocabularyItem("Cat", "кот"),
                isLoading = false
            )
        }
    }
}