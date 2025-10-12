package com.procrastilearn.app.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.domain.repository.VocabularyRepository
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class AddVocabularyItemUseCaseTest {
    private val repository: VocabularyRepository = mockk()

    private lateinit var useCase: AddVocabularyItemUseCase

    @Before
    fun setUp() {
        useCase = AddVocabularyItemUseCase(repository)
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `invoke returns success and trims values`() =
        runTest {
            val capturedItem = slot<VocabularyItem>()
            coEvery { repository.addVocabularyItem(capture(capturedItem)) } just Runs

            val result = useCase(" Haus ", " House ")

            assertThat(result.isSuccess).isTrue()
            coVerify(exactly = 1) { repository.addVocabularyItem(any()) }
            assertThat(capturedItem.captured.word).isEqualTo("Haus")
            assertThat(capturedItem.captured.translation).isEqualTo("House")
            assertThat(capturedItem.captured.isNew).isTrue()
            assertThat(capturedItem.captured.id).isEqualTo(0L)
        }

    @Test
    fun `invoke returns failure when word empty`() =
        runTest {
            val result = useCase("   ", "translation")

            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
            coVerify(exactly = 0) { repository.addVocabularyItem(any()) }
        }

    @Test
    fun `invoke returns failure when repository throws`() =
        runTest {
            val error = IllegalStateException("db down")
            coEvery { repository.addVocabularyItem(any()) } throws error

            val result = useCase("word", "translation")

            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()).isEqualTo(error)
            coVerify(exactly = 1) { repository.addVocabularyItem(any()) }
        }
}
