package com.procrastilearn.app.ui

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.R
import com.procrastilearn.app.data.local.dao.VocabularyDao
import com.procrastilearn.app.data.local.entity.VocabularyEntity
import com.procrastilearn.app.data.local.prefs.DayCountersStore
import com.procrastilearn.app.data.local.prefs.OpenAiPromptDefaults
import com.procrastilearn.app.domain.model.LearningPreferencesConfig
import com.procrastilearn.app.domain.model.MixMode
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.domain.parser.VocabularyParser
import com.procrastilearn.app.domain.repository.VocabularyRepository
import com.procrastilearn.app.utils.MainDispatcherRule
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var appContext: Context
    private lateinit var store: DayCountersStore
    private lateinit var vocabularyDao: VocabularyDao
    private lateinit var vocabularyRepository: VocabularyRepository
    private lateinit var policyFlow: MutableStateFlow<LearningPreferencesConfig>
    private lateinit var apiKeyFlow: MutableStateFlow<String?>
    private lateinit var promptFlow: MutableStateFlow<String>
    private val defaultParser: VocabularyParser =
        object : VocabularyParser {
            override val id: String = "apkg"
            override val titleResId: Int = R.string.settings_import_option_anki_apkg
            override val descriptionResId: Int? = R.string.settings_import_option_anki_apkg_desc
            override val supportedExtensions: Set<String> = setOf("apkg")
            override val mimeTypes: List<String> = listOf("application/apkg")

            override fun parse(file: File): List<VocabularyItem> = emptyList()
        }

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
        store = mockk(relaxed = true)
        vocabularyDao = mockk()
        vocabularyRepository = mockk(relaxed = true)
        policyFlow =
            MutableStateFlow(
                LearningPreferencesConfig(
                    newPerDay = 20,
                    reviewPerDay = 150,
                    overlayInterval = 10,
                    mixMode = MixMode.MIX,
                    buryImmediateRepeat = true,
                ),
            )
        apiKeyFlow = MutableStateFlow(null)
        promptFlow = MutableStateFlow(OpenAiPromptDefaults.translationPrompt)

        every { store.readPolicy() } returns policyFlow
        every { store.readOpenAiApiKey() } returns apiKeyFlow
        every { store.readOpenAiPrompt() } returns promptFlow
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    private fun buildViewModel(parsers: Set<VocabularyParser> = setOf(defaultParser)): SettingsViewModel =
        SettingsViewModel(
            store = store,
            vocabularyDao = vocabularyDao,
            vocabularyRepository = vocabularyRepository,
            parsers = parsers,
            ioDispatcher = mainDispatcherRule.testDispatcher,
        )

    @Test
    fun `import options surface parser metadata`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()

            val options = viewModel.importOptions

            assertThat(options).hasSize(1)
            val option = options.first()
            assertThat(option.id).isEqualTo("apkg")
            assertThat(option.mimeTypes).contains("application/apkg")
        }

    @Test
    fun `uiState reflects values from store flows`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()

            viewModel.uiState.test {
                val initial = awaitItem()
                assertThat(initial).isEqualTo(SettingsUiState())

                val hydrated = awaitItem()
                assertThat(hydrated.mixMode).isEqualTo(MixMode.MIX)
                assertThat(hydrated.newPerDay).isEqualTo(20)
                assertThat(hydrated.reviewPerDay).isEqualTo(150)
                assertThat(hydrated.overlayInterval).isEqualTo(10)
                assertThat(hydrated.openAiApiKey).isNull()
                assertThat(hydrated.openAiPrompt).isEqualTo(OpenAiPromptDefaults.translationPrompt)

                policyFlow.value =
                    policyFlow.value.copy(
                        mixMode = MixMode.NEW_FIRST,
                        newPerDay = 5,
                        reviewPerDay = 80,
                        overlayInterval = 3,
                    )
                apiKeyFlow.value = "abc"
                promptFlow.value = "custom prompt"

                val updated = awaitItem()
                assertThat(updated.mixMode).isEqualTo(MixMode.NEW_FIRST)
                assertThat(updated.newPerDay).isEqualTo(5)
                assertThat(updated.reviewPerDay).isEqualTo(80)
                assertThat(updated.overlayInterval).isEqualTo(3)
                assertThat(updated.openAiApiKey).isEqualTo("abc")
                assertThat(updated.openAiPrompt).isEqualTo("custom prompt")
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onMixModeChange delegates to store`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()
            coEvery { store.setMixMode(any()) } returns Unit

            viewModel.onMixModeChange(MixMode.NEW_FIRST)
            advanceUntilIdle()

            coVerify { store.setMixMode(MixMode.NEW_FIRST) }
        }

    @Test
    fun `onNewPerDayChange delegates to store`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()
            coEvery { store.setNewPerDay(any()) } returns Unit

            viewModel.onNewPerDayChange(42)
            advanceUntilIdle()

            coVerify { store.setNewPerDay(42) }
        }

    @Test
    fun `onReviewPerDayChange delegates to store`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()
            coEvery { store.setReviewPerDay(any()) } returns Unit

            viewModel.onReviewPerDayChange(77)
            advanceUntilIdle()

            coVerify { store.setReviewPerDay(77) }
        }

    @Test
    fun `onOverlayIntervalChange delegates to store`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()
            coEvery { store.setOverlayInterval(any()) } returns Unit

            viewModel.onOverlayIntervalChange(9)
            advanceUntilIdle()

            coVerify { store.setOverlayInterval(9) }
        }

    @Test
    fun `onOpenAiApiKeyChange delegates to store`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()
            coEvery { store.setOpenAiApiKey(any()) } returns Unit

            viewModel.onOpenAiApiKeyChange("key")
            advanceUntilIdle()

            coVerify { store.setOpenAiApiKey("key") }
        }

    @Test
    fun `onOpenAiPromptChange delegates to store`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()
            coEvery { store.setOpenAiPrompt(any()) } returns Unit

            viewModel.onOpenAiPromptChange("prompt")
            advanceUntilIdle()

            coVerify { store.setOpenAiPrompt("prompt") }
        }

    @Test
    fun `exportVocabularyToUri writes json and invokes callback with success`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()
            val context = appContext
            val tempFile =
                kotlin.io.path
                    .createTempFile(prefix = "export", suffix = ".json")
                    .toFile()
            val uri = Uri.fromFile(tempFile)
            val entity =
                VocabularyEntity(
                    id = 1,
                    word = "Haus",
                    translation = "House",
                    createdAt = 123L,
                    lastShownAt = null,
                    correctCount = 2,
                    incorrectCount = 1,
                    fsrsCardJson = "{\"c\":1}",
                    fsrsDueAt = 456L,
                )
            every { vocabularyDao.getAllVocabulary() } returns flowOf(listOf(entity))

            val completion = CompletableDeferred<Boolean>()

            viewModel.exportVocabularyToUri(context, uri) { completion.complete(it) }

            assertThat(completion.await()).isTrue()
            assertThat(tempFile.readText()).contains("\"word\":\"Haus\"")
        }

    @Test
    fun `exportVocabularyToUri reports failure on exception`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()
            val context = appContext
            val tempFile =
                kotlin.io.path
                    .createTempFile(prefix = "export", suffix = ".json")
                    .toFile()
            val uri = Uri.fromFile(tempFile)
            every { vocabularyDao.getAllVocabulary() } returns flow { throw IllegalStateException("boom") }

            val completion = CompletableDeferred<Boolean>()

            viewModel.exportVocabularyToUri(context, uri) { completion.complete(it) }

            assertThat(completion.await()).isFalse()
            assertThat(tempFile.readText()).isEmpty()
        }

    @Test
    fun `importVocabularyFromUri delegates parsed items to repository`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val parsedItem = VocabularyItem(id = 0, word = "Hallo", translation = "Hello", isNew = true)
            val parser =
                object : VocabularyParser {
                    override val id: String = "apkg"
                    override val titleResId: Int = R.string.settings_import_option_anki_apkg
                    override val descriptionResId: Int? = R.string.settings_import_option_anki_apkg_desc
                    override val supportedExtensions: Set<String> = setOf("apkg")
                    override val mimeTypes: List<String> = listOf("application/apkg")

                    override fun parse(file: File): List<VocabularyItem> = listOf(parsedItem)
                }
            coEvery { vocabularyRepository.addVocabularyItem(any()) } returns Unit
            val viewModel = buildViewModel(parsers = setOf(parser))
            val tempFile =
                kotlin.io.path
                    .createTempFile(prefix = "deck", suffix = ".apkg")
                    .toFile()
            tempFile.writeText("placeholder")
            val uri = Uri.fromFile(tempFile)

            var result: VocabularyImportResult? = null
            viewModel.importVocabularyFromUri(appContext, parser.id, uri) { result = it }
            advanceUntilIdle()

            assertThat(result).isEqualTo(VocabularyImportResult.Success(importedCount = 1))
            coVerify { vocabularyRepository.addVocabularyItem(parsedItem) }
        }

    @Test
    fun `importVocabularyFromUri reports unsupported format`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel(parsers = emptySet())
            val tempFile =
                kotlin.io.path
                    .createTempFile(prefix = "deck", suffix = ".apkg")
                    .toFile()
            tempFile.writeText("placeholder")
            val uri = Uri.fromFile(tempFile)

            var result: VocabularyImportResult? = null
            viewModel.importVocabularyFromUri(appContext, "unknown", uri) { result = it }
            advanceUntilIdle()

            assertThat(result).isEqualTo(
                VocabularyImportResult.Failure(VocabularyImportFailureReason.UNSUPPORTED_FORMAT),
            )
            coVerify(exactly = 0) { vocabularyRepository.addVocabularyItem(any()) }
        }

    @Test
    fun `importVocabularyFromUri reports parser errors`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val parser =
                object : VocabularyParser {
                    override val id: String = "apkg"
                    override val titleResId: Int = R.string.settings_import_option_anki_apkg
                    override val descriptionResId: Int? = R.string.settings_import_option_anki_apkg_desc
                    override val supportedExtensions: Set<String> = setOf("apkg")
                    override val mimeTypes: List<String> = listOf("application/apkg")

                    override fun parse(file: File): List<VocabularyItem> = throw IllegalArgumentException("bad")
                }
            val viewModel = buildViewModel(parsers = setOf(parser))
            val tempFile =
                kotlin.io.path
                    .createTempFile(prefix = "deck", suffix = ".apkg")
                    .toFile()
            tempFile.writeText("placeholder")
            val uri = Uri.fromFile(tempFile)

            var result: VocabularyImportResult? = null
            viewModel.importVocabularyFromUri(appContext, parser.id, uri) { result = it }
            advanceUntilIdle()

            assertThat(result).isEqualTo(
                VocabularyImportResult.Failure(VocabularyImportFailureReason.PARSE_ERROR),
            )
            coVerify(exactly = 0) { vocabularyRepository.addVocabularyItem(any()) }
        }
}
