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
        private val reviewCount = MutableStateFlow(0)

        val uiState: StateFlow<DojoUiState> =
            combine(
                flashcardState,
                dayCountersStore.read(),
                dayCountersStore.readPolicy(),
                reviewCount,
            ) { flashcard, counters, policy, pendingReviews ->
                // Calculate stats reactively
                val newQuotaRemaining = (policy.newPerDay - counters.newShown).coerceAtLeast(0)

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
            updateReviewCount()
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
                // Load next word
                loadNextWord()
                // Update review count
                updateReviewCount()
            }
        }

        private fun updateReviewCount() {
            viewModelScope.launch {
                reviewCount.value = vocabularyDao.countReviewsDue(System.currentTimeMillis())
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
