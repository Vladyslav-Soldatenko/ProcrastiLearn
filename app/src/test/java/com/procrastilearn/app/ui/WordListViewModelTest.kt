package com.procrastilearn.app.ui

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.domain.repository.VocabularyRepository
import com.procrastilearn.app.utils.MainDispatcherRule
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WordListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: VocabularyRepository
    private lateinit var vocabularyFlow: MutableSharedFlow<List<VocabularyItem>>

    @Before
    fun setUp() {
        repository = mockk(relaxed = true)
        vocabularyFlow = MutableSharedFlow(replay = 1)
        vocabularyFlow.tryEmit(emptyList())
        every { repository.getAllVocabulary() } returns vocabularyFlow
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    private fun buildViewModel() = WordListViewModel(repository)

    @Test
    fun `words emits latest repository items`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()
            val first = VocabularyItem(id = 1, word = "Haus", translation = "House", isNew = true)
            val second = first.copy(id = 2, word = "Baum", translation = "Tree", isNew = false)

            viewModel.words.test {
                assertThat(awaitItem()).isEmpty()

                vocabularyFlow.tryEmit(listOf(first))
                assertThat(awaitItem()).containsExactly(first)

                vocabularyFlow.tryEmit(listOf(first, second))
                assertThat(awaitItem()).containsExactly(first, second).inOrder()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `deleteWord delegates to repository`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item = VocabularyItem(id = 10, word = "lesen", translation = "read", isNew = false)
            coEvery { repository.deleteVocabularyItem(item) } returns Unit
            val viewModel = buildViewModel()

            viewModel.deleteWord(item)
            advanceUntilIdle()

            coVerify(exactly = 1) { repository.deleteVocabularyItem(item) }
        }

    @Test
    fun `updateWord delegates to repository`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item = VocabularyItem(id = 11, word = "schreiben", translation = "write", isNew = false)
            coEvery { repository.updateVocabularyItem(item) } returns Unit
            val viewModel = buildViewModel()

            viewModel.updateWord(item)
            advanceUntilIdle()

            coVerify(exactly = 1) { repository.updateVocabularyItem(item) }
        }

    @Test
    fun `resetWordProgress delegates to repository`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item = VocabularyItem(id = 12, word = "gehen", translation = "go", isNew = true)
            coEvery { repository.resetVocabularyProgress(item) } returns Unit
            val viewModel = buildViewModel()

            viewModel.resetWordProgress(item)
            advanceUntilIdle()

            coVerify(exactly = 1) { repository.resetVocabularyProgress(item) }
        }
}
