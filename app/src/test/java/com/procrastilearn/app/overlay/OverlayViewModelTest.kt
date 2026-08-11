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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
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
    fun `onOverlayOpened loads the configured rating delay even when the word was already seeded`() =
        runTest(mainDispatcherRule.testDispatcher) {
            policyFlow.value = LearningPreferencesConfig(ratingDelaySeconds = 7)
            val item = VocabularyItem(id = 9, word = "Buch", translation = "book", isNew = false)

            val viewModel = buildViewModel()
            viewModel.seedInitialWord(item)

            viewModel.onOverlayOpened()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.ratingDelaySeconds).isEqualTo(7)
            coVerify(exactly = 0) { getNextVocabularyItem.invoke() }
        }

    @Test
    fun `zero rating delay leaves rating unlocked immediately on reveal`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item = VocabularyItem(id = 1, word = "eins", translation = "one", isNew = false)
            coEvery { getNextVocabularyItem.invoke() } returns Result.success(item)
            coEvery { saveDifficultyRating.invoke(any(), any(), any()) } returns Result.success(Unit)

            val viewModel = buildViewModel()
            viewModel.onOverlayOpened()
            advanceUntilIdle()

            viewModel.onToggleShowAnswer()

            assertThat(viewModel.uiState.value.ratingLockSecondsRemaining).isEqualTo(0)

            viewModel.onDifficultySelected(Rating.GOOD)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.unlocked).isTrue()
            coVerify(exactly = 1) { saveDifficultyRating.invoke(item.id, Rating.GOOD, item.direction) }
        }

    @Test
    fun `revealing with a configured delay locks rating synchronously before any time passes`() =
        runTest(mainDispatcherRule.testDispatcher) {
            policyFlow.value = LearningPreferencesConfig(ratingDelaySeconds = 5)
            val item = VocabularyItem(id = 2, word = "zwei", translation = "two", isNew = false)
            coEvery { getNextVocabularyItem.invoke() } returns Result.success(item)

            val viewModel = buildViewModel()
            viewModel.onOverlayOpened()
            advanceUntilIdle()

            viewModel.onToggleShowAnswer()

            assertThat(viewModel.uiState.value.ratingLockSecondsRemaining).isEqualTo(5)
        }

    @Test
    fun `countdown ticks down to zero one second at a time`() =
        runTest(mainDispatcherRule.testDispatcher) {
            policyFlow.value = LearningPreferencesConfig(ratingDelaySeconds = 3)
            val item = VocabularyItem(id = 3, word = "drei", translation = "three", isNew = false)
            coEvery { getNextVocabularyItem.invoke() } returns Result.success(item)

            val viewModel = buildViewModel()
            viewModel.onOverlayOpened()
            advanceUntilIdle()
            viewModel.onToggleShowAnswer()

            assertThat(viewModel.uiState.value.ratingLockSecondsRemaining).isEqualTo(3)

            advanceTimeBy(1_000)
            runCurrent()
            assertThat(viewModel.uiState.value.ratingLockSecondsRemaining).isEqualTo(2)

            advanceTimeBy(1_000)
            runCurrent()
            assertThat(viewModel.uiState.value.ratingLockSecondsRemaining).isEqualTo(1)

            advanceTimeBy(1_000)
            runCurrent()
            assertThat(viewModel.uiState.value.ratingLockSecondsRemaining).isEqualTo(0)
        }

    @Test
    fun `countdown never goes negative once it reaches zero`() =
        runTest(mainDispatcherRule.testDispatcher) {
            policyFlow.value = LearningPreferencesConfig(ratingDelaySeconds = 1)
            val item = VocabularyItem(id = 4, word = "vier", translation = "four", isNew = false)
            coEvery { getNextVocabularyItem.invoke() } returns Result.success(item)

            val viewModel = buildViewModel()
            viewModel.onOverlayOpened()
            advanceUntilIdle()
            viewModel.onToggleShowAnswer()

            advanceTimeBy(10_000)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.ratingLockSecondsRemaining).isEqualTo(0)
        }

    @Test
    fun `rating while locked does not save or unlock`() =
        runTest(mainDispatcherRule.testDispatcher) {
            policyFlow.value = LearningPreferencesConfig(ratingDelaySeconds = 5)
            val item = VocabularyItem(id = 5, word = "fünf", translation = "five", isNew = false)
            coEvery { getNextVocabularyItem.invoke() } returns Result.success(item)

            val viewModel = buildViewModel()
            viewModel.onOverlayOpened()
            advanceUntilIdle()
            viewModel.onToggleShowAnswer()

            viewModel.onDifficultySelected(Rating.EASY)

            val state = viewModel.uiState.value
            assertThat(state.unlocked).isFalse()
            assertThat(state.ratingLockSecondsRemaining).isEqualTo(5)
            coVerify(exactly = 0) { saveDifficultyRating.invoke(any(), any(), any()) }
        }

    @Test
    fun `rating after the countdown expires saves and unlocks normally`() =
        runTest(mainDispatcherRule.testDispatcher) {
            policyFlow.value = LearningPreferencesConfig(ratingDelaySeconds = 2)
            val item = VocabularyItem(id = 6, word = "sechs", translation = "six", isNew = false)
            coEvery { getNextVocabularyItem.invoke() } returns Result.success(item)
            coEvery { saveDifficultyRating.invoke(any(), any(), any()) } returns Result.success(Unit)

            val viewModel = buildViewModel()
            viewModel.onOverlayOpened()
            advanceUntilIdle()
            viewModel.onToggleShowAnswer()

            advanceTimeBy(2_000)
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.ratingLockSecondsRemaining).isEqualTo(0)

            viewModel.onDifficultySelected(Rating.GOOD)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.unlocked).isTrue()
            coVerify(exactly = 1) { saveDifficultyRating.invoke(item.id, Rating.GOOD, item.direction) }
        }

    @Test
    fun `un-revealing cancels the countdown and resets remaining to zero`() =
        runTest(mainDispatcherRule.testDispatcher) {
            policyFlow.value = LearningPreferencesConfig(ratingDelaySeconds = 10)
            val item = VocabularyItem(id = 7, word = "sieben", translation = "seven", isNew = false)
            coEvery { getNextVocabularyItem.invoke() } returns Result.success(item)

            val viewModel = buildViewModel()
            viewModel.onOverlayOpened()
            advanceUntilIdle()
            viewModel.onToggleShowAnswer()
            advanceTimeBy(3_000)
            runCurrent()
            assertThat(viewModel.uiState.value.ratingLockSecondsRemaining).isEqualTo(7)

            viewModel.onToggleShowAnswer()

            assertThat(viewModel.uiState.value.showAnswer).isFalse()
            assertThat(viewModel.uiState.value.ratingLockSecondsRemaining).isEqualTo(0)

            // Advancing further must not resurrect the cancelled countdown.
            advanceTimeBy(5_000)
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.ratingLockSecondsRemaining).isEqualTo(0)
        }

    @Test
    fun `re-revealing restarts the full countdown from the configured value`() =
        runTest(mainDispatcherRule.testDispatcher) {
            policyFlow.value = LearningPreferencesConfig(ratingDelaySeconds = 4)
            val item = VocabularyItem(id = 8, word = "acht", translation = "eight", isNew = false)
            coEvery { getNextVocabularyItem.invoke() } returns Result.success(item)

            val viewModel = buildViewModel()
            viewModel.onOverlayOpened()
            advanceUntilIdle()

            viewModel.onToggleShowAnswer()
            advanceTimeBy(3_000)
            runCurrent()
            assertThat(viewModel.uiState.value.ratingLockSecondsRemaining).isEqualTo(1)

            viewModel.onToggleShowAnswer() // hide
            viewModel.onToggleShowAnswer() // reveal again

            assertThat(viewModel.uiState.value.ratingLockSecondsRemaining).isEqualTo(4)
        }

    @Test
    fun `a failing policy read fails open with no delay`() =
        runTest(mainDispatcherRule.testDispatcher) {
            every { dayCountersStore.readPolicy() } returns flow { throw IllegalStateException("boom") }
            val item = VocabularyItem(id = 10, word = "neun", translation = "nine", isNew = false)
            coEvery { getNextVocabularyItem.invoke() } returns Result.success(item)

            val viewModel = buildViewModel()
            viewModel.onOverlayOpened()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.ratingDelaySeconds).isEqualTo(0)

            viewModel.onToggleShowAnswer()
            assertThat(viewModel.uiState.value.ratingLockSecondsRemaining).isEqualTo(0)
        }
}
