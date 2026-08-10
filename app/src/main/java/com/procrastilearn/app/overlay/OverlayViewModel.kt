package com.procrastilearn.app.overlay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.procrastilearn.app.data.local.prefs.DayCountersStore
import com.procrastilearn.app.data.repository.NoAvailableItemsException
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.domain.usecase.GetNextVocabularyItemUseCase
import com.procrastilearn.app.domain.usecase.SaveDifficultyRatingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.openspacedrepetition.Rating
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val LOCK_TICK_MS = 1_000L

@HiltViewModel
class OverlayViewModel
    @Inject
    constructor(
        private val getNextVocabularyItem: GetNextVocabularyItemUseCase,
        private val saveDifficultyRating: SaveDifficultyRatingUseCase,
        private val dayCountersStore: DayCountersStore,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(OverlayUiState())
        val uiState: StateFlow<OverlayUiState> = _uiState.asStateFlow()

        private var lockJob: Job? = null

        fun onOverlayOpened() {
            // Call this when overlay is opened/shown
            loadRatingDelay()
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
            lockJob?.cancel()
            val showAnswer = !_uiState.value.showAnswer
            _uiState.update {
                it.copy(
                    showAnswer = showAnswer,
                    ratingLockSecondsRemaining = if (showAnswer) it.ratingDelaySeconds else 0,
                )
            }
            if (showAnswer && _uiState.value.ratingLockSecondsRemaining > 0) {
                startRatingLockCountdown()
            }
        }

        fun onDifficultySelected(rating: Rating) {
            val current = _uiState.value
            if (current.vocabularyItem == null) {
                throw NoSuchElementException("current word is null")
            }
            if (current.ratingLockSecondsRemaining > 0) return

            viewModelScope.launch {
                saveDifficultyRating(current.vocabularyItem.id, rating, current.vocabularyItem.direction)
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
            lockJob?.cancel()
            _uiState.update {
                it.copy(
                    unlocked = false,
                    showAnswer = false,
                    ratingLockSecondsRemaining = 0,
                )
            }
        }

        private fun loadRatingDelay() {
            viewModelScope.launch {
                val delaySeconds =
                    runCatching { dayCountersStore.readPolicy().first().ratingDelaySeconds }
                        .getOrDefault(0)
                _uiState.update { it.copy(ratingDelaySeconds = delaySeconds) }
            }
        }

        private fun startRatingLockCountdown() {
            lockJob =
                viewModelScope.launch {
                    while (_uiState.value.ratingLockSecondsRemaining > 0) {
                        delay(LOCK_TICK_MS)
                        _uiState.update {
                            it.copy(ratingLockSecondsRemaining = (it.ratingLockSecondsRemaining - 1).coerceAtLeast(0))
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
