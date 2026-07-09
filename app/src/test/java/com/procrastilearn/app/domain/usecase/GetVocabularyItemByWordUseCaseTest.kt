package com.procrastilearn.app.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.domain.repository.VocabularyRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class GetVocabularyItemByWordUseCaseTest {
    private val repository: VocabularyRepository = mockk()

    private lateinit var useCase: GetVocabularyItemByWordUseCase

    @Before
    fun setUp() {
        useCase = GetVocabularyItemByWordUseCase(repository)
    }

    @Test
    fun `invoke trims the word and delegates to repository`() =
        runTest {
            val item = VocabularyItem(id = 1L, word = "Haus", translation = "House", isNew = false)
            coEvery { repository.getVocabularyItemByWord("Haus") } returns item

            val result = useCase(" Haus ")

            assertThat(result).isEqualTo(item)
            coVerify(exactly = 1) { repository.getVocabularyItemByWord("Haus") }
        }

    @Test
    fun `invoke returns null when repository has no match`() =
        runTest {
            coEvery { repository.getVocabularyItemByWord("Haus") } returns null

            val result = useCase("Haus")

            assertThat(result).isNull()
        }

    @Test
    fun `invoke returns null for a blank word without touching the repository`() =
        runTest {
            val result = useCase("   ")

            assertThat(result).isNull()
            coVerify(exactly = 0) { repository.getVocabularyItemByWord(any()) }
        }
}
