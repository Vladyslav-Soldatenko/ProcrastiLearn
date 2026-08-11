package com.procrastilearn.app.ui

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.domain.repository.VocabularyCatalogRepository
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

    private lateinit var repository: VocabularyCatalogRepository
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

    @Test
    fun `enterSelectionMode activates selection with the given id`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()

            viewModel.enterSelectionMode(1L)

            assertThat(viewModel.selectionState.value)
                .isEqualTo(WordListViewModel.SelectionState(isActive = true, selectedIds = setOf(1L)))
        }

    @Test
    fun `enterSelectionMode replaces any prior selection rather than merging`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()
            viewModel.enterSelectionMode(1L)
            viewModel.toggleSelection(2L)

            viewModel.enterSelectionMode(3L)

            assertThat(viewModel.selectionState.value)
                .isEqualTo(WordListViewModel.SelectionState(isActive = true, selectedIds = setOf(3L)))
        }

    @Test
    fun `toggleSelection adds an unselected id`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()
            viewModel.enterSelectionMode(1L)

            viewModel.toggleSelection(2L)

            assertThat(viewModel.selectionState.value.selectedIds).containsExactly(1L, 2L)
        }

    @Test
    fun `toggleSelection removes an already selected id`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()
            viewModel.enterSelectionMode(1L)

            viewModel.toggleSelection(1L)

            assertThat(viewModel.selectionState.value.selectedIds).isEmpty()
            assertThat(viewModel.selectionState.value.isActive).isTrue()
        }

    @Test
    fun `toggleSelection is a no-op when selection mode is not active`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()

            viewModel.toggleSelection(5L)

            assertThat(viewModel.selectionState.value).isEqualTo(WordListViewModel.SelectionState())
        }

    @Test
    fun `toggleSelection accepts an id that is not present in words`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()
            viewModel.enterSelectionMode(1L)

            viewModel.toggleSelection(999L)

            assertThat(viewModel.selectionState.value.selectedIds).containsExactly(1L, 999L)
        }

    @Test
    fun `selectAll unions ids into an empty selection`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()

            viewModel.selectAll(listOf(1L, 2L, 3L))

            assertThat(viewModel.selectionState.value.selectedIds).containsExactly(1L, 2L, 3L)
        }

    @Test
    fun `selectAll unions ids into a partially overlapping selection`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()
            viewModel.enterSelectionMode(1L)
            viewModel.toggleSelection(5L)

            viewModel.selectAll(listOf(2L, 3L))

            assertThat(viewModel.selectionState.value.selectedIds).containsExactly(1L, 2L, 3L, 5L)
        }

    @Test
    fun `selectAll with an empty list is a safe no-op`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()
            viewModel.enterSelectionMode(1L)

            viewModel.selectAll(emptyList())

            assertThat(viewModel.selectionState.value.selectedIds).containsExactly(1L)
        }

    @Test
    fun `deselectAll clears selection but keeps selection mode active`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()
            viewModel.enterSelectionMode(1L)
            viewModel.toggleSelection(2L)

            viewModel.deselectAll()

            assertThat(viewModel.selectionState.value.selectedIds).isEmpty()
            assertThat(viewModel.selectionState.value.isActive).isTrue()
        }

    @Test
    fun `deselectAll when already empty is a safe no-op`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()
            viewModel.enterSelectionMode(1L)
            viewModel.deselectAll()

            viewModel.deselectAll()

            assertThat(viewModel.selectionState.value)
                .isEqualTo(WordListViewModel.SelectionState(isActive = true, selectedIds = emptySet()))
        }

    @Test
    fun `exitSelectionMode resets state regardless of prior selection`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()
            viewModel.enterSelectionMode(1L)
            viewModel.toggleSelection(2L)

            viewModel.exitSelectionMode()

            assertThat(viewModel.selectionState.value).isEqualTo(WordListViewModel.SelectionState())
        }

    @Test
    fun `deleteSelectedWords deletes the selected items and exits selection mode`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val first = VocabularyItem(id = 1, word = "Haus", translation = "House", isNew = true)
            val second = first.copy(id = 2, word = "Baum", translation = "Tree", isNew = false)
            val third = first.copy(id = 3, word = "Auto", translation = "Car", isNew = false)
            val viewModel = buildViewModel()
            coEvery { repository.deleteVocabularyItems(any()) } returns Unit

            viewModel.words.test {
                assertThat(awaitItem()).isEmpty()
                vocabularyFlow.tryEmit(listOf(first, second, third))
                assertThat(awaitItem()).containsExactly(first, second, third).inOrder()
                cancelAndIgnoreRemainingEvents()
            }

            viewModel.enterSelectionMode(1L)
            viewModel.toggleSelection(3L)
            viewModel.deleteSelectedWords()
            advanceUntilIdle()

            coVerify(exactly = 1) { repository.deleteVocabularyItems(listOf(first, third)) }
            assertThat(viewModel.selectionState.value).isEqualTo(WordListViewModel.SelectionState())
        }

    @Test
    fun `deleteSelectedWords does nothing when nothing is selected`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()
            viewModel.enterSelectionMode(1L)
            viewModel.toggleSelection(1L)

            viewModel.deleteSelectedWords()
            advanceUntilIdle()

            coVerify(exactly = 0) { repository.deleteVocabularyItems(any()) }
            assertThat(viewModel.selectionState.value)
                .isEqualTo(WordListViewModel.SelectionState(isActive = true, selectedIds = emptySet()))
        }

    @Test
    fun `deleteSelectedWords only deletes ids still present in words`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val first = VocabularyItem(id = 1, word = "Haus", translation = "House", isNew = true)
            val viewModel = buildViewModel()
            coEvery { repository.deleteVocabularyItems(any()) } returns Unit

            viewModel.words.test {
                assertThat(awaitItem()).isEmpty()
                vocabularyFlow.tryEmit(listOf(first))
                assertThat(awaitItem()).containsExactly(first)
                cancelAndIgnoreRemainingEvents()
            }

            viewModel.enterSelectionMode(1L)
            viewModel.toggleSelection(999L)
            viewModel.deleteSelectedWords()
            advanceUntilIdle()

            coVerify(exactly = 1) { repository.deleteVocabularyItems(listOf(first)) }
        }

    private suspend fun WordListViewModel.awaitWords(items: List<VocabularyItem>) {
        words.test {
            awaitItem()
            vocabularyFlow.tryEmit(items)
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setSelectedWordsBidirectional true enables only the selected words that are not yet bidirectional`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val forward = VocabularyItem(id = 1, word = "Haus", translation = "House", isNew = true)
            val alreadyBidi = forward.copy(id = 2, word = "Baum", translation = "Tree", bidirectional = true)
            val notSelected = forward.copy(id = 3, word = "Auto", translation = "Car")
            val viewModel = buildViewModel()
            coEvery { repository.setBidirectional(any(), any()) } returns Unit
            viewModel.awaitWords(listOf(forward, alreadyBidi, notSelected))

            viewModel.enterSelectionMode(1L)
            viewModel.toggleSelection(2L)
            viewModel.setSelectedWordsBidirectional(true)
            advanceUntilIdle()

            coVerify(exactly = 1) { repository.setBidirectional(setOf(1L), true) }
        }

    @Test
    fun `setSelectedWordsBidirectional false disables only the selected words that are bidirectional`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val bidi = VocabularyItem(id = 1, word = "Haus", translation = "House", isNew = true, bidirectional = true)
            val forwardOnly = bidi.copy(id = 2, word = "Baum", translation = "Tree", bidirectional = false)
            val viewModel = buildViewModel()
            coEvery { repository.setBidirectional(any(), any()) } returns Unit
            viewModel.awaitWords(listOf(bidi, forwardOnly))

            viewModel.enterSelectionMode(1L)
            viewModel.toggleSelection(2L)
            viewModel.setSelectedWordsBidirectional(false)
            advanceUntilIdle()

            coVerify(exactly = 1) { repository.setBidirectional(setOf(1L), false) }
        }

    @Test
    fun `setSelectedWordsBidirectional exits selection mode after the repository call`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item = VocabularyItem(id = 1, word = "Haus", translation = "House", isNew = true)
            val viewModel = buildViewModel()
            coEvery { repository.setBidirectional(any(), any()) } returns Unit
            viewModel.awaitWords(listOf(item))

            viewModel.enterSelectionMode(1L)
            viewModel.setSelectedWordsBidirectional(true)
            advanceUntilIdle()

            assertThat(viewModel.selectionState.value).isEqualTo(WordListViewModel.SelectionState())
        }

    @Test
    fun `setSelectedWordsBidirectional does nothing when nothing is selected`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()
            viewModel.enterSelectionMode(1L)
            viewModel.toggleSelection(1L)

            viewModel.setSelectedWordsBidirectional(true)
            advanceUntilIdle()

            coVerify(exactly = 0) { repository.setBidirectional(any(), any()) }
        }

    @Test
    fun `setSelectedWordsBidirectional does nothing when every selected word is already bidirectional`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item = VocabularyItem(id = 1, word = "Haus", translation = "House", isNew = true, bidirectional = true)
            val viewModel = buildViewModel()
            viewModel.awaitWords(listOf(item))

            viewModel.enterSelectionMode(1L)
            viewModel.setSelectedWordsBidirectional(true)
            advanceUntilIdle()

            coVerify(exactly = 0) { repository.setBidirectional(any(), any()) }
        }

    @Test
    fun `setSelectedWordsBidirectional does nothing when every selected word is already forward only`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item = VocabularyItem(id = 1, word = "Haus", translation = "House", isNew = true, bidirectional = false)
            val viewModel = buildViewModel()
            viewModel.awaitWords(listOf(item))

            viewModel.enterSelectionMode(1L)
            viewModel.setSelectedWordsBidirectional(false)
            advanceUntilIdle()

            coVerify(exactly = 0) { repository.setBidirectional(any(), any()) }
        }

    @Test
    fun `setSelectedWordsBidirectional keeps selection mode active when it is a no-op`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item = VocabularyItem(id = 1, word = "Haus", translation = "House", isNew = true, bidirectional = true)
            val viewModel = buildViewModel()
            viewModel.awaitWords(listOf(item))

            viewModel.enterSelectionMode(1L)
            viewModel.setSelectedWordsBidirectional(true)
            advanceUntilIdle()

            assertThat(viewModel.selectionState.value)
                .isEqualTo(WordListViewModel.SelectionState(isActive = true, selectedIds = setOf(1L)))
        }

    @Test
    fun `setSelectedWordsBidirectional ignores selected ids that are no longer present in words`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item = VocabularyItem(id = 1, word = "Haus", translation = "House", isNew = true)
            val viewModel = buildViewModel()
            coEvery { repository.setBidirectional(any(), any()) } returns Unit
            viewModel.awaitWords(listOf(item))

            viewModel.enterSelectionMode(1L)
            viewModel.toggleSelection(999L)
            viewModel.setSelectedWordsBidirectional(true)
            advanceUntilIdle()

            coVerify(exactly = 1) { repository.setBidirectional(setOf(1L), true) }
        }

    @Test
    fun `setSelectedWordsBidirectional includes selected words regardless of any search filter`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val first = VocabularyItem(id = 1, word = "Haus", translation = "House", isNew = true)
            val second = first.copy(id = 2, word = "Baum", translation = "Tree")
            val viewModel = buildViewModel()
            coEvery { repository.setBidirectional(any(), any()) } returns Unit
            viewModel.awaitWords(listOf(first, second))

            viewModel.enterSelectionMode(1L)
            viewModel.toggleSelection(2L)
            viewModel.setSelectedWordsBidirectional(true)
            advanceUntilIdle()

            coVerify(exactly = 1) { repository.setBidirectional(setOf(1L, 2L), true) }
        }

    @Test
    fun `setSelectedWordsBidirectional with a single selected word delegates with that one id`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val item = VocabularyItem(id = 7, word = "lesen", translation = "read", isNew = false)
            val viewModel = buildViewModel()
            coEvery { repository.setBidirectional(any(), any()) } returns Unit
            viewModel.awaitWords(listOf(item))

            viewModel.enterSelectionMode(7L)
            viewModel.setSelectedWordsBidirectional(true)
            advanceUntilIdle()

            coVerify(exactly = 1) { repository.setBidirectional(setOf(7L), true) }
        }
}
