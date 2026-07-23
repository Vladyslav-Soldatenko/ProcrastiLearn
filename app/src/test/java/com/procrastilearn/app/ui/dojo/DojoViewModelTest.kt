package com.procrastilearn.app.ui.dojo

import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.data.counter.DayCounters
import com.procrastilearn.app.data.local.dao.UndoSnapshotDao
import com.procrastilearn.app.data.local.dao.VocabularyDao
import com.procrastilearn.app.data.local.prefs.DayCountersStore
import com.procrastilearn.app.data.repository.NoAvailableItemsException
import com.procrastilearn.app.data.time.TimeTicker
import com.procrastilearn.app.domain.model.LearningPreferencesConfig
import com.procrastilearn.app.domain.model.MixMode
import com.procrastilearn.app.domain.model.UndoResult
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DojoViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var getNextVocabularyItem: GetNextVocabularyItemUseCase
    private lateinit var saveDifficultyRating: SaveDifficultyRatingUseCase
    private lateinit var vocabularyDao: VocabularyDao
    private lateinit var dayCountersStore: DayCountersStore
    private lateinit var undoLastRating: UndoLastRatingUseCase
    private lateinit var undoSnapshotDao: UndoSnapshotDao

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
        vocabularyDao = mockk()
        dayCountersStore = mockk()
        undoLastRating = mockk()
        undoSnapshotDao = mockk()

        // Default flows
        countersFlow =
            MutableStateFlow(
                DayCounters(
                    yyyymmdd = 20260117,
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
        coEvery { vocabularyDao.countReviewsDue(any()) } returns 10
        every { vocabularyDao.observeReviewsDueCount(any()) } returns dueCountFlow
        every { vocabularyDao.observeNewTotalCount() } returns newTotalCountFlow
        every { undoSnapshotDao.observeCount() } returns undoCountFlow
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    private fun buildViewModel(): DojoViewModel =
        DojoViewModel(
            getNextVocabularyItem,
            saveDifficultyRating,
            vocabularyDao,
            dayCountersStore,
            undoLastRating,
            undoSnapshotDao,
            fakeTimeTicker,
        )

    @Test
    fun `initial state loads word and correct stats`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item = VocabularyItem(id = 1, word = "serendipity", translation = "счастливая случайность", isNew = true)
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
    fun `stats update when counters change`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item = VocabularyItem(id = 1, word = "test", translation = "тест", isNew = false)
            coEvery { getNextVocabularyItem.invoke() } returns Result.success(item)

            val viewModel = buildViewModel()
            advanceUntilIdle()

            // Update counters
            countersFlow.value =
                DayCounters(
                    yyyymmdd = 20260117,
                    newShown = 10,
                    reviewShown = 20,
                    reviewsSinceLastNew = 5,
                )
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.newQuotaRemaining).isEqualTo(10) // 20 - 10
        }

    @Test
    fun `stats update when policy changes`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item = VocabularyItem(id = 1, word = "test", translation = "тест", isNew = false)
            coEvery { getNextVocabularyItem.invoke() } returns Result.success(item)

            val viewModel = buildViewModel()
            advanceUntilIdle()

            // Update policy
            policyFlow.value =
                LearningPreferencesConfig(
                    newPerDay = 50,
                    reviewPerDay = 200,
                    mixMode = MixMode.NEW_FIRST,
                    overlayInterval = 10,
                )
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.newQuotaRemaining).isEqualTo(47) // 50 - 3
        }

    @Test
    fun `newQuotaRemaining includes extraNewToday boost`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item = VocabularyItem(id = 1, word = "test", translation = "тест", isNew = false)
            coEvery { getNextVocabularyItem.invoke() } returns Result.success(item)

            // newPerDay=20, newShown=3, extraNewToday=10 -> 20 + 10 - 3 = 27
            countersFlow.value =
                DayCounters(
                    yyyymmdd = 20260117,
                    newShown = 3,
                    reviewShown = 5,
                    reviewsSinceLastNew = 2,
                    extraNewToday = 10,
                )

            val viewModel = buildViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.newQuotaRemaining).isEqualTo(27)
        }

    @Test
    fun `newQuotaRemaining reflects extraNewToday even after permanent quota fully consumed`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item = VocabularyItem(id = 1, word = "test", translation = "тест", isNew = false)
            coEvery { getNextVocabularyItem.invoke() } returns Result.success(item)

            // newPerDay=20, newShown=20 (fully consumed), extraNewToday=5 -> 5 remaining
            countersFlow.value =
                DayCounters(
                    yyyymmdd = 20260117,
                    newShown = 20,
                    reviewShown = 5,
                    reviewsSinceLastNew = 2,
                    extraNewToday = 5,
                )

            val viewModel = buildViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.newQuotaRemaining).isEqualTo(5)
        }

    @Test
    fun `newQuotaRemaining updates reactively when extraNewToday is added mid-session`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item = VocabularyItem(id = 1, word = "test", translation = "тест", isNew = false)
            coEvery { getNextVocabularyItem.invoke() } returns Result.success(item)

            val viewModel = buildViewModel()
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.newQuotaRemaining).isEqualTo(17) // 20 - 3

            countersFlow.value = countersFlow.value.copy(extraNewToday = 8)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.newQuotaRemaining).isEqualTo(25) // 20 + 8 - 3
        }

    @Test
    fun `newQuotaRemaining coerced to 0 when negative even with extraNewToday`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item = VocabularyItem(id = 1, word = "test", translation = "тест", isNew = false)
            coEvery { getNextVocabularyItem.invoke() } returns Result.success(item)

            // newPerDay=20, newShown=25, extraNewToday=3 -> 20 + 3 - 25 = -2 -> coerced to 0
            countersFlow.value =
                DayCounters(
                    yyyymmdd = 20260117,
                    newShown = 25,
                    reviewShown = 5,
                    reviewsSinceLastNew = 2,
                    extraNewToday = 3,
                )

            val viewModel = buildViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.newQuotaRemaining).isEqualTo(0)
        }

    @Test
    fun `newQuotaRemaining is capped at the actual number of unseen cards left in the deck`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item = VocabularyItem(id = 1, word = "test", translation = "тест", isNew = false)
            coEvery { getNextVocabularyItem.invoke() } returns Result.success(item)
            newTotalCountFlow.value = 4

            val viewModel = buildViewModel()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.newQuotaRemaining).isEqualTo(4)
        }

    @Test
    fun `newQuotaRemaining stays under the formula value when unseen count exceeds it`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item = VocabularyItem(id = 1, word = "test", translation = "тест", isNew = false)
            coEvery { getNextVocabularyItem.invoke() } returns Result.success(item)
            // newPerDay=20, newShown=3 -> formula gives 17; plenty of unseen cards (100) exist.
            newTotalCountFlow.value = 100

            val viewModel = buildViewModel()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.newQuotaRemaining).isEqualTo(17)
        }

    @Test
    fun `newQuotaRemaining caps an oversized extraNewToday boost at the unseen total`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item = VocabularyItem(id = 1, word = "test", translation = "тест", isNew = false)
            coEvery { getNextVocabularyItem.invoke() } returns Result.success(item)
            countersFlow.value =
                DayCounters(
                    yyyymmdd = 20260117,
                    newShown = 3,
                    reviewShown = 5,
                    reviewsSinceLastNew = 2,
                    extraNewToday = 500,
                )
            newTotalCountFlow.value = 6

            val viewModel = buildViewModel()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.newQuotaRemaining).isEqualTo(6)
        }

    @Test
    fun `newQuotaRemaining is zero when there are no unseen cards left regardless of quota`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item = VocabularyItem(id = 1, word = "test", translation = "тест", isNew = false)
            coEvery { getNextVocabularyItem.invoke() } returns Result.success(item)
            newTotalCountFlow.value = 0

            val viewModel = buildViewModel()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.newQuotaRemaining).isEqualTo(0)
        }

    @Test
    fun `newQuotaRemaining reacts when the unseen total changes elsewhere`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item = VocabularyItem(id = 1, word = "test", translation = "тест", isNew = false)
            coEvery { getNextVocabularyItem.invoke() } returns Result.success(item)
            newTotalCountFlow.value = 4

            val viewModel = buildViewModel()
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.newQuotaRemaining).isEqualTo(4)

            // More words get imported/added elsewhere in the app.
            newTotalCountFlow.value = 50
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.newQuotaRemaining).isEqualTo(17) // 20 - 3
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
    fun `empty state resolves once daily quota is raised elsewhere`() =
        runTest(mainDispatcherRule.testDispatcher) {
            // Regression test: raising newPerDay in Settings updates the header counter
            // reactively (it's fed straight from DataStore), but Dojo never re-ran
            // loadNextWord(), so it kept showing "no words" even though a word was
            // now available.
            val item = VocabularyItem(id = 1, word = "test", translation = "тест", isNew = true)
            coEvery { getNextVocabularyItem.invoke() } returnsMany
                listOf(Result.failure(NoAvailableItemsException()), Result.success(item))

            val viewModel = buildViewModel()
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.hasNoWords).isTrue()

            policyFlow.value =
                LearningPreferencesConfig(
                    newPerDay = 50,
                    reviewPerDay = 100,
                    mixMode = MixMode.MIX,
                    overlayInterval = 6,
                )
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.hasNoWords).isFalse()
            assertThat(viewModel.uiState.value.vocabularyItem).isEqualTo(item)
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
    fun `review quota at 0 shows 0 pending reviews`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item = VocabularyItem(id = 1, word = "test", translation = "тест", isNew = false)
            coEvery { getNextVocabularyItem.invoke() } returns Result.success(item)

            // Set review quota to 0
            countersFlow.value =
                DayCounters(
                    yyyymmdd = 20260117,
                    newShown = 3,
                    reviewShown = 100, // Exhausted review quota
                    reviewsSinceLastNew = 50,
                )

            val viewModel = buildViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.pendingReviewCount).isEqualTo(0)
        }

    @Test
    fun `new quota remaining coerced to 0 when negative`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item = VocabularyItem(id = 1, word = "test", translation = "тест", isNew = false)
            coEvery { getNextVocabularyItem.invoke() } returns Result.success(item)

            // Set new shown > new per day
            countersFlow.value =
                DayCounters(
                    yyyymmdd = 20260117,
                    newShown = 25, // More than policy (20)
                    reviewShown = 5,
                    reviewsSinceLastNew = 2,
                )

            val viewModel = buildViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.newQuotaRemaining).isEqualTo(0)
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

    @Test
    fun `canUndo reflects the undo stack size`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item = VocabularyItem(id = 1, word = "test", translation = "тест", isNew = true)
            coEvery { getNextVocabularyItem.invoke() } returns Result.success(item)

            val viewModel = buildViewModel()
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.canUndo).isFalse()

            undoCountFlow.value = 1
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.canUndo).isTrue()

            undoCountFlow.value = 0
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.canUndo).isFalse()
        }

    @Test
    fun `onUndo with nothing to undo is a no-op`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item = VocabularyItem(id = 1, word = "test", translation = "тест", isNew = true)
            coEvery { getNextVocabularyItem.invoke() } returns Result.success(item)
            coEvery { undoLastRating.invoke() } returns Result.success(null)

            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onUndo()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.vocabularyItem).isEqualTo(item)
            assertThat(viewModel.uiState.value.undoEvent).isNull()
        }

    @Test
    fun `onUndo pins the restored word on screen with its answer shown`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val current = VocabularyItem(id = 1, word = "current", translation = "текущий", isNew = true)
            val restored = VocabularyItem(id = 99, word = "restored", translation = "восстановлен", isNew = false)
            coEvery { getNextVocabularyItem.invoke() } returns Result.success(current)
            coEvery { undoLastRating.invoke() } returns
                Result.success(UndoResult(item = restored, revertedRating = Rating.EASY))

            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onUndo()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.vocabularyItem).isEqualTo(restored)
            assertThat(state.showAnswer).isTrue()
            assertThat(state.undoEvent?.word).isEqualTo("restored")
            assertThat(state.undoEvent?.revertedRating).isEqualTo(Rating.EASY)
        }

    @Test
    fun `onUndoEventShown clears the undo event`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val current = VocabularyItem(id = 1, word = "current", translation = "текущий", isNew = true)
            val restored = VocabularyItem(id = 99, word = "restored", translation = "восстановлен", isNew = false)
            coEvery { getNextVocabularyItem.invoke() } returns Result.success(current)
            coEvery { undoLastRating.invoke() } returns
                Result.success(UndoResult(item = restored, revertedRating = Rating.GOOD))

            val viewModel = buildViewModel()
            advanceUntilIdle()
            viewModel.onUndo()
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.undoEvent).isNotNull()

            viewModel.onUndoEventShown()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.undoEvent).isNull()
        }

    @Test
    fun `restored word survives the reactive re-fetch triggered by undo's own writes`() =
        runTest(mainDispatcherRule.testDispatcher) {
            // Regression test for the pinned-item guard: undo writes to both the vocabulary
            // table and the day counters, either of which can trigger the reactive
            // combine() in init{} that normally calls loadNextWord(). That must not be
            // allowed to steal the just-restored card off the screen.
            val current = VocabularyItem(id = 1, word = "current", translation = "текущий", isNew = true)
            val restored = VocabularyItem(id = 99, word = "restored", translation = "восстановлен", isNew = false)
            val someOtherWord = VocabularyItem(id = 2, word = "other", translation = "другой", isNew = true)
            coEvery { getNextVocabularyItem.invoke() } returnsMany
                listOf(Result.success(current), Result.success(someOtherWord))
            coEvery { undoLastRating.invoke() } returns
                Result.success(UndoResult(item = restored, revertedRating = Rating.EASY))

            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onUndo()
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.vocabularyItem).isEqualTo(restored)

            // Simulate the reactive triggers undo's own DB/counter writes would cause.
            dueCountFlow.value = 999
            advanceUntilIdle()
            countersFlow.value = countersFlow.value.copy(newShown = 0)
            advanceUntilIdle()

            // The restored card must still be pinned; it must not have been replaced by
            // "someOtherWord" from a stray loadNextWord() call.
            assertThat(viewModel.uiState.value.vocabularyItem).isEqualTo(restored)
        }

    @Test
    fun `re-rating the restored word clears the pin and resumes normal fetching`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val current = VocabularyItem(id = 1, word = "current", translation = "текущий", isNew = true)
            val restored = VocabularyItem(id = 99, word = "restored", translation = "восстановлен", isNew = false)
            val next = VocabularyItem(id = 2, word = "next", translation = "следующий", isNew = true)
            coEvery { getNextVocabularyItem.invoke() } returnsMany
                listOf(Result.success(current), Result.success(next))
            coEvery { undoLastRating.invoke() } returns
                Result.success(UndoResult(item = restored, revertedRating = Rating.EASY))
            coEvery { saveDifficultyRating.invoke(any(), any()) } returns Result.success(Unit)

            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onUndo()
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.vocabularyItem).isEqualTo(restored)

            viewModel.onDifficultySelected(Rating.GOOD)
            advanceUntilIdle()

            coVerify(exactly = 1) { saveDifficultyRating.invoke(99, Rating.GOOD) }
            assertThat(viewModel.uiState.value.vocabularyItem).isEqualTo(next)

            // Pin is cleared: further reactive triggers now fetch for real again rather
            // than re-pinning the old restored word.
            dueCountFlow.value = 1234
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.vocabularyItem).isEqualTo(next)
        }

    @Test
    fun `undoing twice re-pins each newly restored word`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val current = VocabularyItem(id = 1, word = "current", translation = "текущий", isNew = true)
            val restoredFirst = VocabularyItem(id = 98, word = "first-undo", translation = "первый", isNew = false)
            val restoredSecond = VocabularyItem(id = 99, word = "second-undo", translation = "второй", isNew = false)
            coEvery { getNextVocabularyItem.invoke() } returns Result.success(current)
            coEvery { undoLastRating.invoke() } returnsMany
                listOf(
                    Result.success(UndoResult(item = restoredFirst, revertedRating = Rating.GOOD)),
                    Result.success(UndoResult(item = restoredSecond, revertedRating = Rating.AGAIN)),
                )

            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onUndo()
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.vocabularyItem).isEqualTo(restoredFirst)

            viewModel.onUndo()
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.vocabularyItem).isEqualTo(restoredSecond)
            assertThat(viewModel.uiState.value.undoEvent?.word).isEqualTo("second-undo")
        }

    @Test
    fun `pendingReviewCount reflects cards that become due after the screen opened`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item = VocabularyItem(id = 1, word = "test", translation = "тест", isNew = false)
            coEvery { getNextVocabularyItem.invoke() } returns Result.success(item)

            val dueDelayMs = 2 * 60_000L
            every { vocabularyDao.observeReviewsDueCount(any()) } answers {
                val now = firstArg<Long>()
                flowOf(if (now >= baseNow + dueDelayMs) 5 else 0)
            }

            val viewModel = buildViewModel()
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.pendingReviewCount).isEqualTo(0)

            nowTicker.value = baseNow + dueDelayMs
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.pendingReviewCount).isEqualTo(5)
        }

    @Test
    fun `empty state self-heals when a card becomes due without rebuilding the ViewModel`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item = VocabularyItem(id = 1, word = "test", translation = "тест", isNew = true)
            coEvery { getNextVocabularyItem.invoke() } returnsMany
                listOf(Result.failure(NoAvailableItemsException()), Result.success(item))

            val dueDelayMs = 2 * 60_000L
            every { vocabularyDao.observeReviewsDueCount(any()) } answers {
                val now = firstArg<Long>()
                flowOf(if (now >= baseNow + dueDelayMs) 1 else 0)
            }

            val viewModel = buildViewModel()
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.hasNoWords).isTrue()

            nowTicker.value = baseNow + dueDelayMs
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.hasNoWords).isFalse()
            assertThat(viewModel.uiState.value.vocabularyItem).isEqualTo(item)
        }

    @Test
    fun `counter is not zero while relearning (Again) cards are being served`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item = VocabularyItem(id = 1, word = "test", translation = "тест", isNew = false)
            coEvery { getNextVocabularyItem.invoke() } returns Result.success(item)

            val relearningDelayMs = 60_000L
            every { vocabularyDao.observeReviewsDueCount(any()) } answers {
                val now = firstArg<Long>()
                flowOf(if (now >= baseNow + relearningDelayMs) 5 else 0)
            }

            val viewModel = buildViewModel()
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.pendingReviewCount).isEqualTo(0)

            nowTicker.value = baseNow + relearningDelayMs
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.pendingReviewCount).isEqualTo(5)
        }

    @Test
    fun `rating a card recomputes pendingReviewCount using a live now instead of waiting for the next tick`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item = VocabularyItem(id = 1, word = "test", translation = "тест", isNew = false)
            coEvery { getNextVocabularyItem.invoke() } returns Result.success(item)
            coEvery { saveDifficultyRating.invoke(any(), any()) } returns Result.success(Unit)

            val dueDelayMs = 2 * 60_000L
            every { vocabularyDao.observeReviewsDueCount(any()) } answers {
                val now = firstArg<Long>()
                flowOf(if (now >= baseNow + dueDelayMs) 5 else 0)
            }

            val viewModel = buildViewModel()
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.pendingReviewCount).isEqualTo(0)

            liveNow = baseNow + dueDelayMs
            assertThat(nowTicker.value).isEqualTo(baseNow)

            viewModel.onDifficultySelected(Rating.GOOD)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.pendingReviewCount).isEqualTo(5)
        }

    @Test
    fun `relearning cards become visible in the counter as soon as another card is rated, without waiting for the tick`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val first = VocabularyItem(id = 1, word = "first", translation = "первый", isNew = true)
            val second = VocabularyItem(id = 2, word = "second", translation = "второй", isNew = true)
            coEvery { getNextVocabularyItem.invoke() } returnsMany
                listOf(Result.success(first), Result.success(second))
            coEvery { saveDifficultyRating.invoke(any(), any()) } returns Result.success(Unit)

            val relearningDelayMs = 60_000L
            every { vocabularyDao.observeReviewsDueCount(any()) } answers {
                val now = firstArg<Long>()
                flowOf(if (now >= baseNow + relearningDelayMs) 5 else 0)
            }

            val viewModel = buildViewModel()
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.pendingReviewCount).isEqualTo(0)

            viewModel.onDifficultySelected(Rating.AGAIN)
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.vocabularyItem).isEqualTo(second)
            assertThat(viewModel.uiState.value.pendingReviewCount).isEqualTo(0)

            liveNow = baseNow + relearningDelayMs
            viewModel.onDifficultySelected(Rating.GOOD)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.pendingReviewCount).isEqualTo(5)
        }
}
