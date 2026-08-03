package com.procrastilearn.app.ui.dojo

import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.data.counter.DayCounters
import com.procrastilearn.app.data.local.dao.VocabularyStatsDao
import com.procrastilearn.app.data.local.prefs.DayCountersStore
import com.procrastilearn.app.data.time.TimeTicker
import com.procrastilearn.app.domain.model.LearningPreferencesConfig
import com.procrastilearn.app.domain.model.MixMode
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.domain.usecase.GetNextVocabularyItemUseCase
import com.procrastilearn.app.domain.usecase.SaveDifficultyRatingUseCase
import com.procrastilearn.app.domain.usecase.UndoLastRatingUseCase
import com.procrastilearn.app.utils.MainDispatcherRule
import io.mockk.clearAllMocks
import io.mockk.coEvery
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
class DojoViewModelQuotaTest {
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
}
