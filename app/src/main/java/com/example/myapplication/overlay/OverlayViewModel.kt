package com.example.myapplication.presentation.overlay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.domain.usecase.GetNextVocabularyItemUseCase
import com.example.myapplication.domain.usecase.ValidateTranslationUseCase
import com.example.myapplication.overlay.OverlayUiState
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
    private val validateTranslation: ValidateTranslationUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(OverlayUiState())
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
        val current = _uiState.value
        val result = validateTranslation(
            current.input,
            current.vocabularyItem.translation
        )

        when (result) {
            is ValidateTranslationUseCase.ValidationResult.Correct -> {
                _uiState.update { it.copy(unlocked = true, error = false) }
            }
            is ValidateTranslationUseCase.ValidationResult.Close -> {
                _uiState.update {
                    it.copy(
                        error = true,
                        errorTick = it.errorTick + 1,
                    )
                }
            }
            else -> {
                _uiState.update {
                    it.copy(
                        error = true,
                        errorTick = it.errorTick + 1,
                    )
                }
            }
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
                            input = "",
                            showAnswer = false,
                            error = false
                        )
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = true
                        )
                    }
                }
        }
    }
}