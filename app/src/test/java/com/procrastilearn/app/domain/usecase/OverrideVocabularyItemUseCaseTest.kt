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

class OverrideVocabularyItemUseCaseTest {
    private val repository: VocabularyRepository = mockk()

    private lateinit var useCase: OverrideVocabularyItemUseCase

    private val existingItem = VocabularyItem(id = 1L, word = "Haus", translation = "House", isNew = false)

    @Before
    fun setUp() {
        useCase = OverrideVocabularyItemUseCase(repository)
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `invoke returns success and updates trimmed word and translation`() =
        runTest {
            val capturedUpdate = slot<VocabularyItem>()
            val capturedReset = slot<VocabularyItem>()
            coEvery { repository.updateVocabularyItem(capture(capturedUpdate)) } just Runs
            coEvery { repository.resetVocabularyProgress(capture(capturedReset)) } just Runs

            val result = useCase(existingItem, " Wohnung ", " Apartment ")

            assertThat(result.isSuccess).isTrue()
            coVerify(exactly = 1) { repository.updateVocabularyItem(any()) }
            coVerify(exactly = 1) { repository.resetVocabularyProgress(any()) }
            assertThat(capturedUpdate.captured.id).isEqualTo(existingItem.id)
            assertThat(capturedUpdate.captured.word).isEqualTo("Wohnung")
            assertThat(capturedUpdate.captured.translation).isEqualTo("Apartment")
            assertThat(capturedReset.captured).isEqualTo(capturedUpdate.captured)
        }

    @Test
    fun `invoke returns failure when new word is blank`() =
        runTest {
            val result = useCase(existingItem, "   ", "Apartment")

            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
            coVerify(exactly = 0) { repository.updateVocabularyItem(any()) }
            coVerify(exactly = 0) { repository.resetVocabularyProgress(any()) }
        }

    @Test
    fun `invoke returns failure when new translation is blank`() =
        runTest {
            val result = useCase(existingItem, "Wohnung", "   ")

            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
            coVerify(exactly = 0) { repository.updateVocabularyItem(any()) }
            coVerify(exactly = 0) { repository.resetVocabularyProgress(any()) }
        }

    @Test
    fun `invoke returns failure when repository throws`() =
        runTest {
            val error = IllegalStateException("db down")
            coEvery { repository.updateVocabularyItem(any()) } throws error

            val result = useCase(existingItem, "Wohnung", "Apartment")

            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()).isEqualTo(error)
            coVerify(exactly = 1) { repository.updateVocabularyItem(any()) }
            coVerify(exactly = 0) { repository.resetVocabularyProgress(any()) }
        }
}
