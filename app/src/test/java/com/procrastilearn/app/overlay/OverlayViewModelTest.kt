package com.procrastilearn.app.overlay

import android.util.Log
import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.data.repository.NoAvailableItemsException
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
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
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

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        getNextVocabularyItem = mockk()
        saveDifficultyRating = mockk()
    }

    @After
    fun tearDown() {
        clearAllMocks()
        unmockkStatic(Log::class)
    }

    private fun buildViewModel(): OverlayViewModel = OverlayViewModel(getNextVocabularyItem, saveDifficultyRating)

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
}
