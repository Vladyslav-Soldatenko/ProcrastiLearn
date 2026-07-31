package com.procrastilearn.app.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.domain.repository.VocabularyCatalogRepository
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
    private val repository: VocabularyCatalogRepository = mockk()

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
    fun `invoke without new bidirectional or override args preserves the existing item's values`() =
        runTest {
            val bidirectionalItem =
                existingItem.copy(
                    bidirectional = true,
                    backwardPromptOverride = "existing prompt",
                    backwardAnswerOverride = "existing answer",
                )
            val capturedUpdate = slot<VocabularyItem>()
            coEvery { repository.updateVocabularyItem(capture(capturedUpdate)) } just Runs
            coEvery { repository.resetVocabularyProgress(any()) } just Runs

            useCase(bidirectionalItem, "Wohnung", "Apartment")

            assertThat(capturedUpdate.captured.bidirectional).isTrue()
            assertThat(capturedUpdate.captured.backwardPromptOverride).isEqualTo("existing prompt")
            assertThat(capturedUpdate.captured.backwardAnswerOverride).isEqualTo("existing answer")
        }

    @Test
    fun `invoke with explicit bidirectional overrides the existing item's flag`() =
        runTest {
            val capturedUpdate = slot<VocabularyItem>()
            coEvery { repository.updateVocabularyItem(capture(capturedUpdate)) } just Runs
            coEvery { repository.resetVocabularyProgress(any()) } just Runs

            useCase(existingItem, "Wohnung", "Apartment", bidirectional = true)

            assertThat(capturedUpdate.captured.bidirectional).isTrue()
        }

    @Test
    fun `invoke with explicit override text overrides the existing item's override fields`() =
        runTest {
            val capturedUpdate = slot<VocabularyItem>()
            coEvery { repository.updateVocabularyItem(capture(capturedUpdate)) } just Runs
            coEvery { repository.resetVocabularyProgress(any()) } just Runs

            useCase(
                existingItem,
                "Wohnung",
                "Apartment",
                bidirectional = true,
                backwardPromptOverride = " new prompt ",
                backwardAnswerOverride = "new answer",
            )

            assertThat(capturedUpdate.captured.backwardPromptOverride).isEqualTo("new prompt")
            assertThat(capturedUpdate.captured.backwardAnswerOverride).isEqualTo("new answer")
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
