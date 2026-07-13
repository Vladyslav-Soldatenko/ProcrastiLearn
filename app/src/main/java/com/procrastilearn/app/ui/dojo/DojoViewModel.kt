package com.procrastilearn.app.ui.dojo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.procrastilearn.app.data.local.dao.VocabularyDao
import com.procrastilearn.app.data.local.prefs.DayCountersStore
import com.procrastilearn.app.data.repository.NoAvailableItemsException
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.domain.usecase.GetNextVocabularyItemUseCase
import com.procrastilearn.app.domain.usecase.SaveDifficultyRatingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.openspacedrepetition.Rating
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DojoViewModel
    @Inject
    constructor(
        private val getNextVocabularyItem: GetNextVocabularyItemUseCase,
        private val saveDifficultyRating: SaveDifficultyRatingUseCase,
        private val vocabularyDao: VocabularyDao,
        private val dayCountersStore: DayCountersStore,
    ) : ViewModel() {
        private val flashcardState = MutableStateFlow(FlashcardState())

        // Reactive: re-emits whenever the vocabulary table changes anywhere in the app
        // (e.g. a review recorded from the blocking overlay), not just from this screen.
        private val reviewsDueCount = vocabularyDao.observeReviewsDueCount(System.currentTimeMillis())

        val uiState: StateFlow<DojoUiState> =
            combine(
                flashcardState,
                dayCountersStore.read(),
                dayCountersStore.readPolicy(),
                reviewsDueCount,
            ) { flashcard, counters, policy, pendingReviews ->
                // Calculate stats reactively
                val newQuotaRemaining =
                    (policy.newPerDay + counters.extraNewToday - counters.newShown).coerceAtLeast(0)

                // Only show pending reviews if review quota available
                val reviewQuotaRemaining = (policy.reviewPerDay - counters.reviewShown).coerceAtLeast(0)
                val pendingReviewCount = if (reviewQuotaRemaining > 0) pendingReviews else 0

                DojoUiState(
                    vocabularyItem = flashcard.vocabularyItem,
                    showAnswer = flashcard.showAnswer,
                    isLoading = flashcard.isLoading,
                    newQuotaRemaining = newQuotaRemaining,
                    pendingReviewCount = pendingReviewCount,
                )
            }.stateIn(viewModelScope, SharingStarted.Eagerly, DojoUiState(isLoading = true))

        init {
            loadNextWord()
            // The current flashcard is otherwise only refreshed from here and after a
            // local rating. If a due-count/quota change happens for any other reason
            // (a review from the overlay, a quota raised in Settings), re-fetch so the
            // card shown here can't go stale or get stuck on an outdated empty state.
            viewModelScope.launch {
                combine(
                    reviewsDueCount,
                    dayCountersStore.read(),
                    dayCountersStore.readPolicy(),
                ) { due, counters, policy -> Triple(due, counters, policy) }
                    .drop(1)
                    .collect { loadNextWord() }
            }
        }

        fun onToggleShowAnswer() {
            flashcardState.value = flashcardState.value.copy(showAnswer = !flashcardState.value.showAnswer)
        }

        fun onDifficultySelected(rating: Rating) {
            val current = flashcardState.value.vocabularyItem
            if (current == null) {
                throw NoSuchElementException("current word is null")
            }

            viewModelScope.launch {
                saveDifficultyRating(current.id, rating)
                // Reset showAnswer for next card
                flashcardState.value = flashcardState.value.copy(showAnswer = false)
                // Load next word; reviewsDueCount/counters will also react to the write
                // this rating just made, via the combine() in init{}.
                loadNextWord()
            }
        }

        private fun loadNextWord() {
            viewModelScope.launch {
                flashcardState.value = flashcardState.value.copy(isLoading = true)
                getNextVocabularyItem()
                    .onSuccess { item ->
                        flashcardState.value =
                            FlashcardState(
                                vocabularyItem = item,
                                isLoading = false,
                                showAnswer = false,
                            )
                    }.onFailure { exception ->
                        when (exception) {
                            is NoAvailableItemsException -> {
                                // No words available - empty state
                                flashcardState.value =
                                    FlashcardState(
                                        vocabularyItem = null,
                                        isLoading = false,
                                        showAnswer = false,
                                    )
                            }
                            else -> {
                                flashcardState.value = flashcardState.value.copy(isLoading = false)
                            }
                        }
                    }
            }
        }

        private data class FlashcardState(
            val vocabularyItem: VocabularyItem? = null,
            val showAnswer: Boolean = false,
            val isLoading: Boolean = false,
        )
    }
