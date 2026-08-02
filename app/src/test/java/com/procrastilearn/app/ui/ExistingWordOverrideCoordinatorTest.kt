package com.procrastilearn.app.ui

import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.domain.model.AiTranslationDirection
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.domain.usecase.AddVocabularyItemUseCase
import com.procrastilearn.app.domain.usecase.GenerateAiTranslationUseCase
import com.procrastilearn.app.domain.usecase.GetVocabularyItemByWordUseCase
import com.procrastilearn.app.domain.usecase.OverrideVocabularyItemUseCase
import com.procrastilearn.app.domain.usecase.VocabularyEntryUseCases
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class ExistingWordOverrideCoordinatorTest {
    private lateinit var getVocabularyItemByWordUseCase: GetVocabularyItemByWordUseCase
    private lateinit var overrideVocabularyItemUseCase: OverrideVocabularyItemUseCase
    private lateinit var generateAiTranslationUseCase: GenerateAiTranslationUseCase
    private lateinit var vocabularyEntryUseCases: VocabularyEntryUseCases
    private lateinit var coordinator: ExistingWordOverrideCoordinator

    private val existingItem = VocabularyItem(id = 1L, word = "Haus", translation = "House", isNew = false)
    private val cardOptions = BidirectionalCardOptions(bidirectional = false, backwardPromptOverride = null, backwardAnswerOverride = null)

    @Before
    fun setUp() {
        getVocabularyItemByWordUseCase = mockk()
        overrideVocabularyItemUseCase = mockk()
        generateAiTranslationUseCase = mockk()
        vocabularyEntryUseCases = VocabularyEntryUseCases(mockk<AddVocabularyItemUseCase>(), getVocabularyItemByWordUseCase, overrideVocabularyItemUseCase)
        coordinator = ExistingWordOverrideCoordinator(vocabularyEntryUseCases, generateAiTranslationUseCase)
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `resolveForSubmission returns NoConflict when word is not taken`() =
        runTest {
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns null

            val resolution =
                coordinator.resolveForSubmission("Haus", "House", AiTranslationDirection.TARGET_TO_NATIVE, fromPreview = false) { cardOptions }

            assertThat(resolution).isEqualTo(SubmissionResolution.NoConflict)
        }

    @Test
    fun `resolveForSubmission returns ConfirmationRequired on first conflict`() =
        runTest {
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns existingItem

            val resolution =
                coordinator.resolveForSubmission("Haus", "House", AiTranslationDirection.TARGET_TO_NATIVE, fromPreview = false) { cardOptions }

            assertThat(resolution).isEqualTo(SubmissionResolution.ConfirmationRequired("Haus"))
        }

    @Test
    fun `resolveForSubmission applies override and returns Overridden once acknowledged`() =
        runTest {
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns existingItem
            coEvery { overrideVocabularyItemUseCase.invoke(existingItem, "Haus", "House", any(), any(), any()) } returns Result.success(Unit)
            coordinator.acknowledge("Haus")

            val resolution =
                coordinator.resolveForSubmission("Haus", "House", AiTranslationDirection.TARGET_TO_NATIVE, fromPreview = true) { cardOptions }

            assertThat(resolution).isEqualTo(SubmissionResolution.Overridden(fromPreview = true))
        }

    @Test
    fun `resolveForSubmission returns OverrideFailed when override fails`() =
        runTest {
            val error = IllegalStateException("db down")
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns existingItem
            coEvery { overrideVocabularyItemUseCase.invoke(existingItem, "Haus", "House", any(), any(), any()) } returns Result.failure(error)
            coordinator.acknowledge("Haus")

            val resolution =
                coordinator.resolveForSubmission("Haus", "House", AiTranslationDirection.TARGET_TO_NATIVE, fromPreview = false) { cardOptions }

            assertThat(resolution).isEqualTo(SubmissionResolution.OverrideFailed(error, fromPreview = false))
        }

    @Test
    fun `resolveForSubmission returns LookupFailed when lookup throws`() =
        runTest {
            val error = IllegalStateException("db down")
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } throws error

            val resolution =
                coordinator.resolveForSubmission("Haus", "House", AiTranslationDirection.TARGET_TO_NATIVE, fromPreview = false) { cardOptions }

            assertThat(resolution).isEqualTo(SubmissionResolution.LookupFailed(error))
        }

    @Test
    fun `resolveForSubmission acknowledgment is case-insensitive`() =
        runTest {
            coEvery { getVocabularyItemByWordUseCase.invoke("HAUS") } returns existingItem
            coEvery { overrideVocabularyItemUseCase.invoke(existingItem, "HAUS", "House", any(), any(), any()) } returns Result.success(Unit)
            coordinator.acknowledge("haus")

            val resolution =
                coordinator.resolveForSubmission("HAUS", "House", AiTranslationDirection.TARGET_TO_NATIVE, fromPreview = false) { cardOptions }

            assertThat(resolution).isEqualTo(SubmissionResolution.Overridden(fromPreview = false))
        }

    @Test
    fun `resolveForSubmission does not honor an acknowledgment for a different word`() =
        runTest {
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns existingItem
            coordinator.acknowledge("Wohnung")

            val resolution =
                coordinator.resolveForSubmission("Haus", "House", AiTranslationDirection.TARGET_TO_NATIVE, fromPreview = false) { cardOptions }

            assertThat(resolution).isEqualTo(SubmissionResolution.ConfirmationRequired("Haus"))
            coVerify(exactly = 0) { overrideVocabularyItemUseCase.invoke(any(), any(), any(), any(), any(), any()) }
        }

    @Test
    fun `resolveForSubmission consumes the acknowledgment after a successful override`() =
        runTest {
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns existingItem
            coEvery { overrideVocabularyItemUseCase.invoke(existingItem, "Haus", "House", any(), any(), any()) } returns Result.success(Unit)
            coordinator.acknowledge("Haus")
            coordinator.resolveForSubmission("Haus", "House", AiTranslationDirection.TARGET_TO_NATIVE, fromPreview = false) { cardOptions }

            val secondResolution =
                coordinator.resolveForSubmission("Haus", "House", AiTranslationDirection.TARGET_TO_NATIVE, fromPreview = false) { cardOptions }

            assertThat(secondResolution).isEqualTo(SubmissionResolution.ConfirmationRequired("Haus"))
        }

    @Test
    fun `resolveForSubmission consumes the acknowledgment even when the override fails`() =
        runTest {
            val error = IllegalStateException("db down")
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns existingItem
            coEvery { overrideVocabularyItemUseCase.invoke(existingItem, "Haus", "House", any(), any(), any()) } returns Result.failure(error)
            coordinator.acknowledge("Haus")
            coordinator.resolveForSubmission("Haus", "House", AiTranslationDirection.TARGET_TO_NATIVE, fromPreview = false) { cardOptions }

            val secondResolution =
                coordinator.resolveForSubmission("Haus", "House", AiTranslationDirection.TARGET_TO_NATIVE, fromPreview = false) { cardOptions }

            assertThat(secondResolution).isEqualTo(SubmissionResolution.ConfirmationRequired("Haus"))
        }

    @Test
    fun `resolveForSubmission passes the resolved bidirectional card options through to the override`() =
        runTest {
            val capturedBidirectional = slot<Boolean>()
            val capturedBackwardPrompt = slot<String>()
            val capturedBackwardAnswer = slot<String>()
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns existingItem
            coEvery {
                overrideVocabularyItemUseCase.invoke(
                    existingItem,
                    "Haus",
                    "House",
                    capture(capturedBidirectional),
                    capture(capturedBackwardPrompt),
                    capture(capturedBackwardAnswer),
                )
            } returns Result.success(Unit)
            coordinator.acknowledge("Haus")
            val customOptions = BidirectionalCardOptions(bidirectional = true, backwardPromptOverride = "prompt", backwardAnswerOverride = "answer")

            coordinator.resolveForSubmission("Haus", "House", AiTranslationDirection.TARGET_TO_NATIVE, fromPreview = false) { customOptions }

            assertThat(capturedBidirectional.captured).isTrue()
            assertThat(capturedBackwardPrompt.captured).isEqualTo("prompt")
            assertThat(capturedBackwardAnswer.captured).isEqualTo("answer")
        }

    @Test
    fun `checkBeforeAiTranslationRequest prompts even when the word was already acknowledged`() =
        runTest {
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns existingItem
            coordinator.acknowledge("Haus")

            val preflight = coordinator.checkBeforeAiTranslationRequest("Haus", AiTranslationDirection.TARGET_TO_NATIVE)

            assertThat(preflight).isEqualTo(ExistingWordPreflight.ConfirmationRequired("Haus"))
        }

    @Test
    fun `checkBeforeAiTranslationRequest returns NoConflict when word is not taken`() =
        runTest {
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns null

            val preflight = coordinator.checkBeforeAiTranslationRequest("Haus", AiTranslationDirection.TARGET_TO_NATIVE)

            assertThat(preflight).isEqualTo(ExistingWordPreflight.NoConflict)
            assertThat(coordinator.hasPendingOverride()).isFalse()
        }

    @Test
    fun `checkBeforeAiTranslationRequest returns LookupFailed when lookup throws`() =
        runTest {
            val error = IllegalStateException("db down")
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } throws error

            val preflight = coordinator.checkBeforeAiTranslationRequest("Haus", AiTranslationDirection.TARGET_TO_NATIVE)

            assertThat(preflight).isEqualTo(ExistingWordPreflight.LookupFailed(error))
        }

    @Test
    fun `clearAcknowledgement does not clear a pending override`() =
        runTest {
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns existingItem
            coordinator.resolveForSubmission("Haus", "House", AiTranslationDirection.TARGET_TO_NATIVE, fromPreview = false) { cardOptions }

            coordinator.clearAcknowledgement()

            assertThat(coordinator.hasPendingOverride()).isTrue()
        }

    @Test
    fun `resetForNewWord clears an acknowledgment so submission prompts again`() =
        runTest {
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns existingItem
            coordinator.acknowledge("Haus")

            coordinator.resetForNewWord()
            val resolution =
                coordinator.resolveForSubmission("Haus", "House", AiTranslationDirection.TARGET_TO_NATIVE, fromPreview = false) { cardOptions }

            assertThat(resolution).isEqualTo(SubmissionResolution.ConfirmationRequired("Haus"))
        }

    @Test
    fun `resetForNewWord clears a pending override`() =
        runTest {
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns existingItem
            coordinator.resolveForSubmission("Haus", "House", AiTranslationDirection.TARGET_TO_NATIVE, fromPreview = false) { cardOptions }
            assertThat(coordinator.hasPendingOverride()).isTrue()

            coordinator.resetForNewWord()

            assertThat(coordinator.hasPendingOverride()).isFalse()
        }

    @Test
    fun `proceedWithPendingOverride returns NoPendingOverride when nothing is pending`() =
        runTest {
            val result = coordinator.proceedWithPendingOverride { cardOptions }

            assertThat(result).isEqualTo(OverrideProceedResult.NoPendingOverride)
        }

    @Test
    fun `proceedWithPendingOverride resolves an AI translation when none was captured`() =
        runTest {
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns existingItem
            coordinator.checkBeforeAiTranslationRequest("Haus", AiTranslationDirection.TARGET_TO_NATIVE)
            coEvery { generateAiTranslationUseCase.invoke("Haus", AiTranslationDirection.TARGET_TO_NATIVE) } returns "House"
            coEvery { overrideVocabularyItemUseCase.invoke(existingItem, "Haus", "House", any(), any(), any()) } returns Result.success(Unit)

            val result = coordinator.proceedWithPendingOverride { cardOptions }

            assertThat(result).isEqualTo(OverrideProceedResult.Overridden)
            coVerify(exactly = 1) { generateAiTranslationUseCase.invoke("Haus", AiTranslationDirection.TARGET_TO_NATIVE) }
        }

    @Test
    fun `proceedWithPendingOverride uses an already-known translation without calling AI`() =
        runTest {
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns existingItem
            coordinator.resolveForSubmission("Haus", "Home", AiTranslationDirection.TARGET_TO_NATIVE, fromPreview = false) { cardOptions }
            coEvery { overrideVocabularyItemUseCase.invoke(existingItem, "Haus", "Home", any(), any(), any()) } returns Result.success(Unit)

            val result = coordinator.proceedWithPendingOverride { cardOptions }

            assertThat(result).isEqualTo(OverrideProceedResult.Overridden)
            coVerify(exactly = 0) { generateAiTranslationUseCase.invoke(any(), any()) }
        }

    @Test
    fun `proceedWithPendingOverride returns TranslationFailed when AI resolution is blank`() =
        runTest {
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns existingItem
            coordinator.checkBeforeAiTranslationRequest("Haus", AiTranslationDirection.TARGET_TO_NATIVE)
            coEvery { generateAiTranslationUseCase.invoke("Haus", AiTranslationDirection.TARGET_TO_NATIVE) } returns "   "

            val result = coordinator.proceedWithPendingOverride { cardOptions }

            assertThat(result).isInstanceOf(OverrideProceedResult.TranslationFailed::class.java)
            assertThat((result as OverrideProceedResult.TranslationFailed).error.message).isNull()
        }

    @Test
    fun `proceedWithPendingOverride returns OverrideFailed when applying the override fails`() =
        runTest {
            val error = IllegalStateException("db down")
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns existingItem
            coordinator.resolveForSubmission("Haus", "House", AiTranslationDirection.TARGET_TO_NATIVE, fromPreview = false) { cardOptions }
            coEvery { overrideVocabularyItemUseCase.invoke(existingItem, "Haus", "House", any(), any(), any()) } returns Result.failure(error)

            val result = coordinator.proceedWithPendingOverride { cardOptions }

            assertThat(result).isEqualTo(OverrideProceedResult.OverrideFailed(error))
            assertThat(coordinator.hasPendingOverride()).isFalse()
        }

    @Test
    fun `hasPendingOverride is false before any interaction`() {
        assertThat(coordinator.hasPendingOverride()).isFalse()
    }

    @Test
    fun `cancelPendingOverride returns false when nothing is pending`() {
        val fromPreview = coordinator.cancelPendingOverride()

        assertThat(fromPreview).isFalse()
    }

    @Test
    fun `cancelPendingOverride reports whether the pending override came from preview`() =
        runTest {
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns existingItem
            coordinator.resolveForSubmission("Haus", "House", AiTranslationDirection.TARGET_TO_NATIVE, fromPreview = true) { cardOptions }

            val fromPreview = coordinator.cancelPendingOverride()

            assertThat(fromPreview).isTrue()
            assertThat(coordinator.hasPendingOverride()).isFalse()
        }
}
