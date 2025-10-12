package com.procrastilearn.app.ui

import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.data.local.prefs.DayCountersStore
import com.procrastilearn.app.data.translation.AiTranslationProvider
import com.procrastilearn.app.data.translation.AiTranslationRequest
import com.procrastilearn.app.domain.usecase.AddVocabularyItemUseCase
import com.procrastilearn.app.utils.MainDispatcherRule
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddWordViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var addVocabularyItemUseCase: AddVocabularyItemUseCase
    private lateinit var prefs: DayCountersStore
    private lateinit var openAiKeyFlow: MutableStateFlow<String?>
    private lateinit var useAiFlow: MutableStateFlow<Boolean>
    private lateinit var promptFlow: MutableStateFlow<String>
    private lateinit var aiTranslationProvider: FakeAiTranslationProvider

    @Before
    fun setUp() {
        addVocabularyItemUseCase = mockk()
        prefs = mockk(relaxed = true)
        openAiKeyFlow = MutableStateFlow(null)
        useAiFlow = MutableStateFlow(false)
        promptFlow = MutableStateFlow("system prompt")
        aiTranslationProvider = FakeAiTranslationProvider()

        every { prefs.readOpenAiApiKey() } returns openAiKeyFlow
        every { prefs.readUseAiForTranslation() } returns useAiFlow
        every { prefs.readOpenAiPrompt() } returns promptFlow
        coEvery { prefs.setUseAiForTranslation(any()) } just Runs
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    private fun buildViewModel(): AddWordViewModel =
        AddWordViewModel(
            addVocabularyItemUseCase,
            prefs,
            aiTranslationProvider,
            mainDispatcherRule.testDispatcher,
        )

    @Test
    fun `init updates AI flags from preferences`() =
        runTest(mainDispatcherRule.testDispatcher) {
            openAiKeyFlow.value = "abc123"
            useAiFlow.value = true

            val viewModel = buildViewModel()

            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.openAiAvailable).isTrue()
            assertThat(state.useAiForTranslation).isTrue()
        }

    @Test
    fun `preference changes propagate to state`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()

            advanceUntilIdle()
            assertThat(viewModel.uiState.value.openAiAvailable).isFalse()
            assertThat(viewModel.uiState.value.useAiForTranslation).isFalse()

            openAiKeyFlow.value = "new-key"
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.openAiAvailable).isTrue()

            openAiKeyFlow.value = ""
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.openAiAvailable).isFalse()

            useAiFlow.value = true
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.useAiForTranslation).isTrue()
        }

    @Test
    fun `onWordChange updates state and clears error`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()

            viewModel.onAddClick()
            assertThat(viewModel.uiState.value.wordError).isEqualTo("Please enter a word")

            viewModel.onWordChange("Haus")

            assertThat(viewModel.uiState.value.word).isEqualTo("Haus")
            assertThat(viewModel.uiState.value.wordError).isNull()
        }

    @Test
    fun `onTranslationChange updates state and clears error`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()

            viewModel.onWordChange("Haus")
            viewModel.onAddClick()
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.translationError).isEqualTo("Please enter a translation")

            viewModel.onTranslationChange("House")

            assertThat(viewModel.uiState.value.translation).isEqualTo("House")
            assertThat(viewModel.uiState.value.translationError).isNull()
        }

    @Test
    fun `onAddClick with blank translation sets error when AI disabled`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()

            viewModel.onWordChange("Haus")
            viewModel.onTranslationChange("")

            viewModel.onAddClick()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.translationError).isEqualTo("Please enter a translation")
            assertThat(state.isLoading).isFalse()
            coVerify(exactly = 0) { addVocabularyItemUseCase.invoke(any(), any()) }
        }

    @Test
    fun `onAddClick success clears fields and emits success state`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { addVocabularyItemUseCase.invoke(any(), any()) } returns Result.success(Unit)
            val viewModel = buildViewModel()
            viewModel.onWordChange("Haus")
            viewModel.onTranslationChange("House")

            viewModel.onAddClick()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.isLoading).isFalse()
            assertThat(state.word).isEmpty()
            assertThat(state.translation).isEmpty()
            assertThat(state.isSuccess).isTrue()
            assertThat(state.successMessage).isEqualTo("Word added successfully!")
            assertThat(state.errorMessage).isNull()
            coVerify { addVocabularyItemUseCase.invoke("Haus", "House") }
        }

    @Test
    fun `onAddClick failure posts error message`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { addVocabularyItemUseCase.invoke(any(), any()) } returns Result.failure(IllegalStateException("boom"))
            val viewModel = buildViewModel()
            viewModel.onWordChange("Haus")
            viewModel.onTranslationChange("House")

            viewModel.onAddClick()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.errorMessage).isEqualTo("boom")
            assertThat(state.isSuccess).isFalse()
            coVerify { addVocabularyItemUseCase.invoke("Haus", "House") }
        }

    @Test
    fun `resetSuccess clears success state but retains preference flags`() =
        runTest(mainDispatcherRule.testDispatcher) {
            openAiKeyFlow.value = "abc"
            useAiFlow.value = true
            coEvery { addVocabularyItemUseCase.invoke(any(), any()) } returns Result.success(Unit)

            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onWordChange("Haus")
            viewModel.onTranslationChange("House")
            viewModel.onAddClick()
            advanceUntilIdle()

            viewModel.resetSuccess()

            val state = viewModel.uiState.value
            assertThat(state.isSuccess).isFalse()
            assertThat(state.successMessage).isNull()
            assertThat(state.errorMessage).isNull()
            assertThat(state.openAiAvailable).isTrue()
            assertThat(state.useAiForTranslation).isTrue()
        }

    @Test
    fun `onUseAiToggle updates state and saves preference`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { addVocabularyItemUseCase.invoke(any(), any()) } returns Result.success(Unit)
            val viewModel = buildViewModel()

            viewModel.onUseAiToggle(true)
            useAiFlow.value = true
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.useAiForTranslation).isTrue()
            coVerify { prefs.setUseAiForTranslation(true) }
        }

    @Test
    fun `onAddClick uses AI translation when available`() =
        runTest(mainDispatcherRule.testDispatcher) {
            openAiKeyFlow.value = "abc"
            useAiFlow.value = true
            coEvery { addVocabularyItemUseCase.invoke(any(), any()) } returns Result.success(Unit)

            aiTranslationProvider.nextTranslation = "House"
            val viewModel = buildViewModel()

            advanceUntilIdle()

            viewModel.onWordChange("Haus")
            viewModel.onTranslationChange("")
            viewModel.onAddClick()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.word).isEmpty()
            assertThat(state.translation).isEmpty()
            assertThat(state.isSuccess).isTrue()
            assertThat(state.successMessage).isEqualTo("Word added successfully!")
            coVerify { addVocabularyItemUseCase.invoke("Haus", "House") }
            assertThat(aiTranslationProvider.requests).hasSize(1)
            val request = aiTranslationProvider.requests.single()
            assertThat(request.apiKey).isEqualTo("abc")
            assertThat(request.systemPrompt).isEqualTo("system prompt")
            assertThat(request.userPrompt).isEqualTo(
                """
                HEADWORD: "Haus"

                Produce ONLY the entry for this headword, in the exact frame and rules above. No extra text.
                """.trimIndent(),
            )
        }

    @Test
    fun `onAddClick falls back when AI translation fails`() =
        runTest(mainDispatcherRule.testDispatcher) {
            openAiKeyFlow.value = "abc"
            useAiFlow.value = true

            val viewModel = buildViewModel()
            aiTranslationProvider.nextError = IllegalStateException("nope")

            advanceUntilIdle()

            viewModel.onWordChange("Haus")
            viewModel.onTranslationChange("")
            viewModel.onAddClick()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.translationError).isEqualTo("Please enter a translation")
            assertThat(state.errorMessage).isNull()
            assertThat(state.isSuccess).isFalse()
            coVerify(exactly = 0) { addVocabularyItemUseCase.invoke(any(), any()) }
            assertThat(aiTranslationProvider.requests).hasSize(1)
            assertThat(aiTranslationProvider.requests.single().userPrompt).contains("Haus")
        }

    private class FakeAiTranslationProvider : AiTranslationProvider {
        var nextTranslation: String = "House"
        var nextError: Throwable? = null
        val requests = mutableListOf<AiTranslationRequest>()

        override suspend fun translate(request: AiTranslationRequest): String {
            requests += request
            nextError?.let { throw it }
            return nextTranslation
        }
    }
}
