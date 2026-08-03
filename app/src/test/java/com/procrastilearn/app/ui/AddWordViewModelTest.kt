package com.procrastilearn.app.ui

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.R
import com.procrastilearn.app.data.connectivity.NetworkConnectivityObserver
import com.procrastilearn.app.data.local.prefs.LanguagePreferencesStore
import com.procrastilearn.app.data.local.prefs.OpenAiPreferencesStore
import com.procrastilearn.app.data.local.prefs.TranslationPreferences
import com.procrastilearn.app.data.translation.AiTranslationProvider
import com.procrastilearn.app.data.translation.AiTranslationRequest
import com.procrastilearn.app.domain.model.AiTranslationDirection
import com.procrastilearn.app.domain.model.Language
import com.procrastilearn.app.domain.model.LanguagePair
import com.procrastilearn.app.domain.model.PendingWord
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.domain.usecase.AddVocabularyItemUseCase
import com.procrastilearn.app.domain.usecase.DeletePendingWordUseCase
import com.procrastilearn.app.domain.usecase.GenerateAiTranslationUseCase
import com.procrastilearn.app.domain.usecase.GetVocabularyItemByWordUseCase
import com.procrastilearn.app.domain.usecase.ObservePendingWordsUseCase
import com.procrastilearn.app.domain.usecase.OverrideVocabularyItemUseCase
import com.procrastilearn.app.domain.usecase.PendingWordUseCases
import com.procrastilearn.app.domain.usecase.QueuePendingWordUseCase
import com.procrastilearn.app.domain.usecase.VocabularyEntryUseCases
import com.procrastilearn.app.utils.MainDispatcherRule
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LargeClass")
class AddWordViewModelTest {
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
    private lateinit var promptFlow: MutableStateFlow<String>
    private lateinit var reversePromptFlow: MutableStateFlow<String>
    private lateinit var directionFlow: MutableStateFlow<AiTranslationDirection>
    private lateinit var onlineFlow: MutableStateFlow<Boolean>
    private lateinit var pendingWordsFlow: MutableStateFlow<List<PendingWord>>
    private lateinit var languagePairFlow: MutableStateFlow<LanguagePair?>
    private lateinit var aiTranslationProvider: FakeAiTranslationProvider
    private lateinit var context: Context

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
        promptFlow = MutableStateFlow("system prompt")
        reversePromptFlow = MutableStateFlow("reverse system prompt")
        directionFlow = MutableStateFlow(AiTranslationDirection.TARGET_TO_NATIVE)
        onlineFlow = MutableStateFlow(true)
        pendingWordsFlow = MutableStateFlow(emptyList())
        languagePairFlow = MutableStateFlow(LanguagePair(Language.ENGLISH, Language.RUSSIAN))
        aiTranslationProvider = FakeAiTranslationProvider()
        languagePreferencesStore = mockk(relaxed = true)
        generateAiTranslationUseCase =
            GenerateAiTranslationUseCase(
                aiTranslationProvider,
                TranslationPreferences(openAiStore, languagePreferencesStore),
                mainDispatcherRule.testDispatcher,
            )
        context = mockk()
        every { context.getString(R.string.add_word_error_word_required) } returns "Please enter a word."
        every { context.getString(R.string.add_word_error_translation_required) } returns "Please enter a translation."
        every { context.getString(R.string.add_word_error_preview_failed) } returns "Failed to generate preview"
        every { context.getString(R.string.add_word_error_translation_failed) } returns "Failed to generate translation"
        every { context.getString(R.string.add_word_error_update_failed) } returns "Failed to update word"
        every { context.getString(R.string.add_word_error_add_failed) } returns "Failed to add word"
        every { context.getString(R.string.add_word_error_lookup_failed) } returns "Failed to check existing words"
        every { context.getString(R.string.add_word_success_added) } returns "Word added successfully!"
        every { context.getString(R.string.add_word_success_updated) } returns "Word updated and progress reset!"
        every { context.getString(R.string.add_word_success_pending) } returns
            "Saved. Translation will be generated once you're back online."

        every { openAiStore.readOpenAiApiKey() } returns openAiKeyFlow
        every { openAiStore.readUseAiForTranslation() } returns useAiFlow
        every { openAiStore.readOpenAiPrompt() } returns promptFlow
        every { openAiStore.readOpenAiReversePrompt() } returns reversePromptFlow
        every { openAiStore.readAiTranslationDirection() } returns directionFlow
        every { languagePreferencesStore.readLanguagePair() } returns languagePairFlow
        coEvery { openAiStore.setUseAiForTranslation(any()) } just Runs
        coEvery { openAiStore.setAiTranslationDirection(any()) } just Runs
        coEvery { getVocabularyItemByWordUseCase.invoke(any()) } returns null
        coEvery { overrideVocabularyItemUseCase.invoke(any(), any(), any(), any(), any(), any()) } returns
            Result.success(Unit)
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
            VocabularyEntryUseCases(
                addVocabularyItemUseCase,
                getVocabularyItemByWordUseCase,
                overrideVocabularyItemUseCase,
            ),
            PendingWordUseCases(queuePendingWordUseCase, observePendingWordsUseCase, deletePendingWordUseCase),
            TranslationPreferences(openAiStore, languagePreferencesStore),
            generateAiTranslationUseCase,
            connectivityObserver,
            context,
        )

    @Test
    fun `init updates AI flags from preferences`() =
        runTest(mainDispatcherRule.testDispatcher) {
            openAiKeyFlow.value = "abc123"
            useAiFlow.value = true
            directionFlow.value = AiTranslationDirection.NATIVE_TO_TARGET

            val viewModel = buildViewModel()

            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.openAiAvailable).isTrue()
            assertThat(state.useAiForTranslation).isTrue()
            assertThat(state.translationDirection).isEqualTo(AiTranslationDirection.NATIVE_TO_TARGET)
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

            directionFlow.value = AiTranslationDirection.NATIVE_TO_TARGET
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.translationDirection).isEqualTo(AiTranslationDirection.NATIVE_TO_TARGET)
        }

    @Test
    fun `isOnline reflects connectivity observer emissions`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.isOnline).isTrue()

            onlineFlow.value = false
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.isOnline).isFalse()

            onlineFlow.value = true
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.isOnline).isTrue()
        }

    @Test
    fun `pendingWords reflects use case emissions`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.pendingWords).isEmpty()

            pendingWordsFlow.value =
                listOf(PendingWord(id = 5, word = "Haus", direction = AiTranslationDirection.TARGET_TO_NATIVE))
            advanceUntilIdle()

            val pendingWords = viewModel.uiState.value.pendingWords
            assertThat(pendingWords).hasSize(1)
            assertThat(pendingWords.single().id).isEqualTo(5L)
            assertThat(pendingWords.single().word).isEqualTo("Haus")
        }

    @Test
    fun `onDeletePendingWord delegates to use case`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onDeletePendingWord(9L)
            advanceUntilIdle()

            coVerify(exactly = 1) { deletePendingWordUseCase.invoke(9L) }
        }

    @Test
    fun `onWordChange updates state and clears error`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()

            viewModel.onAddClick()
            assertThat(viewModel.uiState.value.wordError).isEqualTo("Please enter a word.")

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
            assertThat(viewModel.uiState.value.translationError).isEqualTo("Please enter a translation.")

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
            assertThat(state.translationError).isEqualTo("Please enter a translation.")
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
            coEvery { addVocabularyItemUseCase.invoke(any(), any()) } returns
                Result.failure(IllegalStateException("boom"))
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
    fun `onAddClick adds directly when offline but AI mode is not active`() =
        runTest(mainDispatcherRule.testDispatcher) {
            onlineFlow.value = false
            coEvery { addVocabularyItemUseCase.invoke(any(), any()) } returns Result.success(Unit)
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onWordChange("Haus")
            viewModel.onTranslationChange("House")
            viewModel.onAddClick()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.isSuccess).isTrue()
            assertThat(state.successMessage).isEqualTo("Word added successfully!")
            coVerify { addVocabularyItemUseCase.invoke("Haus", "House") }
            coVerify(exactly = 0) { queuePendingWordUseCase.invoke(any(), any()) }
        }

    @Test
    fun `onAddClick queues pending word when offline in AI mode`() =
        runTest(mainDispatcherRule.testDispatcher) {
            openAiKeyFlow.value = "abc"
            useAiFlow.value = true
            onlineFlow.value = false
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onWordChange("Haus")
            viewModel.onAddClick()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.word).isEmpty()
            assertThat(state.translation).isEmpty()
            assertThat(state.isSuccess).isTrue()
            assertThat(state.successMessage).contains("back online")
            assertThat(state.loadingAction).isNull()
            coVerify(exactly = 1) { queuePendingWordUseCase.invoke("Haus", AiTranslationDirection.TARGET_TO_NATIVE) }
            coVerify(exactly = 0) { addVocabularyItemUseCase.invoke(any(), any()) }
            assertThat(aiTranslationProvider.requests).isEmpty()
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
            coVerify { openAiStore.setUseAiForTranslation(true) }
        }

    @Test
    fun `onUseAiToggle allows disable when direction is native to foreign`() =
        runTest(mainDispatcherRule.testDispatcher) {
            directionFlow.value = AiTranslationDirection.NATIVE_TO_TARGET
            useAiFlow.value = true
            val viewModel = buildViewModel()

            advanceUntilIdle()

            viewModel.onUseAiToggle(false)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.useAiForTranslation).isFalse()
            coVerify { openAiStore.setUseAiForTranslation(false) }
        }

    @Test
    fun `onTranslationDirectionToggle flips direction without forcing AI on`() =
        runTest(mainDispatcherRule.testDispatcher) {
            useAiFlow.value = false
            directionFlow.value = AiTranslationDirection.TARGET_TO_NATIVE
            val viewModel = buildViewModel()

            advanceUntilIdle()

            viewModel.onTranslationDirectionToggle()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.translationDirection).isEqualTo(AiTranslationDirection.NATIVE_TO_TARGET)
            assertThat(state.useAiForTranslation).isFalse()
            coVerify { openAiStore.setAiTranslationDirection(AiTranslationDirection.NATIVE_TO_TARGET) }
            coVerify(exactly = 0) { openAiStore.setUseAiForTranslation(any()) }
        }

    @Test
    fun `onPreviewClick requires non blank word`() =
        runTest(mainDispatcherRule.testDispatcher) {
            openAiKeyFlow.value = "abc"
            useAiFlow.value = true
            val viewModel = buildViewModel()

            advanceUntilIdle()

            viewModel.onPreviewClick()

            assertThat(viewModel.uiState.value.wordError).isEqualTo("Please enter a word.")
            assertThat(viewModel.uiState.value.isPreviewVisible).isFalse()
        }

    @Test
    fun `onPreviewClick loads AI translation and shows dialog`() =
        runTest(mainDispatcherRule.testDispatcher) {
            openAiKeyFlow.value = "abc"
            useAiFlow.value = true
            aiTranslationProvider.nextTranslation = "House"
            val viewModel = buildViewModel()

            advanceUntilIdle()

            viewModel.onWordChange("Haus")
            viewModel.onPreviewClick()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.isPreviewVisible).isTrue()
            assertThat(state.previewContent).isNotNull()
            assertThat(state.previewContent?.word).isEqualTo("Haus")
            assertThat(state.previewContent?.translation).isEqualTo("House")
            assertThat(state.translation).isEqualTo("House")
            assertThat(state.isLoading).isFalse()
            assertThat(state.loadingAction).isNull()
        }

    @Test
    fun `onPreviewClick failure posts error`() =
        runTest(mainDispatcherRule.testDispatcher) {
            openAiKeyFlow.value = "abc"
            useAiFlow.value = true
            aiTranslationProvider.nextError = IllegalStateException("nope")
            val viewModel = buildViewModel()

            advanceUntilIdle()

            viewModel.onWordChange("Haus")
            viewModel.onPreviewClick()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.errorMessage).isEqualTo("nope")
            assertThat(state.isPreviewVisible).isFalse()
            assertThat(state.previewContent).isNull()
            assertThat(state.isLoading).isFalse()
            assertThat(state.loadingAction).isNull()
        }

    @Test
    fun `onPreviewClick does nothing when offline`() =
        runTest(mainDispatcherRule.testDispatcher) {
            openAiKeyFlow.value = "abc"
            useAiFlow.value = true
            onlineFlow.value = false
            val viewModel = buildViewModel()

            advanceUntilIdle()

            viewModel.onWordChange("Haus")
            viewModel.onPreviewClick()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.isPreviewVisible).isFalse()
            assertThat(state.previewContent).isNull()
            assertThat(state.isLoading).isFalse()
            assertThat(aiTranslationProvider.requests).isEmpty()
        }

    @Test
    fun `onPreviewCancel clears fields and hides dialog`() =
        runTest(mainDispatcherRule.testDispatcher) {
            openAiKeyFlow.value = "abc"
            useAiFlow.value = true
            aiTranslationProvider.nextTranslation = "House"
            val viewModel = buildViewModel()

            advanceUntilIdle()

            viewModel.onWordChange("Haus")
            viewModel.onPreviewClick()
            advanceUntilIdle()

            viewModel.onPreviewCancel()

            val state = viewModel.uiState.value
            assertThat(state.word).isEmpty()
            assertThat(state.translation).isEmpty()
            assertThat(state.previewContent).isNull()
            assertThat(state.isPreviewVisible).isFalse()
            assertThat(state.successMessage).isNull()
        }

    @Test
    fun `onPreviewConfirmAdd adds word and clears preview`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { addVocabularyItemUseCase.invoke(any(), any()) } returns Result.success(Unit)
            openAiKeyFlow.value = "abc"
            useAiFlow.value = true
            aiTranslationProvider.nextTranslation = "House"
            val viewModel = buildViewModel()

            advanceUntilIdle()

            viewModel.onWordChange("Haus")
            viewModel.onPreviewClick()
            advanceUntilIdle()

            viewModel.onPreviewConfirmAdd()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.isPreviewVisible).isFalse()
            assertThat(state.previewContent).isNull()
            assertThat(state.word).isEmpty()
            assertThat(state.translation).isEmpty()
            assertThat(state.isSuccess).isTrue()
            coVerify { addVocabularyItemUseCase.invoke("Haus", "House") }
        }

    @Test
    fun `onPreviewConfirmAdd failure keeps preview visible and posts error`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { addVocabularyItemUseCase.invoke(any(), any()) } returns
                Result.failure(IllegalStateException("boom"))
            openAiKeyFlow.value = "abc"
            useAiFlow.value = true
            aiTranslationProvider.nextTranslation = "House"
            val viewModel = buildViewModel()

            advanceUntilIdle()

            viewModel.onWordChange("Haus")
            viewModel.onPreviewClick()
            advanceUntilIdle()

            viewModel.onPreviewConfirmAdd()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.errorMessage).isEqualTo("boom")
            assertThat(state.isPreviewVisible).isTrue()
            assertThat(state.previewContent).isNotNull()
            coVerify { addVocabularyItemUseCase.invoke("Haus", "House") }
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

                The headword above is in Russian. Write headings, explanations, and usage notes in English, and write every example sentence in Russian.
                """.trimIndent(),
            )
        }

    @Test
    fun `onAddClick uses reverse prompt when direction is RU to EN`() =
        runTest(mainDispatcherRule.testDispatcher) {
            openAiKeyFlow.value = "abc"
            useAiFlow.value = true
            directionFlow.value = AiTranslationDirection.NATIVE_TO_TARGET
            reversePromptFlow.value = "reverse prompt"
            coEvery { addVocabularyItemUseCase.invoke(any(), any()) } returns Result.success(Unit)

            aiTranslationProvider.nextTranslation = "House"
            val viewModel = buildViewModel()

            advanceUntilIdle()

            viewModel.onWordChange("дом")
            viewModel.onTranslationChange("")
            viewModel.onAddClick()
            advanceUntilIdle()

            assertThat(aiTranslationProvider.requests).hasSize(1)
            val request = aiTranslationProvider.requests.single()
            assertThat(request.systemPrompt).isEqualTo("reverse prompt")
            assertThat(request.userPrompt).contains("HEADWORD: \"дом\"")
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
            assertThat(state.translationError).isEqualTo("Please enter a translation.")
            assertThat(state.errorMessage).isNull()
            assertThat(state.isSuccess).isFalse()
            coVerify(exactly = 0) { addVocabularyItemUseCase.invoke(any(), any()) }
            assertThat(aiTranslationProvider.requests).hasSize(1)
            assertThat(aiTranslationProvider.requests.single().userPrompt).contains("Haus")
        }

    @Test
    fun `onAddClick shows duplicate dialog when word exists`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val existing =
                VocabularyItem(
                    id = 1,
                    word = "Haus",
                    translation = "House",
                    isNew = false,
                )
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns existing
            val viewModel = buildViewModel()

            viewModel.onWordChange("Haus")
            viewModel.onTranslationChange("Дом")
            viewModel.onAddClick()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.isExistingWordDialogVisible).isTrue()
            assertThat(state.existingWordDialogWord).isEqualTo("Haus")
            assertThat(state.isLoading).isFalse()
            coVerify(exactly = 0) { addVocabularyItemUseCase.invoke(any(), any()) }
        }

    @Test
    fun `existing word dialog cancel hides dialog`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val existing =
                VocabularyItem(
                    id = 3,
                    word = "Haus",
                    translation = "House",
                    isNew = false,
                )
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns existing
            val viewModel = buildViewModel()

            viewModel.onWordChange("Haus")
            viewModel.onTranslationChange("Дом")
            viewModel.onAddClick()
            advanceUntilIdle()
            viewModel.onExistingWordDialogCancel()

            val state = viewModel.uiState.value
            assertThat(state.isExistingWordDialogVisible).isFalse()
            assertThat(state.existingWordDialogWord).isNull()
        }

    @Test
    fun `existing word dialog proceed overrides and clears state`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val existing =
                VocabularyItem(
                    id = 7,
                    word = "Haus",
                    translation = "House",
                    isNew = false,
                )
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns existing
            coEvery { overrideVocabularyItemUseCase.invoke(any(), any(), any(), any(), any(), any()) } returns
                Result.success(Unit)
            val viewModel = buildViewModel()

            viewModel.onWordChange("Haus")
            viewModel.onTranslationChange("Дом")
            viewModel.onAddClick()
            advanceUntilIdle()
            viewModel.onExistingWordDialogProceed()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.isExistingWordDialogVisible).isFalse()
            assertThat(state.word).isEmpty()
            assertThat(state.translation).isEmpty()
            assertThat(state.isSuccess).isTrue()
            assertThat(state.successMessage).isEqualTo("Word updated and progress reset!")
            coVerify { overrideVocabularyItemUseCase.invoke(existing, "Haus", "Дом") }
        }

    @Test
    fun `existing word dialog proceed failure posts error`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val existing =
                VocabularyItem(
                    id = 8,
                    word = "Haus",
                    translation = "House",
                    isNew = false,
                )
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns existing
            coEvery { overrideVocabularyItemUseCase.invoke(any(), any(), any(), any(), any(), any()) } returns
                Result.failure(IllegalStateException("override failed"))
            val viewModel = buildViewModel()

            viewModel.onWordChange("Haus")
            viewModel.onTranslationChange("Дом")
            viewModel.onAddClick()
            advanceUntilIdle()
            viewModel.onExistingWordDialogProceed()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.errorMessage).isEqualTo("override failed")
            assertThat(state.isExistingWordDialogVisible).isFalse()
            coVerify { overrideVocabularyItemUseCase.invoke(existing, "Haus", "Дом") }
        }

    @Test
    fun `init exposes uppercase language codes from configured pair`() =
        runTest(mainDispatcherRule.testDispatcher) {
            languagePairFlow.value = LanguagePair(Language.GERMAN, Language.FRENCH)

            val viewModel = buildViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.nativeLanguageCode).isEqualTo("DE")
            assertThat(state.targetLanguageCode).isEqualTo("FR")
        }

    @Test
    fun `language pair changes propagate to state live`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.nativeLanguageCode).isEqualTo("EN")
            assertThat(viewModel.uiState.value.targetLanguageCode).isEqualTo("RU")

            languagePairFlow.value = LanguagePair(Language.SPANISH, Language.ITALIAN)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.nativeLanguageCode).isEqualTo("ES")
            assertThat(state.targetLanguageCode).isEqualTo("IT")
        }

    @Test
    fun `null language pair falls back to English to Russian defaults`() =
        runTest(mainDispatcherRule.testDispatcher) {
            languagePairFlow.value = null

            val viewModel = buildViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.nativeLanguageCode).isEqualTo("EN")
            assertThat(state.targetLanguageCode).isEqualTo("RU")
        }

    @Test
    fun `default ui state has English to Russian codes before init completes`() {
        assertThat(AddWordUiState().nativeLanguageCode).isEqualTo("EN")
        assertThat(AddWordUiState().targetLanguageCode).isEqualTo("RU")
    }

    @Test
    fun `onPreviewClick shows stored translation for existing word without AI request`() =
        runTest(mainDispatcherRule.testDispatcher) {
            openAiKeyFlow.value = "abc"
            useAiFlow.value = true
            val existing = VocabularyItem(id = 1, word = "Haus", translation = "Stored house", isNew = false)
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns existing
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onWordChange("Haus")
            viewModel.onPreviewClick()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.isPreviewVisible).isTrue()
            assertThat(state.previewContent?.translation).isEqualTo("Stored house")
            assertThat(state.previewContent?.isStoredTranslation).isTrue()
            assertThat(state.translation).isEqualTo("Stored house")
            assertThat(state.isLoading).isFalse()
            assertThat(state.loadingAction).isNull()
            assertThat(aiTranslationProvider.requests).isEmpty()
        }

    @Test
    fun `onPreviewClick matches existing word case-insensitively`() =
        runTest(mainDispatcherRule.testDispatcher) {
            openAiKeyFlow.value = "abc"
            useAiFlow.value = true
            val existing = VocabularyItem(id = 1, word = "Haus", translation = "Stored house", isNew = false)
            coEvery { getVocabularyItemByWordUseCase.invoke("haus") } returns existing
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onWordChange("haus")
            viewModel.onPreviewClick()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.previewContent?.isStoredTranslation).isTrue()
            assertThat(aiTranslationProvider.requests).isEmpty()
        }

    @Test
    fun `onPreviewClick falls back to AI when existing item has blank translation`() =
        runTest(mainDispatcherRule.testDispatcher) {
            openAiKeyFlow.value = "abc"
            useAiFlow.value = true
            val existing = VocabularyItem(id = 1, word = "Haus", translation = "  ", isNew = false)
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns existing
            aiTranslationProvider.nextTranslation = "House"
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onWordChange("Haus")
            viewModel.onPreviewClick()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.previewContent?.isStoredTranslation).isFalse()
            assertThat(state.previewContent?.translation).isEqualTo("House")
            assertThat(aiTranslationProvider.requests).hasSize(1)
        }

    @Test
    fun `onPreviewClick posts error and skips AI call when lookup fails`() =
        runTest(mainDispatcherRule.testDispatcher) {
            openAiKeyFlow.value = "abc"
            useAiFlow.value = true
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } throws IllegalStateException("db error")
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onWordChange("Haus")
            viewModel.onPreviewClick()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.errorMessage).isEqualTo("db error")
            assertThat(state.isPreviewVisible).isFalse()
            assertThat(state.previewContent).isNull()
            assertThat(aiTranslationProvider.requests).isEmpty()
        }

    @Test
    fun `onPreviewClick still generates via AI for a brand new word`() =
        runTest(mainDispatcherRule.testDispatcher) {
            openAiKeyFlow.value = "abc"
            useAiFlow.value = true
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns null
            aiTranslationProvider.nextTranslation = "House"
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onWordChange("Haus")
            viewModel.onPreviewClick()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.previewContent?.isStoredTranslation).isFalse()
            assertThat(state.previewContent?.translation).isEqualTo("House")
            assertThat(aiTranslationProvider.requests).hasSize(1)
        }

    @Test
    fun `onPreviewClick offline does not look up existing word`() =
        runTest(mainDispatcherRule.testDispatcher) {
            openAiKeyFlow.value = "abc"
            useAiFlow.value = true
            onlineFlow.value = false
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onWordChange("Haus")
            viewModel.onPreviewClick()
            advanceUntilIdle()

            coVerify(exactly = 0) { getVocabularyItemByWordUseCase.invoke(any()) }
            assertThat(aiTranslationProvider.requests).isEmpty()
        }

    @Test
    fun `onPreviewClick with AI off does not look up existing word`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onWordChange("Haus")
            viewModel.onPreviewClick()
            advanceUntilIdle()

            coVerify(exactly = 0) { getVocabularyItemByWordUseCase.invoke(any()) }
        }

    @Test
    fun `onPreviewRegenerate calls AI once and flips preview to generated`() =
        runTest(mainDispatcherRule.testDispatcher) {
            openAiKeyFlow.value = "abc"
            useAiFlow.value = true
            val existing = VocabularyItem(id = 1, word = "Haus", translation = "Stored house", isNew = false)
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns existing
            aiTranslationProvider.nextTranslation = "Fresh house"
            val viewModel = buildViewModel()
            advanceUntilIdle()
            viewModel.onWordChange("Haus")
            viewModel.onPreviewClick()
            advanceUntilIdle()

            viewModel.onPreviewRegenerate()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.previewContent?.isStoredTranslation).isFalse()
            assertThat(state.previewContent?.translation).isEqualTo("Fresh house")
            assertThat(state.translation).isEqualTo("Fresh house")
            assertThat(state.isPreviewVisible).isTrue()
            assertThat(aiTranslationProvider.requests).hasSize(1)
        }

    @Test
    fun `onPreviewRegenerate shows loading action while in flight and keeps preview visible`() =
        runTest(mainDispatcherRule.testDispatcher) {
            openAiKeyFlow.value = "abc"
            useAiFlow.value = true
            val existing = VocabularyItem(id = 1, word = "Haus", translation = "Stored house", isNew = false)
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns existing
            val viewModel = buildViewModel()
            advanceUntilIdle()
            viewModel.onWordChange("Haus")
            viewModel.onPreviewClick()
            advanceUntilIdle()

            val gate = CompletableDeferred<Unit>()
            aiTranslationProvider.suspendUntil = gate
            aiTranslationProvider.nextTranslation = "Fresh house"

            viewModel.onPreviewRegenerate()
            advanceUntilIdle()

            var state = viewModel.uiState.value
            assertThat(state.isLoading).isTrue()
            assertThat(state.loadingAction).isEqualTo(AddWordLoadingAction.PREVIEW_REGENERATE)
            assertThat(state.isPreviewVisible).isTrue()
            assertThat(state.previewContent?.translation).isEqualTo("Stored house")

            gate.complete(Unit)
            advanceUntilIdle()

            state = viewModel.uiState.value
            assertThat(state.isLoading).isFalse()
            assertThat(state.previewContent?.translation).isEqualTo("Fresh house")
        }

    @Test
    fun `onPreviewRegenerate failure hides preview and posts error`() =
        runTest(mainDispatcherRule.testDispatcher) {
            openAiKeyFlow.value = "abc"
            useAiFlow.value = true
            val existing = VocabularyItem(id = 1, word = "Haus", translation = "Stored house", isNew = false)
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns existing
            val viewModel = buildViewModel()
            advanceUntilIdle()
            viewModel.onWordChange("Haus")
            viewModel.onPreviewClick()
            advanceUntilIdle()

            aiTranslationProvider.nextError = IllegalStateException("nope")
            viewModel.onPreviewRegenerate()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.errorMessage).isEqualTo("nope")
            assertThat(state.isPreviewVisible).isFalse()
            assertThat(state.previewContent).isNull()
        }

    @Test
    fun `onPreviewRegenerate blank AI response clears preview without crash`() =
        runTest(mainDispatcherRule.testDispatcher) {
            openAiKeyFlow.value = "abc"
            useAiFlow.value = true
            val existing = VocabularyItem(id = 1, word = "Haus", translation = "Stored house", isNew = false)
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns existing
            val viewModel = buildViewModel()
            advanceUntilIdle()
            viewModel.onWordChange("Haus")
            viewModel.onPreviewClick()
            advanceUntilIdle()

            aiTranslationProvider.nextTranslation = "   "
            viewModel.onPreviewRegenerate()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.translationError).isEqualTo("Please enter a translation.")
            assertThat(state.previewContent).isNull()
            assertThat(state.isPreviewVisible).isFalse()
        }

    @Test
    fun `onPreviewRegenerate no-ops without a visible preview`() =
        runTest(mainDispatcherRule.testDispatcher) {
            openAiKeyFlow.value = "abc"
            useAiFlow.value = true
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onPreviewRegenerate()
            advanceUntilIdle()

            assertThat(aiTranslationProvider.requests).isEmpty()
        }

    @Test
    fun `onPreviewRegenerate no-ops when offline`() =
        runTest(mainDispatcherRule.testDispatcher) {
            openAiKeyFlow.value = "abc"
            useAiFlow.value = true
            val existing = VocabularyItem(id = 1, word = "Haus", translation = "Stored house", isNew = false)
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns existing
            val viewModel = buildViewModel()
            advanceUntilIdle()
            viewModel.onWordChange("Haus")
            viewModel.onPreviewClick()
            advanceUntilIdle()

            onlineFlow.value = false
            advanceUntilIdle()

            viewModel.onPreviewRegenerate()
            advanceUntilIdle()

            assertThat(aiTranslationProvider.requests).isEmpty()
        }

    @Test
    fun `onPreviewRegenerate no-ops when AI toggled off`() =
        runTest(mainDispatcherRule.testDispatcher) {
            openAiKeyFlow.value = "abc"
            useAiFlow.value = true
            val existing = VocabularyItem(id = 1, word = "Haus", translation = "Stored house", isNew = false)
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns existing
            val viewModel = buildViewModel()
            advanceUntilIdle()
            viewModel.onWordChange("Haus")
            viewModel.onPreviewClick()
            advanceUntilIdle()

            // Directly flip the pref flow to simulate AI being disabled elsewhere.
            useAiFlow.value = false
            advanceUntilIdle()

            viewModel.onPreviewRegenerate()
            advanceUntilIdle()

            assertThat(aiTranslationProvider.requests).isEmpty()
        }

    @Test
    fun `failed regenerate does not acknowledge duplicate, later add still prompts dialog`() =
        runTest(mainDispatcherRule.testDispatcher) {
            openAiKeyFlow.value = "abc"
            useAiFlow.value = true
            val existing = VocabularyItem(id = 1, word = "Haus", translation = "Stored house", isNew = false)
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns existing
            val viewModel = buildViewModel()
            advanceUntilIdle()
            viewModel.onWordChange("Haus")
            viewModel.onPreviewClick()
            advanceUntilIdle()

            aiTranslationProvider.nextError = IllegalStateException("nope")
            viewModel.onPreviewRegenerate()
            advanceUntilIdle()

            aiTranslationProvider.nextError = null
            viewModel.onAddClick()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.isExistingWordDialogVisible).isTrue()
            assertThat(aiTranslationProvider.requests).hasSize(1)
        }

    @Test
    fun `confirming regenerated preview overrides existing word without existing-word dialog`() =
        runTest(mainDispatcherRule.testDispatcher) {
            openAiKeyFlow.value = "abc"
            useAiFlow.value = true
            val existing = VocabularyItem(id = 1, word = "Haus", translation = "Stored house", isNew = false)
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns existing
            coEvery { overrideVocabularyItemUseCase.invoke(any(), any(), any(), any(), any(), any()) } returns
                Result.success(Unit)
            aiTranslationProvider.nextTranslation = "Fresh house"
            val viewModel = buildViewModel()
            advanceUntilIdle()
            viewModel.onWordChange("Haus")
            viewModel.onPreviewClick()
            advanceUntilIdle()
            viewModel.onPreviewRegenerate()
            advanceUntilIdle()

            viewModel.onPreviewConfirmAdd()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.isExistingWordDialogVisible).isFalse()
            assertThat(state.isSuccess).isTrue()
            assertThat(state.successMessage).isEqualTo("Word updated and progress reset!")
            assertThat(state.word).isEmpty()
            assertThat(state.translation).isEmpty()
            assertThat(state.previewContent).isNull()
            assertThat(state.isPreviewVisible).isFalse()
            coVerify(exactly = 1) { overrideVocabularyItemUseCase.invoke(existing, "Haus", "Fresh house") }
            // Only the regenerate call; confirming does not issue another AI request.
            assertThat(aiTranslationProvider.requests).hasSize(1)
        }

    @Test
    fun `confirming regenerated preview override failure posts error and keeps dialog closed`() =
        runTest(mainDispatcherRule.testDispatcher) {
            openAiKeyFlow.value = "abc"
            useAiFlow.value = true
            val existing = VocabularyItem(id = 1, word = "Haus", translation = "Stored house", isNew = false)
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns existing
            coEvery { overrideVocabularyItemUseCase.invoke(any(), any(), any(), any(), any(), any()) } returns
                Result.failure(IllegalStateException("override failed"))
            aiTranslationProvider.nextTranslation = "Fresh house"
            val viewModel = buildViewModel()
            advanceUntilIdle()
            viewModel.onWordChange("Haus")
            viewModel.onPreviewClick()
            advanceUntilIdle()
            viewModel.onPreviewRegenerate()
            advanceUntilIdle()

            viewModel.onPreviewConfirmAdd()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.errorMessage).isEqualTo("override failed")
            assertThat(state.isExistingWordDialogVisible).isFalse()
            assertThat(state.isSuccess).isFalse()
        }

    @Test
    fun `editing word after regenerating clears acknowledgment so a later add prompts again`() =
        runTest(mainDispatcherRule.testDispatcher) {
            openAiKeyFlow.value = "abc"
            useAiFlow.value = true
            val existing = VocabularyItem(id = 1, word = "Haus", translation = "Stored house", isNew = false)
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns existing
            aiTranslationProvider.nextTranslation = "Fresh house"
            val viewModel = buildViewModel()
            advanceUntilIdle()
            viewModel.onWordChange("Haus")
            viewModel.onPreviewClick()
            advanceUntilIdle()
            viewModel.onPreviewRegenerate()
            advanceUntilIdle()

            // Simulate leaving and re-entering the same word.
            viewModel.onWordChange("Baum")
            viewModel.onWordChange("Haus")

            viewModel.onAddClick()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.isExistingWordDialogVisible).isTrue()
        }

    @Test
    fun `toggling ai off after regenerating clears acknowledgment`() =
        runTest(mainDispatcherRule.testDispatcher) {
            openAiKeyFlow.value = "abc"
            useAiFlow.value = true
            val existing = VocabularyItem(id = 1, word = "Haus", translation = "Stored house", isNew = false)
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns existing
            aiTranslationProvider.nextTranslation = "Fresh house"
            val viewModel = buildViewModel()
            advanceUntilIdle()
            viewModel.onWordChange("Haus")
            viewModel.onPreviewClick()
            advanceUntilIdle()
            viewModel.onPreviewRegenerate()
            advanceUntilIdle()

            viewModel.onUseAiToggle(false)
            useAiFlow.value = false
            advanceUntilIdle()
            viewModel.onTranslationChange("Дом")

            viewModel.onAddClick()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.isExistingWordDialogVisible).isTrue()
        }

    @Test
    fun `duplicate discovered at confirm time shows existing-word dialog and hides preview`() =
        runTest(mainDispatcherRule.testDispatcher) {
            openAiKeyFlow.value = "abc"
            useAiFlow.value = true
            // Word does not exist yet when previewed...
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns null
            aiTranslationProvider.nextTranslation = "House"
            val viewModel = buildViewModel()
            advanceUntilIdle()
            viewModel.onWordChange("Haus")
            viewModel.onPreviewClick()
            advanceUntilIdle()
            assertThat(
                viewModel.uiState.value.previewContent
                    ?.isStoredTranslation,
            ).isFalse()

            // ...but is added elsewhere by the time the user confirms.
            val existing = VocabularyItem(id = 9, word = "Haus", translation = "Someone else's house", isNew = false)
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns existing

            viewModel.onPreviewConfirmAdd()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.isExistingWordDialogVisible).isTrue()
            assertThat(state.isPreviewVisible).isFalse()
            coVerify(exactly = 0) { addVocabularyItemUseCase.invoke(any(), any()) }
        }

    @Test
    fun `cancelling existing-word dialog from a preview race restores the preview`() =
        runTest(mainDispatcherRule.testDispatcher) {
            openAiKeyFlow.value = "abc"
            useAiFlow.value = true
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns null
            aiTranslationProvider.nextTranslation = "House"
            val viewModel = buildViewModel()
            advanceUntilIdle()
            viewModel.onWordChange("Haus")
            viewModel.onPreviewClick()
            advanceUntilIdle()

            val existing = VocabularyItem(id = 9, word = "Haus", translation = "Someone else's house", isNew = false)
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns existing
            viewModel.onPreviewConfirmAdd()
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.isPreviewVisible).isFalse()

            viewModel.onExistingWordDialogCancel()

            val state = viewModel.uiState.value
            assertThat(state.isExistingWordDialogVisible).isFalse()
            assertThat(state.isPreviewVisible).isTrue()
            assertThat(state.previewContent).isNotNull()
        }

    @Test
    fun `onAddClick with AI on shows dialog immediately for existing word without AI request`() =
        runTest(mainDispatcherRule.testDispatcher) {
            openAiKeyFlow.value = "abc"
            useAiFlow.value = true
            val existing = VocabularyItem(id = 1, word = "Haus", translation = "House", isNew = false)
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns existing
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onWordChange("Haus")
            viewModel.onAddClick()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.isExistingWordDialogVisible).isTrue()
            assertThat(state.existingWordDialogWord).isEqualTo("Haus")
            assertThat(state.isLoading).isFalse()
            assertThat(state.loadingAction).isNull()
            assertThat(aiTranslationProvider.requests).isEmpty()
        }

    @Test
    fun `proceeding with AI on generates translation once and overrides`() =
        runTest(mainDispatcherRule.testDispatcher) {
            openAiKeyFlow.value = "abc"
            useAiFlow.value = true
            val existing = VocabularyItem(id = 1, word = "Haus", translation = "House", isNew = false)
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns existing
            coEvery { overrideVocabularyItemUseCase.invoke(any(), any(), any(), any(), any(), any()) } returns
                Result.success(Unit)
            aiTranslationProvider.nextTranslation = "Fresh house"
            val viewModel = buildViewModel()
            advanceUntilIdle()
            viewModel.onWordChange("Haus")
            viewModel.onAddClick()
            advanceUntilIdle()

            viewModel.onExistingWordDialogProceed()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.isExistingWordDialogVisible).isFalse()
            assertThat(state.isExistingWordDialogLoading).isFalse()
            assertThat(state.isSuccess).isTrue()
            assertThat(state.successMessage).isEqualTo("Word updated and progress reset!")
            assertThat(aiTranslationProvider.requests).hasSize(1)
            coVerify(exactly = 1) { overrideVocabularyItemUseCase.invoke(existing, "Haus", "Fresh house") }
        }

    @Test
    fun `dialog loading stays true while confirm-time AI request is in flight`() =
        runTest(mainDispatcherRule.testDispatcher) {
            openAiKeyFlow.value = "abc"
            useAiFlow.value = true
            val existing = VocabularyItem(id = 1, word = "Haus", translation = "House", isNew = false)
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns existing
            coEvery { overrideVocabularyItemUseCase.invoke(any(), any(), any(), any(), any(), any()) } returns
                Result.success(Unit)
            val viewModel = buildViewModel()
            advanceUntilIdle()
            viewModel.onWordChange("Haus")
            viewModel.onAddClick()
            advanceUntilIdle()

            val gate = CompletableDeferred<Unit>()
            aiTranslationProvider.suspendUntil = gate
            aiTranslationProvider.nextTranslation = "Fresh house"

            viewModel.onExistingWordDialogProceed()
            advanceUntilIdle()

            var state = viewModel.uiState.value
            assertThat(state.isExistingWordDialogVisible).isTrue()
            assertThat(state.isExistingWordDialogLoading).isTrue()

            gate.complete(Unit)
            advanceUntilIdle()

            state = viewModel.uiState.value
            assertThat(state.isExistingWordDialogLoading).isFalse()
            assertThat(state.isSuccess).isTrue()
        }

    @Test
    fun `proceed with AI failure closes dialog, posts error, does not override`() =
        runTest(mainDispatcherRule.testDispatcher) {
            openAiKeyFlow.value = "abc"
            useAiFlow.value = true
            val existing = VocabularyItem(id = 1, word = "Haus", translation = "House", isNew = false)
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns existing
            aiTranslationProvider.nextError = IllegalStateException("boom")
            val viewModel = buildViewModel()
            advanceUntilIdle()
            viewModel.onWordChange("Haus")
            viewModel.onAddClick()
            advanceUntilIdle()

            viewModel.onExistingWordDialogProceed()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.errorMessage).isEqualTo("boom")
            assertThat(state.isExistingWordDialogVisible).isFalse()
            assertThat(state.isExistingWordDialogLoading).isFalse()
            coVerify(exactly = 0) { overrideVocabularyItemUseCase.invoke(any(), any(), any(), any(), any(), any()) }
        }

    @Test
    fun `proceed with blank AI response closes dialog, posts error, does not override`() =
        runTest(mainDispatcherRule.testDispatcher) {
            openAiKeyFlow.value = "abc"
            useAiFlow.value = true
            val existing = VocabularyItem(id = 1, word = "Haus", translation = "House", isNew = false)
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns existing
            aiTranslationProvider.nextTranslation = "   "
            val viewModel = buildViewModel()
            advanceUntilIdle()
            viewModel.onWordChange("Haus")
            viewModel.onAddClick()
            advanceUntilIdle()

            viewModel.onExistingWordDialogProceed()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.errorMessage).isNotNull()
            assertThat(state.isExistingWordDialogVisible).isFalse()
            coVerify(exactly = 0) { overrideVocabularyItemUseCase.invoke(any(), any(), any(), any(), any(), any()) }
        }

    @Test
    fun `cancel on AI duplicate dialog issues no AI request and a later proceed is a no-op`() =
        runTest(mainDispatcherRule.testDispatcher) {
            openAiKeyFlow.value = "abc"
            useAiFlow.value = true
            val existing = VocabularyItem(id = 1, word = "Haus", translation = "House", isNew = false)
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns existing
            val viewModel = buildViewModel()
            advanceUntilIdle()
            viewModel.onWordChange("Haus")
            viewModel.onAddClick()
            advanceUntilIdle()

            viewModel.onExistingWordDialogCancel()
            advanceUntilIdle()

            viewModel.onExistingWordDialogProceed()
            advanceUntilIdle()

            assertThat(aiTranslationProvider.requests).isEmpty()
            coVerify(exactly = 0) { overrideVocabularyItemUseCase.invoke(any(), any(), any(), any(), any(), any()) }
            assertThat(viewModel.uiState.value.isExistingWordDialogVisible).isFalse()
        }

    @Test
    fun `onAddClick with AI on for a brand new word generates translation and adds`() =
        runTest(mainDispatcherRule.testDispatcher) {
            openAiKeyFlow.value = "abc"
            useAiFlow.value = true
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns null
            coEvery { addVocabularyItemUseCase.invoke(any(), any()) } returns Result.success(Unit)
            aiTranslationProvider.nextTranslation = "House"
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onWordChange("Haus")
            viewModel.onAddClick()
            advanceUntilIdle()

            assertThat(aiTranslationProvider.requests).hasSize(1)
            coVerify(exactly = 1) { addVocabularyItemUseCase.invoke("Haus", "House") }
            assertThat(viewModel.uiState.value.isSuccess).isTrue()
        }

    @Test
    fun `onAddClick with AI on posts error and skips AI call when lookup fails`() =
        runTest(mainDispatcherRule.testDispatcher) {
            openAiKeyFlow.value = "abc"
            useAiFlow.value = true
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } throws IllegalStateException("db error")
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onWordChange("Haus")
            viewModel.onAddClick()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.errorMessage).isEqualTo("db error")
            assertThat(state.isExistingWordDialogVisible).isFalse()
            assertThat(aiTranslationProvider.requests).isEmpty()
            coVerify(exactly = 0) { addVocabularyItemUseCase.invoke(any(), any()) }
        }

    @Test
    fun `direction captured at click time is used for the confirm-time AI request`() =
        runTest(mainDispatcherRule.testDispatcher) {
            openAiKeyFlow.value = "abc"
            useAiFlow.value = true
            directionFlow.value = AiTranslationDirection.TARGET_TO_NATIVE
            val existing = VocabularyItem(id = 1, word = "Haus", translation = "House", isNew = false)
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns existing
            coEvery { overrideVocabularyItemUseCase.invoke(any(), any(), any(), any(), any(), any()) } returns
                Result.success(Unit)
            aiTranslationProvider.nextTranslation = "Fresh house"
            val viewModel = buildViewModel()
            advanceUntilIdle()
            viewModel.onWordChange("Haus")
            viewModel.onAddClick()
            advanceUntilIdle()

            // Direction changes after the dialog is already showing.
            directionFlow.value = AiTranslationDirection.NATIVE_TO_TARGET
            advanceUntilIdle()

            viewModel.onExistingWordDialogProceed()
            advanceUntilIdle()

            assertThat(aiTranslationProvider.requests).hasSize(1)
            assertThat(aiTranslationProvider.requests.single().systemPrompt).isEqualTo("system prompt")
        }

    @Test
    fun `onAddClick with AI off shows dialog immediately, proceed overrides with typed translation`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val existing = VocabularyItem(id = 1, word = "Haus", translation = "House", isNew = false)
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns existing
            coEvery { overrideVocabularyItemUseCase.invoke(any(), any(), any(), any(), any(), any()) } returns
                Result.success(Unit)
            val viewModel = buildViewModel()

            viewModel.onWordChange("Haus")
            viewModel.onTranslationChange("Дом")
            viewModel.onAddClick()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.isExistingWordDialogVisible).isTrue()
            assertThat(aiTranslationProvider.requests).isEmpty()

            viewModel.onExistingWordDialogProceed()
            advanceUntilIdle()

            coVerify(exactly = 1) { overrideVocabularyItemUseCase.invoke(existing, "Haus", "Дом") }
            assertThat(aiTranslationProvider.requests).isEmpty()
            assertThat(viewModel.uiState.value.isSuccess).isTrue()
        }

    @Test
    fun `onAddClick offline in AI mode does not look up existing word`() =
        runTest(mainDispatcherRule.testDispatcher) {
            openAiKeyFlow.value = "abc"
            useAiFlow.value = true
            onlineFlow.value = false
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onWordChange("Haus")
            viewModel.onAddClick()
            advanceUntilIdle()

            coVerify(exactly = 0) { getVocabularyItemByWordUseCase.invoke(any()) }
        }

    @Test
    fun `showBidirectionalOption is true when AI mode is not active`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.showBidirectionalOption).isTrue()
        }

    @Test
    fun `showBidirectionalOption is false when AI mode is active`() =
        runTest(mainDispatcherRule.testDispatcher) {
            openAiKeyFlow.value = "abc123"
            useAiFlow.value = true
            val viewModel = buildViewModel()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.showBidirectionalOption).isFalse()
        }

    @Test
    fun `onBidirectionalToggle true sets bidirectional state`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onBidirectionalToggle(true)

            assertThat(viewModel.uiState.value.bidirectional).isTrue()
        }

    @Test
    fun `onBidirectionalToggle false collapses and clears the customize section`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()
            advanceUntilIdle()
            viewModel.onBidirectionalToggle(true)
            viewModel.onCustomizeBackwardToggle()
            viewModel.onBackwardPromptOverrideChange("prompt")
            viewModel.onBackwardAnswerOverrideChange("answer")

            viewModel.onBidirectionalToggle(false)

            val state = viewModel.uiState.value
            assertThat(state.bidirectional).isFalse()
            assertThat(state.isCustomizingBackward).isFalse()
            assertThat(state.backwardPromptOverride).isEmpty()
            assertThat(state.backwardAnswerOverride).isEmpty()
        }

    @Test
    fun `enabling AI mode while bidirectional is checked clears bidirectional and overrides`() =
        runTest(mainDispatcherRule.testDispatcher) {
            openAiKeyFlow.value = "abc123"
            val viewModel = buildViewModel()
            advanceUntilIdle()
            viewModel.onBidirectionalToggle(true)
            viewModel.onCustomizeBackwardToggle()
            viewModel.onBackwardPromptOverrideChange("prompt")

            useAiFlow.value = true
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.bidirectional).isFalse()
            assertThat(state.isCustomizingBackward).isFalse()
            assertThat(state.backwardPromptOverride).isEmpty()
        }

    @Test
    fun `onCustomizeBackwardToggle toggles isCustomizingBackward`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()
            advanceUntilIdle()
            viewModel.onBidirectionalToggle(true)

            viewModel.onCustomizeBackwardToggle()
            assertThat(viewModel.uiState.value.isCustomizingBackward).isTrue()

            viewModel.onCustomizeBackwardToggle()
            assertThat(viewModel.uiState.value.isCustomizingBackward).isFalse()
        }

    @Test
    fun `onBackwardPromptOverrideChange and onBackwardAnswerOverrideChange update state`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onBackwardPromptOverrideChange("custom prompt")
            viewModel.onBackwardAnswerOverrideChange("custom answer")

            assertThat(viewModel.uiState.value.backwardPromptOverride).isEqualTo("custom prompt")
            assertThat(viewModel.uiState.value.backwardAnswerOverride).isEqualTo("custom answer")
        }

    @Test
    fun `onAddClick with bidirectional checked and no overrides passes bidirectional true and null overrides`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val capturedBidirectional = slot<Boolean>()
            coEvery {
                addVocabularyItemUseCase.invoke(
                    any(),
                    any(),
                    capture(capturedBidirectional),
                    null,
                    null,
                )
            } returns Result.success(Unit)

            val viewModel = buildViewModel()
            advanceUntilIdle()
            viewModel.onWordChange("run")
            viewModel.onTranslationChange("бігати")
            viewModel.onBidirectionalToggle(true)

            viewModel.onAddClick()
            advanceUntilIdle()

            assertThat(capturedBidirectional.captured).isTrue()
            coVerify(exactly = 1) { addVocabularyItemUseCase.invoke(any(), any(), true, null, null) }
        }

    @Test
    fun `onAddClick with customized overrides passes trimmed override text`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val capturedPromptOverride = slot<String>()
            val capturedAnswerOverride = slot<String>()
            coEvery {
                addVocabularyItemUseCase.invoke(
                    any(),
                    any(),
                    any(),
                    capture(capturedPromptOverride),
                    capture(capturedAnswerOverride),
                )
            } returns Result.success(Unit)

            val viewModel = buildViewModel()
            advanceUntilIdle()
            viewModel.onWordChange("run")
            viewModel.onTranslationChange("бігати")
            viewModel.onBidirectionalToggle(true)
            viewModel.onCustomizeBackwardToggle()
            viewModel.onBackwardPromptOverrideChange(" custom prompt ")
            viewModel.onBackwardAnswerOverrideChange(" custom answer ")

            viewModel.onAddClick()
            advanceUntilIdle()

            assertThat(capturedPromptOverride.captured).isEqualTo(" custom prompt ")
            assertThat(capturedAnswerOverride.captured).isEqualTo(" custom answer ")
        }

    @Test
    @Suppress("ktlint:standard:max-line-length")
    fun `submitting a duplicate word with bidirectional checked passes bidirectional through to the override use case`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val existing = VocabularyItem(id = 1L, word = "Haus", translation = "Old house", isNew = false)
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns existing
            val capturedBidirectional = slot<Boolean>()
            coEvery {
                overrideVocabularyItemUseCase.invoke(
                    any(),
                    any(),
                    any(),
                    capture(capturedBidirectional),
                    any(),
                    any(),
                )
            } returns Result.success(Unit)

            val viewModel = buildViewModel()
            advanceUntilIdle()
            viewModel.onWordChange("Haus")
            viewModel.onTranslationChange("House")
            viewModel.onBidirectionalToggle(true)

            viewModel.onAddClick()
            advanceUntilIdle()
            viewModel.onExistingWordDialogProceed()
            advanceUntilIdle()

            assertThat(capturedBidirectional.captured).isTrue()
        }

    private class FakeAiTranslationProvider : AiTranslationProvider {
        var nextTranslation: String = "House"
        var nextError: Throwable? = null
        var suspendUntil: CompletableDeferred<Unit>? = null
        val requests = mutableListOf<AiTranslationRequest>()

        override suspend fun translate(request: AiTranslationRequest): String {
            requests += request
            suspendUntil?.await()
            nextError?.let { throw it }
            return nextTranslation
        }
    }
}
