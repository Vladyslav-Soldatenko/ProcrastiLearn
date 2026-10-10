package com.procrastilearn.app.overlay

import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.data.local.prefs.DayCountersStore
import com.procrastilearn.app.data.repository.NoAvailableItemsException
import com.procrastilearn.app.domain.model.LearningPreferencesConfig
import com.procrastilearn.app.domain.model.StudyDirection
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.domain.usecase.GetNextVocabularyItemUseCase
import com.procrastilearn.app.domain.usecase.SaveDifficultyRatingUseCase
import com.procrastilearn.app.utils.MainDispatcherRule
import io.github.openspacedrepetition.Rating
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OverlayViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var getNextVocabularyItem: GetNextVocabularyItemUseCase
    private lateinit var saveDifficultyRating: SaveDifficultyRatingUseCase
    private lateinit var dayCountersStore: DayCountersStore
    private lateinit var policyFlow: MutableStateFlow<LearningPreferencesConfig>

    @Before
    fun setUp() {
        getNextVocabularyItem = mockk()
        saveDifficultyRating = mockk()
        dayCountersStore = mockk()
        policyFlow = MutableStateFlow(LearningPreferencesConfig(ratingDelaySeconds = 0))
        every { dayCountersStore.readPolicy() } returns policyFlow
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    private fun buildViewModel(): OverlayViewModel =
        OverlayViewModel(getNextVocabularyItem, saveDifficultyRating, dayCountersStore)

    private suspend fun TestScope.completeGate(requiredCards: Int) {
        val item = VocabularyItem(id = 20, word = "Wort", translation = "word", isNew = true)
        if (requiredCards > 1) {
            coEvery { getNextVocabularyItem.invoke() } returnsMany
                List(requiredCards - 1) { Result.success(item) }
        }
        coEvery { saveDifficultyRating.invoke(any(), any(), any()) } returns Result.success(Unit)

        val viewModel = buildViewModel()
        viewModel.seedInitialWord(item, requiredCards)
        viewModel.onOverlayOpened()
        advanceUntilIdle()

        repeat(requiredCards) { index ->
            viewModel.onDifficultySelected(Rating.GOOD)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.completedCards).isEqualTo(index + 1)
            assertThat(viewModel.uiState.value.unlocked).isEqualTo(index + 1 == requiredCards)
        }

        coVerify(exactly = requiredCards) { saveDifficultyRating.invoke(any(), any(), any()) }
    }

    @Test
    fun `onOverlayOpened loads next item and resets reveal state`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item = VocabularyItem(id = 42, word = "Haus", translation = "House", isNew = true)
            coEvery { getNextVocabularyItem.invoke() } returns Result.success(item)

            val viewModel = buildViewModel()
            viewModel.onToggleShowAnswer()

            viewModel.onOverlayOpened()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.vocabularyItem).isEqualTo(item)
            assertThat(state.showAnswer).isFalse()
            assertThat(state.unlocked).isFalse()
            assertThat(state.isLoading).isFalse()
            coVerify(exactly = 1) { getNextVocabularyItem.invoke() }
        }

    @Test
    fun `onOverlayOpened does not reload when session already unlocked`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item = VocabularyItem(id = 1, word = "Strasse", translation = "Street", isNew = false)
            coEvery { getNextVocabularyItem.invoke() } returns Result.success(item)
            coEvery { saveDifficultyRating.invoke(any(), any()) } returns Result.success(Unit)

            val viewModel = buildViewModel()
            viewModel.onOverlayOpened()
            advanceUntilIdle()

            viewModel.onDifficultySelected(Rating.GOOD)
            advanceUntilIdle()

            viewModel.onOverlayOpened()
            advanceUntilIdle()

            coVerify(exactly = 1) { getNextVocabularyItem.invoke() }
            coVerify(exactly = 1) { saveDifficultyRating.invoke(item.id, Rating.GOOD) }
            assertThat(viewModel.uiState.value.unlocked).isTrue()
        }

    @Test
    fun `onOverlayOpened sets unlocked when daily limits reached`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { getNextVocabularyItem.invoke() } returns Result.failure(NoAvailableItemsException())

            val viewModel = buildViewModel()

            viewModel.onOverlayOpened()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.vocabularyItem).isNull()
            assertThat(state.unlocked).isTrue()
            assertThat(state.completion)
                .isEqualTo(GateCompletion(0))
            assertThat(state.isLoading).isFalse()
            coVerify(exactly = 1) { getNextVocabularyItem.invoke() }
        }

    @Test
    fun `onOverlayOpened stops loading when unexpected error occurs`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { getNextVocabularyItem.invoke() } returns Result.failure(IllegalStateException("boom"))

            val viewModel = buildViewModel()

            viewModel.onOverlayOpened()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.vocabularyItem).isNull()
            assertThat(state.unlocked).isFalse()
            assertThat(state.isLoading).isFalse()
            coVerify(exactly = 1) { getNextVocabularyItem.invoke() }
        }

    @Test
    fun `onDifficultySelected throws when no active item`() {
        val viewModel = buildViewModel()

        assertThrows(NoSuchElementException::class.java) {
            viewModel.onDifficultySelected(Rating.HARD)
        }
        coVerify(exactly = 0) { saveDifficultyRating.invoke(any(), any()) }
    }

    @Test
    fun `onDifficultySelected saves rating and locks overlay`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item = VocabularyItem(id = 7, word = "lernen", translation = "learn", isNew = true)
            coEvery { getNextVocabularyItem.invoke() } returns Result.success(item)
            coEvery { saveDifficultyRating.invoke(any(), any()) } returns Result.success(Unit)

            val viewModel = buildViewModel()
            viewModel.onOverlayOpened()
            advanceUntilIdle()
            viewModel.onToggleShowAnswer()

            viewModel.onDifficultySelected(Rating.EASY)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.unlocked).isTrue()
            assertThat(state.showAnswer).isFalse()
            assertThat(state.vocabularyItem).isEqualTo(item)
            coVerify(exactly = 1) { saveDifficultyRating.invoke(item.id, Rating.EASY) }
        }

    @Test
    fun `failed rating save keeps the gate locked and the current item available`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item = VocabularyItem(id = 12, word = "lernen", translation = "learn", isNew = true)
            coEvery { saveDifficultyRating.invoke(any(), any(), any()) } returnsMany
                listOf(Result.failure(IllegalStateException("save failed")), Result.success(Unit))

            val viewModel = buildViewModel()
            viewModel.seedInitialWord(item, requiredCards = 1)
            viewModel.onOverlayOpened()
            advanceUntilIdle()

            viewModel.onDifficultySelected(Rating.GOOD)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.unlocked).isFalse()
            assertThat(viewModel.uiState.value.vocabularyItem).isEqualTo(item)
            assertThat(viewModel.uiState.value.completedCards).isEqualTo(0)
            assertThat(viewModel.uiState.value.hasRatingSaveError).isTrue()

            viewModel.onDifficultySelected(Rating.GOOD)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.unlocked).isTrue()
            assertThat(viewModel.uiState.value.completedCards).isEqualTo(1)
            assertThat(viewModel.uiState.value.hasRatingSaveError).isFalse()
        }

    @Test
    fun `failed rating save after progress preserves the current card and count and can be retried`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val first = VocabularyItem(id = 26, word = "erste", translation = "first", isNew = true)
            val second = VocabularyItem(id = 27, word = "zweite", translation = "second", isNew = true)
            val third = VocabularyItem(id = 28, word = "dritte", translation = "third", isNew = true)
            coEvery { getNextVocabularyItem.invoke() } returnsMany listOf(Result.success(second), Result.success(third))
            coEvery { saveDifficultyRating.invoke(any(), any(), any()) } returnsMany
                listOf(
                    Result.success(Unit),
                    Result.failure(IllegalStateException("save failed")),
                    Result.success(Unit),
                )

            val viewModel = buildViewModel()
            viewModel.seedInitialWord(first, requiredCards = 3)
            viewModel.onOverlayOpened()
            advanceUntilIdle()

            viewModel.onDifficultySelected(Rating.GOOD)
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.completedCards).isEqualTo(1)
            assertThat(viewModel.uiState.value.vocabularyItem).isEqualTo(second)

            viewModel.onDifficultySelected(Rating.HARD)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.completedCards).isEqualTo(1)
            assertThat(viewModel.uiState.value.vocabularyItem).isEqualTo(second)
            assertThat(viewModel.uiState.value.unlocked).isFalse()
            assertThat(viewModel.uiState.value.hasRatingSaveError).isTrue()

            viewModel.onDifficultySelected(Rating.HARD)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.completedCards).isEqualTo(2)
            assertThat(viewModel.uiState.value.vocabularyItem).isEqualTo(third)
            assertThat(viewModel.uiState.value.unlocked).isFalse()
            assertThat(viewModel.uiState.value.hasRatingSaveError).isFalse()
            coVerify(exactly = 1) { saveDifficultyRating.invoke(first.id, Rating.GOOD, first.direction) }
            coVerify(exactly = 2) { saveDifficultyRating.invoke(second.id, Rating.HARD, second.direction) }
        }

    @Test
    fun `target of one unlocks after exactly one successful rating`() =
        runTest(mainDispatcherRule.testDispatcher) {
            completeGate(requiredCards = 1)
        }

    @Test
    fun `rating after gate unlock does not save or count another card`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item = VocabularyItem(id = 23, word = "fertig", translation = "done", isNew = true)
            coEvery { saveDifficultyRating.invoke(any(), any(), any()) } returns Result.success(Unit)

            val viewModel = buildViewModel()
            viewModel.seedInitialWord(item, requiredCards = 1)
            viewModel.onOverlayOpened()
            advanceUntilIdle()

            viewModel.onDifficultySelected(Rating.GOOD)
            advanceUntilIdle()
            viewModel.onDifficultySelected(Rating.AGAIN)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.unlocked).isTrue()
            assertThat(viewModel.uiState.value.completedCards).isEqualTo(1)
            coVerify(exactly = 1) { saveDifficultyRating.invoke(item.id, Rating.GOOD, item.direction) }
        }

    @Test
    fun `target of three unlocks after exactly three successful ratings`() =
        runTest(mainDispatcherRule.testDispatcher) {
            completeGate(requiredCards = 3)
        }

    @Test
    fun `target of one hundred unlocks after exactly one hundred successful ratings`() =
        runTest(mainDispatcherRule.testDispatcher) {
            completeGate(requiredCards = 100)
        }

    @Test
    fun `rating the same card again advances progress`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item = VocabularyItem(id = 13, word = "wieder", translation = "again", isNew = false)
            coEvery { getNextVocabularyItem.invoke() } returns Result.success(item)
            coEvery { saveDifficultyRating.invoke(any(), any(), any()) } returns Result.success(Unit)

            val viewModel = buildViewModel()
            viewModel.seedInitialWord(item, requiredCards = 3)
            viewModel.onOverlayOpened()
            advanceUntilIdle()

            repeat(2) {
                viewModel.onDifficultySelected(Rating.GOOD)
                advanceUntilIdle()
            }

            assertThat(viewModel.uiState.value.vocabularyItem).isEqualTo(item)
            assertThat(viewModel.uiState.value.completedCards).isEqualTo(2)
            assertThat(viewModel.uiState.value.unlocked).isFalse()
        }

    @Test
    fun `duplicate rating taps while a save is pending create one save`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item = VocabularyItem(id = 14, word = "doppelt", translation = "duplicate", isNew = true)
            val nextItem = VocabularyItem(id = 15, word = "nächste", translation = "next", isNew = true)
            val saveResult = CompletableDeferred<Result<Unit>>()
            coEvery { getNextVocabularyItem.invoke() } returns Result.success(nextItem)
            coEvery { saveDifficultyRating.invoke(any(), any(), any()) } coAnswers { saveResult.await() }

            val viewModel = buildViewModel()
            viewModel.seedInitialWord(item, requiredCards = 2)
            viewModel.onOverlayOpened()
            advanceUntilIdle()

            viewModel.onDifficultySelected(Rating.GOOD)
            viewModel.onDifficultySelected(Rating.GOOD)
            runCurrent()

            assertThat(viewModel.uiState.value.isSavingRating).isTrue()
            coVerify(exactly = 1) { saveDifficultyRating.invoke(item.id, Rating.GOOD, item.direction) }

            saveResult.complete(Result.success(Unit))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.completedCards).isEqualTo(1)
            assertThat(viewModel.uiState.value.isSavingRating).isFalse()
            coVerify(exactly = 1) { saveDifficultyRating.invoke(item.id, Rating.GOOD, item.direction) }
        }

    @Test
    fun `rating cannot be repeated before the next card load starts`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val firstItem = VocabularyItem(id = 24, word = "erste", translation = "first", isNew = true)
            val nextItem = VocabularyItem(id = 25, word = "zweite", translation = "next", isNew = true)
            val viewModel = buildViewModel()
            var nextCardLoadingObserved = false
            var secondTapObserved = false
            val stateObserver =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.collect { state ->
                        if (state.completedCards == 1 && state.isLoading) nextCardLoadingObserved = true
                        val staleCardCouldBeRated =
                            state.completedCards == 1 &&
                                !state.isSavingRating &&
                                !state.isLoading &&
                                !state.unlocked &&
                                !nextCardLoadingObserved
                        if (staleCardCouldBeRated) {
                            viewModel.onDifficultySelected(Rating.AGAIN)
                            secondTapObserved = true
                        }
                    }
                }
            coEvery { getNextVocabularyItem.invoke() } returns Result.success(nextItem)
            coEvery { saveDifficultyRating.invoke(any(), any(), any()) } returns Result.success(Unit)
            viewModel.seedInitialWord(firstItem, requiredCards = 2)
            viewModel.onOverlayOpened()
            advanceUntilIdle()

            viewModel.onDifficultySelected(Rating.GOOD)
            advanceUntilIdle()

            assertThat(nextCardLoadingObserved).isTrue()
            assertThat(secondTapObserved).isFalse()
            assertThat(viewModel.uiState.value.completedCards).isEqualTo(1)
            assertThat(viewModel.uiState.value.unlocked).isFalse()
            coVerify(exactly = 1) { saveDifficultyRating.invoke(firstItem.id, any(), firstItem.direction) }
            stateObserver.cancel()
        }

    @Test
    fun `next card hides the answer and restores its rating delay`() =
        runTest(mainDispatcherRule.testDispatcher) {
            policyFlow.value = LearningPreferencesConfig(ratingDelaySeconds = 5)
            val first = VocabularyItem(id = 16, word = "erste", translation = "first", isNew = true)
            val second = VocabularyItem(id = 17, word = "zweite", translation = "second", isNew = true)
            coEvery { getNextVocabularyItem.invoke() } returns Result.success(second)
            coEvery { saveDifficultyRating.invoke(any(), any(), any()) } returns Result.success(Unit)

            val viewModel = buildViewModel()
            viewModel.seedInitialWord(first, requiredCards = 2)
            viewModel.onOverlayOpened()
            advanceUntilIdle()
            viewModel.onToggleShowAnswer()
            advanceTimeBy(5_000)
            advanceUntilIdle()

            viewModel.onDifficultySelected(Rating.GOOD)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.vocabularyItem).isEqualTo(second)
            assertThat(viewModel.uiState.value.showAnswer).isFalse()
            assertThat(viewModel.uiState.value.ratingDelaySeconds).isEqualTo(5)
            assertThat(viewModel.uiState.value.ratingLockSecondsRemaining).isEqualTo(0)

            viewModel.onToggleShowAnswer()

            assertThat(viewModel.uiState.value.ratingLockSecondsRemaining).isEqualTo(5)
        }

    @Test
    fun `no available next card unlocks with partial progress`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item = VocabularyItem(id = 18, word = "teilweise", translation = "partial", isNew = true)
            coEvery { getNextVocabularyItem.invoke() } returns Result.failure(NoAvailableItemsException())
            coEvery { saveDifficultyRating.invoke(any(), any(), any()) } returns Result.success(Unit)

            val viewModel = buildViewModel()
            viewModel.seedInitialWord(item, requiredCards = 3)
            viewModel.onOverlayOpened()
            advanceUntilIdle()

            viewModel.onDifficultySelected(Rating.GOOD)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.completedCards).isEqualTo(1)
            assertThat(viewModel.uiState.value.unlocked).isTrue()
            assertThat(viewModel.uiState.value.vocabularyItem).isEqualTo(item)
        }

    @Test
    fun `transient next card load failure remains locked and can be retried`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item = VocabularyItem(id = 19, word = "alt", translation = "old", isNew = true)
            val nextItem = VocabularyItem(id = 20, word = "neu", translation = "new", isNew = true)
            coEvery { getNextVocabularyItem.invoke() } returnsMany
                listOf(Result.failure(IllegalStateException("temporary")), Result.success(nextItem))
            coEvery { saveDifficultyRating.invoke(any(), any(), any()) } returns Result.success(Unit)

            val viewModel = buildViewModel()
            viewModel.seedInitialWord(item, requiredCards = 3)
            viewModel.onOverlayOpened()
            advanceUntilIdle()

            viewModel.onDifficultySelected(Rating.GOOD)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.unlocked).isFalse()
            assertThat(viewModel.uiState.value.completedCards).isEqualTo(1)
            assertThat(viewModel.uiState.value.hasNextCardLoadError).isTrue()

            viewModel.retryNextCard()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.vocabularyItem).isEqualTo(nextItem)
            assertThat(viewModel.uiState.value.hasNextCardLoadError).isFalse()
            assertThat(viewModel.uiState.value.completedCards).isEqualTo(1)
            coVerify(exactly = 2) { getNextVocabularyItem.invoke() }
        }

    @Test
    fun `seeding a new gate resets progress and updates its target`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val first = VocabularyItem(id = 21, word = "alt", translation = "old", isNew = true)
            val second = VocabularyItem(id = 22, word = "neu", translation = "new", isNew = true)
            coEvery { getNextVocabularyItem.invoke() } returns Result.success(second)
            coEvery { saveDifficultyRating.invoke(any(), any(), any()) } returns Result.success(Unit)

            val viewModel = buildViewModel()
            viewModel.seedInitialWord(first, requiredCards = 3)
            viewModel.onOverlayOpened()
            advanceUntilIdle()
            viewModel.onDifficultySelected(Rating.GOOD)
            advanceUntilIdle()

            viewModel.seedInitialWord(second, requiredCards = 4)

            assertThat(viewModel.uiState.value.vocabularyItem).isEqualTo(second)
            assertThat(viewModel.uiState.value.requiredCards).isEqualTo(4)
            assertThat(viewModel.uiState.value.completedCards).isEqualTo(0)
            assertThat(viewModel.uiState.value.unlocked).isFalse()
        }

    @Test
    fun `onDifficultySelected passes the current item's direction to saveDifficultyRating`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item =
                VocabularyItem(
                    id = 7,
                    word = "бігати",
                    translation = "run",
                    isNew = false,
                    direction = StudyDirection.BACKWARD,
                )
            coEvery { getNextVocabularyItem.invoke() } returns Result.success(item)
            coEvery { saveDifficultyRating.invoke(any(), any(), any()) } returns Result.success(Unit)

            val viewModel = buildViewModel()
            viewModel.onOverlayOpened()
            advanceUntilIdle()

            viewModel.onDifficultySelected(Rating.GOOD)
            advanceUntilIdle()

            coVerify(exactly = 1) { saveDifficultyRating.invoke(item.id, Rating.GOOD, StudyDirection.BACKWARD) }
        }

    @Test
    fun `onToggleShowAnswer flips answer visibility`() {
        val viewModel = buildViewModel()

        assertThat(viewModel.uiState.value.showAnswer).isFalse()

        viewModel.onToggleShowAnswer()
        assertThat(viewModel.uiState.value.showAnswer).isTrue()

        viewModel.onToggleShowAnswer()
        assertThat(viewModel.uiState.value.showAnswer).isFalse()
    }

    @Test
    fun `resetForNextSession hides answer and locks overlay`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item = VocabularyItem(id = 3, word = "lesen", translation = "read", isNew = false)
            coEvery { getNextVocabularyItem.invoke() } returns Result.success(item)
            coEvery { saveDifficultyRating.invoke(any(), any()) } returns Result.success(Unit)

            val viewModel = buildViewModel()
            viewModel.onOverlayOpened()
            advanceUntilIdle()
            viewModel.onDifficultySelected(Rating.AGAIN)
            advanceUntilIdle()
            viewModel.onToggleShowAnswer()

            viewModel.resetForNextSession()

            val state = viewModel.uiState.value
            assertThat(state.unlocked).isFalse()
            assertThat(state.showAnswer).isFalse()
            assertThat(state.vocabularyItem).isEqualTo(item)
        }

    @Test
    fun `final saved rating creates a typed target completion`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item = VocabularyItem(id = 11, word = "elf", translation = "eleven", isNew = false)
            coEvery { saveDifficultyRating.invoke(any(), any(), any()) } returns Result.success(Unit)
            val viewModel = buildViewModel()
            viewModel.seedInitialWord(item, requiredCards = 1)

            viewModel.onDifficultySelected(Rating.GOOD)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.completion)
                .isEqualTo(GateCompletion(1))
        }

    @Test
    fun `second saved rating creates a typed two card target completion`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val first = VocabularyItem(id = 13, word = "dreizehn", translation = "thirteen", isNew = false)
            val second = VocabularyItem(id = 14, word = "vierzehn", translation = "fourteen", isNew = false)
            coEvery { saveDifficultyRating.invoke(any(), any(), any()) } returns Result.success(Unit)
            coEvery { getNextVocabularyItem.invoke() } returns Result.success(second)
            val viewModel = buildViewModel()
            viewModel.seedInitialWord(first, requiredCards = 2)

            viewModel.onDifficultySelected(Rating.GOOD)
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.completion).isNull()

            viewModel.onDifficultySelected(Rating.EASY)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.completion)
                .isEqualTo(GateCompletion(2))
        }

    @Test
    fun `queue exhaustion after a saved rating creates typed exhaustion completion`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item = VocabularyItem(id = 12, word = "zwolf", translation = "twelve", isNew = false)
            coEvery { saveDifficultyRating.invoke(any(), any(), any()) } returns Result.success(Unit)
            coEvery { getNextVocabularyItem.invoke() } returns Result.failure(NoAvailableItemsException())
            val viewModel = buildViewModel()
            viewModel.seedInitialWord(item, requiredCards = 2)

            viewModel.onDifficultySelected(Rating.GOOD)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.completion)
                .isEqualTo(GateCompletion(1))
        }
}
