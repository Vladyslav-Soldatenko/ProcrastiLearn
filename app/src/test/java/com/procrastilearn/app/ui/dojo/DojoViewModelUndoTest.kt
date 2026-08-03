package com.procrastilearn.app.ui.dojo

import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.data.counter.DayCounters
import com.procrastilearn.app.data.local.dao.VocabularyStatsDao
import com.procrastilearn.app.data.local.prefs.DayCountersStore
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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DojoViewModelUndoTest {
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
    fun `restored word survives the reactive re-fetch triggered by a newTotalCount change`() =
        runTest(mainDispatcherRule.testDispatcher) {
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

            newTotalCountFlow.value = newTotalCountFlow.value + 1
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.vocabularyItem).isEqualTo(restored)
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
            assertThat(
                viewModel.uiState.value.undoEvent
                    ?.word,
            ).isEqualTo("second-undo")
        }
}
