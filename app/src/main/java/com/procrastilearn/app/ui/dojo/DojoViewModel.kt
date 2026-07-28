package com.procrastilearn.app.ui.dojo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.procrastilearn.app.data.counter.DayCounters
import com.procrastilearn.app.data.local.dao.UndoSnapshotDao
import com.procrastilearn.app.data.local.dao.VocabularyDao
import com.procrastilearn.app.data.local.prefs.DayCountersStore
import com.procrastilearn.app.data.repository.NoAvailableItemsException
import com.procrastilearn.app.data.time.TimeTicker
import com.procrastilearn.app.domain.model.LearningPreferencesConfig
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.domain.model.includesBackward
import com.procrastilearn.app.domain.model.includesForward
import com.procrastilearn.app.domain.model.isBackwardOnly
import com.procrastilearn.app.domain.usecase.GetNextVocabularyItemUseCase
import com.procrastilearn.app.domain.usecase.SaveDifficultyRatingUseCase
import com.procrastilearn.app.domain.usecase.UndoLastRatingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.openspacedrepetition.Rating
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
@Suppress("LongParameterList")
class DojoViewModel
    @Inject
    constructor(
        private val getNextVocabularyItem: GetNextVocabularyItemUseCase,
        private val saveDifficultyRating: SaveDifficultyRatingUseCase,
        private val vocabularyDao: VocabularyDao,
        private val dayCountersStore: DayCountersStore,
        private val undoLastRating: UndoLastRatingUseCase,
        private val undoSnapshotDao: UndoSnapshotDao,
        private val timeTicker: TimeTicker,
    ) : ViewModel() {
        private val flashcardState = MutableStateFlow(FlashcardState())
        private val undoEvent = MutableStateFlow<UndoEvent?>(null)

        // Item just restored by undo, pinned on screen until the user re-rates it.
        // Guards against the reactive re-fetch below (undo writes to both the vocabulary
        // table and the day counters, either of which can otherwise trigger a fetch that
        // would steal the restored card off the screen).
        private var pendingRestoredItem: VocabularyItem? = null

        private val dueCountRefreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        private val nowSource = merge(timeTicker.nowTicks(), dueCountRefreshRequests.map { timeTicker.now() })

        // Due count and the backward-only skip count both depend on (now, policy) and are
        // combined into one flow so baseState's own combine() stays within the 5-flow
        // typed overload of kotlinx.coroutines' combine().
        private val reviewsDueAndSkippedCount =
            combine(nowSource, dayCountersStore.readPolicy()) { now, policy -> now to policy }
                .flatMapLatest { (now, policy) ->
                    val due =
                        vocabularyDao.observeReviewsDueCount(
                            now,
                            includeForward = policy.studyDirectionMode.includesForward,
                            includeBackward = policy.studyDirectionMode.includesBackward,
                        )
                    val skipped =
                        if (policy.studyDirectionMode.isBackwardOnly) {
                            vocabularyDao.observeBackwardOnlySkippedCount(now)
                        } else {
                            flowOf(0)
                        }
                    combine(due, skipped) { dueValue, skippedValue -> dueValue to skippedValue }
                }
                .distinctUntilChanged()

        private val newTotalCount =
            dayCountersStore.readPolicy().flatMapLatest { policy ->
                vocabularyDao.observeNewTotalCount(requireBidirectional = policy.studyDirectionMode.isBackwardOnly)
            }
        private val undoCount = undoSnapshotDao.observeCount()

        private val baseState =
            combine(
                flashcardState,
                dayCountersStore.read(),
                dayCountersStore.readPolicy(),
                reviewsDueAndSkippedCount,
                newTotalCount,
            ) { flashcard, counters, policy, dueAndSkipped, newTotal ->
                val (pendingReviews, skippedCount) = dueAndSkipped
                // Capped at newTotal: the quota can never claim more new cards exist
                // than are actually left unseen in the deck.
                val newQuotaRemaining =
                    (policy.newPerDay + counters.extraNewToday - counters.newShown)
                        .coerceIn(0, newTotal)

                // Only show pending reviews if review quota available
                val reviewQuotaRemaining = (policy.reviewPerDay - counters.reviewShown).coerceAtLeast(0)
                val pendingReviewCount = if (reviewQuotaRemaining > 0) pendingReviews else 0

                BaseDojoState(flashcard, newQuotaRemaining, pendingReviewCount, skippedCount)
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
                    skippedCardCount = base.skippedCardCount,
                )
            }.stateIn(viewModelScope, SharingStarted.Eagerly, DojoUiState(isLoading = true))

        init {
            loadNextWord()
            // The current flashcard is otherwise only refreshed from here and after a
            // local rating. If a due-count/quota/new-word change happens for any other
            // reason (a review from the overlay, a quota raised in Settings, a word
            // added from the Add Word screen), re-fetch so the card shown here can't go
            // stale or get stuck on an outdated empty state.
            viewModelScope.launch {
                combine(
                    reviewsDueAndSkippedCount,
                    dayCountersStore.read(),
                    dayCountersStore.readPolicy(),
                    newTotalCount,
                ) { due, counters, policy, newTotal -> DueCountersSnapshot(due, counters, policy, newTotal) }
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
                saveDifficultyRating(current.id, rating, current.direction)
                dueCountRefreshRequests.emit(Unit)
                pendingRestoredItem = null
                flashcardState.value = flashcardState.value.copy(showAnswer = false)
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
            val skippedCardCount: Int,
        )

        private data class DueCountersSnapshot(
            val dueAndSkipped: Pair<Int, Int>,
            val counters: DayCounters,
            val policy: LearningPreferencesConfig,
            val newTotal: Int,
        )
    }
