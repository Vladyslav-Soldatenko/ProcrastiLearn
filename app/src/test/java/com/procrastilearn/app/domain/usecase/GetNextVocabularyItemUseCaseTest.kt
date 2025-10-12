package com.procrastilearn.app.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.domain.repository.VocabularyRepository
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class GetNextVocabularyItemUseCaseTest {
    private val repository: VocabularyRepository = mockk()

    private lateinit var useCase: GetNextVocabularyItemUseCase

    @Before
    fun setUp() {
        useCase = GetNextVocabularyItemUseCase(repository)
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `invoke returns success when repository succeeds`() =
        runTest {
            val expectedItem =
                VocabularyItem(
                    id = 1,
                    word = "word",
                    translation = "translation",
                    isNew = false,
                )
            coEvery { repository.getNextVocabularyItem() } returns expectedItem

            val result = useCase()

            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrNull()).isEqualTo(expectedItem)
            coVerify(exactly = 1) { repository.getNextVocabularyItem() }
        }

    @Test
    fun `invoke returns failure when repository throws`() =
        runTest {
            val error = IllegalStateException("no items")
            coEvery { repository.getNextVocabularyItem() } throws error

            val result = useCase()

            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()).isEqualTo(error)
            coVerify(exactly = 1) { repository.getNextVocabularyItem() }
        }
}
