package com.procrastilearn.app.ui

import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.data.connectivity.NetworkConnectivityObserver
import com.procrastilearn.app.data.local.prefs.LanguagePreferencesStore
import com.procrastilearn.app.data.local.prefs.OpenAiPreferencesStore
import com.procrastilearn.app.data.text.ProcessTextEventBus
import com.procrastilearn.app.data.translation.AiTranslationProvider
import com.procrastilearn.app.data.translation.AiTranslationRequest
import com.procrastilearn.app.domain.model.AiTranslationDirection
import com.procrastilearn.app.domain.model.Language
import com.procrastilearn.app.domain.model.LanguagePair
import com.procrastilearn.app.domain.model.PendingWord
import com.procrastilearn.app.domain.usecase.AddVocabularyItemUseCase
import com.procrastilearn.app.domain.usecase.DeletePendingWordUseCase
import com.procrastilearn.app.domain.usecase.GenerateAiTranslationUseCase
import com.procrastilearn.app.domain.usecase.GetVocabularyItemByWordUseCase
import com.procrastilearn.app.domain.usecase.ObservePendingWordsUseCase
import com.procrastilearn.app.domain.usecase.OverrideVocabularyItemUseCase
import com.procrastilearn.app.domain.usecase.QueuePendingWordUseCase
import com.procrastilearn.app.utils.MainDispatcherRule
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
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
class AddWordViewModelProcessTextTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var addVocabularyItemUseCase: AddVocabularyItemUseCase
    private lateinit var getVocabularyItemByWordUseCase: GetVocabularyItemByWordUseCase
    private lateinit var overrideVocabularyItemUseCase: OverrideVocabularyItemUseCase
    private lateinit var queuePendingWordUseCase: QueuePendingWordUseCase
    private lateinit var observePendingWordsUseCase: ObservePendingWordsUseCase
    private lateinit var deletePendingWordUseCase: DeletePendingWordUseCase
    private lateinit var connectivityObserver: NetworkConnectivityObserver
    private lateinit var openAiStore: OpenAiPreferencesStore
    private lateinit var languagePreferencesStore: LanguagePreferencesStore
    private lateinit var generateAiTranslationUseCase: GenerateAiTranslationUseCase
    private lateinit var openAiKeyFlow: MutableStateFlow<String?>
    private lateinit var useAiFlow: MutableStateFlow<Boolean>
    private lateinit var directionFlow: MutableStateFlow<AiTranslationDirection>
    private lateinit var onlineFlow: MutableStateFlow<Boolean>
    private lateinit var pendingWordsFlow: MutableStateFlow<List<PendingWord>>
    private lateinit var aiTranslationProvider: FakeAiTranslationProvider
    private lateinit var processTextEventBus: ProcessTextEventBus

    @Before
    fun setUp() {
        addVocabularyItemUseCase = mockk()
        getVocabularyItemByWordUseCase = mockk()
        overrideVocabularyItemUseCase = mockk()
        queuePendingWordUseCase = mockk()
        observePendingWordsUseCase = mockk()
        deletePendingWordUseCase = mockk()
        connectivityObserver = mockk()
        openAiStore = mockk(relaxed = true)
        openAiKeyFlow = MutableStateFlow(null)
        useAiFlow = MutableStateFlow(false)
        directionFlow = MutableStateFlow(AiTranslationDirection.TARGET_TO_NATIVE)
        onlineFlow = MutableStateFlow(true)
        pendingWordsFlow = MutableStateFlow(emptyList())
        aiTranslationProvider = FakeAiTranslationProvider()
        languagePreferencesStore = mockk(relaxed = true)
        generateAiTranslationUseCase =
            GenerateAiTranslationUseCase(aiTranslationProvider, openAiStore, mainDispatcherRule.testDispatcher)
        processTextEventBus = ProcessTextEventBus()

        every { openAiStore.readOpenAiApiKey() } returns openAiKeyFlow
        every { openAiStore.readUseAiForTranslation() } returns useAiFlow
        every { openAiStore.readOpenAiPrompt() } returns MutableStateFlow("system prompt")
        every { openAiStore.readOpenAiReversePrompt() } returns MutableStateFlow("reverse system prompt")
        every { openAiStore.readAiTranslationDirection() } returns directionFlow
        every { languagePreferencesStore.readLanguagePair() } returns
            MutableStateFlow(LanguagePair(Language.ENGLISH, Language.RUSSIAN))
        coEvery { getVocabularyItemByWordUseCase.invoke(any()) } returns null
        every { connectivityObserver.observe() } returns onlineFlow
        every { observePendingWordsUseCase.invoke() } returns pendingWordsFlow
        coEvery { queuePendingWordUseCase.invoke(any(), any()) } just Runs
        coEvery { deletePendingWordUseCase.invoke(any()) } just Runs
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    private fun buildViewModel(): AddWordViewModel =
        AddWordViewModel(
            addVocabularyItemUseCase,
            getVocabularyItemByWordUseCase,
            overrideVocabularyItemUseCase,
            openAiStore,
            languagePreferencesStore,
            generateAiTranslationUseCase,
            queuePendingWordUseCase,
            observePendingWordsUseCase,
            deletePendingWordUseCase,
            connectivityObserver,
            processTextEventBus,
        )

    @Test
    fun `process text event with AI active prefills word and triggers preview`() =
        runTest(mainDispatcherRule.testDispatcher) {
            openAiKeyFlow.value = "abc"
            useAiFlow.value = true
            aiTranslationProvider.nextTranslation = "House"
            val viewModel = buildViewModel()
            advanceUntilIdle()

            processTextEventBus.submit("Haus")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.word).isEqualTo("Haus")
            assertThat(state.isPreviewVisible).isTrue()
            assertThat(state.previewContent?.word).isEqualTo("Haus")
            assertThat(state.previewContent?.translation).isEqualTo("House")
            assertThat(aiTranslationProvider.requests).hasSize(1)
        }

    @Test
    fun `process text event with AI disabled prefills word and leaves translation empty`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()
            advanceUntilIdle()

            processTextEventBus.submit("Haus")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.word).isEqualTo("Haus")
            assertThat(state.translation).isEmpty()
            assertThat(state.isPreviewVisible).isFalse()
            assertThat(state.previewContent).isNull()
            assertThat(aiTranslationProvider.requests).isEmpty()
        }

    @Test
    fun `process text event with AI toggle on but no api key does not trigger preview`() =
        runTest(mainDispatcherRule.testDispatcher) {
            useAiFlow.value = true
            val viewModel = buildViewModel()
            advanceUntilIdle()

            processTextEventBus.submit("Haus")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.word).isEqualTo("Haus")
            assertThat(state.translation).isEmpty()
            assertThat(state.isPreviewVisible).isFalse()
            assertThat(aiTranslationProvider.requests).isEmpty()
        }

    @Test
    fun `process text event while offline in AI mode prefills without triggering preview`() =
        runTest(mainDispatcherRule.testDispatcher) {
            openAiKeyFlow.value = "abc"
            useAiFlow.value = true
            onlineFlow.value = false
            val viewModel = buildViewModel()
            advanceUntilIdle()

            processTextEventBus.submit("Haus")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.word).isEqualTo("Haus")
            assertThat(state.translation).isEmpty()
            assertThat(state.isPreviewVisible).isFalse()
            assertThat(aiTranslationProvider.requests).isEmpty()
        }

    @Test
    fun `process text event trims whitespace from selected text`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()
            advanceUntilIdle()

            processTextEventBus.submit("  Haus  \n")
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.word).isEqualTo("Haus")
        }

    @Test
    fun `process text event with blank text is ignored`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()
            viewModel.onWordChange("existing")
            advanceUntilIdle()

            processTextEventBus.submit("   ")
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.word).isEqualTo("existing")
        }

    @Test
    fun `process text event is consumed from the bus after handling`() =
        runTest(mainDispatcherRule.testDispatcher) {
            buildViewModel()
            advanceUntilIdle()

            processTextEventBus.submit("Haus")
            advanceUntilIdle()

            assertThat(processTextEventBus.events.value).isNull()
        }

    @Test
    fun `process text event overwrites a previously prefilled word and preview state`() =
        runTest(mainDispatcherRule.testDispatcher) {
            openAiKeyFlow.value = "abc"
            useAiFlow.value = true
            aiTranslationProvider.nextTranslation = "House"
            val viewModel = buildViewModel()
            advanceUntilIdle()

            processTextEventBus.submit("Haus")
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.isPreviewVisible).isTrue()

            aiTranslationProvider.nextTranslation = "Cat"
            processTextEventBus.submit("Katze")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.word).isEqualTo("Katze")
            assertThat(state.previewContent?.word).isEqualTo("Katze")
            assertThat(state.previewContent?.translation).isEqualTo("Cat")
        }

    @Test
    fun `process text event received before AI preferences load still applies once they arrive`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()

            processTextEventBus.submit("Haus")
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.word).isEqualTo("Haus")
            assertThat(viewModel.uiState.value.isPreviewVisible).isFalse()
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
