package com.procrastilearn.app.ui.dojo

import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.data.counter.DayCounters
import com.procrastilearn.app.data.local.dao.VocabularyStatsDao
import com.procrastilearn.app.data.local.prefs.DayCountersStore
import com.procrastilearn.app.data.repository.NoAvailableItemsException
import com.procrastilearn.app.data.time.TimeTicker
import com.procrastilearn.app.domain.model.LearningPreferencesConfig
import com.procrastilearn.app.domain.model.MixMode
import com.procrastilearn.app.domain.model.StudyDirection
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.domain.usecase.GetNextVocabularyItemUseCase
import com.procrastilearn.app.domain.usecase.SaveDifficultyRatingUseCase
import com.procrastilearn.app.domain.usecase.UndoLastRatingUseCase
import com.procrastilearn.app.utils.MainDispatcherRule
import io.github.openspacedrepetition.Rating
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DojoViewModelCoreTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var getNextVocabularyItem: GetNextVocabularyItemUseCase
    private lateinit var saveDifficultyRating: SaveDifficultyRatingUseCase
    private lateinit var vocabularyStatsDao: VocabularyStatsDao
    private lateinit var dayCountersStore: DayCountersStore
    private lateinit var undoLastRating: UndoLastRatingUseCase

    private lateinit var countersFlow: MutableStateFlow<DayCounters>
    private lateinit var policyFlow: MutableStateFlow<LearningPreferencesConfig>
    private lateinit var dueCountFlow: MutableStateFlow<Int>
    private lateinit var newTotalCountFlow: MutableStateFlow<Int>
    private lateinit var undoCountFlow: MutableStateFlow<Int>

    private val baseNow = 1_700_000_000_000L
    private lateinit var nowTicker: MutableStateFlow<Long>
    private var liveNow = baseNow
    private val fakeTimeTicker =
        object : TimeTicker {
            override fun nowTicks(): Flow<Long> = nowTicker

            override fun now(): Long = liveNow
        }

    @Before
    fun setUp() {
        getNextVocabularyItem = mockk()
        saveDifficultyRating = mockk()
        vocabularyStatsDao = mockk()
        dayCountersStore = mockk()
        undoLastRating = mockk()

        // Default flows
        countersFlow =
            MutableStateFlow(
                DayCounters(
                    yyyymmdd = 20_260_117,
                    newShown = 3,
                    reviewShown = 5,
                    reviewsSinceLastNew = 2,
                ),
            )
        policyFlow =
            MutableStateFlow(
                LearningPreferencesConfig(
                    newPerDay = 20,
                    reviewPerDay = 100,
                    mixMode = MixMode.MIX,
                    overlayInterval = 6,
                ),
            )
        dueCountFlow = MutableStateFlow(10)
        // High default so existing formula-only tests aren't affected by the new cap;
        // tests that specifically exercise the cap override this per-test.
        newTotalCountFlow = MutableStateFlow(1000)
        undoCountFlow = MutableStateFlow(0)
        nowTicker = MutableStateFlow(baseNow)
        liveNow = baseNow

        every { dayCountersStore.read() } returns countersFlow
        every { dayCountersStore.readPolicy() } returns policyFlow
        coEvery { vocabularyStatsDao.countReviewsDue(any(), any(), any()) } returns 10
        every { vocabularyStatsDao.observeReviewsDueCount(any(), any(), any()) } returns dueCountFlow
        every { vocabularyStatsDao.observeNewTotalCount(any()) } returns newTotalCountFlow
        every { vocabularyStatsDao.observeBackwardOnlySkippedCount(any()) } returns MutableStateFlow(0)
        every { undoLastRating.observeUndoCount() } returns undoCountFlow
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    private fun buildViewModel(): DojoViewModel =
        DojoViewModel(
            getNextVocabularyItem,
            saveDifficultyRating,
            dayCountersStore,
            undoLastRating,
            DojoCountersSource(vocabularyStatsDao, dayCountersStore, fakeTimeTicker),
        )

    @Test
    fun `initial state loads word and correct stats`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item =
                VocabularyItem(id = 1, word = "serendipity", translation = "счастливая случайность", isNew = true)
            coEvery { getNextVocabularyItem.invoke() } returns Result.success(item)

            val viewModel = buildViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.vocabularyItem).isEqualTo(item)
            assertThat(state.showAnswer).isFalse()
            assertThat(state.isLoading).isFalse()
            assertThat(state.newQuotaRemaining).isEqualTo(17) // 20 - 3
            assertThat(state.pendingReviewCount).isEqualTo(10)
            coVerify(exactly = 1) { getNextVocabularyItem.invoke() }
        }

    @Test
    fun `toggle answer updates showAnswer flag`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item = VocabularyItem(id = 1, word = "test", translation = "тест", isNew = false)
            coEvery { getNextVocabularyItem.invoke() } returns Result.success(item)

            val viewModel = buildViewModel()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.showAnswer).isFalse()

            viewModel.onToggleShowAnswer()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.showAnswer).isTrue()

            viewModel.onToggleShowAnswer()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.showAnswer).isFalse()
        }

    @Test
    fun `difficulty selection saves rating`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item = VocabularyItem(id = 42, word = "test", translation = "тест", isNew = true)
            coEvery { getNextVocabularyItem.invoke() } returns Result.success(item)
            coEvery { saveDifficultyRating.invoke(any(), any()) } returns Result.success(Unit)

            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onDifficultySelected(Rating.GOOD)
            advanceUntilIdle()

            coVerify(exactly = 1) { saveDifficultyRating.invoke(42, Rating.GOOD) }
        }

    @Test
    fun `difficulty selection passes the current item's direction to saveDifficultyRating`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item =
                VocabularyItem(
                    id = 42,
                    word = "test",
                    translation = "тест",
                    isNew = true,
                    direction = StudyDirection.BACKWARD,
                )
            coEvery { getNextVocabularyItem.invoke() } returns Result.success(item)
            coEvery { saveDifficultyRating.invoke(any(), any(), any()) } returns Result.success(Unit)

            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onDifficultySelected(Rating.GOOD)
            advanceUntilIdle()

            coVerify(exactly = 1) { saveDifficultyRating.invoke(42, Rating.GOOD, StudyDirection.BACKWARD) }
        }

    @Test
    fun `difficulty selection loads next word`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item1 = VocabularyItem(id = 1, word = "first", translation = "первый", isNew = true)
            val item2 = VocabularyItem(id = 2, word = "second", translation = "второй", isNew = false)
            coEvery { getNextVocabularyItem.invoke() } returnsMany listOf(Result.success(item1), Result.success(item2))
            coEvery { saveDifficultyRating.invoke(any(), any()) } returns Result.success(Unit)

            val viewModel = buildViewModel()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.vocabularyItem).isEqualTo(item1)

            viewModel.onDifficultySelected(Rating.EASY)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.vocabularyItem).isEqualTo(item2)
            coVerify(exactly = 2) { getNextVocabularyItem.invoke() }
        }

    @Test
    fun `difficulty selection refreshes stats`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item = VocabularyItem(id = 1, word = "test", translation = "тест", isNew = true)
            coEvery { getNextVocabularyItem.invoke() } returns Result.success(item)
            coEvery { saveDifficultyRating.invoke(any(), any()) } returns Result.success(Unit)

            val viewModel = buildViewModel()
            advanceUntilIdle()

            val statsBefore = viewModel.uiState.value.pendingReviewCount
            assertThat(statsBefore).isEqualTo(10)

            viewModel.onDifficultySelected(Rating.HARD)
            advanceUntilIdle()

            // Simulate Room re-querying the due count after the underlying table write
            dueCountFlow.value = 15
            advanceUntilIdle()

            val statsAfter = viewModel.uiState.value.pendingReviewCount
            assertThat(statsAfter).isEqualTo(15)
        }

    @Test
    fun `flashcard refreshes when vocabulary changes elsewhere (e_g_ reviewed via overlay)`() =
        runTest(mainDispatcherRule.testDispatcher) {
            // Regression test: Dojo used to cache the current flashcard and only ever
            // reload it from init{} or after a local rating. If the same word was
            // reviewed from the blocking overlay while Dojo was left showing it, Dojo
            // had no way of finding out and kept showing the stale card indefinitely.
            val stale = VocabularyItem(id = 1, word = "stale", translation = "старый", isNew = true)
            val fresh = VocabularyItem(id = 2, word = "fresh", translation = "новый", isNew = true)
            coEvery { getNextVocabularyItem.invoke() } returnsMany listOf(Result.success(stale), Result.success(fresh))

            val viewModel = buildViewModel()
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.vocabularyItem).isEqualTo(stale)

            // Something else (the overlay, reviewing the same word) writes to the
            // vocabulary table; Room would re-run any observed query on that table.
            dueCountFlow.value = 20
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.vocabularyItem).isEqualTo(fresh)
            coVerify(exactly = 2) { getNextVocabularyItem.invoke() }
        }

    @Test
    fun `empty state when NoAvailableItemsException`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { getNextVocabularyItem.invoke() } returns Result.failure(NoAvailableItemsException())

            val viewModel = buildViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.vocabularyItem).isNull()
            assertThat(state.isLoading).isFalse()
            assertThat(state.hasNoWords).isTrue()
        }

    @Test
    fun `loading state during fetch`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item = VocabularyItem(id = 1, word = "test", translation = "тест", isNew = false)
            coEvery { getNextVocabularyItem.invoke() } returns Result.success(item)

            val viewModel = buildViewModel()

            // Before advanceUntilIdle, loading should be true
            val initialState = viewModel.uiState.value
            assertThat(initialState.isLoading).isTrue()

            advanceUntilIdle()

            // After loading, should be false
            val loadedState = viewModel.uiState.value
            assertThat(loadedState.isLoading).isFalse()
        }

    @Test
    fun `difficulty selection resets showAnswer for next card`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item = VocabularyItem(id = 1, word = "test", translation = "тест", isNew = true)
            coEvery { getNextVocabularyItem.invoke() } returns Result.success(item)
            coEvery { saveDifficultyRating.invoke(any(), any()) } returns Result.success(Unit)

            val viewModel = buildViewModel()
            advanceUntilIdle()

            // Show answer
            viewModel.onToggleShowAnswer()
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.showAnswer).isTrue()

            // Select difficulty
            viewModel.onDifficultySelected(Rating.EASY)
            advanceUntilIdle()

            // showAnswer should be reset
            assertThat(viewModel.uiState.value.showAnswer).isFalse()
        }
}
