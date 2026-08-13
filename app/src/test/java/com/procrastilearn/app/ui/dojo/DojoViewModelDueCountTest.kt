package com.procrastilearn.app.ui.dojo

import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.data.counter.DayCounters
import com.procrastilearn.app.data.local.dao.VocabularyStatsDao
import com.procrastilearn.app.data.local.prefs.DayCountersStore
import com.procrastilearn.app.data.repository.NoAvailableItemsException
import com.procrastilearn.app.data.time.TimeTicker
import com.procrastilearn.app.domain.model.LearningPreferencesConfig
import com.procrastilearn.app.domain.model.MixMode
import com.procrastilearn.app.domain.model.StudyDirectionMode
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
class DojoViewModelDueCountTest {
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
    fun `skippedCardCount reflects the DAO's live skip count when studyDirectionMode is BACKWARD`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item = VocabularyItem(id = 1, word = "test", translation = "тест", isNew = false)
            coEvery { getNextVocabularyItem.invoke() } returns Result.success(item)
            val skippedFlow = MutableStateFlow(3)
            every { vocabularyStatsDao.observeBackwardOnlySkippedCount(any()) } returns skippedFlow
            policyFlow.value = policyFlow.value.copy(studyDirectionMode = StudyDirectionMode.BACKWARD)

            val viewModel = buildViewModel()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.skippedCardCount).isEqualTo(3)

            skippedFlow.value = 5
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.skippedCardCount).isEqualTo(5)
        }

    @Test
    fun `skippedCardCount is 0 when studyDirectionMode is FORWARD`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item = VocabularyItem(id = 1, word = "test", translation = "тест", isNew = false)
            coEvery { getNextVocabularyItem.invoke() } returns Result.success(item)

            val viewModel = buildViewModel()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.skippedCardCount).isEqualTo(0)
        }

    @Test
    fun `skippedCardCount is 0 when studyDirectionMode is BIDIRECTIONAL`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item = VocabularyItem(id = 1, word = "test", translation = "тест", isNew = false)
            coEvery { getNextVocabularyItem.invoke() } returns Result.success(item)
            policyFlow.value = policyFlow.value.copy(studyDirectionMode = StudyDirectionMode.BIDIRECTIONAL)

            val viewModel = buildViewModel()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.skippedCardCount).isEqualTo(0)
        }

    @Test
    fun `reviewsDueCount and newTotalCount re-query when studyDirectionMode changes`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item = VocabularyItem(id = 1, word = "test", translation = "тест", isNew = false)
            coEvery { getNextVocabularyItem.invoke() } returns Result.success(item)
            val forwardDueFlow = MutableStateFlow(10)
            val backwardDueFlow = MutableStateFlow(2)
            every { vocabularyStatsDao.observeReviewsDueCount(any(), true, false) } returns forwardDueFlow
            every { vocabularyStatsDao.observeReviewsDueCount(any(), false, true) } returns backwardDueFlow
            val forwardNewFlow = MutableStateFlow(20)
            val backwardNewFlow = MutableStateFlow(4)
            every { vocabularyStatsDao.observeNewTotalCount(false) } returns forwardNewFlow
            every { vocabularyStatsDao.observeNewTotalCount(true) } returns backwardNewFlow

            val viewModel = buildViewModel()
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.pendingReviewCount).isEqualTo(10)

            policyFlow.value = policyFlow.value.copy(studyDirectionMode = StudyDirectionMode.BACKWARD)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.pendingReviewCount).isEqualTo(2)
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
    fun `empty state resolves when a new word is added elsewhere and only newTotalCount changes`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item = VocabularyItem(id = 1, word = "test", translation = "тест", isNew = true)
            coEvery { getNextVocabularyItem.invoke() } returnsMany
                listOf(Result.failure(NoAvailableItemsException()), Result.success(item))

            val viewModel = buildViewModel()
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.hasNoWords).isTrue()

            // A new word was inserted elsewhere (Add Word screen); this changes only the
            // newTotalCount flow. Due count, day counters, and policy are all untouched.
            newTotalCountFlow.value = 1

            advanceUntilIdle()

            assertThat(viewModel.uiState.value.hasNoWords).isFalse()
            assertThat(viewModel.uiState.value.vocabularyItem).isEqualTo(item)
            coVerify(exactly = 2) { getNextVocabularyItem.invoke() }
        }

    @Test
    fun `flashcard re-fetches when newTotalCount changes even though due count, counters, and policy are unchanged`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val first = VocabularyItem(id = 1, word = "first", translation = "первый", isNew = true)
            val second = VocabularyItem(id = 2, word = "second", translation = "второй", isNew = true)
            coEvery { getNextVocabularyItem.invoke() } returnsMany
                listOf(Result.success(first), Result.success(second))

            val viewModel = buildViewModel()
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.vocabularyItem).isEqualTo(first)

            newTotalCountFlow.value = newTotalCountFlow.value + 1
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.vocabularyItem).isEqualTo(second)
            coVerify(exactly = 2) { getNextVocabularyItem.invoke() }
        }

    @Test
    fun `review quota at 0 shows 0 pending reviews`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item = VocabularyItem(id = 1, word = "test", translation = "тест", isNew = false)
            coEvery { getNextVocabularyItem.invoke() } returns Result.success(item)

            // Set review quota to 0
            countersFlow.value =
                DayCounters(
                    yyyymmdd = 20_260_117,
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
    fun `pendingReviewCount reflects cards that become due after the screen opened`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item = VocabularyItem(id = 1, word = "test", translation = "тест", isNew = false)
            coEvery { getNextVocabularyItem.invoke() } returns Result.success(item)

            val dueDelayMs = 2 * 60_000L
            every { vocabularyStatsDao.observeReviewsDueCount(any()) } answers {
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
            every { vocabularyStatsDao.observeReviewsDueCount(any()) } answers {
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
            every { vocabularyStatsDao.observeReviewsDueCount(any()) } answers {
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
            every { vocabularyStatsDao.observeReviewsDueCount(any()) } answers {
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
    @Suppress("ktlint:standard:max-line-length")
    fun `relearning cards become visible in the counter as soon as another card is rated, without waiting for the tick`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val first = VocabularyItem(id = 1, word = "first", translation = "первый", isNew = true)
            val second = VocabularyItem(id = 2, word = "second", translation = "второй", isNew = true)
            coEvery { getNextVocabularyItem.invoke() } returnsMany
                listOf(Result.success(first), Result.success(second))
            coEvery { saveDifficultyRating.invoke(any(), any()) } returns Result.success(Unit)

            val relearningDelayMs = 60_000L
            every { vocabularyStatsDao.observeReviewsDueCount(any()) } answers {
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
