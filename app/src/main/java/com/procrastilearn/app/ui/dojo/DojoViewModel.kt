package com.procrastilearn.app.ui.dojo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.procrastilearn.app.data.local.dao.UndoSnapshotDao
import com.procrastilearn.app.data.local.dao.VocabularyDao
import com.procrastilearn.app.data.local.prefs.DayCountersStore
import com.procrastilearn.app.data.repository.NoAvailableItemsException
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.domain.usecase.GetNextVocabularyItemUseCase
import com.procrastilearn.app.domain.usecase.SaveDifficultyRatingUseCase
import com.procrastilearn.app.domain.usecase.UndoLastRatingUseCase
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
        private val undoLastRating: UndoLastRatingUseCase,
        private val undoSnapshotDao: UndoSnapshotDao,
    ) : ViewModel() {
        private val flashcardState = MutableStateFlow(FlashcardState())
        private val undoEvent = MutableStateFlow<UndoEvent?>(null)

        // Item just restored by undo, pinned on screen until the user re-rates it.
        // Guards against the reactive re-fetch below (undo writes to both the vocabulary
        // table and the day counters, either of which can otherwise trigger a fetch that
        // would steal the restored card off the screen).
        private var pendingRestoredItem: VocabularyItem? = null

        // Reactive: re-emits whenever the vocabulary table changes anywhere in the app
        // (e.g. a review recorded from the blocking overlay), not just from this screen.
        private val reviewsDueCount = vocabularyDao.observeReviewsDueCount(System.currentTimeMillis())
        private val undoCount = undoSnapshotDao.observeCount()

        private val baseState =
            combine(
                flashcardState,
                dayCountersStore.read(),
                dayCountersStore.readPolicy(),
                reviewsDueCount,
            ) { flashcard, counters, policy, pendingReviews ->
                val newQuotaRemaining =
                    (policy.newPerDay + counters.extraNewToday - counters.newShown).coerceAtLeast(0)

                // Only show pending reviews if review quota available
                val reviewQuotaRemaining = (policy.reviewPerDay - counters.reviewShown).coerceAtLeast(0)
                val pendingReviewCount = if (reviewQuotaRemaining > 0) pendingReviews else 0

                BaseDojoState(flashcard, newQuotaRemaining, pendingReviewCount)
            }

        val uiState: StateFlow<DojoUiState> =
            combine(baseState, undoCount, undoEvent) { base, undoCountValue, event ->
                DojoUiState(
                    vocabularyItem = base.flashcard.vocabularyItem,
                    showAnswer = base.flashcard.showAnswer,
                    isLoading = base.flashcard.isLoading,
                    newQuotaRemaining = base.newQuotaRemaining,
                    pendingReviewCount = base.pendingReviewCount,
                    canUndo = undoCountValue > 0,
                    undoEvent = event,
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
                // Re-rating clears the pin: the next loadNextWord() should fetch for real.
                pendingRestoredItem = null
                // Reset showAnswer for next card
                flashcardState.value = flashcardState.value.copy(showAnswer = false)
                // Load next word; reviewsDueCount/counters will also react to the write
                // this rating just made, via the combine() in init{}.
                loadNextWord()
            }
        }

        fun onUndo() {
            viewModelScope.launch {
                val result = undoLastRating().getOrNull() ?: return@launch
                pendingRestoredItem = result.item
                flashcardState.value =
                    FlashcardState(
                        vocabularyItem = result.item,
                        isLoading = false,
                        showAnswer = true,
                    )
                undoEvent.value =
                    UndoEvent(
                        id = System.nanoTime(),
                        word = result.item.word,
                        revertedRating = result.revertedRating,
                    )
            }
        }

        fun onUndoEventShown() {
            undoEvent.value = null
        }

        private fun loadNextWord() {
            viewModelScope.launch {
                pendingRestoredItem?.let { restored ->
                    flashcardState.value =
                        FlashcardState(
                            vocabularyItem = restored,
                            isLoading = false,
                            showAnswer = true,
                        )
                    return@launch
                }

                flashcardState.value = flashcardState.value.copy(isLoading = true)
                getNextVocabularyItem()
                    .onSuccess { item ->
                        // A pin may have been set by onUndo() while this fetch was in
                        // flight; don't let a stale fetch clobber the restored card.
                        if (pendingRestoredItem != null) return@onSuccess
                        flashcardState.value =
                            FlashcardState(
                                vocabularyItem = item,
                                isLoading = false,
                                showAnswer = false,
                            )
                    }.onFailure { exception ->
                        if (pendingRestoredItem != null) return@onFailure
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

        private data class BaseDojoState(
            val flashcard: FlashcardState,
            val newQuotaRemaining: Int,
            val pendingReviewCount: Int,
        )
    }
