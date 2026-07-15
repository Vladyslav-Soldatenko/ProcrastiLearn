package com.procrastilearn.app.overlay

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.procrastilearn.app.data.local.prefs.PronunciationPreferencesStore
import com.procrastilearn.app.data.repository.NoAvailableItemsException
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.domain.usecase.GetNextVocabularyItemUseCase
import com.procrastilearn.app.domain.usecase.SaveDifficultyRatingUseCase
import com.procrastilearn.app.tts.Speaker
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.openspacedrepetition.Rating
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class OverlayViewModel
    @Inject
    constructor(
        private val getNextVocabularyItem: GetNextVocabularyItemUseCase,
        private val saveDifficultyRating: SaveDifficultyRatingUseCase,
        private val pronunciationPreferencesStore: PronunciationPreferencesStore,
        private val speaker: Speaker,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(OverlayUiState())
        val uiState: StateFlow<OverlayUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                pronunciationPreferencesStore.readEnabled().collect { enabled ->
                    _uiState.update { it.copy(pronunciationEnabled = enabled) }
                }
            }
        }

        fun speakCurrentWord() {
            val word = _uiState.value.vocabularyItem?.word ?: return
            speaker.speak(word, Locale.ENGLISH)
        }

        fun onOverlayOpened() {
            // Call this when overlay is opened/shown
            val state = _uiState.value
            if (state.unlocked || state.isLoading || state.vocabularyItem != null) return
            loadNewWord()
        }

        /**
         * Seed the first word synchronously, before the overlay's first composition.
         *
         * The word is loaded by the service *before* the ComposeView is attached to the
         * window, so the very first frame already shows it. This avoids drawing an empty
         * "No word loaded" frame and relying on a later async update to repaint — which,
         * in a Service-hosted ComposeView, would not flush to screen until a touch event.
         */
        fun seedInitialWord(item: VocabularyItem) {
            _uiState.update {
                it.copy(
                    vocabularyItem = item,
                    isLoading = false,
                    showAnswer = false,
                    unlocked = false,
                )
            }
        }

        fun onToggleShowAnswer() {
            _uiState.update { it.copy(showAnswer = !it.showAnswer) }
        }

        fun onDifficultySelected(rating: Rating) {
            val current = _uiState.value.vocabularyItem
            if (current == null) {
                throw NoSuchElementException("current word is null")
            }

            Log.i("fsrs", "$rating selected for $current ")

            viewModelScope.launch {
                saveDifficultyRating(current.id, rating)
                _uiState.update {
                    it.copy(
                        unlocked = true,
                        showAnswer = false, // Reset for next time
                    )
                }
            }
        }

        fun resetForNextSession() {
            // Call this when overlay is dismissed/closed
            _uiState.update {
                it.copy(
                    unlocked = false,
                    showAnswer = false,
                )
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
                                showAnswer = false,
                                unlocked = false,
                            )
                        }
                    }.onFailure { exception ->
                        when (exception) {
                            is NoAvailableItemsException -> {
                                // Handle the case where limits are reached
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        unlocked = true,
                                    )
                                }
                            }
                            else -> {
                                _uiState.update {
                                    it.copy(isLoading = false)
                                }
                            }
                        }
                    }
            }
        }
    }
