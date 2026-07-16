package com.procrastilearn.app.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.domain.model.UndoResult
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.domain.repository.VocabularyRepository
import io.github.openspacedrepetition.Rating
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class UndoLastRatingUseCaseTest {
    private val repository: VocabularyRepository = mockk()

    private lateinit var useCase: UndoLastRatingUseCase

    @Before
    fun setUp() {
        useCase = UndoLastRatingUseCase(repository)
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `invoke returns success with the restored item when repository has something to undo`() =
        runTest {
            val item = VocabularyItem(id = 7, word = "Baum", translation = "tree", isNew = false)
            val undoResult = UndoResult(item = item, revertedRating = Rating.EASY)
            coEvery { repository.undoLastRating() } returns undoResult

            val result = useCase()

            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrNull()).isEqualTo(undoResult)
            coVerify(exactly = 1) { repository.undoLastRating() }
        }

    @Test
    fun `invoke returns success with null when there is nothing to undo`() =
        runTest {
            coEvery { repository.undoLastRating() } returns null

            val result = useCase()

            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrNull()).isNull()
        }

    @Test
    fun `invoke returns failure when repository throws`() =
        runTest {
            val error = IllegalStateException("boom")
            coEvery { repository.undoLastRating() } throws error

            val result = useCase()

            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()).isEqualTo(error)
        }
}
