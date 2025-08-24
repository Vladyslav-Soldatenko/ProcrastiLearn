package com.example.myapplication.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.domain.usecase.AddVocabularyItemUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddWordViewModel
    @Inject
    constructor(
        private val addVocabularyItemUseCase: AddVocabularyItemUseCase,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(AddWordUiState())
        val uiState: StateFlow<AddWordUiState> = _uiState.asStateFlow()

        fun onWordChange(word: String) {
            _uiState.value =
                _uiState.value.copy(
                    word = word,
                    wordError = null,
                )
        }

        fun onTranslationChange(translation: String) {
            _uiState.value =
                _uiState.value.copy(
                    translation = translation,
                    translationError = null,
                )
        }

        fun onAddClick() {
            val currentState = _uiState.value

            // Validate inputs
            var hasError = false

            if (currentState.word.isBlank()) {
                _uiState.value = _uiState.value.copy(wordError = "Please enter a word")
                hasError = true
            }

            if (currentState.translation.isBlank()) {
                _uiState.value = _uiState.value.copy(translationError = "Please enter a translation")
                hasError = true
            }

            if (hasError) return

            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(isLoading = true)

                addVocabularyItemUseCase(
                    word = currentState.word,
                    translation = currentState.translation,
                ).fold(
                    onSuccess = {
                        _uiState.value =
                            AddWordUiState(
                                isSuccess = true,
                                successMessage = "Word added successfully!",
                            )
                    },
                    onFailure = { error ->
                        _uiState.value =
                            _uiState.value.copy(
                                isLoading = false,
                                errorMessage = error.message ?: "Failed to add word",
                            )
                    },
                )
            }
        }

        fun resetSuccess() {
            _uiState.value = AddWordUiState()
        }
    }

data class AddWordUiState(
    val word: String = "",
    val translation: String = "",
    val wordError: String? = null,
    val translationError: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false,
    val successMessage: String? = null,
)
